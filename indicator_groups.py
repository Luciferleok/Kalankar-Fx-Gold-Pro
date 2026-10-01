import requests, os
import pandas as pd
import numpy as np

API_KEY = os.getenv("TWELVE_DATA_API_KEY")
SYMBOL = "XAU/USD"

def fetch_data(interval="4h", size=100):
    url = "https://api.twelvedata.com/time_series"
    params = {"symbol": SYMBOL, "interval": interval, "outputsize": size, "apikey": API_KEY, "order": "ASC"}
    r = requests.get(url, params=params, timeout=15)
    data = r.json()
    if "values" not in data:
        print("API ERROR:", data)
        raise SystemExit
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

def trend_group(last):
    votes = []
    votes.append("BUY" if last["ema9"] > last["ema21"] else "SELL")
    votes.append("BUY" if last["close"] > last["ema50"] else "SELL")
    votes.append("BUY" if last["macd"] > last["macd_signal"] else "SELL")
    if last["adx14"] > 20:
        votes.append("BUY" if last["plus_di"] > last["minus_di"] else "SELL")
    else:
        votes.append("WAIT")
    votes.append("BUY" if last["sar_bull"] else "SELL")
    return decide(votes)

def momentum_group(last):
    votes = []
    votes.append("BUY" if last["rsi14"] >= 55 else ("SELL" if last["rsi14"] <= 45 else "WAIT"))
    votes.append("BUY" if last["stoch_k"] < 20 else ("SELL" if last["stoch_k"] > 80 else "WAIT"))
    votes.append("BUY" if last["cci"] < -100 else ("SELL" if last["cci"] > 100 else "WAIT"))
    votes.append("BUY" if last["williams_r"] < -80 else ("SELL" if last["williams_r"] > -20 else "WAIT"))
    votes.append("BUY" if last["roc"] > 0 else "SELL")
    return decide(votes)

def volatility_group(last):
    votes = []
    votes.append("BUY" if last["close"] < last["bb_lower"] else ("SELL" if last["close"] > last["bb_upper"] else "WAIT"))
    votes.append("BUY" if last["close"] < last["kc_lower"] else ("SELL" if last["close"] > last["kc_upper"] else "WAIT"))
    lower_std = last["bb_mid"] - (2 * last["std20"])
    upper_std = last["bb_mid"] + (2 * last["std20"])
    votes.append("BUY" if last["close"] < lower_std else ("SELL" if last["close"] > upper_std else "WAIT"))
    return decide(votes)

def sr_group(last):
    votes = []
    votes.append("BUY" if last["close"] > last["pivot"] else "SELL")
    votes.append("BUY" if last["close"] < last["s1"] else ("SELL" if last["close"] > last["r1"] else "WAIT"))
    return decide(votes)

def candlestick_group(df):
    last = df.iloc[-1]
    prev = df.iloc[-2]
    votes = []
    if prev["close"] < prev["open"] and last["close"] > last["open"] and last["close"] > prev["open"] and last["open"] < prev["close"]:
        votes.append("BUY")
    elif prev["close"] > prev["open"] and last["close"] < last["open"] and last["open"] > prev["close"] and last["close"] < prev["open"]:
        votes.append("SELL")
    else:
        votes.append("WAIT")

    body = abs(last["close"] - last["open"])
    upper_wick = last["high"] - max(last["close"], last["open"])
    lower_wick = min(last["close"], last["open"]) - last["low"]
    if lower_wick > 2 * body and upper_wick < body:
        votes.append("BUY")
    elif upper_wick > 2 * body and lower_wick < body:
        votes.append("SELL")
    else:
        votes.append("WAIT")

    if body < (last["high"] - last["low"]) * 0.1:
        votes.append("WAIT")
    else:
        votes.append("BUY" if last["close"] > last["open"] else "SELL")

    return decide(votes)

def get_all_signals():
    df = fetch_data("4h", 100)
    df = add_all_indicators(df)
    last = df.iloc[-1]
    results = {
        "Trend": trend_group(last),
        "Momentum": momentum_group(last),
        "Volatility": volatility_group(last),
        "Support/Resistance": sr_group(last),
        "Candlestick": candlestick_group(df),
    }
    return results, last["close"]

if __name__ == "__main__":
    results, price = get_all_signals()
    print("=" * 50)
    print(f"XAU/USD Price: {price:.2f}")
    print("=" * 50)
    for group, signal in results.items():
        print(f"{group:20s}: {signal}")

    buy_count = sum(1 for s in results.values() if s == "BUY")
    sell_count = sum(1 for s in results.values() if s == "SELL")
    total = len(results)
    if buy_count > sell_count:
        agreement = (buy_count / total) * 100
        print(f"\nOverall: BUY   Agreement: {agreement:.0f}% ({buy_count}/{total} groups)")
    elif sell_count > buy_count:
        agreement = (sell_count / total) * 100
        print(f"\nOverall: SELL   Agreement: {agreement:.0f}% ({sell_count}/{total} groups)")
    else:
        print(f"\nOverall: WAIT/MIXED   (no clear majority)")
    print("=" * 50)
