package com.beadcraft.pattern

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beadcraft.pattern.ui.HomeScreen
import com.beadcraft.pattern.ui.ResultScreen
import com.beadcraft.pattern.ui.theme.BeadCraftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BeadCraftTheme {
                BeadCraftApp()
            }
        }
    }
}

@Composable
fun BeadCraftApp(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val pattern = state.pattern
        if (pattern != null) {
            ResultScreen(pattern = pattern, onBack = { viewModel.backToHome() })
        } else {
            HomeScreen(viewModel = viewModel)
        }
    }
}
