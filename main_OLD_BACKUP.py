import os
import requests
import pandas as pd
import numpy as np
from datetime import datetime

API_KEY = os.getenv("TWELVE_DATA_API_KEY")

print("=" * 45)
print("     LIVE GOLD AI - XAUUSD ENGINE")
print("=" * 45)

url = "https://api.twelvedata.com/time_series"

params = {
    "symbol": "XAU/USD",
    "interval": "4h",
    "outputsize": 100,
    "apikey": API_KEY
}

r = requests.get(url, params=params, timeout=15)
data = r.json()

if "values" not in data:
    print("API ERROR:", data)
    raise SystemExit

df = pd.DataFrame(data["values"])

for col in ["open", "high", "low", "close"]:
    df[col] = pd.to_numeric(df[col])

df = df.iloc[::-1].reset_index(drop=True)

print("DATA CONNECTED")
print("Candles:", len(df))
print("Latest XAUUSD:", df["close"].iloc[-1])
print("Time:", datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
print("Time:", datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
print("=" * 45)
# ===== INDICATORS =====

df["ema9"] = df["close"].ewm(span=9, adjust=False).mean()
df["ema21"] = df["close"].ewm(span=21, adjust=False).mean()
df["ema50"] = df["close"].ewm(span=50, adjust=False).mean()

delta = df["close"].diff()
gain = delta.clip(lower=0)
loss = -delta.clip(upper=0)

avg_gain = gain.ewm(alpha=1/14, adjust=False).mean()
avg_loss = loss.ewm(alpha=1/14, adjust=False).mean()

rs = avg_gain / avg_loss
df["rsi14"] = 100 - (100 / (1 + rs))

last = df.iloc[-1]

print("EMA 9 :", round(last["ema9"], 2))
print("EMA 21:", round(last["ema21"], 2))
print("RSI 14:", round(last["rsi14"], 2))

if last["ema9"] > last["ema21"] and last["close"] > last["ema50"]:
    signal = "BUY"
elif last["ema9"] < last["ema21"] and last["close"] < last["ema50"]:
    signal = "SELL"
else:
    signal = "WAIT"

print("=" * 45)
print("SIGNAL:", signal)
print("=" * 45)
# ===== ATR 14 =====
prev_close = df["close"].shift(1)

tr = pd.concat([
    df["high"] - df["low"],
    (df["high"] - prev_close).abs(),
    (df["low"] - prev_close).abs()
], axis=1).max(axis=1)

df["atr14"] = tr.ewm(alpha=1/14, adjust=False).mean()

last = df.iloc[-1]

print("ATR 14:", round(last["atr14"], 2))
# ===== TRADE LEVELS =====

entry = last["close"]
atr = last["atr14"]

if signal == "BUY":
    sl = entry - (1.5 * atr)
    tp1 = entry + (1.5 * atr)
    tp2 = entry + (3.0 * atr)

elif signal == "SELL":
    sl = entry + (1.5 * atr)
    tp1 = entry - (1.5 * atr)
    tp2 = entry - (3.0 * atr)

else:
    sl = tp1 = tp2 = None

print("=" * 45)

if signal in ["BUY", "SELL"]:
    print("ENTRY :", round(entry, 2))
    print("SL    :", round(sl, 2))
    print("TP1   :", round(tp1, 2))
    print("TP2   :", round(tp2, 2))
else:
    print("NO TRADE LEVELS - WAIT")

print("=" * 45)
# ===== MACD + MOMENTUM + CONFIDENCE =====

ema12 = df["close"].ewm(span=12, adjust=False).mean()
ema26 = df["close"].ewm(span=26, adjust=False).mean()

df["macd"] = ema12 - ema26
df["macd_signal"] = df["macd"].ewm(span=9, adjust=False).mean()

last = df.iloc[-1]

bull_score = 0
bear_score = 0

# EMA trend
if last["ema9"] > last["ema21"]:
    bull_score += 1
else:
    bear_score += 1

# RSI momentum
if last["rsi14"] >= 55:
    bull_score += 1
elif last["rsi14"] <= 45:
    bear_score += 1

# MACD confirmation
if last["macd"] > last["macd_signal"]:
    bull_score += 1
else:
    bear_score += 1

# Candle momentum
if last["close"] > last["open"]:
    bull_score += 1
elif last["close"] < last["open"]:
    bear_score += 1

if bull_score >= 3:
    final_signal = "BUY"
    confidence = bull_score * 25
elif bear_score >= 3:
    final_signal = "SELL"
    confidence = bear_score * 25
else:
    final_signal = "WAIT"
    confidence = max(bull_score, bear_score) * 25

print("MACD       :", round(last["macd"], 2))
print("MACD SIGNAL:", round(last["macd_signal"], 2))
print("BULL SCORE :", bull_score, "/ 4")
print("BEAR SCORE :", bear_score, "/ 4")
print("FINAL      :", final_signal)
print("CONFIDENCE :", str(confidence) + "%")
print("=" * 45)
# ===== STRONG SIGNAL FILTER =====

ema_gap = abs(last["ema9"] - last["ema21"])
atr_now = last["atr14"]

strong_trend = ema_gap >= (0.20 * atr_now)

body = abs(last["close"] - last["open"])
candle_range = last["high"] - last["low"]

if candle_range > 0:
    body_ratio = body / candle_range
else:
    body_ratio = 0

strong_candle = body_ratio >= 0.50

filtered_signal = final_signal

if final_signal in ["BUY", "SELL"]:
    if not strong_trend:
        filtered_signal = "WAIT"
    elif not strong_candle:
        filtered_signal = "WAIT"

print("TREND GAP  :", round(ema_gap, 2))
print("BODY RATIO :", round(body_ratio * 100, 1), "%")
print("FILTERED   :", filtered_signal)
print("=" * 45)
# ===== PANDAS MULTI-TIMEFRAME ENGINE =====

def mtf_from_1m():
    global mtf_signal, mtf_confidence

    params_1m = {
        "symbol": "XAU/USD",
        "interval": "1min",
        "outputsize": 1000,
        "apikey": API_KEY
    }

    try:
        r1 = requests.get(
            "https://api.twelvedata.com/time_series",
            params=params_1m,
            timeout=20
        )
        d1 = r1.json()
    except Exception as e:
        print("MTF API ERROR:", e)
        return

    if "values" not in d1:
        print("MTF DATA ERROR:", d1)
        return

    base = pd.DataFrame(d1["values"])

    base["datetime"] = pd.to_datetime(base["datetime"])

    for c in ["open", "high", "low", "close"]:
        base[c] = pd.to_numeric(base[c], errors="coerce")

    base = (
        base
        .sort_values("datetime")
        .set_index("datetime")
    )

    # Remove current/incomplete 1-minute candle
    if len(base) > 1:
        base = base.iloc[:-1]

    def analyse_tf(minutes):

        rule = f"{minutes}min"

        tf = base.resample(
            rule,
            label="right",
            closed="right"
        ).agg({
            "open": "first",
            "high": "max",
            "low": "min",
            "close": "last"
        }).dropna()

        # Don't analyse incomplete resampled candle
        if len(tf) > 1:
            tf = tf.iloc[:-1]

        if len(tf) < 30:
            return "WAIT", 0, 0

        tf["ema9"] = tf["close"].ewm(
            span=9,
            adjust=False
        ).mean()

        tf["ema21"] = tf["close"].ewm(
            span=21,
            adjust=False
        ).mean()

        delta = tf["close"].diff()

        gain = delta.clip(lower=0)
        loss = -delta.clip(upper=0)

        avg_gain = gain.ewm(
            alpha=1/14,
            adjust=False
        ).mean()

        avg_loss = loss.ewm(
            alpha=1/14,
            adjust=False
        ).mean()

        rs = avg_gain / avg_loss

        tf["rsi14"] = 100 - (
            100 / (1 + rs)
        )

        ema12 = tf["close"].ewm(
            span=12,
            adjust=False
        ).mean()

        ema26 = tf["close"].ewm(
            span=26,
            adjust=False
        ).mean()

        tf["macd"] = ema12 - ema26

        tf["macd_signal"] = tf["macd"].ewm(
            span=9,
            adjust=False
        ).mean()

        x = tf.iloc[-1]

        bull = 0
        bear = 0

        # EMA trend
        if x["ema9"] > x["ema21"]:
            bull += 1
        elif x["ema9"] < x["ema21"]:
            bear += 1

        # RSI momentum
        if x["rsi14"] >= 55:
            bull += 1
        elif x["rsi14"] <= 45:
            bear += 1

        # MACD momentum
        if x["macd"] > x["macd_signal"]:
            bull += 1
        elif x["macd"] < x["macd_signal"]:
            bear += 1

        # Candle direction
        if x["close"] > x["open"]:
            bull += 1
        elif x["close"] < x["open"]:
            bear += 1

        if bull >= 3:
            direction = "BUY"

        elif bear >= 3:
            direction = "SELL"

        else:
            direction = "WAIT"

        return direction, bull, bear


    timeframes = [
        2, 3, 5, 7,
        10, 12, 15, 30
    ]

    buy_count = 0
    sell_count = 0
    wait_count = 0

    print("=" * 45)
    print("PANDAS MULTI-TIMEFRAME CONFIRMATION")
    print("=" * 45)

    for minutes in timeframes:

        direction, bull, bear = analyse_tf(minutes)

        print(
            f"{minutes}m : {direction}"
            f" | Bull {bull}"
            f" | Bear {bear}"
        )

        if direction == "BUY":
            buy_count += 1

        elif direction == "SELL":
            sell_count += 1

        else:
            wait_count += 1


    # ===== FINAL MTF VOTING =====

    if buy_count >= 7:
        mtf_signal = "STRONG BUY"

    elif sell_count >= 7:
        mtf_signal = "STRONG SELL"

    elif buy_count >= 5:
        mtf_signal = "BUY"

    elif sell_count >= 5:
        mtf_signal = "SELL"

    else:
        mtf_signal = "WAIT"


    dominant = max(
        buy_count,
        sell_count
    )

    mtf_confidence = (
        dominant / len(timeframes)
    ) * 100


    print("=" * 45)

    print("BUY TFs     :", buy_count)
    print("SELL TFs    :", sell_count)
    print("WAIT TFs    :", wait_count)

    print("MTF SIGNAL  :", mtf_signal)

    print(
        "MTF CONF    :",
        round(mtf_confidence, 1),
        "%"
    )

    print("=" * 45)


mtf_from_1m()
# ===== MASTER TRADE DECISION =====

if filtered_signal == "BUY" and mtf_signal in ["BUY", "STRONG BUY"]:
    master_signal = "BUY"

elif filtered_signal == "SELL" and mtf_signal in ["SELL", "STRONG SELL"]:
    master_signal = "SELL"

else:
    master_signal = "WAIT"

print("MASTER TRADE :", master_signal)
print("=" * 45)
# ===== MASTER TRADE LEVELS =====

print("=" * 45)

if master_signal == "BUY":
    master_entry = last["close"]
    master_sl = master_entry - (1.5 * last["atr14"])
    master_tp1 = master_entry + (1.5 * last["atr14"])
    master_tp2 = master_entry + (3.0 * last["atr14"])

    print("MASTER TRADE : BUY")
    print("ENTRY        :", round(master_entry, 2))
    print("SL           :", round(master_sl, 2))
    print("TP1          :", round(master_tp1, 2))
    print("TP2          :", round(master_tp2, 2))
    print("MTF CONF     :", round(mtf_confidence, 1), "%")

elif master_signal == "SELL":
    master_entry = last["close"]
    master_sl = master_entry + (1.5 * last["atr14"])
    master_tp1 = master_entry - (1.5 * last["atr14"])
    master_tp2 = master_entry - (3.0 * last["atr14"])

    print("MASTER TRADE : SELL")
    print("ENTRY        :", round(master_entry, 2))
    print("SL           :", round(master_sl, 2))
    print("TP1          :", round(master_tp1, 2))
    print("TP2          :", round(master_tp2, 2))
    print("MTF CONF     :", round(mtf_confidence, 1), "%")

else:
    print("MASTER TRADE : WAIT")
    print("NO TRADE")

print("=" * 45)
import subprocess

if master_signal in ["BUY", "SELL"]:
    title = f"Gold AI {master_signal}"

    message = (
        f"XAUUSD {master_signal} | "
        f"Entry {round(master_entry, 2)} | "
        f"SL {round(master_sl, 2)} | "
        f"TP1 {round(master_tp1, 2)} | "
        f"TP2 {round(master_tp2, 2)} | "
        f"MTF {round(mtf_confidence, 1)}%"
    )

    subprocess.run([
        "termux-notification",
        "--id", "gold-ai-signal",
        "--title", title,
        "--content", message,
        "--priority", "high"
    ])
# ===== DUPLICATE SIGNAL LOCK =====
signal_file = ".last_signal"

try:
    with open(signal_file, "r") as f:
        last_sent_signal = f.read().strip()
except FileNotFoundError:
    last_sent_signal = ""

if master_signal in ["BUY", "SELL"] and master_signal != last_sent_signal:
    with open(signal_file, "w") as f:
        f.write(master_signal)
if master_signal in ["BUY", "SELL"] and master_signal != last_sent_signal:
    with open(signal_file, "w") as f:
        f.write(master_signal)

import subprocess

signal_file = ".last_signal"

try:
    with open(signal_file, "r") as f:
        last_sent_signal = f.read().strip()
except FileNotFoundError:
    last_sent_signal = ""

if master_signal in ["BUY", "SELL"] and master_signal != last_sent_signal:
    title = f"Gold AI {master_signal}"

    message = (
        f"XAUUSD {master_signal} | "
        f"Entry {round(master_entry, 2)} | "
        f"SL {round(master_sl, 2)} | "
        f"TP1 {round(master_tp1, 2)} | "
        f"TP2 {round(master_tp2, 2)} | "
        f"MTF {round(mtf_confidence, 1)}%"
    )

    subprocess.run([
        "termux-notification",
        "--id", "gold-ai-signal",
        "--title", title,
        "--content", message,
        "--priority", "high"
    ])

    with open(signal_file, "w") as f:
        f.write(master_signal)


# ===== BRANDED APP UI (IMPROVED) =====
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.gridlayout import GridLayout
from kivy.core.window import Window

Window.clearcolor = (0.05, 0.05, 0.05, 1)

class NonnyApp(App):
    def build(self):
        layout = BoxLayout(orientation="vertical", padding=30, spacing=15)

        big_label = Label(
            text="Nonny",
            font_size="50sp",
            bold=True,
            size_hint=(1, 0.2),
            color=(1, 0.84, 0, 1)
        )
        small_label = Label(
            text="Gold AI FX",
            font_size="20sp",
            size_hint=(1, 0.08),
            color=(0.8, 0.8, 0.8, 1)
        )

        signal_color = (0.2, 1, 0.2, 1) if master_signal == "BUY" else (1, 0.3, 0.3, 1) if master_signal == "SELL" else (1, 1, 0.3, 1)
        disclaimer_label = Label(
            text="Experimental tool - not financial advice. Trade at your own risk.",
            font_size="13sp",
            size_hint=(1, 0.08),
            color=(0.9, 0.6, 0.2, 1)
        )
        signal_label = Label(
            text=f"SIGNAL: {master_signal}",
            font_size="34sp",
            bold=True,
            size_hint=(1, 0.15),
            color=signal_color
        )

        details_grid = GridLayout(cols=2, size_hint=(1, 0.4), spacing=8)

        def add_row(label_text, value_text):
            details_grid.add_widget(Label(text=label_text, font_size="18sp", color=(0.7,0.7,0.7,1), halign="left"))
            details_grid.add_widget(Label(text=value_text, font_size="18sp", bold=True, color=(1,1,1,1), halign="right"))

        if master_signal in ("BUY", "SELL"):
            add_row("Entry:", f"{master_entry:.2f}")
            add_row("Stop Loss:", f"{master_sl:.2f}")
            add_row("Take Profit 1:", f"{master_tp1:.2f}")
            if master_signal == "BUY":
                add_row("Take Profit 2:", f"{master_tp2:.2f}")
        else:
            add_row("Status:", "No Trade")

        add_row("MTF Confidence:", f"{mtf_confidence:.1f}%")

        layout.add_widget(big_label)
        layout.add_widget(small_label)
        layout.add_widget(disclaimer_label)
        layout.add_widget(signal_label)
        layout.add_widget(details_grid)
        return layout

if __name__ == "__main__":
    NonnyApp().run()
