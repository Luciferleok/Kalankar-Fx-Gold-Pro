package com.example.livegoldai.model

import com.example.livegoldai.localization.AppLanguage
import kotlinx.serialization.Serializable

@Serializable
enum class Signal(val label: String) {
    BUY("BUY"),
    SELL("SELL"),
    WAIT("WAIT")
}

@Serializable
data class CandleBar(
    val datetime: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val ema9: Double? = null,
    val ema21: Double? = null,
    val bbUpper: Double? = null,
    val bbLower: Double? = null,
    val superTrend: Double? = null,
    val volume: Double? = null,
    val vwap: Double? = null,
    val buyVolume: Double? = null
)

@Serializable
data class BuyerSellerSentiment(
    val buyersPercent: Int,
    val sellersPercent: Int,
    val buyerVolume: Double,
    val sellerVolume: Double,
    val netVolumeDelta: Double,
    val orderBookBidCount: Int,
    val orderBookAskCount: Int,
    val retailSentimentBias: Signal,
    val institutionalSentimentBias: Signal,
    val liveActionHindi: String,
    val liveActionEnglish: String,
    val liveActionMarathi: String = "",
    val strengthLevel: String
) {
    fun getLiveAction(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> liveActionEnglish
        AppLanguage.HINDI -> liveActionHindi
        AppLanguage.MARATHI -> liveActionMarathi.ifEmpty { liveActionHindi }
    }
}

@Serializable
data class EconomicEvent(
    val title: String,
    val country: String = "USD",
    val date: String,
    val time: String,
    val impact: String, // "High", "Medium", "Low", "Holiday"
    val forecast: String = "",
    val previous: String = "",
    val goldImpact: String = "Volatile Reaction",
    val isoTime: String = "",
    val indiaTime: String = "" // e.g. "08:30 PM IST"
) {
    fun getIndiaTimeFormatted(): String {
        if (indiaTime.isNotBlank()) return indiaTime
        return try {
            if (isoTime.length >= 19) {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val dateObj = sdf.parse(isoTime.substring(0, 19))
                if (dateObj != null) {
                    val istSdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US).apply {
                        timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                    }
                    istSdf.format(dateObj) + " IST"
                } else "IST (भारत समय)"
            } else "IST (भारत समय)"
        } catch (_: Exception) {
            "IST"
        }
    }

    fun getDisplayTimeBoth(): String {
        val ist = getIndiaTimeFormatted()
        return if (ist.isNotBlank() && !time.contains("IST", ignoreCase = true)) {
            "🇮🇳 $ist | $time"
        } else time
    }
}

@Serializable
data class MacroMarketIndex(
    val symbol: String,
    val name: String,
    val value: Double,
    val changePercent: Double,
    val impactOnGold: Signal,
    val explanation: String
)

@Serializable
data class NewsSentimentItem(
    val headline: String,
    val headlineHindi: String = "",
    val headlineMarathi: String = "",
    val source: String,
    val timestamp: String,
    val sentiment: Signal,
    val impactTag: String,
    val reason: String,
    val reasonHindi: String = "",
    val reasonMarathi: String = ""
) {
    fun getHeadline(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> headline
        AppLanguage.HINDI -> headlineHindi.ifEmpty { headline }
        AppLanguage.MARATHI -> headlineMarathi.ifEmpty { headlineHindi.ifEmpty { headline } }
    }
    fun getReason(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> reason
        AppLanguage.HINDI -> reasonHindi.ifEmpty { reason }
        AppLanguage.MARATHI -> reasonMarathi.ifEmpty { reasonHindi.ifEmpty { reason } }
    }
}

@Serializable
data class MacroSentimentRadar(
    val overallBias: Signal,
    val sentimentScorePercent: Int, // e.g. 75% Bullish Gold
    val dxyIndex: MacroMarketIndex,
    val us10yYield: MacroMarketIndex,
    val upcomingEvents: List<EconomicEvent> = emptyList(),
    val newsFeed: List<NewsSentimentItem> = emptyList(),
    val summaryInsight: String,
    val summaryInsightHindi: String = "",
    val summaryInsightMarathi: String = ""
) {
    fun getSummaryInsight(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> summaryInsight
        AppLanguage.HINDI -> summaryInsightHindi.ifEmpty { summaryInsight }
        AppLanguage.MARATHI -> summaryInsightMarathi.ifEmpty { summaryInsightHindi.ifEmpty { summaryInsight } }
    }
}

