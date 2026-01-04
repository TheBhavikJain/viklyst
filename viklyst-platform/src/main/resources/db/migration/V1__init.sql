CREATE TABLE IF NOT EXISTS instrument (
    id BIGSERIAL PRIMARY KEY,
    symbol TEXT NOT NULL UNIQUE,
    name TEXT
);

CREATE TABLE IF NOT EXISTS bar_daily (
    instrument_id BIGINT NOT NULL REFERENCES instrument(id),
    day DATE NOT NULL,
    open DOUBLE PRECISION NOT NULL,
    high DOUBLE PRECISION NOT NULL,
    low DOUBLE PRECISION NOT NULL,
    close DOUBLE PRECISION NOT NULL,
    volume BIGINT,
    PRIMARY KEY(instrument_id, day)
);

CREATE INDEX IF NOT EXISTS idx_bar_daily_day ON bar_daily(day);