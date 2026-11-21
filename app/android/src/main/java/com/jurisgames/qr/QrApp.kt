package com.jurisgames.qr

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** What the platform gives the screens (Android: MainActivity). */
class Env(
    val history: History,
    val version: String,
    /** Opens the camera; the callback gets (text, format) or nothing. */
    val scan: ((Pair<String, BarcodeFormat>?) -> Unit) -> Unit,
    /** Picks a picture; the callback gets it (decoded) or null. */
    val pickPicture: ((Bitmap?) -> Unit) -> Unit,
    /** Asks where to save a file (name, mime type) and writes the bytes; the callback says whether it worked. */
    val saveFile: (String, String, ByteArray, (Boolean) -> Unit) -> Unit,
)

sealed class Screen {
    object Home : Screen()
    data class Result(val text: String, val format: BarcodeFormat, val fromHistory: Boolean = false) : Screen()
    object Make : Screen()
    object History : Screen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrApp(env: Env, start: Screen = Screen.Home, startNote: String? = null) {
    var screen by remember { mutableStateOf(start) }
    var historyVersion by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun note(s: String) { scope.launch { snackbar.showSnackbar(s) } }
    LaunchedEffect(startNote) { startNote?.let(::note) }

    fun found(text: String, format: BarcodeFormat) {
        env.history.add(text, Payload.of(text, format).kind, format.name, made = false)
        historyVersion++
        screen = Screen.Result(text, format)
    }
    val scan = { env.scan { r -> if (r != null) found(r.first, r.second) } }
    val scanPicture = {
        env.pickPicture { b ->
            if (b == null) return@pickPicture
            scope.launch {
                val f = withContext(Dispatchers.Default) { Decode.fromBitmap(b) }
                if (f == null) note(tr("No code found in this picture.")) else found(f.text, f.format)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(when (screen) {
                        is Screen.Home -> tr("QR")
                        is Screen.Result -> tr("What the code says")
                        is Screen.Make -> tr("Make a code")
                        is Screen.History -> tr("History")
                    })
                },
                navigationIcon = {
                    if (screen != Screen.Home) TextButton(onClick = { screen = Screen.Home }, modifier = Modifier.testTag("back")) { Text("←", style = MaterialTheme.typography.titleLarge) }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("menu")) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(tr("History")) }, onClick = { menu = false; screen = Screen.History }, modifier = Modifier.testTag("historyMenu"))
                            DropdownMenuItem(text = { Text(tr("Scan a picture")) }, onClick = { menu = false; scanPicture() }, modifier = Modifier.testTag("pictureMenu"))
                            // in the menu itself (Kleider, 2026-11-21: "Make sure users always have an easy way to give feedback or report bugs on ALL our apps")
                            DropdownMenuItem(text = { Text(tr("Send feedback")) }, onClick = { menu = false; feedback = true }, modifier = Modifier.testTag("feedbackMenu"))
                            DropdownMenuItem(text = { Text(tr("About")) }, onClick = { menu = false; about = true }, modifier = Modifier.testTag("aboutMenu"))
                        }
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val s = screen) {
                is Screen.Home -> Home(env, historyVersion, scan, scanPicture, onMake = { screen = Screen.Make }, onOpen = { screen = Screen.Result(it.text, runCatching { BarcodeFormat.valueOf(it.format) }.getOrDefault(BarcodeFormat.QR_CODE), true) }, onHistory = { screen = Screen.History })
                is Screen.Result -> ResultScreen(s.text, s.format, ::note)
                is Screen.Make -> MakeScreen(env, ::note, onMade = { historyVersion++ })
                is Screen.History -> HistoryScreen(env, historyVersion, onChanged = { historyVersion++ }, onOpen = { screen = Screen.Result(it.text, runCatching { BarcodeFormat.valueOf(it.format) }.getOrDefault(BarcodeFormat.QR_CODE), true) }, note = ::note)
            }
        }
    }
    if (feedback) FeedbackDialog(env, onSent = { feedback = false; note(tr("Thanks! Your message was sent.")) }, onClose = { feedback = false })
    if (about) AlertDialog(
        onDismissRequest = { about = false },
        confirmButton = { TextButton(onClick = { about = false }) { Text(tr("Close")) } },
        title = { Text(tr("About QR")) },
        text = {
            Text(tr("Version {v}. Free and open source (Apache-2.0), no ads, no account. Codes are read with ZXing. Your history stays on this phone. For many more barcode formats, see Binary Eye.", "v" to env.version), modifier = Modifier.testTag("aboutText"))
        },
    )
}