@Serializable
data class SmartMoneyAnalysis(
    val superTrendSignal: Signal,
    val superTrendValue: Double,
    val vwapValue: Double,
    val vwapSignal: Signal,
    val mfi14: Double,
    val mfiSignal: Signal,
    val fib0618: Double,
    val fib0500: Double,
    val marketStructure: String, // "BOS Bullish", "CHoCH Reversal", "Range Compression"
    val liquiditySweepAlert: String
)

@Serializable
data class AccountTierRisk(
    val balanceLabel: String,
    val safeLotSize: String,
    val riskAmountDollars: String,
    val rewardTp1Dollars: String,
    val rewardTp2Dollars: String
)

@Serializable
data class AppliedCorrectionDetail(
    val titleEnglish: String,
    val titleHindi: String,
    val titleMarathi: String = "",
    val descriptionEnglish: String,
    val descriptionHindi: String,
    val descriptionMarathi: String = "",
    val errorAddressedEnglish: String,
    val errorAddressedHindi: String,
    val errorAddressedMarathi: String = "",
    val badgeTag: String = "ACTIVE GUARD 🛡️"
) {
    fun getTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> titleEnglish
        AppLanguage.HINDI -> titleHindi
        AppLanguage.MARATHI -> titleMarathi.ifEmpty { titleHindi }
    }
    fun getDescription(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> descriptionEnglish
        AppLanguage.HINDI -> descriptionHindi
        AppLanguage.MARATHI -> descriptionMarathi.ifEmpty { descriptionHindi }
    }
    fun getErrorAddressed(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> errorAddressedEnglish
        AppLanguage.HINDI -> errorAddressedHindi
        AppLanguage.MARATHI -> errorAddressedMarathi.ifEmpty { errorAddressedHindi }
    }
}

@Serializable
data class ErrorCorrectionFeedback(
    val errorType: String,
    val errorTypeHindi: String,
    val pastMistakeDescriptionEnglish: String,
    val pastMistakeDescriptionHindi: String,
    val pastMistakeDescriptionMarathi: String = "",
    val correctionAppliedEnglish: String,
    val correctionAppliedHindi: String,
    val correctionAppliedMarathi: String = "",
    val status: String = "ACTIVE_GUARD"
) {
    fun getPastMistake(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> pastMistakeDescriptionEnglish
        AppLanguage.HINDI -> pastMistakeDescriptionHindi
        AppLanguage.MARATHI -> pastMistakeDescriptionMarathi.ifEmpty { pastMistakeDescriptionHindi }
    }
    fun getCorrectionApplied(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> correctionAppliedEnglish
        AppLanguage.HINDI -> correctionAppliedHindi
        AppLanguage.MARATHI -> correctionAppliedMarathi.ifEmpty { correctionAppliedHindi }
    }
}

