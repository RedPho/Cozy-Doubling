package com.grepho.cozydoubling.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HomePage(
    topBar: @Composable () -> Unit // Accept the UI as a parameter
) {
    Scaffold (
        topBar = topBar
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            // Your home page content goes here!
        }
    }
}