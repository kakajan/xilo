package ir.xilo.app.ui.postdetail

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.xilo.app.R
import ir.xilo.app.theme.ColorSuccess
import ir.xilo.app.theme.IranSansXFontFamily
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import kotlinx.coroutines.delay

private val PlaybackRates = floatArrayOf(1f, 1.25f, 1.5f)

@Composable
fun PostAudioPlayer(
    audioUrl: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    var playing by remember(audioUrl) { mutableStateOf(false) }
    var positionMs by remember(audioUrl) { mutableFloatStateOf(0f) }
    var durationMs by remember(audioUrl) { mutableFloatStateOf(0f) }
    var rateIndex by remember(audioUrl) { mutableIntStateOf(0) }
    var userSeeking by remember(audioUrl) { mutableStateOf(false) }
    var ready by remember(audioUrl) { mutableStateOf(false) }
    var loadError by remember(audioUrl) { mutableStateOf(false) }

    val player = remember(audioUrl) { MediaPlayer() }

    DisposableEffect(audioUrl, player) {
        if (audioUrl.isBlank()) {
            loadError = true
            ready = false
        } else {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            player.setOnPreparedListener {
                ready = true
                loadError = false
                durationMs = it.duration.coerceAtLeast(0).toFloat()
            }
            player.setOnCompletionListener {
                playing = false
                positionMs = 0f
                it.seekTo(0)
            }
            player.setOnErrorListener { _, _, _ ->
                playing = false
                ready = false
                loadError = true
                true
            }
            runCatching {
                player.setDataSource(audioUrl)
                player.prepareAsync()
            }.onFailure {
                ready = false
                loadError = true
            }
        }
        onDispose {
            runCatching {
                if (player.isPlaying) player.stop()
                player.reset()
                player.release()
            }
        }
    }

    LaunchedEffect(player, userSeeking, ready) {
        while (ready) {
            if (!userSeeking) {
                runCatching {
                    playing = player.isPlaying
                    positionMs = player.currentPosition.toFloat()
                    durationMs = player.duration.coerceAtLeast(0).toFloat()
                }
            }
            delay(250)
        }
    }

    LaunchedEffect(rateIndex, ready) {
        if (ready) {
            runCatching {
                player.playbackParams = player.playbackParams.setSpeed(PlaybackRates[rateIndex])
            }
        }
    }

    val progressFraction = if (durationMs > 0f) {
        (positionMs / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = if (userSeeking) 0 else 220),
        label = "audioProgress",
    )

    val shimmer = rememberInfiniteTransition(label = "progressShimmer")
    val shimmerShift by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "progressShimmerShift",
    )
    // Tip of the progress gently breathes between green and theme blue.
    val edgeBlueMix by shimmer.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "progressEdgeBlueMix",
    )
    val edgeGlow by shimmer.animateFloat(
        initialValue = 0.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "progressEdgeGlow",
    )
    val playingPulse by shimmer.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "playingPulse",
    )

    val seekEnabled = ready && !loadError && durationMs > 0f
    val latestDuration by rememberUpdatedState(durationMs)
    val latestSeekEnabled by rememberUpdatedState(seekEnabled)
    val latestPlayer by rememberUpdatedState(player)

    fun applySeekFraction(fraction: Float) {
        if (!latestSeekEnabled) return
        val duration = latestDuration
        if (duration <= 0f) return
        val clamped = fraction.coerceIn(0f, 1f)
        positionMs = clamped * duration
        runCatching { latestPlayer.seekTo(positionMs.toInt()) }
    }

    val playPauseLabel = stringResource(
        if (playing) R.string.post_audio_pause else R.string.post_audio_play,
    )
    val playTint by animateColorAsState(
        targetValue = if (playing) ColorSuccess else XiloBlue,
        animationSpec = tween(180),
        label = "playTint",
    )

    val surface = MaterialTheme.colorScheme.surface

    // Compact chrome: no elevation; opaque at top of the bar, fades to transparent at its bottom.
    Column(modifier = modifier.fillMaxWidth()) {
        // Media chrome is always LTR so progress, seek, and trailing play stay consistent.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to surface.copy(alpha = 0.96f),
                                0.50f to surface.copy(alpha = 0.82f),
                                0.82f to surface.copy(alpha = 0.28f),
                                1.0f to Color.Transparent,
                            ),
                        ),
                    ),
            ) {
                // Thin progress track along the top edge (green → theme blue at the tip).
                val tipColor = lerp(ColorSuccess, XiloBlue, edgeBlueMix)
                val midColor = lerp(ColorSuccess, XiloBlue, edgeBlueMix * 0.45f)
                val tipAlpha = (0.78f + edgeGlow * 0.18f).coerceIn(0.75f, 0.98f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .align(Alignment.TopCenter)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                            .align(AbsoluteAlignment.CenterLeft)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0.0f to ColorSuccess.copy(alpha = 0.35f),
                                        0.40f to ColorSuccess.copy(
                                            alpha = 0.55f + shimmerShift * 0.12f,
                                        ),
                                        0.72f to midColor.copy(alpha = 0.78f + edgeGlow * 0.10f),
                                        0.90f to tipColor.copy(alpha = tipAlpha * 0.92f),
                                        1.0f to tipColor.copy(alpha = tipAlpha),
                                    ),
                                ),
                            ),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
                ) {
                        // Seekable content cluster (title + time).
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .pointerInput(seekEnabled) {
                                    detectTapGestures { offset ->
                                        if (!latestSeekEnabled || size.width <= 0) {
                                            return@detectTapGestures
                                        }
                                        userSeeking = true
                                        applySeekFraction(offset.x / size.width.toFloat())
                                        userSeeking = false
                                    }
                                }
                                .pointerInput(seekEnabled) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { offset ->
                                            if (!latestSeekEnabled || size.width <= 0) {
                                                return@detectHorizontalDragGestures
                                            }
                                            userSeeking = true
                                            applySeekFraction(offset.x / size.width.toFloat())
                                        },
                                        onDragEnd = { userSeeking = false },
                                        onDragCancel = { userSeeking = false },
                                        onHorizontalDrag = { change, _ ->
                                            if (!latestSeekEnabled || size.width <= 0) {
                                                return@detectHorizontalDragGestures
                                            }
                                            change.consume()
                                            applySeekFraction(
                                                change.position.x / size.width.toFloat(),
                                            )
                                        },
                                    )
                                },
                        ) {
                            XiloIcon(
                                icon = XiloIcons.Music,
                                contentDescription = null,
                                tint = if (playing) ColorSuccess else XiloBlue,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = if (loadError) {
                                    stringResource(R.string.post_audio_load_error)
                                } else {
                                    title.ifBlank { stringResource(R.string.post_audio_title) }
                                },
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = IranSansXFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    lineHeight = 16.sp,
                                ),
                                color = if (loadError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (!loadError && durationMs > 0f) {
                                Text(
                                    text = "${formatMs(positionMs.toLong())} / ${formatMs(durationMs.toLong())}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = IranSansXFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                        fontFeatureSettings = "tnum",
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }

                        // Speed chip — compact meta control before the primary action.
                        val speedEnabled = ready && !loadError
                        Text(
                            text = formatRate(PlaybackRates[rateIndex]),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = IranSansXFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                fontFeatureSettings = "tnum",
                            ),
                            color = if (speedEnabled) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = if (speedEnabled) 0.06f else 0.03f,
                                    ),
                                )
                                .clickable(
                                    enabled = speedEnabled,
                                    role = Role.Button,
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) {
                                    rateIndex = (rateIndex + 1) % PlaybackRates.size
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )

                        // Primary play/pause — always trailing edge (LTR end).
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .graphicsLayer {
                                    val scale = if (playing) playingPulse else 1f
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clip(CircleShape)
                                .background(playTint.copy(alpha = if (playing) 0.14f else 0.12f))
                                .border(
                                    width = 1.5.dp,
                                    color = playTint.copy(alpha = if (playing) 0.9f else 0.55f),
                                    shape = CircleShape,
                                )
                                .semantics {
                                    role = Role.Button
                                    contentDescription = playPauseLabel
                                }
                                .clickable(
                                    enabled = ready && !loadError,
                                    role = Role.Button,
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) {
                                    runCatching {
                                        if (player.isPlaying) {
                                            player.pause()
                                            playing = false
                                        } else {
                                            player.start()
                                            playing = true
                                        }
                                    }
                                },
                        ) {
                            XiloIcon(
                                icon = if (playing) {
                                    XiloIcons.PauseCircle
                                } else {
                                    XiloIcons.PlayCircle
                                },
                                contentDescription = null,
                                tint = playTint,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

private fun formatRate(rate: Float): String {
    return if (rate % 1f == 0f) {
        "${rate.toInt()}×"
    } else {
        "${rate}×"
    }
}
