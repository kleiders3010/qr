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
    // screens and menu
    "QR" to "QR",
    "What the code says" to "Qué dice el código",
    "Make a code" to "Crear un código",
    "History" to "Historial",
    "Scan a picture" to "Leer de una imagen",
    "Send feedback" to "Enviar comentarios",
    "About" to "Acerca de",
    "About QR" to "Acerca de QR",
    "Close" to "Cerrar",
    "Version {v}. Free and open source (Apache-2.0), no ads, no account. Codes are read with ZXing. Your history stays on this phone. For many more barcode formats, see Binary Eye." to
        "Versión {v}. Gratis y de código abierto (Apache-2.0), sin anuncios, sin cuenta. Los códigos se leen con ZXing. Tu historial queda en este teléfono. Para muchos más tipos de códigos de barras, mirá Binary Eye.",
    "Scan a code" to "Escanear un código",
    "Read a code from a picture or screenshot" to "Leer un código de una imagen o captura",
    "Recent" to "Recientes",
    "All" to "Todos",
    "History is off: codes you scan or make aren't kept." to "El historial está apagado: no se guardan los códigos que escaneás o creás.",
    "Point the camera at a code" to "Apuntá la cámara a un código",
    "Scanning needs the camera. You can allow it in the phone's settings, or read a code from a picture." to
        "Para escanear hace falta la cámara. Podés permitirla en los ajustes del teléfono, o leer un código de una imagen.",
    "No code found in this picture." to "No se encontró ningún código en esta imagen.",
    // kinds
    "Link" to "Enlace", "Wi-Fi" to "Wi-Fi", "Contact" to "Contacto", "Event" to "Evento", "Phone" to "Teléfono",
    "Text message" to "Mensaje de texto", "Email" to "Email", "Place" to "Lugar", "Product" to "Producto", "Text" to "Texto",
    "Made" to "Creado", "Scanned" to "Escaneado",
    // result
    "No app on this phone can do that." to "Ninguna app de este teléfono puede hacer eso.",
    "It goes to" to "Lleva a",
    "Open" to "Abrir", "Copied" to "Copiado", "Copy" to "Copiar", "Share" to "Compartir",
    "Network" to "Red", "Security" to "Seguridad", "Open (no password)" to "Abierta (sin contraseña)",
    "Hide" to "Ocultar", "Show" to "Mostrar",
    "Join this network" to "Conectarse a esta red", "Password copied" to "Contraseña copiada", "Copy password" to "Copiar contraseña",
    "Name" to "Nombre", "Company" to "Empresa", "Address" to "Dirección", "Website" to "Sitio web", "Note" to "Nota",
    "Add to contacts" to "Agregar a contactos",
    "Starts" to "Empieza", "Ends" to "Termina", "Where" to "Dónde", "Details" to "Detalles", "Add to calendar" to "Agregar al calendario",
    "Number" to "Número", "Call" to "Llamar", "To" to "Para", "Message" to "Mensaje", "Write the message" to "Escribir el mensaje",
    "Subject" to "Asunto", "Write the email" to "Escribir el email", "Search" to "Buscar", "Show on a map" to "Ver en un mapa",
    "Product number" to "Número de producto", "Search the web for it" to "Buscarlo en la web",
    "Copy all" to "Copiar todo", "Read from: {f}" to "Leído de: {f}",
    // link check
    "This isn't a web link, so it won't open." to "No es un enlace web, así que no se abre.",
    "Links of this kind ({scheme}:) don't open from here." to "Los enlaces de este tipo ({scheme}:) no se abren desde acá.",
    "Not secure (http): others on the network can see or change the page." to "No es seguro (http): otros en la red pueden ver o cambiar la página.",
    "The link has text before an @ that looks like an address. It really goes to {host}." to
        "El enlace tiene texto antes de una @ que parece una dirección. En realidad lleva a {host}.",
    "It goes to a bare number address, not a named site." to "Lleva a una dirección numérica, no a un sitio con nombre.",
    "The name uses letters from other alphabets. Check it carefully: it may imitate another site." to
        "El nombre usa letras de otros alfabetos. Revisalo bien: puede imitar a otro sitio.",
    "The name mixes alphabets. Check it carefully: it may imitate another site." to "El nombre mezcla alfabetos. Revisalo bien: puede imitar a otro sitio.",
    "This is a short link: where it really leads is only shown after opening." to "Es un enlace acortado: adónde lleva de verdad solo se ve al abrirlo.",
    "This link is unusually long." to "Este enlace es inusualmente largo.",
    "It uses an unusual port ({port})." to "Usa un puerto poco común ({port}).",
    // make
    "Web address" to "Dirección web", "Network name" to "Nombre de la red",
    "Password (empty for an open network)" to "Contraseña (vacía para una red abierta)", "Hidden network" to "Red oculta",
    "Date (YYYY-MM-DD)" to "Fecha (AAAA-MM-DD)", "Time (HH:MM, empty for all day)" to "Hora (HH:MM, vacía para todo el día)",
    "How many hours" to "Cuántas horas", "Phone number" to "Número de teléfono", "Email address" to "Dirección de email",
    "Colors" to "Colores",
    "The squares must be darker than the background, or many cameras won't read it." to
        "Los cuadraditos tienen que ser más oscuros que el fondo, o muchas cámaras no lo van a leer.",
    "These colors are too close. Pick a darker color or a lighter background." to "Estos colores se parecen demasiado. Elegí un color más oscuro o un fondo más claro.",
    "Add a picture in the middle" to "Agregar una imagen en el centro", "Change the picture" to "Cambiar la imagen", "Remove it" to "Quitarla",
    "Fill in the fields to see your code." to "Completá los campos para ver tu código.",
    "Too much for one code ({n} of {max} bytes). Shorten it, or remove the picture." to
        "Es demasiado para un código ({n} de {max} bytes). Acortalo o quitá la imagen.",
    "Your code" to "Tu código",
    "Checked: it reads back correctly." to "Revisado: se lee bien.",
    "This code didn't read back. Try other colors, a smaller picture, or less text." to "Este código no se pudo leer. Probá otros colores, una imagen más chica o menos texto.",
    "Saved to Pictures/QR" to "Guardado en Imágenes/QR", "Couldn't save it" to "No se pudo guardar", "Save picture" to "Guardar imagen",
    "Saved" to "Guardado", "Save as SVG" to "Guardar como SVG",
    "Codes made here never expire and don't go through any server: what you typed is inside the picture." to
        "Los códigos creados acá no vencen nunca y no pasan por ningún servidor: lo que escribiste está dentro de la imagen.",
    // history
    "Keep a history on this phone" to "Guardar un historial en este teléfono",
    "Save as a spreadsheet" to "Guardar como planilla", "Clear all" to "Borrar todo",
    "Nothing here yet." to "Todavía no hay nada.", "Nothing matches." to "Nada coincide.",
    "Clear" to "Borrar", "Cancel" to "Cancelar", "Clear the history?" to "¿Borrar el historial?",
    "Every code in the history is removed from this phone. This can't be undone." to "Se borran del teléfono todos los códigos del historial. No se puede deshacer.",
    // feedback
    "Something isn't working" to "Algo no funciona", "An idea" to "Una idea", "Something else" to "Otra cosa",
    "Too many reports from this network today. Please try again tomorrow, or write to hello@jurisgames.com." to
        "Demasiados mensajes desde esta red hoy. Probá de nuevo mañana o escribí a hello@jurisgames.com.",
    "That email doesn't look right." to "Ese email no parece correcto.",
    "It couldn't be sent. Check your connection and try again, or write to hello@jurisgames.com." to
        "No se pudo enviar. Revisá la conexión y probá de nuevo, o escribí a hello@jurisgames.com.",
    "Thanks! Your message was sent." to "¡Gracias! Tu mensaje se envió.",
    "Write a few words first." to "Escribí algunas palabras primero.",
    "Sending…" to "Enviando…", "Send" to "Enviar",
    "It goes to the person who makes this app. Thanks for helping!" to "Le llega a quien hace esta app. ¡Gracias por ayudar!",
    "What happened, or what would make it better?" to "¿Qué pasó, o qué la haría mejor?",
    "Your email, if you'd like an answer (optional)" to "Tu email, si querés una respuesta (opcional)",
    "Include technical details" to "Incluir detalles técnicos",
    "App version, system, and how many codes are in the history. Never what the codes say." to
        "Versión de la app, sistema y cuántos códigos hay en el historial. Nunca lo que dicen los códigos.",
    "Hide what's sent" to "Ocultar qué se envía", "See what's sent" to "Ver qué se envía",
)
