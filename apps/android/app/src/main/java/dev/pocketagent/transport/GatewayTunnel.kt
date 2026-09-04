// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

// P11: host gateway'e (127.0.0.1:24543) erişim yalnız SSH oturumunun içinden,
// direct-tcpip kanalıyla akar. Port-forward dinleyicisi açmaya gerek yok:
// her istek kısa ömürlü bir tünel kanalıdır (HTTP/1.0, bağlantı sonunda kapanır).
interface GatewayTunnel {
    // (status, body) döner; ağ/protokol hatasında exception.
    suspend fun gatewayGet(path: String, token: String, maxBytes: Int): Pair<Int, ByteArray>
}
