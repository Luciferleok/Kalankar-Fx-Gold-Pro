package com.example.livegoldai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.livegoldai.R
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalizationStrings
import com.example.livegoldai.theme.*
import com.example.livegoldai.ui.components.*
import com.example.livegoldai.ui.v8.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldHomeScreen(
    viewModel: GoldViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appColors = LocalAppColors.current
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }

    // Android 13+ Notification Permission Launcher
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { /* granted */ }

        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            currentApiKey = uiState.apiKey,
            currentLanguage = uiState.language,
            onSelectLanguage = { lang -> viewModel.selectLanguage(lang) },
            onSaveKey = { newKey -> viewModel.updateApiKey(newKey) },
            onOpenThemeSelector = { viewModel.openThemeSelector() },
            onDismiss = { showSettings = false }
        )
    }

    if (uiState.showLotCalculator && uiState.data != null) {
        LotCalculatorDialog(
            initialStopLossPips = uiState.selectedSlPips,
            currentGoldPrice = uiState.data!!.currentPrice,
            onDismiss = { viewModel.closeLotCalculator() }
        )
    }

    if (uiState.showPriceAlertDialog && uiState.data != null) {
        PriceAlertDialog(
            currentPrice = uiState.data!!.currentPrice,
            activeTargetPrice = uiState.priceAlertTarget,
            onSetAlert = { target -> viewModel.setPriceAlert(target) },
            onDismiss = { viewModel.closePriceAlertDialog() }
        )
    }


    if (uiState.showThemeSelectorDialog) {
        ThemeSelectorDialog(
            currentTheme = uiState.themeMode,
            isCompactEasyView = uiState.isCompactEasyView,
            onToggleEasyView = { viewModel.toggleCompactEasyView() },
            amoled = uiState.amoled,
            onToggleAmoled = { on -> viewModel.setAmoled(on) },
            onSelectTheme = { mode -> viewModel.selectTheme(mode) },
            onDismiss = { viewModel.closeThemeSelector() }
        )
    }

    if (uiState.showPredictionDialog && uiState.data != null) {
        KyaHogaPredictionDialog(
            analysis = uiState.data!!,
            onOpenLotCalculator = { slPips -> viewModel.openLotCalculator(slPips) },
            onOpenPredictionErrorAnalyzer = { viewModel.openPredictionErrorAnalyzer() },
            onDismiss = { viewModel.closePredictionDialog() }
        )
    }

    if (uiState.showPredictionErrorAnalyzerDialog && uiState.data != null) {
        PredictionErrorAnalyzerDialog(
            analysis = uiState.data!!,
            onDismiss = { viewModel.closePredictionErrorAnalyzer() },
            onRecalibrate = { viewModel.recalibratePredictionEngine() }
        )
    }

    if (uiState.selectedPillarForDeepDive != null && uiState.data != null) {
        IndicatorPillarDeepDiveSheet(
            group = uiState.selectedPillarForDeepDive!!,
            currentPrice = uiState.data!!.currentPrice,
            onDismiss = { viewModel.closePillarDeepDive() },
            onOpenLotCalculator = { slPips -> viewModel.openLotCalculator(slPips) },
            learning = uiState.data!!.learning
        )
    }

    // Back from any other tab returns to Home first; from Home the system closes the app as usual.
    androidx.activity.compose.BackHandler(enabled = uiState.bottomTab != 0) { viewModel.setBottomTab(0) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.background),
        containerColor = appColors.background,
        bottomBar = {
            V8BottomNav(
                selected = uiState.bottomTab,
                lang = uiState.language,
                healthStatus = uiState.data?.health?.overallStatus
            ) { viewModel.setBottomTab(it) }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .testTag("app_logo")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF080A0F))
                                .border(
                                    0.75.dp,
                                    Brush.sweepGradient(listOf(appColors.lightGold, appColors.primaryGold, Color(0xFFD4AF37), appColors.lightGold)),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.kalankar_logo_mark),
                                contentDescription = "Kalankar FX Gold Pro",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Brand lockup: two short lines, never ellipsised. The actions on the right are only
                        // two icons, so this always fits, also on a 360dp phone and with large fonts.
                        Column {
                            Text(
                                text = "KALANKAR",
                                fontWeight = FontWeight.SemiBold,
                                color = appColors.textPrimary,
                                letterSpacing = 3.sp,
                                fontSize = 17.sp,
                                lineHeight = 20.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "FX GOLD PRO",
                                fontWeight = FontWeight.Medium,
                                color = appColors.primaryGold,
                                letterSpacing = 2.5.sp,
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.openPriceAlertDialog() },
                        modifier = Modifier.testTag("top_price_alert_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (uiState.priceAlertTarget != null) {
                                    Badge(containerColor = if (uiState.isAlertTriggered) appColors.signalSell else appColors.primaryGold)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Price Alert",
                                tint = if (uiState.priceAlertTarget != null) appColors.primaryGold else appColors.textSecondary
                            )
                        }
                    }

                    // Theme and language live in Settings: the header keeps its width for the brand.
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = appColors.textSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appColors.background,
                    titleContentColor = appColors.textPrimary,
                    actionIconContentColor = appColors.textPrimary
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading && uiState.data == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = GoldPrimary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = LocalizationStrings.loadingLiveIndicators(uiState.language),
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary
                            )
                        }
                    }
                }

                uiState.data != null -> {
                    val analysis = uiState.data!!
                    val tabs = when (uiState.language) {
                        com.example.livegoldai.localization.AppLanguage.ENGLISH -> listOf(
                            "📊 COCKPIT",
                            "🤖 RULE COUNCIL",
                            "🚨 NEWS RADAR (AUTO)",
                            "🏦 SMART MONEY SMC",
                            "⚡ STRATEGY & TRICKS"
                        )
                        com.example.livegoldai.localization.AppLanguage.HINDI -> listOf(
                            "📊 कॉकपिट",
                            "🤖 नियम काउंसिल",
                            "🚨 न्यूज़ रडार (ऑटो)",
                            "🏦 स्मार्ट मनी SMC",
                            "⚡ रणनीति और ट्रिक्स"
                        )
                        com.example.livegoldai.localization.AppLanguage.MARATHI -> listOf(
                            "📊 कॉकपिट",
                            "🤖 नियम कौन्सिल",
                            "🚨 न्यूज रडार (ऑटो)",
                            "🏦 स्मार्ट मनी SMC",
                            "⚡ रणनीती आणि ट्रिक्स"
                        )
                    }

                    // Each tab keeps its own scroll position and sub-state; switching is a short crossfade, never a reload.
                    val tabStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
                    androidx.compose.animation.Crossfade(
                        targetState = uiState.bottomTab,
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 180),
                        label = "tab_switch"
                    ) { tab ->
                    tabStateHolder.SaveableStateProvider(tab) {
                    when (tab) {
                    0 -> V8CockpitScreen(
                        analysis = analysis,
                        lang = uiState.language,
                        selectedInterval = uiState.selectedInterval,
                        tick = uiState.countdownSeconds,
                        onIntervalChange = { viewModel.setInterval(it) },
                        onOpenHealth = { viewModel.setBottomTab(3) },
                        onOpenForecast = { viewModel.setBottomTab(1) },
                        onOpenPlaybook = { viewModel.openPredictionDialog() },
                        onOpenAi = { viewModel.setBottomTab(2) }
                    )
                    1 -> V8ForecastScreen(
                        analysis = analysis,
                        lang = uiState.language,
                        onOpenPlaybook = { viewModel.openPredictionDialog() },
                        onOpenLearning = { viewModel.setBottomTab(4) }
                    )
                    2 -> V8AiScreen(
                        analysis = analysis,
                        lang = uiState.language,
                        providers = uiState.aiProviders,
                        mode = uiState.aiMode,
                        debate = uiState.aiDebate,
                        freshnessSec = uiState.aiFreshnessSec,
                        tests = uiState.aiTests,
                        running = uiState.aiRunning,
                        tick = uiState.countdownSeconds,
                        onSave = { id, key, model, role, enabled -> viewModel.saveAiProvider(id, key, model, role, enabled) },
                        onRemoveKey = { viewModel.removeAiKey(it) },
                        onTest = { viewModel.testAiProvider(it) },
                        onMode = { viewModel.setAiMode(it) },
                        onDebate = { viewModel.setAiDebate(it) },
                        onFreshness = { viewModel.setAiFreshness(it) },
                        onRunNow = { viewModel.runAiCouncil() }
                    )
                    3 -> V8HealthScreen(
                        health = analysis.health,
                        lang = uiState.language,
                        pulse = analysis.pulse,
                        bgRecording = uiState.bgRecording,
                        onToggleBackground = { viewModel.setBackgroundRecording(it) }
                    )
                    4 -> LearningCenterContent(
                        analysis = analysis,
                        onRecalibrate = { viewModel.recalibratePredictionEngine() }
                    )
                    else -> {
                    // MORE: the full classic dashboard (all original cards, news, SMC, bots, tricks)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
                    ) {
                        // Alert Triggered Banner
                        if (uiState.isAlertTriggered) {
                            item(key = "alert_triggered_banner") {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = SignalSell.copy(alpha = 0.2f),
                                    border = CardDefaults.outlinedCardBorder().copy(
                                        brush = Brush.linearGradient(listOf(SignalSell, GoldPrimary))
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NotificationsActive,
                                                contentDescription = null,
                                                tint = SignalSell,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "PRICE ALERT TRIGGERED!",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Black,
                                                    color = SignalSell
                                                )
                                                Text(
                                                    text = "Target ($${String.format(Locale.US, "%.2f", uiState.priceAlertTarget ?: 0.0)}) hit! Live Gold: $${String.format(Locale.US, "%.2f", analysis.currentPrice)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextPrimary
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { viewModel.dismissAlertBanner() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Dismiss",
                                                tint = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 🚨 HIGH-IMPACT NEWS DEFENSE MODE BANNER (Always visible on all views with India Time & Notif Test)
                        val highImpactUpcoming = analysis.macroRadar?.upcomingEvents?.firstOrNull {
                            it.impact.equals("High", ignoreCase = true)
                        } ?: analysis.macroRadar?.upcomingEvents?.firstOrNull()

                        if (highImpactUpcoming != null || analysis.isNewsModeTriggered) {
                            item(key = "high_impact_news_defense_banner") {
                                HighImpactNewsDefenseBanner(
                                    event = highImpactUpcoming,
                                    currentPrice = analysis.currentPrice,
                                    isNewsModeActive = analysis.isNewsModeTriggered,
                                    onOpenNewsPlan = {
                                        viewModel.setDashboardViewMode(DashboardViewMode.PRO)
                                        viewModel.setTab(2) // Jump to News Radar Tab
                                    }
                                )
                            }
                        }

                        // --- 🌟 MODE SELECTOR: UNIFIED VS SIMPLE VS PRO ---
                        item(key = "mode_selector_bar") {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = ObsidianSurfaceCard,
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderHighlight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val currentMode = uiState.dashboardViewMode

                                    // 🎛️ Unified Signal Dashboard
                                    val isUnified = currentMode == DashboardViewMode.UNIFIED
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isUnified) GoldPrimary else Color.Transparent,
                                        border = if (isUnified) null else androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { viewModel.setDashboardViewMode(DashboardViewMode.UNIFIED) }
                                            .testTag("mode_selector_unified")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "🎛️", fontSize = 13.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = when (uiState.language) {
                                                        AppLanguage.ENGLISH -> "UNIFIED"
                                                        AppLanguage.HINDI -> "संयुक्त"
                                                        AppLanguage.MARATHI -> "संयुक्त"
                                                    },
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (isUnified) OnAccent else TextPrimary
                                                )
                                                Text(
                                                    text = when (uiState.language) {
                                                        AppLanguage.ENGLISH -> "Signal Gauge"
                                                        AppLanguage.HINDI -> "सिग्नल गेज"
                                                        AppLanguage.MARATHI -> "सिग्नल गेज"
                                                    },
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isUnified) OnAccent.copy(alpha = 0.8f) else TextMuted
                                                )
                                            }
                                        }
                                    }

                                    // 🌟 Simple Easy View
                                    val isEasy = currentMode == DashboardViewMode.SIMPLE
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isEasy) GoldPrimary else Color.Transparent,
                                        border = if (isEasy) null else androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { viewModel.setDashboardViewMode(DashboardViewMode.SIMPLE) }
                                            .testTag("mode_selector_easy")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "🌟", fontSize = 13.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = when (uiState.language) {
                                                        AppLanguage.ENGLISH -> "SIMPLE"
                                                        AppLanguage.HINDI -> "सरल दृश्य"
                                                        AppLanguage.MARATHI -> "सोपे दृश्य"
                                                    },
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (isEasy) OnAccent else TextPrimary
                                                )
                                                Text(
                                                    text = when (uiState.language) {
                                                        AppLanguage.ENGLISH -> "Clean & Sorted"
                                                        AppLanguage.HINDI -> "सुलझा हुआ"
                                                        AppLanguage.MARATHI -> "सुलभ स्पष्ट"
                                                    },
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isEasy) OnAccent.copy(alpha = 0.8f) else TextMuted
                                                )
                                            }
                                        }
                                    }

                                    // ⚡ Pro Cockpit
                                    val isPro = currentMode == DashboardViewMode.PRO
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isPro) GoldPrimary else Color.Transparent,
                                        border = if (isPro) null else androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { viewModel.setDashboardViewMode(DashboardViewMode.PRO) }
                                            .testTag("mode_selector_pro")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "⚡", fontSize = 13.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = when (uiState.language) {
                                                        AppLanguage.ENGLISH -> "PRO"
                                                        AppLanguage.HINDI -> "प्रो कॉकपिट"
                                                        AppLanguage.MARATHI -> "प्रो कॉकपिट"
                                                    },
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (isPro) OnAccent else TextPrimary
                                                )
                                                Text(
                                                    text = when (uiState.language) {
                                                        AppLanguage.ENGLISH -> "All Indicators"
                                                        AppLanguage.HINDI -> "सभी इंडिकेटर्स"
                                                        AppLanguage.MARATHI -> "सभी इंडिकेटर्स"
                                                    },
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isPro) OnAccent.copy(alpha = 0.8f) else TextMuted
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // --- CONTENT SWITCHING BASED ON MODE ---
                        when (uiState.dashboardViewMode) {
                            DashboardViewMode.UNIFIED -> {
                                // 🎛️ UNIFIED SIGNAL DASHBOARD VIEW (Speedometer Gauge + AI + Indicators + Quant Bot)
                                item(key = "unified_dashboard_content") {
                                    UnifiedSignalDashboardView(
                                        analysis = analysis,
                                        selectedInterval = uiState.selectedInterval,
                                        onIntervalSelected = { viewModel.setInterval(it) },
                                        onOpenCalculator = { slPips -> viewModel.openLotCalculator(slPips) },
                                        onOpenPredictionDialog = { viewModel.openPredictionDialog() },
                                        onOpenPredictionErrorAnalyzer = { viewModel.openPredictionErrorAnalyzer() },
                                        onPillarClick = { group -> viewModel.openPillarDeepDive(group) },
                                        onSwitchMode = { modeStr ->
                                            val m = when (modeStr) {
                                                "SIMPLE" -> DashboardViewMode.SIMPLE
                                                "PRO" -> DashboardViewMode.PRO
                                                else -> DashboardViewMode.UNIFIED
                                            }
                                            viewModel.setDashboardViewMode(m)
                                        },
                                        onRefreshClick = { viewModel.loadData(isInitial = false) }
                                    )
                                }
                            }
                            DashboardViewMode.SIMPLE -> {
                                // 🌟 100% CLEAN, SORTED, UNSHAKEABLE EASY VIEW
                                item(key = "easy_view_content") {
                                    SimpleEasyTradingView(
                                        analysis = analysis,
                                        selectedInterval = uiState.selectedInterval,
                                        onIntervalSelected = { viewModel.setInterval(it) },
                                        onOpenCalculator = { slPips -> viewModel.openLotCalculator(slPips) },
                                        onOpenPredictionDialog = { viewModel.openPredictionDialog() },
                                        onOpenPredictionErrorAnalyzer = { viewModel.openPredictionErrorAnalyzer() },
                                        onPillarClick = { group -> viewModel.openPillarDeepDive(group) },
                                        onSwitchToProMode = { viewModel.setDashboardViewMode(DashboardViewMode.PRO) },
                                        onRefreshClick = { viewModel.loadData(isInitial = false) }
                                    )
                                }
                            }
                            DashboardViewMode.PRO -> {
                                // ⚡ ADVANCED PRO COCKPIT (Price header, snapshot, 5 sorted tabs)
                                // 1. Live Price Card with timeframes & alert setter
                                item(key = "price_header") {
                                    PriceHeaderCard(
                                        analysis = analysis,
                                        selectedInterval = uiState.selectedInterval,
                                        countdownSeconds = uiState.countdownSeconds,
                                        isRefreshing = uiState.isRefreshing,
                                        onIntervalSelected = { viewModel.setInterval(it) },
                                        onRefreshClick = { viewModel.loadData(isInitial = false) },
                                        alertTargetPrice = uiState.priceAlertTarget,
                                        isAlertTriggered = uiState.isAlertTriggered,
                                        onAlertClick = { viewModel.openPriceAlertDialog() }
                                    )
                                }

                            // 2. Sleek Executive 1-Glance Snapshot Bar
                            item(key = "executive_summary_bar") {
                                ExecutiveSummaryBar(
                                    analysis = analysis,
                                    onTabSelect = { tabIndex -> viewModel.setTab(tabIndex) }
                                )
                            }

                            // 3. Category Navigation Tabs (Sorted, Intuitive, Aesthetic)
                            item(key = "category_tabs") {
                                ScrollableTabRow(
                                    selectedTabIndex = uiState.selectedTab.coerceIn(0, tabs.size - 1),
                                    edgePadding = 0.dp,
                                    containerColor = Color.Transparent,
                                    contentColor = GoldPrimary,
                                    indicator = {},
                                    divider = {}
                                ) {
                                    tabs.forEachIndexed { index, title ->
                                        val isSelected = uiState.selectedTab == index
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) {
                                                if (index == 2 && uiState.isNewsModeActive) Color(0xFFFF1744) else GoldPrimary
                                            } else ObsidianSurfaceElevated,
                                            border = if (isSelected) null else CardDefaults.outlinedCardBorder().copy(
                                                brush = Brush.linearGradient(
                                                    if (index == 2 && uiState.isNewsModeActive) listOf(Color(0xFFFF1744), Color(0xFFFF9100))
                                                    else listOf(ObsidianBorderHighlight, ObsidianBorder)
                                                )
                                            ),
                                            modifier = Modifier
                                                .padding(end = 8.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { viewModel.setTab(index) }
                                                .testTag("tab_$title")
                                        ) {
                                            Text(
                                                text = title,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                                color = if (isSelected) OnAccent else TextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            // --- TAB 0: 📊 COCKPIT & LIVE TRADING ---
                            if (uiState.selectedTab == 0) {
                                // 🤖 Kalankar Quant Bot v6.0 High-Accuracy Signal & Execution Order
                                analysis.quantBotSignal?.let { botSignal ->
                                    item(key = "cockpit_quant_bot_signal") {
                                        QuantBotCard(
                                            botSignal = botSignal,
                                            onOpenCalculator = { slPips -> viewModel.openLotCalculator(slPips) }
                                        )
                                    }
                                }

                                // Dual Columns: AI Prediction & Oscillators
                                item(key = "cockpit_dual_prediction_indicators") {
                                    DualPredictionAndIndicatorsSection(
                                        analysis = analysis,
                                        onOpenCalculator = { slPips -> viewModel.openLotCalculator(slPips) },
                                        onOpenPredictionErrorAnalyzer = { viewModel.openPredictionErrorAnalyzer() },
                                        onPillarClick = { group -> viewModel.openPillarDeepDive(group) }
                                    )
                                }

                                // Professional Candlestick Chart (With Fullscreen Studio)
                                item(key = "cockpit_pro_chart") {
                                    ProCandleChart(
                                        candles = analysis.chartCandles.ifEmpty { analysis.recentCandles },
                                        groups = analysis.groups,
                                        volumeSource = analysis.feed?.volumeSource ?: "",
                                        buyerSellerRatio = analysis.buyerSellerRatio,
                                        tradeSetup = analysis.tradeSetup,
                                        pivotLevels = analysis.pivotLevels,
                                        currentPrice = analysis.currentPrice
                                    )
                                }

                                // Real-Time Buyers vs Sellers Order Flow Depth
                                analysis.buyerSellerRatio?.let { bs ->
                                    item(key = "cockpit_buyer_seller_card") {
                                        BuyerSellerDepthCard(
                                            sentiment = bs,
                                            currentPrice = analysis.currentPrice
                                        )
                                    }
                                }

                                // Live Global Market Sessions Clock
                                item(key = "cockpit_market_sessions") {
                                    MarketSessionsCard(sessions = analysis.marketSessions)
                                }
                            }

                            // --- TAB 1: 🤖 MULTI-AI CONSENSUS & AUDIT ---
                            if (uiState.selectedTab == 1) {
                                // 🤖 Kalankar Quant Bot v6.0 Live Algorithmic Execution Signal
                                analysis.quantBotSignal?.let { botSignal ->
                                    item(key = "tab1_quant_bot_signal") {
                                        QuantBotCard(
                                            botSignal = botSignal,
                                            onOpenCalculator = { slPips -> viewModel.openLotCalculator(slPips) }
                                        )
                                    }
                                }

                                // Multi-AI Council (Gemini + ChatGPT + Claude + DeepSeek + Perplexity)
                                analysis.multiAiConsensus?.let { consensus ->
                                    item(key = "multi_ai_council_tab_page") {
                                        MultiAiCouncilCard(
                                            consensus = consensus,
                                            onRefreshAiCouncil = { viewModel.loadData(isInitial = false) }
                                        )
                                    }
                                }

                                // AI Timeframe Prediction Accuracy Audit
                                analysis.timeframeAudit?.let { audit ->
                                    item(key = "multi_ai_accuracy_audit_page") {
                                        PredictionAccuracyAuditCard(
                                            audit = audit,
                                            selectedInterval = uiState.selectedInterval,
                                            onOpenPredictionErrorAnalyzer = { viewModel.openPredictionErrorAnalyzer() }
                                        )
                                    }
                                }

                                // AI Failed Prediction Autopsy & Self-Correction
                                analysis.failedPredictionAutopsy?.let { autopsy ->
                                    item(key = "multi_ai_failed_autopsy_tab_page") {
                                        AiFailedPredictionAutopsyCard(autopsy = autopsy)
                                    }
                                }
                            }

                            // --- TAB 2: 🚨 NEWS RADAR & 1-HR ALERT SYSTEM ---
                            if (uiState.selectedTab == 2) {
                                // 1-Hour Prior Notification Radar Card (AI Pre-Analysis & Real Notification Trigger)
                                item(key = "news_tab_pre_news_intelligence_card") {
                                    PreNewsIntelligenceCard(
                                        currentPrice = analysis.currentPrice,
                                        upcomingEvents = analysis.macroRadar?.upcomingEvents ?: emptyList(),
                                        onOpenLotCalculator = { slPips -> viewModel.openLotCalculator(slPips) }
                                    )
                                }

                                // High-Impact News Volatility Trading Mode & Dynamic Color Shift Card
                                analysis.newsTradingPlan?.let { newsPlan ->
                                    item(key = "news_tab_trading_mode_card") {
                                        NewsTradingModeCard(
                                            newsPlan = newsPlan,
                                            isNewsModeActive = uiState.isNewsModeActive,
                                            onToggleNewsMode = { active -> viewModel.setNewsModeActive(active) }
                                        )
                                    }
                                }

                                // Macro Sentiment Radar (DXY, 10Y Yields, Calendar Feed)
                                analysis.macroRadar?.let { radar ->
                                    item(key = "news_tab_macro_radar_page") {
                                        MacroNewsRadarCard(radar = radar)
                                    }
                                }
                            }

                            // --- TAB 3: 🏦 SMART MONEY SMC & PIVOT LADDER ---
                            if (uiState.selectedTab == 3) {
                                analysis.smartMoney?.let { smc ->
                                    item(key = "smart_money_smc_page") {
                                        SmartMoneySmcCard(
                                            smc = smc,
                                            currentPrice = analysis.currentPrice
                                        )
                                    }
                                }

                                item(key = "pivot_ladder_tab") {
                                    PivotLadderCard(
                                        currentPrice = analysis.currentPrice,
                                        pivotLevels = analysis.pivotLevels
                                    )
                                }
                            }

                            // --- TAB 4: ⚡ INSTITUTIONAL PLAYBOOK & TRICKS ---
                            if (uiState.selectedTab == 4) {
                                // 🤖 6-Bot Arsenal & Consensus
                                analysis.multiBotEnsemble?.let { ensemble ->
                                    item(key = "tab4_multi_bot_arsenal") {
                                        MultiBotArsenalCard(
                                            ensemble = ensemble,
                                            onOpenCalculator = { slPips -> viewModel.openLotCalculator(slPips) }
                                        )
                                    }
                                }

                                // ⚡ Autonomous Accuracy Verification & Production Improvement Engine
                                analysis.productionImprovement?.let { prodEngine ->
                                    item(key = "tab4_production_improvement") {
                                        ProductionImprovementAuditCard(
                                            engine = prodEngine,
                                            onRunAccuracyCheck = { viewModel.loadData(isInitial = false) }
                                        )
                                    }
                                }

                                item(key = "trade_setup_tricks") {
                                    TradeSetupCard(
                                        setup = analysis.tradeSetup,
                                        onOpenCalculator = { slPips -> viewModel.openLotCalculator(slPips) }
                                    )
                                }

                                if (analysis.candleInsight != null && analysis.mtfMatrix != null) {
                                    item(key = "candle_mtf_page") {
                                        CandleMtfOracleCard(
                                            candleInsight = analysis.candleInsight!!,
                                            mtfMatrix = analysis.mtfMatrix!!,
                                            tradingTricks = analysis.tradingTricks
                                        )
                                    }
                                }
                            }
                        }
                    }

                        // Attribution & Disclaimer Footer
                        item(key = "footer") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // risk note first, so the brand footer is the last thing on the page
                                Text(
                                    text = LocalizationStrings.disclaimer(uiState.language),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp),
                                    color = TextMuted,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "KALANKAR FX GOLD PRO",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 2.5.sp,
                                    color = GoldPrimary,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = LocalizationStrings.dedicatedTo(uiState.language),
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                    }
                    }
                    }
                    }
                }

                uiState.errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = LocalizationStrings.errorLoading(uiState.language),
                                style = MaterialTheme.typography.titleMedium,
                                color = SignalSell
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.errorMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadData(isInitial = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                            ) {
                                Text(text = LocalizationStrings.tryAgain(uiState.language), color = ObsidianBackground)
                            }
                        }
                    }
                }
            }
        }
    }
}