@Serializable
data class NextPredictionPlaybook(
    val verdict: Signal,
    val urgencyTag: String, // "STRONG CONVICTION", "ACCUMULATE ON DIP", "WAIT / NO TRADE"
    val winProbabilityPercent: Int,
    val actionHeading: String,
    val tradeType: String = "INTRADAY SCALP / SWING", // "BUY SCALP (M15)", "SWING BUY (H1/H4)", "WAIT"
    val orderExecutionType: String = "BUY LIMIT / PULLBACK", // "BUY LIMIT @ $X", "MARKET EXECUTION"
    val recommendedEntryZone: String,
    val stopLossLevel: String,
    val stopLossPips: Double = 18.0,
    val stopLossRationale: String = "Placed strictly below swing structure & SuperTrend trail to prevent stop hunts",
    val stopLossRationaleEnglish: String = "",
    val stopLossRationaleMarathi: String = "",
    val takeProfit1: String,
    val takeProfit2: String,
    val takeProfit3: String = "",
    val whereToEnterHindi: String = "",
    val whereToEnterEnglish: String = "",
    val whereToEnterMarathi: String = "",
    val whereToAvoidHindi: String = "",
    val whereToAvoidEnglish: String = "",
    val whereToAvoidMarathi: String = "",
    val whatToDoHindi: String,
    val whatToDoEnglish: String,
    val whatToDoMarathi: String = "",
    val hindiAudioAdvice: String = "",
    val englishAudioAdvice: String = "",
    val marathiAudioAdvice: String = "",
    val executionRules: List<String> = emptyList(),
    val executionRulesHindi: List<String> = emptyList(),
    val executionRulesMarathi: List<String> = emptyList(),
    val accountTierMatrix: List<AccountTierRisk> = emptyList(),
    val profitProjection001Lot: String,
    val profitProjection010Lot: String,
    val profitProjection100Lot: String,
    val timeHorizon: String,
    val riskManagementRule: String,
    val validityDurationMinutes: Int = 180,
    val validUntilTimestamp: Long = 0L,
    val validityFormattedEnglish: String = "",
    val validityFormattedHindi: String = "",
    val validityFormattedMarathi: String = "",
    val invalidationRuleEnglish: String = "",
    val invalidationRuleHindi: String = "",
    val invalidationRuleMarathi: String = "",
    val appliedCorrections: List<AppliedCorrectionDetail> = emptyList()
) {
    fun getValidityFormatted(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> validityFormattedEnglish.ifEmpty { "Valid for $validityDurationMinutes Minutes" }
        AppLanguage.HINDI -> validityFormattedHindi.ifEmpty { validityFormattedEnglish.ifEmpty { "अगले $validityDurationMinutes मिनट तक मान्य" } }
        AppLanguage.MARATHI -> validityFormattedMarathi.ifEmpty { validityFormattedHindi.ifEmpty { validityFormattedEnglish } }
    }

    fun getInvalidationRule(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> invalidationRuleEnglish.ifEmpty { "Valid until time expires or price touches Stop Loss ($stopLossLevel) or TP2 ($takeProfit2)" }
        AppLanguage.HINDI -> invalidationRuleHindi.ifEmpty { "समय समाप्त होने तक या Stop Loss ($stopLossLevel) / Target ($takeProfit1) छूने तक मान्य" }
        AppLanguage.MARATHI -> invalidationRuleMarathi.ifEmpty { invalidationRuleHindi }
    }
    fun getWhereToEnter(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> whereToEnterEnglish.ifEmpty { "Execute limit orders in the confirmed pullback zone." }
        AppLanguage.HINDI -> whereToEnterHindi
        AppLanguage.MARATHI -> whereToEnterMarathi.ifEmpty { whereToEnterHindi }
    }

    fun getWhereToAvoid(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> whereToAvoidEnglish.ifEmpty { "Avoid market chasing at extended high or low levels." }
        AppLanguage.HINDI -> whereToAvoidHindi
        AppLanguage.MARATHI -> whereToAvoidMarathi.ifEmpty { whereToAvoidHindi }
    }

    fun getWhatToDo(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> whatToDoEnglish.ifEmpty { "Maintain strict risk management and await high-confluence confirmation." }
        AppLanguage.HINDI -> whatToDoHindi
        AppLanguage.MARATHI -> whatToDoMarathi.ifEmpty { whatToDoHindi }
    }

    fun getAudioAdvice(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> englishAudioAdvice.ifEmpty { whatToDoEnglish }
        AppLanguage.HINDI -> hindiAudioAdvice.ifEmpty { whatToDoHindi }
        AppLanguage.MARATHI -> marathiAudioAdvice.ifEmpty { whatToDoMarathi.ifEmpty { hindiAudioAdvice } }
    }

    fun getExecutionRules(lang: AppLanguage): List<String> = when (lang) {
        AppLanguage.ENGLISH -> executionRules
        AppLanguage.HINDI -> executionRulesHindi.ifEmpty { executionRules }
        AppLanguage.MARATHI -> executionRulesMarathi.ifEmpty { executionRulesHindi.ifEmpty { executionRules } }
    }

    fun getStopLossRationale(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> stopLossRationaleEnglish.ifEmpty { stopLossRationale }
        AppLanguage.HINDI -> stopLossRationale
        AppLanguage.MARATHI -> stopLossRationaleMarathi.ifEmpty { stopLossRationale }
    }
}

@Serializable
data class IndicatorItem(
    val name: String,
    val signal: Signal,
    val valueDisplay: String,
    val detail: String
)

@Serializable
data class GroupAnalysis(
    val key: String,
    val title: String,
    val verdict: Signal,
    val indicators: List<IndicatorItem>
)

@Serializable
data class PivotLevels(
    val pivot: Double,
    val r1: Double,
    val r2: Double,
    val s1: Double,
    val s2: Double
)

