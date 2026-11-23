package com.jurisgames.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** The text inside a code we make, per kind, in the formats phones' cameras read (the same ones [Payload] reads). */
object Content {
    /** Wi-Fi: \ ; , " : are escaped with a backslash (the format ZXing and phone cameras read). */
    private fun wifiEscape(s: String) = buildString { for (c in s) { if (c in "\\;,\":") append('\\'); append(c) } }

    fun wifi(ssid: String, password: String, security: String, hidden: Boolean): String = buildString {
        val t = if (password.isEmpty()) "nopass" else security
        append("WIFI:T:").append(t).append(";S:").append(wifiEscape(ssid)).append(';')
        if (t != "nopass") append("P:").append(wifiEscape(password)).append(';')
        if (hidden) append("H:true;")
        append(';')
    }

    /** vCard text values escape \ , ; and line breaks. */
    private fun v(s: String) = s.trim().replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\r\n", "\\n").replace("\n", "\\n")

    fun contact(name: String, phone: String, email: String, org: String, url: String, address: String): String = buildString {
        append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
        val n = name.trim()
        val last = n.substringAfterLast(' ', "")
        val first = if (last.isEmpty()) n else n.substringBeforeLast(' ')
        append("N:").append(v(last)).append(';').append(v(first)).append(";;;\r\n")
        append("FN:").append(v(n)).append("\r\n")
        if (org.isNotBlank()) append("ORG:").append(v(org)).append("\r\n")
        if (phone.isNotBlank()) append("TEL:").append(phone.trim()).append("\r\n")
        if (email.isNotBlank()) append("EMAIL:").append(email.trim()).append("\r\n")
        if (url.isNotBlank()) append("URL:").append(url.trim()).append("\r\n")
        if (address.isNotBlank()) append("ADR:;;").append(v(address)).append(";;;;\r\n")
        append("END:VCARD")
    }

    private fun stamp(ms: Long, allDay: Boolean): String {
        val f = SimpleDateFormat(if (allDay) "yyyyMMdd" else "yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
        if (!allDay) f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(ms))
    }

    /** An event (iCalendar VEVENT); [startMs]/[endMs] are instants, or local midnights when [allDay]. */
    fun event(title: String, startMs: Long, endMs: Long, allDay: Boolean, location: String, description: String): String = buildString {
        append("BEGIN:VEVENT\r\n")
        append("SUMMARY:").append(v(title)).append("\r\n")
        if (allDay) {
            append("DTSTART;VALUE=DATE:").append(stamp(startMs, true)).append("\r\n")
            append("DTEND;VALUE=DATE:").append(stamp(max(endMs, startMs + 86_400_000L), true)).append("\r\n")
        } else {
            append("DTSTART:").append(stamp(startMs, false)).append("\r\n")
            append("DTEND:").append(stamp(max(endMs, startMs), false)).append("\r\n")
        }
        if (location.isNotBlank()) append("LOCATION:").append(v(location)).append("\r\n")
        if (description.isNotBlank()) append("DESCRIPTION:").append(v(description)).append("\r\n")
        append("END:VEVENT")
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun phone(number: String) = "tel:" + number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

    fun sms(number: String, body: String) = "SMSTO:" + number.filter { it.isDigit() || it == '+' } + ":" + body

    fun email(to: String, subject: String, body: String): String {
        val q = listOfNotNull(subject.takeIf { it.isNotBlank() }?.let { "subject=" + enc(it) }, body.takeIf { it.isNotBlank() }?.let { "body=" + enc(it) })
        return "mailto:" + address(to) + (if (q.isEmpty()) "" else "?" + q.joinToString("&"))
    }

    /**
     * Safety round 6 Q7: the address part of a mailto link keeps only letters, digits and @ , . + - _; anything else
     * (an encoded ? or & from a scanned code, which would add a hidden bcc or message) is percent-encoded.
     */
    fun address(to: String): String = to.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",") { a ->
        a.map { c -> if ((c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c in "@.+-_") c.toString() else enc(c.toString()) }.joinToString("")
    }

    /** An address with parts a mail app could read as more than an address. */
    fun oddAddress(to: String): Boolean = to.any { it in "?&#=%" }

    private val LINK_IN_TEXT = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"']+""")

