package ir.xilo.app.ui.settings

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.xilo.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun AvatarCropDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (ByteArray) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    // Holders (not delegated `by`) so gesture lambdas always read the latest values.
    val userScale = remember { mutableFloatStateOf(1f) }
    val offsetX = remember { mutableFloatStateOf(0f) }
    val offsetY = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(imageUri) {
        loadFailed = false
        bitmap = null
        userScale.floatValue = 1f
        offsetX.floatValue = 0f
        offsetY.floatValue = 0f
        // Use Activity context so temporary Photo Picker URI grants remain valid.
        val appContext = context.applicationContext
        bitmap = withContext(Dispatchers.IO) {
            AvatarImageDecoder.decode(context, imageUri)
                ?: AvatarImageDecoder.decode(appContext, imageUri)
        }
        if (bitmap == null) {
            loadFailed = true
        }
    }

    Dialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isExporting,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
        ) {
            val source = bitmap
            when {
                loadFailed -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.avatar_crop_load_failed),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.avatar_crop_cancel))
                        }
                    }
                }

                source == null -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White,
                    )
                }

                else -> {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val containerW = constraints.maxWidth.toFloat()
                        val containerH = constraints.maxHeight.toFloat()
                        val cropDiameter = min(containerW, containerH) * 0.72f
                        val base = AvatarCropMath.baseScale(source.width, source.height, cropDiameter)
                        val imageBitmap = remember(source) { source.asImageBitmap() }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(source, cropDiameter) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val nextScale = AvatarCropMath.clampUserScale(
                                            userScale.floatValue * zoom
                                        )
                                        val (nx, ny) = AvatarCropMath.clampOffset(
                                            offsetX = offsetX.floatValue + pan.x,
                                            offsetY = offsetY.floatValue + pan.y,
                                            bitmapWidth = source.width,
                                            bitmapHeight = source.height,
                                            cropDiameter = cropDiameter,
                                            userScale = nextScale,
                                        )
                                        userScale.floatValue = nextScale
                                        offsetX.floatValue = nx
                                        offsetY.floatValue = ny
                                    }
                                }
                        ) {
                            val displayScale = base * userScale.floatValue
                            val drawnW = source.width * displayScale
                            val drawnH = source.height * displayScale
                            val left = (size.width - drawnW) / 2f + offsetX.floatValue
                            val top = (size.height - drawnH) / 2f + offsetY.floatValue
                            drawImage(
                                image = imageBitmap,
                                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                dstSize = IntSize(
                                    drawnW.roundToInt().coerceAtLeast(1),
                                    drawnH.roundToInt().coerceAtLeast(1),
                                ),
                            )

                            val circleCenter = Offset(size.width / 2f, size.height / 2f)
                            val radius = cropDiameter / 2f
                            val hole = Path().apply {
                                addOval(
                                    Rect(
                                        circleCenter.x - radius,
                                        circleCenter.y - radius,
                                        circleCenter.x + radius,
                                        circleCenter.y + radius,
                                    )
                                )
                            }
                            clipPath(hole, ClipOp.Difference) {
                                drawRect(Color.Black.copy(alpha = 0.62f))
                            }
                            drawCircle(
                                color = Color.White.copy(alpha = 0.9f),
                                radius = radius,
                                center = circleCenter,
                                style = Stroke(width = 2.dp.toPx()),
                            )
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp, start = 20.dp, end = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.avatar_crop_title),
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.avatar_crop_hint),
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                enabled = !isExporting,
                            ) {
                                Text(
                                    text = stringResource(R.string.avatar_crop_cancel),
                                    color = Color.White,
                                )
                            }
                            TextButton(
                                onClick = {
                                    if (isExporting) return@TextButton
                                    isExporting = true
                                    scope.launch {
                                        val bytes = withContext(Dispatchers.Default) {
                                            val cropped = AvatarCropMath.renderSquareCrop(
                                                source = source,
                                                cropDiameter = cropDiameter,
                                                userScale = userScale.floatValue,
                                                offsetX = offsetX.floatValue,
                                                offsetY = offsetY.floatValue,
                                            )
                                            try {
                                                ByteArrayOutputStream().use { stream ->
                                                    cropped.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                                    stream.toByteArray()
                                                }
                                            } finally {
                                                if (cropped !== source) {
                                                    cropped.recycle()
                                                }
                                            }
                                        }
                                        isExporting = false
                                        onConfirm(bytes)
                                    }
                                },
                                enabled = !isExporting,
                            ) {
                                if (isExporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.avatar_crop_confirm),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

