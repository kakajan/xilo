package ir.xilo.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
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
import ir.xilo.app.ui.chat.SavedHubScreen
import ir.xilo.app.ui.contact.ContactDetailScreen
import ir.xilo.app.ui.main.MainScreen
import ir.xilo.app.ui.main.MainScreenViewModel
import ir.xilo.app.ui.postdetail.PostDetailScreen
import ir.xilo.app.ui.profile.FollowListMode
import ir.xilo.app.ui.profile.FollowListScreen
import ir.xilo.app.ui.profile.ProfileScreen
import ir.xilo.app.ui.settings.ChatFoldersScreen
import ir.xilo.app.ui.settings.DevicesScreen
import ir.xilo.app.ui.settings.SettingsScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

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
            replyToPost = key.replyToPost,
            onBackClick = { backStack.removeLastOrNull() },
            onAuthorClick = { username ->
              if (username.isNotBlank()) backStack.add(ProfileKey(username))
            },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<ProfileKey> { key ->
          ProfileScreen(
            username = key.username,
            onBackClick = { backStack.removeLastOrNull() },
            onPostClick = { slug -> backStack.add(PostDetailKey(slug = slug)) },
            onSettingsClick = { backStack.add(SettingsKey) },
            onEditProfileClick = { backStack.add(SettingsKey) },
            onSetPhotoClick = { backStack.add(SettingsKey) },
            onCreatePostClick = { backStack.add(CreatePostKey) },
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
        entry<CreatePostKey> {
          ir.xilo.app.ui.feed.CreatePostScreen(
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
            modifier = Modifier.fillMaxSize()
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
