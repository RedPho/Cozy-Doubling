package com.grepho.cozydoubling.features.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grepho.cozydoubling.core.Supabase
import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithGoogle
import io.github.jan.supabase.compose.auth.composeAuth

// --- THE SCREEN ENTRY POINT ---
@Composable
fun LoginScreen(viewModel: LoginViewModel = viewModel()) {
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
        onSignInClick = { action.startFlow() }
    )
}

// --- THE UI COMPONENT ---
@Composable
fun LoginPage(
    onSignInClick: () -> Unit
) {
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Welcome to Cozy Doubling",
                    style = MaterialTheme.typography.headlineMedium
                )

                Button(onClick = onSignInClick) {
                    Text("Sign in with Google")
                }
            }
        }
    }
}