package com.example.livegoldai.localization

import com.example.livegoldai.model.PredictionOutcomeStatus
import com.example.livegoldai.model.Signal

object LocalizationStrings {

    // App Headers
    fun appTitle(lang: AppLanguage): String = "Kalankar FX Gold Pro"

    fun appSubtitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Trading Intelligence by Rudvay Ujjwal Kalankar"
        AppLanguage.HINDI -> "रुद्वय उज्ज्वल कलणकर द्वारा ट्रेडिंग इंटेलिजेंस"
        AppLanguage.MARATHI -> "रुद्वय उज्ज्वल काळणकर यांचे ट्रेडिंग इंटेलिजन्स"
    }

    fun autoRefreshIn(lang: AppLanguage, seconds: Int): String = when (lang) {
        AppLanguage.ENGLISH -> "Auto-refresh in ${seconds}s"
        AppLanguage.HINDI -> "ऑटो-रिफ्रेश ${seconds}s में"
        AppLanguage.MARATHI -> "ऑटो-रिफ्रेश ${seconds}s मध्ये"
    }

    // Cockpit Navigation Modes
    fun cockpitAll(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "ALL COCKPIT"
        AppLanguage.HINDI -> "सभी फीचर्स"
        AppLanguage.MARATHI -> "सर्व फीचर्स"
    }

    fun cockpitLive(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "LIVE STATUS"
        AppLanguage.HINDI -> "अभी का स्टेटस"
        AppLanguage.MARATHI -> "सध्याची स्थिती"
    }

    fun cockpitPrediction(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "AI PREDICTION"
        AppLanguage.HINDI -> "AI प्रेडिक्शन"
        AppLanguage.MARATHI -> "AI अंदाज (प्रेडिक्शन)"
    }

    // Settings
    fun settingsTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Trading Terminal Settings"
        AppLanguage.HINDI -> "ट्रेडिंग टर्मिनल सेटिंग्स"
        AppLanguage.MARATHI -> "ट्रेडिंग टर्मिनल सेटिंग्ज"
    }

    fun selectLanguage(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Choose App Language"
        AppLanguage.HINDI -> "ऐप की भाषा चुनें (Language Setting)"
        AppLanguage.MARATHI -> "अ‍ॅपची भाषा निवडा (Language Setting)"
    }

    fun selectLanguageDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Choose between English, Hindi, and Marathi to translate the full app"
        AppLanguage.HINDI -> "इंग्लिश, हिंदी या मराठी चुनें - पूरा ऐप तुरंत उसी भाषा में बदल जाएगा"
        AppLanguage.MARATHI -> "इंग्रजी, हिंदी किंवा मराठी निवडा - संपूर्ण ॲप त्वरित त्या भाषेत बदलेल"
    }

    fun currentSelected(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "ACTIVE LANGUAGE"
        AppLanguage.HINDI -> "सक्रिय भाषा"
        AppLanguage.MARATHI -> "सक्रिय भाषा"
    }

    fun languageChangedToast(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "✓ Language updated to English • Entire app refreshed!"
        AppLanguage.HINDI -> "✓ भाषा बदलकर हिंदी हो गई • पूरा ऐप तुरंत अपडेट हो गया!"
        AppLanguage.MARATHI -> "✓ भाषा बदलून मराठी झाली • संपूर्ण ॲप त्वरित अपडेट झाले!"
    }

    fun liveLanguagePreviewTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "LIVE LANGUAGE PREVIEW (FULL APP TRANSLATION)"
        AppLanguage.HINDI -> "लाइव भाषा पूर्वावलोकन (पूरे ऐप का अनुवाद)"
        AppLanguage.MARATHI -> "थेट भाषा पूर्वावलोकन (संपूर्ण ॲपचे भाषांतर)"
    }

    fun deepDiveIntelligence(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "DEEP-DIVE MARKET INTELLIGENCE"
        AppLanguage.HINDI -> "मार्केट का गहरा विश्लेषण (DEEP DIVE)"
        AppLanguage.MARATHI -> "मार्केटचे सखोल विश्लेषण (DEEP DIVE)"
    }

    fun proModulesCount(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "5 Pro Modules"
        AppLanguage.HINDI -> "5 प्रो मॉड्यूल्स"
        AppLanguage.MARATHI -> "5 प्रो मॉड्यूल्स"
    }

    fun dedicatedTo(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Dedicated to Mr. Rudvay Ujjwal Kalankar"
        AppLanguage.HINDI -> "श्री रुद्वय उज्ज्वल कलणकर को समर्पित"
        AppLanguage.MARATHI -> "श्री रुद्वय उज्ज्वल काळणकर यांना समर्पित"
    }

    fun disclaimer(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Advanced AI analysis engine • For educational & trading reference"
        AppLanguage.HINDI -> "उन्नत AI विश्लेषण इंजन • केवल शैक्षणिक और ट्रेडिंग संदर्भ के लिए"
        AppLanguage.MARATHI -> "प्रगत AI विश्लेषण इंजिन • केवळ शैक्षणिक आणि ट्रेडिंग संदर्भासाठी"
    }

    fun loadingLiveIndicators(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Analyzing XAU/USD live technical indicators..."
        AppLanguage.HINDI -> "XAU/USD लाइव टेक्निकल इंडिकेटर्स का विश्लेषण हो रहा है..."
        AppLanguage.MARATHI -> "XAU/USD लाईव्ह टेक्निकल इंडिकेटर्सचे विश्लेषण सुरू आहे..."
    }

    fun errorLoading(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Unable to load live analysis"
        AppLanguage.HINDI -> "लाइव विश्लेषण लोड करने में असमर्थ"
        AppLanguage.MARATHI -> "लाईव्ह विश्लेषण लोड करण्यात अडचण"
    }

    fun tryAgain(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Try Again"
        AppLanguage.HINDI -> "पुनः प्रयास करें"
        AppLanguage.MARATHI -> "पुन्हा प्रयत्न करा"
    }

    fun offlineModeCached(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Offline / Network limited • Showing cached gold stream"
        AppLanguage.HINDI -> "ऑफ़लाइन / नेटवर्क सीमित • कैश्ड गोल्ड डेटा दिखाया जा रहा है"
        AppLanguage.MARATHI -> "ऑफलाइन / नेटवर्क मर्यादित • कॅश केलेला डेटा दाखवला जात आहे"
    }

    fun liveOrderFlowBalance(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Live Order Flow Balance"
        AppLanguage.HINDI -> "लाइव ऑर्डर फ्लो बैलेंस"
        AppLanguage.MARATHI -> "लाईव्ह ऑर्डर फ्लो बॅलन्स"
    }

    fun bullsDominant(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "🟢 BULLS DOMINANT"
        AppLanguage.HINDI -> "🟢 खरीदार भारी (Bulls Dominant)"
        AppLanguage.MARATHI -> "🟢 खरेदीदार वरचढ (Bulls Dominant)"
    }

    fun bearsDominant(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "🔴 BEARS DOMINANT"
        AppLanguage.HINDI -> "🔴 विक्रेता भारी (Bears Dominant)"
        AppLanguage.MARATHI -> "🔴 विक्रेते वरचढ (Bears Dominant)"
    }

    fun changeLanguageQuick(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Language"
        AppLanguage.HINDI -> "भाषा"
        AppLanguage.MARATHI -> "भाषा"
    }

    fun apiKeyLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "TwelveData API Key"
        AppLanguage.HINDI -> "TwelveData API कुंजी"
        AppLanguage.MARATHI -> "TwelveData API की"
    }

    fun apiKeyDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Provides live 1m, 5m, 15m, 1h, 4h & 1d XAU/USD gold feeds."
        AppLanguage.HINDI -> "यह लाइव 1m, 5m, 15m, 1h, 4h और 1d XAU/USD गोल्ड डेटा देता है।"
        AppLanguage.MARATHI -> "हे थेट 1m, 5m, 15m, 1h, 4h आणि 1d XAU/USD सोन्याचा डेटा पुरवते."
    }

    fun customizeTheme(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Appearance"
        AppLanguage.HINDI -> "दिखावट"
        AppLanguage.MARATHI -> "स्वरूप"
    }

    fun customizeEmblem(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Customize VIP Brand Emblem"
        AppLanguage.HINDI -> "VIP ब्रांड प्रतीक बदलें"
        AppLanguage.MARATHI -> "VIP ब्रँड चिन्ह बदला"
    }

    fun saveAndRefresh(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Save & Apply"
        AppLanguage.HINDI -> "सेव करें और लागू करें"
        AppLanguage.MARATHI -> "जतन करा आणि लागू करा"
    }

    fun cancel(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Cancel"
        AppLanguage.HINDI -> "रद्द करें"
        AppLanguage.MARATHI -> "रद्द करा"
    }

    // Price Header Card
    fun liveGoldPrice(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "LIVE GOLD (XAU/USD)"
        AppLanguage.HINDI -> "लाइव सोना भाव (XAU/USD)"
        AppLanguage.MARATHI -> "लाईव्ह सोन्याचा दर (XAU/USD)"
    }

    fun high24h(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "24H High"
        AppLanguage.HINDI -> "24 घंटे उच्चतम"
        AppLanguage.MARATHI -> "24 तास उच्च"
    }

    fun low24h(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "24H Low"
        AppLanguage.HINDI -> "24 घंटे न्यूनतम"
        AppLanguage.MARATHI -> "24 तास नीच"
    }

    fun change(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "24H Change"
        AppLanguage.HINDI -> "24 घंटे बदलाव"
        AppLanguage.MARATHI -> "24 तास बदल"
    }

    fun buyersVsSellers(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "BUYERS VS SELLERS"
        AppLanguage.HINDI -> "खरीदार बनाम विक्रेता"
        AppLanguage.MARATHI -> "खरेदीदार विरुद्ध विक्रेते"
    }

    fun buyers(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Buyers"
        AppLanguage.HINDI -> "खरीदार (बायर्स)"
        AppLanguage.MARATHI -> "खरेदीदार (बायर्स)"
    }

    fun sellers(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Sellers"
        AppLanguage.HINDI -> "विक्रेता (सेलर्स)"
        AppLanguage.MARATHI -> "विक्रेते (सेलर्स)"
    }

    // Dual Prediction & Indicators Section
    fun dualSectionTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "⚡ DUAL COMMAND: PREDICTION & INDICATORS"
        AppLanguage.HINDI -> "⚡ डुअल कमांड: प्रेडिक्शन और इंडिकेटर्स"
        AppLanguage.MARATHI -> "⚡ दुहेरी नियंत्रण: अंदाज आणि इंडिकेटर्स"
    }

    fun dualSectionSubtitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Column 1: AI Future Forecast • Column 2: 7 Indicator Pillars"
        AppLanguage.HINDI -> "कॉलम 1: AI भविष्य संकेत • कॉलम 2: 7 इंडिकेटर पिलर्स"
        AppLanguage.MARATHI -> "कॉलम 1: AI भविष्याचा अंदाज • कॉलम 2: 7 इंडिकेटर खांब"
    }

    fun viewBoth(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "BOTH COLUMNS"
        AppLanguage.HINDI -> "दोनों साथ में"
        AppLanguage.MARATHI -> "दोन्ही एकत्र"
    }

    fun viewPredictionOnly(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "1. PREDICTION"
        AppLanguage.HINDI -> "1. सिर्फ प्रेडिक्शन"
        AppLanguage.MARATHI -> "1. फक्त अंदाज"
    }

    fun viewIndicatorsOnly(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "2. INDICATORS"
        AppLanguage.HINDI -> "2. सिर्फ इंडिकेटर्स"
        AppLanguage.MARATHI -> "2. फक्त इंडिकेटर्स"
    }

    fun verdictBuy(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "BUY OPPORTUNITY"
        AppLanguage.HINDI -> "बाय (खरीद) हो सकता है"
        AppLanguage.MARATHI -> "बाय (खरेदी) होऊ शकते"
    }

    fun verdictSell(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "SELL OPPORTUNITY"
        AppLanguage.HINDI -> "सेल (बिकवाली) हो सकती है"
        AppLanguage.MARATHI -> "सेल (विक्री) होऊ शकते"
    }

    fun verdictWait(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "WAIT / RANGE-BOUND"
        AppLanguage.HINDI -> "इंतज़ार करो (वेट)"
        AppLanguage.MARATHI -> "वाट पहा (वेट करा)"
    }

    fun buySubtitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Strong bullish accumulation • Target upper resistance"
        AppLanguage.HINDI -> "मजबूत खरीदारी दबाव • ऊपरी रेजिस्टेंस का लक्ष्य"
        AppLanguage.MARATHI -> "मजबूत खरेदीचा जोर • वरील रेझिस्टन्सचे लक्ष्य"
    }

    fun sellSubtitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Bearish rejection • Target lower support"
        AppLanguage.HINDI -> "मजबूत बिकवाली दबाव • निचले सपोर्ट का लक्ष्य"
        AppLanguage.MARATHI -> "मजबूत विक्रीचा जोर • खालील सपोर्टचे लक्ष्य"
    }

    fun waitSubtitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Market compressing • Wait for clean breakout"
        AppLanguage.HINDI -> "मार्केट साइडवेज़ है • साफ ब्रेकआउट का इंतज़ार करें"
        AppLanguage.MARATHI -> "मार्केट साइडवेज आहे • स्पष्ट ब्रेकआउटची वाट पहा"
    }

    fun recommendedEntry(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Recommended Entry"
        AppLanguage.HINDI -> "सुझावित एंट्री"
        AppLanguage.MARATHI -> "शिफारस केलेली एंट्री"
    }

    fun target1(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Target 1 (TP1)"
        AppLanguage.HINDI -> "टारगेट 1 (TP1)"
        AppLanguage.MARATHI -> "टारगेट 1 (TP1)"
    }

    fun target2(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Target 2 (TP2)"
        AppLanguage.HINDI -> "टारगेट 2 (TP2)"
        AppLanguage.MARATHI -> "टारगेट 2 (TP2)"
    }

    fun stopLoss(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Stop Loss (SL)"
        AppLanguage.HINDI -> "स्टॉप लॉस (SL)"
        AppLanguage.MARATHI -> "स्टॉप लॉस (SL)"
    }

    fun whereToEnter(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Where to Enter"
        AppLanguage.HINDI -> "कहाँ एंट्री लें"
        AppLanguage.MARATHI -> "कुठे एंट्री घ्यावी"
    }

    fun whereToAvoid(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Where to Avoid"
        AppLanguage.HINDI -> "कहाँ ट्रेड न लें"
        AppLanguage.MARATHI -> "कुठे ट्रेड टाळावा"
    }

    fun whatToDo(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "What to Do Now"
        AppLanguage.HINDI -> "अभी क्या करना चाहिए"
        AppLanguage.MARATHI -> "आता काय करावे"
    }

    fun openLotCalc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Open Lot Calculator"
        AppLanguage.HINDI -> "लॉट कैलकुलेटर खोलें"
        AppLanguage.MARATHI -> "लॉट कॅल्क्युलेटर उघडा"
    }

    fun askAiButton(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "🔮 WHAT WILL HAPPEN NEXT? (ASK AI)"
        AppLanguage.HINDI -> "🔮 आगे क्या होगा? (AI से पूछो)"
        AppLanguage.MARATHI -> "🔮 पुढे काय होणार? (AI ला विचारा)"
    }

    // Accuracy Audit Card
    fun accuracyAuditTitle(lang: AppLanguage, tf: String): String = when (lang) {
        AppLanguage.ENGLISH -> "$tf ACCURACY & OUTCOME AUDIT"
        AppLanguage.HINDI -> "$tf टाइमफ्रेम एक्यूरेसी और ऑडिट"
        AppLanguage.MARATHI -> "$tf टाइमफ्रेम अचूकता आणि ऑडिट"
    }

    fun winRateLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "WIN RATE"
        AppLanguage.HINDI -> "जीतने की दर"
        AppLanguage.MARATHI -> "जिंकण्याचा दर"
    }

    fun wonLost(lang: AppLanguage, won: Int, lost: Int): String = when (lang) {
        AppLanguage.ENGLISH -> "$won Won / $lost Lost"
        AppLanguage.HINDI -> "$won पास / $lost फेल"
        AppLanguage.MARATHI -> "$won यशस्वी / $lost अयशस्वी"
    }

    fun netProfit(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "NET PROFIT"
        AppLanguage.HINDI -> "कुल मुनाफा"
        AppLanguage.MARATHI -> "एकूण नफा"
    }

    fun pastTradesLog(lang: AppLanguage, tf: String): String = when (lang) {
        AppLanguage.ENGLISH -> "$tf PAST SIGNALS VERIFIED RESULT"
        AppLanguage.HINDI -> "$tf पिछले सिग्नल्स का परिणाम (ऑडिट लॉग)"
        AppLanguage.MARATHI -> "$tf मागील सिग्नल्सचा निकाल (ऑडिट लॉग)"
    }

    fun whyItHappened(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Why it happened:"
        AppLanguage.HINDI -> "क्यों हुआ था:"
        AppLanguage.MARATHI -> "का घडले होते:"
    }

    fun lessonLearned(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "AI Lesson & Rule:"
        AppLanguage.HINDI -> "AI ने क्या सबक सीखा:"
        AppLanguage.MARATHI -> "AI ने काय धडा शिकला:"
    }

    fun aiSelfCorrectionRules(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "AI SELF-CORRECTION SAFEGUARDS"
        AppLanguage.HINDI -> "AI आत्म-सुधार सुरक्षा नियम"
        AppLanguage.MARATHI -> "AI आत्म-सुधारणा सुरक्षा नियम"
    }

    // Order Flow Card
    fun orderFlowTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "BUYERS VS SELLERS LIVE ORDER FLOW"
        AppLanguage.HINDI -> "खरीदार बनाम विक्रेता लाइव ऑर्डर फ्लो"
        AppLanguage.MARATHI -> "खरेदीदार विरुद्ध विक्रेते लाईव्ह ऑर्डर फ्लो"
    }

    fun buyersStrength(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Buyers Dominance"
        AppLanguage.HINDI -> "खरीदारों की ताकत (Buyers Dominance)"
        AppLanguage.MARATHI -> "खरेदीदारांची ताकद (Buyers Dominance)"
    }

    fun sellersPressure(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Sellers Pressure"
        AppLanguage.HINDI -> "विक्रेताओं का दबाव (Sellers Pressure)"
        AppLanguage.MARATHI -> "विक्रेत्यांचा दबाव (Sellers Pressure)"
    }

    fun orderBookDepth(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "ORDER BOOK DEPTH"
        AppLanguage.HINDI -> "ऑर्डर बुक डेप्थ (Bids & Asks)"
        AppLanguage.MARATHI -> "ऑर्डर बुक डेप्थ (Bids & Asks)"
    }

    fun bidsCount(lang: AppLanguage, count: Int): String = when (lang) {
        AppLanguage.ENGLISH -> "$count Active Bids (Buy Orders)"
        AppLanguage.HINDI -> "$count एक्टिव बिड्स (बाय ऑर्डर्स)"
        AppLanguage.MARATHI -> "$count सक्रिय बिड्स (बाय ऑर्डर्स)"
    }

    fun asksCount(lang: AppLanguage, count: Int): String = when (lang) {
        AppLanguage.ENGLISH -> "$count Active Asks (Sell Orders)"
        AppLanguage.HINDI -> "$count एक्टिव आस्कस (सेल ऑर्डर्स)"
        AppLanguage.MARATHI -> "$count सक्रिय आस्कस (सेल ऑर्डर्स)"
    }

    fun netDelta(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Net Volume Delta"
        AppLanguage.HINDI -> "नेट वॉल्यूम डेल्टा"
        AppLanguage.MARATHI -> "निव्वळ व्हॉल्यूम डेल्टा"
    }

    // Market Sessions
    fun marketSessionsTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "GLOBAL MARKET SESSIONS"
        AppLanguage.HINDI -> "ग्लोबल मार्केट सेशन्स"
        AppLanguage.MARATHI -> "जागतिक मार्केट सेशन्स"
    }

    fun liveCandleChart(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "PROFESSIONAL CANDLESTICK CHART"
        AppLanguage.HINDI -> "प्रोफेशनल कैंडलस्टिक चार्ट"
        AppLanguage.MARATHI -> "व्यावसायिक कँडलस्टिक चार्ट"
    }

    // Dialogs
    fun lotCalculatorTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Lot & Risk Calculator"
        AppLanguage.HINDI -> "लॉट और रिस्क कैलकुलेटर"
        AppLanguage.MARATHI -> "लॉट आणि रिस्क कॅल्क्युलेटर"
    }

    fun priceAlertTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Gold Price Alert"
        AppLanguage.HINDI -> "गोल्ड प्राइस अलर्ट"
        AppLanguage.MARATHI -> "सोन्याचे दर अलर्ट"
    }

    fun themeSelectorTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Customize VIP Terminal Theme"
        AppLanguage.HINDI -> "VIP टर्मिनल थीम चुनें"
        AppLanguage.MARATHI -> "VIP टर्मिनल थीम निवडा"
    }

    fun logoSelectorTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Select VIP Brand Emblem"
        AppLanguage.HINDI -> "VIP ब्रांड प्रतीक चुनें"
        AppLanguage.MARATHI -> "VIP ब्रँड चिन्ह निवडा"
    }

    // Dual Column Switcher
    fun dualModeBoth(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "BOTH COLUMNS"
        AppLanguage.HINDI -> "दोनों साथ में"
        AppLanguage.MARATHI -> "दोन्ही एकत्र"
    }

    fun dualModeBothSub(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Combined View"
        AppLanguage.HINDI -> "संयुक्त दृश्य"
        AppLanguage.MARATHI -> "संयुक्त दृश्य"
    }

    fun dualModePrediction(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "1. PREDICTION"
        AppLanguage.HINDI -> "1. प्रेडिक्शन"
        AppLanguage.MARATHI -> "1. अंदाज"
    }

    fun dualModePredictionSub(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Prediction Focus"
        AppLanguage.HINDI -> "केवल प्रेडिक्शन"
        AppLanguage.MARATHI -> "फक्त अंदाज"
    }

    fun dualModeIndicators(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "2. INDICATORS"
        AppLanguage.HINDI -> "2. इंडिकेटर्स"
        AppLanguage.MARATHI -> "2. इंडिकेटर्स"
    }

    fun dualModeIndicatorsSub(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Indicators Focus"
        AppLanguage.HINDI -> "केवल इंडिकेटर्स"
        AppLanguage.MARATHI -> "फक्त इंडिकेटर्स"
    }

    fun buySignalsLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Buy Signals"
        AppLanguage.HINDI -> "खरीदारी संकेत"
        AppLanguage.MARATHI -> "खरेदी संकेत"
    }

    fun sellSignalsLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Sell Signals"
        AppLanguage.HINDI -> "बिकवाली संकेत"
        AppLanguage.MARATHI -> "विक्री संकेत"
    }

    fun waitSignalsLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Neutral / Hold"
        AppLanguage.HINDI -> "तटस्थ / इंतज़ार"
        AppLanguage.MARATHI -> "तटस्थ / वाट पहा"
    }

    // Dynamic translate helpers for why it happened & lesson learned
    fun translateReason(text: String, lang: AppLanguage): String {
        return when (lang) {
            AppLanguage.ENGLISH -> {
                // If text is already mostly English, return it directly
                val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
                if (!hasDevanagari) {
                    text
                } else {
                    // Translate typical fallback Hindi phrases to clean English
                    text
                        .replace("7 इंडिकेटर ग्रुप्स में बुलिश मोमेंटम की पुष्टि।", "Bullish momentum confirmed across 7 indicator groups.")
                        .replace("इंडिकेटर्स में मंदी और सप्लाई रिजेक्शन की पुष्टि।", "Bearish rejection and supply pressure confirmed across indicators.")
                        .replace("मार्केट अनिश्चित रेंज में फंसा है।", "Market is in sideways consolidation range.")
                        .replace("सपोर्ट", "support")
                        .replace("रेजिस्टेंस", "resistance")
                        .replace("खरीदारी", "buying")
                        .replace("बिकवाली", "selling")
                }
            }
            AppLanguage.HINDI -> text
            AppLanguage.MARATHI -> {
                val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
                if (!hasDevanagari) {
                    text
                } else {
                    text
                        .replace("खरीदारी", "खरेदी")
                        .replace("बिकवाली", "विक्री")
                        .replace("संभावना", "शक्यता")
                        .replace("हुआ", "झाले")
                        .replace("होगा", "होईल")
                        .replace("किया गया", "केले गेले")
                        .replace("सुधार", "सुधारणा")
                        .replace("गलती", "चूक")
                }
            }
        }
    }
}
