package ir.xilo.app.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import ir.xilo.app.R
import ir.xilo.app.core.util.AppLocale
import ir.xilo.app.data.repository.BrandRepository
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.ui.components.AileBrandLogo
import ir.xilo.app.ui.components.AileLogoVariant
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingBrandViewModel @Inject constructor(
    brandRepository: BrandRepository,
) : ViewModel() {
    val brand = brandRepository.brand
}

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier,
    brandViewModel: OnboardingBrandViewModel = hiltViewModel(),
) {
    var step by remember { mutableStateOf(1) } // 1: Welcome, 2: Interests, 3: Suggestions
    val selectedInterests = remember { mutableStateListOf<String>() }
    val brand by brandViewModel.brand.collectAsState()
    val languageCode = AppLocale.languageCode(LocalContext.current)
    val brandName = brand.nameForLanguage(languageCode).ifBlank { stringResource(R.string.app_name) }

    val backgroundBg = MaterialTheme.colorScheme.background
    val textPrimary = MaterialTheme.colorScheme.onBackground

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // Skip Button
        TextButton(
            onClick = { onOnboardingComplete() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.onboarding_skip), color = XiloBlue, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Animated step-based switcher
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                },
                label = "onboarding_step"
            ) { targetStep ->
                when (targetStep) {
                    1 -> OnboardingStepWelcome(brandName = brandName)
                    2 -> OnboardingStepInterests(
                        selected = selectedInterests.toSet(),
                        onToggle = { interest ->
                            if (selectedInterests.contains(interest)) {
                                selectedInterests.remove(interest)
                            } else {
                                selectedInterests.add(interest)
                            }
                        }
                    )
                    3 -> OnboardingStepFollows(brandName = brandName)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Controls & Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        val active = index + 1 == step
                        Box(
                            modifier = Modifier
                                .size(if (active) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (active) XiloBlue else textPrimary.copy(alpha = 0.2f))
                        )
                    }
                }

                // Next Button
                Button(
                    onClick = {
                        if (step < 3) {
                            step += 1
                        } else {
                            onOnboardingComplete()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = XiloBlue),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(if (step == 3) R.string.onboarding_get_started else R.string.onboarding_continue),
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

        // Onboarding Tooltip Assistance
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
fun OnboardingStepInterests(selected: Set<String>, onToggle: (String) -> Unit) {
    val interests = listOf(
        stringResource(R.string.onboarding_interest_tech),
        stringResource(R.string.onboarding_interest_decentralization),
        stringResource(R.string.onboarding_interest_ui),
        stringResource(R.string.onboarding_interest_art),
        stringResource(R.string.onboarding_interest_crypto),
        stringResource(R.string.onboarding_interest_gaming),
        stringResource(R.string.onboarding_interest_memes),
        stringResource(R.string.onboarding_interest_kotlin),
        stringResource(R.string.onboarding_interest_database),
    )

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

        // Grid Layout
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
                        val isSelected = selected.contains(interest)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) XiloBlue else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onToggle(interest) }
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = interest,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingStepFollows(brandName: String) {
    val suggestedFollows = listOf(
        SuggestedUser("علی رضایی", "ali_rezaei", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&q=80&w=150", "معمار فنی $brandName"),
        SuggestedUser("امیرحسین امیری", "amir_dev", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&q=80&w=150", "توسعه‌دهنده اندروید"),
        SuggestedUser("دیانا حسینی", "diana_sync", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&q=80&w=150", "هماهنگ‌کننده شبکه‌های مش")
    )

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

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
        ) {
            items(suggestedFollows) { user ->
                var isFollowing by remember { mutableStateOf(true) }

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
                            text = user.name.take(1),
                            modifier = Modifier.align(Alignment.Center),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = user.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(text = "@${user.handle}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }

                    Button(
                        onClick = { isFollowing = !isFollowing },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowing) XiloBlue.copy(alpha = 0.15f) else XiloBlue,
                            contentColor = if (isFollowing) XiloBlue else Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        if (isFollowing) {
                            XiloIcon(
                                icon = XiloIcons.MessageTick,
                                contentDescription = stringResource(R.string.onboarding_synced),
                                tint = XiloBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.onboarding_synced), style = MaterialTheme.typography.labelLarge, fontSize = 12.sp)
                        } else {
                            Text(stringResource(R.string.onboarding_sync), style = MaterialTheme.typography.labelLarge, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

data class SuggestedUser(val name: String, val handle: String, val avatar: String, val bio: String)
