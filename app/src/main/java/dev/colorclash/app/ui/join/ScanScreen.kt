package dev.colorclash.app.ui.join

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.components.ButtonTone
import dev.colorclash.app.ui.components.HintText
import dev.colorclash.app.ui.components.NoticeBar
import dev.colorclash.app.ui.components.ScreenHeader
import dev.colorclash.app.ui.theme.GAME_TITLE
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.protocol.JoinLink
import java.util.concurrent.Executors

@Composable
fun ScanScreen(onBack: () -> Unit, onScanned: (JoinLink) -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var hint by remember { mutableStateOf("Point at the QR code on the host's screen") }
    var cameraFailed by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = GameTheme.screenPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(GameTheme.sectionGap),
    ) {
        ScreenHeader("Scan to join", onBack)
        when {
            !granted -> {
                HintText("The camera is needed to scan the host's QR code.")
                BigButton("Allow camera", ButtonTone.PRIMARY, { launcher.launch(Manifest.permission.CAMERA) })
            }
            cameraFailed -> NoticeBar("Couldn't open the camera on this phone. Go back and type the room code instead.")
            else -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(GameTheme.sheetShape)
                        .border(4.dp, GameTheme.highlight, GameTheme.sheetShape),
                ) {
                    QrCamera(
                        onText = { text ->
                            val link = JoinLink.parse(text)
                            if (link != null) onScanned(link) else hint = "That's not a $GAME_TITLE code. Try the one in the host's lobby."
                        },
                        onFailed = { cameraFailed = true },
                    )
                }
                HintText(hint, Modifier.fillMaxWidth(), TextAlign.Center)
            }
        }
    }
}

@Composable
private fun QrCamera(onText: (String) -> Unit, onFailed: () -> Unit) {
    val context = LocalContext.current
    val latestOnFailed by rememberUpdatedState(onFailed)
    // The hosting Context is not contractually a LifecycleOwner: under a Dialog, an AndroidView
    // subcomposition or a preview it isn't, and the cast this replaces was an outright crash.
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnText by rememberUpdatedState(onText)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val scanner = QrScanner()
        // getInstance() resolves asynchronously. Leaving the screen inside that window used to skip
        // the unbind and then bind the camera for a screen that no longer existed.
        var disposed = false
        var bound: ProcessCameraProvider? = null
        var analysis: ImageAnalysis? = null
        providerFuture.addListener({
            if (disposed) return@addListener
            // The camera isn't required to install this, and both of these throw without a usable one.
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val frames = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                frames.setAnalyzer(executor) { image ->
                    // Closed whatever happens: the camera stops sending frames until it is.
                    val text = try {
                        scanner.scan(image)
                    } finally {
                        image.close()
                    }
                    if (text != null) mainExecutor.execute { latestOnText(text) }
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, frames)
                bound = provider
                analysis = frames
            } catch (e: Exception) {
                Log.w(TAG, "Camera unavailable", e)
                latestOnFailed()
            }
        }, mainExecutor)
        onDispose {
            disposed = true
            analysis?.clearAnalyzer()
            bound?.unbindAll()
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

private const val TAG = "ScanScreen"

/**
 * One scanner per camera session, holding the reader and one luminance buffer. A fresh array per
 * frame was megabytes a second of large short-lived garbage, and the GC pauses showed in the preview.
 * Only ever touched from the analyzer's single thread.
 */
private class QrScanner {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }
    private var luminance = ByteArray(0)
    private var lastText: String? = null

    /** QR codes read fine at any rotation, so the raw luminance plane is enough. */
    fun scan(image: ImageProxy): String? {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val size = buffer.remaining()
        if (luminance.size < size) luminance = ByteArray(size)
        buffer.get(luminance, 0, size)
        val source = PlanarYUVLuminanceSource(
            luminance, plane.rowStride, image.height, 0, 0, image.width, image.height, false,
        )
        val text = try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (e: ReaderException) {
            null // no code in this frame, or a blurry one
        } finally {
            reader.reset()
        }
        if (text == null || text == lastText) return null
        lastText = text
        return text
    }
}
