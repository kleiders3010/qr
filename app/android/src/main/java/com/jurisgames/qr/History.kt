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
        const val BACKUP_HEAD = "QR history backup 1"
        /** A full history is under 1000 x ~4 KB of base64; anything far bigger isn't ours. */
        const val MAX_IMPORT_CHARS = 8_000_000
        /** The longest text a code holds is 2953 bytes; a little room for UTF-8. */
        const val MAX_TEXT = 4096
        val FORMATS: Set<String> = com.google.zxing.BarcodeFormat.values().map { it.name }.toSet()
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

    /** Undo of [remove] (UX review U-Q13): the entry goes back in its place by time, even with the history off. */
    @Synchronized
    fun restore(e: Entry): Boolean = runCatching {
        if (all().any { it == e }) return true
        save((all() + e).sortedByDescending { it.time }.take(MAX))
    }.isSuccess

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

    /**
     * U-Q25: the whole history as a file this app reads back exactly (the spreadsheet drops hidden characters and
     * marks formula starts). Backup to the cloud and phone-to-phone copy stay off (it holds Wi-Fi passwords): the
     * person keeps this file where they choose.
     */
    fun backup(): String = BACKUP_HEAD + "\n" + all().asReversed().joinToString("") { line(it) + "\n" }

    /**
     * Adds what a backup (or this app's spreadsheet) holds to the history: the same code already here keeps the
     * newer of the two, at most [MAX] entries stay. The file is someone else's input: kinds are worked out again,
     * unknown formats become QR, overlong texts and impossible times are skipped. Null when it isn't one of ours.
     */
    @Synchronized
    fun restoreFrom(content: String, now: Long = System.currentTimeMillis()): Int? = runCatching {
        if (content.length > MAX_IMPORT_CHARS) return null
        val got = (if (content.startsWith(BACKUP_HEAD)) content.lineSequence().drop(1).mapNotNull(::parse).toList()
                   else fromCsv(content) ?: return null)
            .filter { it.text.isNotEmpty() && it.text.length <= MAX_TEXT && it.time in 1..(now + 86_400_000L) }
            .map { e ->
                val format = if (FORMATS.contains(e.format)) e.format else "QR_CODE"
                // the kind is worked out again from the text, as a scan would, never taken from the file
                e.copy(format = format, kind = Payload.of(e.text, com.google.zxing.BarcodeFormat.valueOf(format)).kind)
            }
        val mine = all()
        val fresh = got.filter { g -> mine.none { it.text == g.text && it.made == g.made && it.time >= g.time } }
        val merged = (fresh + mine.filterNot { m -> fresh.any { it.text == m.text && it.made == m.made } })
            .sortedByDescending { it.time }.distinctBy { it.text to it.made }.take(MAX)
        save(merged)
        fresh.count { f -> merged.any { it === f } }
    }.getOrNull()

    /** This app's spreadsheet back into entries (null when it isn't one). */
    private fun fromCsv(s: String): List<Entry>? {
        val rows = csvRows(s)
        if (rows.isEmpty() || rows[0] != listOf("time", "how", "kind", "format", "text")) return null
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
        return rows.drop(1).mapNotNull { r ->
            if (r.size != 5) return@mapNotNull null
            val t = runCatching { f.parse(r[0])?.time }.getOrNull() ?: return@mapNotNull null
            // the spreadsheet put ' before a formula start; take it off again
            val text = if (r[4].startsWith("'") && r[4].drop(1).trimStart().firstOrNull()?.let { it in "=+-@" } == true) r[4].drop(1) else r[4]
            Entry(t, r[1] == "made", r[2], r[3], text)
        }
    }

    private fun csvRows(s: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                quoted && c == '"' && s.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row.add(cell.toString()); cell.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && s.getOrNull(i + 1) == '\n') i++
                    row.add(cell.toString()); cell.clear(); rows.add(row); row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows
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
