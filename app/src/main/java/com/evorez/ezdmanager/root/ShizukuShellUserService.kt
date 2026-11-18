package com.evorez.ezdmanager.root

import androidx.annotation.Keep
import com.evorez.ezdmanager.IShizukuShellService
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

@Keep
class ShizukuShellUserService : IShizukuShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun execCommand(command: String?): String {
        val cmd = command?.trim().orEmpty()
        if (cmd.isBlank() || cmd.contains('\u0000')) {
            return JSONObject().apply {
                put("code", -1)
                put("out", "")
                put("err", "Invalid command input")
            }.toString()
        }
        return try {
            val proc = ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(false)
                .start()
            val stdout = proc.inputStream.bufferedReader().readText()
            val stderr = proc.errorStream.bufferedReader().readText()
            val finished = proc.waitFor(8, TimeUnit.SECONDS)
            val code = if (finished) proc.exitValue() else {
                proc.destroyForcibly()
                -1
            }
            JSONObject().apply {
                put("code", code)
                put("out", stdout)
                put("err", if (!finished && stderr.isBlank()) "Timed out after 8s" else stderr)
            }.toString()
        } catch (t: Throwable) {
            JSONObject().apply {
                put("code", -1)
                put("out", "")
                put("err", t.message ?: "Shizuku shell failed")
            }.toString()
        }
    }
}
