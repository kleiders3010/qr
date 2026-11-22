package com.jurisgames.qr

import com.google.zxing.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** What must stay true: what we make reads back as the same thing, links are shown honestly, history stays local and safe.
 *  pinned: the literals compared here are inputs that must come back unchanged (round trips), formats phones read
 *  (WIFI:, vCard, VEVENT, tel:, SMSTO:, mailto:) and the spreadsheet formula guard (APP-RULES safety 15). */
class CoreTest {
    private fun roundTrip(text: String, logo: Boolean = false): String? {
        val m = Codes.matrix(text, logo)
        val (side, px) = Codes.pixels(m, 6, 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), logo)
        return Codes.read(side, px)
    }

    @Test fun madeCodesReadBack() {
        for (t in listOf("hello", "https://jurisgames.com/a?b=c", "ñandú ☕ 日本", "x".repeat(800))) {
            assertEquals(t, roundTrip(t))
            assertEquals(t, roundTrip(t, logo = true))
        }
    }

    @Test fun wifiRoundTrip() {
        val s = Content.wifi("My;Net,\"x\":", "p\\a;ss", "WPA", hidden = true)
        val p = Payload.of(s) as Payload.Wifi
        assertEquals("My;Net,\"x\":", p.ssid)
        assertEquals("p\\a;ss", p.password)
        assertEquals("WPA", p.security)
        assertTrue(p.hidden)
        val open = Payload.of(Content.wifi("Cafe", "", "WPA", false)) as Payload.Wifi
        assertEquals("nopass", open.security)
    }

    @Test fun contactRoundTrip() {
        val s = Content.contact("Ana María Pérez", "+54 11 5555-0000", "ana@example.com", "Juris, Inc; x", "https://example.com", "Calle 1, Rosario")
        val p = Payload.of(s) as Payload.Contact
        assertTrue(p.name, p.name.contains("Ana María") && p.name.contains("Pérez"))
        assertEquals(listOf("+54 11 5555-0000"), p.phones)
        assertEquals(listOf("ana@example.com"), p.emails)
        assertEquals("Juris, Inc; x", p.org)
    }

    @Test fun eventRoundTrip() {
        val start = 1_800_000_000_000L
        val p = Payload.of(Content.event("Meet; up", start, start + 3_600_000, false, "Plaza", "Bring, things")) as Payload.Event
        assertEquals("Meet; up", p.title)
        assertEquals(start, p.startMs)
        assertEquals(start + 3_600_000, p.endMs)
        assertEquals("Plaza", p.location)
    }

    @Test fun otherKinds() {
        assertEquals("+5411", (Payload.of(Content.phone("+54 (11)")) as Payload.Phone).number)
        val sms = Payload.of(Content.sms("+5411", "hola: che")) as Payload.Sms
        assertEquals("+5411", sms.number)
        assertEquals("hola: che", sms.body)
        val mail = Payload.of(Content.email("a@b.com", "Hi there", "x & y")) as Payload.Email
        assertEquals("a@b.com", mail.to)
        assertEquals("Hi there", mail.subject)
        assertEquals("x & y", mail.body)
        assertTrue(Payload.of("geo:-34.6,-58.4") is Payload.Geo)
        assertTrue(Payload.of("7790001001234", BarcodeFormat.EAN_13) is Payload.Product)
        assertTrue(Payload.of("just words") is Payload.Text)
        assertEquals("https://example.com", Content.link(" example.com "))
        assertEquals("http://x.org", Content.link("http://x.org"))
    }

    @Test fun linkCheckShowsTheRealPlace() {
        val ok = LinkCheck.check("https://www.mercadolibre.com.ar/x")
        assertTrue(ok.opens)
        assertEquals("www.mercadolibre.com.ar", ok.site) // pinned: safety round 6 Q6, the whole host is shown in bold
        assertTrue(ok.warnings.isEmpty())

        val at = LinkCheck.check("https://www.bank.com@evil.example/login")
        assertEquals("evil.example", at.host) // pinned: the real host is the one after the @
        assertTrue(LinkCheck.USERINFO in at.warnings)

        assertTrue(LinkCheck.HTTP in LinkCheck.check("http://example.com").warnings)
        assertTrue(LinkCheck.PUNYCODE in LinkCheck.check("https://xn--pple-43d.com").warnings)
        assertTrue(LinkCheck.MIXED in LinkCheck.check("https://pаypal.com").warnings) // Cyrillic а
        assertTrue(LinkCheck.IP in LinkCheck.check("https://192.168.0.1/admin").warnings)
        assertTrue(LinkCheck.SHORT in LinkCheck.check("https://bit.ly/abc").warnings)
        for (bad in listOf("javascript:alert(1)", "intent://x#Intent;end", "file:///sdcard/a", "content://x/y", "not a link")) {
            assertFalse(bad, LinkCheck.check(bad).opens)
        }
    }

    /**
     * Safety round 6 (APP-RULES 75-77): each promised warning against the ways around it. A probe either gets its
     * warning or does not open at all; nothing slips through as a clean link.
     */
    @Test fun linkWarningsHoldAgainstEvasions() {
        fun warned(url: String, w: String) {
            val r = LinkCheck.check(url)
            assertTrue("$url -> ${r.opens} ${r.warnings}", (r.opens && w in r.warnings) || (!r.opens && r.warnings == listOf(LinkCheck.BROKEN)))
        }
        // pinned: Q1 lookalike letters outside Cyrillic/Greek
        for (u in listOf("https://ɡoogle.com/", "https://paypaı.com/", "https://gօօgle.com/", "https://bücher.example/", "https://xn--pple-43d.com"))
            warned(u, LinkCheck.PUNYCODE)
        val g = LinkCheck.check("https://ɡoogle.com/")
        assertTrue(g.letters, g.letters.contains("U+0261"))
        assertTrue(g.ascii, g.ascii.startsWith("xn--") && g.ascii != g.host)
        assertTrue(g.target, g.target.startsWith("https://xn--") && g.target.all { it.code < 128 })
        // pinned: Q2 the @ and the port survive a host the loose parser can't read
        for (u in listOf("https://www.paypal.com@bücher.example/", "https://www.bank.com@evil.example/login", "https://accounts.google.com@login@evil.example/"))
            warned(u, LinkCheck.USERINFO)
        assertFalse(LinkCheck.check("https://accounts.google.com@login@evil.example/").opens)
        assertEquals("bücher.example", LinkCheck.check("https://www.paypal.com@bücher.example/").host)
        for (u in listOf("https://bücher.example:8443/", "https://example.com:8443/")) warned(u, LinkCheck.PORT)
        // pinned: Q5 number addresses in every notation
        for (u in listOf("https://3232235777/", "https://0x7f000001/", "https://0177.0.0.1/", "https://192.168.1/", "https://0xC0.0xA8.0x1.0x1/", "https://[::1]/", "https://192.168.0.1/"))
            warned(u, LinkCheck.IP)
        assertEquals("192.168.0.1", LinkCheck.check("https://192.168.0.1/").site) // the whole number, never "0.1"
        assertTrue(LinkCheck.isNumber("0xc0.0xa8.0x1.0x1") && LinkCheck.isNumber("3232235777") && !LinkCheck.isNumber("example.com"))
        // pinned: Q6 shared hosting shows the whole host; QR redirectors are short links
        assertEquals("paypal-login.github.io", LinkCheck.check("https://paypal-login.github.io/").site)
        for (u in listOf("https://linktr.ee/x", "https://qr.link/x", "https://www.bit.ly/x")) warned(u, LinkCheck.SHORT)
        // pinned: Q15 hidden and direction-changing characters
        warned("https://evil.example/‮moc.elgoog//:sptth", Visible.WARNING)
        warned("https://example.com/a​b", Visible.WARNING)
        // Things that never open.
        for (bad in listOf("https://g%6Fogle.com/", "https://exa mple.com/", "https://example.com:80x/", "https://[::1/", "https:evil", "https:///evil"))
            assertFalse(bad, LinkCheck.check(bad).opens)
        // Clean names stay clean.
        for (u in listOf("https://example.com/", "https://sub.example.co.uk/a?b=c#d", "http://localhost:80/"))
            assertTrue(u, LinkCheck.check(u).warnings.none { it == LinkCheck.PUNYCODE || it == LinkCheck.IP || it == LinkCheck.USERINFO || it == LinkCheck.PORT })
    }

    @Test fun hiddenCharactersAreShown() {
        assertEquals("a[U+202E]b", Visible.show("a‮b")) // pinned: Q15 the mark's form
        assertEquals("line\nnext\ttab", Visible.show("line\nnext\ttab"))
        assertTrue(Visible.has("x‍y") && Visible.has("﻿=1") && !Visible.has("ñandú 😀"))
    }

    /** pinned: safety round 6 Q3, Q4, Q7 */
    @Test fun scannedPartsCantHideWhatTheyDo() {
        assertTrue((Payload.of("tel:**21*5491100000000#") as Payload.Phone).isCommand)
        assertFalse((Payload.of("tel:+5491100000000") as Payload.Phone).isCommand)
        val w = Payload.of("WIFI:T:WPA;S:Home;P:secret123;;")
        assertEquals("Home", w.summary())
        assertFalse(w.summary().contains("secret"))
        val m = Payload.of("mailto:victim%40x.com%3Fbcc%3Dattacker%40evil.example%26body%3Dhidden") as Payload.Email
        assertTrue(m.to, Content.oddAddress(m.to))
        val link = Content.email(m.to, "", "")
        assertFalse(link, link.contains("?") || link.contains("&"))
        assertEquals("mailto:a@b.com,c@d.org?subject=Hi", Content.email("a@b.com, c@d.org", "Hi", ""))
    }

    /** pinned: a deceptive link is shown as a link with its warnings, never as plain text (ZXing alone drops it to text). */
    @Test fun deceptiveLinksStillGetTheLinkCheck() {
        val raw = "http://mybank.com-secure-login@203.0.113.7/verify"
        val p = Payload.of(raw)
        assertTrue("$p", p is Payload.Link)
        val w = LinkCheck.check((p as Payload.Link).url).warnings
        assertTrue("$w", LinkCheck.USERINFO in w && LinkCheck.IP in w && LinkCheck.HTTP in w)
        assertTrue(Payload.of("just some words") is Payload.Text)
    }

    @Test fun colors() {
        assertNull(Codes.colorProblem(0xFF000000.toInt(), 0xFFFFFFFF.toInt()))
        assertNotNull(Codes.colorProblem(0xFFFFFFFF.toInt(), 0xFF000000.toInt()))
        assertNotNull(Codes.colorProblem(0xFFAAAAAA.toInt(), 0xFFFFFFFF.toInt()))
        assertNull(Codes.colorProblem(0xFF1B3A6B.toInt(), 0xFFFFF8E1.toInt()))
    }

    @Test fun svgHasEverySquare() {
        val m = Codes.matrix("svg", false)
        val svg = Codes.svg(m, 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), null)
        var dark = 0
        for (y in 0 until m.height) for (x in 0 until m.width) if (m.get(x, y)) dark++
        assertEquals(dark, Regex("h1v1h-1z").findAll(svg).count())
        assertTrue(svg.startsWith("<svg") && svg.endsWith("</svg>"))
    }

    @Test fun historyIsLocalCappedAndSafe() {
        val dir = Files.createTempDirectory("qr-history").toFile()
        val h = History(java.io.File(dir, "history"), java.io.File(dir, "history-setting"))
        h.add("one\ttab\nline", "text", "QR_CODE", made = false, now = 1)
        h.add("=HYPERLINK(\"x\")", "text", "QR_CODE", made = true, now = 2)
        h.add("one\ttab\nline", "text", "QR_CODE", made = false, now = 3)
        val fresh = History(java.io.File(dir, "history"), java.io.File(dir, "history-setting"))
        assertEquals(2, fresh.all().size)
        assertEquals("one\ttab\nline", fresh.all()[0].text)
        assertTrue(fresh.csv().contains("\"'=HYPERLINK(\"\"x\"\")\""))
        assertEquals(1, fresh.search("TAB one").size)
        fresh.enabled = false
        fresh.add("not kept", "text", "QR_CODE", false)
        assertEquals(2, fresh.all().size)
        fresh.enabled = true
        for (i in 0 until History.MAX + 5) fresh.add("n$i", "text", "QR_CODE", false, now = 10L + i)
        assertEquals(History.MAX, fresh.all().size)
        fresh.clear()
        assertTrue(fresh.all().isEmpty())
    }

    /** APP-RULES 70: a formula after leading spaces or a hidden control character is neutralised too. */
    @Test fun csvFormulasAfterSpacesOrControls() {
        val dir = Files.createTempDirectory("qr-csv").toFile()
        val h = History(java.io.File(dir, "history"), java.io.File(dir, "history-setting"))
        h.add("  =1+1", "text", "QR_CODE", made = true, now = 1)
        h.add("\u0001@SUM(A1)", "text", "QR_CODE", made = true, now = 2)
        val csv = h.csv()
        assertTrue(csv, csv.contains("\"'  =1+1\"") && csv.contains("\"'@SUM(A1)\"") && !csv.contains("\u0001"))
    }
}
