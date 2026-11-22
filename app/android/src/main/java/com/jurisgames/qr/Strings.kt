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
    "Send feedback" to "Enviar comentarios",
    "About" to "Acerca de",
    "About QR" to "Acerca de QR",
    "Close" to "Cerrar", "Privacy" to "Privacidad",
    "Version {v}. Free and open source (Apache-2.0), no ads, no account. Codes are read with ZXing. Your history stays on this phone. For many more barcode formats, see Binary Eye." to
        "Versión {v}. Gratis y de código abierto (Apache-2.0), sin anuncios, sin cuenta. Los códigos se leen con ZXing. Tu historial queda en este teléfono. Para muchos más tipos de códigos de barras, mirá Binary Eye.",
    "Scan a code" to "Escanear un código",
    "Recent" to "Recientes",
    "All" to "Todos",
    "History is off: codes you scan or make aren't kept." to "El historial está apagado: no se guardan los códigos que escaneás o creás.",
    "Point the camera at a code" to "Apuntá la cámara a un código",
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
    "Links of this kind ({scheme}:) don't open from here. Copy it only if you trust who made the code." to
        "Los enlaces de este tipo ({scheme}:) no se abren desde acá. Copialo solo si confiás en quien hizo el código.",
    "This page isn't protected (http): others on the network can see or change it. Don't type passwords or card numbers there." to
        "Esta página no está protegida (http): otros en la red pueden verla o cambiarla. No escribas contraseñas ni números de tarjeta ahí.",
    "The link has text before an @ that looks like an address. It really goes to {host}: don't trust the part before the @." to
        "El enlace tiene texto antes de una @ que parece una dirección. En realidad lleva a {host}: no confíes en la parte de antes de la @.",
    "It goes to a computer by its number instead of a site name. Real shops and banks don't do this." to
        "Lleva a una computadora por su número en vez del nombre de un sitio. Los negocios y bancos de verdad no hacen esto.",
    "The name uses letters from another alphabet ({letters}). It may be pretending to be another site: don't sign in or pay there." to
        "El nombre usa letras de otro alfabeto ({letters}). Puede estar haciéndose pasar por otro sitio: no inicies sesión ni pagues ahí.",
    "This link is written in an unusual way, so it won't open. Copy it only if you trust who made the code." to
        "Este enlace está escrito de una forma rara, así que no se abre. Copialo solo si confiás en quien hizo el código.",
    "This code has hidden characters, shown here as [U+…]. They can make text look like something else: check it before you trust it." to
        "Este código tiene caracteres ocultos, mostrados acá como [U+…]. Pueden hacer que un texto parezca otra cosa: revisalo antes de confiar.",
    "Phone command, not a number" to "Comando del teléfono, no un número",
    "Codes like this change phone settings: some send all your calls to another number. Don't dial it unless you know what it does." to
        "Los códigos así cambian ajustes del teléfono: algunos desvían todas tus llamadas a otro número. No lo marques si no sabés qué hace.",
    "The address has characters (? & # = %) that aren't part of a normal address. It is passed on as one odd address, nothing more." to
        "La dirección tiene caracteres (? & # = %) que no son de una dirección normal. Se pasa como una sola dirección rara, nada más.",
    "The spreadsheet includes Wi-Fi passwords." to "La planilla incluye contraseñas de Wi-Fi.",
    "The name mixes letters from different alphabets. It may be pretending to be another site: don't sign in or pay there." to
        "El nombre mezcla letras de distintos alfabetos. Puede estar haciéndose pasar por otro sitio: no inicies sesión ni pagues ahí.",
    "This is a short link: where it really leads is only shown after opening. Open it only if you trust who made the code." to
        "Es un enlace acortado: adónde lleva de verdad solo se ve al abrirlo. Abrilo solo si confiás en quien hizo el código.",
    "This link is unusually long." to "Este enlace es inusualmente largo.",
    "It uses an unusual port number ({port}). That's normal for home devices like a router, but odd for a shop or a bank." to
        "Usa un número de puerto poco común ({port}). Es normal en aparatos de la casa como un router, pero raro en un negocio o un banco.",
    "A link in it goes to" to "Tiene un enlace que lleva a",
    "Couldn't save it to the history: the phone may be full." to "No se pudo guardar en el historial: el teléfono puede estar lleno.",
    "The address uses letters outside a–z. Check it carefully: it may imitate another address." to
        "La dirección usa letras fuera de a–z. Revisala bien: puede imitar otra dirección.",
    "This card has a phone code that changes phone settings (some send all your calls to another number). It is left out of the contact." to
        "Esta tarjeta tiene un código que cambia ajustes del teléfono (algunos desvían todas tus llamadas a otro número). Queda fuera del contacto.",
    "The real site is {site}. The words before it are only part of its address and can say anything: don't sign in or pay there unless {site} is who you expect." to
        "El sitio real es {site}. Las palabras de antes son solo parte de su dirección y pueden decir cualquier cosa: no inicies sesión ni pagues ahí salvo que {site} sea quien esperás.",
    // make
    "Web address" to "Dirección web", "Network name" to "Nombre de la red",
    "Password (empty for an open network)" to "Contraseña (vacía para una red abierta)", "Hidden network" to "Red oculta",
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
    "Saved to Pictures/QR" to "Guardado en Pictures/QR", "Couldn't save it" to "No se pudo guardar", "Save picture" to "Guardar imagen",
    "Saved" to "Guardado", "Save as SVG" to "Guardar como SVG",
    "Codes made here never expire and don't go through any server: what you typed is inside the picture." to
        "Los códigos creados acá no vencen nunca y no pasan por ningún servidor: lo que escribiste está dentro de la imagen.",
    // history
    "Keep a history on this phone" to "Guardar un historial en este teléfono",
    "Save as a spreadsheet" to "Guardar como planilla", "Clear all" to "Borrar todo",
    "Nothing matches." to "Nada coincide.",
    "Clear" to "Borrar", "Cancel" to "Cancelar",
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
    // UX review 2026-11-22 (U-Q1..U-Q28)
    "Undo" to "Deshacer",
    "Back" to "Volver",
    "More options" to "Más opciones",
    "Read a code from a picture" to "Leer un código de una imagen",
    "Source code" to "Código fuente",
    "Scanning needs the camera, and it isn't allowed yet." to "Para escanear hace falta la cámara, y todavía no está permitida.",
    "Allow the camera" to "Permitir la cámara",
    "Reading the picture…" to "Leyendo la imagen…",
    "Delete {what}" to "Borrar {what}",
    "Careful: this may be a fake site." to "Cuidado: puede ser un sitio falso.",
    "It really goes to" to "En realidad lleva a",
    "Looks like: {name}" to "Parece: {name}",
    "Open anyway" to "Abrir igual",
    "Pick “{network}” in the list and paste the password (it's copied)." to "Elegí “{network}” en la lista y pegá la contraseña (ya está copiada).",
    "Pick “{network}” in the list." to "Elegí “{network}” en la lista.",
    "Search words" to "Búsqueda",
    "Show as a code" to "Mostrar como código",
    "Scan another" to "Escanear otro",
    "Open {name}?" to "¿Abrir {name}?",
    "It only looks like {name}." to "Solo se parece a {name}.",
    "It may not be the site it seems to be. Don't sign in or pay there." to "Puede no ser el sitio que parece. No inicies sesión ni pagues ahí.",
    "QR code" to "Código QR",
    "Product barcode ({kind})" to "Código de barras de producto ({kind})",
    "Barcode ({kind})" to "Código de barras ({kind})",
    "The code" to "El código",
    "Couldn't make a code from this. Try less text." to "No se pudo crear un código con esto. Probá con menos texto.",
    "Pick the date" to "Elegir la fecha",
    "Date: {d}" to "Fecha: {d}",
    "All day" to "Todo el día",
    "Time: {t}" to "Hora: {t}",
    "Color of the squares" to "Color de los cuadraditos",
    "Background color" to "Color del fondo",
    "Give the event a name and pick its date to see your code." to "Ponele un nombre al evento y elegí la fecha para ver tu código.",
    "Making the code…" to "Creando el código…",
    "Updating…" to "Actualizando…",
    "Network: {n} · Password: {c} characters" to "Red: {n} · Contraseña: {c} caracteres",
    "Saving…" to "Guardando…",
    "OK" to "Aceptar",
    "Codes already here stay until you clear them." to "Los códigos que ya están quedan hasta que los borres.",
    "Codes you scan or make show up here." to "Acá aparecen los códigos que escaneás o creás.",
    "Deleted “{what}”" to "Se borró “{what}”",
    "Clear the 1 code in the history?" to "¿Borrar el código del historial?",
    "Clear all {n} codes in the history?" to "¿Borrar los {n} códigos del historial?",
    "Light" to "Linterna",
    "Light off" to "Apagar linterna",
    "Black" to "Negro",
    "Dark blue" to "Azul oscuro",
    "Dark green" to "Verde oscuro",
    "Wine" to "Bordó",
    "Purple" to "Violeta",
    "Brown" to "Marrón",
    "White" to "Blanco",
    "Cream" to "Crema",
    "Light green" to "Verde claro",
    "Light blue" to "Celeste",
    "Yellow" to "Amarillo",
)
