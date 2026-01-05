from __future__ import annotations

import glob
import json
import os
from typing import Optional

import joblib
import pandas as pd
import requests
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from openai import OpenAI

PLATFORM_BASE_URL = os.environ.get("VIKLYST_PLATFORM_URL", "http://localhost:8080")
MODELS_DIR = os.path.join(os.path.dirname(__file__), "models")

app = FastAPI(title="Viklyst ML Inference Server")

# --- add DTOs ---
class ExplainRequest(BaseModel):
    symbol: str
    from_date: str
    to_date: str
    threshold: float = 0.55

class ExplainResponse(BaseModel):
    symbol: str
    from_date: str
    to_date: str
    threshold: float
    explanation: str

def fetch_json(url: str, params: dict | None = None):
    r = requests.get(url, params=params, timeout=20)
    r.raise_for_status()
    return r.json()

def get_openai_client() -> OpenAI | None:
    # If key missing, return None and we’ll do a fallback response
    if not os.environ.get("OPENAI_API_KEY"):
        return None
    return OpenAI()

@app.post("/explain", response_model=ExplainResponse)
def explain(req: ExplainRequest):
    sym = req.symbol.upper()

    # 1) pull benchmark + ML curve summaries from your platform
    buyhold_url = f"{PLATFORM_BASE_URL}/api/backtests/baseline/buy-and-hold"
    mlcurve_url = f"{PLATFORM_BASE_URL}/api/backtests/ml/curve"

    buyhold = fetch_json(buyhold_url, params={"symbol": sym, "from": req.from_date, "to": req.to_date})
    mlcurve = fetch_json(mlcurve_url, params={"symbol": sym, "from": req.from_date, "to": req.to_date, "threshold": req.threshold})

    buyhold_sum = buyhold  # this endpoint returns summary directly
    ml_sum = mlcurve.get("summary", {})

    facts = {
        "symbol": sym,
        "range": {"from": req.from_date, "to": req.to_date},
        "benchmark_buy_and_hold": {
            "totalReturnPct": buyhold_sum.get("totalReturnPct"),
            "maxDrawdownPct": buyhold_sum.get("maxDrawdownPct"),
            "endingEquity": buyhold_sum.get("endingEquity"),
        },
        "ml_strategy": {
            "threshold": req.threshold,
            "trades": ml_sum.get("trades"),
            "winRate": ml_sum.get("winRate"),
            "totalReturnPct": ml_sum.get("totalReturnPct"),
            "maxDrawdownPct": ml_sum.get("maxDrawdownPct"),
            "endingEquity": ml_sum.get("endingEquity"),
        }
    }

    # 2) call OpenAI (or fallback)
    client = get_openai_client()
    if client is None:
        explanation = (
            f"[MOCK AI]\n"
            f"From {req.from_date} to {req.to_date}, ML(threshold={req.threshold}) "
            f"had return={facts['ml_strategy']['totalReturnPct']}% vs buy&hold={facts['benchmark_buy_and_hold']['totalReturnPct']}%. "
            f"Trades={facts['ml_strategy']['trades']}, winRate={facts['ml_strategy']['winRate']}%, "
            f"drawdown={facts['ml_strategy']['maxDrawdownPct']}%.\n"
            f"This is a demo explanation. Set OPENAI_API_KEY to get real LLM output."
        )
    else:
        prompt = f"""
You are an assistant explaining backtest results to a beginner.
Explain in simple terms, avoid hype, and include 3 bullet takeaways + 2 cautions.
Do NOT give financial advice.

FACTS (JSON):
{json.dumps(facts, indent=2)}
"""
        resp = client.responses.create(
            model="gpt-4o-mini",
            input=prompt,
        )
        explanation = resp.output_text

    return ExplainResponse(
        symbol=sym,
        from_date=req.from_date,
        to_date=req.to_date,
        threshold=req.threshold,
        explanation=explanation.strip()
    )

# -------------------------
# Request/Response DTOs
# -------------------------
class PredictRequest(BaseModel):
    symbol: str = Field(..., examples=["AAPL"])
    from_date: str = Field(..., examples=["2025-10-01"])
    to_date: str = Field(..., examples=["2026-01-03"])
    # Optional: override model path (normally we pick latest for that symbol)
    model_path: Optional[str] = None


class PredictResponse(BaseModel):
    symbol: str
    model_file: str
    as_of: str
    prob_up: float
    predicted: int  # 1 = up, 0 = down


# -------------------------
# Helpers
# -------------------------
def latest_model_for_symbol(symbol: str) -> str:
    pattern = os.path.join(MODELS_DIR, f"{symbol.upper()}_*_logreg.joblib")
    candidates = sorted(glob.glob(pattern))
    if not candidates:
        raise FileNotFoundError(f"No model found for {symbol}. Expected something like: {pattern}")
    return candidates[-1]  # latest by filename timestamp


