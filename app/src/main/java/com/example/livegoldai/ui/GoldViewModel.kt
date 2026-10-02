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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import com.example.livegoldai.data.FileLedgerStore
import com.example.livegoldai.data.LearningCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.livegoldai.data.HealthMonitor
import com.example.livegoldai.data.ai.AIOrchestrator
import com.example.livegoldai.data.ai.AiPerformanceTracker
import com.example.livegoldai.data.ai.AiProviderConfig
import com.example.livegoldai.data.ai.AiProviderId
import com.example.livegoldai.data.ai.AiRole
import com.example.livegoldai.data.ai.OkHttpAiTransport
import com.example.livegoldai.model.AiProviderUi
import com.example.livegoldai.model.AiTestUi

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

enum class DashboardViewMode(
    val titleEnglish: String,
    val titleHindi: String,
    val titleMarathi: String,
    val iconEmoji: String
) {
    UNIFIED("UNIFIED SIGNAL", "संयुक्त सिग्नल", "संयुक्त सिग्नल", "🎛️"),
    SIMPLE("SIMPLE VIEW", "सरल दृश्य", "सोपे दृश्य", "🌟"),
    PRO("PRO COCKPIT", "प्रो कॉकपिट", "प्रो कॉकपिट", "⚡")
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
    val dashboardViewMode: DashboardViewMode = DashboardViewMode.UNIFIED,
    val mainScreenMode: MainScreenMode = MainScreenMode.ALL,
    val showPredictionDialog: Boolean = false,
    val showPredictionErrorAnalyzerDialog: Boolean = false,
    val showSpotInspectorDialog: Boolean = false,
    val selectedPillarForDeepDive: GroupAnalysis? = null,
    val isManualNewsMode: Boolean = false,
    val bottomTab: Int = 0, // 0 Cockpit, 1 Forecast, 2 AI, 3 Health, 4 Learning, 5 More
    // ---- external AI council (keys live encrypted in AiKeyVault, never in UI state)
    val aiProviders: List<com.example.livegoldai.model.AiProviderUi> = emptyList(),
    val aiMode: String = "AUTO",
    val aiDebate: Boolean = false,
    val aiFreshnessSec: Int = 0,
    val aiTests: Map<String, com.example.livegoldai.model.AiTestUi> = emptyMap(),
    val aiRunning: Boolean = false
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

    private val healthMonitor = com.example.livegoldai.data.HealthMonitor()

    // Real Prediction Ledger (append-only file in app storage). Loaded lazily on a background thread.
    // One store instance is shared by the learning engine and the AI provider ledger (same file, same lock).
    private val ledgerStore: FileLedgerStore by lazy {
        FileLedgerStore(java.io.File(getApplication<Application>().filesDir, "prediction_ledger.jsonl"))
    }
    private val learning: LearningCoordinator by lazy {
        LearningCoordinator(ledgerStore) { start, end -> apiService.fetchPricePath(start, end) }
    }

    // ---- External AI council: real API calls only to providers the user connected with their own key.
    private val aiVault by lazy { AiKeyVault(prefs) }
    private val aiOrchestrator: AIOrchestrator by lazy {   // first use reads the ledger file: background thread only
        AIOrchestrator(OkHttpAiTransport(), AiPerformanceTracker(ledgerStore))
    }
    @Volatile private var aiConfigCache: List<AiProviderConfig>? = null

    // ---- V10 Market Brain: real cross-market series + feature store (same ledger file)
    private val crossMarket = com.example.livegoldai.data.brain.CrossMarketService()
    private val featureStore by lazy { com.example.livegoldai.data.brain.FeatureStore(ledgerStore) }   // background thread only

    /** Background thread only. Returns null for offline demo data: the brain is never built on fake prices. */
    private fun buildBrain(a: GoldAnalysisResult, council: com.example.livegoldai.model.AiCouncilReport?): com.example.livegoldai.model.BrainReport? {
        if (a.isSimulatedFallback) return null
        val now = System.currentTimeMillis()
        val xm = com.example.livegoldai.data.brain.CrossMarketEngine.build(crossMarket.series(), now)
        val anomaly = com.example.livegoldai.data.brain.AnomalyEngine.build(a, xm, now)
        val feats = com.example.livegoldai.data.brain.FeatureCatalog.compute(a, xm, anomaly, now)
        val st = learning.currentState()
        // store the features once, for the prediction that was recorded on this refresh
        st.records.lastOrNull { it.interval == a.interval && now - it.createdAt < 120_000L }
            ?.let { r -> featureStore.recordIfAbsent(r.id, r.createdAt, r.interval, feats) }
        return com.example.livegoldai.data.brain.MarketBrain.build(a, xm, feats, featureStore, st, council, now)
    }
    private var aiJob: Job? = null

    init {
        val savedModeStr = prefs.getString("selected_dashboard_mode", DashboardViewMode.UNIFIED.name)
        val initialMode = try {
            DashboardViewMode.valueOf(savedModeStr ?: DashboardViewMode.UNIFIED.name)
        } catch (_: Exception) {
            DashboardViewMode.UNIFIED
        }
        val savedEasyView = prefs.getBoolean("is_compact_easy_view", initialMode == DashboardViewMode.SIMPLE)
        _uiState.update { 
            it.copy(
                dashboardViewMode = initialMode,
                isCompactEasyView = (initialMode == DashboardViewMode.SIMPLE) || savedEasyView,
                aiMode = prefs.getString("ai_mode", "AUTO") ?: "AUTO",
                aiDebate = prefs.getBoolean("ai_debate", false),
                aiFreshnessSec = prefs.getInt("ai_freshness_sec", 0)
            ) 
        }
        refreshAiProviders()
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

            result.onSuccess { rawAnalysis ->
                // Record / verify predictions and replace every accuracy number with real ledger values.
                val analysis = try {
                    withContext(Dispatchers.IO) {
                        val mtfCandles = try { apiService.fetchMtfCandles() } catch (_: Exception) { emptyMap() }
                        val learned = learning.process(rawAnalysis, currentInterval, mtfCandles)
                        val health = try { healthMonitor.build(learned, learning.stats(), mtfCandles.size) } catch (_: Exception) { null }
                        val insights = try { com.example.livegoldai.data.CockpitInsightsBuilder.build(learned) } catch (_: Exception) { null }
                        // keep showing the last AI council for this timeframe until the next run finishes
                        val council = try { if (aiConfigs().any { it.isConfigured && it.enabled }) aiOrchestrator.latest(currentInterval) else null } catch (_: Exception) { null }
                        val full = learned.copy(health = HealthMonitor.withAi(health, council), insights = insights, aiCouncil = council)
                        val pulse = try { com.example.livegoldai.data.LedgerPulseBuilder.build(full, learning.currentState(), learning.stats()) } catch (_: Exception) { null }
                        full.copy(brain = try { buildBrain(full, council) } catch (_: Exception) { null }, pulse = pulse)
                    }
                } catch (_: Exception) {
                    rawAnalysis
                }
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

                runAiCouncil()

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

    fun setBottomTab(index: Int) {
        _uiState.update { it.copy(bottomTab = index) }
    }

    // ------------------------------------------------------------------ external AI council

    /** Background thread only (decrypts keys). */
    private fun aiConfigs(): List<AiProviderConfig> =
        aiConfigCache ?: AiProviderId.values().map { aiVault.loadConfig(it) }.also { aiConfigCache = it }

    private fun refreshAiProviders() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                try {
                    aiConfigs().map { c ->
                        AiProviderUi(
                            id = c.id.name, name = c.id.display, hasKey = c.isConfigured,
                            maskedKey = AiKeyVault.mask(c.apiKey), model = c.model, defaultModel = c.id.defaultModel,
                            role = c.role.name, enabled = c.enabled, keyUrl = c.id.keyUrl
                        )
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }
            _uiState.update { it.copy(aiProviders = list) }
        }
    }

    /** Asks the connected providers about the current snapshot (cached per snapshot, so no repeat calls). */
    fun runAiCouncil() {
        if (aiJob?.isActive == true) return
        val s0 = _uiState.value
        val data = s0.data ?: return
        if (data.isSimulatedFallback) return          // never ask AI about offline demo data
        aiJob = viewModelScope.launch {
            val report = withContext(Dispatchers.IO) {
                try {
                    val cfgs = aiConfigs()
                    if (cfgs.none { it.isConfigured && it.enabled }) {
                        null
                    } else {
                        _uiState.update { it.copy(aiRunning = true) }
                        val orch = aiOrchestrator
                        orch.mode = s0.aiMode
                        orch.debateEnabled = s0.aiDebate
                        orch.freshnessOverrideSec = s0.aiFreshnessSec
                        val st = learning.currentState()
                        val now = System.currentTimeMillis()
                        val rid = st.records.lastOrNull { it.interval == s0.selectedInterval && now < it.expiresAt }?.id ?: ""
                        val rep = orch.run(data, cfgs, rid, st)
                        // refresh the brain so its AI line and budget use this council
                        rep to (try { buildBrain(data.copy(aiCouncil = rep), rep) } catch (_: Exception) { null })
                    }
                } catch (_: Exception) {
                    null
                }
            }
            _uiState.update { s ->
                val d = s.data
                val rep = report?.first
                if (rep != null && d != null && d.interval == rep.interval) {
                    s.copy(aiRunning = false, data = d.copy(aiCouncil = rep, health = HealthMonitor.withAi(d.health, rep), brain = report.second ?: d.brain))
                } else s.copy(aiRunning = false)
            }
        }
    }

    /** newKey = null keeps the stored key. */
    fun saveAiProvider(id: String, newKey: String?, model: String, role: String, enabled: Boolean) {
        val pid = AiProviderId.fromName(id) ?: return
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) {
                val ok = if (!newKey.isNullOrBlank()) aiVault.putKey(pid, newKey) else true
                aiVault.saveSettings(pid, model.ifBlank { pid.defaultModel }, AiRole.fromName(role, pid.defaultRole), enabled)
                aiConfigCache = null
                try { aiOrchestrator.reset(pid) } catch (_: Exception) { }
                ok
            }
            _uiState.update {
                it.copy(aiTests = if (stored) it.aiTests - id else it.aiTests + (id to AiTestUi(false, false, "ERROR", "Could not store the key securely on this phone")))
            }
            refreshAiProviders()
            runAiCouncil()
        }
    }

    fun removeAiKey(id: String) {
        val pid = AiProviderId.fromName(id) ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                aiVault.removeKey(pid)
                aiConfigCache = null
                try { aiOrchestrator.reset(pid) } catch (_: Exception) { }
            }
            _uiState.update { it.copy(aiTests = it.aiTests - id) }
            refreshAiProviders()
        }
    }

    /** "Test" button: real request (model list or a tiny prompt) with the saved key. */
    fun testAiProvider(id: String) {
        val pid = AiProviderId.fromName(id) ?: return
        _uiState.update { it.copy(aiTests = it.aiTests + (id to AiTestUi(running = true))) }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    val cfg = aiConfigs().first { it.id == pid }
                    aiOrchestrator.test(cfg)
                } catch (e: Exception) {
                    null
                }
            }
            val ui = if (r == null) AiTestUi(false, false, "ERROR", "Test could not run")
            else AiTestUi(false, r.ok, r.status, r.detail, r.models.take(40))
            _uiState.update { it.copy(aiTests = it.aiTests + (id to ui)) }
        }
    }

    fun setAiMode(mode: String) {
        prefs.edit().putString("ai_mode", mode).apply()
        _uiState.update { it.copy(aiMode = mode) }
        runAiCouncil()
    }

    fun setAiDebate(on: Boolean) {
        prefs.edit().putBoolean("ai_debate", on).apply()
        _uiState.update { it.copy(aiDebate = on) }
    }

    fun setAiFreshness(sec: Int) {
        prefs.edit().putInt("ai_freshness_sec", sec).apply()
        _uiState.update { it.copy(aiFreshnessSec = sec) }
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

    fun setDashboardViewMode(mode: DashboardViewMode) {
        prefs.edit().putString("selected_dashboard_mode", mode.name).apply()
        _uiState.update { 
            it.copy(
                dashboardViewMode = mode,
                isCompactEasyView = (mode == DashboardViewMode.SIMPLE)
            ) 
        }
    }

    fun toggleCompactEasyView() {
        val current = _uiState.value.dashboardViewMode
        val next = when (current) {
            DashboardViewMode.UNIFIED -> DashboardViewMode.SIMPLE
            DashboardViewMode.SIMPLE -> DashboardViewMode.PRO
            DashboardViewMode.PRO -> DashboardViewMode.UNIFIED
        }
        setDashboardViewMode(next)
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
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { learning.recalibrateNow() }
            } catch (_: Exception) {
            }
            loadData(isInitial = false)
        }
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
                    releaseCountdownFormatted = if (active) "🚨 News mode switched ON manually" else "News mode switched OFF manually",
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
