package com.example.xilo.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.xilo.theme.XiloBlue
import com.example.xilo.ui.components.AuthField
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
    val snackbarHostState = remember { SnackbarHostState() }

    val fieldErrors = (state as? AuthUiState.Error)?.fieldErrors.orEmpty()
    val generalError = (state as? AuthUiState.Error)?.generalError

    LaunchedEffect(state) {
        when (state) {
            is AuthUiState.Success -> onAuthSuccess()
            else -> Unit
        }
    }

    LaunchedEffect(generalError) {
        generalError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFE3F2FD), Color(0xFFFFFFFF))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    Text(
                        text = "Xilo ✈",
                        style = MaterialTheme.typography.displayLarge,
                        color = XiloBlue
                    )
                    Text(
                        text = if (isLoginMode) "خوش آمدید" else "ثبت نام در پلتفرم",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                    )

                    XiloTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            viewModel.clearFieldError(AuthField.Username)
                        },
                        placeholder = if (isLoginMode) "نام کاربری یا ایمیل" else "نام کاربری",
                        modifier = Modifier.fillMaxWidth(),
                        isError = fieldErrors.containsKey(AuthField.Username),
                        errorText = fieldErrors[AuthField.Username],
                    )

                    if (!isLoginMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        XiloTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                viewModel.clearFieldError(AuthField.Email)
                            },
                            placeholder = "نشانی ایمیل",
                            modifier = Modifier.fillMaxWidth(),
                            isError = fieldErrors.containsKey(AuthField.Email),
                            errorText = fieldErrors[AuthField.Email],
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        XiloTextField(
                            value = displayName,
                            onValueChange = {
                                displayName = it
                                viewModel.clearFieldError(AuthField.DisplayName)
                            },
                            placeholder = "نام نمایشی (اختیاری)",
                            modifier = Modifier.fillMaxWidth(),
                            isError = fieldErrors.containsKey(AuthField.DisplayName),
                            errorText = fieldErrors[AuthField.DisplayName],
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    XiloTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            viewModel.clearFieldError(AuthField.Password)
                        },
                        placeholder = "رمز عبور",
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        isError = fieldErrors.containsKey(AuthField.Password),
                        errorText = fieldErrors[AuthField.Password],
                    )

                    if (!isLoginMode) {
                        Text(
                            text = "رمز عبور باید حداقل ۸ کاراکتر و شامل حرف بزرگ انگلیسی، عدد و کاراکتر ویژه باشد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            text = "نام کاربری فقط با حروف انگلیسی، عدد و زیرخط (۳ تا ۳۲ کاراکتر).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

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
}
