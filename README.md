# Viklyst - Quant Backtesting + ML Signals + AI Explanations

Viklyst is a full-stack quantitative research playground that ingests OHLCV market data, runs baseline strategy backtests, trains ML models to predict next-day direction, and serves explainable results through APIs and a web UI.

---

## What this project does
- **Market data ingestion**: store daily OHLCV bars per ticker and date range
- **Backtesting engine**: compute equity curves, drawdown curves, and performance metrics
- **Baseline strategies**: Buy & Hold, Next-Day Momentum with lookback
- **ML pipeline**: feature engineering, time-series cross-validation training, model artifacts
- **Inference service**: FastAPI microservice serving probability predictions
- **ML-gated backtests**: trade only when probability exceeds threshold with warmup windows
- **AI explanations**: generate user-facing narratives with a mock fallback if LLM is unavailable

---

## Tech stack

### Backend
- Kotlin, Spring Boot, REST APIs
- Postgres (or your configured DB)
- Docker / Docker Compose

### ML + Inference
- Python, pandas, scikit-learn, joblib
- FastAPI + Uvicorn

### Frontend
- Vite (React or vanilla, depending on your setup)

---

## Architecture

```text
Vite UI  --->  Spring Boot Platform  --->  Postgres
                     |
                     +--> FastAPI ML Service
                           - /predict: probability of next-day up
                           - /explain: AI narrative of backtest results
````

### Components

* **Spring Boot Platform (8080)**

    * Instruments CRUD
    * OHLCV ingestion
    * Baseline backtests and curves
    * ML backtest curve endpoint that calls the ML service

* **FastAPI ML Service (8000)**

    * Loads latest model per symbol from `ml/models`
    * Enforces feature parity using training metadata JSON
    * Returns `prob_up` and `predicted`
    * Optional explanation endpoint powered by LLM with fallback

---

## Getting started

### 1) Start DB and dependencies with Docker

```bash
docker compose up -d
```

### 2) Run Spring Boot backend

```bash
cd viklyst-platform
./gradlew bootRun
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

### 3) Create instrument and ingest bars

PowerShell:

```powershell
$body = @{ symbol="TSLA"; name="Tesla Inc." } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/instruments" -ContentType "application/json" -Body $body

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/ingest/daily?symbol=TSLA&from=2025-10-01&to=2026-01-03"
```

### 4) Train an ML model

```bash
python .\ml\train.py --symbol TSLA --from-date 2025-10-01 --to-date 2026-01-03
```

Artifacts created:

* `ml/models/<SYMBOL>_<timestamp>_logreg.joblib`
* `ml/models/<SYMBOL>_<timestamp>_meta.json`

### 5) Run ML inference service

```bash
python -m uvicorn ml.serve:app --reload --port 8000
```

Health check:

```bash
curl http://localhost:8000/health
```

### 6) Call prediction endpoint

PowerShell:

```powershell
$body = @{
  symbol="TSLA"
  from_date="2025-10-01"
  to_date="2026-01-03"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "http://localhost:8000/predict" -ContentType "application/json" -Body $body
```

### 7) Run ML-threshold backtest from Spring

PowerShell:

```powershell
Invoke-RestMethod "http://localhost:8080/api/backtests/ml/curve?symbol=TSLA&from=2025-10-01&to=2026-01-03&threshold=0.55" |
  Select-Object -Expand summary
```

---

## API reference

### Spring Boot

* `POST /api/instruments`
* `POST /api/ingest/daily?symbol=TSLA&from=YYYY-MM-DD&to=YYYY-MM-DD`
* `GET  /api/instruments/{symbol}/bars/daily?from=YYYY-MM-DD&to=YYYY-MM-DD`
* `GET  /api/backtests/baseline/buy-and-hold?symbol=...`
* `GET  /api/backtests/baseline/next-day-momentum/curve?symbol=...&lookback=...`
* `GET  /api/backtests/ml/curve?symbol=...&threshold=...`
* `GET  /api/ai/explain?symbol=...&from=...&to=...&threshold=...` (if wired through Spring)

### FastAPI

* `POST /predict` → returns probability and prediction
* `POST /explain` → returns AI narrative (mock fallback if not configured)
* `GET  /health`

---

## Design notes and safeguards

* Uses **time-series cross-validation** to reduce look-ahead leakage.
* Uses **warmup days** to ensure rolling-window features are valid.
* Inference enforces **feature parity** via `feature_columns` stored in model metadata.
* Explanation endpoint falls back to **mock AI** if the LLM key is missing or errors.

---

## Common issues

### 1) PowerShell `cd` path with spaces

Use quotes:

```powershell
cd "C:\Users\bhavi\Bhavik Jain\Bhavik\Projects\viklyst"
```

### 2) Virtual environment activation fails

Make sure you are in the folder containing `.venv`:

```powershell
cd "C:\Users\bhavi\Bhavik Jain\Bhavik\Projects\viklyst"
.\.venv\Scripts\Activate.ps1
```

### 3) `No module named uvicorn`

Install missing packages:

```powershell
python -m pip install uvicorn fastapi pandas scikit-learn joblib requests
```

### 4) Model feature mismatch

This means the inference service engineered features differently than training.
Fix by reusing the exact same feature engineering logic and keep `feature_columns` in meta JSON.

---

## Roadmap

* Add more strategies and transaction cost modeling
* Portfolio-level backtesting and risk metrics
* Hyperparameter tuning and model benchmarking
* UI charts for equity curve, drawdown, and trade markers
* Persist backtest runs and compare across strategies/symbols

---

## Disclaimer

This project is for educational research purposes and is not financial advice.
