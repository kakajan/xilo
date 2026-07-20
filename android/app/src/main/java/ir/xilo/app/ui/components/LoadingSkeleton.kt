package ir.xilo.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.xilo.app.theme.XiloSpacing

/** Matches ProfileScreen header fill while content loads. */
private val ProfileSkeletonTeal = Color(0xFF14919B)
private val ProfileSkeletonSheetRadius = 28.dp
private val ProfileSkeletonAvatarSize = 112.dp

@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate - 300f, 0f),
        end = Offset(translate, 300f)
    )
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp
) {
    val brush = rememberShimmerBrush()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(brush)
    )
}

@Composable
fun PostCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = XiloSpacing.horizontal, vertical = XiloSpacing.vertical)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            SkeletonBox(modifier = Modifier.size(40.dp), cornerRadius = 20.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.5f).height(14.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth().height(14.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.85f).height(14.dp))
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    cornerRadius = XiloSpacing.mediaRadius
                )
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    repeat(4) {
                        SkeletonBox(modifier = Modifier.size(20.dp), cornerRadius = 4.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun FeedSkeletonList(itemCount: Int = 4, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        repeat(itemCount) { PostCardSkeleton() }
    }
}

@Composable
fun ChatListItemSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = XiloSpacing.horizontal, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonBox(modifier = Modifier.size(48.dp), cornerRadius = 24.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.85f).height(12.dp))
        }
        SkeletonBox(modifier = Modifier.width(36.dp).height(12.dp))
    }
}

@Composable
fun ChatListSkeleton(itemCount: Int = 6, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        repeat(itemCount) { ChatListItemSkeleton() }
    }
}

@Composable
private fun rememberProfileHeaderShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "profileHeaderShimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "profileHeaderShimmerTranslate"
    )
    val base = Color.White.copy(alpha = 0.22f)
    val highlight = Color.White.copy(alpha = 0.40f)
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate - 300f, 0f),
        end = Offset(translate, 300f)
    )
}

@Composable
private fun ProfileHeaderSkeletonBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp
) {
    val brush = rememberProfileHeaderShimmerBrush()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(brush)
    )
}

@Composable
fun ProfileHeaderSkeleton(modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.background
    // Teal runs under the sheet corners (same “ears” as the real ProfileScreen).
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ProfileSkeletonTeal)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 68.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProfileHeaderSkeletonBox(
                modifier = Modifier.size(ProfileSkeletonAvatarSize),
                cornerRadius = ProfileSkeletonAvatarSize / 2
            )
            Spacer(modifier = Modifier.height(12.dp))
            ProfileHeaderSkeletonBox(modifier = Modifier.width(160.dp).height(22.dp))
            Spacer(modifier = Modifier.height(8.dp))
            ProfileHeaderSkeletonBox(modifier = Modifier.width(88.dp).height(13.dp))
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                repeat(3) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ProfileHeaderSkeletonBox(modifier = Modifier.width(36.dp).height(16.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        ProfileHeaderSkeletonBox(modifier = Modifier.width(52.dp).height(12.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(3) {
                    ProfileHeaderSkeletonBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        cornerRadius = 14.dp
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        topStart = ProfileSkeletonSheetRadius,
                        topEnd = ProfileSkeletonSheetRadius
                    )
                )
                .background(surface)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(2) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBox(modifier = Modifier.fillMaxWidth(0.55f).height(16.dp))
                    SkeletonBox(modifier = Modifier.width(72.dp).height(12.dp))
                }
            }
        }
    }
}

@Composable
fun ProfileSkeleton(modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.background
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(surface)
    ) {
        ProfileHeaderSkeleton(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(40.dp),
            cornerRadius = 16.dp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            repeat(3) {
                SkeletonBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp),
                    cornerRadius = 0.dp
                )
            }
        }
    }
}