@Serializable
data class TradeSetup(
    val signal: Signal,
    val entryPrice: Double,
    val stopLoss: Double,
    val stopLossPips: Double,
    val takeProfit1: Double,
    val takeProfit1Pips: Double,
    val takeProfit2: Double,
    val takeProfit2Pips: Double,
    val riskRewardRatio: String,
    val confidencePercent: Int,
    val strategyNote: String,
    val strategyNoteHindi: String = "",
    val strategyNoteMarathi: String = "",
    val atrPips: Double
) {
    fun getStrategyNote(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> strategyNote
        AppLanguage.HINDI -> strategyNoteHindi.ifEmpty { strategyNote }
        AppLanguage.MARATHI -> strategyNoteMarathi.ifEmpty { strategyNoteHindi.ifEmpty { strategyNote } }
    }
}

@Serializable
data class MarketSession(
    val name: String,
    val city: String,
    val timeWindowUtc: String,
    val isOpen: Boolean,
    val volatilityLevel: String,
    val isGoldenOverlap: Boolean = false
)

@Serializable
data class CandleReadingInsight(
    val lastCandleType: String, // e.g. "Bullish Hammer / Liquidity Wick"
    val lastCandleMeaning: String, // "Strong buyer absorption at support"
    val upperWickPressure: String, // "Low (No seller resistance)"
    val lowerWickRejection: String, // "High ($4.80 liquidity absorption)"
    val bodyMomentum: String, // "Bullish Expansion (74% body ratio)"
    val nextCandleForecast: String, // "High probability of Green Bullish Expansion candle"
    val nextCandleExpectedRange: String, // "$4,382.00 - $4,396.00"
    val nextCandleTradeTactic: String, // "Buy the initial lower dip wick within first 2 minutes of the candle"
    val nextCandleTradeTacticHindi: String = "",
    val nextCandleTradeTacticMarathi: String = "",
    val confidencePercent: Int // 88%
) {
    fun getNextCandleTradeTactic(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> nextCandleTradeTactic
        AppLanguage.HINDI -> nextCandleTradeTacticHindi.ifEmpty { nextCandleTradeTactic }
        AppLanguage.MARATHI -> nextCandleTradeTacticMarathi.ifEmpty { nextCandleTradeTacticHindi.ifEmpty { nextCandleTradeTactic } }
    }
}

@Serializable
data class TimeframeStatus(
    val timeframe: String, // "1M", "5M", "15M", "1H", "4H", "1D"
    val label: String, // "Micro Scalp", "Fast Scalp", "Intraday Primary", "Hourly Trend", "Institutional Swing", "Daily Macro"
    val signal: Signal, // BUY, SELL, WAIT
    val keyLevel: String, // "$4,381.20 Support", "$4,395.00 Target"
    val momentumPercent: Int, // 85%
    val quickAction: String // "Scalp Long on dip", "Ride Bullish Expansion", "Hold"
)

@Serializable
data class MultiTimeframeMatrix(
    val timeframes: List<TimeframeStatus>,
    val alignmentSummary: String, // "5 of 6 Timeframes BULLISH • MAXIMUM CONFLUENCE"
    val scalpRecommendation: String, // "1M & 5M: Immediate Buy on Dip targeting +12 to +25 pips"
    val swingRecommendation: String // "15M to 4H: Strong Bullish Hold targeting +60 to +140 pips"
)

@Serializable
data class TradingTrick(
    val id: String,
    val name: String, // e.g. "Wick Trap & Liquidity Grab Trick", "Fair Value Gap (FVG) Magnet Trick"
    val winRate: String, // "89% Win Rate"
    val status: String, // "ACTIVE TRIGGER 🟢", "WATCHING 🟡", "READY"
    val triggerCondition: String, // "Price wicked below S1 then instantly reclaimed above VWAP"
    val triggerConditionEnglish: String = "",
    val triggerConditionMarathi: String = "",
    val howToTradeHindi: String, // Hindi in Devanagari with English core terms
    val howToTradeEnglish: String, // Pure English
    val howToTradeMarathi: String = "", // Marathi in Devanagari with English core terms
    val expectedPipGain: String // "+20 to +45 Pips"
) {
    fun getTriggerCondition(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> triggerConditionEnglish.ifEmpty { triggerCondition }
        AppLanguage.HINDI -> triggerCondition
        AppLanguage.MARATHI -> triggerConditionMarathi.ifEmpty { triggerCondition }
    }

    fun getHowToTrade(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> howToTradeEnglish
        AppLanguage.HINDI -> howToTradeHindi
        AppLanguage.MARATHI -> howToTradeMarathi.ifEmpty { howToTradeHindi }
    }
}

