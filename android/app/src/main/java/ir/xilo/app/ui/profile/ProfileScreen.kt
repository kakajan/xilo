package ir.xilo.app.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import ir.xilo.app.R
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.theme.IranSansXFontFamily
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.theme.YekanBakhFontFamily
import ir.xilo.app.ui.components.LocalChromeVisibility
import ir.xilo.app.ui.components.ProfileSkeleton
import ir.xilo.app.ui.components.VerifiedBadge
import ir.xilo.app.ui.components.XiloAvatar
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.forUsernameHandle
import ir.xilo.app.ui.components.trackChromeVisibility
import ir.xilo.app.ui.components.usernameHandle
import ir.xilo.app.ui.settings.AvatarCropDialog
import ir.xilo.app.ui.settings.copyPickedImageToCache
import ir.xilo.app.ui.settings.deleteCachedCropSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Single flat teal for the whole header — including the “ears” above the white sheet. */
private val ProfileTeal = Color(0xFF14919B)
private val ProfileOnline = Color(0xFFB8F0F5)
private val GlassButtonBg = Color(0x33000000)
private val TabPillBg = Color(0xFFE8E8ED)
private val TabSelectedBg = XiloBlue
private val TabSelectedContent = Color.White

/** App caption font + room for Persian dots/descenders (no line-height trim). */
private val PersianSafeCaptionStyle = TextStyle(
    fontFamily = IranSansXFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 18.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

/**
 * Glass action labels — Persian descenders (س / ش / پ) need extra line box;
 * IranSansX metrics alone under-report descent at this size.
 */
private val PersianSafeGlassActionLabelStyle = PersianSafeCaptionStyle.copy(
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 22.sp,
)

private val CollapsedBarHeight = 64.dp
private val AvatarExpandedSize = 112.dp
private val ActionRowHeight = 84.dp
private val GlassActionMinHeight = 72.dp
private val ExpandedHeaderExtra = 260.dp
private val InfoCardTopRadius = 28.dp

@Composable
fun ProfileScreen(
    username: String,
    onBackClick: () -> Unit,
    onPostClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    onEditProfileClick: () -> Unit = {},
    onCreatePostClick: () -> Unit = {},
    onChatClick: (String) -> Unit = {},
    onFollowersClick: (String) -> Unit = {},
    onFollowingClick: (String) -> Unit = {},
    onReplyClick: (slug: String, commentId: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    /** When false, skip ON_RESUME refresh (avoids Main-tab own-profile overwriting a pushed profile). */
    refreshOnResume: Boolean = true,
    viewModel: ProfileViewModel = hiltViewModel(key = username),
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val userPosts by viewModel.userPosts.collectAsState()
    val userReplies by viewModel.userReplies.collectAsState()
    val userLikes by viewModel.userLikes.collectAsState()
    val isFollowing by viewModel.isFollowing.collectAsState()
    val isOwnProfile by viewModel.isOwnProfile.collectAsState()
    val canCreatePost by viewModel.canCreatePost.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isUploadingAvatar by viewModel.isUploadingAvatar.collectAsState()
    val error by viewModel.error.collectAsState()
    val infoMessage by viewModel.infoMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cropImageUri by remember { mutableStateOf<Uri?>(null) }
    var isPreparingCrop by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isPreparingCrop = true
        scope.launch {
            val localUri = withContext(Dispatchers.IO) {
                copyPickedImageToCache(context, uri)
            }
            isPreparingCrop = false
            if (localUri != null) {
                cropImageUri = localUri
            } else {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.avatar_crop_load_failed)
                )
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

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(infoMessage) {
        infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearInfo()
        }
    }

    LaunchedEffect(username) {
        viewModel.loadProfile(username)
    }

    // Pager keeps own-profile alive under Settings; re-fetch when that instance is visible again.
    // Pushed ProfileKey screens use a keyed ViewModel and refreshOnResume=false so Main cannot race.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (refreshOnResume) {
            viewModel.refreshProfile()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.openChatId.collect { chatId ->
            onChatClick(chatId)
        }
    }

    var selectedTab by remember(username) { mutableIntStateOf(0) }
    LaunchedEffect(selectedTab, isOwnProfile) {
        viewModel.onTabSelected(selectedTab)
    }

    val gridState = rememberLazyGridState()
    val chromeState = LocalChromeVisibility.current
    val density = LocalDensity.current
    val tabLabels = if (isOwnProfile) {
        listOf(
            stringResource(R.string.profile_tab_posts),
            stringResource(R.string.profile_tab_archived),
        )
    } else {
        listOf(
            stringResource(R.string.profile_tab_posts),
            stringResource(R.string.profile_tab_replies),
            stringResource(R.string.profile_tab_likes),
        )
    }

    fun shareProfile() {
        val handle = userProfile?.username ?: username
        val text = context.getString(R.string.profile_share_text, handle)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    if (isLoading && userProfile == null) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = modifier,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            ProfileSkeleton(modifier = Modifier.padding(it))
        }
        return
    }

    val displayUser = userProfile
    val displayName = displayUser?.displayName?.takeIf { it.isNotBlank() }
        ?: displayUser?.username
        ?: username
    val postCount = displayUser?.postCount ?: userPosts.size

    val maxCollapsePx = with(density) {
        (ExpandedHeaderExtra + ActionRowHeight).toPx()
    }
    val collapseOffsetState = remember { mutableFloatStateOf(0f) }
    val nestedScrollConnection = remember(maxCollapsePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta >= 0f) return Offset.Zero
                val previous = collapseOffsetState.floatValue
                val next = (previous - delta).coerceIn(0f, maxCollapsePx)
                val consumed = next - previous
                if (consumed == 0f) return Offset.Zero
                collapseOffsetState.floatValue = next
                return Offset(0f, -consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val delta = available.y
                if (delta <= 0f) return Offset.Zero
                val previous = collapseOffsetState.floatValue
                val next = (previous - delta).coerceIn(0f, maxCollapsePx)
                val consumedY = previous - next
                if (consumedY == 0f) return Offset.Zero
                collapseOffsetState.floatValue = next
                return Offset(0f, consumedY)
            }
        }
    }

    val scrollProgress = (collapseOffsetState.floatValue / maxCollapsePx).coerceIn(0f, 1f)
    // Phase 1 (0→0.45): large avatar morphs into compact top identity
    // Phase 2 (0.45→1): action buttons fade, header becomes light compact bar
    val phase1 = (scrollProgress / 0.45f).coerceIn(0f, 1f)
    val phase2 = ((scrollProgress - 0.45f) / 0.55f).coerceIn(0f, 1f)
    val eased1 = FastOutSlowInEasing.transform(phase1)
    val eased2 = FastOutSlowInEasing.transform(phase2)

    // Grid items: 0 = info, 1 = tabs. Once tabs hit the top of the grid, pin them in an overlay.
    var tabsBarHeightPx by remember { mutableIntStateOf(0) }
    val tabsFixed by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex >= 1
        }
    }
    val tabsBarHeight = with(density) {
        if (tabsBarHeightPx > 0) tabsBarHeightPx.toDp() else 44.dp
    }
    // Own profile can always collapse so the add-post control can appear after scroll.
    // Other empty tabs stay fixed (no bounce over empty content).
    val canScrollProfile = when {
        selectedTab == 0 -> userPosts.isNotEmpty() || isOwnProfile
        isOwnProfile -> false
        selectedTab == 1 -> userReplies.isNotEmpty()
        selectedTab == 2 -> userLikes.isNotEmpty()
        else -> false
    }

    LaunchedEffect(canScrollProfile) {
        if (!canScrollProfile) {
            collapseOffsetState.floatValue = 0f
            gridState.scrollToItem(0)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        val surface = MaterialTheme.colorScheme.background
        // Teal → pure white in the compact phase so the whole screen reads as one surface.
        val headerFill = lerpColor(ProfileTeal, surface, scrollProgress)
        // Hand chrome clearance from in-flow header → grid top padding continuously so sticky
        // tabs never jump when an inset is suddenly applied.
        val statusBarTop = with(density) {
            WindowInsets.statusBars.getTop(this).toDp()
        }
        val overlayChrome = statusBarTop + CollapsedBarHeight
        val headerChromePad = lerp(overlayChrome, 0.dp, eased2)
        val gridChromePad = lerp(0.dp, overlayChrome, eased2)
        // Flatten as soon as the header collapses (phase1) — mid-state must already be square.
        val sheetTopRadius = lerp(InfoCardTopRadius, 0.dp, eased1)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .then(
                    if (canScrollProfile) Modifier.nestedScroll(nestedScrollConnection)
                    else Modifier
                )
                .background(surface)
        ) {
            // Collapse fully to 0 — TopChrome is an overlay; don't keep a second bar in-flow.
            val headerHeight = lerp(
                CollapsedBarHeight + ExpandedHeaderExtra + ActionRowHeight,
                0.dp,
                scrollProgress
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Overlap past the sheet corner radius so ears share the same fill.
                    .height(headerHeight + sheetTopRadius + 32.dp)
                    .background(headerFill)
                    .zIndex(0f)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
            ) {
                ProfileScrollHeader(
                    displayName = displayName,
                    isVerified = displayUser?.isVerified == true,
                    avatarUrl = displayUser?.avatarUrl,
                    isOwnProfile = isOwnProfile,
                    isFollowing = isFollowing,
                    postCount = postCount,
                    followerCount = displayUser?.followerCount ?: 0,
                    followingCount = displayUser?.followingCount ?: 0,
                    phase1 = eased1,
                    phase2 = eased2,
                    topChromePad = headerChromePad,
                    onSetPhotoClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onEditProfileClick = onEditProfileClick,
                    onSettingsClick = onSettingsClick,
                    onFollowClick = { viewModel.toggleFollow() },
                    onMessageClick = { viewModel.startDirectMessage() },
                    onShareClick = { shareProfile() },
                    onPostsStatClick = { selectedTab = 0 },
                    onFollowersClick = {
                        onFollowersClick(displayUser?.username ?: username)
                    },
                    onFollowingClick = {
                        onFollowingClick(displayUser?.username ?: username)
                    },
                    modifier = Modifier
                        .background(headerFill)
                        .then(
                            if (canScrollProfile) {
                                Modifier.pointerInput(maxCollapsePx) {
                                    detectVerticalDragGestures { _, dragAmount ->
                                        val previous = collapseOffsetState.floatValue
                                        collapseOffsetState.floatValue =
                                            (previous - dragAmount).coerceIn(0f, maxCollapsePx)
                                    }
                                }
                            } else {
                                Modifier
                            }
                        ),
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    userScrollEnabled = canScrollProfile,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(surface)
                        .then(
                            if (chromeState != null) {
                                Modifier.trackChromeVisibility(chromeState)
                            } else {
                                Modifier
                            }
                        ),
                    contentPadding = PaddingValues(
                        top = if (canScrollProfile) gridChromePad else 0.dp,
                        bottom = XiloSpacing.bottomNavPadding + 72.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    // 0: info sheet
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(headerFill)
                        ) {
                            ProfileInfoFields(
                                phone = displayUser?.phone,
                                bio = displayUser?.bio,
                                username = displayUser?.username ?: username,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (sheetTopRadius > 0.5.dp) {
                                            Modifier.clip(
                                                RoundedCornerShape(
                                                    topStart = sheetTopRadius,
                                                    topEnd = sheetTopRadius
                                                )
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .background(surface)
                            )
                        }
                    }

                    // 1: inline tabs (or height placeholder while the fixed overlay is shown)
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        val tabPadH = lerp(12.dp, 10.dp, eased2)
                        val tabPadV = lerp(8.dp, 4.dp, eased2)
                        if (tabsFixed) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(tabsBarHeight)
                                    .background(surface)
                            )
                        } else {
                            ProfileSegmentedTabs(
                                tabs = tabLabels,
                                selectedTab = selectedTab,
                                onTabSelected = { selectedTab = it },
                                compact = eased2 > 0.35f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(surface)
                                    .onSizeChanged { tabsBarHeightPx = it.height }
                                    .padding(horizontal = tabPadH, vertical = tabPadV)
                            )
                        }
                    }

                    when {
                        selectedTab == 0 -> {
                            if (userPosts.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    ProfileEmptyTab(text = stringResource(R.string.profile_empty_posts))
                                }
                            } else {
                                items(userPosts, key = { it.id }) { post ->
                                    ProfileMediaCell(
                                        post = post,
                                        onClick = { onPostClick(post.slug) }
                                    )
                                }
                                if (userPosts.size < 9) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Spacer(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(280.dp)
                                                .background(surface)
                                        )
                                    }
                                }
                            }
                        }
                        isOwnProfile -> {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                ProfileEmptyTab(text = stringResource(R.string.profile_empty_archived))
                            }
                        }
                        selectedTab == 1 -> {
                            if (userReplies.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    ProfileEmptyTab(text = stringResource(R.string.profile_empty_replies))
                                }
                            } else {
                                items(
                                    userReplies,
                                    key = { it.id },
                                    span = { GridItemSpan(maxLineSpan) },
                                ) { reply ->
                                    ProfileReplyRow(
                                        reply = reply,
                                        onClick = {
                                            if (reply.postSlug.isNotBlank()) {
                                                onReplyClick(reply.postSlug, reply.id)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        else -> {
                            if (userLikes.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    ProfileEmptyTab(text = stringResource(R.string.profile_empty_likes))
                                }
                            } else {
                                items(userLikes, key = { it.id }) { post ->
                                    ProfileMediaCell(
                                        post = post,
                                        onClick = { onPostClick(post.slug) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Fixed tabs: stay under TopChrome and do not scroll away with the grid.
            if (tabsFixed) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .zIndex(2.5f)
                        .background(surface)
                        .statusBarsPadding()
                        .padding(top = CollapsedBarHeight)
                ) {
                    ProfileSegmentedTabs(
                        tabs = tabLabels,
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        compact = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            ProfileTopChrome(
                displayName = displayName,
                isVerified = displayUser?.isVerified == true,
                postCount = postCount,
                phase1 = eased1,
                phase2 = eased2,
                onMenuClick = onBackClick,
                onMoreClick = {
                    if (isOwnProfile) onSettingsClick() else shareProfile()
                },
                showMore = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(3f)
            )

            if (isOwnProfile && canCreatePost) {
                // Show only after the user starts collapsing the header; keep solid XiloBlue.
                AnimatedVisibility(
                    visible = scrollProgress > 0.08f,
                    enter = fadeIn() + scaleIn(initialScale = 0.92f),
                    exit = fadeOut() + scaleOut(targetScale = 0.92f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = XiloSpacing.bottomNavPadding + 12.dp)
                        .zIndex(4f)
                ) {
                    Surface(
                        onClick = onCreatePostClick,
                        shape = RoundedCornerShape(12.dp),
                        color = XiloBlue,
                        contentColor = Color.White,
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            XiloIcon(
                                icon = XiloIcons.Camera,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = stringResource(R.string.profile_add_post),
                                style = PersianSafeCaptionStyle.copy(fontWeight = FontWeight.Medium),
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            if (isPreparingCrop || isUploadingAvatar) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f))
                        .zIndex(5f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ProfileTopChrome(
    displayName: String,
    isVerified: Boolean,
    postCount: Int,
    phase1: Float,
    phase2: Float,
    onMenuClick: () -> Unit,
    onMoreClick: () -> Unit,
    showMore: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val iconTint = lerpColor(Color.White, Color(0xFF1C1C1E), phase2)
    // White on teal header; XiloBlue once the collapsed chrome turns light.
    val verifiedTint = lerpColor(Color.White, XiloBlue, phase2)
    val titleAlpha = phase1
    val onlineAlpha = phase1 * (1f - phase2)
    val storiesAlpha = phase2
    val barBgAlpha = phase2 * 0.96f
    val chromeBg = MaterialTheme.colorScheme.background.copy(alpha = barBgAlpha)

    Column(
        modifier = modifier
            .background(chromeBg)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CollapsedBarHeight)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileChromeIconButton(
                icon = XiloIcons.Grid,
                contentDescription = stringResource(R.string.profile_menu),
                tint = iconTint,
                onClick = onMenuClick
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .alpha(titleAlpha),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayName,
                        color = lerpColor(Color.White, Color(0xFF1C1C1E), phase2),
                        fontFamily = YekanBakhFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        VerifiedBadge(size = 16.dp, tint = verifiedTint)
                    }
                }
                // Avoid a short fixed height — Persian dots under letters (e.g. پ) need room.
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.chat_online),
                        color = lerpColor(ProfileOnline, Color(0xFF8E8E93), phase2),
                        style = PersianSafeCaptionStyle,
                        modifier = Modifier.alpha(onlineAlpha)
                    )
                    Text(
                        text = stringResource(R.string.profile_posts_count, postCount),
                        color = Color(0xFF8E8E93),
                        style = PersianSafeCaptionStyle,
                        modifier = Modifier.alpha(storiesAlpha)
                    )
                }
            }

            if (showMore) {
                ProfileChromeIconButton(
                    icon = XiloIcons.More,
                    contentDescription = stringResource(R.string.profile_more),
                    tint = iconTint,
                    onClick = onMoreClick
                )
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }
        }
    }
}

@Composable
private fun ProfileScrollHeader(
    displayName: String,
    isVerified: Boolean,
    avatarUrl: String?,
    isOwnProfile: Boolean,
    isFollowing: Boolean,
    postCount: Int,
    followerCount: Int,
    followingCount: Int,
    phase1: Float,
    phase2: Float,
    topChromePad: Dp,
    onSetPhotoClick: () -> Unit,
    onEditProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFollowClick: () -> Unit,
    onMessageClick: () -> Unit,
    onShareClick: () -> Unit,
    onPostsStatClick: () -> Unit,
    onFollowersClick: () -> Unit,
    onFollowingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val avatarScale = 1f - phase1 * 0.55f
    val avatarAlpha = 1f - phase1
    val expandedIdentityAlpha = (1f - phase1).coerceIn(0f, 1f)
    val actionsAlpha = (1f - phase2).coerceIn(0f, 1f)
    val avatarSectionHeight = lerp(ExpandedHeaderExtra, 0.dp, phase1)
    val actionsHeight = lerp(ActionRowHeight, 0.dp, phase2)
    val bottomGap = lerp(12.dp, 0.dp, phase2)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topChromePad)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(avatarSectionHeight),
            contentAlignment = Alignment.TopCenter
        ) {
            if (phase1 < 0.98f) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .graphicsLayer {
                            scaleX = avatarScale
                            scaleY = avatarScale
                            alpha = avatarAlpha
                            translationY = -phase1 * 48f
                        }
                ) {
                    XiloAvatar(
                        imageUrl = avatarUrl,
                        size = AvatarExpandedSize,
                        modifier = Modifier.zIndex(1f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.alpha(expandedIdentityAlpha)
                    ) {
                        Text(
                            text = displayName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center
                        )
                        if (isVerified) {
                            Spacer(modifier = Modifier.width(6.dp))
                            VerifiedBadge(size = 18.dp, tint = Color.White)
                        }
                    }
                    if (isOwnProfile) {
                        Text(
                            text = stringResource(R.string.chat_online),
                            color = ProfileOnline,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .alpha(expandedIdentityAlpha)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .padding(top = 14.dp, bottom = 4.dp)
                            .alpha(expandedIdentityAlpha),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        ProfileStatItem(
                            count = formatCount(postCount),
                            label = stringResource(R.string.profile_stat_posts),
                            onClick = onPostsStatClick,
                            light = true,
                        )
                        ProfileStatItem(
                            count = formatCount(followerCount),
                            label = stringResource(R.string.profile_stat_followers),
                            onClick = onFollowersClick,
                            light = true,
                        )
                        ProfileStatItem(
                            count = formatCount(followingCount),
                            label = stringResource(R.string.profile_stat_following),
                            onClick = onFollowingClick,
                            light = true,
                        )
                    }
                }
            }
        }

        if (phase2 < 0.98f) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(actionsHeight)
                    .alpha(actionsAlpha)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isOwnProfile) {
                    ProfileGlassAction(
                        icon = XiloIcons.Camera,
                        label = stringResource(R.string.profile_set_photo),
                        onClick = onSetPhotoClick,
                        modifier = Modifier.weight(1f)
                    )
                    ProfileGlassAction(
                        icon = XiloIcons.Edit,
                        label = stringResource(R.string.profile_edit_info),
                        onClick = onEditProfileClick,
                        modifier = Modifier.weight(1f)
                    )
                    ProfileGlassAction(
                        icon = XiloIcons.Settings,
                        label = stringResource(R.string.profile_settings),
                        onClick = onSettingsClick,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    ProfileGlassAction(
                        icon = if (isFollowing) XiloIcons.ProfileSelected else XiloIcons.UserAdd,
                        label = stringResource(
                            if (isFollowing) R.string.profile_unfollow else R.string.profile_follow
                        ),
                        onClick = onFollowClick,
                        modifier = Modifier.weight(1f)
                    )
                    ProfileGlassAction(
                        icon = XiloIcons.Sms,
                        label = stringResource(R.string.profile_message),
                        onClick = onMessageClick,
                        modifier = Modifier.weight(1f)
                    )
                    ProfileGlassAction(
                        icon = XiloIcons.Share,
                        label = stringResource(R.string.profile_share),
                        onClick = onShareClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(bottomGap))
    }
}

@Composable
private fun ProfileGlassAction(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        // Wrap height so Persian descenders are never clipped by a fixed box.
        modifier = modifier
            .heightIn(min = GlassActionMinHeight)
            .wrapContentHeight(),
        shape = RoundedCornerShape(16.dp),
        color = GlassButtonBg,
        contentColor = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp, start = 8.dp, end = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            XiloIcon(
                icon = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = label,
                style = PersianSafeGlassActionLabelStyle,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun ProfileInfoFields(
    phone: String?,
    bio: String?,
    username: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!phone.isNullOrBlank()) {
            ProfileInfoRow(value = phone, label = stringResource(R.string.profile_label_mobile))
        }
        if (!bio.isNullOrBlank()) {
            ProfileInfoRow(value = bio, label = stringResource(R.string.profile_label_bio))
        }
        ProfileInfoRow(
            value = usernameHandle(username),
            label = stringResource(R.string.profile_label_username),
            forceLtr = true,
        )
    }
}

@Composable
private fun ProfileInfoRow(
    value: String,
    label: String,
    forceLtr: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 22.sp,
            style = if (forceLtr) {
                TextStyle.Default.forUsernameHandle()
            } else {
                TextStyle.Default
            },
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun ProfileSegmentedTabs(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val pillRadius = if (compact) 14.dp else 16.dp
    val segmentRadius = if (compact) 11.dp else 13.dp
    val rowPad = if (compact) 2.dp else 3.dp
    val segmentPadV = if (compact) 5.dp else 7.dp
    val labelSize = if (compact) 12.sp else 13.sp

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(pillRadius),
        color = TabPillBg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rowPad),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val selected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(segmentRadius))
                        .background(if (selected) TabSelectedBg else Color.Transparent)
                        .clickable { onTabSelected(index) }
                        .padding(vertical = segmentPadV, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = labelSize,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) {
                            TabSelectedContent
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileMediaCell(
    post: PostEntity,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 2.dp, bottom = 2.dp)
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    ) {
        val image = post.coverImageUrl?.takeIf { it.isNotBlank() } ?: post.authorAvatar
        AsyncImage(
            model = image,
            contentDescription = post.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            XiloIcon(
                icon = XiloIcons.Eye,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = formatCount(post.likeCount.coerceAtLeast(post.commentCount)),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ProfileChromeIconButton(
    icon: Int,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        XiloIcon(
            icon = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Kept for any external callers / previews that still import the old tab row. */
@Composable
fun ProfileTabRow(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    ProfileSegmentedTabs(
        tabs = tabs,
        selectedTab = selectedTab,
        onTabSelected = onTabSelected,
        modifier = modifier.padding(horizontal = 16.dp)
    )
}

@Composable
fun ProfileStatItem(
    count: String,
    label: String,
    onClick: (() -> Unit)? = null,
    light: Boolean = false,
) {
    val countColor = if (light) Color.White else MaterialTheme.colorScheme.onBackground
    val labelColor = if (light) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.secondary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) {
            Modifier.clickable(role = Role.Button, onClick = onClick)
        } else {
            Modifier
        }
    ) {
        Text(text = count, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = countColor)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = PersianSafeCaptionStyle,
            color = labelColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProfileEmptyTab(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 48.dp, vertical = 32.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun ProfileReplyRow(
    reply: ProfileReplyItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (reply.postTitle.isNotBlank()) {
            Text(
                text = reply.postTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = reply.content,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000 -> "${n / 1_000}K"
    else -> n.toString()
}

private fun lerpColor(start: Color, end: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f
    )
}
