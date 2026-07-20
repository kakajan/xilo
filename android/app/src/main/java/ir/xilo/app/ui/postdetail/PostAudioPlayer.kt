package ir.xilo.app.ui.postdetail

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.xilo.app.R
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

    val player = remember(audioUrl) { MediaPlayer() }

    DisposableEffect(audioUrl, player) {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        player.setOnPreparedListener {
            ready = true
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
            true
        }
        runCatching {
            player.setDataSource(audioUrl)
            player.prepareAsync()
        }.onFailure {
            ready = false
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

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                XiloIcon(
                    icon = XiloIcons.Music,
                    contentDescription = null,
                    tint = XiloBlue,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = title.ifBlank { stringResource(R.string.post_audio_title) },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (!ready) return@IconButton
                        if (player.isPlaying) {
                            player.pause()
                            playing = false
                        } else {
                            player.start()
                            playing = true
                        }
                    },
                    enabled = ready,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(XiloBlue),
                ) {
                    XiloIcon(
                        icon = if (playing) XiloIcons.Pause else XiloIcons.Play,
                        contentDescription = stringResource(
                            if (playing) R.string.post_audio_pause else R.string.post_audio_play,
                        ),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                TextButton(
                    onClick = { rateIndex = (rateIndex + 1) % PlaybackRates.size },
                    enabled = ready,
                ) {
                    Text(
                        text = "${PlaybackRates[rateIndex]}×",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = formatMs(positionMs.toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Slider(
                    value = positionMs.coerceIn(0f, durationMs.coerceAtLeast(1f)),
                    onValueChange = {
                        userSeeking = true
                        positionMs = it
                    },
                    onValueChangeFinished = {
                        if (ready) {
                            runCatching { player.seekTo(positionMs.toInt()) }
                        }
                        userSeeking = false
                    },
                    valueRange = 0f..(durationMs.coerceAtLeast(1f)),
                    enabled = ready,
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = XiloBlue,
                        activeTrackColor = XiloBlue,
                    ),
                )
                Text(
                    text = formatMs(durationMs.toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
