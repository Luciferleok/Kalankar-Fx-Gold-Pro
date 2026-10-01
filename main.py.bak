import os
import logging
import threading
logging.getLogger("urllib3").setLevel(logging.WARNING)
logging.getLogger("requests").setLevel(logging.WARNING)
import requests
import pandas as pd
import numpy as np

from kivy.app import App
from kivy.uix.scrollview import ScrollView
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.label import Label
from kivy.uix.button import Button
from kivy.clock import Clock

from sentiment_signal import get_sentiment_signal
from signal_logger import log_signal, export_log, share_log

API_KEY = "8e1493529b8e42d9b0a9e557c3451db0"
SYMBOL = "XAU/USD"


def fetch_data(interval="4h", size=100):
    url = "https://api.twelvedata.com/time_series"
    params = {"symbol": SYMBOL, "interval": interval, "outputsize": size, "apikey": API_KEY, "order": "ASC"}
    r = requests.get(url, params=params, timeout=15)
    data = r.json()
    if "values" not in data:
        return None
    df = pd.DataFrame(data["values"])
    for col in ["open", "high", "low", "close"]:
        df[col] = pd.to_numeric(df[col])
    return df


def add_all_indicators(df):
    df["ema9"] = df["close"].ewm(span=9, adjust=False).mean()
    df["ema21"] = df["close"].ewm(span=21, adjust=False).mean()
    df["ema50"] = df["close"].ewm(span=50, adjust=False).mean()

    ema12 = df["close"].ewm(span=12, adjust=False).mean()
    ema26 = df["close"].ewm(span=26, adjust=False).mean()
    df["macd"] = ema12 - ema26
    df["macd_signal"] = df["macd"].ewm(span=9, adjust=False).mean()

    delta = df["close"].diff()
    gain = delta.clip(lower=0)
    loss = -delta.clip(upper=0)
    avg_gain = gain.ewm(alpha=1/14, adjust=False).mean()
    avg_loss = loss.ewm(alpha=1/14, adjust=False).mean()
    rs = avg_gain / avg_loss
    df["rsi14"] = 100 - (100 / (1 + rs))

    prev_close = df["close"].shift(1)
    tr = pd.concat([df["high"]-df["low"], (df["high"]-prev_close).abs(), (df["low"]-prev_close).abs()], axis=1).max(axis=1)
    df["atr14"] = tr.ewm(alpha=1/14, adjust=False).mean()

    plus_dm = df["high"].diff()
    minus_dm = -df["low"].diff()
    plus_dm[plus_dm < 0] = 0
    minus_dm[minus_dm < 0] = 0
    plus_dm[(plus_dm - minus_dm) < 0] = 0
    minus_dm[(minus_dm - plus_dm) < 0] = 0
    tr14 = tr.ewm(alpha=1/14, adjust=False).mean()
    plus_di = 100 * (plus_dm.ewm(alpha=1/14, adjust=False).mean() / tr14)
    minus_di = 100 * (minus_dm.ewm(alpha=1/14, adjust=False).mean() / tr14)
    dx = 100 * (plus_di - minus_di).abs() / (plus_di + minus_di)
    df["adx14"] = dx.ewm(alpha=1/14, adjust=False).mean()
    df["plus_di"] = plus_di
    df["minus_di"] = minus_di

    df["sar_bull"] = df["close"] > df["low"].rolling(5).min()

    low14 = df["low"].rolling(14).min()
    high14 = df["high"].rolling(14).max()
    df["stoch_k"] = 100 * (df["close"] - low14) / (high14 - low14)

    tp = (df["high"] + df["low"] + df["close"]) / 3
    sma_tp = tp.rolling(20).mean()
    mad = tp.rolling(20).apply(lambda x: np.abs(x - x.mean()).mean())
    df["cci"] = (tp - sma_tp) / (0.015 * mad)

    df["williams_r"] = -100 * (high14 - df["close"]) / (high14 - low14)
    df["roc"] = df["close"].pct_change(periods=10) * 100

    sma20 = df["close"].rolling(20).mean()
    std20 = df["close"].rolling(20).std()
    df["bb_upper"] = sma20 + (2 * std20)
    df["bb_lower"] = sma20 - (2 * std20)
    df["bb_mid"] = sma20
    df["std20"] = std20

    df["kc_upper"] = df["ema21"] + (2 * df["atr14"])
    df["kc_lower"] = df["ema21"] - (2 * df["atr14"])

    df["pivot"] = (df["high"].shift(1) + df["low"].shift(1) + df["close"].shift(1)) / 3
    df["r1"] = (2 * df["pivot"]) - df["low"].shift(1)
    df["s1"] = (2 * df["pivot"]) - df["high"].shift(1)

    return df