@Composable
private fun Home(env: Env, version: Int, scan: () -> Unit, scanPicture: () -> Unit, onMake: () -> Unit, onOpen: (History.Entry) -> Unit, onHistory: () -> Unit) {
    val recent = remember(version) { env.history.all().take(5) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = scan, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("scan"), contentPadding = PaddingValues(16.dp)) {
            Text(tr("Scan a code"), style = MaterialTheme.typography.titleLarge)
        }
        FilledTonalButton(onClick = onMake, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("make"), contentPadding = PaddingValues(16.dp)) {
            Text(tr("Make a code"), style = MaterialTheme.typography.titleLarge)
        }
        TextButton(onClick = scanPicture, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("scanImage")) { Text(tr("Read a code from a picture or screenshot")) }
        if (recent.isNotEmpty()) {
            Spacer(Modifier.size(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Recent"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onHistory, modifier = Modifier.testTag("allHistory")) { Text(tr("All")) }
            }
            for (e in recent) EntryRow(e, onOpen, null)
        } else if (!env.history.enabled) {
            Text(tr("History is off: codes you scan or make aren't kept."), style = MaterialTheme.typography.bodySmall)
        }
    }
}

fun kindLabel(kind: String): String = when (kind) {
    "link" -> tr("Link"); "wifi" -> tr("Wi-Fi"); "contact" -> tr("Contact"); "event" -> tr("Event"); "phone" -> tr("Phone")
    "sms" -> tr("Text message"); "email" -> tr("Email"); "geo" -> tr("Place"); "product" -> tr("Product"); else -> tr("Text")
}

