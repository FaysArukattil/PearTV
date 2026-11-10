package com.leantv.app.adb

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
    data class Ok(val host: String) : ConnResult
    data object PortClosed : ConnResult
    data class Failed(val reason: String) : ConnResult
}

data class Shell(val code: Int, val out: String) {
    val ok: Boolean get() = code == 0
}

/**
 * Talks to the TV's own ADB daemon (adbd) over TCP, from the TV itself.
 * Requires "USB debugging" to be on; the TV shows a one-time authorization prompt.
 */
class AdbClient(private val keyDir: File) {

    @Volatile
    private var dadb: Dadb? = null

    val isConnected: Boolean get() = dadb != null

    private fun keys(): AdbKeyPair {
        val priv = File(keyDir, "adbkey")
        val pub = File(keyDir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    private fun probe(host: String, port: Int = ADB_PORT, timeoutMs: Int = 1500): Boolean = try {
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

    suspend fun connect(): ConnResult = withContext(Dispatchers.IO) {
        close()
        val host = listOfNotNull("127.0.0.1", lanIp()).firstOrNull { probe(it) }
        if (host == null) {
            ConnResult.PortClosed
        } else {
            try {
                val d = Dadb.create(host, ADB_PORT, keys())
                val reply = d.shell("echo leantv").output.trim()
                if (reply == "leantv") {
                    dadb = d
                    ConnResult.Ok(host)
                } else {
                    ConnResult.Failed("Unexpected reply from the TV: $reply")
                }
            } catch (t: Throwable) {
                ConnResult.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    suspend fun shell(cmd: String): Shell = withContext(Dispatchers.IO) {
        val d = dadb
        if (d == null) {
            Shell(-1, "Not connected")
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
        } catch (_: Throwable) {
        }
        dadb = null
    }

    companion object {
        const val ADB_PORT = 5555
    }
}