def load_meta_for_model(model_path: str) -> dict:
    # We saved meta as: <sameprefix>_meta.json
    meta_path = model_path.replace("_logreg.joblib", "_meta.json")
    if not os.path.exists(meta_path):
        raise FileNotFoundError(f"Meta file not found: {meta_path}")
    with open(meta_path, "r", encoding="utf-8") as f:
        return json.load(f)


def fetch_bars(symbol: str, from_date: str, to_date: str) -> pd.DataFrame:
    url = f"{PLATFORM_BASE_URL}/api/instruments/{symbol.upper()}/bars/daily"
    r = requests.get(url, params={"from": from_date, "to": to_date}, timeout=15)
    if r.status_code != 200:
        raise HTTPException(status_code=400, detail=f"Failed to fetch bars: {r.status_code} {r.text}")

    data = r.json()
    if not data:
        raise HTTPException(status_code=400, detail="No bars returned (empty dataset)")

    # Expect at least: day + close
    df = pd.DataFrame(data)
    if "close" not in df.columns:
        raise HTTPException(status_code=400, detail=f"Bars response missing 'close'. Got columns: {list(df.columns)}")

    # Normalize/parse day column
    if "day" in df.columns:
        df["day"] = pd.to_datetime(df["day"])
        df = df.sort_values("day").reset_index(drop=True)
    return df


def engineer_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    IMPORTANT:
    These features MUST match what you trained in ml/train.py.
    If your train.py uses different features, either:
      (A) copy that feature function here, or
      (B) import it from train.py and reuse it.
    """
    out = df.copy()

    out["day"] = pd.to_datetime(out["day"])
    out = out.sort_values("day").reset_index(drop=True)

    # Same features as train.py
    out["ret_1d"] = out["close"].pct_change()

    out["ma_5"] = out["close"].rolling(5).mean()
    out["ma_10"] = out["close"].rolling(10).mean()

    out["close_over_ma5"] = out["close"] / out["ma_5"] - 1.0
    out["close_over_ma10"] = out["close"] / out["ma_10"] - 1.0

    out["vol_5"] = out["ret_1d"].rolling(5).std()
    out["vol_10"] = out["ret_1d"].rolling(10).std()

    out["volchg_1d"] = out["volume"].pct_change()

    # Drop rows where rolling windows aren't ready
    out = out.dropna().reset_index(drop=True)
    return out


def pick_latest_row_features(df_feat: pd.DataFrame, feature_cols: list[str]) -> tuple[str, list[float]]:
    missing = [c for c in feature_cols if c not in df_feat.columns]
    if missing:
        raise HTTPException(
            status_code=500,
            detail=(
                "Feature mismatch: model expects columns not present in engineered features.\n"
                f"Missing: {missing}\n"
                "Fix: make engineer_features() match train.py exactly."
            ),
        )

    last = df_feat.iloc[-1]
    as_of = str(last["day"].date()) if "day" in df_feat.columns else "latest"
    x = [float(last[c]) for c in feature_cols]
    return as_of, x


# -------------------------
# Endpoints
# -------------------------
@app.get("/health")
def health():
    return {
        "status": "ok",
        "platform": PLATFORM_BASE_URL,
        "models_dir": MODELS_DIR,
        "serve_file": __file__,
        "openai_key_present": bool(os.environ.get("OPENAI_API_KEY")),
    }


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    symbol = req.symbol.upper()

    model_path = req.model_path or latest_model_for_symbol(symbol)
    try:
        model = joblib.load(model_path)
        meta = load_meta_for_model(model_path)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to load model/meta: {e}")

    # meta should contain the columns used during training
    feature_cols = meta.get("feature_columns")
    if not feature_cols:
        raise HTTPException(
            status_code=500,
            detail="Meta JSON missing 'feature_columns'. Update train.py to store feature_columns in meta.",
        )

    df = fetch_bars(symbol, req.from_date, req.to_date)
    df_feat = engineer_features(df)

    if len(df_feat) < 1:
        raise HTTPException(status_code=400, detail="Not enough bars after feature engineering (too short range).")

    as_of, x = pick_latest_row_features(df_feat, feature_cols)

    # Predict probability of class 1
    prob_up = float(model.predict_proba([x])[0][1])
    predicted = 1 if prob_up >= 0.5 else 0

    return PredictResponse(
        symbol=symbol,
        model_file=os.path.basename(model_path),
        as_of=as_of,
        prob_up=round(prob_up, 6),
        predicted=predicted,
    )