@Serializable
enum class PredictionOutcomeStatus {
    TP1_HIT,
    TP2_HIT,
    ALL_TARGETS_HIT,
    STOP_LOSS_HIT,
    IN_PROFIT_ACTIVE,
    PENDING_ENTRY
}

@Serializable
data class PastPredictionAuditItem(
    val id: String,
    val timestamp: String,
    val timeAgo: String,
    val signal: Signal,
    val entryPrice: Double,
    val target1Price: Double,
    val target2Price: Double,
    val stopLossPrice: Double,
    val actualHighLowReached: Double,
    val pipsResult: Double,
    val outcomeStatus: PredictionOutcomeStatus,
    val whyItHappenedHindi: String,
    val whyItHappenedEnglish: String,
    val whyItHappenedMarathi: String = "",
    val lessonLearnedHindi: String,
    val lessonLearnedEnglish: String,
    val lessonLearnedMarathi: String = "",
    val indicatorsInvolved: List<String>
) {
    fun getWhyItHappened(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> whyItHappenedEnglish
        AppLanguage.HINDI -> whyItHappenedHindi
        AppLanguage.MARATHI -> whyItHappenedMarathi.ifEmpty { whyItHappenedHindi }
    }

    fun getLessonLearned(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> lessonLearnedEnglish
        AppLanguage.HINDI -> lessonLearnedHindi
        AppLanguage.MARATHI -> lessonLearnedMarathi.ifEmpty { lessonLearnedHindi }
    }
}

@Serializable
data class TimeframeAccuracyAudit(
    val timeframe: String,
    val totalSignalsTested: Int,
    val winCount: Int,
    val lossCount: Int,
    val activeCount: Int,
    val winRatePercent: Int,
    val netPipsGained: Double,
    val lastPredictionOutcome: PastPredictionAuditItem?,
    val recentSignalAudits: List<PastPredictionAuditItem>,
    val autoCorrectionRules: List<String>,
    val autoCorrectionRulesHindi: List<String>,
    val autoCorrectionRulesMarathi: List<String> = emptyList(),
    val aiEngineLearningStatus: String,
    val autoCorrectionsLearnedCount: Int = 4,
    val errorDiagnosisList: List<ErrorCorrectionFeedback> = emptyList()
) {
    fun getAutoCorrectionRules(lang: AppLanguage): List<String> = when (lang) {
        AppLanguage.ENGLISH -> autoCorrectionRules
        AppLanguage.HINDI -> autoCorrectionRulesHindi
        AppLanguage.MARATHI -> if (autoCorrectionRulesMarathi.isNotEmpty()) autoCorrectionRulesMarathi else autoCorrectionRulesHindi
    }
}

@Serializable
data class GoldAnalysisResult(
    val symbol: String = "XAU/USD",
    val currentPrice: Double,
    val prevClose: Double,
    val changeAmount: Double,
    val changePercent: Double,
    val high24h: Double,
    val low24h: Double,
    val interval: String,
    val lastUpdated: String,
    val overallSignal: Signal,
    val agreementPercent: Double,
    val buyCount: Int,
    val sellCount: Int,
    val waitCount: Int,
    val totalGroups: Int = 7,
    val groups: List<GroupAnalysis>,
    val pivotLevels: PivotLevels,
    val tradeSetup: TradeSetup,
    val marketSessions: List<MarketSession> = emptyList(),
    val recentCandles: List<CandleBar> = emptyList(),
    val nextPrediction: NextPredictionPlaybook? = null,
    val macroRadar: MacroSentimentRadar? = null,
    val smartMoney: SmartMoneyAnalysis? = null,
    val candleInsight: CandleReadingInsight? = null,
    val mtfMatrix: MultiTimeframeMatrix? = null,
    val tradingTricks: List<TradingTrick> = emptyList(),
    val buyerSellerRatio: BuyerSellerSentiment? = null,
    val timeframeAudit: TimeframeAccuracyAudit? = null,
    val multiAiConsensus: MultiAiConsensusReport? = null,
    val failedPredictionAutopsy: FailedPredictionCandleAutopsy? = null,
    val newsTradingPlan: NewsTradingPlan? = null,
    val quantBotSignal: QuantBotTradeSignal? = null,
    val multiBotEnsemble: MultiBotEnsemble? = null,
    val productionImprovement: ProductionImprovementEngine? = null,
    val isNewsModeTriggered: Boolean = false,
    val isSimulatedFallback: Boolean = false,
    val newsMode: NewsModeStatus? = null,
    val learning: LearningSnapshot? = null,
    val feed: FeedStatus? = null,
    val health: SystemHealth? = null,
    val insights: CockpitInsights? = null,
    val aiCouncil: AiCouncilReport? = null,
    val brain: BrainReport? = null
)

