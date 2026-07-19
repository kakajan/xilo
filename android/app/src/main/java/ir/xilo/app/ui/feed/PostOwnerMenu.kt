package ir.xilo.app.ui.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.xilo.app.R
import ir.xilo.app.theme.ColorError
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons

private enum class PostOwnerConfirm {
    Archive,
    Delete,
}

@Composable
fun PostOwnerMenu(
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<PostOwnerConfirm?>(null) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.size(40.dp),
        ) {
            XiloIcon(
                icon = XiloIcons.More,
                contentDescription = stringResource(R.string.post_owner_menu),
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(XiloSpacing.iconInline),
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.post_owner_edit)) },
                leadingIcon = {
                    XiloIcon(
                        icon = XiloIcons.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    menuExpanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.post_owner_archive)) },
                leadingIcon = {
                    XiloIcon(
                        icon = XiloIcons.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    menuExpanded = false
                    confirm = PostOwnerConfirm.Archive
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.post_owner_delete),
                        color = ColorError,
                    )
                },
                leadingIcon = {
                    XiloIcon(
                        icon = XiloIcons.Trash,
                        contentDescription = null,
                        tint = ColorError,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    menuExpanded = false
                    confirm = PostOwnerConfirm.Delete
                },
            )
        }
    }

    when (confirm) {
        PostOwnerConfirm.Archive -> {
            AlertDialog(
                onDismissRequest = { confirm = null },
                title = { Text(stringResource(R.string.post_owner_archive_title)) },
                text = { Text(stringResource(R.string.post_owner_archive_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirm = null
                            onArchive()
                        },
                    ) {
                        Text(stringResource(R.string.post_owner_archive))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirm = null }) {
                        Text(stringResource(R.string.post_owner_cancel))
                    }
                },
            )
        }
        PostOwnerConfirm.Delete -> {
            AlertDialog(
                onDismissRequest = { confirm = null },
                title = { Text(stringResource(R.string.post_owner_delete_title)) },
                text = { Text(stringResource(R.string.post_owner_delete_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirm = null
                            onDelete()
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.post_owner_delete),
                            color = ColorError,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirm = null }) {
                        Text(stringResource(R.string.post_owner_cancel))
                    }
                },
            )
        }
        null -> Unit
    }
}

fun isPostOwner(
    authorId: String,
    authorUsername: String,
    currentUserId: String?,
    currentUsername: String?,
): Boolean {
    if (!currentUserId.isNullOrBlank() && currentUserId == authorId) return true
    if (!currentUsername.isNullOrBlank() &&
        currentUsername.equals(authorUsername, ignoreCase = true)
    ) {
        return true
    }
    return false
}
