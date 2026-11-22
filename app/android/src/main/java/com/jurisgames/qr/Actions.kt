package com.jurisgames.qr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** What the result screen's buttons do. Nothing here runs by itself: each is one press of the person's. */
object Actions {
    private fun go(ctx: Context, i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) {
        false
    }

    fun copy(ctx: Context, label: String, text: String, secret: Boolean = false) {
        val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, text)
        if (secret && Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        cm.setPrimaryClip(clip)
    }

    fun shareText(ctx: Context, text: String) =
        go(ctx, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))

    /** Only web links (LinkCheck decides); opened in the browser the person chose. */
    fun open(ctx: Context, url: String): Boolean {
        val r = LinkCheck.check(url)
        return r.opens && go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(r.target)).addCategory(Intent.CATEGORY_BROWSABLE))
    }

    /** Android 11+: the system's own "Save this network?" sheet, no permission needed. Older phones: Wi-Fi settings. */
    fun joinWifi(ctx: Context, w: Payload.Wifi): Boolean {
        if (Build.VERSION.SDK_INT >= 30) {
            val sec = w.security.uppercase()
            if (sec !in setOf("WPA", "WPA2", "WPA3", "SAE", "NOPASS", "")) return go(ctx, Intent(Settings.ACTION_WIFI_SETTINGS))
            // Safety round 6 Q14: the builder throws on a password that isn't plain ASCII (or an odd name); every call
            // is inside the catch, and a refusal opens Wi-Fi settings (the screen offers "Copy password").
            val s = runCatching {
                val b = WifiNetworkSuggestion.Builder().setSsid(w.ssid).setIsHiddenSsid(w.hidden)
                when (sec) {
                    "WPA", "WPA2" -> b.setWpa2Passphrase(w.password)
                    "WPA3", "SAE" -> b.setWpa3Passphrase(w.password)
                }
                b.build()
            }.getOrNull() ?: return go(ctx, Intent(Settings.ACTION_WIFI_SETTINGS))
            return go(ctx, Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(s)))
        }
        return go(ctx, Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    fun addContact(ctx: Context, c: Payload.Contact): Boolean {
        val i = Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
            .putExtra(ContactsContract.Intents.Insert.NAME, c.name)
        c.safePhones.firstOrNull()?.let { i.putExtra(ContactsContract.Intents.Insert.PHONE, it) } // never a phone command (Q2-2)
        c.emails.firstOrNull()?.let { i.putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
        if (c.org.isNotEmpty()) i.putExtra(ContactsContract.Intents.Insert.COMPANY, c.org)
        if (c.address.isNotEmpty()) i.putExtra(ContactsContract.Intents.Insert.POSTAL, c.address)
        if (c.note.isNotEmpty()) i.putExtra(ContactsContract.Intents.Insert.NOTES, c.note)
        return go(ctx, i)
    }

    fun addEvent(ctx: Context, e: Payload.Event) = go(ctx, Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, e.title)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.startMs)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.endMs)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, e.allDay)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, e.location)
        .putExtra(CalendarContract.Events.DESCRIPTION, e.description))

    /** Opens the dialer with the number; the person presses call themselves. */
    fun dial(ctx: Context, number: String) = go(ctx, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))

    fun sms(ctx: Context, s: Payload.Sms) = go(ctx, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", s.number, null)).putExtra("sms_body", s.body))

    fun email(ctx: Context, m: Payload.Email) = go(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse(Content.email(m.to, m.subject, m.body))))

    fun map(ctx: Context, g: Payload.Geo) = go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:${g.lat},${g.lng}?q=${g.lat},${g.lng}")))

    fun search(ctx: Context, q: String) = go(ctx, Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, q))

    /** The code as a picture: [px] pixels per square, the quiet zone around it, the logo (if any) on a rounded tile. */
    fun bitmap(text: String, fg: Int, bg: Int, logo: Bitmap?, px: Int = 16): Bitmap {
        val m = Codes.matrix(text, logo != null)
        val (side, pixels) = Codes.pixels(m, px, fg, bg, logo != null)
        val b = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, true)
        if (logo != null) {
            val hole = Codes.holeRange(m.width)
            val at = (hole.first + Codes.QUIET) * px.toFloat()
            val size = (hole.last - hole.first + 1) * px.toFloat()
            val c = Canvas(b)
            c.drawRoundRect(RectF(at, at, at + size, at + size), px.toFloat(), px.toFloat(), Paint().apply { color = bg; isAntiAlias = true })
            val pad = px * 0.6f
            c.drawBitmap(logo, null, RectF(at + pad, at + pad, at + size - pad, at + size - pad), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return b
    }

    /** What the camera would see: the finished picture read back with our own decoder. */
    fun readsBack(b: Bitmap, text: String): Boolean = Decode.fromBitmap(b)?.text == text

    fun png(b: Bitmap): ByteArray = java.io.ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    /** Into Pictures/QR. Android 10+ only (no storage permission there); older ones use the save dialog. */
    fun savePicture(ctx: Context, b: Bitmap, name: String): Boolean = runCatching {
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/QR")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return false
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(png(b)) } ?: return false
        true
    }.getOrDefault(false)

    /** Safety round 6 Q10 (APP-RULES 70): shared pictures don't stay; emptied on start, before each share and on clear. */
    fun clearExports(ctx: Context) {
        runCatching { File(ctx.cacheDir, "exports").listFiles()?.forEach { it.delete() } }
    }

    fun sharePicture(ctx: Context, b: Bitmap, name: String): Boolean {
        clearExports(ctx)
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val f = File(dir, "$name.png").apply { writeBytes(png(b)) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        return go(ctx, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
    }
}
