package com.jurisgames.qr

import androidx.compose.runtime.produceState
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
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
    /** True after the camera was refused (UX review U-Q6): Home then says so, with a way to fix it. */
    val cameraRefused: androidx.compose.runtime.State<Boolean> = mutableStateOf(false),
    /** Asks for the camera again, or opens this app's page in the phone's settings once Android no longer asks. */
    val allowCamera: () -> Unit = {},
)

/** Where the source code is (About, UX review U-Q23). */
const val SOURCE_URL = "https://github.com/kleiders3010/qr"

sealed class Screen {
    object Home : Screen()
    data class Result(val text: String, val format: BarcodeFormat, val fromHistory: Boolean = false) : Screen()
    object Make : Screen()
    object History : Screen()
}

/** The screen survives a rotation, a theme change and Android ending the app in the background (U-Q4). */
val ScreenSaver = androidx.compose.runtime.saveable.Saver<Screen, List<String>>(
    save = { s ->
        when (s) {
            is Screen.Home -> listOf("home")
            is Screen.Make -> listOf("make")
            is Screen.History -> listOf("history")
            is Screen.Result -> listOf("result", s.text, s.format.name, s.fromHistory.toString())
        }
    },
    restore = { l ->
        when (l.firstOrNull()) {
            "make" -> Screen.Make
            "history" -> Screen.History
            "result" -> Screen.Result(l[1], runCatching { BarcodeFormat.valueOf(l[2]) }.getOrDefault(BarcodeFormat.QR_CODE), l[3] == "true")
            else -> Screen.Home
        }
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrApp(env: Env, start: Screen = Screen.Home, startNote: String? = null) {
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf(start) }
    var historyVersion by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun note(s: String) { scope.launch { snackbar.showSnackbar(s) } }
    /** A done action with Undo (U-Q13). */
    fun undoable(s: String, undo: () -> Unit) {
        scope.launch {
            if (snackbar.showSnackbar(s, actionLabel = tr("Undo"), withDismissAction = false, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) undo()
        }
    }
    LaunchedEffect(startNote) { startNote?.let(::note) }
    // System Back and the back gesture go one step back inside the app, never out of it from a screen (U-Q1).
    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    fun found(text: String, format: BarcodeFormat) {
        if (!env.history.add(text, Payload.of(text, format).kind, format.name, made = false)) note(tr("Couldn't save it to the history: the phone may be full."))
        historyVersion++
        screen = Screen.Result(text, format)
    }
    val scan = { if (env.cameraRefused.value) env.allowCamera() else env.scan { r -> if (r != null) found(r.first, r.second) } }
    val scanPicture = {
        reading = true // "Reading the picture…" on Home until there is an answer (U-Q16)
        env.pickPicture { b ->
            if (b == null) { reading = false; return@pickPicture }
            scope.launch {
                val f = withContext(Dispatchers.Default) { Decode.fromBitmap(b) }
                reading = false
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
                    // glyph buttons say what they are to a screen reader (U-Q13, APP-RULES 167)
                    val backName = tr("Back")
                    if (screen != Screen.Home) TextButton(onClick = { screen = Screen.Home }, modifier = Modifier.testTag("back").semantics { contentDescription = backName }) { Text("←", style = MaterialTheme.typography.titleLarge) }
                },
                actions = {
                    Box {
                        val moreName = tr("More options")
                        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("menu").semantics { contentDescription = moreName }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(tr("History")) }, onClick = { menu = false; screen = Screen.History }, modifier = Modifier.testTag("historyMenu"))
                            // one name for it everywhere (U-Q18)
                            DropdownMenuItem(text = { Text(tr("Read a code from a picture")) }, onClick = { menu = false; scanPicture() }, modifier = Modifier.testTag("pictureMenu"))
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
                is Screen.Home -> Home(env, historyVersion, scan, scanPicture, reading, onMake = { screen = Screen.Make }, onOpen = { screen = Screen.Result(it.text, runCatching { BarcodeFormat.valueOf(it.format) }.getOrDefault(BarcodeFormat.QR_CODE), true) }, onHistory = { screen = Screen.History })
                is Screen.Result -> ResultScreen(s.text, s.format, ::note, onScanAgain = if (s.fromHistory) null else scan)
                is Screen.Make -> MakeScreen(env, ::note, onMade = { historyVersion++ })
                is Screen.History -> HistoryScreen(env, historyVersion, onChanged = { historyVersion++ }, onOpen = { screen = Screen.Result(it.text, runCatching { BarcodeFormat.valueOf(it.format) }.getOrDefault(BarcodeFormat.QR_CODE), true) }, note = ::note, undoable = ::undoable, onScan = { screen = Screen.Home; scan() })
            }
        }
    }
    if (feedback) FeedbackDialog(env, onSent = { feedback = false; note(tr("Thanks! Your message was sent.")) }, onClose = { feedback = false })
    if (about) AlertDialog(
        onDismissRequest = { about = false },
        confirmButton = { TextButton(onClick = { about = false }) { Text(tr("Close")) } },
        dismissButton = {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Row {
                // the source code next to Privacy (U-Q23)
                TextButton(onClick = { Actions.open(ctx, SOURCE_URL) }, modifier = Modifier.testTag("source")) { Text(tr("Source code")) }
                TextButton(onClick = { Actions.open(ctx, "https://apps.jurisgames.com/privacy/") }, modifier = Modifier.testTag("privacy")) { Text(tr("Privacy")) }
            }
        },
        title = { Text(tr("About QR")) },
        text = {
            Text(tr("Version {v}. Free and open source (Apache-2.0), no ads, no account. Codes are read with ZXing. Your history stays on this phone. For many more barcode formats, see Binary Eye.", "v" to env.version), modifier = Modifier.testTag("aboutText"))
        },
    )
}

@Composable
private fun Home(env: Env, version: Int, scan: () -> Unit, scanPicture: () -> Unit, reading: Boolean, onMake: () -> Unit, onOpen: (History.Entry) -> Unit, onHistory: () -> Unit) {
    val recent = remember(version) { env.history.all().take(5) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = scan, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("scan"), contentPadding = PaddingValues(16.dp)) {
            Text(tr("Scan a code"), style = MaterialTheme.typography.titleLarge)
        }
        // U-Q6: a refused camera is said here, where the person looks, with the two ways on.
        if (env.cameraRefused.value) Column(Modifier.fillMaxWidth().testTag("cameraRefused"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("Scanning needs the camera, and it isn't allowed yet."), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = env.allowCamera, modifier = Modifier.testTag("allowCamera")) { Text(tr("Allow the camera")) }
                OutlinedButton(onClick = scanPicture) { Text(tr("Read a code from a picture")) }
            }
        }
        FilledTonalButton(onClick = onMake, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("make"), contentPadding = PaddingValues(16.dp)) {
            Text(tr("Make a code"), style = MaterialTheme.typography.titleLarge)
        }
        TextButton(onClick = scanPicture, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("scanImage")) { Text(tr("Read a code from a picture")) }
        if (reading) Text(tr("Reading the picture…"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("reading").semantics { liveRegion = LiveRegionMode.Polite })
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
    val summary = remember(e.text, e.format) { Payload.of(e.text, runCatching { BarcodeFormat.valueOf(e.format) }.getOrDefault(BarcodeFormat.QR_CODE)).summary() }
    Row(Modifier.fillMaxWidth().clickable { onOpen(e) }.padding(vertical = 8.dp).testTag("entry"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(summary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text((if (e.made) tr("Made") else tr("Scanned")) + " · " + kindLabel(e.kind) + " · " + time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onDelete != null) {
            val name = tr("Delete {what}", "what" to summary)
            TextButton(onClick = { onDelete(e) }, modifier = Modifier.testTag("deleteEntry").semantics { contentDescription = name }) { Text("✕") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultScreen(text: String, format: BarcodeFormat, note: (String) -> Unit, onScanAgain: (() -> Unit)?) {
    val ctx = LocalContext.current
    val p = remember(text, format) { Payload.of(text, format) }
    fun failed(ok: Boolean) { if (!ok) note(tr("No app on this phone can do that.")) }
    // Android 13+ shows its own "copied" preview: one confirmation is enough (U-Q27).
    fun copied(s: String) { if (android.os.Build.VERSION.SDK_INT < 33) note(s) }
    var showCode by rememberSaveable { mutableStateOf(false) }
    var openAnyway by remember { mutableStateOf<LinkCheck.Report?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(kindLabel(p.kind), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("kind"))
        @Composable fun line(label: String, value: String) {
            if (value.isNotBlank()) Column { Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(Visible.show(value), style = MaterialTheme.typography.bodyLarge) }
        }
        // Q2-3: a link inside a card, event or message gets the same check as a scanned link (it would sit one tap
        // away in Contacts or the calendar, never checked): where it really goes, in bold, and its warnings.
        @Composable fun linksIn(vararg texts: String) {
            for (url in Content.linksIn(*texts)) {
                val r = LinkCheck.check(url)
                Column(Modifier.testTag("innerLink")) {
                    Text(tr("A link in it goes to"), style = MaterialTheme.typography.bodySmall)
                    Text(if (r.opens) LinkCheck.headline(r) else Visible.show(url), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    for (w in r.warnings) Text("⚠ " + LinkCheck.shown(w, r), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("warning"))
                }
            }
        }
        // Q2-3: an email address with lookalike letters in its domain gets the letters warnings.
        @Composable fun addressCheck(addresses: List<String>) {
            for (w in Content.addressWarnings(addresses)) Text("⚠ " + tr(w), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("warning"))
        }
        when (p) {
            is Payload.Link -> {
                val r = remember(p.url) { LinkCheck.check(p.url) }
                val serious = r.opens && LinkCheck.serious(r)
                if (r.opens) {
                    // U-Q3: a likely trap leads with a plain sentence; the bold name is never the lookalike form.
                    if (serious) Text("⚠ " + tr("Careful: this may be a fake site."), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("trap"))
                    Text(if (serious) tr("It really goes to") else tr("It goes to"), style = MaterialTheme.typography.bodySmall)
                    Text(LinkCheck.headline(r), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("site"))
                    if (r.ascii == r.host && r.host != LinkCheck.headline(r)) Text(r.host, style = MaterialTheme.typography.bodyMedium)
                    // A lookalike reads like the real name: the form people would read is shown under the ASCII one, labelled.
                    if (r.ascii != r.host) Text(tr("Looks like: {name}", "name" to r.host), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("asciiHost"))
                }
                Text(Visible.show(p.url), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("fullUrl"))
                for (w in r.warnings) Text("⚠ " + LinkCheck.shown(w, r), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("warning"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // With warnings, Open is not the button the eye goes to first; with a likely trap it asks once.
                    if (r.opens && r.warnings.isEmpty()) Button(onClick = { failed(Actions.open(ctx, p.url)) }, modifier = Modifier.testTag("open")) { Text(tr("Open")) }
                    else if (serious) TextButton(onClick = { openAnyway = r }, modifier = Modifier.testTag("open")) { Text(tr("Open anyway")) }
                    else if (r.opens) OutlinedButton(onClick = { failed(Actions.open(ctx, p.url)) }, modifier = Modifier.testTag("open")) { Text(tr("Open")) }
                    OutlinedButton(onClick = { Actions.copy(ctx, "link", p.url); copied(tr("Copied")) }, modifier = Modifier.testTag("copy")) { Text(tr("Copy")) }
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
                    Button(onClick = {
                        when (Actions.joinWifi(ctx, p)) {
                            Actions.Join.Sheet -> {}
                            Actions.Join.Failed -> failed(false)
                            // U-Q14: the phone's Wi-Fi list opened instead: the password is copied and the screen says what's next.
                            Actions.Join.Settings -> {
                                if (p.password.isNotEmpty()) {
                                    Actions.copy(ctx, "password", p.password, secret = true)
                                    note(tr("Pick “{network}” in the list and paste the password (it's copied).", "network" to p.ssid))
                                } else note(tr("Pick “{network}” in the list.", "network" to p.ssid))
                            }
                        }
                    }, modifier = Modifier.testTag("joinWifi")) { Text(tr("Join this network")) }
                    if (p.password.isNotEmpty()) OutlinedButton(onClick = { Actions.copy(ctx, "password", p.password, secret = true); copied(tr("Password copied")) }, modifier = Modifier.testTag("copy")) { Text(tr("Copy password")) }
                }
            }
            is Payload.Contact -> {
                line(tr("Name"), p.name); line(tr("Phone"), p.safePhones.joinToString(", ")); line(tr("Email"), p.emails.joinToString(", "))
                // Q2-2: a "number" that is a phone command is shown apart, with the warning, and never saved into Contacts.
                if (p.commandPhones.isNotEmpty()) {
                    line(tr("Phone command, not a number"), p.commandPhones.joinToString(", "))
                    Text("⚠ " + tr("This card has a phone code that changes phone settings (some send all your calls to another number). It is left out of the contact."), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("warning"))
                }
                line(tr("Company"), p.org); line(tr("Address"), p.address); line(tr("Website"), p.url); line(tr("Note"), p.note)
                linksIn(p.url, p.note); addressCheck(p.emails)
                Button(onClick = { failed(Actions.addContact(ctx, p)) }, modifier = Modifier.testTag("addContact")) { Text(tr("Add to contacts")) }
            }
            is Payload.Event -> {
                val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
                line(tr("Event"), p.title)
                line(tr("Starts"), if (p.startMs > 0) df.format(Date(p.startMs)) else "")
                line(tr("Ends"), if (p.endMs > p.startMs) df.format(Date(p.endMs)) else "")
                line(tr("Where"), p.location); line(tr("Details"), p.description)
                linksIn(p.location, p.description)
                Button(onClick = { failed(Actions.addEvent(ctx, p)) }, modifier = Modifier.testTag("addEvent")) { Text(tr("Add to calendar")) }
            }
            is Payload.Phone -> {
                if (p.isCommand) {
                    // Safety round 6 Q3: * or # makes it a phone command (*21* forwards all calls); no Call button for it.
                    line(tr("Phone command, not a number"), p.number)
                    Text("⚠ " + tr("Codes like this change phone settings: some send all your calls to another number. Don't dial it unless you know what it does."), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("warning"))
                    OutlinedButton(onClick = { Actions.copy(ctx, "number", p.number); copied(tr("Copied")) }, modifier = Modifier.testTag("copy")) { Text(tr("Copy")) }
                } else {
                    line(tr("Number"), p.number)
                    Button(onClick = { failed(Actions.dial(ctx, p.number)) }, modifier = Modifier.testTag("dial")) { Text(tr("Call")) }
                }
            }
            is Payload.Sms -> {
                line(tr("To"), p.number); line(tr("Message"), p.body)
                linksIn(p.body)
                Button(onClick = { failed(Actions.sms(ctx, p)) }, modifier = Modifier.testTag("sms")) { Text(tr("Write the message")) }
            }
            is Payload.Email -> {
                line(tr("To"), p.to); line(tr("Subject"), p.subject); line(tr("Message"), p.body)
                addressCheck(p.to.split(',').map { it.trim() }); linksIn(p.subject, p.body)
                if (Content.oddAddress(p.to)) Text("⚠ " + tr("The address has characters (? & # = %) that aren't part of a normal address. It is passed on as one odd address, nothing more."), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("warning"))
                Button(onClick = { failed(Actions.email(ctx, p)) }, modifier = Modifier.testTag("email")) { Text(tr("Write the email")) }
            }
            is Payload.Geo -> {
                // dots, not the locale's decimal comma, so the two numbers can't run together (U-Q19); own key for the words (U-Q18)
                line(tr("Place"), "%.5f, %.5f".format(java.util.Locale.ROOT, p.lat, p.lng)); line(tr("Search words"), p.query)
                Button(onClick = { failed(Actions.map(ctx, p)) }, modifier = Modifier.testTag("map")) { Text(tr("Show on a map")) }
            }
            is Payload.Product -> {
                line(tr("Product number"), p.code)
                Button(onClick = { failed(Actions.search(ctx, p.code)) }, modifier = Modifier.testTag("search")) { Text(tr("Search the web for it")) }
            }
            is Payload.Text -> Text(Visible.show(p.raw), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("text"))
        }
        if (p !is Payload.Link && Visible.has(text)) Text("⚠ " + tr(Visible.WARNING), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("warning"))
        if (p !is Payload.Link) {
            HorizontalDivider()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { Actions.copy(ctx, "code", text, secret = p is Payload.Wifi); copied(tr("Copied")) }, modifier = Modifier.testTag("copyAll")) { Text(tr("Copy all")) }
                OutlinedButton(onClick = { failed(Actions.shareText(ctx, text)) }, modifier = Modifier.testTag("shareAll")) { Text(tr("Share")) }
            }
        }
        // U-Q11: any QR code here, made or scanned, can be shown again as a picture (a guest's Wi-Fi code).
        if (format == BarcodeFormat.QR_CODE && text.toByteArray().size <= Codes.MAX_BYTES)
            OutlinedButton(onClick = { showCode = true }, modifier = Modifier.testTag("showCode")) { Text(tr("Show as a code")) }
        if (onScanAgain != null) Button(onClick = onScanAgain, modifier = Modifier.testTag("scanAgain")) { Text(tr("Scan another")) } // U-Q24
        Text(tr("Read from: {f}", "f" to formatName(format)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showCode) CodeDialog(text, onClose = { showCode = false })
    openAnyway?.let { r ->
        AlertDialog(
            onDismissRequest = { openAnyway = null },
            confirmButton = { TextButton(onClick = { openAnyway = null; failed(Actions.open(ctx, r.url)) }, modifier = Modifier.testTag("openYes")) { Text(tr("Open")) } },
            dismissButton = { TextButton(onClick = { openAnyway = null }) { Text(tr("Cancel")) } },
            title = { Text(tr("Open {name}?", "name" to r.ascii)) },
            text = { Text(if (r.ascii != r.host) tr("It only looks like {name}.", "name" to r.host) else tr("It may not be the site it seems to be. Don't sign in or pay there.")) },
        )
    }
}

/** A barcode kind in words people know (U-Q19), not the library's name ("EAN_13"). */
fun formatName(f: BarcodeFormat): String = when (f) {
    BarcodeFormat.QR_CODE -> tr("QR code")
    BarcodeFormat.DATA_MATRIX -> "Data Matrix"
    BarcodeFormat.AZTEC -> "Aztec"
    BarcodeFormat.PDF_417 -> "PDF417"
    BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E ->
        tr("Product barcode ({kind})", "kind" to f.name.replace('_', '-'))
    else -> tr("Barcode ({kind})", "kind" to f.name.replace('_', ' ').replace("CODE ", "Code ").replace("CODABAR", "Codabar"))
}

/** The code drawn again, full screen, the screen at full brightness while it shows (U-Q11). */
@Composable
private fun CodeDialog(text: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val image = remember(text) { runCatching { Actions.bitmap(text, 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), null, px = 8) }.getOrNull() }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val w = (ctx as? android.app.Activity)?.window
        val before = w?.attributes?.screenBrightness
        w?.attributes = w?.attributes?.apply { screenBrightness = 1f }
        onDispose { if (w != null && before != null) w.attributes = w.attributes.apply { screenBrightness = before } }
    }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose, modifier = Modifier.testTag("codeClose")) { Text(tr("Close")) } },
        text = {
            if (image != null) Image(image.asImageBitmap(), contentDescription = tr("The code"), filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxWidth().background(Color.White).testTag("codePicture"))
            else Text(tr("Couldn't make a code from this. Try less text."))
        },
    )
}

/** Kinds of code to make, in the order shown. */
private val MAKE_KINDS = listOf("link", "text", "wifi", "contact", "event", "phone", "sms", "email")

/** Square colors (dark) and backgrounds (light) to pick from; any pair is checked for contrast. */
private val SQUARES = listOf(0xFF000000, 0xFF1B3A6B, 0xFF0B5D3B, 0xFF6B1B3A, 0xFF4A2C82, 0xFF7A3E00).map { it.toInt() }
private val BACKS = listOf(0xFFFFFFFF, 0xFFFFF8E1, 0xFFE8F5E9, 0xFFE3F2FD, 0xFFFFD54F).map { it.toInt() }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MakeScreen(env: Env, note: (String) -> Unit, onMade: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // U-Q4: what was typed survives a rotation, a theme change and a trip to another app.
    var kind by rememberSaveable { mutableStateOf("link") }
    val f = rememberSaveable(saver = FieldsSaver) { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    var security by rememberSaveable { mutableStateOf("WPA") }
    var hidden by rememberSaveable { mutableStateOf(false) }
    var fg by rememberSaveable { mutableIntStateOf(SQUARES[0]) }
    var bg by rememberSaveable { mutableIntStateOf(BACKS[0]) }
    var logo by remember { mutableStateOf<Bitmap?>(null) }
    var showPassword by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
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
    // Q2-5: the preview is built and read back off the main thread, a moment after typing stops, at a small scale
    // (6 px a square reads back fine) and inside a catch; the saved or shared picture is made full size on the tap.
    // U-Q10: the last preview stays on screen (dimmed, buttons off) while the next one is made, so nothing jumps.
    val key = listOf<Any?>(content, fg, bg, logo)
    val preview by produceState<Preview?>(null, content, fg, bg, logo) {
        if (content.isEmpty() || bytes > limit) { value = null; return@produceState }
        kotlinx.coroutines.delay(250)
        value = withContext(Dispatchers.Default) {
            val b = runCatching { Actions.bitmap(content, fg, bg, logo, px = 6) }.getOrNull()
            Preview(key, b, b != null && runCatching { Actions.readsBack(b, content) }.getOrDefault(false))
        }
    }
    val fresh = preview?.key == key
    val image: Bitmap? = preview?.image
    val reads = fresh && preview?.reads == true
    fun full(): Bitmap? = runCatching { Actions.bitmap(content, fg, bg, logo) }.getOrNull()
    /** U-Q17: the full-size picture and its PNG are made off the main thread; the button says "Saving…" meanwhile. */
    fun withFull(then: (Bitmap?) -> Unit) {
        saving = true
        scope.launch {
            val b = withContext(Dispatchers.Default) { full() }
            saving = false
            then(b)
        }
    }
    val pick = { env.pickPicture { b -> if (b != null) logo = b } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (k in MAKE_KINDS) FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(kindLabel(k)) }, modifier = Modifier.testTag("kind-$k"))
        }
        @Composable fun field(key: String, label: String, type: KeyboardType = KeyboardType.Text, lines: Int = 1, secret: Boolean = false) {
            OutlinedTextField(v(key), { f[key] = it.take(2000) }, label = { Text(label) }, singleLine = lines == 1, minLines = lines,
                keyboardOptions = KeyboardOptions(keyboardType = type),
                visualTransformation = if (secret && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
                // hidden by default (Q2-4, rule 78), with a Show so a typo can be caught (U-Q28)
                trailingIcon = if (secret) ({ TextButton(onClick = { showPassword = !showPassword }, modifier = Modifier.testTag("showPassword")) { Text(if (showPassword) tr("Hide") else tr("Show")) } }) else null,
                modifier = Modifier.fillMaxWidth().testTag("field-$key"))
        }
        when (kind) {
            "link" -> field("url", tr("Web address"), KeyboardType.Uri)
            "text" -> field("text", tr("Text"), lines = 4)
            "wifi" -> {
                field("ssid", tr("Network name"))
                field("password", tr("Password (empty for an open network)"), KeyboardType.Password, secret = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (s in listOf("WPA", "WEP")) FilterChip(selected = security == s, onClick = { security = s }, label = { Text(if (s == "WPA") "WPA/WPA2/WPA3" else s) })
                }
                ToggleRow(tr("Hidden network"), hidden, "hiddenNetwork", checkbox = true) { hidden = it }
            }
            "contact" -> {
                field("name", tr("Name")); field("phone", tr("Phone"), KeyboardType.Phone); field("email", tr("Email"), KeyboardType.Email)
                field("org", tr("Company")); field("web", tr("Website"), KeyboardType.Uri); field("address", tr("Address"))
            }
            "event" -> {
                // U-Q9: the date and time come from the phone's own pickers, in its own format; "All day" is a switch.
                field("title", tr("Event"))
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pickDate")) {
                    Text(if (v("date").isEmpty()) tr("Pick the date") else tr("Date: {d}", "d" to shownDate(v("date"))))
                }
                ToggleRow(tr("All day"), v("time").isEmpty(), "allDay") { on -> f["time"] = if (on) "" else "12:00" }
                if (v("time").isNotEmpty()) OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pickTime")) {
                    Text(tr("Time: {t}", "t" to shownTime(v("time"))))
                }
                field("hours", tr("How many hours"), KeyboardType.Decimal); field("where", tr("Where")); field("details", tr("Details"), lines = 2)
            }
            "phone" -> field("number", tr("Phone number"), KeyboardType.Phone)
            "sms" -> { field("number", tr("Phone number"), KeyboardType.Phone); field("message", tr("Message"), lines = 2) }
            "email" -> { field("to", tr("Email address"), KeyboardType.Email); field("subject", tr("Subject")); field("message", tr("Message"), lines = 2) }
        }
        // U-Q12: each row says what it colours
        Text(tr("Color of the squares"), style = MaterialTheme.typography.titleSmall)
        Swatches(SQUARES, SQUARE_NAMES, fg, "fg") { fg = it }
        Text(tr("Background color"), style = MaterialTheme.typography.titleSmall)
        Swatches(BACKS, BACK_NAMES, bg, "bg") { bg = it }
        if (colorProblem != null) Text("⚠ " + tr(colorProblem), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("colorProblem"))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = pick, modifier = Modifier.testTag("logo")) { Text(if (logo == null) tr("Add a picture in the middle") else tr("Change the picture")) }
            if (logo != null) TextButton(onClick = { logo = null }, modifier = Modifier.testTag("noLogo")) { Text(tr("Remove it")) }
        }
        when {
            content.isEmpty() -> Text(
                // U-Q9: an event says what it still needs, not "fill in the fields" when they are all filled
                if (kind == "event") tr("Give the event a name and pick its date to see your code.") else tr("Fill in the fields to see your code."),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("empty"))
            bytes > limit -> Text("⚠ " + tr("Too much for one code ({n} of {max} bytes). Shorten it, or remove the picture.", "n" to bytes, "max" to limit), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("tooLong"))
            // U-Q8: never a blank bottom: making it, or why it couldn't be made
            preview == null -> Text(tr("Making the code…"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("making"))
            image == null && fresh -> Text("⚠ " + tr("Couldn't make a code from this. Try less text."), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("cantMake"))
            image != null -> {
                Image(image.asImageBitmap(), contentDescription = tr("Your code"), filterQuality = FilterQuality.None,
                    modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = 320.dp).fillMaxWidth().alpha(if (fresh) 1f else 0.4f).testTag("preview"))
                if (!fresh) Text(tr("Updating…"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("updating"))
                else if (reads) Text("✓ " + tr("Checked: it reads back correctly."), color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("readsBack"))
                else Text("⚠ " + tr("This code didn't read back. Try other colors, a smaller picture, or less text."), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("noRead"))
                // U-Q28: what's inside a Wi-Fi code, without showing the password
                if (kind == "wifi" && fresh) Text(tr("Network: {n} · Password: {c} characters", "n" to v("ssid"), "c" to v("password").length), style = MaterialTheme.typography.bodySmall)
                val name = "qr-" + System.currentTimeMillis() / 1000
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = reads && !saving, onClick = {
                        withFull { b ->
                            if (android.os.Build.VERSION.SDK_INT >= 29) {
                                scope.launch {
                                    val ok = b?.let { withContext(Dispatchers.IO) { Actions.savePicture(ctx, it, name) } } ?: false
                                    if (ok) { env.history.add(content, kind, "QR_CODE", made = true); onMade() }
                                    note(if (ok) tr("Saved to Pictures/QR") else tr("Couldn't save it"))
                                }
                            } else {
                                // Android 8-9: Pictures needs a storage permission we don't ask for; the system's save dialog doesn't.
                                env.saveFile("$name.png", "image/png", Actions.png(b ?: image)) { ok ->
                                    if (ok) { env.history.add(content, kind, "QR_CODE", made = true); onMade() }
                                    note(if (ok) tr("Saved") else tr("Couldn't save it"))
                                }
                            }
                        }
                    }, modifier = Modifier.testTag("savePng")) { Text(if (saving) tr("Saving…") else tr("Save picture")) }
                    OutlinedButton(enabled = reads && !saving, onClick = {
                        withFull { b -> if (b?.let { Actions.sharePicture(ctx, it, name) } == true) { env.history.add(content, kind, "QR_CODE", made = true); onMade() } }
                    }, modifier = Modifier.testTag("sharePng")) { Text(tr("Share")) }
                    OutlinedButton(enabled = reads, onClick = {
                        val svg = Codes.svg(Codes.matrix(content, logo != null), fg, bg, logo?.let { Actions.png(it) })
                        env.saveFile("$name.svg", "image/svg+xml", svg.toByteArray()) { ok -> note(if (ok) tr("Saved") else tr("Couldn't save it")) }
                    }, modifier = Modifier.testTag("saveSvg")) { Text(tr("Save as SVG")) }
                }
                Text(tr("Codes made here never expire and don't go through any server: what you typed is inside the picture."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Text(tr("Making the code…"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("making"))
        }
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoToUtcMs(v("date")) ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { f["date"] = utcMsToIso(it) }; pickDate = false }, modifier = Modifier.testTag("dateOk")) { Text(tr("OK")) } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(tr("Cancel")) } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val (h0, m0) = v("time").split(':').let { (it.getOrNull(0)?.toIntOrNull() ?: 12) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
        val state = rememberTimePickerState(initialHour = h0, initialMinute = m0, is24Hour = android.text.format.DateFormat.is24HourFormat(ctx))
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = { TextButton(onClick = { f["time"] = "%02d:%02d".format(java.util.Locale.ROOT, state.hour, state.minute); pickTime = false }, modifier = Modifier.testTag("timeOk")) { Text(tr("OK")) } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(tr("Cancel")) } },
            text = { TimePicker(state) },
        )
    }
}

