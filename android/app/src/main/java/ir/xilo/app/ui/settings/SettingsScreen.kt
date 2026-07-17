package ir.xilo.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.net.Uri
import ir.xilo.app.R
import ir.xilo.app.core.util.CalendarPreference
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.ui.components.XiloAvatar
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.XiloTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class SettingsMenuItem(
    val title: String,
    @androidx.annotation.DrawableRes val icon: Int,
    val iconTint: Color,
    val isDestructive: Boolean = false,
    val action: SettingsAction
)

private enum class SettingsAction {
    ChangePhoto, MyProfile, Wallet, SavedMessages, Devices, ChatFolder, Calendar, Logout
}

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLogoutComplete: () -> Unit,
    onMyProfileClick: () -> Unit,
    onSavedMessagesClick: () -> Unit,
    onDevicesClick: () -> Unit,
    onChatFoldersClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showCalendarDialog by remember { mutableStateOf(false) }
    var cropImageUri by remember { mutableStateOf<Uri?>(null) }
    var isPreparingCrop by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Copy while the Photo Picker grant is still fresh — picker URIs are often one-shot.
        isPreparingCrop = true
        scope.launch {
            val localUri = withContext(Dispatchers.IO) {
                copyPickedImageToCache(context, uri)
            }
            isPreparingCrop = false
            if (localUri != null) {
                cropImageUri = localUri
            } else {
                snackbarHostState.showSnackbar(context.getString(R.string.avatar_crop_load_failed))
            }
        }
    }

    cropImageUri?.let { uri ->
        AvatarCropDialog(
            imageUri = uri,
            onDismiss = {
                deleteCachedCropSource(context, uri)
                cropImageUri = null
            },
            onConfirm = { bytes ->
                deleteCachedCropSource(context, uri)
                cropImageUri = null
                viewModel.onChangePhoto(bytes)
            },
        )
    }

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

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearInfo()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                SettingsNavEvent.MyProfile -> onMyProfileClick()
                SettingsNavEvent.SavedMessages -> onSavedMessagesClick()
                SettingsNavEvent.Devices -> onDevicesClick()
                SettingsNavEvent.ChatFolders -> onChatFoldersClick()
            }
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

    if (showCalendarDialog) {
        AlertDialog(
            onDismissRequest = { showCalendarDialog = false },
            title = { Text("تقویم نمایش تاریخ", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    CalendarPreference.entries.forEach { pref ->
                        val label = when (pref) {
                            CalendarPreference.AUTO -> "خودکار (پیش‌فرض سیستم)"
                            CalendarPreference.JALALI -> "شمسی"
                            CalendarPreference.GREGORIAN -> "میلادی"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCalendarDialog = false
                                    viewModel.updatePreferredCalendar(pref)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (uiState.preferredCalendar == pref) "●  $label" else "○  $label",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (uiState.preferredCalendar == pref) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCalendarDialog = false }) {
                    Text("بستن")
                }
            }
        )
    }

    val menuItems = listOf(
        SettingsMenuItem("تغییر عکس پروفایل", XiloIcons.Camera, XiloBlue, action = SettingsAction.ChangePhoto),
        SettingsMenuItem("پروفایل من", XiloIcons.User, Color(0xFFE53935), action = SettingsAction.MyProfile),
        SettingsMenuItem("تقویم", XiloIcons.Calendar, Color(0xFF00897B), action = SettingsAction.Calendar),
        SettingsMenuItem("کیف پول", XiloIcons.Wallet, Color(0xFF8E24AA), action = SettingsAction.Wallet),
        SettingsMenuItem("پیام‌های ذخیره‌شده", XiloIcons.Bookmark, XiloBlue, action = SettingsAction.SavedMessages),
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            XiloAvatar(imageUrl = uiState.avatarUrl, size = 96.dp)
                            if (uiState.isUploadingAvatar) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = uiState.displayName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val subtitle = buildString {
                            if (uiState.phone.isNotBlank()) {
                                append(uiState.phone)
                                append(" • ")
                            }
                            append("@")
                            append(uiState.username)
                        }
                        Text(
                            text = subtitle,
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
                                SettingsAction.ChangePhoto -> {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                }
                                SettingsAction.MyProfile -> viewModel.onMyProfile()
                                SettingsAction.Calendar -> showCalendarDialog = true
                                SettingsAction.Wallet -> viewModel.onWalletComingSoon()
                                SettingsAction.SavedMessages -> viewModel.onSavedMessages()
                                SettingsAction.Devices -> viewModel.onDevices()
                                SettingsAction.ChatFolder -> viewModel.onChatFolders()
                                SettingsAction.Logout -> showLogoutDialog = true
                            }
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(96.dp))
                }
            }

            if ((uiState.isLoading && !uiState.isUploadingAvatar) || isPreparingCrop) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun copyPickedImageToCache(context: android.content.Context, source: Uri): Uri? {
    return runCatching {
        val dir = File(context.cacheDir, "avatar_crop").apply { mkdirs() }
        val dest = File(dir, "source_${System.nanoTime()}.img")
        context.contentResolver.openInputStream(source)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        if (dest.length() == 0L) {
            dest.delete()
            return null
        }
        Uri.fromFile(dest)
    }.getOrNull()
}

private fun deleteCachedCropSource(context: android.content.Context, uri: Uri) {
    if (uri.scheme != "file") return
    val path = uri.path ?: return
    val cacheRoot = File(context.cacheDir, "avatar_crop").canonicalFile
    val file = File(path).canonicalFile
    if (file.path.startsWith(cacheRoot.path)) {
        file.delete()
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