    /** Every web link written inside other text (a card's note, an event's place or details, a message), at most 5 (Q2-3). */
    fun linksIn(vararg texts: String): List<String> = texts.flatMap { t -> LINK_IN_TEXT.findAll(t).map { it.value.trimEnd('.', ',', ')', ';') }.toList() }
        .map { if (it.startsWith("www.", ignoreCase = true)) "https://$it" else it }.distinct().take(5)

    /** The lookalike warnings of the domains of these email addresses (Cyrillic а in "pаypal.com"), as English templates (Q2-3). */
    fun addressWarnings(addresses: List<String>): List<String> = addresses.mapNotNull { a ->
        a.substringAfterLast('@', "").trim().takeIf { it.isNotEmpty() }
    }.flatMap { domain ->
        val r = LinkCheck.check("https://$domain/")
        if (!r.opens) (if (domain.any { it.code > 0x7F }) listOf(ADDRESS_LETTERS) else emptyList()) else r.warnings.filter { it == LinkCheck.PUNYCODE || it == LinkCheck.MIXED }.map { if (it == LinkCheck.PUNYCODE) ADDRESS_LETTERS else it }
    }.distinct()

    const val ADDRESS_LETTERS = "The address uses letters outside a–z. Check it carefully: it may imitate another address."

    /** A typed address becomes a web link: "example.com" gets https:// in front. */
    fun link(s: String): String {
        val t = s.trim()
        return if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(t)) t else "https://$t"
    }
}

/** A code as squares, drawn by the app, saved as PNG (Android) or SVG (here), and read back before it is offered. */
object Codes {
    /**
     * The most text one code holds at the correction [matrix] uses: M without a picture (version 40: 2334 bytes less 4 for the UTF-8 mark and length;
     * 2953 is level L, which is never used, so text in between made nothing at all: UX review U-Q8), H with one (1272).
     */
    const val MAX_BYTES_H = 1272
    const val MAX_BYTES = 2330

    /** A code's dark squares, without the quiet zone (added when drawn). Correction H when a logo covers the middle. */
    fun matrix(text: String, withLogo: Boolean): BitMatrix {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to if (withLogo) ErrorCorrectionLevel.H else ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 0,
        )
        return QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    }

    /** How much of the code a logo may cover, as a share of its side (H recovers 30 %; 22 % of the side is ~5 % area). */
    const val LOGO_SIDE = 0.22

    const val QUIET = 4

    /** The code as pixels (ARGB), [scale] px per square, with the quiet zone, a logo hole cleared to [bg] when asked. */
    fun pixels(m: BitMatrix, scale: Int, fg: Int, bg: Int, logoHole: Boolean): Pair<Int, IntArray> {
        val n = m.width
        val side = (n + 2 * QUIET) * scale
        val px = IntArray(side * side) { bg }
        val hole = holeRange(n)
        for (y in 0 until n) for (x in 0 until n) {
            if (!m.get(x, y)) continue
            if (logoHole && x in hole && y in hole) continue
            for (dy in 0 until scale) {
                val row = ((y + QUIET) * scale + dy) * side + (x + QUIET) * scale
                for (dx in 0 until scale) px[row + dx] = fg
            }
        }
        return side to px
    }

    /** The squares a logo covers (centered, [LOGO_SIDE] of the code's side). */
    fun holeRange(n: Int): IntRange {
        val k = max(1, (n * LOGO_SIDE).toInt()).let { if ((n - it) % 2 == 1) it + 1 else it }
        val from = (n - k) / 2
        return from until from + k
    }

    private fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)

    /** SVG: one path of squares (crisp at any size), the background as a rect; [logoPng] goes in the middle. */
    fun svg(m: BitMatrix, fg: Int, bg: Int, logoPng: ByteArray?): String = buildString {
        val n = m.width
        val side = n + 2 * QUIET
        val hole = holeRange(n)
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $side $side\" shape-rendering=\"crispEdges\">")
        append("<rect width=\"$side\" height=\"$side\" fill=\"${hex(bg)}\"/><path fill=\"${hex(fg)}\" d=\"")
        for (y in 0 until n) for (x in 0 until n) {
            if (!m.get(x, y) || (logoPng != null && x in hole && y in hole)) continue
            append("M${x + QUIET} ${y + QUIET}h1v1h-1z")
        }
        append("\"/>")
        if (logoPng != null) {
            val at = hole.first + QUIET
            val size = hole.last - hole.first + 1
            append("<image x=\"$at\" y=\"$at\" width=\"$size\" height=\"$size\" href=\"data:image/png;base64,")
            append(java.util.Base64.getEncoder().encodeToString(logoPng)).append("\"/>")
        }
        append("</svg>")
    }

    private fun lin(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(argb: Int): Double = 0.2126 * lin((argb shr 16) and 255) + 0.7152 * lin((argb shr 8) and 255) + 0.0722 * lin(argb and 255)

    /** The WCAG contrast ratio of two colors (1..21). */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** null when these colors scan well, or why not: many cameras need dark squares on a light background. */
    fun colorProblem(fg: Int, bg: Int): String? = when {
        luminance(fg) >= luminance(bg) -> "The squares must be darker than the background, or many cameras won't read it."
        contrast(fg, bg) < 4.0 -> "These colors are too close. Pick a darker color or a lighter background."
        else -> null
    }

    /** Reads pixels back with the same decoder the scanner uses; the text, or null. */
    fun read(side: Int, px: IntArray): String? {
        val src = RGBLuminanceSource(side, side, px)
        val hints = mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))
        return runCatching { MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(src)), hints).text }.getOrNull()
    }
}

