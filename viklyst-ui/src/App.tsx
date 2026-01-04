import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
  Legend,
  AreaChart,
  Area,
} from "recharts";
import { fetchMomentumCurve } from "./api";

type ChartRow = {
  day: string;
  benchmark: number | null;
  strategy: number | null;
  benchmarkValue: number | null;
  strategyValue: number | null;
  benchmarkDrawdownPct: number | null;
  strategyDrawdownPct: number | null;
};

function formatPct(x: number) {
  const sign = x > 0 ? "+" : "";
  return `${sign}${x.toFixed(2)}%`;
}

function formatNum(x: number) {
  return x.toLocaleString(undefined, { maximumFractionDigits: 2 });
}

function maxDrawdownPctFromEquity(equity: number[]) {
  let peak = equity[0];
  let maxDd = 0; // negative
  for (const e of equity) {
    peak = Math.max(peak, e);
    const dd = (e / peak - 1) * 100;
    if (dd < maxDd) maxDd = dd;
  }
  return maxDd; // negative number
}

function totalReturnPctFromEquity(equity: number[]) {
  if (equity.length < 2) return 0;
  return (equity[equity.length - 1] / equity[0] - 1) * 100;
}

function computeDrawdownSeriesPct(equity: (number | null)[]) {
  let peak: number | null = null;
  return equity.map((e) => {
    if (e == null) return null;
    peak = peak == null ? e : Math.max(peak, e);
    return ((e / peak) - 1) * 100; // negative or 0
  });
}

function computeExposurePct(strategyEquity: (number | null)[]) {
  // Strategy curve stays flat on cash days. We'll count "invested days" when equity changes.
  // We compare today's equity to previous non-null equity.
  let prev: number | null = null;
  let investedSteps = 0;
  let steps = 0;

  for (const e of strategyEquity) {
    if (e == null) continue;
    if (prev != null) {
      steps++;
      if (Math.abs(e - prev) > 1e-12) investedSteps++;
    }
    prev = e;
  }

  if (steps === 0) return 0;
  return (investedSteps / steps) * 100;
}

const presets = [
  { label: "AAPL", symbol: "AAPL" },
  { label: "MSFT", symbol: "MSFT" },
  { label: "TSLA", symbol: "TSLA" },
  { label: "NVDA", symbol: "NVDA" },
];

