package com.jurisgames.qr

import java.net.IDN
import java.net.URI

/**
 * What a link really points at, shown before anything opens (nothing opens by itself): the host people should read,
 * and plain warnings. Only web links open; anything else (javascript:, intent:, file:, ...) is shown as text.
 *
 * Safety round 6 (APP-RULES 75-77): the host is turned into its ASCII form first and the link is read again with a
 * strict parser; every warning comes from that reading. A link the strict parser can't read doesn't open.
 */
object LinkCheck {
    data class Report(
        /** The link as written. */
        val url: String,
        /** The host as people read it (Unicode), or "" when there is none. */
        val host: String,
        /** What is shown in bold: the whole host (a guess at "the site" misleads on shared hosting). */
        val site: String,
        val opens: Boolean,
        /** English templates (translated by [shown]); {host}, {port}, {scheme} and {letters} are filled in there. */
        val warnings: List<String>,
        val scheme: String = "",
        val port: Int = -1,
        /** The letters outside a-z in the host, written out ("ɡ U+0261"). */
        val letters: String = "",
        /** What Open hands to the browser: the link with its host in ASCII, exactly what was checked. */
        val target: String = "",
        /** The host in ASCII ("xn--oogle-..."), the form the browser really uses. */
        val ascii: String = "",
    )

    /** Well-known short-link and "dynamic QR" hosts: where they lead is hidden until opened. Not a complete list. */
    private val SHORTENERS = setOf(
        "bit.ly", "bitly.com", "tinyurl.com", "t.co", "goo.gl", "is.gd", "v.gd", "ow.ly", "buff.ly", "cutt.ly", "rebrand.ly",
        "t.ly", "rb.gy", "shorturl.at", "tiny.cc", "s.id", "bl.ink", "short.io", "lnkd.in", "trib.al", "u.to",
        "qrco.de", "qr.link", "qr.page", "qr.codes", "q-r.to", "l.ead.me", "me-qr.com", "linktr.ee",
    )

    const val NOT_WEB = "This isn't a web link, so it won't open."
    const val OTHER_SCHEME = "Links of this kind ({scheme}:) don't open from here."
    const val BROKEN = "This link is written in an unusual way, so it won't open. Copy it only if you trust who made the code."
    const val HTTP = "Not secure (http): others on the network can see or change the page."
    const val USERINFO = "The link has text before an @ that looks like an address. It really goes to {host}."
    const val IP = "It goes to a bare number address, not a named site."
    const val PUNYCODE = "The name uses letters outside a–z ({letters}). Check it carefully: it may imitate another site."
    const val MIXED = "The name mixes alphabets. Check it carefully: it may imitate another site."
    const val SHORT = "This is a short link: where it really leads is only shown after opening."
    const val LONG = "This link is unusually long."
    const val PORT = "It uses an unusual port ({port})."

    /** Every warning text, for the translation test. */
    val ALL = listOf(NOT_WEB, OTHER_SCHEME, BROKEN, HTTP, USERINFO, IP, PUNYCODE, MIXED, SHORT, LONG, PORT, Visible.WARNING)

