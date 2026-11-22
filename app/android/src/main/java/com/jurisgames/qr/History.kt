package com.jurisgames.qr

import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

/**
 * Codes scanned and made, on this device only (never sent anywhere). One line per entry in [file]; newest last.
 * Can be turned off (then nothing new is kept) and cleared. At most [MAX] entries: the oldest go first.
 */
class History(private val file: File, private val settings: File) {
    /** One History per process (Q2-6): two activities (a rotation while a picture is read) must not overwrite each other. */
    object Shared {
        @Volatile private var h: History? = null
        @Synchronized fun of(filesDir: File): History = h ?: History(File(filesDir, "history"), File(filesDir, "history-setting")).also { h = it }
    }

    data class Entry(val time: Long, val made: Boolean, val kind: String, val format: String, val text: String)

    companion object {
        const val MAX = 1000
    }

    @Volatile private var cache: List<Entry>? = null

    var enabled: Boolean
        get() = !settings.exists() || settings.readText().trim() != "off"
        set(on) {
            settings.parentFile?.mkdirs()
            settings.writeText(if (on) "on" else "off")
        }

    private fun line(e: Entry) = listOf(e.time.toString(), if (e.made) "m" else "s", e.kind, e.format,
        Base64.getEncoder().encodeToString(e.text.toByteArray())).joinToString("\t")

    private fun parse(l: String): Entry? {
        val p = l.split('\t')
        if (p.size != 5) return null
        val text = runCatching { String(Base64.getDecoder().decode(p[4])) }.getOrNull() ?: return null
        return Entry(p[0].toLongOrNull() ?: return null, p[1] == "m", p[2], p[3], text)
    }

    /** Newest first. */
    fun all(): List<Entry> = cache ?: (if (file.exists()) file.readLines().mapNotNull(::parse) else emptyList()).asReversed().also { cache = it }

    private fun save(newestFirst: List<Entry>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(newestFirst.asReversed().joinToString("") { line(it) + "\n" })
        if (!tmp.renameTo(file)) { file.delete(); check(tmp.renameTo(file)) { "history not saved" } }
        cache = newestFirst
    }

    /**
     * Keeps [text] (when history is on); the same text again moves to the top instead of being listed twice.
     * False when it couldn't be written (a full phone): the scan itself still shows (Q2-6, rule 82).
     */
    @Synchronized
    fun add(text: String, kind: String, format: String, made: Boolean, now: Long = System.currentTimeMillis()): Boolean = runCatching {
        if (!enabled || text.isEmpty()) return true
        val rest = all().filterNot { it.text == text && it.made == made }
        save((listOf(Entry(now, made, kind, format, text)) + rest).take(MAX))
    }.isSuccess

    @Synchronized
    fun remove(e: Entry): Boolean = runCatching { save(all().filterNot { it == e }) }.isSuccess

    fun clear() {
        file.delete()
        cache = emptyList()
    }

    /** Entries whose text contains every word of [q] (any case). */
    fun search(q: String): List<Entry> {
        val words = q.lowercase().split(' ').filter { it.isNotBlank() }
        return all().filter { e -> words.all { e.text.lowercase().contains(it) } }
    }

    /** A spreadsheet cell: quoted, and text that starts like a formula gets a ' in front (APP-RULES safety 15). */
    private fun cell(raw: String): String {
        // Hidden control characters go; a formula start counts after leading spaces too (APP-RULES 70).
        // Q2-7: zero-width, byte-order and direction marks go too, so none can hide a formula start.
        val s = buildString { raw.codePoints().forEach { cp -> if (!Visible.isHidden(cp)) appendCodePoint(cp) } }
        val lead = s.trimStart()
        val safe = if (lead.isNotEmpty() && lead[0] in "=+-@" && lead.toDoubleOrNull() == null) "'$s" else s
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }

    fun csv(): String = buildString {
        append("time,how,kind,format,text\r\n")
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
        for (e in all()) {
            append(listOf(f.format(Date(e.time)), if (e.made) "made" else "scanned", e.kind, e.format, e.text).joinToString(",") { cell(it) })
            append("\r\n")
        }
    }
}