export default function App() {
  const [symbol, setSymbol] = useState("AAPL");
  const [from, setFrom] = useState("2025-10-01");
  const [to, setTo] = useState("2026-01-03");
  const [lookback, setLookback] = useState(5);

  // New: initial capital (for $ chart)
  const [initialCapital, setInitialCapital] = useState(10000);

  // Only fetch when user clicks Run
  const [runKey, setRunKey] = useState(0);
  const [lastUpdated, setLastUpdated] = useState<string>("");

  const query = useQuery({
    queryKey: ["momentumCurve", runKey],
    queryFn: () => fetchMomentumCurve({ symbol, from, to, lookback }),
    enabled: runKey > 0,
  });

  const friendlyStrategy = `Next-Day Momentum (Lookback = ${lookback})`;

  const chartData: ChartRow[] = useMemo(() => {
    if (!query.data) return [];

    const benchMap = new Map(query.data.benchmark.map((p) => [p.day, p.equity]));
    const stratMap = new Map(query.data.curve.map((p) => [p.day, p.equity]));

    const days = Array.from(new Set([...benchMap.keys(), ...stratMap.keys()])).sort();

    const benchmarkEquity = days.map((d) => benchMap.get(d) ?? null);
    const strategyEquity = days.map((d) => stratMap.get(d) ?? null);

    const benchDd = computeDrawdownSeriesPct(benchmarkEquity);
    const stratDd = computeDrawdownSeriesPct(strategyEquity);

    return days.map((day, idx) => {
      const b = benchmarkEquity[idx];
      const s = strategyEquity[idx];
      return {
        day,
        benchmark: b,
        strategy: s,
        benchmarkValue: b == null ? null : b * initialCapital,
        strategyValue: s == null ? null : s * initialCapital,
        benchmarkDrawdownPct: benchDd[idx],
        strategyDrawdownPct: stratDd[idx],
      };
    });
  }, [query.data, initialCapital]);

  // Derived metrics (computed from curves)
  const derived = useMemo(() => {
    if (!query.data) return null;

    const bench = query.data.benchmark.map((p) => p.equity);
    const strat = query.data.curve.map((p) => p.equity);

    const benchmarkReturn = totalReturnPctFromEquity(bench);
    const benchmarkMaxDd = maxDrawdownPctFromEquity(bench);

    const strategyReturn = totalReturnPctFromEquity(strat);
    const strategyMaxDd = maxDrawdownPctFromEquity(strat);

    // Exposure from unioned series (more accurate re: flats)
    const unionStrategy = chartData.map((r) => r.strategy);
    const exposurePct = computeExposurePct(unionStrategy);

    return {
      benchmarkReturn,
      benchmarkMaxDd,
      strategyReturn,
      strategyMaxDd,
      exposurePct,
    };
  }, [query.data, chartData]);

  const summary = query.data?.summary;

  function onRun() {
    setLastUpdated(new Date().toLocaleString());
    setRunKey((k) => k + 1);
  }

  return (
      <div className="min-h-screen bg-zinc-950 text-zinc-100">
        <div className="mx-auto max-w-6xl px-6 py-8 space-y-6">
          <header className="space-y-2">
            <h1 className="text-2xl font-semibold">Viklyst — Strategy Dashboard</h1>
            <p className="text-zinc-400">
              Compare a baseline momentum strategy vs buy-and-hold using your own backend.
            </p>
          </header>

          {/* Controls */}
          <div className="rounded-2xl border border-zinc-800 bg-zinc-900 p-4">
            <div className="flex flex-wrap gap-2 mb-3">
              {presets.map((p) => (
                  <button
                      key={p.symbol}
                      onClick={() => setSymbol(p.symbol)}
                      className={`px-3 py-1 rounded-xl border text-sm ${
                          symbol === p.symbol
                              ? "border-white text-white"
                              : "border-zinc-700 text-zinc-300 hover:border-zinc-500"
                      }`}
                  >
                    {p.label}
                  </button>
              ))}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-6 gap-3 items-end">
              <div>
                <label className="text-sm text-zinc-400">Symbol</label>
                <input
                    value={symbol}
                    onChange={(e) => setSymbol(e.target.value.toUpperCase())}
                    className="mt-1 w-full rounded-xl bg-zinc-950 border border-zinc-800 px-3 py-2 outline-none"
                    placeholder="AAPL"
                />
              </div>

              <div>
                <label className="text-sm text-zinc-400">From</label>
                <input
                    type="date"
                    value={from}
                    onChange={(e) => setFrom(e.target.value)}
                    className="mt-1 w-full rounded-xl bg-zinc-950 border border-zinc-800 px-3 py-2 outline-none"
                />
              </div>

              <div>
                <label className="text-sm text-zinc-400">To</label>
                <input
                    type="date"
                    value={to}
                    onChange={(e) => setTo(e.target.value)}
                    className="mt-1 w-full rounded-xl bg-zinc-950 border border-zinc-800 px-3 py-2 outline-none"
                />
              </div>

              <div>
                <label className="text-sm text-zinc-400">Lookback</label>
                <input
                    type="number"
                    value={lookback}
                    min={2}
                    max={60}
                    onChange={(e) => setLookback(Number(e.target.value))}
                    className="mt-1 w-full rounded-xl bg-zinc-950 border border-zinc-800 px-3 py-2 outline-none"
                />
              </div>

              <div>
                <label className="text-sm text-zinc-400">Initial Capital</label>
                <input
                    type="number"
                    value={initialCapital}
                    min={100}
                    step={100}
                    onChange={(e) => setInitialCapital(Number(e.target.value))}
                    className="mt-1 w-full rounded-xl bg-zinc-950 border border-zinc-800 px-3 py-2 outline-none"
                />
              </div>

              <button
                  onClick={onRun}
                  disabled={query.isFetching}
                  className={`rounded-xl font-medium px-4 py-2 ${
                      query.isFetching
                          ? "bg-zinc-400 text-zinc-900 cursor-not-allowed"
                          : "bg-white text-zinc-950 hover:opacity-90 active:opacity-80"
                  }`}
              >
                {query.isFetching ? "Running…" : "Run"}
              </button>
            </div>

            <div className="mt-3 flex items-center justify-between">
              <div className="text-sm text-zinc-400">
                {lastUpdated ? `Last updated: ${lastUpdated}` : "Tip: try AAPL then TSLA"}
              </div>
              <div className="text-xs text-zinc-500">
                Backend: <span className="text-zinc-300">/api/backtests/.../curve</span>
              </div>
            </div>

            {query.isError && (
                <div className="mt-3 text-sm text-red-400">
                  Error: {(query.error as Error).message}
                </div>
            )}
          </div>

          {/* Summary Cards */}
          {(summary || derived) && (
              <div className="grid grid-cols-1 md:grid-cols-6 gap-4">
                <Card title="Strategy" value={friendlyStrategy} />
                <Card title="Total Return (Strategy)" value={derived ? formatPct(derived.strategyReturn) : "-"} />
                <Card title="Max Drawdown (Strategy)" value={derived ? formatPct(derived.strategyMaxDd) : "-"} />
                <Card title="Exposure" value={derived ? `${derived.exposurePct.toFixed(1)}%` : "-"} />
                <Card title="Total Return (Buy & Hold)" value={derived ? formatPct(derived.benchmarkReturn) : "-"} />
                <Card title="Max Drawdown (Buy & Hold)" value={derived ? formatPct(derived.benchmarkMaxDd) : "-"} />
              </div>
          )}

          {/* Equity Chart */}
          <div className="rounded-2xl border border-zinc-800 bg-zinc-900 p-4">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold">Equity Curve</h2>
              <div className="text-sm text-zinc-400">
                {summary ? `${summary.symbol} (${summary.from} → ${summary.to})` : "Run a backtest"}
              </div>
            </div>

            <div className="mt-4 h-[420px]">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="day" tick={{ fontSize: 12 }} />
                  <YAxis
                      tick={{ fontSize: 12 }}
                      domain={["auto", "auto"]}
                      tickFormatter={(v) => `$${formatNum(v)}`}
                  />
                  <Tooltip
                      formatter={(value: any, name: any) => {
                        if (value == null) return ["-", name];
                        return [`$${formatNum(value)}`, name];
                      }}
                  />
                  <Legend />
                  <Line type="monotone" dataKey="benchmarkValue" dot={false} name="Buy & Hold ($)" />
                  <Line type="monotone" dataKey="strategyValue" dot={false} name="Momentum Strategy ($)" />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>

          {/* Drawdown Chart */}
          <div className="rounded-2xl border border-zinc-800 bg-zinc-900 p-4">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold">Drawdown</h2>
              <div className="text-sm text-zinc-400">How far you’re down from your peak (risk view)</div>
            </div>

            <div className="mt-4 h-[260px]">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="day" tick={{ fontSize: 12 }} />
                  <YAxis tick={{ fontSize: 12 }} tickFormatter={(v) => `${v.toFixed(0)}%`} />
                  <Tooltip
                      formatter={(value: any, name: any) => {
                        if (value == null) return ["-", name];
                        return [`${(value as number).toFixed(2)}%`, name];
                      }}
                  />
                  <Legend />
                  <Area
                      type="monotone"
                      dataKey="benchmarkDrawdownPct"
                      name="Buy & Hold DD%"
                      fillOpacity={0.12}
                  />
                  <Area
                      type="monotone"
                      dataKey="strategyDrawdownPct"
                      name="Strategy DD%"
                      fillOpacity={0.12}
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>

            <div className="mt-2 text-xs text-zinc-500">
              Drawdown is always ≤ 0%. A value of -10% means you’re 10% below your historical peak.
            </div>
          </div>

          <footer className="text-xs text-zinc-500">
            Tip: Keep Spring Boot running on 8080. Run the UI with <code>npm run dev</code>.
          </footer>
        </div>
      </div>
  );
}

function Card({ title, value }: { title: string; value: string }) {
  return (
      <div className="rounded-2xl border border-zinc-800 bg-zinc-900 p-4">
        <div className="text-sm text-zinc-400">{title}</div>
        <div className="mt-2 text-lg font-semibold break-words">{value}</div>
      </div>
  );
}
