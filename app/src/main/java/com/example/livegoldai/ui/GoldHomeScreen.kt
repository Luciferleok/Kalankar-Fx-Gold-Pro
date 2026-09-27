package com.example.livegoldai.ui

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.livegoldai.R
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalizationStrings
import com.example.livegoldai.theme.*
import com.example.livegoldai.ui.components.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldHomeScreen(
    viewModel: GoldViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appColors = LocalAppColors.current
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsDialog(
            currentApiKey = uiState.apiKey,
            currentLanguage = uiState.language,
            onSelectLanguage = { lang -> viewModel.selectLanguage(lang) },
            onSaveKey = { newKey -> viewModel.updateApiKey(newKey) },
            onOpenLogoGallery = { viewModel.openLogoSelector() },
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

    if (uiState.showLogoSelectorDialog) {
        LogoSelectorDialog(
            currentSelectedLogo = uiState.selectedLogoRes,
            onSelectLogo = { logoRes -> viewModel.selectLogo(logoRes) },
            onDismiss = { viewModel.closeLogoSelector() }
        )
    }

    if (uiState.showThemeSelectorDialog) {
        ThemeSelectorDialog(
            currentTheme = uiState.themeMode,
            isCompactEasyView = uiState.isCompactEasyView,
            onToggleEasyView = { viewModel.toggleCompactEasyView() },
            onSelectTheme = { mode -> viewModel.selectTheme(mode) },
            onDismiss = { viewModel.closeThemeSelector() }
        )
    }

    if (uiState.showPredictionDialog && uiState.data != null) {
        KyaHogaPredictionDialog(
            analysis = uiState.data!!,
            onOpenLotCalculator = { slPips -> viewModel.openLotCalculator(slPips) },
            onDismiss = { viewModel.closePredictionDialog() }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.background),
        containerColor = appColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { viewModel.openLogoSelector() }
                            .testTag("app_logo_clickable")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF080A0F))
                                .border(
                                    1.8.dp,
                                    Brush.sweepGradient(listOf(appColors.lightGold, appColors.primaryGold, Color(0xFFD4AF37), appColors.lightGold)),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = uiState.selectedLogoRes),
                                contentDescription = "Kalankar FX Gold Royal Logo - Tap to customize emblem",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "KALANKAR FX GOLD PRO",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = appColors.textPrimary,
                                    letterSpacing = 0.5.sp,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(appColors.signalBuy)
                                )
                            }
                            Text(
                                text = when (uiState.language) {
                                    com.example.livegoldai.localization.AppLanguage.ENGLISH -> "VIP BULLION TERMINAL • By Rudvay Ujjwal Kalankar"
                                    com.example.livegoldai.localization.AppLanguage.HINDI -> "VIP बुलियन टर्मिनल • रुद्वय उज्ज्वल कलणकर"
                                    com.example.livegoldai.localization.AppLanguage.MARATHI -> "VIP बुलियन टर्मिनल • रुद्वय उज्ज्वल काळणकर"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp),
                                color = appColors.primaryGold,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val slPips = uiState.data?.tradeSetup?.stopLossPips ?: 90.0
                            viewModel.openLotCalculator(slPips)
                        },
                        modifier = Modifier.testTag("top_lot_calc_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = "Lot Calculator",
                            tint = appColors.lightGold
                        )
                    }

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

                    IconButton(
                        onClick = { viewModel.openPredictionDialog() },
                        modifier = Modifier.testTag("top_prediction_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Forecast",
                            tint = appColors.lightGold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (uiState.isNewsModeActive) Color(0xFFFF1744).copy(alpha = 0.25f) else appColors.primaryGold.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (uiState.isNewsModeActive) Color(0xFFFF1744) else appColors.primaryGold.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewModel.openThemeSelector() }
                            .testTag("top_theme_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = if (uiState.isNewsModeActive) "🚨" else uiState.themeMode.icon, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (uiState.isNewsModeActive) "NEWS ACTIVE" else when (uiState.themeMode) {
                                    ThemeMode.DUBAI_ROYALE -> "DUBAI 24K"
                                    ThemeMode.ROYAL_OBSIDIAN -> "24K GOLD"
                                    ThemeMode.MONACO_ROSE -> "ROSE GOLD"
                                    ThemeMode.CYBER_NEON -> "CYBER"
                                    ThemeMode.SWISS_BANK -> "SWISS"
                                    ThemeMode.EMERALD_ALPHA -> "EMERALD"
                                    ThemeMode.NEWS_ALERT -> "NEWS ALERT"
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = if (uiState.isNewsModeActive) Color(0xFFFF264D) else appColors.primaryGold
                            )
                        }
                    }

                    // Language Quick Pill (English 🇬🇧, Hindi 🇮🇳, Marathi 🚩)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = appColors.primaryGold.copy(alpha = 0.18f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, appColors.lightGold.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showSettings = true }
                            .testTag("top_language_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = uiState.language.flag, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = uiState.language.nativeName,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = appColors.primaryGold
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.loadData(isInitial = false) },
                        modifier = Modifier.testTag("top_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Data",
                            tint = appColors.textPrimary
                        )
                    }

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
                            "🤖 MULTI-AI COUNCIL",
                            "🚨 NEWS RADAR (AUTO)",
                            "🏦 SMART MONEY SMC",
                            "⚡ STRATEGY & TRICKS"
                        )
                        com.example.livegoldai.localization.AppLanguage.HINDI -> listOf(
                            "📊 कॉकपिट",
                            "🤖 मल्टी-AI काउंसिल",
                            "🚨 न्यूज़ रडार (ऑटो)",
                            "🏦 स्मार्ट मनी SMC",
                            "⚡ रणनीति और ट्रिक्स"
                        )
                        com.example.livegoldai.localization.AppLanguage.MARATHI -> listOf(
                            "📊 कॉकपिट",
                            "🤖 मल्टी-AI कौन्सिल",
                            "🚨 न्यूज रडार (ऑटो)",
                            "🏦 स्मार्ट मनी SMC",
                            "⚡ रणनीती आणि ट्रिक्स"
                        )
                    }

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
                                            color = if (isSelected) Color.Black else TextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // --- TAB 0: 📊 COCKPIT & LIVE TRADING ---
                        if (uiState.selectedTab == 0) {
                            // Dual Columns: AI Prediction & Oscillators
                            item(key = "cockpit_dual_prediction_indicators") {
                                DualPredictionAndIndicatorsSection(
                                    analysis = analysis,
                                    onOpenCalculator = { slPips -> viewModel.openLotCalculator(slPips) }
                                )
                            }

                            // Professional Candlestick Chart
                            item(key = "cockpit_pro_chart") {
                                ProCandleChart(
                                    candles = analysis.recentCandles,
                                    buyerSellerRatio = analysis.buyerSellerRatio
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
                                        selectedInterval = uiState.selectedInterval
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

                        // Attribution & Disclaimer Footer
                        item(key = "footer") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "KALANKAR FX GOLD PRO",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldLight
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = LocalizationStrings.dedicatedTo(uiState.language),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = LocalizationStrings.disclaimer(uiState.language),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                    color = TextMuted
                                )
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

