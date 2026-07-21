package ir.xilo.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import ir.xilo.app.ui.chat.ChatConversationScreen
import ir.xilo.app.ui.chat.ChatViewModel
import ir.xilo.app.ui.chat.NewChatScreen
import ir.xilo.app.ui.chat.SavedHubScreen
import ir.xilo.app.ui.contact.ContactDetailScreen
import ir.xilo.app.ui.contacts.ContactsScreen
import ir.xilo.app.ui.main.MainScreen
import ir.xilo.app.ui.main.MainScreenViewModel
import ir.xilo.app.ui.postdetail.PostDetailScreen
import ir.xilo.app.ui.profile.EditProfileScreen
import ir.xilo.app.ui.profile.FollowListMode
import ir.xilo.app.ui.profile.FollowListScreen
import ir.xilo.app.ui.profile.ProfileScreen
import ir.xilo.app.ui.settings.ChatFoldersScreen
import ir.xilo.app.ui.settings.DevicesScreen
import ir.xilo.app.ui.settings.NotificationPreferencesScreen
import ir.xilo.app.ui.settings.SettingsScreen
import ir.xilo.app.ui.notifications.NotificationInboxScreen
import ir.xilo.app.push.PushNavigationCoordinator
import ir.xilo.app.push.PushNavigationEntryPoint
import ir.xilo.app.di.AppStateEntryPoint
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.ui.platform.LocalContext

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)
  val context = LocalContext.current
  val appState = remember {
    EntryPointAccessors.fromApplication(
      context.applicationContext,
      AppStateEntryPoint::class.java,
    )
  }
  val pushNavigationCoordinator = remember {
    EntryPointAccessors.fromApplication(
      context.applicationContext,
      PushNavigationEntryPoint::class.java,
    ).pushNavigationCoordinator()
  }
  val isAuthenticated by appState.tokenManager().isAuthenticatedFlow.collectAsStateWithLifecycle()
  val onboardingCompleted by appState.authRepository().onboardingCompletedFlow.collectAsStateWithLifecycle()
  val pendingPushNav by pushNavigationCoordinator.pendingNavKey.collectAsStateWithLifecycle()

  LaunchedEffect(pendingPushNav, isAuthenticated, onboardingCompleted) {
    val navKey = pendingPushNav ?: return@LaunchedEffect
    if (!isAuthenticated || !onboardingCompleted) return@LaunchedEffect
    backStack.add(navKey)
    pushNavigationCoordinator.consumePendingNavKey()
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          val mainViewModel: MainScreenViewModel = hiltViewModel()
          MainScreen(
            onItemClick = { navKey -> backStack.add(navKey) },
            modifier = Modifier.fillMaxSize(),
            viewModel = mainViewModel
          )
        }
        entry<PostDetailKey> { key ->
          PostDetailScreen(
            slug = key.slug,
            replyToCommentId = key.replyToCommentId,
            replyToAuthor = key.replyToAuthor,
            replyToAuthorAvatar = key.replyToAuthorAvatar,
            replyToPost = key.replyToPost,
            onBackClick = { backStack.removeLastOrNull() },
            onAuthorClick = { username ->
              if (username.isNotBlank()) backStack.add(ProfileKey(username))
            },
            onEditPost = { postId ->
              backStack.add(CreatePostKey(editPostId = postId))
            },
            onQuotePost = { postId ->
              backStack.add(CreatePostKey(quotedPostId = postId))
            },
            onQuotedPostClick = { quotedSlug ->
              if (quotedSlug.isNotBlank()) backStack.add(PostDetailKey(slug = quotedSlug))
            },
            onHashtagClick = { tag ->
              if (tag.isNotBlank()) backStack.add(TagFeedKey(tag = tag))
            },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<TagFeedKey> { key ->
          ir.xilo.app.ui.feed.TagFeedScreen(
            tag = key.tag,
            onBackClick = { backStack.removeLastOrNull() },
            onPostClick = { slug -> backStack.add(PostDetailKey(slug = slug)) },
            onHashtagClick = { tag ->
              if (tag.isNotBlank() && tag != key.tag) {
                backStack.add(TagFeedKey(tag = tag))
              }
            },
            onAuthorClick = { username ->
              if (username.isNotBlank()) backStack.add(ProfileKey(username))
            },
            onQuotePost = { postId ->
              backStack.add(CreatePostKey(quotedPostId = postId))
            },
            modifier = Modifier.fillMaxSize(),
          )
        }
        entry<ProfileKey> { key ->
          ProfileScreen(
            username = key.username,
            onBackClick = { backStack.removeLastOrNull() },
            onPostClick = { slug -> backStack.add(PostDetailKey(slug = slug)) },
            onSettingsClick = { backStack.add(SettingsKey) },
            onEditProfileClick = { backStack.add(EditProfileKey) },
            onCreatePostClick = {
              // ProfileScreen hides FAB when role cannot create posts.
              backStack.add(CreatePostKey())
            },
            onChatClick = { chatId ->
              backStack.add(ChatConversationKey(chatId = chatId))
            },
            onFollowersClick = { username ->
              backStack.add(
                FollowListKey(username = username, mode = FollowListMode.Followers.name)
              )
            },
            onFollowingClick = { username ->
              backStack.add(
                FollowListKey(username = username, mode = FollowListMode.Following.name)
              )
            },
            onReplyClick = { slug, commentId ->
              backStack.add(
                PostDetailKey(slug = slug, replyToCommentId = commentId)
              )
            },
            refreshOnResume = false,
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<FollowListKey> { key ->
          val mode = runCatching { FollowListMode.valueOf(key.mode) }
            .getOrDefault(FollowListMode.Followers)
          FollowListScreen(
            username = key.username,
            mode = mode,
            onBackClick = { backStack.removeLastOrNull() },
            onUserClick = { username ->
              if (username.isNotBlank()) backStack.add(ProfileKey(username))
            },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<ChatConversationKey> { key ->
          val chatViewModel: ChatViewModel = hiltViewModel()
          ChatConversationScreen(
            chatId = key.chatId,
            onBackClick = { backStack.removeLastOrNull() },
            onContactClick = { backStack.add(ContactDetailKey(key.chatId)) },
            isSavedMessages = key.isSavedMessages,
            modifier = Modifier.fillMaxSize(),
            viewModel = chatViewModel
          )
        }
        entry<NewChatKey> {
          NewChatScreen(
            onBackClick = { backStack.removeLastOrNull() },
            onChatStarted = { chatId ->
              backStack.removeLastOrNull()
              backStack.add(ChatConversationKey(chatId = chatId))
            },
            modifier = Modifier.fillMaxSize(),
          )
        }
        entry<ContactsKey> {
          ContactsScreen(
            onBackClick = { backStack.removeLastOrNull() },
            onProfileClick = { username ->
              if (username.isNotBlank()) backStack.add(ProfileKey(username))
            },
            onChatStarted = { chatId ->
              backStack.removeLastOrNull()
              backStack.add(ChatConversationKey(chatId = chatId))
            },
            modifier = Modifier.fillMaxSize(),
          )
        }
        entry<CreatePostKey> { key ->
          ir.xilo.app.ui.feed.CreatePostScreen(
            editPostId = key.editPostId,
            quotedPostId = key.quotedPostId,
            onBackClick = { backStack.removeLastOrNull() },
            onPostCreated = { backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<SettingsKey> {
          val mainViewModel: MainScreenViewModel = hiltViewModel()
          SettingsScreen(
            onBackClick = { backStack.removeLastOrNull() },
            onLogoutComplete = {
              mainViewModel.updateAuthStatus()
              while (backStack.size > 1) {
                backStack.removeLastOrNull()
              }
            },
            onMyProfileClick = {
              mainViewModel.requestTab(3)
              backStack.removeLastOrNull()
            },
            onSavedMessagesClick = {
              backStack.add(SavedHubKey)
            },
            onDevicesClick = { backStack.add(DevicesKey) },
            onChatFoldersClick = { backStack.add(ChatFoldersKey) },
            onNotificationPreferencesClick = { backStack.add(NotificationPreferencesKey) },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<EditProfileKey> {
          EditProfileScreen(
            onBackClick = { backStack.removeLastOrNull() },
            onSaved = { backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize(),
          )
        }
        entry<SavedHubKey> {
          val chatViewModel: ChatViewModel = hiltViewModel()
          SavedHubScreen(
            onBackClick = { backStack.removeLastOrNull() },
            onPostClick = { slug ->
              backStack.add(PostDetailKey(slug = slug))
            },
            onCommentClick = { slug, commentId ->
              backStack.add(
                PostDetailKey(
                  slug = slug,
                  replyToCommentId = commentId,
                )
              )
            },
            onAuthorClick = { username ->
              if (username.isNotBlank()) backStack.add(ProfileKey(username))
            },
            modifier = Modifier.fillMaxSize(),
            viewModel = chatViewModel,
          )
        }
        entry<DevicesKey> {
          val mainViewModel: MainScreenViewModel = hiltViewModel()
          DevicesScreen(
            onBackClick = { backStack.removeLastOrNull() },
            onCurrentSessionRevoked = {
              mainViewModel.updateAuthStatus()
              while (backStack.size > 1) {
                backStack.removeLastOrNull()
              }
            },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<ChatFoldersKey> {
          ChatFoldersScreen(
            onBackClick = { backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<NotificationsKey> {
          NotificationInboxScreen(
            onBackClick = { backStack.removeLastOrNull() },
            onPostClick = { slug, commentId ->
              backStack.add(
                PostDetailKey(
                  slug = slug,
                  replyToCommentId = commentId,
                )
              )
            },
            onChatClick = { chatId ->
              backStack.add(ChatConversationKey(chatId = chatId))
            },
            onProfileClick = { username ->
              if (username.isNotBlank()) backStack.add(ProfileKey(username))
            },
            onPreferencesClick = { backStack.add(NotificationPreferencesKey) },
            modifier = Modifier.fillMaxSize(),
          )
        }
        entry<NotificationPreferencesKey> {
          NotificationPreferencesScreen(
            onBackClick = { backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize(),
          )
        }
        entry<ContactDetailKey> { key ->
          ContactDetailScreen(
            chatId = key.chatId,
            onBackClick = { backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize()
          )
        }
      },
      transitionSpec = {
        slideInHorizontally(
          initialOffsetX = { it },
          animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        ) togetherWith slideOutHorizontally(
          targetOffsetX = { -it / 3 },
          animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        )
      },
      popTransitionSpec = {
        slideInHorizontally(
          initialOffsetX = { -it / 3 },
          animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        ) togetherWith slideOutHorizontally(
          targetOffsetX = { it },
          animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        )
      },
      predictivePopTransitionSpec = {
        slideInHorizontally(
          initialOffsetX = { -it / 3 },
          animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        ) togetherWith slideOutHorizontally(
          targetOffsetX = { it },
          animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        )
      }
  )
}
