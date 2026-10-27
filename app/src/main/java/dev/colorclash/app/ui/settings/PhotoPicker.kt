package dev.colorclash.app.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.colorclash.app.data.Profile
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.app.ui.theme.LocalTableStyle
import dev.colorclash.protocol.MAX_PHOTO_CHARS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.max
import androidx.compose.ui.geometry.Rect as ComposeRect

/** Pick a picture from the phone and crop it into the circle. */
@Composable
fun PhotoTab(profile: Profile, onChange: (Profile) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<Bitmap?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            picked = withContext(Dispatchers.IO) { loadPhoto(context, uri) }
            problem = if (picked == null) "Couldn't open that picture. Try another one." else null
        }
    }
    val choose = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (profile.photo == null) "Use any picture from your phone: your favourite character, yourself, anything."
            else "Everyone at the table sees this photo instead of an avatar.",
            color = GameTheme.mutedText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        BigButton(if (profile.photo == null) "Choose a photo" else "Choose another photo", GameTheme.blue, choose)
        if (profile.photo != null) {
            BigButton("Use an avatar instead", GameTheme.panelStrong, { onChange(profile.copy(photo = null)) })
        }
        problem?.let { Text(it, color = GameTheme.yellow, style = MaterialTheme.typography.bodyMedium) }
    }

    picked?.let { bitmap ->
        PhotoCropper(
            bitmap,
            onDone = { data ->
                picked = null
                if (data == null) problem = "That picture didn't work. Try another one." else onChange(profile.copy(photo = data))
            },
            onCancel = { picked = null },
        )
    }
}

@Composable
private fun PhotoCropper(bitmap: Bitmap, onDone: (String?) -> Unit, onCancel: () -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var view by remember(bitmap) { mutableStateOf(CropView()) }
    var viewport by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    var saving by remember(bitmap) { mutableStateOf(false) }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(LocalTableStyle.current.top)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Drag and pinch to fit the circle", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))) {
                val width = constraints.maxWidth.toFloat()
                SideEffect { viewport = width }
                androidx.compose.foundation.Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(bitmap) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                view = CropView(view.zoom * zoom, view.panX + pan.x, view.panY + pan.y)
                                    .clamped(bitmap.width, bitmap.height, viewport)
                            }
                        },
                ) {
                    val box = cropBox(bitmap.width, bitmap.height, size.width, view)
                    drawImage(
                        image,
                        srcOffset = IntOffset(box.left, box.top),
                        srcSize = IntSize(box.size, box.size),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    )
                    // Dim what gets cut off.
                    val whole = ComposeRect(Offset.Zero, size)
                    val outside = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(whole)
                        addOval(whole)
                    }
                    drawPath(outside, Color.Black.copy(alpha = 0.55f))
                    drawCircle(Color.White, size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("Cancel", GameTheme.panelStrong, onCancel, Modifier.weight(1f))
                BigButton(
                    if (saving) "Saving…" else "Use photo",
                    GameTheme.green,
                    {
                        if (saving) return@BigButton
                        saving = true
                        // A bitmap redraw and up to three JPEG passes: never on the main thread.
                        val box = cropBox(bitmap.width, bitmap.height, viewport, view)
                        scope.launch {
                            val data = withContext(Dispatchers.Default) { encodePhoto(bitmap, box) }
                            saving = false
                            onDone(data)
                        }
                    },
                    Modifier.weight(1f),
                )
            }
        }
    }
}

/** Opens a picture the right way up, scaled down so a big camera shot doesn't eat the phone's memory. */
private fun loadPhoto(context: Context, uri: Uri): Bitmap? = runCatching {
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // ImageDecoder turns the picture upright by itself.
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > LOAD_PIXELS) decoder.setTargetSampleSize(longest / LOAD_PIXELS)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LOAD_PIXELS) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        val turn = resolver.openInputStream(uri)?.use { exifRotation(ExifInterface(it)) } ?: 0
        if (bitmap == null || turn == 0) bitmap
        else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(turn.toFloat()) }, true)
    }
}.getOrNull()

private fun exifRotation(exif: ExifInterface): Int = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
    ExifInterface.ORIENTATION_ROTATE_90 -> 90
    ExifInterface.ORIENTATION_ROTATE_180 -> 180
    ExifInterface.ORIENTATION_ROTATE_270 -> 270
    else -> 0
}

/** Cuts [box] out of the picture and packs it as the small JPEG other phones get, in base64. */
private fun encodePhoto(bitmap: Bitmap, box: CropBox): String? = runCatching {
    val out = Bitmap.createBitmap(PHOTO_PIXELS, PHOTO_PIXELS, Bitmap.Config.ARGB_8888)
    try {
        Canvas(out).apply {
            // JPEG has no transparency; see-through bits of a PNG come out white rather than black.
            drawColor(android.graphics.Color.WHITE)
            val source = Rect(box.left, box.top, box.left + box.size, box.top + box.size)
            drawBitmap(bitmap, source, Rect(0, 0, PHOTO_PIXELS, PHOTO_PIXELS), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        listOf(85, 70, 55).firstNotNullOfOrNull { quality ->
            val bytes = ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            Base64.getEncoder().encodeToString(bytes).takeIf { it.length <= MAX_PHOTO_CHARS }
        }
    } finally {
        out.recycle()
    }
}.getOrNull()

private const val LOAD_PIXELS = 1024
private const val PHOTO_PIXELS = 160