@Serializable
data class FailedPredictionCandleAutopsy(
    val previousTradeId: String,
    val failedSignal: Signal,
    val entryPrice: Double,
    val stopLossPrice: Double,
    val targetPrice: Double,
    val pipsLoss: Double,
    val timeAgo: String,
    val trapCandleType: String,
    val candleOpen: Double,
    val candleHigh: Double,
    val candleLow: Double,
    val candleClose: Double,
    val upperWickPips: Double,
    val lowerWickPips: Double,
    val bodyPips: Double,
    val volumeSurgeMultiplier: Double,
    val trapDiagnosisEnglish: String,
    val trapDiagnosisHindi: String,
    val trapDiagnosisMarathi: String = "",
    val geminiCandleReading: String,
    val chatGptCandleReading: String,
    val claudeCandleReading: String,
    val deepSeekCandleReading: String,
    val recalibrationActionTakenEnglish: String,
    val recalibrationActionTakenHindi: String,
    val recalibrationActionTakenMarathi: String = ""
) {
    fun getDiagnosis(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> trapDiagnosisEnglish
        AppLanguage.HINDI -> trapDiagnosisHindi
        AppLanguage.MARATHI -> trapDiagnosisMarathi.ifEmpty { trapDiagnosisHindi }
    }
    fun getRecalibrationAction(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> recalibrationActionTakenEnglish
        AppLanguage.HINDI -> recalibrationActionTakenHindi
        AppLanguage.MARATHI -> recalibrationActionTakenMarathi.ifEmpty { recalibrationActionTakenHindi }
    }
}

@Serializable
enum class NewsPhase {
    PRE_NEWS_COIL,
    LIVE_NEWS_SPIKE,
    POST_NEWS_RETRACEMENT,
    STANDBY
}

@Serializable
data class NewsTradingPlan(
    val isNewsActive: Boolean,
    val eventName: String,
    val eventImpact: String,
    val releaseCountdownFormatted: String,
    val phase: NewsPhase,
    val primaryDirectionBias: Signal,
    val straddleUpperLevel: Double,
    val straddleLowerLevel: Double,
    val spreadWarningBufferPips: Double,
    val freezeRuleTitle: String,
    val freezeRuleDescriptionEnglish: String,
    val freezeRuleDescriptionHindi: String,
    val freezeRuleDescriptionMarathi: String = "",
    val newsTacticHeadingEnglish: String,
    val newsTacticHeadingHindi: String,
    val newsTacticHeadingMarathi: String = "",
    val newsTacticDetailEnglish: String,
    val newsTacticDetailHindi: String,
    val newsTacticDetailMarathi: String = "",
    val secondWaveRetracementLevel: String,
    val actualVsForecastScenario: String
) {
    fun getFreezeRule(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> freezeRuleDescriptionEnglish
        AppLanguage.HINDI -> freezeRuleDescriptionHindi
        AppLanguage.MARATHI -> freezeRuleDescriptionMarathi.ifEmpty { freezeRuleDescriptionHindi }
    }
    fun getNewsTactic(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> newsTacticDetailEnglish
        AppLanguage.HINDI -> newsTacticDetailHindi
        AppLanguage.MARATHI -> newsTacticDetailMarathi.ifEmpty { newsTacticDetailHindi }
    }
}

@Serializable
enum class AiModelProvider(
    val displayName: String,
    val company: String,
    val iconEmoji: String,
    val roleBadge: String
) {
    // Local rule engines (no external AI is called). Constant names kept for compatibility.
    GEMINI("Rule 1 · Pillar Vote + VWAP", "Local rule", "1️⃣", "4+/7 pillars and VWAP side"),
    CHAT_GPT("Rule 2 · Dollar Filter", "Local rule", "2️⃣", "Main signal only if DXY agrees"),
    CLAUDE("Rule 3 · Risk Guard", "Local rule", "3️⃣", "Stricter after a real recent loss"),
    DEEP_SEEK("Rule 4 · RSI Zone", "Local rule", "4️⃣", "RSI >= 55 BUY, <= 45 SELL"),
    PERPLEXITY("Rule 5 · Weighted 68% Gate", "Local rule", "5️⃣", "The app's main signal")
}

