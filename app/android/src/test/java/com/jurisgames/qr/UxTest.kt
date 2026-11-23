package com.jurisgames.qr

import com.google.zxing.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The UX review of 2026-11-22 (research/ux-review-2026-11-22/qr.md): what its fixes must keep true. */
class UxTest {
    /** U-Q3: a lookalike is never the bold name; it is a likely trap, and so are the other deceptive kinds. */
    @Test fun aLookalikeIsNeverTheHeadline() {
        val r = LinkCheck.check("https://аpple.com/login") // Cyrillic а
        assertTrue(r.opens)
        assertTrue(LinkCheck.serious(r))
        val h = LinkCheck.headline(r)
        assertTrue(h, h.startsWith("xn--"))
        assertFalse(h, h.any { it.code > 0x7F })
        for (u in listOf("https://paypal.com.account-verify.login.secure.example.com/", "https://192.168.0.1/admin", "https://google.com@evil.example/"))
            assertTrue(u, LinkCheck.serious(LinkCheck.check(u)))
        for (u in listOf("http://example.com", "https://bit.ly/abc", "https://example.com:8443/", "https://example.com"))
            assertFalse(u, LinkCheck.serious(LinkCheck.check(u)))
        assertEquals("example.com", LinkCheck.headline(LinkCheck.check("https://www.example.com/x"))) // pinned: the bold name is the registrable site (Q2-1)
    }

    /** U-Q5: every warning says what to do or what it means for the person, not only a code. */
    @Test fun warningsSayWhatToDo() {
        for (w in listOf(LinkCheck.HTTP, LinkCheck.IP, LinkCheck.PUNYCODE, LinkCheck.MIXED, LinkCheck.PORT, LinkCheck.SHORT, LinkCheck.OTHER_SCHEME))
            assertTrue(w, Regex("don.t|only if|normal|real shops", RegexOption.IGNORE_CASE).containsMatchIn(w))
    }

    /** U-Q8: everything under the limit really makes a code (2953 was level L, which is never used). */
    @Test fun textUpToTheLimitMakesACode() {
        assertNotNull(Codes.matrix("a".repeat(Codes.MAX_BYTES), false))
        assertNotNull(Codes.matrix("a".repeat(Codes.MAX_BYTES_H), true))
        assertNotNull(Codes.matrix("é".repeat(Codes.MAX_BYTES_H / 2), true))
    }

    /** U-Q9: a date that doesn't exist is refused, never rolled over; the picker's dates round-trip. */
    @Test fun eventDatesAreStrict() {
        assertEquals("", eventContent("Party", "2026-02-31", "", "", "", "")) // pinned: an impossible date makes no event
        assertTrue(eventContent("Party", "2026-12-15", "20:30", "2", "", "").contains("BEGIN:VEVENT"))
        assertEquals("", eventContent("Party", "2026-12-15", "25:99", "2", "", "")) // pinned: an impossible time makes no event
        for (d in listOf("2026-01-01", "2026-12-31", "2024-02-29")) assertEquals(d, utcMsToIso(isoToUtcMs(d)!!))
    }

    /** U-Q13: a deleted entry comes back in its place. */
    @Test fun deleteCanBeUndone() {
        val dir = java.nio.file.Files.createTempDirectory("qr-undo").toFile()
        val h = History(File(dir, "history"), File(dir, "history-setting"))
        h.add("one", "text", "QR_CODE", made = false, now = 1)
        h.add("two", "text", "QR_CODE", made = false, now = 2)
        h.add("three", "text", "QR_CODE", made = false, now = 3)
        val two = h.all().first { it.text == "two" }
        h.remove(two)
        assertEquals(listOf("three", "one"), h.all().map { it.text }) // pinned: newest first
        h.restore(two)
        assertEquals(listOf("three", "two", "one"), h.all().map { it.text }) // pinned: back in its place by time
        h.restore(two)
        assertEquals(3, h.all().size)
    }

    /** U-Q19: barcode kinds in words, not the library's names. */
    @Test fun formatNamesAreWords() {
        for (f in Decode.FORMATS) assertFalse(formatName(f), formatName(f).contains('_') || formatName(f) == f.name)
    }

    /** U-Q2: the quarter turn keeps every byte, in the turned place. */
    @Test fun rotatedFrameKeepsItsPixels() {
        // 3 wide, 2 high, row stride 4 (one padding byte a row)
        val y = byteArrayOf(1, 2, 3, 0, 4, 5, 6, 0)
        val r = Decode.rotated(y, 4, 3, 2)
        // turned clockwise: 2 wide, 3 high
        assertEquals(listOf<Byte>(4, 1, 5, 2, 6, 3), r.toList()) // pinned: a clockwise quarter turn
    }

