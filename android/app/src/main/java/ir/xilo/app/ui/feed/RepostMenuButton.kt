package ir.xilo.app.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ir.xilo.app.R
import ir.xilo.app.theme.ColorSuccess
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons

/**
 * Twitter-style repost control: opens a menu with plain repost / undo and quote.
 */
@Composable
fun RepostMenuButton(
    repostCount: Int,
    isReposted: Boolean,
    onRepostClick: () -> Unit,
    onQuoteClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val tint = if (isReposted) ColorSuccess else MaterialTheme.colorScheme.secondary

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = { menuExpanded = true })
                .padding(
                    horizontal = if (compact) 4.dp else 8.dp,
                    vertical = if (compact) 6.dp else 8.dp,
                ),
        ) {
            XiloIcon(
                icon = XiloIcons.Repeat,
                contentDescription = stringResource(R.string.cd_repost),
                tint = tint,
                modifier = Modifier.size(XiloSpacing.iconInline),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = repostCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (isReposted) R.string.repost_action_undo else R.string.repost_action_repost
                        )
                    )
                },
                leadingIcon = {
                    XiloIcon(
                        icon = XiloIcons.Repeat,
                        contentDescription = null,
                        tint = if (isReposted) ColorSuccess else Color.Unspecified,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    menuExpanded = false
                    onRepostClick()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.repost_action_quote)) },
                leadingIcon = {
                    XiloIcon(
                        icon = XiloIcons.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    menuExpanded = false
                    onQuoteClick()
                },
            )
        }
    }
}
