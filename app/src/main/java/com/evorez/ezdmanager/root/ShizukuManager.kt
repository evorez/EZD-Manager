package com.evorez.ezdmanager.root

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.evorez.ezdmanager.BuildConfig
import com.evorez.ezdmanager.IShizukuShellService
import com.evorez.ezdmanager.model.PrivilegeMode
import com.evorez.ezdmanager.model.RootStatus
import com.evorez.ezdmanager.model.ShizukuStatus
import com.evorez.ezdmanager.model.WorkMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object ShizukuManager {
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 4201

    private val workerDispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ezd-shizuku-worker").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val initialized = AtomicBoolean(false)
    private var appContext: Context? = null

    @Volatile
    private var shellService: IShizukuShellService? = null

    @Volatile
    private var bindDeferred: CompletableDeferred<IShizukuShellService?>? = null

    private val _status = MutableStateFlow(ShizukuStatus.NOT_INSTALLED)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        shellService = null
        refreshStatus()
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE) {
            refreshStatus()
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                appContext?.let { runCatching { ensureBoundSync(it) } }
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = IShizukuShellService.Stub.asInterface(binder)
            shellService = svc
            bindDeferred?.complete(svc)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shellService = null
        }
    }

    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (initialized.compareAndSet(false, true)) {
            runCatching {
                Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
                Shizuku.addBinderDeadListener(binderDeadListener)
                Shizuku.addRequestPermissionResultListener(permissionResultListener)
            }
        }
        refreshStatus(ctx)
    }

    fun isShizukuInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    fun refreshStatus(context: Context? = appContext): ShizukuStatus {
        val ctx = context ?: return _status.value
        val installed = isShizukuInstalled(ctx)
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val next = when {
            !binderAlive && !installed -> ShizukuStatus.NOT_INSTALLED
            !binderAlive -> ShizukuStatus.NOT_RUNNING
            runCatching { !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false) ->
                ShizukuStatus.GRANTED
            else -> ShizukuStatus.PERMISSION_REQUIRED
        }
        _status.value = next
        return next
    }

    fun isPermissionGranted(): Boolean = _status.value == ShizukuStatus.GRANTED

    fun requestPermission(): ShizukuStatus {
        val cur = refreshStatus()
        if (cur == ShizukuStatus.PERMISSION_REQUIRED) {
            runCatching {
                if (!Shizuku.isPreV11()) {
                    Shizuku.requestPermission(REQUEST_CODE)
                }
            }
        }
        return _status.value
    }

    private fun userServiceArgs(ctx: Context): Shizuku.UserServiceArgs {
        return Shizuku.UserServiceArgs(
            ComponentName(ctx.packageName, ShizukuShellUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("shizuku_shell")
            .debuggable(BuildConfig.DEBUG)
            .version(1)
    }

    private fun ensureBoundSync(ctx: Context) {
        if (shellService?.asBinder()?.isBinderAlive == true) return
        val deferred = CompletableDeferred<IShizukuShellService?>()
        bindDeferred = deferred
        runCatching {
            Shizuku.bindUserService(userServiceArgs(ctx), serviceConnection)
        }.onFailure {
            deferred.complete(null)
        }
    }

    suspend fun exec(command: String, timeoutMs: Long = 6_000L): CommandResult {
        if (command.isBlank() || command.contains('\u0000')) {
            return CommandResult(false, -1, emptyList(), emptyList(), error = "Invalid command input")
        }
        val ctx = appContext ?: return CommandResult(false, -1, emptyList(), emptyList(), error = "Shizuku not initialized")
        if (refreshStatus(ctx) != ShizukuStatus.GRANTED) {
            return CommandResult(false, 126, emptyList(), emptyList(), error = "Shizuku permission not granted")
        }

        return withContext(workerDispatcher) {
            try {
                withTimeout(timeoutMs) {
                    var svc = shellService?.takeIf { it.asBinder().isBinderAlive }
                    if (svc == null) {
                        val deferred = CompletableDeferred<IShizukuShellService?>()
                        bindDeferred = deferred
                        Shizuku.bindUserService(userServiceArgs(ctx), serviceConnection)
                        svc = deferred.await()
                    }
                    if (svc == null) {
                        return@withTimeout CommandResult(
                            false, -1, emptyList(), emptyList(),
                            error = "Unable to bind Shizuku shell service"
                        )
                    }
                    val rawJson = svc.execCommand(command)
                    val json = JSONObject(rawJson)
                    val code = json.optInt("code", -1)
                    val out = json.optString("out", "")
                        .lines()
                        .dropLastWhile { it.isEmpty() }
                    val err = json.optString("err", "")
                        .lines()
                        .dropLastWhile { it.isEmpty() }
                    CommandResult(
                        success = code == 0,
                        exitCode = code,
                        stdout = out,
                        stderr = err,
                        error = err.firstOrNull()?.takeIf { code != 0 }
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                CommandResult(
                    success = false,
                    exitCode = -1,
                    stdout = emptyList(),
                    stderr = emptyList(),
                    error = t.message ?: "Shizuku command failed"
                )
            }
        }
    }

    fun resolveEffectiveMode(
        workMode: WorkMode,
        rootStatus: RootStatus,
        shizukuStatus: ShizukuStatus
    ): PrivilegeMode {
        val hasRoot = rootStatus == RootStatus.GRANTED
        val hasShizuku = shizukuStatus == ShizukuStatus.GRANTED
        return when (workMode) {
            WorkMode.AUTO -> when {
                hasRoot -> PrivilegeMode.ROOT
                hasShizuku -> PrivilegeMode.SHIZUKU
                else -> PrivilegeMode.BASIC
            }
            WorkMode.ROOT -> if (hasRoot) PrivilegeMode.ROOT else PrivilegeMode.BASIC
            WorkMode.SHIZUKU -> if (hasShizuku) PrivilegeMode.SHIZUKU else PrivilegeMode.BASIC
            WorkMode.BASIC -> PrivilegeMode.BASIC
        }
    }
}

object CommandBridge {
    suspend fun execPrivileged(
        command: String,
        useRoot: Boolean,
        useShizuku: Boolean,
        timeoutMs: Long = 6_000L
    ): CommandResult {
        return when {
            useRoot -> RootShellManager.exec(command, timeoutMs)
            useShizuku -> ShizukuManager.exec(command, timeoutMs)
            else -> CommandResult(false, 126, emptyList(), emptyList(), error = "Requires Root or Shizuku")
        }
    }
}