/**
 * U-Q11: a code made here, opened from History, goes back into Make with its fields filled (the Wi-Fi password
 * isn't typed again). Only kinds Make can make; anything else (a map place, a product barcode) gives null.
 */
data class Refill(val kind: String, val fields: Map<String, String>, val security: String = "WPA", val hidden: Boolean = false) {
    companion object {
        fun of(raw: String): Refill? = when (val p = Payload.of(raw)) {
            is Payload.Link -> Refill("link", mapOf("url" to p.url))
            is Payload.Text -> Refill("text", mapOf("text" to p.raw))
            is Payload.Wifi -> Refill("wifi", mapOf("ssid" to p.ssid, "password" to p.password),
                if (p.security.equals("WEP", true)) "WEP" else "WPA", p.hidden)
            is Payload.Contact -> Refill("contact", mapOf("name" to p.name, "phone" to (p.phones.firstOrNull() ?: ""),
                "email" to (p.emails.firstOrNull() ?: ""), "org" to p.org, "web" to p.url, "address" to p.address))
            is Payload.Event -> {
                // ZXing reads a date-only start (all day) as midnight GMT; a timed one is an instant shown in local time
                val zone = if (p.allDay) java.util.TimeZone.getTimeZone("UTC") else java.util.TimeZone.getDefault()
                val cal = java.util.Calendar.getInstance(zone).apply { timeInMillis = p.startMs }
                val date = "%04d-%02d-%02d".format(java.util.Locale.ROOT, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
                val time = if (p.allDay) "" else "%02d:%02d".format(java.util.Locale.ROOT, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
                val h = (p.endMs - p.startMs) / 3_600_000.0
                val hours = if (p.allDay) "" else if (h == Math.floor(h)) h.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, h).trimEnd('0')
                Refill("event", mapOf("title" to p.title, "date" to date, "time" to time, "hours" to hours, "where" to p.location, "details" to p.description))
            }
            is Payload.Phone -> Refill("phone", mapOf("number" to p.number))
            is Payload.Sms -> Refill("sms", mapOf("number" to p.number, "message" to p.body))
            is Payload.Email -> Refill("email", mapOf("to" to p.to, "subject" to p.subject, "message" to p.body))
            else -> null
        }
    }
}
