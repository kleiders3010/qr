package com.jurisgames.qr

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.google.zxing.BarcodeFormat
import java.io.File

class MainActivity : ComponentActivity() {
    private var onScanned: ((Pair<String, BarcodeFormat>?) -> Unit)? = null
    private var onPicture: ((Bitmap?) -> Unit)? = null
    private var pendingSave: Pair<ByteArray, (Boolean) -> Unit>? = null
    private val start = mutableStateOf<Pair<Screen, String?>>(Screen.Home to null)

    private val scanner = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringExtra("text")
        val format = r.data?.getStringExtra("format")?.let { runCatching { BarcodeFormat.valueOf(it) }.getOrNull() } ?: BarcodeFormat.QR_CODE
        onScanned?.invoke(text?.let { it to format })
        if (r.data?.getBooleanExtra("denied", false) == true) start.value = Screen.Home to tr("Scanning needs the camera. You can allow it in the phone's settings, or read a code from a picture.")
    }
    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> onPicture?.invoke(uri?.let(::load)) }
    private val saver = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val (bytes, done) = pendingSave ?: return@registerForActivityResult
        pendingSave = null
        done(uri != null && runCatching { contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null }.getOrDefault(false))
    }

    /** A picture as a software bitmap, at most 2000 px on its long side (enough to read any code in it). */
    private fun load(uri: Uri): Bitmap? = runCatching {
        val b = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val long = maxOf(info.size.width, info.size.height)
                if (long > 2000) d.setTargetSize(info.size.width * 2000 / long, info.size.height * 2000 / long)
            }
        } else {
            @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }
        b.copy(Bitmap.Config.ARGB_8888, false)
    }.getOrNull()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val history = History(File(filesDir, "history"), File(filesDir, "history-setting"))
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
        handleShared(intent)
        val env = Env(
            history = history,
            version = version,
            scan = { cb -> onScanned = cb; scanner.launch(Intent(this, ScanActivity::class.java)) },
            pickPicture = { cb -> onPicture = cb; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            saveFile = { name, _, bytes, done -> pendingSave = bytes to done; saver.launch(name) },
        )
        setContent {
            QrTheme {
                val (screen, note) = start.value
                androidx.compose.runtime.key(screen, note) { QrApp(env, screen, note) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShared(intent)
    }

    /** A picture shared to QR from another app (a screenshot, a photo): its code is read here, on this phone. */
    private fun handleShared(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return
        @Suppress("DEPRECATION") val uri = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableExtra(Intent.EXTRA_STREAM)) ?: return
        val found = load(uri)?.let { Decode.fromBitmap(it) }
        start.value = if (found == null) Screen.Home to tr("No code found in this picture.") else {
            val h = History(File(filesDir, "history"), File(filesDir, "history-setting"))
            h.add(found.text, Payload.of(found.text, found.format).kind, found.format.name, made = false)
            Screen.Result(found.text, found.format) to null
        }
    }
}
