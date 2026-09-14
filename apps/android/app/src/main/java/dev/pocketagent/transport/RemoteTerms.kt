// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

// Cihazlar-arası oturum keşfi (0.28.9): her oturum host'ta adlı bir
// pa-<hex8> tmux'unda yaşar; metadata ~/.pocket-agent/terms/<tmux>
// dosyasında tutulur ("n=<urlenc ad>", "d=<ANDROID_ID>"). Başka cihazın
// açtığı oturum buradan bulunur, dokununca aynı tmux'a attach edilir —
// iki cihaz aynı terminali paylaşır (tmux multi-client).
data class RemoteTerm(val tmux: String, val name: String?, val device: String?)

// Tek exec: önce canlı pa-* tmux adları, @@REG@@ sonrası registry içerikleri
// (ölü kayıtlar has-session ile elenip silinir). Glob eşleşmezse literal
// "pa-*" has-session'dan düşer — boş çıktı güvenli.
const val REMOTE_TERMS_PROBE_CMD =
    "tmux list-sessions -F '#S' 2>/dev/null; echo '@@REG@@'; " +
        "for f in \"\$HOME\"/.pocket-agent/terms/pa-*; do " +
        "b=\$(basename \"\$f\"); " +
        "tmux has-session -t \"\$b\" 2>/dev/null || { rm -f \"\$f\"; continue; }; " +
        "echo \"@@F@@\$b\"; cat \"\$f\"; done 2>/dev/null"

fun parseRemoteTerms(out: String): List<RemoteTerm> {
    val live = out.substringBefore("@@REG@@")
        .lineSequence().map { it.trim() }
        .filter { it.startsWith("pa-") }.toMutableSet()
    val names = mutableMapOf<String, String?>()
    val devs = mutableMapOf<String, String?>()
    var cur: String? = null
    out.substringAfter("@@REG@@", "").lineSequence().forEach { l ->
        when {
            l.startsWith("@@F@@") -> {
                cur = l.removePrefix("@@F@@").trim().ifBlank { null }
                cur?.let { live += it }
            }
            l.startsWith("n=") -> cur?.let {
                names[it] = java.net.URLDecoder.decode(l.substring(2), "UTF-8").ifBlank { null }
            }
            l.startsWith("d=") -> cur?.let { devs[it] = l.substring(2).ifBlank { null } }
        }
    }
    return live.sorted().map { RemoteTerm(it, names[it], devs[it]) }
}
