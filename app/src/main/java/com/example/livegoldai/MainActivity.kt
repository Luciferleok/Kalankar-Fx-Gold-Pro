package com.example.livegoldai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.livegoldai.localization.LocalAppLanguage
import com.example.livegoldai.theme.LiveGoldAITheme
import com.example.livegoldai.ui.GoldHomeScreen
import com.example.livegoldai.ui.GoldViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: GoldViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val navTab = intent?.getIntExtra("EXTRA_NAV_TAB", -1) ?: -1
        if (navTab >= 0) {
            viewModel.setTab(navTab)
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            CompositionLocalProvider(LocalAppLanguage provides uiState.language) {
                LiveGoldAITheme(
                    themeMode = uiState.themeMode,
                    isNewsModeActive = uiState.isNewsModeActive
                ) {
                    GoldHomeScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val navTab = intent.getIntExtra("EXTRA_NAV_TAB", -1)
        if (navTab >= 0) {
            viewModel.setTab(navTab)
        }
    }
}
