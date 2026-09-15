package ir.xilo.app.ui.feed

import android.content.Intent
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ir.xilo.app.R
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.data.remote.dto.decodePostMedia
import ir.xilo.app.theme.XiloSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.ui.components.ContentAwareText
import ir.xilo.app.ui.components.HashtagAwareText
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.postdetail.extractPlainText

fun postShowsTitle(post: PostEntity): Boolean {
    if (post.postType == ComposeKind.MICRO) return false
    return post.title.isNotBlank()
}

fun postBodyText(post: PostEntity): String =
    post.excerpt?.takeIf { it.isNotBlank() }
        ?: extractPlainText(post.content).takeIf { it.isNotBlank() }
        ?: post.content.takeIf { !it.startsWith("{") && it.isNotBlank() }
        ?: ""

@Composable
fun PostTitleBlock(
    post: PostEntity,
    modifier: Modifier = Modifier,
) {
    if (!postShowsTitle(post)) return
    ContentAwareText(
        text = post.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
    )
}

@Composable
fun PostBodyBlock(
    post: PostEntity,
    onHashtagClick: ((String) -> Unit)? = null,
    onTextClick: (() -> Unit)? = null,
    maxLines: Int = Int.MAX_VALUE,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val previewText = postBodyText(post)
    if (previewText.isBlank()) return
    val style = if (compact) {
        MaterialTheme.typography.bodyLarge
    } else {
        MaterialTheme.typography.bodyLarge
    }
    if (onHashtagClick != null) {
        HashtagAwareText(
            text = previewText,
            onHashtagClick = onHashtagClick,
            onTextClick = onTextClick,
            style = style,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = maxLines,
            modifier = modifier,
        )
    } else {
        ContentAwareText(
            text = previewText,
            style = style,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = maxLines,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostPhotoCarousel(
    post: PostEntity,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val mediaUrls = remember(post.mediaJson, post.coverImageUrl) {
        decodePostMedia(post.mediaJson)
            .map { it.url }
            .filter { it.isNotBlank() }
            .ifEmpty {
                listOfNotNull(post.coverImageUrl?.takeIf { it.isNotBlank() })
            }
    }
    if (mediaUrls.isEmpty()) return
    val context = LocalContext.current
    if (mediaUrls.size == 1) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(mediaUrls.first())
                .size(1080, 720)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(R.string.cd_post_image),
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(XiloSpacing.mediaRadius))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    }
                ),
        )
        return
    }
    val pagerState = rememberPagerState(pageCount = { mediaUrls.size })
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth(),
    ) { page ->
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(mediaUrls[page])
                .size(1080, 720)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(R.string.cd_post_image),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(XiloSpacing.mediaRadius))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    }
                ),
        )
    }
}

@Composable
fun PostVideoBlock(
    post: PostEntity,
    modifier: Modifier = Modifier,
) {
    val videoUrl = remember(post.mediaJson, post.coverImageUrl) {
        decodePostMedia(post.mediaJson)
            .firstOrNull { it.url.isNotBlank() }
            ?.url
            ?: post.coverImageUrl?.takeIf { it.isNotBlank() }
    } ?: return
    AndroidView(
        factory = { context ->
            VideoView(context).apply {
                setVideoURI(Uri.parse(videoUrl))
            }
        },
        update = { view ->
            if (view.tag != videoUrl) {
                view.tag = videoUrl
                view.setVideoURI(Uri.parse(videoUrl))
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(XiloSpacing.mediaRadius)),
    )
}

@Composable
fun PostLinkCard(
    url: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val host = remember(url) {
        runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault(url)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(XiloSpacing.mediaRadius))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(XiloSpacing.mediaRadius),
            )
            .clickable(role = Role.Button) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            .padding(12.dp),
    ) {
        Text(
            text = host.ifBlank { url },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun PostAudioBlock(
    post: PostEntity,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hasCover = !post.coverImageUrl.isNullOrBlank()

    if (hasCover) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(XiloSpacing.mediaRadius))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    }
                ),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(post.coverImageUrl)
                    .size(1080, 720)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.cd_post_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                XiloIcon(
                    icon = XiloIcons.Music,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.post_audio_title),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(XiloSpacing.mediaRadius))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(XiloSpacing.mediaRadius),
                )
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    }
                )
                .padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(XiloBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                XiloIcon(
                    icon = XiloIcons.Music,
                    contentDescription = null,
                    tint = XiloBlue,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.title.ifBlank { stringResource(R.string.post_audio_title) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.post_audio_attached),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(XiloBlue),
                contentAlignment = Alignment.Center,
            ) {
                XiloIcon(
                    icon = XiloIcons.Play,
                    contentDescription = stringResource(R.string.post_audio_play),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun PostTypeMediaBlock(
    post: PostEntity,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isAudio = post.postType == ComposeKind.AUDIO || !post.audioUrl.isNullOrBlank()
    when {
        post.postType == ComposeKind.PHOTO -> PostPhotoCarousel(post = post, onClick = onClick, modifier = modifier)
        post.postType == ComposeKind.VIDEO -> PostVideoBlock(post = post, modifier = modifier)
        post.postType == ComposeKind.LINK -> post.linkUrl?.takeIf { it.isNotBlank() }?.let { link ->
            PostLinkCard(url = link, modifier = modifier)
        }
        isAudio -> PostAudioBlock(post = post, onClick = onClick, modifier = modifier)
        else -> if (!post.coverImageUrl.isNullOrBlank()) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(post.coverImageUrl)
                    .size(1080, 720)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.cd_post_image),
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(XiloSpacing.mediaRadius))
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(role = Role.Button, onClick = onClick)
                        } else {
                            Modifier
                        }
                    ),
            )
        }
    }
}
