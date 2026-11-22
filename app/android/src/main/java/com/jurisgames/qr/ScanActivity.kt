package com.jurisgames.qr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/** Reading codes from camera frames and pictures, with ZXing (no Google Play services). */
object Decode {
    val FORMATS = listOf(
        BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX, BarcodeFormat.AZTEC, BarcodeFormat.PDF_417,
        BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
        BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.ITF, BarcodeFormat.CODABAR,
    )
    private val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to FORMATS, DecodeHintType.TRY_HARDER to true, DecodeHintType.CHARACTER_SET to "UTF-8")

    data class Found(val text: String, val format: BarcodeFormat)

    private fun read(src: LuminanceSource): Found? {
        val r = MultiFormatReader().apply { setHints(hints) }
        for (s in listOf(src, src.invert())) { // light-on-dark codes too
            val got = runCatching { r.decodeWithState(BinaryBitmap(HybridBinarizer(s))) }.getOrNull()
            if (got != null) return Found(got.text, got.barcodeFormat)
            r.reset()
        }
        return null
    }

    fun fromLuminance(y: ByteArray, rowStride: Int, width: Int, height: Int): Found? =
        read(PlanarYUVLuminanceSource(y, rowStride, height, 0, 0, width, height, false))

    /**
     * The frame turned a quarter (UX review U-Q2): camera frames come in the sensor's landscape orientation, and
     * ZXing's product-barcode readers only scan rows, so a barcode held level in portrait reads only this way.
     */
    fun rotated(y: ByteArray, rowStride: Int, width: Int, height: Int): ByteArray {
        val out = ByteArray(width * height)
        for (r in 0 until height) {
            val base = r * rowStride
            for (c in 0 until width) out[c * height + (height - 1 - r)] = y[base + c]
        }
        return out
    }

    /** A picture (screenshot, photo): tried as it is, then smaller (big photos read better scaled down). */
    fun fromBitmap(b: Bitmap): Found? {
        var bmp = b
        repeat(3) {
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            read(RGBLuminanceSource(bmp.width, bmp.height, px))?.let { return it }
            if (bmp.width < 600) return null
            bmp = Bitmap.createScaledBitmap(bmp, bmp.width / 2, bmp.height / 2, true)
        }
        return null
    }
}

/** The camera screen: preview, and the first code it reads goes back (the camera is asked for only here). */
class ScanActivity : ComponentActivity() {
    private val analysis = Executors.newSingleThreadExecutor()
    @Volatile private var done = false
    private var frames = 0
    private var hint: TextView? = null
    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else { setResult(RESULT_CANCELED, Intent().putExtra("denied", true)); finish() }
    }
    /** "Read from a picture" on the camera screen (U-Q7): the code from the picture goes back like a scan. */
    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        Thread {
            val found = loadPicture(this, uri)?.let { Decode.fromBitmap(it) }
            runOnUiThread {
                if (found != null && !done) give(found)
                else if (found == null) hint?.text = tr("No code found in this picture.")
            }
        }.start()
    }

    private fun give(found: Decode.Found) {
        done = true
        setResult(RESULT_OK, Intent().putExtra("text", found.text).putExtra("format", found.format.name))
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) start() else askCamera.launch(Manifest.permission.CAMERA)
    }

    private fun start() {
        val view = PreviewView(this)
        val hint = TextView(this).apply {
            text = tr("Point the camera at a code")
            setTextColor(0xFFFFFFFF.toInt())
            setShadowLayer(6f, 0f, 0f, 0xFF000000.toInt())
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(8)) // dp, not raw pixels (U-Q7)
        }
        this.hint = hint
        // U-Q7: a way out, another way in, and light for a dim table; each at least 48 dp
        fun button(label: String, onClick: () -> Unit) = android.widget.Button(this).apply {
            text = label
            minHeight = dp(48); minimumHeight = dp(48)
            isAllCaps = false
            setOnClickListener { onClick() }
        }
        val torch = button(tr("Light")) {}.apply { visibility = android.view.View.GONE }
        val bar = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(32))
            addView(button(tr("Cancel")) { finish() })
            addView(button(tr("Read a code from a picture")) {
                picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            addView(torch)
        }
        val bottom = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(hint)
            addView(bar)
        }
        setContentView(FrameLayout(this).apply {
            addView(view)
            addView(bottom, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        })
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            val reader = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            reader.setAnalyzer(analysis) { frame ->
                val plane = frame.planes[0]
                val bytes = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
                val turned = frame.imageInfo.rotationDegrees % 180 != 0
                // every other frame is read turned a quarter when the phone is upright (U-Q2): 2D codes read either way,
                // product barcodes only along the rows
                val found = when {
                    done -> null
                    turned && frames++ % 2 == 1 -> Decode.fromLuminance(Decode.rotated(bytes, plane.rowStride, frame.width, frame.height), frame.height, frame.height, frame.width)
                    else -> Decode.fromLuminance(bytes, plane.rowStride, frame.width, frame.height)
                }
                frame.close()
                if (found != null && !done) runOnUiThread { if (!done) give(found) }
            }
            provider.unbindAll()
            val camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, reader)
            if (camera.cameraInfo.hasFlashUnit()) {
                var on = false
                torch.visibility = android.view.View.VISIBLE
                torch.setOnClickListener {
                    on = !on
                    camera.cameraControl.enableTorch(on)
                    torch.text = if (on) tr("Light off") else tr("Light")
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        analysis.shutdown()
        super.onDestroy()
    }
}
