package com.example.xilo.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.xilo.theme.XiloSpacing

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
fun ProfileHeaderSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(XiloSpacing.horizontal),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SkeletonBox(modifier = Modifier.fillMaxWidth().height(200.dp), cornerRadius = 0.dp)
        Spacer(modifier = Modifier.height(16.dp))
        SkeletonBox(modifier = Modifier.size(100.dp), cornerRadius = 50.dp)
        Spacer(modifier = Modifier.height(12.dp))
        SkeletonBox(modifier = Modifier.width(160.dp).height(20.dp))
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBox(modifier = Modifier.width(120.dp).height(14.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            repeat(3) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SkeletonBox(modifier = Modifier.width(40.dp).height(16.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    SkeletonBox(modifier = Modifier.width(56.dp).height(12.dp))
                }
            }
        }
    }
}

@Composable
fun ProfileSkeleton(modifier: Modifier = Modifier) = ProfileHeaderSkeleton(modifier)
