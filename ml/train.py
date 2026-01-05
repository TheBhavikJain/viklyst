import os
import json
import argparse
from datetime import datetime

import requests
import numpy as np
import pandas as pd
from sklearn.model_selection import TimeSeriesSplit
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix
import joblib


def fetch_daily_bars(base_url: str, symbol: str, start: str, end: str) -> pd.DataFrame:
    """
    Calls your Spring Boot endpoint:
    GET /api/instruments/{symbol}/bars/daily?from=YYYY-MM-DD&to=YYYY-MM-DD
    """
    url = f"{base_url.rstrip('/')}/api/instruments/{symbol}/bars/daily"
    r = requests.get(url, params={"from": start, "to": end}, timeout=30)
    r.raise_for_status()
    data = r.json()

    if not data:
        raise ValueError("No data returned from API.")

    df = pd.DataFrame(data)

    # Expecting: day, open, high, low, close, volume
    df["day"] = pd.to_datetime(df["day"])
    df = df.sort_values("day").reset_index(drop=True)
    return df


def build_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    Create ML-friendly features from OHLCV.
    Also create the target label: up tomorrow (1) / not up (0)
    """
    out = df.copy()

    # Basic returns
    out["ret_1d"] = out["close"].pct_change()

    # Moving averages (features)
    out["ma_5"] = out["close"].rolling(5).mean()
    out["ma_10"] = out["close"].rolling(10).mean()

    # Price vs MA (normalized features)
    out["close_over_ma5"] = out["close"] / out["ma_5"] - 1.0
    out["close_over_ma10"] = out["close"] / out["ma_10"] - 1.0

    # Volatility (std of returns)
    out["vol_5"] = out["ret_1d"].rolling(5).std()
    out["vol_10"] = out["ret_1d"].rolling(10).std()

    # Volume change
    out["volchg_1d"] = out["volume"].pct_change()

    # TARGET: tomorrow up? (shift(-1) means "next day")
    out["target_up_tomorrow"] = (out["close"].shift(-1) > out["close"]).astype(int)

    # Drop rows where rolling windows aren't ready / target missing
    out = out.dropna().reset_index(drop=True)
    return out


def train_timeseries_model(df_feat: pd.DataFrame, feature_cols: list[str]) -> dict:
    """
    Train a simple model (Logistic Regression) with time-series splits.
    """
    X = df_feat[feature_cols].values
    y = df_feat["target_up_tomorrow"].values

    # TimeSeriesSplit respects time ordering (no future leakage)
    tss = TimeSeriesSplit(n_splits=5)

    pipeline = Pipeline([
        ("scaler", StandardScaler()),
        ("clf", LogisticRegression(max_iter=2000))
    ])

    fold_scores = []
    last_fold_report = None
    last_fold_cm = None

    for fold, (train_idx, test_idx) in enumerate(tss.split(X), start=1):
        X_train, X_test = X[train_idx], X[test_idx]
        y_train, y_test = y[train_idx], y[test_idx]

        pipeline.fit(X_train, y_train)
        preds = pipeline.predict(X_test)

        acc = accuracy_score(y_test, preds)
        fold_scores.append(acc)

        # keep last fold details (most recent time slice)
        last_fold_report = classification_report(y_test, preds, digits=4)
        last_fold_cm = confusion_matrix(y_test, preds).tolist()

        print(f"Fold {fold} accuracy: {acc:.4f}")

    # Fit on full dataset for final saved model
    pipeline.fit(X, y)

    return {
        "model": pipeline,
        "cv_accuracy_mean": float(np.mean(fold_scores)),
        "cv_accuracy_std": float(np.std(fold_scores)),
        "last_fold_report": last_fold_report,
        "last_fold_confusion_matrix": last_fold_cm
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--symbol", default="AAPL")
    parser.add_argument("--from-date", dest="from_date", default="2025-01-01")
    parser.add_argument("--to-date", dest="to_date", default="2026-01-03")
    args = parser.parse_args()

    print("=== Viklyst ML Training (Option 1) ===")
    print(f"Fetching data from: {args.base_url}")
    print(f"Symbol: {args.symbol} | Range: {args.from_date} -> {args.to_date}")

    raw = fetch_daily_bars(args.base_url, args.symbol, args.from_date, args.to_date)
    print(f"Pulled {len(raw)} daily bars")

    feat = build_features(raw)
    print(f"After feature engineering: {len(feat)} usable rows")

    feature_cols = [
        "ret_1d",
        "close_over_ma5",
        "close_over_ma10",
        "vol_5",
        "vol_10",
        "volchg_1d",
    ]

    result = train_timeseries_model(feat, feature_cols)

    print("\n=== CV Summary ===")
    print(f"Mean accuracy: {result['cv_accuracy_mean']:.4f}")
    print(f"Std accuracy : {result['cv_accuracy_std']:.4f}")

    print("\n=== Last Fold Report (most recent slice) ===")
    print(result["last_fold_report"])
    print("Confusion matrix (last fold):", result["last_fold_confusion_matrix"])

    # Save model + metadata
    os.makedirs("ml/models", exist_ok=True)
    ts = datetime.utcnow().strftime("%Y%m%d_%H%M%S")
    model_path = f"ml/models/{args.symbol.upper()}_{ts}_logreg.joblib"
    meta_path = f"ml/models/{args.symbol.upper()}_{ts}_meta.json"

    joblib.dump(result["model"], model_path)

    meta = {
        "symbol": args.symbol.upper(),
        "from": args.from_date,
        "to": args.to_date,
        "feature_cols": feature_cols,
        "cv_accuracy_mean": result["cv_accuracy_mean"],
        "cv_accuracy_std": result["cv_accuracy_std"],
        "notes": "Predicts whether tomorrow close > today close using engineered OHLCV features. Trained via time-series CV.",
    }
    with open(meta_path, "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2)

    print("\nSaved model:", model_path)
    print("Saved meta :", meta_path)


if __name__ == "__main__":
    main()
