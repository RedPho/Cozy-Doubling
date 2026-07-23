package com.grepho.cozydoubling.features.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grepho.cozydoubling.BuildConfig
import com.grepho.cozydoubling.core.Supabase
import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithGoogle
import io.github.jan.supabase.compose.auth.composeAuth

// --- THE SCREEN ENTRY POINT ---
@Composable
fun LoginScreen(viewModel: LoginViewModel = viewModel()) {
    val error by viewModel.error.collectAsState()

    // This hook manages the Google Sign-In intent and the response from Android
    val action = Supabase.client.composeAuth.rememberSignInWithGoogle(
        onResult = { result ->
            when (result) {
                is NativeSignInResult.Success -> println("DEBUG: Google Success")
                is NativeSignInResult.Error -> println("DEBUG: Google Error: ${result.message}")
                else -> println("DEBUG: Google Other: $result")
            }
        }
    )

    LoginPage(
        onSignInClick = { action.startFlow() },
        onReviewerLogin = { email, pass -> viewModel.signInWithEmail(email, pass) },
        error = error,
        onClearError = { viewModel.clearError() }
    )
}

// --- THE UI COMPONENT ---
@Composable
fun LoginPage(
    onSignInClick: () -> Unit,
    onReviewerLogin: (String, String) -> Unit,
    error: String?,
    onClearError: () -> Unit
) {
    var showReviewerDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        // We use a Box with fillMaxSize to handle the background effect and centering
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // --- Background Effect (Subtle Circles) ---
            // You can add a Canvas here to draw the concentric circles seen in the design

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
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

                // --- App Name ---
                Text(
                    text = "Cozy Doubling",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(48.dp))

                // --- Welcome Text ---
                Text(
                    text = "Welcome to Cozy Doubling",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "A quiet space to focus together, unhurried and calm.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(64.dp))

                // --- Sign in with Google Button (Custom Design) ---
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable { onSignInClick() },
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
                        // You can use a real Google G icon here from your drawables
                        Icon(
                            imageVector = Icons.Default.AccountCircle, // Placeholder for G logo
                            contentDescription = null,
                            tint = Color.Unspecified, // Keep original colors if using a multi-color SVG
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Sign in with Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- Reviewer Access Button ---
                TextButton(
                    onClick = { showReviewerDialog = true }
                ) {
                    Text(
                        text = "Reviewer Access",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // --- Footer Text ---
                val uriHandler = LocalUriHandler.current
                val annotatedString = buildAnnotatedString {
                    append("By signing in, you agree to our ")
                    
                    pushStringAnnotation(tag = "URL", annotation = BuildConfig.TERMS_OF_SERVICE_URL)
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                        append("terms of service")
                    }
                    pop()
                    
                    append(" and ")

                    pushStringAnnotation(tag = "URL", annotation = BuildConfig.PRIVACY_POLICY_URL)
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                        append("privacy policy")
                    }
                    pop()
                    append(".")
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

    if (showReviewerDialog) {
        ReviewerLoginDialog(
            onDismiss = { showReviewerDialog = false },
            onLogin = { email, pass -> 
                onReviewerLogin(email, pass)
                showReviewerDialog = false
            }
        )
    }

    if (error != null) {
        AlertDialog(
            onDismissRequest = onClearError,
            title = { Text("Login Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = onClearError) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun ReviewerLoginDialog(
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reviewer Access") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onLogin(email, password) },
                enabled = email.isNotBlank() && password.isNotBlank()
            ) {
                Text("Login")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
