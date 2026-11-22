package com.jurisgames.qr

/**
 * Hidden and direction-changing characters (APP-RULES 15 and 79) in text someone else wrote: on screen each one is
 * shown as a visible mark like [U+202E], so a scanned value can't look like something it isn't. Copy, Open and the
 * other apps still get the real text.
 */
object Visible {
    const val WARNING = "This code has hidden characters, shown here as [U+…]. They can make text look like something else."

    fun isHidden(cp: Int): Boolean {
        if (cp == '\n'.code || cp == '\t'.code || cp == '\r'.code) return false
        return when (Character.getType(cp).toByte()) {
            Character.FORMAT, Character.CONTROL, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR,
            Character.UNASSIGNED, Character.PRIVATE_USE -> true
            // Blank-looking letters (Hangul fillers) and the combining grapheme joiner.
            else -> cp == 0x115F || cp == 0x1160 || cp == 0x3164 || cp == 0xFFA0 || cp == 0x034F
        }
    }

    fun has(s: String): Boolean = s.codePoints().anyMatch { isHidden(it) }

    fun show(s: String): String {
        if (!has(s)) return s
        val b = StringBuilder()
        s.codePoints().forEach { cp -> if (isHidden(cp)) b.append("[U+%04X]".format(cp)) else b.appendCodePoint(cp) }
        return b.toString()
    }
}
