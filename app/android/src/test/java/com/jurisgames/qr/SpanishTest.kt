package com.jurisgames.qr

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every text the app shows has its Spanish (the app is EN + ES from the first release). */
class SpanishTest {
    @Test fun everyTextIsTranslated() {
        val dir = File("src/main/java/com/jurisgames/qr")
        val keys = mutableSetOf<String>()
        for (f in dir.listFiles()!!.filter { it.name.endsWith(".kt") && it.name != "Strings.kt" }) {
            val s = f.readText()
            Regex("""(?<![A-Za-z])tr\("((?:[^"\\]|\\.)*)"""").findAll(s).forEach { keys += it.groupValues[1] }
        }
        // Warnings and color problems are shown through tr(constant).
        keys += listOf(LinkCheck.NOT_WEB, LinkCheck.OTHER_SCHEME, LinkCheck.HTTP, LinkCheck.USERINFO, LinkCheck.IP, LinkCheck.PUNYCODE,
            LinkCheck.MIXED, LinkCheck.SHORT, LinkCheck.LONG, LinkCheck.PORT)
        keys += listOfNotNull(Codes.colorProblem(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), Codes.colorProblem(0xFFAAAAAA.toInt(), 0xFFFFFFFF.toInt()))
        keys += Feedback.kinds.map { it.second }
        val missing = keys.filter { !ES.containsKey(it.replace("\\\"", "\"")) }
        assertTrue("no Spanish for: $missing", missing.isEmpty())
    }
}