    fun check(url: String): Report {
        val trimmed = url.trim()
        val loose = runCatching { URI(trimmed) }.getOrNull()
        val scheme = loose?.scheme?.lowercase() ?: ""
        val web = scheme == "https" || scheme == "http"
        if (loose == null || !web || loose.rawAuthority.isNullOrEmpty()) {
            val why = if (scheme.isEmpty() || loose == null) NOT_WEB else OTHER_SCHEME
            return Report(trimmed, "", "", false, listOf(why), scheme)
        }
        fun broken() = Report(trimmed, "", "", false, listOf(BROKEN), scheme)
        val authority = loose.rawAuthority
        val head = "$scheme://"
        if (!trimmed.startsWith(head, ignoreCase = true) || !trimmed.regionMatches(head.length, authority, 0, authority.length)) return broken()
        val rest = trimmed.substring(head.length + authority.length)
        val user = if ('@' in authority) authority.substringBeforeLast('@') else null
        val hostPort = authority.substringAfterLast('@')
        val rawHost: String
        val rawPort: String
        if (hostPort.startsWith("[")) {
            if (!hostPort.contains(']')) return broken()
            rawHost = hostPort.substringBefore(']') + "]"
            rawPort = hostPort.substringAfter(']').removePrefix(":")
        } else {
            rawHost = hostPort.substringBefore(':')
            rawPort = hostPort.substringAfter(':', "")
        }
        if (rawHost.isEmpty() || !rawPort.all { it.isDigit() }) return broken()
        val ascii = if (rawHost.startsWith("[")) rawHost.lowercase()
        else runCatching { IDN.toASCII(rawHost, IDN.ALLOW_UNASSIGNED) }.getOrNull()?.lowercase()?.trimEnd('.') ?: return broken()
        if (ascii.isEmpty()) return broken()
        val target = head + (if (user != null) "$user@" else "") + ascii + (if (rawPort.isNotEmpty()) ":$rawPort" else "") + rest
        // The strict reading: if it can't name a server, nothing below would be trustworthy.
        val strict = runCatching { URI(target).parseServerAuthority() }.getOrNull() ?: return broken()
        val host = strict.host?.lowercase()?.trimEnd('.') ?: return broken()
        if (host != ascii) return broken()

        val warnings = mutableListOf<String>()
        val unicode = if (host.startsWith("[")) host else runCatching { IDN.toUnicode(host, IDN.ALLOW_UNASSIGNED) }.getOrDefault(host)
        if (scheme == "http") warnings += HTTP
        if (strict.rawUserInfo != null) warnings += USERINFO
        if (isNumber(host)) warnings += IP
        var letters = ""
        if (host.split('.').any { it.startsWith("xn--") }) {
            warnings += PUNYCODE
            letters = unicode.codePoints().filter { it > 0x7F }.distinct().toArray().joinToString(", ") { cp ->
                String(Character.toChars(cp)) + " U+%04X".format(cp)
            }
        }
        if (mixedScripts(unicode)) warnings += MIXED
        if (SHORTENERS.any { host == it || host.endsWith(".$it") }) warnings += SHORT
        if (trimmed.length > 2000) warnings += LONG
        if (strict.port != -1 && strict.port != 443 && strict.port != 80) warnings += PORT
        if (Visible.has(trimmed)) warnings += Visible.WARNING
        return Report(trimmed, unicode, unicode, true, warnings, scheme, strict.port, letters, target, host)
    }

    /** A warning in the app's language, with its values filled in. */
    fun shown(w: String, r: Report): String =
        tr(w, "host" to r.host, "port" to r.port, "scheme" to r.scheme, "letters" to r.letters)

    /**
     * A number address in any notation browsers accept: IPv6 in brackets, or a last label that is a number
     * (decimal, 0x hex or 0-prefixed octal: "3232235777", "0x7f000001", "0177.0.0.1", "192.168.1").
     */
    fun isNumber(h: String): Boolean {
        if (h.startsWith("[")) return true
        val last = h.trimEnd('.').substringAfterLast('.')
        return last.isNotEmpty() && (last.all { it.isDigit() } || Regex("^0x[0-9a-f]*$").matches(last))
    }

    /** Latin mixed with Cyrillic or Greek in one name ("аpple" with a Cyrillic а): an extra to the letters warning. */
    private fun mixedScripts(s: String): Boolean {
        var latin = false
        var other = false
        for (c in s) {
            if (!c.isLetter()) continue
            when (Character.UnicodeScript.of(c.code)) {
                Character.UnicodeScript.LATIN -> latin = true
                Character.UnicodeScript.CYRILLIC, Character.UnicodeScript.GREEK -> other = true
                else -> {}
            }
        }
        return latin && other
    }
}
