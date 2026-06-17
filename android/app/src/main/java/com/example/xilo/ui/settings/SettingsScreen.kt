package com.example.xilo.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.xilo.theme.XiloBlue
import com.example.xilo.theme.XiloSpacing
import com.example.xilo.ui.components.XiloAvatar
import com.example.xilo.ui.components.XiloIcon
import com.example.xilo.ui.components.XiloIcons
import com.example.xilo.ui.components.XiloTopAppBar

private data class SettingsMenuItem(
    val title: String,
    @androidx.annotation.DrawableRes val icon: Int,
    val iconTint: Color,
    val isDestructive: Boolean = false,
    val action: SettingsAction
)

private enum class SettingsAction {
    ChangePhoto, MyProfile, Wallet, SavedMessages, RecentCalls, Devices, ChatFolder, Logout
}

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLogoutComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.logoutComplete) {
        if (uiState.logoutComplete) {
            onLogoutComplete()
            viewModel.resetLogoutFlag()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("خروج از حساب", fontWeight = FontWeight.Bold) },
            text = { Text("آیا مطمئن هستید که می‌خواهید از حساب کاربری خارج شوید؟") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout()
                }) {
                    Text("خروج", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    val menuItems = listOf(
        SettingsMenuItem("تغییر عکس پروفایل", XiloIcons.Camera, XiloBlue, action = SettingsAction.ChangePhoto),
        SettingsMenuItem("پروفایل من", XiloIcons.User, Color(0xFFE53935), action = SettingsAction.MyProfile),
        SettingsMenuItem("کیف پول", XiloIcons.Wallet, Color(0xFF8E24AA), action = SettingsAction.Wallet),
        SettingsMenuItem("پیام‌های ذخیره‌شده", XiloIcons.Bookmark, XiloBlue, action = SettingsAction.SavedMessages),
        SettingsMenuItem("تماس‌های اخیر", XiloIcons.Call, Color(0xFF43A047), action = SettingsAction.RecentCalls),
        SettingsMenuItem("دستگاه‌ها", XiloIcons.Mobile, Color(0xFFFF9800), action = SettingsAction.Devices),
        SettingsMenuItem("پوشه گفتگو", XiloIcons.Folder, Color(0xFF29B6F6), action = SettingsAction.ChatFolder),
        SettingsMenuItem("خروج از حساب", XiloIcons.Logout, MaterialTheme.colorScheme.error, isDestructive = true, action = SettingsAction.Logout)
    )

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            XiloTopAppBar(
                title = { Text("تنظیمات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBackClick) {
                        XiloIcon(
                            icon = XiloIcons.Back,
                            contentDescription = "بازگشت"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    XiloAvatar(imageUrl = uiState.avatarUrl, size = 96.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${uiState.phone} • @${uiState.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            items(menuItems, key = { it.title }) { item ->
                SettingsMenuRow(
                    item = item,
                    onClick = {
                        when (item.action) {
                            SettingsAction.Logout -> showLogoutDialog = true
                            else -> { /* future navigation */ }
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}

@Composable
private fun SettingsMenuRow(
    item: SettingsMenuItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = XiloSpacing.horizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(item.iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            XiloIcon(
                icon = item.icon,
                contentDescription = item.title,
                tint = item.iconTint,
                modifier = Modifier.size(XiloSpacing.iconInline)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (item.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        XiloIcon(
            icon = XiloIcons.ChevronEnd,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(XiloSpacing.iconInline)
        )
    }
}
