import requests
import pandas as pd
import os

API_KEY = os.getenv("TWELVE_DATA_API_KEY")
SYMBOL = "XAU/USD"
INTERVAL = "15min"
OUTPUTSIZE = 500

url = "https://api.twelvedata.com/time_series"
params = {
    "symbol": SYMBOL,
    "interval": INTERVAL,
    "outputsize": OUTPUTSIZE,
    "apikey": API_KEY,
    "order": "ASC"
}

r = requests.get(url, params=params)
data = r.json()

if "values" not in data:
    print("API ERROR:", data)
    exit()

df = pd.DataFrame(data["values"])
df = df.rename(columns={"datetime": "time"})
for col in ["open", "high", "low", "close"]:
    df[col] = df[col].astype(float)

df["ema9"] = df["close"].ewm(span=9, adjust=False).mean()
df["ema21"] = df["close"].ewm(span=21, adjust=False).mean()

delta = df["close"].diff()
gain = delta.clip(lower=0)
loss = -delta.clip(upper=0)
avg_gain = gain.ewm(alpha=1/14, adjust=False).mean()
avg_loss = loss.ewm(alpha=1/14, adjust=False).mean()
rs = avg_gain / avg_loss
df["rsi14"] = 100 - (100 / (1 + rs))

prev_close = df["close"].shift(1)
tr = pd.concat([
    df["high"] - df["low"],
    (df["high"] - prev_close).abs(),
    (df["low"] - prev_close).abs()
], axis=1).max(axis=1)
df["atr14"] = tr.ewm(alpha=1/14, adjust=False).mean()

df["signal"] = "WAIT"
df.loc[(df["ema9"] > df["ema21"]) & (df["rsi14"] >= 55), "signal"] = "BUY"
df.loc[(df["ema9"] < df["ema21"]) & (df["rsi14"] <= 45), "signal"] = "SELL"

wins = 0
losses = 0
no_result = 0
trades = []

for i in range(30, len(df) - 1):
    row = df.iloc[i]
    if row["signal"] == "WAIT":
        continue

    entry = row["close"]
    atr = row["atr14"]

    if row["signal"] == "BUY":
        sl = entry - (1.5 * atr)
        tp1 = entry + (1.5 * atr)
    else:
        sl = entry + (1.5 * atr)
        tp1 = entry - (1.5 * atr)

    outcome = None
    for j in range(i + 1, min(i + 20, len(df))):
        future = df.iloc[j]
        if row["signal"] == "BUY":
            if future["low"] <= sl:
                outcome = "LOSS"
                break
            if future["high"] >= tp1:
                outcome = "WIN"
                break
        else:
            if future["high"] >= sl:
                outcome = "LOSS"
                break
            if future["low"] <= tp1:
                outcome = "WIN"
                break

    if outcome == "WIN":
        wins += 1
    elif outcome == "LOSS":
        losses += 1
    else:
        no_result += 1

    trades.append((row["time"], row["signal"], outcome))

total = wins + losses
winrate = (wins / total * 100) if total > 0 else 0

print("=" * 50)
print("BACKTEST RESULT -", SYMBOL, INTERVAL)
print("=" * 50)
print("Total signals tested:", total + no_result)
print("Wins:", wins)
print("Losses:", losses)
print("No clear result (still open):", no_result)
print("Win Rate:", round(winrate, 2), "%")
print("=" * 50)
