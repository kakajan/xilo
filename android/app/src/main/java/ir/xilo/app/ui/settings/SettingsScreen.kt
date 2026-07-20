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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.app.Activity
import android.net.Uri
import ir.xilo.app.R
import ir.xilo.app.core.util.AppLocale
import ir.xilo.app.core.util.CalendarPreference
import ir.xilo.app.data.repository.ThemeMode
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.ui.components.XiloAvatar
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.XiloTextField
import ir.xilo.app.ui.components.XiloTopAppBar
import ir.xilo.app.ui.components.forUsernameHandle
import ir.xilo.app.ui.components.usernameHandle
import androidx.compose.ui.text.style.TextDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SettingsMenuItem(
    val title: String,
    @androidx.annotation.DrawableRes val icon: Int,
    val iconTint: Color,
    val isDestructive: Boolean = false,
    val action: SettingsAction
)

private enum class SettingsAction {
    ChangePhoto,
    Username,
    MyProfile,
    Language,
    Theme,
    Wallet,
    SavedMessages,
    Devices,
    ChatFolder,
    NotificationPreferences,
    Calendar,
    Logout,
}

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLogoutComplete: () -> Unit,
    onMyProfileClick: () -> Unit,
    onSavedMessagesClick: () -> Unit,
    onDevicesClick: () -> Unit,
    onChatFoldersClick: () -> Unit,
    onNotificationPreferencesClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showCalendarDialog by remember { mutableStateOf(false) }
    var showUsernameDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var cropImageUri by remember { mutableStateOf<Uri?>(null) }
    var isPreparingCrop by remember { mutableStateOf(false) }
    val usernameSavedMessage = stringResource(R.string.settings_username_saved)

    LaunchedEffect(uiState.usernamePending) {
        if (uiState.usernamePending) {
            showUsernameDialog = true
        }
    }

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
                snackbarHostState.showSnackbar(AppLocale.string(context, R.string.avatar_crop_load_failed))
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

    LaunchedEffect(uiState.errorMessage, showUsernameDialog) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        // Keep validation errors on the username dialog field.
        if (showUsernameDialog) return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearError()
    }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let { message ->
            if (message == usernameSavedMessage) {
                showUsernameDialog = false
            }
            snackbarHostState.showSnackbar(message)
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
                SettingsNavEvent.NotificationPreferences -> onNotificationPreferencesClick()
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_logout_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.settings_logout_message),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout()
                }) {
                    Text(
                        text = stringResource(R.string.settings_action_logout),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(
                        text = stringResource(R.string.settings_action_cancel),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        )
    }

    if (showCalendarDialog) {
        AlertDialog(
            onDismissRequest = { showCalendarDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_calendar_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Column {
                    CalendarPreference.entries.forEach { pref ->
                        val label = when (pref) {
                            CalendarPreference.AUTO -> stringResource(R.string.settings_calendar_auto)
                            CalendarPreference.JALALI -> stringResource(R.string.settings_calendar_jalali)
                            CalendarPreference.GREGORIAN -> stringResource(R.string.settings_calendar_gregorian)
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
                    Text(
                        text = stringResource(R.string.settings_action_close),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        )
    }

    if (showUsernameDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.usernamePending) showUsernameDialog = false
            },
            title = {
                Text(
                    text = stringResource(
                        if (uiState.usernamePending) {
                            R.string.settings_username_choose_title
                        } else {
                            R.string.settings_username_dialog_title
                        }
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Column {
                    if (uiState.usernamePending) {
                        Text(
                            text = stringResource(R.string.settings_username_pending_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    XiloTextField(
                        value = uiState.usernameDraft,
                        onValueChange = viewModel::onUsernameDraftChange,
                        placeholder = stringResource(R.string.settings_username_placeholder),
                        isError = uiState.errorMessage != null,
                        errorText = uiState.errorMessage,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.settings_username_rules),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.updateUsername() },
                    enabled = !uiState.isLoading,
                ) {
                    Text(
                        text = stringResource(R.string.settings_action_save),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
            dismissButton = {
                if (!uiState.usernamePending) {
                    TextButton(onClick = { showUsernameDialog = false }) {
                        Text(
                            text = stringResource(R.string.settings_action_cancel),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            },
        )
    }

    if (showLanguageDialog) {
        val languages = uiState.languages.ifEmpty {
            listOf(
                ir.xilo.app.data.remote.dto.LanguageInfo("fa", "فارسی", "Persian", "rtl"),
                ir.xilo.app.data.remote.dto.LanguageInfo("en", "English", "English", "ltr"),
                ir.xilo.app.data.remote.dto.LanguageInfo("ar", "العربية", "Arabic", "rtl"),
                ir.xilo.app.data.remote.dto.LanguageInfo("ru", "Русский", "Russian", "ltr"),
                ir.xilo.app.data.remote.dto.LanguageInfo("tr", "Türkçe", "Turkish", "ltr"),
            )
        }
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_language_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Column {
                    languages.forEach { lang ->
                        val selected = uiState.preferredLanguage == lang.code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val changed = lang.code != uiState.preferredLanguage
                                    showLanguageDialog = false
                                    viewModel.updatePreferredLanguage(lang.code)
                                    if (changed) {
                                        (context as? Activity)?.recreate()
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (selected) "●  ${lang.nameNative}" else "○  ${lang.nameNative}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(
                        text = stringResource(R.string.settings_action_close),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_theme_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                            ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                            ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showThemeDialog = false
                                    viewModel.updateThemeMode(mode)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (uiState.themeMode == mode) "●  $label" else "○  $label",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (uiState.themeMode == mode) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(
                        text = stringResource(R.string.settings_action_close),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
        )
    }

    val menuItems = listOf(
        SettingsMenuItem(
            stringResource(R.string.settings_menu_change_photo),
            XiloIcons.Camera,
            XiloBlue,
            action = SettingsAction.ChangePhoto,
        ),
        SettingsMenuItem(
            stringResource(
                if (uiState.usernamePending) {
                    R.string.settings_menu_choose_username
                } else {
                    R.string.settings_menu_username
                }
            ),
            XiloIcons.UserAdd,
            Color(0xFFE53935),
            action = SettingsAction.Username,
        ),
        SettingsMenuItem(
            stringResource(R.string.settings_menu_my_profile),
            XiloIcons.User,
            Color(0xFFE53935),
            action = SettingsAction.MyProfile,
        ),
        SettingsMenuItem(
            stringResource(R.string.settings_menu_language),
            XiloIcons.Sms,
            Color(0xFF5E35B1),
            action = SettingsAction.Language,
        ),
        SettingsMenuItem(
            stringResource(R.string.settings_menu_theme),
            XiloIcons.Eye,
            Color(0xFF3949AB),
            action = SettingsAction.Theme,
        ),
        SettingsMenuItem(
            stringResource(R.string.settings_menu_calendar),
            XiloIcons.Calendar,
            Color(0xFF00897B),
            action = SettingsAction.Calendar,
        ),
        SettingsMenuItem(
            stringResource(R.string.settings_menu_wallet),
            XiloIcons.Wallet,
            Color(0xFF8E24AA),
            action = SettingsAction.Wallet,
        ),
        SettingsMenuItem(
            stringResource(R.string.settings_menu_saved_messages),
            XiloIcons.Bookmark,
            XiloBlue,
            action = SettingsAction.SavedMessages,
        ),
        SettingsMenuItem(
            stringResource(R.string.settings_menu_devices),
            XiloIcons.Mobile,
            Color(0xFFFF9800),
            action = SettingsAction.Devices,
        ),
        SettingsMenuItem(
            stringResource(R.string.settings_menu_chat_folders),
            XiloIcons.Folder,
            Color(0xFF29B6F6),
            action = SettingsAction.ChatFolder,
        ),
        SettingsMenuItem(
            stringResource(R.string.settings_menu_notifications),
            XiloIcons.Notification,
            Color(0xFF7E57C2),
            action = SettingsAction.NotificationPreferences,
        ),
        SettingsMenuItem(
            stringResource(R.string.settings_menu_logout),
            XiloIcons.Logout,
            MaterialTheme.colorScheme.error,
            isDestructive = true,
            action = SettingsAction.Logout,
        ),
    )

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            XiloTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBackClick) {
                        XiloIcon(
                            icon = XiloIcons.Back,
                            contentDescription = stringResource(R.string.settings_back)
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
                        val usernameUnset = stringResource(R.string.settings_username_unset)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.phone.isNotBlank()) {
                                Text(
                                    text = uiState.phone,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDirection = TextDirection.Ltr,
                                    ),
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Text(
                                    text = " • ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            if (uiState.usernamePending) {
                                Text(
                                    text = usernameUnset,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            } else {
                                Text(
                                    text = usernameHandle(uiState.username),
                                    style = MaterialTheme.typography.bodyMedium.forUsernameHandle(),
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }

                items(menuItems, key = { it.action.name }) { item ->
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
                                SettingsAction.Username -> showUsernameDialog = true
                                SettingsAction.MyProfile -> viewModel.onMyProfile()
                                SettingsAction.Language -> showLanguageDialog = true
                                SettingsAction.Theme -> showThemeDialog = true
                                SettingsAction.Calendar -> showCalendarDialog = true
                                SettingsAction.Wallet -> viewModel.onWalletComingSoon()
                                SettingsAction.SavedMessages -> viewModel.onSavedMessages()
                                SettingsAction.Devices -> viewModel.onDevices()
                                SettingsAction.ChatFolder -> viewModel.onChatFolders()
                                SettingsAction.NotificationPreferences -> viewModel.onNotificationPreferences()
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
