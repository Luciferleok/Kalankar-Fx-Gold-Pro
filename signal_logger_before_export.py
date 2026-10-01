"""
signal_logger.py
Logs every generated signal to a CSV file so you can track
accuracy over time. Works both in Termux testing and inside
the built APK (pass the app's user_data_dir when calling from
inside Kivy so it persists in the right writable location on
Android; falls back to the current folder otherwise).

Also provides export_log(), which copies the CSV to Android's
app-specific external storage folder (Android/data/<package>/files/)
so you can browse it with any normal file manager or pull it via
Termux's storage access, without needing any special runtime
storage permission.
"""

import os
import csv
import shutil
from datetime import datetime

LOG_FILENAME = "signal_history.csv"

FIELDNAMES = [
    "timestamp", "price",
    "trend", "momentum", "volatility", "sr", "candlestick", "sentiment",
    "overall", "agreement_pct",
]


def get_log_path(base_dir=None):
    if base_dir is None:
        base_dir = os.getcwd()
    return os.path.join(base_dir, LOG_FILENAME)


def log_signal(groups, overall, agreement, price, base_dir=None):
    """
    Appends one row to the signal history CSV.
    groups: dict like {"trend": "BUY", "momentum": "WAIT", ...}
    overall: "BUY" / "SELL" / "WAIT/MIXED"
    agreement: float percentage (0-100)
    price: current XAU/USD price
    """
    path = get_log_path(base_dir)
    file_exists = os.path.isfile(path)

    row = {
        "timestamp": datetime.now().isoformat(timespec="seconds"),
        "price": round(price, 2),
        "trend": groups.get("trend", ""),
        "momentum": groups.get("momentum", ""),
        "volatility": groups.get("volatility", ""),
        "sr": groups.get("sr", ""),
        "candlestick": groups.get("candlestick", ""),
        "sentiment": groups.get("sentiment", ""),
        "overall": overall,
        "agreement_pct": round(agreement, 1),
    }

    with open(path, "a", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=FIELDNAMES)
        if not file_exists:
            writer.writeheader()
        writer.writerow(row)

    return path


def get_export_dir():
    """
    Returns a folder that's easy to reach with a normal file manager
    (or Termux, via termux-setup-storage) without needing any special
    runtime storage permission:
      Android/data/<package_name>/files/
    Falls back to the current directory when not running on Android
    (e.g. when testing in Termux directly).
    """
    try:
        from jnius import autoclass
        PythonActivity = autoclass("org.kivy.android.PythonActivity")
        context = PythonActivity.mActivity
        export_dir = context.getExternalFilesDir(None).getAbsolutePath()
        return export_dir
    except Exception:
        return os.getcwd()


def export_log(base_dir=None):
    """
    Copies the current signal_history.csv to the accessible export
    folder (see get_export_dir). Safe to call often; overwrites the
    previous copy each time. Returns the export path, or None if the
    log file doesn't exist yet.
    """
    source_path = get_log_path(base_dir)
    if not os.path.isfile(source_path):
        return None

    export_dir = get_export_dir()
    os.makedirs(export_dir, exist_ok=True)
    export_path = os.path.join(export_dir, LOG_FILENAME)

    shutil.copyfile(source_path, export_path)
    return export_path
