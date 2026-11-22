package com.jurisgames.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.Result
import com.google.zxing.client.result.AddressBookParsedResult
import com.google.zxing.client.result.CalendarParsedResult
import com.google.zxing.client.result.EmailAddressParsedResult
import com.google.zxing.client.result.GeoParsedResult
import com.google.zxing.client.result.ISBNParsedResult
import com.google.zxing.client.result.ProductParsedResult
import com.google.zxing.client.result.ResultParser
import com.google.zxing.client.result.SMSParsedResult
import com.google.zxing.client.result.TelParsedResult
import com.google.zxing.client.result.URIParsedResult
import com.google.zxing.client.result.WifiParsedResult

/** What a scanned code holds, by kind, so the result screen can offer the right action. */
sealed class Payload {
    abstract val raw: String

    data class Link(override val raw: String, val url: String) : Payload()
    data class Wifi(override val raw: String, val ssid: String, val password: String, val security: String, val hidden: Boolean) : Payload()
    data class Contact(override val raw: String, val name: String, val phones: List<String>, val emails: List<String>,
                       val org: String, val address: String, val url: String, val note: String) : Payload() {
        /** Q2-2: "numbers" that are really phone commands; never saved into Contacts, where calling one later runs it. */
        val commandPhones: List<String> get() = phones.filter { isPhoneCommand(it) }
        val safePhones: List<String> get() = phones.filterNot { isPhoneCommand(it) }
    }
    data class Event(override val raw: String, val title: String, val startMs: Long, val endMs: Long, val allDay: Boolean,
                     val location: String, val description: String) : Payload()
    data class Phone(override val raw: String, val number: String) : Payload() {
        /** Safety round 6 Q3: with * or # it is a network command (*21*...# forwards every call), not a number. */
        val isCommand: Boolean get() = isPhoneCommand(number)
    }
    data class Sms(override val raw: String, val number: String, val body: String) : Payload()
    data class Email(override val raw: String, val to: String, val subject: String, val body: String) : Payload()
    data class Geo(override val raw: String, val lat: Double, val lng: Double, val query: String) : Payload()
    data class Product(override val raw: String, val code: String) : Payload()
    data class Text(override val raw: String) : Payload()

    /** The kind's id, stored in the history and used for its icon and label. */
    val kind: String get() = when (this) {
        is Link -> "link"; is Wifi -> "wifi"; is Contact -> "contact"; is Event -> "event"; is Phone -> "phone"
        is Sms -> "sms"; is Email -> "email"; is Geo -> "geo"; is Product -> "product"; is Text -> "text"
    }

    /**
     * One line for lists (Recent, History), built from the parsed parts and never the raw text for kinds with a secret
     * (safety round 6 Q4: a Wi-Fi code's raw text holds its password). Hidden characters are shown as marks.
     */
    fun summary(): String = Visible.show(when (this) {
        is Wifi -> ssid.ifEmpty { tr("Wi-Fi") }
        // Q2-4: a Wi-Fi code ZXing didn't read as one (lower case, malformed) still never shows its password in a list.
        else -> if (WIFI_LIKE.containsMatchIn(raw)) tr("Wi-Fi") + (Regex("(?i)(?:^|;|:)S:([^;]*)").find(raw)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
        else raw.lineSequence().firstOrNull { it.isNotBlank() } ?: raw
    })

    companion object {
        /** Reads [raw] (the code's text) with ZXing's own parsers; [format] is the barcode type it came from. */
        fun of(raw: String, format: BarcodeFormat = BarcodeFormat.QR_CODE): Payload {
            // ZXing reads only an upper-case "WIFI:" prefix: "wifi:T:WPA;S:..;P:..;;" is the same network (Q2-4).
            val t = raw.trimStart()
            if (t.startsWith("wifi:", ignoreCase = true) && !t.startsWith("WIFI:")) {
                val w = of("WIFI:" + t.substring(5), format)
                if (w is Wifi) return w.copy(raw = raw)
            }
            val p = runCatching { ResultParser.parseResult(Result(raw, null, null, format)) }.getOrNull() ?: return Text(raw)
            fun s(v: String?) = v?.trim() ?: ""
            fun all(v: Array<String>?) = v?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            return when (p) {
                is URIParsedResult -> Link(raw, p.uri)
                is WifiParsedResult -> Wifi(raw, s(p.ssid), s(p.password), s(p.networkEncryption).ifEmpty { "nopass" }, p.isHidden)
                is AddressBookParsedResult -> Contact(raw, all(p.names).joinToString(" "), all(p.phoneNumbers), all(p.emails),
                    s(p.org), all(p.addresses).joinToString(", "), all(p.urLs).firstOrNull() ?: "", s(p.note))
                is CalendarParsedResult -> Event(raw, s(p.summary), p.startTimestamp, if (p.endTimestamp > 0) p.endTimestamp else p.startTimestamp,
                    p.isStartAllDay, s(p.location), s(p.description))
                is TelParsedResult -> Phone(raw, s(p.number))
                is SMSParsedResult -> Sms(raw, all(p.numbers).firstOrNull() ?: "", s(p.body))
                is EmailAddressParsedResult -> Email(raw, all(p.tos).joinToString(", "), s(p.subject), s(p.body))
                is GeoParsedResult -> Geo(raw, p.latitude, p.longitude, s(p.query))
                is ProductParsedResult -> Product(raw, s(p.normalizedProductID).ifEmpty { raw })
                is ISBNParsedResult -> Product(raw, s(p.isbn))
                // ZXing drops links it finds suspicious (text before an @, odd characters) to plain text; those are the very
                // links whose warnings matter, so anything shaped like scheme://... goes through our LinkCheck as a link.
                else -> if (URL_SHAPE.matches(raw.trim())) Link(raw, raw.trim()) else Text(raw)
            }
        }

        /** Anything that starts like a Wi-Fi code, in any case. */
        private val WIFI_LIKE = Regex("(?i)^\\s*wifi:")

        private val URL_SHAPE = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://\S+$""")

        /** Star and hash in every form a code can write them (full-width, small, math), after NFKC. */
        private val COMMAND_CHARS = setOf('*', '#', '\u2217', '\u204E', '\u2731', '\u066D', '\uFE61', '\uFE5F', '\uFF0A', '\uFF03')

        /**
         * Whether a "phone number" is a network command (Q3, Q2-2): * or # in any form, also percent-encoded
         * ("%2A%2A21%2A...%23") or full-width ("＊＊21＊...＃"). Checked on every number shown or handed on.
         */
        fun isPhoneCommand(number: String): Boolean {
            val decoded = Regex("%([0-9a-fA-F]{2})").replace(number) { m -> m.groupValues[1].toInt(16).toChar().toString() }
            val n = java.text.Normalizer.normalize(decoded, java.text.Normalizer.Form.NFKC)
            return n.any { it in COMMAND_CHARS } || number.any { it in COMMAND_CHARS }
        }
    }
}
