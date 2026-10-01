import requests
import pandas as pd
import os

API_KEY = os.getenv("TWELVE_DATA_API_KEY")
SYMBOL = "XAU/USD"
INTERVAL = "4h"

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

print("Fetching gold data...")
df = fetch_data(SYMBOL, INTERVAL, 5000)
print("Fetching USD data...")
df_usd = fetch_data("USDX", INTERVAL, 5000)

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

ema12 = df["close"].ewm(span=12, adjust=False).mean()
ema26 = df["close"].ewm(span=26, adjust=False).mean()
df["macd"] = ema12 - ema26
df["macd_signal"] = df["macd"].ewm(span=9, adjust=False).mean()

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

df_usd["usdx_ema21"] = df_usd["close"].ewm(span=21, adjust=False).mean()
df_usd["usd_falling"] = df_usd["close"] < df_usd["usdx_ema21"]
df_usd_trend = df_usd[["time", "usd_falling"]].copy()
df = df.merge(df_usd_trend, on="time", how="left")
df["usd_falling"] = df["usd_falling"].ffill()

df["signal"] = "WAIT"

buy_cond = (
    (df["ema9"] > df["ema21"]) &
    (df["close"] > df["ema50"])
)
sell_cond = (
    (df["ema9"] < df["ema21"]) &
    (df["close"] < df["ema50"])
)

df.loc[buy_cond, "signal"] = "BUY"
df.loc[sell_cond, "signal"] = "SELL"

wins = 0
losses = 0
no_result = 0

print("Running backtest on", len(df), "candles...")

split_point = len(df) // 2
print(f"Testing SECOND HALF: midpoint to end")
for i in range(split_point, len(df) - 1):
    row = df.iloc[i]
    if row["signal"] == "WAIT":
        continue
    entry = row["close"]
    atr = row["atr14"]
    if row["signal"] == "BUY":
        sl = entry - (1.5 * atr)
        tp1 = entry + (2.0 * atr)
    else:
        sl = entry + (1.5 * atr)
        tp1 = entry - (2.0 * atr)
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

print("=" * 55)
print("BACKTEST (BIG DATA ~52 days) - USD Filter -", SYMBOL, INTERVAL)
print("=" * 55)
print("Total signals tested:", total + no_result)
print("Wins:", wins)
print("Losses:", losses)
print("No clear result:", no_result)
print("Win Rate:", round(winrate, 2), "%")
if total > 0:
    rr = 2.0/1.5
    ev = (winrate/100*rr) - ((1-winrate/100)*1)
    print("Risk:Reward = 1:", round(rr,2))
    print("Expected Value per trade (in R):", round(ev,3))

SPREAD_COST = 0.25
avg_atr = df["atr14"].mean()
spread_in_r = SPREAD_COST / (avg_atr * 1.5)
net_ev = ev - spread_in_r
print("Average ATR14:", round(avg_atr, 3))
print("Estimated spread cost (in R):", round(spread_in_r, 4))
print("Net EV after spread (in R):", round(net_ev, 3))
print("=" * 55)
