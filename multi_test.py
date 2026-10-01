import json
import pandas as pd
import os
import requests

API_KEY = os.getenv("TWELVE_DATA_API_KEY")

def fetch_data(symbol, interval, size=5000):
    url = "https://api.twelvedata.com/time_series"
    params = {"symbol": symbol, "interval": interval, "outputsize": size, "apikey": API_KEY, "order": "ASC"}
    r = requests.get(url, params=params)
    data = r.json()
    if "values" not in data:
        print("API ERROR:", data)
        exit()
    df = pd.DataFrame(data["values"])
    df = df.rename(columns={"datetime": "time"})
    for col in ["open", "high", "low", "close"]:
        df[col] = df[col].astype(float)
    return df

print("Loading gold data (cached if possible)...")
try:
    data = json.load(open("full_data.json"))
    df = pd.DataFrame(data["values"])
    df = df.rename(columns={"datetime": "time"})
    for col in ["open", "high", "low", "close"]:
        df[col] = df[col].astype(float)
    df = df.iloc[::-1].reset_index(drop=True)
except:
    df = fetch_data("XAU/USD", "15min", 5000)

print("Fetching USD data...")
df_usd = fetch_data("USDX", "15min", 5000)

df["ema9"] = df["close"].ewm(span=9, adjust=False).mean()
df["ema21"] = df["close"].ewm(span=21, adjust=False).mean()
df["ema50"] = df["close"].ewm(span=50, adjust=False).mean()
df["ema200"] = df["close"].ewm(span=200, adjust=False).mean()

delta = df["close"].diff()
gain = delta.clip(lower=0)
loss = -delta.clip(upper=0)
avg_gain = gain.ewm(alpha=1/14, adjust=False).mean()
avg_loss = loss.ewm(alpha=1/14, adjust=False).mean()
rs = avg_gain / avg_loss
df["rsi14"] = 100 - (100 / (1 + rs))

ema12 = df["close"].ewm(span=12, adjust=False).mean()
ema26 = df["close"].ewm(span=26, adjust=False).mean()
df["macd"] = ema12 - ema26
df["macd_signal"] = df["macd"].ewm(span=9, adjust=False).mean()

prev_close = df["close"].shift(1)
tr = pd.concat([df["high"]-df["low"], (df["high"]-prev_close).abs(), (df["low"]-prev_close).abs()], axis=1).max(axis=1)
df["atr14"] = tr.ewm(alpha=1/14, adjust=False).mean()

df_usd["usdx_ema21"] = df_usd["close"].ewm(span=21, adjust=False).mean()
df_usd["usd_falling"] = df_usd["close"] < df_usd["usdx_ema21"]
df_usd_trend = df_usd[["time", "usd_falling"]].copy()
df = df.merge(df_usd_trend, on="time", how="left")
df["usd_falling"] = df["usd_falling"].ffill()

def run_backtest(name, buy_cond, sell_cond, rr_sl=1.5, rr_tp=2.0):
    df["signal"] = "WAIT"
    df.loc[buy_cond, "signal"] = "BUY"
    df.loc[sell_cond, "signal"] = "SELL"

    wins = losses = no_result = 0
    for i in range(200, len(df) - 1):
        row = df.iloc[i]
        if row["signal"] == "WAIT":
            continue
        entry = row["close"]
        atr = row["atr14"]
        if row["signal"] == "BUY":
            sl = entry - (rr_sl * atr)
            tp1 = entry + (rr_tp * atr)
        else:
            sl = entry + (rr_sl * atr)
            tp1 = entry - (rr_tp * atr)
        outcome = None
        for j in range(i + 1, min(i + 20, len(df))):
            future = df.iloc[j]
            if row["signal"] == "BUY":
                if future["low"] <= sl:
                    outcome = "LOSS"; break
                if future["high"] >= tp1:
                    outcome = "WIN"; break
            else:
                if future["high"] >= sl:
                    outcome = "LOSS"; break
                if future["low"] <= tp1:
                    outcome = "WIN"; break
        if outcome == "WIN":
            wins += 1
        elif outcome == "LOSS":
            losses += 1
        else:
            no_result += 1

    total = wins + losses
    winrate = (wins / total * 100) if total > 0 else 0
    rr = rr_tp / rr_sl
    ev = (winrate/100*rr) - ((1-winrate/100)*1) if total > 0 else 0
    print(f"{name:40s} | Signals: {total+no_result:4d} | WinRate: {winrate:5.2f}% | EV: {ev:+.3f}")

print("=" * 100)
print(f"{'STRATEGY':40s} | {'SIGNALS':>12s} | {'WINRATE':>8s} | {'EV(R)':>7s}")
print("=" * 100)

# Test 1: RSI only, simple
run_backtest("1. RSI only (55/45)",
    (df["rsi14"] >= 55), (df["rsi14"] <= 45))

# Test 2: EMA crossover only
run_backtest("2. EMA9/21 crossover only",
    (df["ema9"] > df["ema21"]), (df["ema9"] < df["ema21"]))

# Test 3: EMA200 trend + RSI extreme (mean reversion against trend)
run_backtest("3. EMA200 trend + RSI pullback",
    (df["close"] > df["ema200"]) & (df["rsi14"] <= 40),
    (df["close"] < df["ema200"]) & (df["rsi14"] >= 60))

# Test 4: MACD cross only
run_backtest("4. MACD cross only",
    (df["macd"] > df["macd_signal"]), (df["macd"] < df["macd_signal"]))

# Test 5: Better R:R (1:2 with wider SL)
run_backtest("5. EMA+RSI, wider SL (2.0/3.0)",
    (df["ema9"] > df["ema21"]) & (df["rsi14"] >= 55),
    (df["ema9"] < df["ema21"]) & (df["rsi14"] <= 45),
    rr_sl=2.0, rr_tp=3.0)

# Test 6: Strong trend filter (EMA9>21>50>200 alignment)
run_backtest("6. Full EMA alignment (9>21>50>200)",
    (df["ema9"] > df["ema21"]) & (df["ema21"] > df["ema50"]) & (df["ema50"] > df["ema200"]),
    (df["ema9"] < df["ema21"]) & (df["ema21"] < df["ema50"]) & (df["ema50"] < df["ema200"]))

# Test 7: USD filter only (no technical)
run_backtest("7. USD filter only",
    (df["usd_falling"] == True), (df["usd_falling"] == False))

print("=" * 100)
