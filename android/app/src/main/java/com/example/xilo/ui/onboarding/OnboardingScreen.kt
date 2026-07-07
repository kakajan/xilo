package com.example.xilo.ui.onboarding

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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xilo.theme.XiloBlue
import com.example.xilo.ui.components.XiloIcon
import com.example.xilo.ui.components.XiloIcons

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(1) } // 1: Welcome, 2: Interests, 3: Suggestions
    val selectedInterests = remember { mutableStateListOf<String>() }

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
            Text("رد شدن", color = XiloBlue, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                    1 -> OnboardingStepWelcome()
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
                    3 -> OnboardingStepFollows()
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
                        text = if (step == 3) "شروع به کار" else "ادامه",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    XiloIcon(
                        icon = XiloIcons.ChevronEnd,
                        contentDescription = "گام بعدی",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingStepWelcome() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Logo
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(XiloBlue),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .rotate(45f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
            )
        }

        Text(
            text = "به زیلو خوش آمدید",
            style = MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp
        )

        Text(
            text = "یک شبکه اجتماعی محلی غیرمتمرکز، سریع و امن. به صورت محلی متصل شوید، در گفتگوها پاسخ دهید و از طریق شبکه‌های مش به طور مستقیم با دوستان خود همگام‌سازی کنید.",
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
                    contentDescription = "نکته کمکی",
                    tint = XiloBlue,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "نکته: زیلو تمام پیام‌های شما را در یک پایگاه داده محلی SQLite ذخیره می‌کند. برای همگام‌سازی زنده، دکمه مش را در پروفایل خود فعال کنید!",
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
        "فناوری" to "Tech",
        "تمرکززدایی" to "Decentralization",
        "رابط کاربری" to "UI/UX",
        "هنر" to "Art",
        "ارز دیجیتال" to "Crypto",
        "بازی" to "Gaming",
        "میم‌ها" to "Memes",
        "کاتلین" to "Kotlin",
        "پایگاه داده" to "Room DB"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "علاقه‌مندی‌های خود را انتخاب کنید",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = "ما فید اکتشاف شما را بر اساس انتخاب‌های شما شخصی‌سازی می‌کنیم.",
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
                    for ((farsi, english) in row) {
                        val isSelected = selected.contains(english)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) XiloBlue else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onToggle(english) }
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = farsi,
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
fun OnboardingStepFollows() {
    val suggestedFollows = listOf(
        SuggestedUser("علی رضایی", "ali_rezaei", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&q=80&w=150", "معمار فنی زیلو"),
        SuggestedUser("امیرحسین امیری", "amir_dev", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&q=80&w=150", "توسعه‌دهنده اندروید"),
        SuggestedUser("دیانا حسینی", "diana_sync", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&q=80&w=150", "هماهنگ‌کننده شبکه‌های مش")
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "گره‌های پیشنهادی",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = "برای همگام‌سازی آنی پایگاه داده با همتایان خود متصل شوید.",
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
                                contentDescription = "همگام شده",
                                tint = XiloBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("همگام شده", style = MaterialTheme.typography.labelLarge, fontSize = 12.sp)
                        } else {
                            Text("همگام‌سازی", style = MaterialTheme.typography.labelLarge, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

data class SuggestedUser(val name: String, val handle: String, val avatar: String, val bio: String)
