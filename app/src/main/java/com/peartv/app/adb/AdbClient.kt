package com.peartv.app.adb

import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

sealed interface ConnResult {
    data class Ok(val host: String, val port: Int) : ConnResult
    data object PortClosed : ConnResult
    data class Failed(val reason: String) : ConnResult
}

data class Shell(val code: Int, val out: String) {
    val ok: Boolean get() = code == 0
}

/**
 * On-Device TV ADB Client.
 * Connects directly to localhost or LAN ADB daemon on the TV.
 */
class AdbClient(private val keyDir: File) {

    @Volatile
    private var dadb: Dadb? = null

    val isConnected: Boolean get() = dadb != null
    var connectedPort: Int = ADB_PORT
        private set

    private fun keys(): AdbKeyPair {
        val priv = File(keyDir, "adbkey")
        val pub = File(keyDir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    private fun probe(host: String, port: Int, timeoutMs: Int = 1200): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
        true
    } catch (_: IOException) {
        false
    }

    private fun lanIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull()?.hostAddress
    } catch (_: Exception) {
        null
    }

    /**
     * Connects to the TV's ADB daemon on the given port (default 5555),
     * or probes 127.0.0.1 and LAN IP.
     */
    suspend fun connect(targetPort: Int = ADB_PORT, specificHost: String? = null): ConnResult = withContext(Dispatchers.IO) {
        close()
        val hosts = if (specificHost != null) listOf(specificHost) else listOfNotNull("127.0.0.1", lanIp())
        val host = hosts.firstOrNull { probe(it, targetPort) }

        if (host == null) {
            ConnResult.PortClosed
        } else {
            try {
                val d = Dadb.create(host, targetPort, keys())
                val reply = d.shell("echo cleanyourtv").output.trim()
                if (reply == "cleanyourtv") {
                    dadb = d
                    connectedPort = targetPort
                    ConnResult.Ok(host, targetPort)
                } else {
                    ConnResult.Failed("Unexpected response from TV adbd: $reply")
                }
            } catch (t: Throwable) {
                ConnResult.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    suspend fun shell(cmd: String): Shell = withContext(Dispatchers.IO) {
        val d = dadb
        if (d == null) {
            Shell(-1, "Not connected to ADB")
        } else {
            try {
                val r = d.shell(cmd)
                Shell(r.exitCode, r.allOutput.trim())
            } catch (t: Throwable) {
                dadb = null
                Shell(-1, t.message ?: "ADB error")
            }
        }
    }

    fun close() {
        try {
            dadb?.close()
        } catch (_: Throwable) {}
        dadb = null
    }

    companion object {
        const val ADB_PORT = 5555
    }
}