def decide(votes):
    buy = votes.count("BUY")
    sell = votes.count("SELL")
    if buy > sell:
        return "BUY"
    elif sell > buy:
        return "SELL"
    else:
        return "WAIT"


def get_full_analysis():
    df = fetch_data("4h", 100)
    if df is None:
        return None
    df = add_all_indicators(df)
    last = df.iloc[-1]
    prev = df.iloc[-2]

    items = {}

    items["trend"] = {
        "EMA9 vs EMA21": "BUY" if last["ema9"] > last["ema21"] else "SELL",
        "Close vs EMA50": "BUY" if last["close"] > last["ema50"] else "SELL",
        "MACD vs Signal": "BUY" if last["macd"] > last["macd_signal"] else "SELL",
        "ADX/DI": ("BUY" if last["plus_di"] > last["minus_di"] else "SELL") if last["adx14"] > 20 else "WAIT",
        "Parabolic SAR": "BUY" if last["sar_bull"] else "SELL",
    }

    items["momentum"] = {
        "RSI14": "BUY" if last["rsi14"]>=55 else ("SELL" if last["rsi14"]<=45 else "WAIT"),
        "Stochastic %K": "BUY" if last["stoch_k"]<20 else ("SELL" if last["stoch_k"]>80 else "WAIT"),
        "CCI": "BUY" if last["cci"]<-100 else ("SELL" if last["cci"]>100 else "WAIT"),
        "Williams %R": "BUY" if last["williams_r"]<-80 else ("SELL" if last["williams_r"]>-20 else "WAIT"),
        "ROC": "BUY" if last["roc"]>0 else "SELL",
    }

    lower_std = last["bb_mid"] - (2*last["std20"])
    upper_std = last["bb_mid"] + (2*last["std20"])
    items["volatility"] = {
        "Bollinger Bands": "BUY" if last["close"]<last["bb_lower"] else ("SELL" if last["close"]>last["bb_upper"] else "WAIT"),
        "Keltner Channel": "BUY" if last["close"]<last["kc_lower"] else ("SELL" if last["close"]>last["kc_upper"] else "WAIT"),
        "Std Dev Bands": "BUY" if last["close"]<lower_std else ("SELL" if last["close"]>upper_std else "WAIT"),
    }

    items["sr"] = {
        "Pivot Point": "BUY" if last["close"]>last["pivot"] else "SELL",
        "R1/S1 Levels": "BUY" if last["close"]<last["s1"] else ("SELL" if last["close"]>last["r1"] else "WAIT"),
    }

    body = abs(last["close"] - last["open"])
    upper_wick = last["high"] - max(last["close"], last["open"])
    lower_wick = min(last["close"], last["open"]) - last["low"]

    if prev["close"] < prev["open"] and last["close"] > last["open"] and last["close"] > prev["open"] and last["open"] < prev["close"]:
        engulf = "BUY"
    elif prev["close"] > prev["open"] and last["close"] < last["open"] and last["open"] > prev["close"] and last["close"] < prev["open"]:
        engulf = "SELL"
    else:
        engulf = "WAIT"

    if lower_wick > 2*body and upper_wick < body:
        wick_pattern = "BUY"
    elif upper_wick > 2*body and lower_wick < body:
        wick_pattern = "SELL"
    else:
        wick_pattern = "WAIT"

    if body < (last["high"]-last["low"])*0.1:
        doji = "WAIT"
    else:
        doji = "BUY" if last["close"] > last["open"] else "SELL"

    if body < (last["high"]-last["low"])*0.1:
        if prev["close"] < prev["open"]:
            real_doji = "BUY"
        elif prev["close"] > prev["open"]:
            real_doji = "SELL"
        else:
            real_doji = "WAIT"
    else:
        real_doji = "WAIT"

    if lower_wick > 3*body and upper_wick < body:
        pin_bar = "BUY"
    elif upper_wick > 3*body and lower_wick < body:
        pin_bar = "SELL"
    else:
        pin_bar = "WAIT"

    if last["high"] < prev["high"] and last["low"] > prev["low"]:
        if prev["close"] > prev["open"]:
            inside_bar = "BUY"
        elif prev["close"] < prev["open"]:
            inside_bar = "SELL"
        else:
            inside_bar = "WAIT"
    else:
        inside_bar = "WAIT"

    items["candlestick"] = {
        "Engulfing Pattern": engulf,
        "Hammer/Shooting Star": wick_pattern,
        "Candle Direction": doji,
        "Doji Pattern": real_doji,
        "Pin Bar": pin_bar,
        "Inside Bar": inside_bar,
    }

    try:
        sentiment_result = get_sentiment_signal()
        sentiment_sig = sentiment_result["signal"]
        sentiment_score = sentiment_result["avg_sentiment"]
        sentiment_count = sentiment_result["article_count"]
    except Exception:
        sentiment_sig = "WAIT"
        sentiment_score = 0.0
        sentiment_count = 0

    items["sentiment"] = {
        f"News Sentiment ({sentiment_count} articles)": sentiment_sig,
        f"Avg Score: {sentiment_score:.4f}": sentiment_sig,
    }

    groups = {}
    for key, indicators in items.items():
        groups[key] = decide(list(indicators.values()))

    return {
        "price": last["close"],
        "items": items,
        "groups": groups,
    }


