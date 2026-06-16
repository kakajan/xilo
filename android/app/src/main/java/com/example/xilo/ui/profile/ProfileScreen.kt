package com.example.xilo.ui.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.xilo.theme.XiloBlue
import com.example.xilo.ui.components.LocalChromeVisibility
import com.example.xilo.ui.components.VerifiedBadge
import com.example.xilo.ui.components.XiloAvatar
import com.example.xilo.ui.components.XiloButton
import com.example.xilo.ui.components.XiloButtonStyle
import com.example.xilo.ui.components.trackChromeVisibility
import com.example.xilo.ui.feed.PostCard

private val HeroBannerHeight = 220.dp
private val CollapsedToolbarHeight = 64.dp

@Composable
fun ProfileScreen(
    username: String,
    onBackClick: () -> Unit,
    onPostClick: (String) -> Unit,
    onMessageClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val userPosts by viewModel.userPosts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(username) {
        viewModel.loadProfile(username)
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("پست‌ها", "پاسخ‌ها", "رسانه", "پسندیده‌ها")
    val listState = rememberLazyListState()
    val chromeState = LocalChromeVisibility.current

    if (isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = XiloBlue)
        }
        return
    }

    val displayUser = userProfile
    val heroUrl = displayUser?.avatarUrl

    val density = LocalDensity.current
    val heroHeightPx = with(density) { HeroBannerHeight.toPx() }
    val scrollOffset = if (listState.firstVisibleItemIndex == 0) {
        listState.firstVisibleItemScrollOffset.toFloat()
    } else {
        heroHeightPx
    }
    val collapseFraction = (scrollOffset / heroHeightPx).coerceIn(0f, 1f)
    val heroScale = 1f - collapseFraction * 0.15f
    val heroAlpha = 1f - collapseFraction * 0.4f

    val titleAlpha by animateFloatAsState(
        targetValue = if (collapseFraction > 0.6f) 1f else 0f,
        animationSpec = tween(200),
        label = "titleAlpha"
    )

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (chromeState != null) {
                        Modifier.trackChromeVisibility(chromeState, listState)
                    } else {
                        Modifier
                    }
                ),
            contentPadding = PaddingValues(bottom = 112.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HeroBannerHeight)
                ) {
                    AsyncImage(
                        model = heroUrl ?: "https://www.gravatar.com/avatar/00000000000000000000000000000000?d=mp&f=y",
                        contentDescription = "Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = heroScale
                                scaleY = heroScale
                                alpha = heroAlpha
                            }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.35f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.55f)
                                    )
                                )
                            )
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-48).dp)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    XiloAvatar(
                        imageUrl = displayUser?.avatarUrl,
                        size = 100.dp,
                        modifier = Modifier.zIndex(1f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayUser?.displayName ?: displayUser?.username ?: username,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        if (displayUser?.isVerified == true) {
                            Spacer(modifier = Modifier.width(6.dp))
                            VerifiedBadge(size = 20.dp)
                        }
                    }

                    Text(
                        text = "@${displayUser?.username ?: username}",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    if (!displayUser?.bio.isNullOrBlank()) {
                        Text(
                            text = displayUser?.bio ?: "",
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp, start = 8.dp, end = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(0.85f),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProfileStatItem(
                            count = formatCount(displayUser?.postCount ?: userPosts.size),
                            label = "پست‌ها"
                        )
                        ProfileStatItem(
                            count = formatCount(displayUser?.followerCount ?: 0),
                            label = "دنبال‌کننده"
                        )
                        ProfileStatItem(
                            count = formatCount(displayUser?.followingCount ?: 0),
                            label = "دنبال‌شونده"
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        XiloButton(
                            text = "دنبال کردن",
                            onClick = {},
                            leadingIcon = {
                                Icon(Icons.Default.PersonAddAlt1, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        XiloButton(
                            text = "گفتگو",
                            onClick = { onMessageClick(displayUser?.username ?: username) },
                            style = XiloButtonStyle.Outline,
                            leadingIcon = {
                                Icon(Icons.Default.MailOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {}, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                }
            }

            item {
                ProfileTabRow(
                    tabs = tabs,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (selectedTab == 0) {
                items(userPosts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        onPostClick = onPostClick,
                        onLikeClick = {},
                        onBookmarkClick = {}
                    )
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("موردی برای نمایش وجود ندارد.", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        // Collapsing overlay toolbar (REQ-AND-003)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CollapsedToolbarHeight)
                .background(
                    MaterialTheme.colorScheme.background.copy(alpha = collapseFraction.coerceIn(0f, 0.95f))
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f * (1f - collapseFraction * 0.5f)))
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = if (collapseFraction > 0.5f) MaterialTheme.colorScheme.onBackground else Color.White
                )
            }

            Text(
                text = displayUser?.displayName ?: username,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.alpha(titleAlpha)
            )

            IconButton(
                onClick = {},
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f * (1f - collapseFraction * 0.5f)))
            ) {
                Icon(
                    imageVector = if (collapseFraction > 0.5f) Icons.Default.Edit else Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = if (collapseFraction > 0.5f) MaterialTheme.colorScheme.onBackground else Color.White
                )
            }
        }
    }
}

@Composable
fun ProfileTabRow(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    ScrollableTabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = XiloBlue,
        edgePadding = 16.dp,
        divider = {},
        indicator = { tabPositions ->
            if (selectedTab < tabPositions.size) {
                Box(
                    modifier = Modifier
                        .tabIndicatorOffset(tabPositions[selectedTab])
                        .height(3.dp)
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(XiloBlue)
                )
            }
        },
        modifier = modifier
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == index) XiloBlue else MaterialTheme.colorScheme.secondary
                    )
                }
            )
        }
    }
}

@Composable
fun ProfileStatItem(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
    }
}

private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000 -> "${n / 1_000}K"
    else -> n.toString()
}