    /** U-Q1 / U-Q4: every screen survives being saved and restored. */
    /** U-Q11: a made code opened from History goes back into Make with the same fields, so it makes the same code. */
    @Test fun madeCodesRefillMake() {
        val made = listOf(
            "link" to mapOf("url" to "https://jurisgames.com/a?b=c"),
            "text" to mapOf("text" to "just some words"),
            "wifi" to mapOf("ssid" to "Casa;1", "password" to "p\\a:ss"),
            "contact" to mapOf("name" to "Ana Pérez", "phone" to "+34600111222", "email" to "ana@example.com", "org" to "Juris", "web" to "https://example.com", "address" to "Calle 1"),
            "event" to mapOf("title" to "Cena", "date" to "2026-12-24", "time" to "21:30", "hours" to "2.5", "where" to "Casa", "details" to "traer postre"),
            "event" to mapOf("title" to "Feriado", "date" to "2026-12-25", "time" to "", "hours" to "", "where" to "", "details" to ""),
            "phone" to mapOf("number" to "+34600111222"),
            "sms" to mapOf("number" to "+34600111222", "message" to "hola: ¿vienes?"),
            "email" to mapOf("to" to "ana@example.com", "subject" to "Hola", "message" to "Una línea"),
        )
        // west and east of UTC: an all-day date came back a day early west of it (ZXing reads it as midnight GMT)
        val home = java.util.TimeZone.getDefault()
        try {
            for (tz in listOf("America/Argentina/Buenos_Aires", "Asia/Tokyo", "UTC")) {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(tz))
                for ((kind, fields) in made) for (sec in listOf("WPA", "WEP")) for (hidden in listOf(false, true)) {
                    if (kind != "wifi" && (sec != "WPA" || hidden)) continue
                    val code = makeContent(kind, fields, sec, hidden)
                    assertTrue("$kind makes something", code.isNotEmpty())
                    val r = Refill.of(code)
                    assertNotNull("$kind refills", r)
                    assertEquals(kind, r!!.kind)
                    assertEquals("$tz $kind $sec $hidden", code, makeContent(r.kind, r.fields, r.security, r.hidden)) // pinned: a refilled Make makes the same code
                }
            }
        } finally {
            java.util.TimeZone.setDefault(home)
        }
        assertNull(Refill.of("geo:40.4,-3.7")) // pinned: Make can't make a map place
    }

    /** An all-day event shows its own date in any time zone, and a one-day event shows no end. */
    @Test fun allDayEventsShowTheirDate() {
        val home = java.util.TimeZone.getDefault()
        val homeLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.US)
            for (tz in listOf("America/Argentina/Buenos_Aires", "Asia/Tokyo", "UTC")) {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(tz))
                val one = Payload.of(makeContent("event", mapOf("title" to "Feriado", "date" to "2026-12-25"), "WPA", false)) as Payload.Event
                val (s1, e1) = eventWhen(one)
                assertTrue("$tz: $s1", s1.contains("Dec 25"))
                assertEquals("$tz one day has no end", "", e1) // pinned: a one-day event shows no end
                val two = Payload.of("BEGIN:VEVENT\r\nSUMMARY:Viaje\r\nDTSTART;VALUE=DATE:20261224\r\nDTEND;VALUE=DATE:20261227\r\nEND:VEVENT") as Payload.Event
                val (s2, e2) = eventWhen(two)
                assertTrue("$tz: $s2 - $e2", s2.contains("Dec 24") && e2.contains("Dec 26"))
            }
        } finally {
            java.util.TimeZone.setDefault(home)
            java.util.Locale.setDefault(homeLocale)
        }
    }

    @Test fun screensSurviveSaving() {
        val scope = androidx.compose.runtime.saveable.SaverScope { true }
        for (s in listOf(Screen.Home, Screen.Make(), Screen.Make("WIFI:S:x;;"), Screen.History, Screen.Result("WIFI:S:x;;", BarcodeFormat.QR_CODE, true, made = true), Screen.Result("123", BarcodeFormat.EAN_13))) {
            val saved = with(ScreenSaver) { scope.save(s) }!!
            assertEquals(s, ScreenSaver.restore(saved))
        }
    }
}
