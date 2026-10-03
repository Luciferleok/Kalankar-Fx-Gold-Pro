package com.example.livegoldai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.livegoldai.localization.LocalAppLanguage
import com.example.livegoldai.theme.LiveGoldAITheme
import com.example.livegoldai.ui.GoldHomeScreen
import com.example.livegoldai.ui.GoldViewModel
import com.example.livegoldai.ui.components.LuxurySplashScreen

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
            // the brand reveal plays once per app start: not again on rotation or when coming back from the background
            var isSplashVisible by remember { mutableStateOf(!viewModel.launchDone) }

            CompositionLocalProvider(LocalAppLanguage provides uiState.language) {
                LiveGoldAITheme(
                    themeMode = uiState.themeMode,
                    isNewsModeActive = uiState.isNewsModeActive,
                    amoled = uiState.amoled
                ) {
                    Crossfade(
                        targetState = isSplashVisible,
                        animationSpec = tween(durationMillis = 320),
                        label = "splash_screen_crossfade"
                    ) { splashVisible ->
                        if (splashVisible) {
                            LuxurySplashScreen(
                                ready = uiState.data != null,
                                offline = uiState.data == null && uiState.errorMessage != null,
                                onSplashFinished = { viewModel.launchDone = true; isSplashVisible = false }
                            )
                        } else {
                            GoldHomeScreen(viewModel = viewModel)
                        }
                    }
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
