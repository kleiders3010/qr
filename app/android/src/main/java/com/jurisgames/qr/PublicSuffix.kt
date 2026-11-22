package com.jurisgames.qr

import java.net.IDN

/**
 * The registrable domain of a host ("paypal.com.account-verify.evil.example" -> "evil.example"), from the Public
 * Suffix List (publicsuffix.org, MPL 2.0, ICANN and private sections; bundled as a resource, refreshed with each
 * release). Safety audit pass 2, Q2-1: the bold "site" is the part a person can trust, never the start of the host.
 */
object PublicSuffix {
    private class Rules(val exact: Set<String>, val wild: Set<String>, val except: Set<String>)

    private val rules: Rules by lazy { load() }

    private fun load(): Rules {
        val exact = HashSet<String>(16_000)
        val wild = HashSet<String>()
        val except = HashSet<String>()
        val text = PublicSuffix::class.java.getResourceAsStream("public_suffix_list.dat")?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        for (raw in text.lineSequence()) {
            val line = raw.trim().substringBefore(' ')
            if (line.isEmpty() || line.startsWith("//")) continue
            val ascii = { s: String -> if (s.all { it.code < 0x80 }) s.lowercase() else runCatching { IDN.toASCII(s, IDN.ALLOW_UNASSIGNED).lowercase() }.getOrNull() }
            when {
                line.startsWith("!") -> ascii(line.substring(1))?.let { except += it }
                line.startsWith("*.") -> ascii(line.substring(2))?.let { wild += it }
                else -> ascii(line)?.let { exact += it }
            }
        }
        return Rules(exact, wild, except)
    }

    /** How many labels at the end of [host] are its public suffix (the "*" rule: at least the last one). */
    private fun suffixLabels(labels: List<String>): Int {
        val r = rules
        for (i in labels.indices) {
            val name = labels.subList(i, labels.size).joinToString(".")
            if (name in r.except) return labels.size - i - 1
            if (name in r.exact) return labels.size - i
            if (i + 1 < labels.size && labels.subList(i + 1, labels.size).joinToString(".") in r.wild) return labels.size - i
        }
        return 1
    }

    /** The registrable domain of an ASCII host, or null when the host is itself a public suffix (or empty). */
    fun registrable(asciiHost: String): String? {
        val labels = asciiHost.lowercase().trimEnd('.').split('.').filter { it.isNotEmpty() }
        if (labels.isEmpty()) return null
        val n = suffixLabels(labels)
        if (labels.size <= n) return null
        return labels.subList(labels.size - n - 1, labels.size).joinToString(".")
    }

    /** Whether [label] alone is a public suffix ("com", "ar", "net"): a name ending in it reads like a whole address. */
    fun isSuffixLabel(label: String): Boolean = label.lowercase() in rules.exact
}
