package com.jurisgames.qr

import java.net.IDN
import java.net.URI

/**
 * What a link really points at, shown before anything opens (nothing opens by itself): the host people should read,
 * and plain warnings. Only web links open; anything else (javascript:, intent:, file:, ...) is shown as text.
 */
object LinkCheck {
    data class Report(
        val url: String,
        /** The host as people read it (Unicode), or "" when there is none. */
        val host: String,
        /** The registrable part people should check ("example.co.uk" of "a.b.example.co.uk"). */
        val site: String,
        val opens: Boolean,
        /** English templates (translated by [shown]); {host}, {port} and {scheme} are filled in there. */
        val warnings: List<String>,
        val scheme: String = "",
        val port: Int = -1,
    )

    /** Well-known short-link hosts: where they lead is hidden until opened. */
    private val SHORTENERS = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "v.gd", "ow.ly", "buff.ly", "cutt.ly", "rebrand.ly", "t.ly",
        "rb.gy", "shorturl.at", "tiny.cc", "s.id", "qrco.de", "bl.ink", "short.io", "lnkd.in", "trib.al",
    )

    /** Second-level labels under which the registrable name has three parts ("example.co.uk"). */
    private val SECOND = setOf("co", "com", "org", "net", "gov", "edu", "ac", "gob", "mil", "nom", "ne", "or")

    const val NOT_WEB = "This isn't a web link, so it won't open."
    const val OTHER_SCHEME = "Links of this kind ({scheme}:) don't open from here."
    const val HTTP = "Not secure (http): others on the network can see or change the page."
    const val USERINFO = "The link has text before an @ that looks like an address. It really goes to {host}."
    const val IP = "It goes to a bare number address, not a named site."
    const val PUNYCODE = "The name uses letters from other alphabets. Check it carefully: it may imitate another site."
    const val MIXED = "The name mixes alphabets. Check it carefully: it may imitate another site."
    const val SHORT = "This is a short link: where it really leads is only shown after opening."
    const val LONG = "This link is unusually long."
    const val PORT = "It uses an unusual port ({port})."

    fun check(url: String): Report {
        val trimmed = url.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
        val scheme = uri?.scheme?.lowercase() ?: ""
        val web = scheme == "https" || scheme == "http"
        if (uri == null || !web || uri.rawAuthority.isNullOrEmpty()) {
            val why = if (scheme.isEmpty() || uri == null) NOT_WEB else OTHER_SCHEME
            return Report(trimmed, "", "", false, listOf(why), scheme)
        }
        val warnings = mutableListOf<String>()
        val ascii = (uri.host ?: hostOf(uri.rawAuthority)).lowercase().trimEnd('.')
        val unicode = runCatching { IDN.toUnicode(ascii, IDN.ALLOW_UNASSIGNED) }.getOrDefault(ascii)
        if (scheme == "http") warnings += HTTP
        if (uri.rawUserInfo != null) warnings += USERINFO
        if (isIp(ascii)) warnings += IP
        if (ascii.split('.').any { it.startsWith("xn--") }) warnings += PUNYCODE
        else if (mixedScripts(unicode)) warnings += MIXED
        val site = siteOf(ascii)
        if (site in SHORTENERS) warnings += SHORT
        if (trimmed.length > 2000) warnings += LONG
        if (uri.port != -1 && uri.port != 443 && uri.port != 80) warnings += PORT
        val shownSite = runCatching { IDN.toUnicode(site, IDN.ALLOW_UNASSIGNED) }.getOrDefault(site)
        return Report(trimmed, unicode, shownSite, true, warnings, scheme, uri.port)
    }

    /** A warning in the app's language, with its values filled in. */
    fun shown(w: String, r: Report): String = tr(w, "host" to r.host, "port" to r.port, "scheme" to r.scheme)

    private fun hostOf(authority: String): String = authority.substringAfterLast('@').substringBefore(':').trim('[', ']')

    private fun isIp(h: String) = Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(h) || h.contains(':')

    fun siteOf(host: String): String {
        val parts = host.split('.').filter { it.isNotEmpty() }
        if (parts.size <= 2 || isIp(host)) return host
        val n = if (parts[parts.size - 2] in SECOND && parts.last().length == 2) 3 else 2
        return parts.takeLast(n).joinToString(".")
    }

    /** Latin mixed with Cyrillic or Greek in one name ("аpple" with a Cyrillic а). */
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
