package ir.xilo.app.ui.auth

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ir.xilo.app.R
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.ui.components.AuthField
import ir.xilo.app.ui.components.XiloButton
import ir.xilo.app.ui.components.XiloButtonStyle
import ir.xilo.app.ui.components.XiloTextField
import ir.xilo.app.ui.components.forInput

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val preferredLanguage by viewModel.preferredLanguage.collectAsState()
    val brandTitle by viewModel.brandTitle.collectAsState()
    val context = LocalContext.current
    var isLoginMode by remember { mutableStateOf(true) }
    var isOtpMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
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
        containerColor = Color.White
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 72.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = brandTitle.ifBlank { stringResource(R.string.app_name) },
                    style = MaterialTheme.typography.displayLarge,
                    color = XiloBlue
                )
                Text(
                    text = when {
                        isOtpMode -> stringResource(R.string.auth_otp_title)
                        isLoginMode -> stringResource(R.string.auth_welcome)
                        else -> stringResource(R.string.auth_register_title)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )

                if (isOtpMode) {
                    if (state is AuthUiState.OtpSent) {
                        XiloTextField(
                            value = otpCode,
                            onValueChange = { otpCode = it },
                            placeholder = stringResource(R.string.auth_hint_otp_code),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        XiloTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            placeholder = stringResource(R.string.auth_hint_phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else if (isLoginMode) {
                    XiloTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            viewModel.clearFieldError(AuthField.Email)
                        },
                        placeholder = stringResource(R.string.auth_hint_email),
                        modifier = Modifier.fillMaxWidth(),
                        isError = fieldErrors.containsKey(AuthField.Email),
                        errorText = fieldErrors[AuthField.Email],
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        XiloTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                viewModel.clearFieldError(AuthField.Password)
                            },
                            placeholder = stringResource(R.string.auth_hint_password),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            isError = fieldErrors.containsKey(AuthField.Password),
                            errorText = fieldErrors[AuthField.Password],
                            textStyle = MaterialTheme.typography.bodyLarge.forInput().copy(
                                textAlign = TextAlign.Left,
                                textDirection = TextDirection.Ltr,
                            ),
                        )
                    }
                } else {
                    XiloTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            viewModel.clearFieldError(AuthField.Email)
                        },
                        placeholder = stringResource(R.string.auth_hint_email_address),
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
                        placeholder = stringResource(R.string.auth_hint_display_name),
                        modifier = Modifier.fillMaxWidth(),
                        isError = fieldErrors.containsKey(AuthField.DisplayName),
                        errorText = fieldErrors[AuthField.DisplayName],
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        XiloTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                viewModel.clearFieldError(AuthField.Password)
                            },
                            placeholder = stringResource(R.string.auth_hint_password),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            isError = fieldErrors.containsKey(AuthField.Password),
                            errorText = fieldErrors[AuthField.Password],
                            textStyle = MaterialTheme.typography.bodyLarge.forInput().copy(
                                textAlign = TextAlign.Left,
                                textDirection = TextDirection.Ltr,
                            ),
                        )
                    }
                    Text(
                        text = stringResource(R.string.auth_password_rules),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.auth_username_after_login_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (state is AuthUiState.Loading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                } else {
                    if (isOtpMode) {
                        if (state is AuthUiState.OtpSent) {
                            XiloButton(
                                text = stringResource(R.string.auth_otp_verify),
                                onClick = { viewModel.verifyOtpLogin(phone, otpCode) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            XiloButton(
                                text = stringResource(R.string.auth_otp_send_code),
                                onClick = { viewModel.requestOtp(phone) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        XiloButton(
                            text = if (isLoginMode) {
                                stringResource(R.string.auth_login_submit)
                            } else {
                                stringResource(R.string.auth_register_submit)
                            },
                            onClick = {
                                if (isLoginMode) {
                                    viewModel.login(email, password)
                                } else {
                                    viewModel.register(
                                        email,
                                        password,
                                        displayName.takeIf { it.isNotBlank() },
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isOtpMode) {
                    XiloButton(
                        text = if (isLoginMode) {
                            stringResource(R.string.auth_no_account_signup)
                        } else {
                            stringResource(R.string.auth_have_account_login)
                        },
                        onClick = {
                            isLoginMode = !isLoginMode
                            viewModel.clearError()
                        },
                        style = XiloButtonStyle.Outline,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    XiloButton(
                        text = stringResource(R.string.auth_otp_login),
                        onClick = {
                            isOtpMode = true
                            viewModel.clearError()
                        },
                        style = XiloButtonStyle.Secondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    XiloButton(
                        text = stringResource(R.string.auth_otp_back),
                        onClick = {
                            isOtpMode = false
                            viewModel.clearError()
                        },
                        style = XiloButtonStyle.Secondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Outer align stays in parent direction; chip row itself is always LTR-ordered.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                AuthLanguageSelector(
                    selectedCode = preferredLanguage,
                    onLanguageSelected = { code ->
                        if (viewModel.selectLanguage(code)) {
                            (context as? Activity)?.recreate()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AuthLanguageSelector(
    selectedCode: String,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hard-fixed left→right order; never take layout direction or API list order.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .wrapContentWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            authLanguageChips.forEach { lang ->
                val selected = lang.code.equals(selectedCode, ignoreCase = true)
                val shape = RoundedCornerShape(20.dp)
                Text(
                    text = lang.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(shape)
                        .background(if (selected) XiloBlue else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onLanguageSelected(lang.code) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private data class AuthLanguageChip(val code: String, val label: String)

/** Absolute visual order (left → right), independent of app RTL/LTR. */
private val authLanguageChips = listOf(
    AuthLanguageChip("fa", "فارسی"),
    AuthLanguageChip("en", "English"),
    AuthLanguageChip("ar", "العربية"),
    AuthLanguageChip("ru", "Русский"),
    AuthLanguageChip("tr", "Türkçe"),
)
