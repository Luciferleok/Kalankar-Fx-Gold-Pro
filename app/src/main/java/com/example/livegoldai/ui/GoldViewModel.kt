package com.example.livegoldai.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.livegoldai.R
import com.example.livegoldai.data.GoldApiService
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.GroupAnalysis
import com.example.livegoldai.theme.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class MainScreenMode(
    val title: String,
    val hindiTitle: String,
    val badge: String,
    val icon: String
) {
    ALL(
        title = "ALL COCKPIT",
        hindiTitle = "Sabhi Feature",
        badge = "PRO",
        icon = "🎯"
    ),
    LIVE_MARKET(
        title = "LIVE STATUS",
        hindiTitle = "Abhi Kya Chal Raha Hai",
        badge = "REALTIME",
        icon = "🔴"
    ),
    PREDICTION(
        title = "AI PREDICTION",
        hindiTitle = "Agla Kya Hoga & Possibility",
        badge = "FORECAST",
        icon = "🔮"
    )
}

data class GoldUiState(
    val language: AppLanguage = AppLanguage.HINDI,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val data: GoldAnalysisResult? = null,
    val selectedInterval: String = "4h",
    val selectedTab: Int = 0, // 0=All, 1=Trend, 2=Momentum, 3=Volatility, 4=S/R, 5=Candlestick
    val countdownSeconds: Int = 60,
    val errorMessage: String? = null,
    val apiKey: String = "8e1493529b8e42d9b0a9e557c3451db0",
    val priceAlertTarget: Double? = null,
    val isAlertTriggered: Boolean = false,
    val showLotCalculator: Boolean = false,
    val selectedSlPips: Double = 90.0,
    val showPriceAlertDialog: Boolean = false,
    val selectedLogoRes: Int = R.drawable.ic_kalankar_royal_emblem,
    val showLogoSelectorDialog: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DUBAI_ROYALE,
    val showThemeSelectorDialog: Boolean = false,
    val isCompactEasyView: Boolean = false,
    val mainScreenMode: MainScreenMode = MainScreenMode.ALL,
    val showPredictionDialog: Boolean = false,
    val showPredictionErrorAnalyzerDialog: Boolean = false,
    val showSpotInspectorDialog: Boolean = false,
    val selectedPillarForDeepDive: GroupAnalysis? = null,
    val isManualNewsMode: Boolean = false
) {
    val isNewsModeActive: Boolean
        get() = isManualNewsMode ||
                data?.isNewsModeTriggered == true ||
                data?.newsTradingPlan?.isNewsActive == true ||
                (data?.newsMode != null && data.newsMode.minutes <= 30) ||
                themeMode == ThemeMode.NEWS_ALERT
}