@Serializable
data class SingleAiPredictionInsight(
    val provider: AiModelProvider,
    val signal: Signal,
    val confidencePercent: Int,
    val coreThesisEnglish: String,
    val coreThesisHindi: String,
    val coreThesisMarathi: String = "",
    val correctionAppliedEnglish: String,
    val correctionAppliedHindi: String,
    val correctionAppliedMarathi: String = "",
    val suggestedStopLossPips: Double,
    val suggestedTargetPips: Double,
    val keyTrapWarned: String? = null
) {
    fun getCoreThesis(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> coreThesisEnglish
        AppLanguage.HINDI -> coreThesisHindi
        AppLanguage.MARATHI -> coreThesisMarathi.ifEmpty { coreThesisEnglish }
    }
    fun getCorrectionApplied(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> correctionAppliedEnglish
        AppLanguage.HINDI -> correctionAppliedHindi
        AppLanguage.MARATHI -> correctionAppliedMarathi.ifEmpty { correctionAppliedEnglish }
    }
}

@Serializable
data class MultiAiConsensusReport(
    val unanimousAgreementPercent: Int,
    val consensusSignal: Signal,
    val consensusConfidence: Int,
    val agreeingModelsCount: Int,
    val totalModelsCount: Int = 5,
    val consensusSummaryEnglish: String,
    val consensusSummaryHindi: String,
    val consensusSummaryMarathi: String = "",
    val modelInsights: List<SingleAiPredictionInsight>,
    val jointAiCorrections: List<String>,
    val jointAiCorrectionsHindi: List<String>,
    val jointAiCorrectionsMarathi: List<String> = emptyList(),
    val calibratedEntryRecommendation: String,
    val calibratedSlRecommendation: String,
    val calibratedTpRecommendation: String
) {
    fun getSummary(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> consensusSummaryEnglish
        AppLanguage.HINDI -> consensusSummaryHindi
        AppLanguage.MARATHI -> consensusSummaryMarathi.ifEmpty { consensusSummaryEnglish }
    }
    fun getJointCorrections(lang: AppLanguage): List<String> = when (lang) {
        AppLanguage.ENGLISH -> jointAiCorrections
        AppLanguage.HINDI -> jointAiCorrectionsHindi.ifEmpty { jointAiCorrections }
        AppLanguage.MARATHI -> jointAiCorrectionsMarathi.ifEmpty { jointAiCorrections }
    }
}

@Serializable
data class NewsModeStatus(
    val phase: String, // "PRE" = 15m prior radar, "POST" / "LIVE" = release window
    val eventTitle: String,
    val minutes: Long,
    val newsSignal: Signal,
    val technicalSignal: Signal,
    val headline: String,
    val detail: String,
    val headlineEnglish: String = "",
    val headlineHindi: String = "",
    val headlineMarathi: String = "",
    val detailEnglish: String = "",
    val detailHindi: String = "",
    val detailMarathi: String = ""
) {
    fun getLocalizedHeadline(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> headlineEnglish.ifBlank { headline }
        AppLanguage.HINDI -> headlineHindi.ifBlank { headline }
        AppLanguage.MARATHI -> headlineMarathi.ifBlank { headlineEnglish.ifBlank { headline } }
    }
    fun getLocalizedDetail(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> detailEnglish.ifBlank { detail }
        AppLanguage.HINDI -> detailHindi.ifBlank { detail }
        AppLanguage.MARATHI -> detailMarathi.ifBlank { detailEnglish.ifBlank { detail } }
    }
}

@Serializable
data class QuantBotTradeSignal(
    val botName: String = "KALANKAR RULE BOT",
    val statusText: String, // "🟢 ACTIVE SCANNING & READY", "⚡ LIVE ORDER DISPATCHED", "🛡️ STANDBY (CAPITAL GUARD)"
    val isTradeActive: Boolean,
    val signal: Signal,
    val orderType: String, // "BUY LIMIT @ 50% FVG", "SELL LIMIT @ RESISTANCE", "STANDBY / WAIT"
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val pipsRisk: Double,
    val pipsRewardTp1: Double,
    val pipsRewardTp2: Double,
    val recommendedLot: Double, // e.g. 0.04
    val winProbabilityPercent: Int, // e.g. 93
    val strategyName: String, // "Institutional ICT / SMC + Multi-AI Hybrid"
    val executionCommandEng: String, // "BUY LIMIT XAUUSD @ 2646.40 SL 2638.50 TP1 2658.00 TP2 2668.00"
    val botReasoningEnglish: String,
    val botReasoningHindi: String,
    val botReasoningMarathi: String = "",
    val autoBreakevenRuleEng: String,
    val autoBreakevenRuleHindi: String,
    val autoBreakevenRuleMarathi: String = "",
    val newsLockActive: Boolean = false
) {
    fun getBotReasoning(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> botReasoningEnglish
        AppLanguage.HINDI -> botReasoningHindi
        AppLanguage.MARATHI -> botReasoningMarathi.ifEmpty { botReasoningHindi }
    }
    fun getBreakevenRule(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> autoBreakevenRuleEng
        AppLanguage.HINDI -> autoBreakevenRuleHindi
        AppLanguage.MARATHI -> autoBreakevenRuleMarathi.ifEmpty { autoBreakevenRuleHindi }
    }
}

