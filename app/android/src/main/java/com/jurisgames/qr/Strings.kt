package com.jurisgames.qr

import java.util.Locale

/**
 * The app's words in the device's language: English, or Spanish (Latin American, "vos") when the device is set to
 * Spanish. Each English text is its own key; anything not in [ES] stays English. Placeholders are `{name}`.
 */
object Lang {
    @Volatile var spanish: Boolean = Locale.getDefault().language == "es"
}

fun tr(en: String): String = if (Lang.spanish) ES[en] ?: en else en

fun tr(en: String, vararg args: Pair<String, Any?>): String {
    var s = tr(en)
    for ((k, v) in args) s = s.replace("{$k}", v.toString())
    return s
}

val ES: Map<String, String> = mapOf(
    "QR" to "QR",
)
