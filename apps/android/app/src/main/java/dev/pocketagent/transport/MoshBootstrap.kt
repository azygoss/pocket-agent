// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

// P07: mosh bootstrap. SSH exec kanalıyla `mosh-server new` çalıştırılır;
// anahtar SSH içinde döner (asla diske/loga düşmez — plan §2.3). UDP client
// tarafı native binary (jniLibs/libmoshclient.so) ile cihazda çalışır; bu
// sınıf yalnızca host tarafı probing + anahtar ayrıştırmayı yapar (headless
// kanıtlanabilir kısım).

// SSH exec kanalı açabilen transport (SshjTransport uygular).
interface ExecCapable {
    // (exitCode, stdout+stderr birleşik)
    suspend fun exec(cmd: String, timeoutMs: Int = 10_000): Pair<Int, String>
}

data class MoshSession(val udpPort: Int, val key: String)

object MoshBootstrap {
    // `mosh-server new` çıktısı: "MOSH CONNECT <port> <key>" satırı.
    fun parseConnect(output: String): MoshSession? {
        val m = Regex("MOSH CONNECT (\\d+) ([A-Za-z0-9/+]+)").find(output) ?: return null
        val port = m.groupValues[1].toIntOrNull() ?: return null
        return MoshSession(port, m.groupValues[2])
    }

    const val SERVER_CMD = "mosh-server new -s -c 256 -l LC_ALL=C.UTF-8 -p 60000:61000"

    // Host'ta mosh-server var mı + oturum başlat. Yoksa null (UI SSH'de kalır).
    suspend fun start(exec: ExecCapable): MoshSession? {
        val (code, out) = try {
            exec.exec(SERVER_CMD, timeoutMs = 8_000)
        } catch (e: Exception) {
            return null
        }
        if (code != 0) return null
        return parseConnect(out)
    }
}
