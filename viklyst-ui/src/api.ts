import axios from "axios";

export type BacktestSummary = {
    symbol: string;
    from: string;
    to: string;
    strategy: string;
    trades: number;
    winRate: number;
    totalReturnPct: number;
    maxDrawdownPct: number;
    points: number;
};

export type EquityPoint = { day: string; equity: number };

export type BacktestCurveResponse = {
    summary: BacktestSummary;
    curve: EquityPoint[];
    benchmark: EquityPoint[];
};

export async function fetchMomentumCurve(params: {
    symbol: string;
    from: string;
    to: string;
    lookback: number;
}) {
    const { symbol, from, to, lookback } = params;

    const res = await axios.get<BacktestCurveResponse>(
        `/api/backtests/baseline/next-day-momentum/curve`,
        { params: { symbol, from, to, lookback } }
    );

    return res.data;
}
