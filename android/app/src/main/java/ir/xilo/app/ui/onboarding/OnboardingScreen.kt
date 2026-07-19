package ir.xilo.app.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import ir.xilo.app.R
import ir.xilo.app.core.util.AppLocale
import ir.xilo.app.data.remote.dto.InterestDto
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.ui.components.AileBrandLogo
import ir.xilo.app.ui.components.AileLogoVariant
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.forUsernameHandle
import ir.xilo.app.ui.components.usernameHandle

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val brand by viewModel.brand.collectAsState()
    val languageCode = AppLocale.languageCode(LocalContext.current)
    val brandName = brand.nameForLanguage(languageCode).ifBlank { stringResource(R.string.app_name) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onContactsPermissionResult(granted)
    }

    LaunchedEffect(uiState.interestsError) {
        uiState.interestsError?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(uiState.contactsError) {
        uiState.contactsError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearContactsError()
        }
    }

    LaunchedEffect(uiState.step, uiState.contactsPhase) {
        if (uiState.step == 3 && uiState.contactsPhase == ContactsPhase.Idle) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.matchContacts()
            } else {
                viewModel.requestContactsPermission()
            }
        }
    }

    val backgroundBg = MaterialTheme.colorScheme.background
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val busy = uiState.savingInterests || uiState.completing ||
        (uiState.step == 2 && uiState.interestsLoading)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )

        TextButton(
            onClick = { viewModel.onSkipPressed(onOnboardingComplete) },
            enabled = !uiState.completing,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.onboarding_skip),
                color = XiloBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            AnimatedContent(
                targetState = uiState.step,
                transitionSpec = {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                        slideOutHorizontally { width -> -width } + fadeOut()
                },
                label = "onboarding_step"
            ) { targetStep ->
                when (targetStep) {
                    1 -> OnboardingStepWelcome(brandName = brandName)
                    2 -> OnboardingStepInterests(
                        interests = uiState.interests,
                        selectedIds = uiState.selectedInterestIds,
                        languageCode = languageCode,
                        loading = uiState.interestsLoading,
                        onToggle = viewModel::toggleInterest,
                        onRetry = viewModel::loadInterests,
                    )
                    else -> OnboardingStepFollows(
                        suggestions = uiState.suggestions,
                        contactsPhase = uiState.contactsPhase,
                        onFindContacts = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                        onSkipContacts = viewModel::skipContacts,
                        onRetryMatch = viewModel::matchContacts,
                        onToggleFollow = viewModel::toggleFollow,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        val active = index + 1 == uiState.step
                        Box(
                            modifier = Modifier
                                .size(if (active) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (active) XiloBlue else textPrimary.copy(alpha = 0.2f))
                        )
                    }
                }

                Button(
                    onClick = { viewModel.onPrimaryAction(onOnboardingComplete) },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = XiloBlue),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    if (uiState.savingInterests) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = stringResource(
                                if (uiState.step == 3) {
                                    R.string.onboarding_get_started
                                } else {
                                    R.string.onboarding_continue
                                }
                            ),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        XiloIcon(
                            icon = XiloIcons.ChevronEnd,
                            contentDescription = stringResource(R.string.onboarding_next_step),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingStepWelcome(brandName: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AileBrandLogo(
            variant = AileLogoVariant.MarkColored,
            height = 88.dp,
            contentDescription = brandName,
        )

        Text(
            text = stringResource(R.string.onboarding_welcome, brandName),
            style = MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp
        )

        Text(
            text = stringResource(R.string.onboarding_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 8.dp),
            lineHeight = 22.sp
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = XiloBlue.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                XiloIcon(
                    icon = XiloIcons.Bookmark,
                    contentDescription = stringResource(R.string.onboarding_tip_cd),
                    tint = XiloBlue,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.onboarding_tip, brandName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun OnboardingStepInterests(
    interests: List<InterestDto>,
    selectedIds: Set<String>,
    languageCode: String,
    loading: Boolean,
    onToggle: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.onboarding_interests_title),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.onboarding_interests_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        when {
            loading -> {
                CircularProgressIndicator(color = XiloBlue)
            }
            interests.isEmpty() -> {
                Text(
                    text = stringResource(R.string.onboarding_interests_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.common_refresh))
                }
            }
            else -> {
                val rows = interests.chunked(3)
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (row in rows) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (interest in row) {
                                val isSelected = selectedIds.contains(interest.id)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (isSelected) {
                                                XiloBlue
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        )
                                        .clickable { onToggle(interest.id) }
                                        .padding(vertical = 14.dp, horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = interest.labelFor(languageCode),
                                        color = if (isSelected) {
                                            Color.White
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            repeat(3 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingStepFollows(
    suggestions: List<SuggestedMatchUi>,
    contactsPhase: ContactsPhase,
    onFindContacts: () -> Unit,
    onSkipContacts: () -> Unit,
    onRetryMatch: () -> Unit,
    onToggleFollow: (String) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.onboarding_suggested_title),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.onboarding_suggested_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        when (contactsPhase) {
            ContactsPhase.Idle, ContactsPhase.NeedsPermission -> {
                Text(
                    text = stringResource(R.string.onboarding_contacts_rationale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )
                Button(
                    onClick = onFindContacts,
                    colors = ButtonDefaults.buttonColors(containerColor = XiloBlue),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(stringResource(R.string.onboarding_find_contacts))
                }
                TextButton(onClick = onSkipContacts) {
                    Text(stringResource(R.string.onboarding_skip_contacts))
                }
            }
            ContactsPhase.Loading -> {
                CircularProgressIndicator(color = XiloBlue)
                Text(
                    text = stringResource(R.string.onboarding_matching_contacts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
            ContactsPhase.Error -> {
                Text(
                    text = stringResource(R.string.onboarding_contacts_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onRetryMatch) {
                    Text(stringResource(R.string.common_refresh))
                }
                TextButton(onClick = onSkipContacts) {
                    Text(stringResource(R.string.onboarding_skip_contacts))
                }
            }
            ContactsPhase.Skipped -> {
                Text(
                    text = stringResource(R.string.onboarding_contacts_skipped),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onFindContacts) {
                    Text(stringResource(R.string.onboarding_find_contacts))
                }
            }
            ContactsPhase.Ready -> {
                if (suggestions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.onboarding_no_matches),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(suggestions, key = { it.id }) { user ->
                            SuggestionRow(
                                user = user,
                                onToggleFollow = { onToggleFollow(user.username) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    user: SuggestedMatchUi,
    onToggleFollow: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = 0.3f))
        ) {
            Text(
                text = user.displayName.take(1),
                modifier = Modifier.align(Alignment.Center),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.displayName,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = usernameHandle(user.username),
                style = MaterialTheme.typography.labelSmall.forUsernameHandle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }

        Button(
            onClick = onToggleFollow,
            enabled = !user.isFollowBusy,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (user.isFollowing) XiloBlue.copy(alpha = 0.15f) else XiloBlue,
                contentColor = if (user.isFollowing) XiloBlue else Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            if (user.isFollowing) {
                XiloIcon(
                    icon = XiloIcons.MessageTick,
                    contentDescription = stringResource(R.string.onboarding_following),
                    tint = XiloBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringResource(R.string.onboarding_following),
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 12.sp,
                )
            } else {
                Text(
                    stringResource(R.string.onboarding_follow),
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
