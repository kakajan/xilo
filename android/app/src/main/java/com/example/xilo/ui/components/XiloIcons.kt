package com.example.xilo.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xilo.theme.XiloBlue
import io.eyram.iconsax.IconSax

@Composable
fun XiloIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

object XiloIcons {
    val FeedSelected = IconSax.Bold.Home
    val FeedUnselected = IconSax.Outline.Home
    val DiscoverSelected = IconSax.Bold.Discover
    val DiscoverUnselected = IconSax.Linear.Discover
    val ChatSelected = IconSax.Bold.Messages
    val ChatUnselected = IconSax.Outline.Messages
    val ProfileSelected = IconSax.Bold.ProfileCircle
    val ProfileUnselected = IconSax.Outline.ProfileCircle

    val Add = IconSax.Outline.Add
    val Search = IconSax.Outline.SearchNormal
    val Settings = IconSax.Outline.Setting2
    val Close = IconSax.Outline.CloseCircle
    val More = IconSax.Outline.More
    val MoreHorizontal = IconSax.Outline.More2
    val Back = IconSax.Outline.ArrowRight
    val ChevronEnd = IconSax.Outline.ArrowLeft2

    val Heart = IconSax.Outline.Heart
    val HeartFilled = IconSax.Bold.Heart
    val Bookmark = IconSax.Outline.Bookmark
    val BookmarkFilled = IconSax.Bold.Bookmark
    val Share = IconSax.Outline.Share
    val Message = IconSax.Outline.Message
    val Repeat = IconSax.Outline.Repeat
    val Chart = IconSax.Outline.Chart
    val Send = IconSax.Linear.Send
    val Attach = IconSax.Outline.AttachCircle
    val Emoji = IconSax.Outline.EmojiHappy
    val Verify = IconSax.Bold.Verify

    val CloudOff = IconSax.Outline.CloudRemove
    val MessageTick = IconSax.Outline.MessageTick
    val MessageTickBold = IconSax.Bold.MessageTick
    val Edit = IconSax.Outline.Edit
    val Archive = IconSax.Outline.Archive
    val Pin = IconSax.Outline.Location

    val Call = IconSax.Outline.Call
    val Video = IconSax.Outline.Video
    val Notification = IconSax.Outline.Notification
    val UserAdd = IconSax.Outline.UserAdd
    val Sms = IconSax.Outline.MessageText

    val Camera = IconSax.Outline.Camera
    val User = IconSax.Outline.User
    val Wallet = IconSax.Outline.Wallet
    val Mobile = IconSax.Outline.Mobile
    val Folder = IconSax.Outline.Folder
    val Logout = IconSax.Outline.Logout
}

@Composable
fun XiloSendIcon(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val mirrorForDirection = when (LocalLayoutDirection.current) {
        LayoutDirection.Rtl -> -1f
        LayoutDirection.Ltr -> 1f
    }
    XiloIcon(
        icon = XiloIcons.Send,
        contentDescription = contentDescription,
        modifier = modifier.graphicsLayer { scaleX = mirrorForDirection },
        tint = tint,
    )
}

@Composable
fun VerifiedBadge(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    XiloIcon(
        icon = XiloIcons.Verify,
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