class NonnyApp(App):
    def build(self):
        self.root_scroll = ScrollView(size_hint=(1, 1))
        self.main_layout = BoxLayout(orientation="vertical", size_hint_y=None, spacing=10, padding=15)
        self.main_layout.bind(minimum_height=self.main_layout.setter("height"))
        self.root_scroll.add_widget(self.main_layout)

        self.add_label("Loading data...", size=18, height=30)
        Clock.schedule_once(lambda dt: self.refresh_ui(), 0.5)
        Clock.schedule_interval(lambda dt: self.refresh_ui(), 60)
        return self.root_scroll

    def add_label(self, text, size=16, bold=False, color=(1,1,1,1), height=30):
        lbl = Label(text=text, font_size=f"{size}sp", bold=bold, color=color,
                    size_hint_y=None, halign="left", valign="middle")
        lbl.bind(width=lambda inst, w: setattr(inst, "text_size", (w, None)))
        lbl.bind(texture_size=lambda inst, ts: setattr(inst, "height", ts[1] + 10))
        self.main_layout.add_widget(lbl)

    def color_for(self, signal):
        if signal == "BUY":
            return (0.16, 0.75, 0.38, 1)
        elif signal == "SELL":
            return (0.90, 0.25, 0.28, 1)
        else:
            return (0.93, 0.80, 0.40, 1)

    def add_divider(self):
        self.add_label("-" * 40, size=12, height=16, color=(0.85,0.68,0.22,1))

    def share_signal_log(self, instance=None):
        try:
            ok = share_log(base_dir=self.user_data_dir)
            if not ok:
                print("[main] Nothing to share yet (no log file).")
        except Exception as e:
            print(f"[main] Failed to share log: {e}")

    def refresh_ui(self):
        self.main_layout.clear_widgets()
        self.add_label("Loading data...", size=18, height=30)
        threading.Thread(target=self._fetch_data, daemon=True).start()

    def _fetch_data(self):
        data = get_full_analysis()
        Clock.schedule_once(lambda dt: self._build_ui(data), 0)

    def _build_ui(self, data):
        self.main_layout.clear_widgets()
        if data is None:
            self.add_label("Failed to fetch data. Check connection.", size=18, color=(1,0.3,0.3,1))
            return

        price = data["price"]
        groups = data["groups"]
        items = data["items"]

        self.add_label("KALANKAR FX GOLD PRO", size=22, bold=True, height=40, color=(0.85,0.68,0.22,1))
        self.add_label("By Mr. Rudvay Ujjwal Kalankar", size=13, height=22, color=(0.7,0.7,0.7,1))
        self.add_label(f"XAU/USD Price: {price:.2f}", size=18, bold=True, height=35)

        share_btn = Button(
            text="Share Signal Log",
            size_hint_y=None,
            height=70,
            background_color=(0.16, 0.75, 0.38, 1),
            color=(1, 1, 1, 1),
        )
        share_btn.bind(on_release=self.share_signal_log)
        self.main_layout.add_widget(share_btn)

        self.add_label("=" * 40, size=14, height=20)

        self.add_label("SUMMARY (6 Groups)", size=20, bold=True, height=35, color=(0.85,0.68,0.22,1))

        group_labels = {
            "trend": "Trend",
            "momentum": "Momentum",
            "volatility": "Volatility",
            "sr": "Support/Resistance",
            "candlestick": "Candlestick",
            "sentiment": "News Sentiment",
        }

        buy_count = sum(1 for g in groups.values() if g == "BUY")
        sell_count = sum(1 for g in groups.values() if g == "SELL")
        total = len(groups)

        for key, label in group_labels.items():
            sig = groups[key]
            self.add_label(f"{label}: {sig}", size=18, bold=True, color=self.color_for(sig), height=32)

        if buy_count > sell_count:
            overall = "BUY"
            agreement = (buy_count/total)*100
        elif sell_count > buy_count:
            overall = "SELL"
            agreement = (sell_count/total)*100
        else:
            overall = "WAIT/MIXED"
            agreement = 0

        # Log this signal to history, and also copy it to the app's
        # external files folder (for completeness, even though that
        # folder isn't browsable by other apps on Android 11+).
        try:
            log_signal(groups, overall, agreement, price, base_dir=self.user_data_dir)
            export_log(base_dir=self.user_data_dir)
        except Exception as e:
            print(f"[signal_logger] Failed to log/export signal: {e}")

        self.add_label("-" * 40, size=14, height=20)
        self.add_label(f"OVERALL: {overall}", size=22, bold=True, color=self.color_for(overall), height=40)
        if agreement > 0:
            self.add_label(f"Agreement: {agreement:.0f}% ({max(buy_count,sell_count)}/{total} groups)", size=16, height=28)

        self.add_label("=" * 40, size=14, height=20)
        self.add_label("FULL DETAIL (All Indicators)", size=20, bold=True, height=35, color=(0.85,0.68,0.22,1))

        for key, label in group_labels.items():
            self.add_label(f"{label.upper()}", size=16, bold=True, height=28, color=(0.93,0.80,0.40,1))
            for ind_name, ind_sig in items[key].items():
                self.add_label(f"   {ind_name}", size=14, color=(0.60,0.60,0.66,1), height=20)
                self.add_label(f"      {ind_sig}", size=15, bold=True, color=self.color_for(ind_sig), height=24)
            self.add_label(f"Group Result: {groups[key]}", size=16, bold=True, color=self.color_for(groups[key]), height=28)
            self.add_divider()

        self.add_label("Experimental tool  -  not financial advice", size=12, height=22, color=(0.60,0.60,0.66,1))


if __name__ == "__main__":
    NonnyApp().run()
