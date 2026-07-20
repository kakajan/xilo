package ir.xilo.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.xilo.app.theme.XiloBlue
import io.eyram.iconsax.IconSax

/**
 * Iconsax Back / ChevronEnd are authored for RTL (Persian default):
 * Back = ArrowRight3 (→), ChevronEnd = ArrowLeft2 (←).
 * In LTR, swap to the opposing chevron drawable instead of scaleX mirroring
 * (graphicsLayer flips are unreliable on these vector painters).
 */
@DrawableRes
private fun resolveDirectionalIcon(
    @DrawableRes icon: Int,
    direction: LayoutDirection,
): Int {
    if (direction == LayoutDirection.Rtl) return icon
    return when (icon) {
        XiloIcons.Back -> IconSax.Outline.ArrowLeft2
        XiloIcons.ChevronEnd -> IconSax.Outline.ArrowRight3
        else -> icon
    }
}

@Composable
fun XiloIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val resolvedIcon = resolveDirectionalIcon(icon, LocalLayoutDirection.current)
    Icon(
        painter = painterResource(resolvedIcon),
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
    // ArrowRight3 is a plain chevron; ArrowRight2 draws an arrow inside a circle.
    val Back = IconSax.Outline.ArrowRight3
    val ChevronEnd = IconSax.Outline.ArrowLeft2

    val Heart = IconSax.Outline.Heart
    val HeartFilled = IconSax.Bold.Heart
    val ThumbUp = IconSax.Outline.Like1
    val ThumbUpFilled = IconSax.Bold.Like1
    // Outline style has no Dislike drawable; Linear matches the stroke weight of Outline.
    val ThumbDown = IconSax.Linear.Dislike
    val ThumbDownFilled = IconSax.Bold.Dislike
    val Report = IconSax.Outline.Flag
    val Bookmark = IconSax.Outline.Bookmark
    val BookmarkFilled = IconSax.Bold.Bookmark
    // Export = classic share-up arrow; Outline.Share is the three-node "network" glyph.
    val Share = IconSax.Outline.Export
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
    val Trash = IconSax.Outline.Trash
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
    val Calendar = IconSax.Outline.Calendar
    val Grid = IconSax.Outline.Element4
    val Eye = IconSax.Outline.Eye
    val Music = IconSax.Outline.Music
    val Play = IconSax.Bold.Play
    val Pause = IconSax.Bold.Pause
}

@Composable
fun XiloSendIcon(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
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
    size: Dp = 18.dp,
    tint: Color = XiloBlue,
) {
    XiloIcon(
        icon = XiloIcons.Verify,
        contentDescription = "Verified",
        tint = tint,
        modifier = modifier.size(size)
    )
}

@Composable
fun XiloLogo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    AileBrandLogo(
        variant = AileLogoVariant.MarkColored,
        height = size,
        modifier = modifier,
    )
}
