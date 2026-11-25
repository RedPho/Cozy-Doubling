package com.grepho.cozydoubling.features.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grepho.cozydoubling.BuildConfig
import com.grepho.cozydoubling.R
import com.grepho.cozydoubling.core.validation.TextLimits

// --- THE SCREEN ENTRY POINT ---
@Composable
fun LoginScreen(viewModel: LoginViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LoginPage(
        uiState = uiState,
        onGoogleSignIn = { context.findActivity()?.let(viewModel::signInWithGoogle) },
        onEmailSignIn = viewModel::signInWithEmail,
        onRegister = viewModel::register,
        onClearError = viewModel::clearError
    )
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
private fun LoginError.asText(): String = when (this) {
    is LoginError.Resource -> stringResource(resId)
    is LoginError.Message -> text
}

// --- THE UI COMPONENT ---
@Composable
fun LoginPage(
    uiState: LoginUiState,
    onGoogleSignIn: () -> Unit,
    onEmailSignIn: (email: String, password: String) -> Unit,
    onRegister: (email: String, password: String, confirmPassword: String, displayName: String) -> Unit,
    onClearError: () -> Unit
) {
    var showEmailDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 40.dp, vertical = 24.dp)
            ) {
                // --- Logo ---
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Eco,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = stringResource(R.string.login_welcome),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.login_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(56.dp))

                // --- Sign in with Google ---
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .testTag("login_google_button")
                        .clickable(enabled = !uiState.isLoading) { onGoogleSignIn() },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f)),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle, // TODO: official Google "G" mark per branding guidelines
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.login_google_signin),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- Email sign-in / registration: a first-class option, not a hidden reviewer path ---
                OutlinedButton(
                    onClick = { showEmailDialog = true },
                    enabled = !uiState.isLoading,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("login_email_entry")
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.login_continue_with_email),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (uiState.isLoading && !showEmailDialog) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }

                Spacer(modifier = Modifier.height(32.dp))

                // --- Footer: legal links ---
                val uriHandler = LocalUriHandler.current
                val annotatedString = buildAnnotatedString {
                    append(stringResource(R.string.login_footer_prefix))

                    pushStringAnnotation(tag = "URL", annotation = BuildConfig.TERMS_OF_SERVICE_URL)
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                        append(stringResource(R.string.login_footer_terms))
                    }
                    pop()

                    append(stringResource(R.string.login_footer_and))

                    pushStringAnnotation(tag = "URL", annotation = BuildConfig.PRIVACY_POLICY_URL)
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                        append(stringResource(R.string.login_footer_privacy))
                    }
                    pop()
                    append(stringResource(R.string.login_footer_suffix))
                }

                ClickableText(
                    text = annotatedString,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    ),
                    onClick = { offset ->
                        annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                uriHandler.openUri(annotation.item)
                            }
                    }
                )
            }
        }
    }

    if (showEmailDialog) {
        EmailAuthDialog(
            isLoading = uiState.isLoading,
            error = uiState.error?.asText(),
            onDismiss = {
                showEmailDialog = false
                onClearError()
            },
            onSignIn = onEmailSignIn,
            onRegister = onRegister,
            onEdit = { if (uiState.error != null) onClearError() }
        )
    } else if (uiState.error != null) {
        AlertDialog(
            onDismissRequest = onClearError,
            title = { Text(stringResource(R.string.login_error_title)) },
            text = { Text(uiState.error.asText()) },
            confirmButton = {
                TextButton(onClick = onClearError) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }
}

@Composable
fun EmailAuthDialog(
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    onRegister: (email: String, password: String, confirmPassword: String, displayName: String) -> Unit,
    onEdit: () -> Unit
) {
    var isRegistering by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val submit: () -> Unit = {
        if (isRegistering) onRegister(email, password, confirmPassword, displayName) else onSignIn(email, password)
    }
    val passwordTransformation =
        if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(stringResource(if (isRegistering) R.string.login_register_title else R.string.login_sign_in_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = TextLimits.limitInput(it, TextLimits.MAX_EMAIL_LENGTH); onEdit() },
                    label = { Text(stringResource(R.string.login_email_label)) },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_email_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                )
                if (isRegistering) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = {
                            displayName = TextLimits.limitInput(it, TextLimits.MAX_DISPLAY_NAME_LENGTH); onEdit()
                        },
                        label = { Text(stringResource(R.string.login_display_name_label)) },
                        singleLine = true,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_display_name_input"),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = TextLimits.limitInput(it, TextLimits.MAX_PASSWORD_LENGTH); onEdit() },
                    label = { Text(stringResource(R.string.login_password_label)) },
                    supportingText = if (isRegistering) {
                        { Text(stringResource(R.string.login_password_hint_min)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password_input"),
                    visualTransformation = passwordTransformation,
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(
                                    if (passwordVisible) R.string.login_hide_password else R.string.login_show_password
                                )
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (isRegistering) ImeAction.Next else ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (!isRegistering) submit() })
                )
                if (isRegistering) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = TextLimits.limitInput(it, TextLimits.MAX_PASSWORD_LENGTH); onEdit()
                        },
                        label = { Text(stringResource(R.string.login_confirm_password_label)) },
                        singleLine = true,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_confirm_password_input"),
                        visualTransformation = passwordTransformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() })
                    )
                }
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("login_error_text")
                    )
                }
                TextButton(
                    onClick = {
                        isRegistering = !isRegistering
                        confirmPassword = ""
                        onEdit()
                    },
                    enabled = !isLoading,
                    modifier = Modifier.testTag("login_mode_toggle")
                ) {
                    Text(
                        stringResource(
                            if (isRegistering) R.string.login_switch_to_sign_in else R.string.login_switch_to_register
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = submit,
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.testTag("login_submit_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(if (isRegistering) R.string.login_register_button else R.string.login_button))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
