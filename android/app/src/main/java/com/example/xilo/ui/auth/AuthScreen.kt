package com.example.xilo.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.xilo.theme.XiloBlue
import com.example.xilo.ui.components.XiloButton
import com.example.xilo.ui.components.XiloButtonStyle
import com.example.xilo.ui.components.XiloTextField

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    var isLoginMode by remember { mutableStateOf(true) }

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is AuthUiState.Success) {
            onAuthSuccess()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFE3F2FD),
                        Color(0xFFFFFFFF)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Brand Header
                Text(
                    text = "Xilo ✈",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = XiloBlue
                )
                
                Text(
                    text = if (isLoginMode) "خوش آمدید" else "ثبت نام در پلتفرم",
                    fontSize = 18.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )

                if (state is AuthUiState.Error) {
                    Text(
                        text = (state as AuthUiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Input fields
                XiloTextField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = if (isLoginMode) "نام کاربری یا ایمیل" else "نام کاربری (username)",
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isLoginMode) {
                    Spacer(modifier = Modifier.height(12.dp))
                    XiloTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = "نشانی ایمیل (email)",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    XiloTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        placeholder = "نام نمایشی (اختیاری)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                XiloTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "رمز عبور",
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Submit Button
                if (state is AuthUiState.Loading) {
                    CircularProgressIndicator(color = XiloBlue)
                } else {
                    XiloButton(
                        text = if (isLoginMode) "ورود به حساب" else "ساخت حساب کاربری",
                        onClick = {
                            if (isLoginMode) {
                                viewModel.login(username, password)
                            } else {
                                viewModel.register(username, email, password, displayName.takeIf { it.isNotBlank() })
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Toggle Mode button
                XiloButton(
                    text = if (isLoginMode) "حساب کاربری ندارید؟ ثبت نام" else "دارای حساب کاربری هستید؟ ورود",
                    onClick = {
                        isLoginMode = !isLoginMode
                        viewModel.clearError()
                    },
                    style = XiloButtonStyle.Outline,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
