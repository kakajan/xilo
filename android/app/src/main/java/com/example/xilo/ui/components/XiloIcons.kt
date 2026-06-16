package com.example.xilo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xilo.theme.XiloBlue

object XiloIcons {
    val FeedSelected get() = Icons.Filled.Home
    val FeedUnselected get() = Icons.Outlined.Home
    val DiscoverSelected get() = Icons.Filled.Explore
    val DiscoverUnselected get() = Icons.Outlined.Explore
    val ChatSelected get() = Icons.Filled.Forum
    val ChatUnselected get() = Icons.Outlined.Forum
    val ProfileSelected get() = Icons.Filled.AccountCircle
    val ProfileUnselected get() = Icons.Outlined.AccountCircle
}

@Composable
fun VerifiedBadge(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    Icon(
        imageVector = Icons.Filled.Verified,
        contentDescription = "Verified",
        tint = XiloBlue,
        modifier = modifier.size(size)
    )
}

@Composable
fun XiloLogo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(XiloBlue),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "✈",
            color = Color.White,
            fontSize = (size.value * 0.45f).sp
        )
    }
}