@Composable
private fun EntryRow(e: History.Entry, onOpen: (History.Entry) -> Unit, onDelete: ((History.Entry) -> Unit)?) {
    val time = remember(e.time) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(e.time)) }
    Row(Modifier.fillMaxWidth().clickable { onOpen(e) }.padding(vertical = 8.dp).testTag("entry"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(e.text.lineSequence().firstOrNull { it.isNotBlank() } ?: e.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text((if (e.made) tr("Made") else tr("Scanned")) + " · " + kindLabel(e.kind) + " · " + time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onDelete != null) TextButton(onClick = { onDelete(e) }, modifier = Modifier.testTag("deleteEntry")) { Text("✕") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultScreen(text: String, format: BarcodeFormat, note: (String) -> Unit) {
    val ctx = LocalContext.current
    val p = remember(text, format) { Payload.of(text, format) }
    fun failed(ok: Boolean) { if (!ok) note(tr("No app on this phone can do that.")) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(kindLabel(p.kind), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("kind"))
        @Composable fun line(label: String, value: String) {
            if (value.isNotBlank()) Column { Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.bodyLarge) }
        }
        when (p) {
            is Payload.Link -> {
                val r = remember(p.url) { LinkCheck.check(p.url) }
                if (r.opens) {
                    Text(tr("It goes to"), style = MaterialTheme.typography.bodySmall)
                    Text(r.site.ifEmpty { r.host }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("site"))
                    if (r.host != r.site) Text(r.host, style = MaterialTheme.typography.bodyMedium)
                }
                Text(p.url, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("fullUrl"))
                for (w in r.warnings) Text("⚠ " + LinkCheck.shown(w, r), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("warning"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r.opens) Button(onClick = { failed(Actions.open(ctx, p.url)) }, modifier = Modifier.testTag("open")) { Text(tr("Open")) }
                    OutlinedButton(onClick = { Actions.copy(ctx, "link", p.url); note(tr("Copied")) }, modifier = Modifier.testTag("copy")) { Text(tr("Copy")) }
                    OutlinedButton(onClick = { failed(Actions.shareText(ctx, p.url)) }, modifier = Modifier.testTag("share")) { Text(tr("Share")) }
                }
            }
            is Payload.Wifi -> {
                var show by remember { mutableStateOf(false) }
                line(tr("Network"), p.ssid)
                line(tr("Security"), if (p.security.equals("nopass", true)) tr("Open (no password)") else p.security)
                if (p.password.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (show) p.password else "•".repeat(minOf(12, p.password.length)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).testTag("password"))
                    TextButton(onClick = { show = !show }) { Text(if (show) tr("Hide") else tr("Show")) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { failed(Actions.joinWifi(ctx, p)) }, modifier = Modifier.testTag("joinWifi")) { Text(tr("Join this network")) }
                    if (p.password.isNotEmpty()) OutlinedButton(onClick = { Actions.copy(ctx, "password", p.password, secret = true); note(tr("Password copied")) }, modifier = Modifier.testTag("copy")) { Text(tr("Copy password")) }
                }
            }
            is Payload.Contact -> {
                line(tr("Name"), p.name); line(tr("Phone"), p.phones.joinToString(", ")); line(tr("Email"), p.emails.joinToString(", "))
                line(tr("Company"), p.org); line(tr("Address"), p.address); line(tr("Website"), p.url); line(tr("Note"), p.note)
                Button(onClick = { failed(Actions.addContact(ctx, p)) }, modifier = Modifier.testTag("addContact")) { Text(tr("Add to contacts")) }
            }
            is Payload.Event -> {
                val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
                line(tr("Event"), p.title)
                line(tr("Starts"), if (p.startMs > 0) df.format(Date(p.startMs)) else "")
                line(tr("Ends"), if (p.endMs > p.startMs) df.format(Date(p.endMs)) else "")
                line(tr("Where"), p.location); line(tr("Details"), p.description)
                Button(onClick = { failed(Actions.addEvent(ctx, p)) }, modifier = Modifier.testTag("addEvent")) { Text(tr("Add to calendar")) }
            }
            is Payload.Phone -> {
                line(tr("Number"), p.number)
                Button(onClick = { failed(Actions.dial(ctx, p.number)) }, modifier = Modifier.testTag("dial")) { Text(tr("Call")) }
            }
            is Payload.Sms -> {
                line(tr("To"), p.number); line(tr("Message"), p.body)
                Button(onClick = { failed(Actions.sms(ctx, p)) }, modifier = Modifier.testTag("sms")) { Text(tr("Write the message")) }
            }
            is Payload.Email -> {
                line(tr("To"), p.to); line(tr("Subject"), p.subject); line(tr("Message"), p.body)
                Button(onClick = { failed(Actions.email(ctx, p)) }, modifier = Modifier.testTag("email")) { Text(tr("Write the email")) }
            }
            is Payload.Geo -> {
                line(tr("Place"), "%.5f, %.5f".format(p.lat, p.lng)); line(tr("Search"), p.query)
                Button(onClick = { failed(Actions.map(ctx, p)) }, modifier = Modifier.testTag("map")) { Text(tr("Show on a map")) }
            }
            is Payload.Product -> {
                line(tr("Product number"), p.code)
                Button(onClick = { failed(Actions.search(ctx, p.code)) }, modifier = Modifier.testTag("search")) { Text(tr("Search the web for it")) }
            }
            is Payload.Text -> Text(p.raw, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("text"))
        }
        if (p !is Payload.Link) {
            HorizontalDivider()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { Actions.copy(ctx, "code", text); note(tr("Copied")) }, modifier = Modifier.testTag("copyAll")) { Text(tr("Copy all")) }
                OutlinedButton(onClick = { failed(Actions.shareText(ctx, text)) }, modifier = Modifier.testTag("shareAll")) { Text(tr("Share")) }
            }
        }
        Text(tr("Read from: {f}", "f" to format.name.replace('_', ' ')), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Kinds of code to make, in the order shown. */
private val MAKE_KINDS = listOf("link", "text", "wifi", "contact", "event", "phone", "sms", "email")

/** Square colors (dark) and backgrounds (light) to pick from; any pair is checked for contrast. */
private val SQUARES = listOf(0xFF000000, 0xFF1B3A6B, 0xFF0B5D3B, 0xFF6B1B3A, 0xFF4A2C82, 0xFF7A3E00).map { it.toInt() }
private val BACKS = listOf(0xFFFFFFFF, 0xFFFFF8E1, 0xFFE8F5E9, 0xFFE3F2FD, 0xFFFFD54F).map { it.toInt() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MakeScreen(env: Env, note: (String) -> Unit, onMade: () -> Unit) {
    val ctx = LocalContext.current
    var kind by remember { mutableStateOf("link") }
    val f = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    var security by remember { mutableStateOf("WPA") }
    var hidden by remember { mutableStateOf(false) }
    var fg by remember { mutableIntStateOf(SQUARES[0]) }
    var bg by remember { mutableIntStateOf(BACKS[0]) }
    var logo by remember { mutableStateOf<Bitmap?>(null) }
    fun v(k: String) = f[k] ?: ""
    val content: String = when (kind) {
        "link" -> if (v("url").isBlank()) "" else Content.link(v("url"))
        "text" -> v("text")
        "wifi" -> if (v("ssid").isBlank()) "" else Content.wifi(v("ssid"), v("password"), security, hidden)
        "contact" -> if (v("name").isBlank()) "" else Content.contact(v("name"), v("phone"), v("email"), v("org"), v("web"), v("address"))
        "event" -> eventContent(v("title"), v("date"), v("time"), v("hours"), v("where"), v("details"))
        "phone" -> if (v("number").isBlank()) "" else Content.phone(v("number"))
        "sms" -> if (v("number").isBlank()) "" else Content.sms(v("number"), v("message"))
        "email" -> if (v("to").isBlank()) "" else Content.email(v("to"), v("subject"), v("message"))
        else -> ""
    }
    val bytes = content.toByteArray().size
    val limit = if (logo != null) Codes.MAX_BYTES_H else Codes.MAX_BYTES
    val colorProblem = Codes.colorProblem(fg, bg)
    val image: Bitmap? = remember(content, fg, bg, logo) {
        if (content.isEmpty() || bytes > limit) null else runCatching { Actions.bitmap(content, fg, bg, logo) }.getOrNull()
    }
    val reads = remember(image) { image != null && Actions.readsBack(image, content) }
    val pick = { env.pickPicture { b -> if (b != null) logo = b } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (k in MAKE_KINDS) FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(kindLabel(k)) }, modifier = Modifier.testTag("kind-$k"))
        }
        @Composable fun field(key: String, label: String, type: KeyboardType = KeyboardType.Text, lines: Int = 1, secret: Boolean = false) {
            OutlinedTextField(v(key), { f[key] = it.take(2000) }, label = { Text(label) }, singleLine = lines == 1, minLines = lines,
                keyboardOptions = KeyboardOptions(keyboardType = type),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth().testTag("field-$key"))
        }
        when (kind) {
            "link" -> field("url", tr("Web address"), KeyboardType.Uri)
            "text" -> field("text", tr("Text"), lines = 4)
            "wifi" -> {
                field("ssid", tr("Network name"))
                field("password", tr("Password (empty for an open network)"), KeyboardType.Password)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (s in listOf("WPA", "WEP")) FilterChip(selected = security == s, onClick = { security = s }, label = { Text(if (s == "WPA") "WPA/WPA2/WPA3" else s) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(hidden, { hidden = it }); Text(tr("Hidden network")) }
            }
            "contact" -> {
                field("name", tr("Name")); field("phone", tr("Phone"), KeyboardType.Phone); field("email", tr("Email"), KeyboardType.Email)
                field("org", tr("Company")); field("web", tr("Website"), KeyboardType.Uri); field("address", tr("Address"))
            }
            "event" -> {
                field("title", tr("Event")); field("date", tr("Date (YYYY-MM-DD)")); field("time", tr("Time (HH:MM, empty for all day)"))
                field("hours", tr("How many hours"), KeyboardType.Decimal); field("where", tr("Where")); field("details", tr("Details"), lines = 2)
            }
            "phone" -> field("number", tr("Phone number"), KeyboardType.Phone)
            "sms" -> { field("number", tr("Phone number"), KeyboardType.Phone); field("message", tr("Message"), lines = 2) }
            "email" -> { field("to", tr("Email address"), KeyboardType.Email); field("subject", tr("Subject")); field("message", tr("Message"), lines = 2) }
        }
        Text(tr("Colors"), style = MaterialTheme.typography.titleSmall)
        Swatches(SQUARES, fg, "fg") { fg = it }
        Swatches(BACKS, bg, "bg") { bg = it }
        if (colorProblem != null) Text("⚠ " + tr(colorProblem), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("colorProblem"))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = pick, modifier = Modifier.testTag("logo")) { Text(if (logo == null) tr("Add a picture in the middle") else tr("Change the picture")) }
            if (logo != null) TextButton(onClick = { logo = null }, modifier = Modifier.testTag("noLogo")) { Text(tr("Remove it")) }
        }
        when {
            content.isEmpty() -> Text(tr("Fill in the fields to see your code."), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("empty"))
            bytes > limit -> Text("⚠ " + tr("Too much for one code ({n} of {max} bytes). Shorten it, or remove the picture.", "n" to bytes, "max" to limit), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("tooLong"))
            image != null -> {
                Image(image.asImageBitmap(), contentDescription = tr("Your code"), filterQuality = FilterQuality.None,
                    modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = 320.dp).fillMaxWidth().testTag("preview"))
                if (reads) Text("✓ " + tr("Checked: it reads back correctly."), color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("readsBack"))
                else Text("⚠ " + tr("This code didn't read back. Try other colors, a smaller picture, or less text."), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("noRead"))
                val name = "qr-" + System.currentTimeMillis() / 1000
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = reads, onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= 29) {
                            val ok = Actions.savePicture(ctx, image, name)
                            if (ok) { env.history.add(content, kind, "QR_CODE", made = true); onMade() }
                            note(if (ok) tr("Saved to Pictures/QR") else tr("Couldn't save it"))
                        } else {
                            // Android 8-9: Pictures needs a storage permission we don't ask for; the system's save dialog doesn't.
                            env.saveFile("$name.png", "image/png", Actions.png(image)) { ok ->
                                if (ok) { env.history.add(content, kind, "QR_CODE", made = true); onMade() }
                                note(if (ok) tr("Saved") else tr("Couldn't save it"))
                            }
                        }
                    }, modifier = Modifier.testTag("savePng")) { Text(tr("Save picture")) }
                    OutlinedButton(enabled = reads, onClick = {
                        if (Actions.sharePicture(ctx, image, name)) { env.history.add(content, kind, "QR_CODE", made = true); onMade() }
                    }, modifier = Modifier.testTag("sharePng")) { Text(tr("Share")) }
                    OutlinedButton(enabled = reads, onClick = {
                        val svg = Codes.svg(Codes.matrix(content, logo != null), fg, bg, logo?.let { Actions.png(it) })
                        env.saveFile("$name.svg", "image/svg+xml", svg.toByteArray()) { ok -> note(if (ok) tr("Saved") else tr("Couldn't save it")) }
                    }, modifier = Modifier.testTag("saveSvg")) { Text(tr("Save as SVG")) }
                }
                Text(tr("Codes made here never expire and don't go through any server: what you typed is inside the picture."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** An event from typed date/time; "" until the date reads. */
fun eventContent(title: String, date: String, time: String, hours: String, where: String, details: String): String {
    if (title.isBlank()) return ""
    val d = Regex("""^\s*(\d{4})-(\d{1,2})-(\d{1,2})\s*$""").find(date) ?: return ""
    val (y, mo, da) = d.destructured
    val cal = java.util.Calendar.getInstance().apply { clear(); set(y.toInt(), mo.toInt() - 1, da.toInt()) }
    val t = Regex("""^\s*(\d{1,2}):(\d{2})\s*$""").find(time)
    val allDay = t == null
    if (t != null) { cal.set(java.util.Calendar.HOUR_OF_DAY, t.groupValues[1].toInt()); cal.set(java.util.Calendar.MINUTE, t.groupValues[2].toInt()) }
    val start = cal.timeInMillis
    val len = ((hours.replace(',', '.').toDoubleOrNull() ?: 1.0).coerceIn(0.0, 24.0 * 30) * 3_600_000).toLong()
    return Content.event(title, start, if (allDay) start + 86_400_000L else start + len, allDay, where, details)
}

@Composable
private fun Swatches(colors: List<Int>, chosen: Int, tag: String, pick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (c in colors) {
            val sel = c == chosen
            Box(Modifier.size(36.dp).background(Color(c), CircleShape)
                .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { pick(c) }.testTag("$tag-${Integer.toHexString(c)}"))
        }
    }
}

@Composable
private fun HistoryScreen(env: Env, version: Int, onChanged: () -> Unit, onOpen: (History.Entry) -> Unit, note: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var on by remember { mutableStateOf(env.history.enabled) }
    val list = remember(version, q) { if (q.isBlank()) env.history.all() else env.history.search(q) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Text(tr("Keep a history on this phone"), modifier = Modifier.weight(1f))
            Switch(on, { on = it; env.history.enabled = it; onChanged() }, modifier = Modifier.testTag("historyOn"))
        }
        OutlinedTextField(q, { q = it }, label = { Text(tr("Search")) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("search"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            OutlinedButton(enabled = list.isNotEmpty(), onClick = {
                env.saveFile("qr-history.csv", "text/csv", env.history.csv().toByteArray()) { ok -> note(if (ok) tr("Saved") else tr("Couldn't save it")) }
            }, modifier = Modifier.testTag("exportCsv")) { Text(tr("Save as a spreadsheet")) }
            TextButton(enabled = env.history.all().isNotEmpty(), onClick = { confirmClear = true }, modifier = Modifier.testTag("clear")) { Text(tr("Clear all")) }
        }
        if (list.isEmpty()) Text(if (q.isBlank()) tr("Nothing here yet.") else tr("Nothing matches."), modifier = Modifier.padding(top = 16.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.time.toString() + it.made + it.text.hashCode() }) { e ->
                EntryRow(e, onOpen) { env.history.remove(it); onChanged() }
                HorizontalDivider()
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        confirmButton = { TextButton(onClick = { env.history.clear(); confirmClear = false; onChanged() }, modifier = Modifier.testTag("clearYes")) { Text(tr("Clear")) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(tr("Cancel")) } },
        title = { Text(tr("Clear the history?")) },
        text = { Text(tr("Every code in the history is removed from this phone. This can't be undone.")) },
    )
}

@Composable
private fun FeedbackDialog(env: Env, onSent: () -> Unit, onClose: () -> Unit) {
    var kind by remember { mutableStateOf("bug") }
    var message by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var include by remember { mutableStateOf(true) }
    var preview by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val details = remember { listOf("history: " + (if (env.history.enabled) "on" else "off") + ", " + env.history.all().size + " codes") }
    val body = Feedback.json(kind, message, email, env.version, if (include) details else null)
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {
            TextButton(enabled = !sending, modifier = Modifier.testTag("feedbackSend"), onClick = {
                if (message.isBlank()) { error = tr("Write a few words first."); return@TextButton }
                sending = true; error = ""
                scope.launch {
                    val problem = withContext(Dispatchers.IO) { Feedback.send(body) }
                    sending = false
                    if (problem == null) onSent() else error = problem
                }
            }) { Text(if (sending) tr("Sending…") else tr("Send")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } },
        title = { Text(tr("Send feedback")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("It goes to the person who makes this app. Thanks for helping!"), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((k, label) in Feedback.kinds) FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(tr(label)) }, modifier = Modifier.testTag("feedbackKind-$k"))
                }
                OutlinedTextField(message, { message = it.take(4000) }, placeholder = { Text(tr("What happened, or what would make it better?")) }, minLines = 4, modifier = Modifier.fillMaxWidth().testTag("feedbackMessage"))
                OutlinedTextField(email, { email = it.take(254) }, placeholder = { Text(tr("Your email, if you'd like an answer (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("feedbackEmail"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(include, { include = it }, modifier = Modifier.testTag("feedbackDetails"))
                    Text(tr("Include technical details"))
                }
                Text(tr("App version, system, and how many codes are in the history. Never what the codes say."), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { preview = !preview }, modifier = Modifier.testTag("feedbackSee")) { Text(if (preview) tr("Hide what's sent") else tr("See what's sent")) }
                if (preview) Text(body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("feedbackPreview"))
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("feedbackError"))
            }
        },
    )
}
