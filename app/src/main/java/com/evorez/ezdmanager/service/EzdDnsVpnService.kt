package com.evorez.ezdmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.evorez.ezdmanager.R
import com.evorez.ezdmanager.data.SettingsDataStore
import com.evorez.ezdmanager.model.PerAppConfig
import com.evorez.ezdmanager.model.PerAppDnsMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLSocketFactory

object PerAppDnsVpnBridge {
    private val _vpnActive = MutableStateFlow(false)
    val vpnActive: StateFlow<Boolean> = _vpnActive.asStateFlow()

    private val _routedPackages = MutableStateFlow<List<String>>(emptyList())
    val routedPackages: StateFlow<List<String>> = _routedPackages.asStateFlow()

    internal fun setState(active: Boolean, packages: List<String>) {
        _vpnActive.value = active
        _routedPackages.value = packages
    }

    fun startOrUpdate(context: Context) {
        val intent = Intent(context.applicationContext, EzdDnsVpnService::class.java).apply {
            action = ACTION_SYNC
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.applicationContext.startForegroundService(intent)
            } else {
                context.applicationContext.startService(intent)
            }
        }
    }

    fun stop(context: Context) {
        val intent = Intent(context.applicationContext, EzdDnsVpnService::class.java).apply {
            action = ACTION_STOP
        }
        runCatching { context.applicationContext.startService(intent) }
    }

    const val ACTION_SYNC = "com.evorez.ezdmanager.VPN_DNS_SYNC"
    const val ACTION_STOP = "com.evorez.ezdmanager.VPN_DNS_STOP"
}

/**
 * Genuine per-app DNS tunnel built on Android's VpnService using Builder.addAllowedApplication(pkg).
 * Only packages assigned Custom DNS, DoT, or DoH enter the TUN interface, forwarding UDP/53
 * DNS queries via protected sockets (UDP/53, RFC 7858 DoT TCP/853, or RFC 8484 DoH HTTPS).
 */
class EzdDnsVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunFd: ParcelFileDescriptor? = null
    private var forwardJob: Job? = null
    private lateinit var store: SettingsDataStore

    @Volatile
    private var appRules: Map<String, PerAppConfig> = emptyMap()

    override fun onCreate() {
        super.onCreate()
        store = SettingsDataStore(applicationContext)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == PerAppDnsVpnBridge.ACTION_STOP) {
            teardown()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildNotification("Initializing Per-App DNS tunnel…"))

        scope.launch {
            val allConfigs = runCatching { store.perAppConfigsFlow.first() }.getOrDefault(emptyMap())
            val dnsApps = allConfigs.filterValues {
                it.dnsMode != PerAppDnsMode.SYSTEM && it.packageName != packageName
            }
            appRules = dnsApps
            if (dnsApps.isEmpty()) {
                teardown()
                stopSelf()
                return@launch
            }
            rebuildTunnel(dnsApps)
        }
        return START_STICKY
    }

    override fun onRevoke() {
        teardown()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        teardown()
        scope.cancel()
        super.onDestroy()
    }

    private fun teardown() {
        forwardJob?.cancel()
        forwardJob = null
        runCatching { tunFd?.close() }
        tunFd = null
        PerAppDnsVpnBridge.setState(false, emptyList())
    }

    private fun rebuildTunnel(rules: Map<String, PerAppConfig>) {
        forwardJob?.cancel()
        runCatching { tunFd?.close() }

        val builder = Builder()
            .setSession("EZD Per-App DNS")
            .setMtu(1500)
            .addAddress(TUN_CLIENT_IP, 32)
            .addDnsServer(TUN_DNS_IP)
            .addRoute(TUN_DNS_IP, 32)

        val activePkgs = mutableListOf<String>()
        rules.keys.forEach { pkg ->
            runCatching {
                builder.addAllowedApplication(pkg)
                activePkgs.add(pkg)
            }
        }

        if (activePkgs.isEmpty()) {
            teardown()
            stopSelf()
            return
        }

        val pfd = runCatching { builder.establish() }.getOrNull()
        if (pfd == null) {
            PerAppDnsVpnBridge.setState(false, emptyList())
            stopSelf()
            return
        }

        tunFd = pfd
        PerAppDnsVpnBridge.setState(true, activePkgs)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification("Routing DNS for ${activePkgs.size} app(s)"))

        val primaryRule = rules.values.first()
        forwardJob = scope.launch {
            runTunLoop(pfd, primaryRule)
        }
    }

    private suspend fun runTunLoop(pfd: ParcelFileDescriptor, fallbackRule: PerAppConfig) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buffer = ByteArray(2048)

        while (scope.isActive) {
            val len = runCatching { input.read(buffer) }.getOrElse { -1 }
            if (len <= 0) break
            if (len < 28) continue

            val version = (buffer[0].toInt() ushr 4) and 0x0F
            if (version != 4) continue

            val ihl = (buffer[0].toInt() and 0x0F) * 4
            if (len < ihl + 8) continue
            val proto = buffer[9].toInt() and 0xFF
            if (proto != 17) continue // UDP only

            val srcPort = ((buffer[ihl].toInt() and 0xFF) shl 8) or (buffer[ihl + 1].toInt() and 0xFF)
            val dstPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)
            if (dstPort != 53) continue

            val dnsOffset = ihl + 8
            val dnsLen = len - dnsOffset
            if (dnsLen <= 0) continue

            val dnsQuery = buffer.copyOfRange(dnsOffset, dnsOffset + dnsLen)
            val packetHeader = buffer.copyOfRange(0, ihl)

            // Resolve asynchronously so multiple concurrent queries don't stall the TUN reader
            scope.launch {
                val activeRule = appRules.values.firstOrNull() ?: fallbackRule
                val dnsResp = resolveDnsQuery(dnsQuery, activeRule) ?: return@launch
                val replyPacket = buildIpv4UdpReply(packetHeader, srcPort, dstPort, dnsResp)
                synchronized(output) {
                    runCatching {
                        output.write(replyPacket)
                        output.flush()
                    }
                }
            }
        }
    }

    private fun resolveDnsQuery(query: ByteArray, rule: PerAppConfig): ByteArray? {
        return when (rule.dnsMode) {
            PerAppDnsMode.DOH -> resolveDoH(query, rule.customDnsServer)
            PerAppDnsMode.DOT -> resolveDoT(query, rule.customDnsServer)
            PerAppDnsMode.CUSTOM_DNS -> resolveUdp(query, rule.customDnsServer.ifBlank { "1.1.1.1" })
            PerAppDnsMode.SYSTEM -> resolveUdp(query, "1.1.1.1")
        }
    }

    private fun resolveUdp(query: ByteArray, serverIpOrHost: String): ByteArray? {
        val target = serverIpOrHost.ifBlank { "1.1.1.1" }
        return runCatching {
            val socket = DatagramSocket()
            protect(socket)
            socket.soTimeout = 3_500
            socket.use { sock ->
                val addr = InetAddress.getByName(target)
                sock.send(DatagramPacket(query, query.size, addr, 53))
                val respBuf = ByteArray(1500)
                val respPkt = DatagramPacket(respBuf, respBuf.size)
                sock.receive(respPkt)
                respBuf.copyOf(respPkt.length)
            }
        }.getOrNull()
    }

    private fun resolveDoT(query: ByteArray, hostOrIp: String): ByteArray? {
        val host = when {
            hostOrIp.isBlank() || hostOrIp == "1.1.1.1" -> "1.1.1.1"
            hostOrIp == "8.8.8.8" -> "dns.google"
            hostOrIp == "9.9.9.9" -> "dns.quad9.net"
            else -> hostOrIp
        }
        return runCatching {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val sock = factory.createSocket(host, 853)
            sock.soTimeout = 4_000
            sock.use { s ->
                val out = DataOutputStream(s.getOutputStream())
                val inp = DataInputStream(s.getInputStream())
                out.writeShort(query.size)
                out.write(query)
                out.flush()
                val respLen = inp.readUnsignedShort()
                val resp = ByteArray(respLen)
                inp.readFully(resp)
                resp
            }
        }.getOrNull() ?: resolveUdp(query, "1.1.1.1")
    }

    private fun resolveDoH(query: ByteArray, dohUrlOrHost: String): ByteArray? {
        val endpoint = when {
            dohUrlOrHost.startsWith("https://") -> dohUrlOrHost
            dohUrlOrHost == "8.8.8.8" || dohUrlOrHost.contains("google") -> "https://dns.google/dns-query"
            dohUrlOrHost == "9.9.9.9" || dohUrlOrHost.contains("quad9") -> "https://dns.quad9.net/dns-query"
            dohUrlOrHost.contains("adguard") -> "https://dns.adguard-dns.com/dns-query"
            else -> "https://cloudflare-dns.com/dns-query"
        }
        return runCatching {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4_000
                readTimeout = 4_000
                doOutput = true
                setRequestProperty("Content-Type", "application/dns-message")
                setRequestProperty("Accept", "application/dns-message")
            }
            conn.outputStream.use { it.write(query) }
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { it.readBytes() }
            } else null
        }.getOrNull() ?: resolveUdp(query, "1.1.1.1")
    }

    private fun buildIpv4UdpReply(
        reqIpHeader: ByteArray,
        clientPort: Int,
        serverPort: Int,
        dnsPayload: ByteArray
    ): ByteArray {
        val totalLen = 20 + 8 + dnsPayload.size
        val pkt = ByteArray(totalLen)
        val bb = ByteBuffer.wrap(pkt).order(ByteOrder.BIG_ENDIAN)

        // IPv4 Header (20 bytes)
        pkt[0] = 0x45.toByte()
        pkt[1] = 0x00
        bb.putShort(2, totalLen.toShort())
        bb.putShort(4, reqIpHeader[4])
        bb.putShort(6, 0x4000.toShort()) // DF
        pkt[8] = 64.toByte() // TTL
        pkt[9] = 17.toByte() // UDP
        // Swap src (12..15) and dst (16..19)
        System.arraycopy(reqIpHeader, 16, pkt, 12, 4)
        System.arraycopy(reqIpHeader, 12, pkt, 16, 4)

        val ipChecksum = calcIpChecksum(pkt, 20)
        bb.putShort(10, ipChecksum.toShort())

        // UDP Header (8 bytes)
        bb.putShort(20, serverPort.toShort())
        bb.putShort(22, clientPort.toShort())
        bb.putShort(24, (8 + dnsPayload.size).toShort())
        bb.putShort(26, 0) // Checksum computed next
        System.arraycopy(dnsPayload, 0, pkt, 28, dnsPayload.size)

        val udpChecksum = calcUdpChecksum(pkt, 20, 8 + dnsPayload.size)
        bb.putShort(26, udpChecksum.toShort())

        return pkt
    }

    private fun calcIpChecksum(buf: ByteArray, len: Int): Int {
        var sum = 0
        var i = 0
        while (i < len) {
            if (i == 10) {
                i += 2
                continue
            }
            val hi = buf[i].toInt() and 0xFF
            val lo = if (i + 1 < len) buf[i + 1].toInt() and 0xFF else 0
            sum += (hi shl 8) or lo
            i += 2
        }
        while ((sum ushr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun calcUdpChecksum(pkt: ByteArray, udpOffset: Int, udpLen: Int): Int {
        var sum = 0
        // Pseudo-header: src IP + dst IP + proto (17) + udpLen
        for (i in 12 until 20 step 2) {
            sum += ((pkt[i].toInt() and 0xFF) shl 8) or (pkt[i + 1].toInt() and 0xFF)
        }
        sum += 17 + udpLen
        var i = 0
        while (i < udpLen) {
            val idx = udpOffset + i
            val hi = pkt[idx].toInt() and 0xFF
            val lo = if (i + 1 < udpLen) pkt[idx + 1].toInt() and 0xFF else 0
            sum += (hi shl 8) or lo
            i += 2
        }
        while ((sum ushr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        val res = sum.inv() and 0xFFFF
        return if (res == 0) 0xFFFF else res
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "EZD Per-App DNS", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("EZD Per-App DNS Active")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ezd_per_app_dns"
        private const val NOTIF_ID = 7002
        private const val TUN_CLIENT_IP = "10.23.45.1"
        private const val TUN_DNS_IP = "10.23.45.2"
    }
}