@Serializable
data class IndividualBot(
    val id: String,
    val name: String,
    val hindiName: String,
    val iconEmoji: String,
    val botType: String,
    val backtestedWinRate: Double, // e.g. 91.2
    val profitFactor: Double, // e.g. 3.4
    val signal: Signal,
    val confidence: Int,
    val keyTriggerSummary: String,
    val keyTriggerSummaryHindi: String,
    val keyTriggerSummaryMarathi: String = "",
    val suggestedOrder: String,
    val stopLossPips: Double,
    val targetPips: Double,
    val status: String // "LIVE ACTIVE 🟢", "WATCHING 🟡", "LOCKED 🛡️"
) {
    fun getKeyTrigger(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> keyTriggerSummary
        AppLanguage.HINDI -> keyTriggerSummaryHindi.ifBlank { keyTriggerSummary }
        AppLanguage.MARATHI -> keyTriggerSummaryMarathi.ifBlank { keyTriggerSummaryHindi.ifBlank { keyTriggerSummary } }
    }
}

@Serializable
data class MultiBotEnsemble(
    val ensembleSignal: Signal,
    val consensusPercent: Int,
    val buyVotes: Int,
    val sellVotes: Int,
    val waitVotes: Int,
    val totalBots: Int = 6,
    val isEnsembleConsensusStrong: Boolean,
    val consensusLevel: String, // "MAXIMUM CONFLUENCE 🚀", "HIGH CONVICTION 🟢", "CAPITAL PRESERVATION STANDBY 🛡️"
    val allBots: List<IndividualBot>,
    val ensembleRationaleEnglish: String,
    val ensembleRationaleHindi: String,
    val ensembleRationaleMarathi: String = "",
    val executionTacticHindi: String,
    val executionTacticEnglish: String,
    val executionTacticMarathi: String = "",
    val recommendedEntry: Double,
    val recommendedSl: Double,
    val recommendedTp1: Double,
    val recommendedTp2: Double
) {
    fun getRationale(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> ensembleRationaleEnglish
        AppLanguage.HINDI -> ensembleRationaleHindi.ifBlank { ensembleRationaleEnglish }
        AppLanguage.MARATHI -> ensembleRationaleMarathi.ifBlank { ensembleRationaleEnglish }
    }
    fun getExecutionTactic(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> executionTacticEnglish
        AppLanguage.HINDI -> executionTacticHindi.ifBlank { executionTacticEnglish }
        AppLanguage.MARATHI -> executionTacticMarathi.ifBlank { executionTacticEnglish }
    }
}

@Serializable
data class AppliedProductionFix(
    val ruleTitle: String,
    val ruleTitleHindi: String,
    val errorPrevented: String,
    val errorPreventedHindi: String,
    val improvementImpact: String,
    val improvementImpactHindi: String
)

@Serializable
data class ModelWeightItem(
    val pillarName: String,
    val pillarNameHindi: String,
    val weightMultiplier: Double,
    val adjustmentReason: String,
    val adjustmentReasonHindi: String
)

@Serializable
data class ProductionImprovementEngine(
    val engineVersion: String = "Base rules v1",
    val statusBadge: String = "OBSERVING",
    val verifiedAccuracyPercent: Double = -1.0, // -1 = not enough verified predictions
    val totalBacktestedTrades: Int = 0, // number of real verified predictions
    val profitFactor: Double = -1.0,
    val averagePipGainPerTrade: Double = 0.0,
    val dynamicConfidenceThreshold: Int = 68,
    val appliedProductionFixes: List<AppliedProductionFix>,
    val liveModelWeights: List<ModelWeightItem>,
    val isAutoTuningActive: Boolean = true
)