/** One preview of Make: what it was made from, the picture (null when it couldn't be made), whether it reads back. */
private class Preview(val key: List<Any?>, val image: Bitmap?, val reads: Boolean)

/** Make's typed fields as a saveable map (U-Q4). */
private val FieldsSaver = androidx.compose.runtime.saveable.Saver<androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>, ArrayList<String>>(
    save = { m -> ArrayList(m.flatMap { listOf(it.key, it.value) }) },
    restore = { l -> androidx.compose.runtime.mutableStateMapOf<String, String>().apply { l.chunked(2).forEach { if (it.size == 2) put(it[0], it[1]) } } },
)

/** "2026-12-15" <-> the picker's UTC midnight. */
fun isoToUtcMs(d: String): Long? {
    val m = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""").find(d.trim()) ?: return null
    val (y, mo, da) = m.destructured
    return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { clear(); set(y.toInt(), mo.toInt() - 1, da.toInt()) }.timeInMillis
}

fun utcMsToIso(ms: Long): String {
    val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
    return "%04d-%02d-%02d".format(java.util.Locale.ROOT, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
}

/** A stored date in the phone's own style ("15 dic 2026"). */
fun shownDate(d: String): String {
    val ms = isoToUtcMs(d) ?: return d
    return DateFormat.getDateInstance(DateFormat.MEDIUM).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(Date(ms))
}

/** A stored "HH:MM" in the phone's own style. */
fun shownTime(t: String): String {
    val (h, m) = t.split(':').let { (it.getOrNull(0)?.toIntOrNull() ?: 0) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
    val c = java.util.Calendar.getInstance().apply { clear(); set(2000, 0, 1, h, m) }
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(c.time)
}

/** An event from typed date/time; "" until the date reads. */
fun eventContent(title: String, date: String, time: String, hours: String, where: String, details: String): String {
    if (title.isBlank()) return ""
    val d = Regex("""^\s*(\d{4})-(\d{1,2})-(\d{1,2})\s*$""").find(date) ?: return ""
    val (y, mo, da) = d.destructured
    // not lenient: 2026-02-31 is refused, never rolled over into March (U-Q9)
    val cal = java.util.Calendar.getInstance().apply { clear(); isLenient = false; set(y.toInt(), mo.toInt() - 1, da.toInt()) }
    if (runCatching { cal.timeInMillis }.isFailure) return ""
    val t = Regex("""^\s*(\d{1,2}):(\d{2})\s*$""").find(time)
    val allDay = t == null
    if (t != null) { cal.set(java.util.Calendar.HOUR_OF_DAY, t.groupValues[1].toInt()); cal.set(java.util.Calendar.MINUTE, t.groupValues[2].toInt()) }
    val start = runCatching { cal.timeInMillis }.getOrNull() ?: return ""
    val len = ((hours.replace(',', '.').toDoubleOrNull() ?: 1.0).coerceIn(0.0, 24.0 * 30) * 3_600_000).toLong()
    return Content.event(title, start, if (allDay) start + 86_400_000L else start + len, allDay, where, details)
}

val SQUARE_NAMES = listOf("Black", "Dark blue", "Dark green", "Wine", "Purple", "Brown")
val BACK_NAMES = listOf("White", "Cream", "Light green", "Light blue", "Yellow")

/** U-Q12: 48 dp targets, each one named and marked as a choice, the chosen one with a tick too (not the border alone). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(colors: List<Int>, names: List<String>, chosen: Int, tag: String, pick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((i, c) in colors.withIndex()) {
            val sel = c == chosen
            val name = tr(names[i])
            Box(Modifier.size(48.dp).selectable(sel, role = Role.RadioButton) { pick(c) }.semantics { contentDescription = name }.testTag("$tag-${Integer.toHexString(c)}"),
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(38.dp).background(Color(c), CircleShape)
                    .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                    contentAlignment = Alignment.Center) {
                    // a light swatch gets a dark tick and a dark one a light tick
                    if (sel) Text("✓", color = if (Codes.luminance(c) > 0.5) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** A switch or tick whose whole row, words included, is the control (U-Q21, APP-RULES 167). */
@Composable
private fun ToggleRow(label: String, on: Boolean, tag: String, checkbox: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(on, role = if (checkbox) Role.Checkbox else Role.Switch, onValueChange = onChange).testTag(tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (checkbox) { Checkbox(on, null); Text(label, Modifier.weight(1f)) }
        else { Text(label, Modifier.weight(1f)); Switch(on, null) }
    }
}

@Composable
private fun HistoryScreen(env: Env, version: Int, onChanged: () -> Unit, onOpen: (History.Entry) -> Unit, note: (String) -> Unit,
                          undoable: (String, () -> Unit) -> Unit, onScan: () -> Unit) {
    val ctx = LocalContext.current
    var q by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var on by remember { mutableStateOf(env.history.enabled) }
    val list = remember(version, q) { if (q.isBlank()) env.history.all() else env.history.search(q) }
    // Back clears a typed search first (APP-RULES 146); the app-wide handler then goes Home.
    BackHandler(enabled = q.isNotEmpty()) { q = "" }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ToggleRow(tr("Keep a history on this phone"), on, "historyOn") { on = it; env.history.enabled = it; onChanged() }
        // U-Q20: turning it off keeps what is already here; say so
        if (!on && env.history.all().isNotEmpty()) Text(tr("Codes already here stay until you clear them."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(q, { q = it }, label = { Text(tr("Search")) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("search"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            OutlinedButton(enabled = list.isNotEmpty(), onClick = {
                env.saveFile("qr-history.csv", "text/csv", env.history.csv().toByteArray()) { ok -> note(if (ok) tr("Saved") else tr("Couldn't save it")) }
            }, modifier = Modifier.testTag("exportCsv")) { Text(tr("Save as a spreadsheet")) }
            TextButton(enabled = env.history.all().isNotEmpty(), onClick = { confirmClear = true }, modifier = Modifier.testTag("clear")) { Text(tr("Clear all")) }
        }
        // Safety round 6 Q4: the spreadsheet holds each code's full text, Wi-Fi passwords included.
        if (list.any { it.kind == "wifi" }) Text(tr("The spreadsheet includes Wi-Fi passwords."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (list.isEmpty() && q.isBlank()) Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // U-Q20: the empty list says what fills it and offers the way to start
            Text(tr("Codes you scan or make show up here."))
            Button(onClick = onScan, modifier = Modifier.testTag("emptyScan")) { Text(tr("Scan a code")) }
        } else if (list.isEmpty()) Text(tr("Nothing matches."), modifier = Modifier.padding(top = 16.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.time.toString() + it.made + it.text.hashCode() }) { e ->
                EntryRow(e, onOpen) {
                    // U-Q13: a delete can be taken back
                    env.history.remove(it); onChanged()
                    val what = Payload.of(it.text, runCatching { BarcodeFormat.valueOf(it.format) }.getOrDefault(BarcodeFormat.QR_CODE)).summary()
                    undoable(tr("Deleted “{what}”", "what" to what.take(40))) { env.history.restore(it); onChanged() }
                }
                HorizontalDivider()
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        confirmButton = { TextButton(onClick = { env.history.clear(); Actions.clearExports(ctx); confirmClear = false; onChanged() }, modifier = Modifier.testTag("clearYes")) { Text(tr("Clear")) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(tr("Cancel")) } },
        title = { val n = env.history.all().size; Text(if (n == 1) tr("Clear the 1 code in the history?") else tr("Clear all {n} codes in the history?", "n" to n)) },
        text = { Text(tr("Every code in the history is removed from this phone. This can't be undone.")) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedbackDialog(env: Env, onSent: () -> Unit, onClose: () -> Unit) {
    var kind by remember { mutableStateOf("bug") }
    var message by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var include by remember { mutableStateOf(true) }
    var preview by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val details = remember { Feedback.details(env.history) }
    val body = Feedback.json(kind, message, email, env.version, if (include) details else null)
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onClose,
        // a tap beside the dialog never throws a typed message away (U-Q22)
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = message.isBlank()),
        confirmButton = {
            TextButton(enabled = !sending, modifier = Modifier.testTag("feedbackSend"), onClick = {
                if (message.isBlank()) { error = tr("Write a few words first."); return@TextButton }
                sending = true; error = ""
                scope.launch {
                    val problem = withContext(Dispatchers.IO) { Feedback.send(body, env.version) }
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { // wraps on a narrow dialog or large text (U-Q22)
                    for ((k, label) in Feedback.kinds) FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(tr(label)) }, modifier = Modifier.testTag("feedbackKind-$k"))
                }
                OutlinedTextField(message, { message = it.take(4000) }, placeholder = { Text(tr("What happened, or what would make it better?")) }, minLines = 4, modifier = Modifier.fillMaxWidth().testTag("feedbackMessage"))
                OutlinedTextField(email, { email = it.take(254) }, placeholder = { Text(tr("Your email, if you'd like an answer (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("feedbackEmail"))
                ToggleRow(tr("Include technical details"), include, "feedbackDetails", checkbox = true) { include = it }
                Text(tr("App version, system, and how many codes are in the history. Never what the codes say."), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { preview = !preview }, modifier = Modifier.testTag("feedbackSee")) { Text(if (preview) tr("Hide what's sent") else tr("See what's sent")) }
                if (preview) Text(body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("feedbackPreview"))
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("feedbackError"))
            }
        },
    )
}
