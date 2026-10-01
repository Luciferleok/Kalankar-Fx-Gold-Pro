import requests
import pandas as pd
import os
import time

API_KEY = os.getenv("TWELVE_DATA_API_KEY")
SYMBOL = "XAU/USD"

def fetch_data(symbol, interval, size):
    url = "https://api.twelvedata.com/time_series"
    params = {"symbol": symbol, "interval": interval, "outputsize": size, "apikey": API_KEY, "order": "ASC"}
    r = requests.get(url, params=params)
    data = r.json()
    if "values" not in data:
        return None
    df = pd.DataFrame(data["values"])
    df = df.rename(columns={"datetime": "time"})
    for col in ["open", "high", "low", "close"]:
        df[col] = df[col].astype(float)
    return df

def add_indicators(df):
    df["ema9"] = df["close"].ewm(span=9, adjust=False).mean()
    df["ema21"] = df["close"].ewm(span=21, adjust=False).mean()
    delta = df["close"].diff()
    gain = delta.clip(lower=0)
    loss = -delta.clip(upper=0)
    avg_gain = gain.ewm(alpha=1/14, adjust=False).mean()
    avg_loss = loss.ewm(alpha=1/14, adjust=False).mean()
    rs = avg_gain / avg_loss
    df["rsi14"] = 100 - (100 / (1 + rs))

    df["ema50"] = df["close"].ewm(span=50, adjust=False).mean()
    prev_close = df["close"].shift(1)
    tr = pd.concat([df["high"]-df["low"], (df["high"]-prev_close).abs(), (df["low"]-prev_close).abs()], axis=1).max(axis=1)
    df["atr14"] = tr.ewm(alpha=1/14, adjust=False).mean()

    return df

def get_fast_signal():
    df = fetch_data(SYMBOL, "1min", 50)
    if df is None:
        return "N/A", None
    df = add_indicators(df)
    last = df.iloc[-1]
    if last["ema9"] > last["ema21"] and last["rsi14"] >= 55:
        return "BUY", last["close"]
    elif last["ema9"] < last["ema21"] and last["rsi14"] <= 45:
        return "SELL", last["close"]
    else:
        return "WAIT", last["close"]

def get_longhold_signal():
    df = fetch_data(SYMBOL, "1h", 200)
    df_usd = fetch_data("UUP", "1h", 200)
    if df is None or df_usd is None:
        return "N/A", None

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

    df["ema50"] = df["close"].ewm(span=50, adjust=False).mean()
    prev_close = df["close"].shift(1)
    tr = pd.concat([df["high"]-df["low"], (df["high"]-prev_close).abs(), (df["low"]-prev_close).abs()], axis=1).max(axis=1)
    df["atr14"] = tr.ewm(alpha=1/14, adjust=False).mean()

    ema12 = df["close"].ewm(span=12, adjust=False).mean()
    ema26 = df["close"].ewm(span=26, adjust=False).mean()
    df["macd"] = ema12 - ema26
    df["macd_signal"] = df["macd"].ewm(span=9, adjust=False).mean()

    df_usd["uup_ema21"] = df_usd["close"].ewm(span=21, adjust=False).mean()
    usd_falling = df_usd["close"].iloc[-1] < df_usd["uup_ema21"].iloc[-1]

    df_tlt = fetch_data("TLT", "1h", 200)
    tlt_rising = False
    if df_tlt is not None:
        df_tlt["tlt_ema21"] = df_tlt["close"].ewm(span=21, adjust=False).mean()
        tlt_rising = df_tlt["close"].iloc[-1] > df_tlt["tlt_ema21"].iloc[-1]

    last = df.iloc[-1]

    if (last["ema9"] > last["ema21"] and last["close"] > last["ema50"]
        and last["rsi14"] >= 60 and last["macd"] > last["macd_signal"] and usd_falling and tlt_rising):
        return "BUY", last["close"]
    elif (last["ema9"] < last["ema21"] and last["close"] < last["ema50"]
        and last["rsi14"] <= 40 and last["macd"] < last["macd_signal"] and not usd_falling and not tlt_rising):
        return "SELL", last["close"]
    else:
        return "WAIT", last["close"]

print("=" * 55)
print("     LIVE GOLD AI - DUAL SIGNAL ENGINE")
print("=" * 55)

fast_sig, fast_price = get_fast_signal()
print(f"⚡ FAST SIGNAL (1min scalp)   : {fast_sig}   Price: {fast_price}")

long_sig, long_price = get_longhold_signal()
print(f"📈 LONG-HOLD SIGNAL (1h swing): {long_sig}   Price: {long_price}")

def get_4h_signal():
    df = fetch_data(SYMBOL, "4h", 100)
    if df is None:
        return "N/A", None, None, None
    df = add_indicators(df)
    last = df.iloc[-1]
    entry = last["close"]
    atr = last["atr14"]
    if last["ema9"] > last["ema21"] and last["close"] > last["ema50"]:
        sl = entry - (1.5 * atr)
        tp = entry + (2.0 * atr)
        return "BUY", entry, sl, tp
    elif last["ema9"] < last["ema21"] and last["close"] < last["ema50"]:
        sl = entry + (1.5 * atr)
        tp = entry - (2.0 * atr)
        return "SELL", entry, sl, tp
    else:
        return "WAIT", entry, None, None

sig_4h, price_4h, sl_4h, tp_4h = get_4h_signal()
print(f"VALIDATED 4H SIGNAL (backtested): {sig_4h}   Price: {price_4h}")
if sig_4h in ("BUY", "SELL"):
    print(f"   SL: {round(sl_4h,2)}   TP: {round(tp_4h,2)}")

print("=" * 55)
print("NOTE: Fast signal = short-term/noisy. Long-hold = higher confidence, slower.")
print("=" * 55)
