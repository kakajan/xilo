package com.example.xilo

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
import com.example.xilo.ui.chat.ChatConversationScreen
import com.example.xilo.ui.chat.ChatViewModel
import com.example.xilo.ui.contact.ContactDetailScreen
import com.example.xilo.ui.main.MainScreen
import com.example.xilo.ui.main.MainScreenViewModel
import com.example.xilo.ui.postdetail.PostDetailScreen
import com.example.xilo.ui.settings.SettingsScreen

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
            onBackClick = { backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<ChatConversationKey> { key ->
          val chatViewModel: ChatViewModel = hiltViewModel()
          ChatConversationScreen(
            chatId = key.chatId,
            onBackClick = { backStack.removeLastOrNull() },
            onContactClick = { backStack.add(ContactDetailKey(key.chatId)) },
            modifier = Modifier.fillMaxSize(),
            viewModel = chatViewModel
          )
        }
        entry<CreatePostKey> {
          com.example.xilo.ui.feed.CreatePostScreen(
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
