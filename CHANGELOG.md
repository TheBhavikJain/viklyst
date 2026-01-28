# Changelog

All notable changes to this project will be documented in this file.

## v1.0.0 - 2026-01-05

### Added
- End-to-end quantitative backtesting platform with REST APIs for instruments, OHLCV ingestion, and backtest curves.
- Baseline strategy engines including buy-and-hold and next-day momentum with lookback, producing equity and drawdown analytics.
- ML training pipeline with time-series cross-validation, feature engineering, and model artifact persistence with metadata.
- FastAPI inference service to serve next-day direction probabilities using the latest model per symbol with feature parity checks.
- ML-gated backtest mode using probability thresholds and warmup windows for reliable feature computation.
- AI explanation endpoint with safe fallback behavior when external LLM access is unavailable.
- Web UI dashboard built with Vite to visualize backtests and results.
- Docker-based local development setup for services.

### Fixed
- Feature-column parity issues between training and inference by storing and enforcing feature columns in model metadata.
- Backtest stability by adding warmup logic to avoid insufficient-bar feature failures.

### Notes
- AI explanations require a valid LLM provider key and active quota; otherwise the service returns the built-in fallback explanation.
