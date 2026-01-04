# Viklyst — Strategy Backtesting Dashboard

Viklyst is a full-stack trading strategy sandbox that:
- pulls daily market data (Alpha Vantage),
- stores it in Postgres,
- runs baseline backtests (Buy & Hold vs Next-Day Momentum),
- and visualizes equity + drawdown curves in a web dashboard.

## Tech Stack
**Backend**
- Kotlin, Spring Boot
- Spring Web, Spring Data JPA
- Flyway (DB migrations)
- PostgreSQL

**Frontend**
- Vite (dev server on `5173`)
- Dashboard UI that calls the Spring Boot API (`8080`)

**Data**
- Alpha Vantage (free tier)

---

## Repo Structure
- `viklyst-platform/` — Spring Boot backend (API, DB, backtesting engine)
- `viklyst-ui/` — Vite frontend (dashboard)
- `docker/` — local Postgres setup (docker compose)

---

## Prerequisites
- **Java 21**
- **Docker Desktop** (for Postgres)
- **Node.js 18+** (for UI)
- **Alpha Vantage API key** (free)

---

## Setup
### 1) Configure Alpha Vantage API Key
Set an environment variable:

**PowerShell**
```powershell
$env:ALPHAVANTAGE_API_KEY="YOUR_KEY_HERE"