class GoldViewModel @JvmOverloads constructor(
    application: Application,
    private val apiService: GoldApiService = GoldApiService()
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("kalankar_gold_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        GoldUiState(
            language = AppLanguage.fromCode(prefs.getString("selected_app_language", "hi"))
        )
    )
    val uiState: StateFlow<GoldUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        loadData(isInitial = true)
        startAutoRefreshLoop()
    }

    fun selectLanguage(language: AppLanguage) {
        prefs.edit().putString("selected_app_language", language.code).apply()
        _uiState.update { it.copy(language = language) }
    }

    fun loadData(isInitial: Boolean = false) {
        viewModelScope.launch {
            if (isInitial) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            } else {
                _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            }

            val currentInterval = _uiState.value.selectedInterval
            val result = apiService.fetchAnalysis(interval = currentInterval)

            result.onSuccess { analysis ->
                val target = _uiState.value.priceAlertTarget
                val triggered = if (target != null) {
                    val p = analysis.currentPrice
                    kotlin.math.abs(p - target) <= 2.5
                } else false

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        data = analysis,
                        countdownSeconds = 60,
                        errorMessage = null,
                        isAlertTriggered = triggered || it.isAlertTriggered
                    )
                }

                // Check 1-Hour Pre-News upcoming events and trigger notification if due
                analysis.macroRadar?.upcomingEvents?.let { events ->
                    com.example.livegoldai.notification.PreNewsAlertNotificationManager.checkAndTriggerUpcomingAlert(
                        context = getApplication(),
                        events = events,
                        currentPrice = analysis.currentPrice,
                        lang = _uiState.value.language
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = err.localizedMessage ?: "Failed to update market data"
                    )
                }
            }
        }
    }

    fun setInterval(interval: String) {
        if (_uiState.value.selectedInterval == interval) return
        _uiState.update { it.copy(selectedInterval = interval, isLoading = true) }
        loadData(isInitial = true)
    }

    fun setTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun updateApiKey(newKey: String) {
        apiService.setApiKey(newKey)
        _uiState.update { it.copy(apiKey = newKey) }
        loadData(isInitial = false)
    }

    fun openLotCalculator(slPips: Double) {
        _uiState.update { it.copy(showLotCalculator = true, selectedSlPips = slPips) }
    }

    fun closeLotCalculator() {
        _uiState.update { it.copy(showLotCalculator = false) }
    }

    fun openPriceAlertDialog() {
        _uiState.update { it.copy(showPriceAlertDialog = true) }
    }

    fun closePriceAlertDialog() {
        _uiState.update { it.copy(showPriceAlertDialog = false) }
    }

    fun setPriceAlert(target: Double?) {
        _uiState.update { it.copy(priceAlertTarget = target, isAlertTriggered = false) }
    }

    fun dismissAlertBanner() {
        _uiState.update { it.copy(isAlertTriggered = false) }
    }

    fun openLogoSelector() {
        _uiState.update { it.copy(showLogoSelectorDialog = true) }
    }

    fun closeLogoSelector() {
        _uiState.update { it.copy(showLogoSelectorDialog = false) }
    }

    fun selectLogo(logoRes: Int) {
        _uiState.update { it.copy(selectedLogoRes = logoRes, showLogoSelectorDialog = false) }
    }

    fun openThemeSelector() {
        _uiState.update { it.copy(showThemeSelectorDialog = true) }
    }

    fun closeThemeSelector() {
        _uiState.update { it.copy(showThemeSelectorDialog = false) }
    }

    fun selectTheme(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode, showThemeSelectorDialog = false) }
    }

    fun toggleCompactEasyView() {
        _uiState.update { it.copy(isCompactEasyView = !it.isCompactEasyView) }
    }

    fun setMainScreenMode(mode: MainScreenMode) {
        _uiState.update { it.copy(mainScreenMode = mode) }
    }

    fun openPredictionDialog() {
        _uiState.update { it.copy(showPredictionDialog = true) }
    }

    fun closePredictionDialog() {
        _uiState.update { it.copy(showPredictionDialog = false) }
    }

    fun toggleNewsMode() {
        val newActive = !_uiState.value.isManualNewsMode
        setNewsModeActive(newActive)
    }

    fun openPredictionErrorAnalyzer() {
        _uiState.update { it.copy(showPredictionErrorAnalyzerDialog = true) }
    }

    fun closePredictionErrorAnalyzer() {
        _uiState.update { it.copy(showPredictionErrorAnalyzerDialog = false) }
    }

    fun recalibratePredictionEngine() {
        loadData(isInitial = false)
    }

    fun openSpotInspector() {
        _uiState.update { it.copy(showSpotInspectorDialog = true) }
    }

    fun closeSpotInspector() {
        _uiState.update { it.copy(showSpotInspectorDialog = false) }
    }

    fun openPillarDeepDive(group: GroupAnalysis) {
        _uiState.update { it.copy(selectedPillarForDeepDive = group) }
    }

    fun closePillarDeepDive() {
        _uiState.update { it.copy(selectedPillarForDeepDive = null) }
    }

    fun setNewsModeActive(active: Boolean) {
        _uiState.update { current ->
            val updatedData = current.data?.let { d ->
                val updatedPlan = d.newsTradingPlan?.copy(
                    isNewsActive = active,
                    releaseCountdownFormatted = if (active) "🚨 LIVE NEWS SPIKE WINDOW ACTIVE (Next 45 Mins)" else "⏰ Next Release: Today 18:30 UTC (US CPI)",
                    phase = if (active) com.example.livegoldai.model.NewsPhase.LIVE_NEWS_SPIKE else com.example.livegoldai.model.NewsPhase.PRE_NEWS_COIL
                )
                d.copy(
                    isNewsModeTriggered = active,
                    newsTradingPlan = updatedPlan
                )
            }
            current.copy(
                isManualNewsMode = active,
                data = updatedData
            )
        }
    }

    private fun startAutoRefreshLoop() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val currentSec = _uiState.value.countdownSeconds
                if (currentSec > 1) {
                    _uiState.update { it.copy(countdownSeconds = currentSec - 1) }
                } else {
                    _uiState.update { it.copy(countdownSeconds = 60) }
                    loadData(isInitial = false)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
    }
}
