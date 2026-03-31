import asyncio
from datetime import datetime, timedelta, timezone

import httpx

from app.config import settings

# Tickers supported by the course cached API (appendix)
CACHED_API_TICKERS = frozenset({"C", "AMZN", "TSLA", "FB", "AAPL"})


async def fetch_cached_price(ticker: str) -> float | None:
    t = ticker.upper().strip()
    if not t:
        return None
    url = f"{settings.cached_price_base}?ticker={t}"
    async with httpx.AsyncClient(timeout=15.0) as client:
        try:
            r = await client.get(url)
            r.raise_for_status()
            data = r.json()
        except (httpx.HTTPError, ValueError):
            return None
    # API may return { "price": ... } or raw number — handle common shapes
    if isinstance(data, dict):
        for key in ("price", "lastPrice", "close", "value"):
            if key in data and data[key] is not None:
                try:
                    return float(data[key])
                except (TypeError, ValueError):
                    pass
        if "data" in data and isinstance(data["data"], (int, float)):
            return float(data["data"])
    if isinstance(data, (int, float)):
        return float(data)
    return None


def yahoo_history_csv_url(ticker: str, start: datetime, end: datetime) -> str:
    p1 = int(start.replace(tzinfo=timezone.utc).timestamp())
    p2 = int(end.replace(tzinfo=timezone.utc).timestamp())
    return (
        f"https://query1.finance.yahoo.com/v7/finance/download/{ticker}"
        f"?period1={p1}&period2={p2}&interval=1d&events=history&includeAdjustedClose=true"
    )


async def fetch_yahoo_adj_close_series(
    ticker: str, days: int
) -> list[tuple[datetime, float]]:
    end = datetime.now(timezone.utc)
    start = end - timedelta(days=max(days + 5, 7))
    url = yahoo_history_csv_url(ticker.upper(), start, end)
    async with httpx.AsyncClient(timeout=20.0, follow_redirects=True) as client:
        try:
            r = await client.get(
                url,
                headers={"User-Agent": "Mozilla/5.0 (compatible; PortfolioDemo/1.0)"},
            )
            r.raise_for_status()
            text = r.text
        except httpx.HTTPError:
            return []
    lines = [ln for ln in text.strip().splitlines() if ln and not ln.startswith("Date")]
    out: list[tuple[datetime, float]] = []
    for ln in lines:
        parts = ln.split(",")
        if len(parts) < 6:
            continue
        try:
            d = datetime.strptime(parts[0], "%Y-%m-%d").replace(tzinfo=timezone.utc)
            adj = float(parts[5] if len(parts) > 5 else parts[4])
            out.append((d, adj))
        except (ValueError, IndexError):
            continue
    out.sort(key=lambda x: x[0])
    if len(out) > days:
        out = out[-days:]
    return out


async def price_for_holding(asset_type: str, ticker: str | None) -> float | None:
    if asset_type == "cash":
        return 1.0
    if not ticker:
        return None
    t = ticker.upper()
    if t in CACHED_API_TICKERS:
        return await fetch_cached_price(t)
    return await fetch_cached_price(t) or await _fallback_yahoo_last(t)


async def _fallback_yahoo_last(ticker: str) -> float | None:
    series = await fetch_yahoo_adj_close_series(ticker, days=5)
    if not series:
        return None
    return series[-1][1]


async def portfolio_value_series(
    stock_weights: list[tuple[str, float]], days: int
) -> list[tuple[datetime, float]]:
    """stock_weights: (ticker, quantity) for stocks/bonds only; sums portfolio value per day."""
    if not stock_weights:
        return []
    series_list = await asyncio.gather(
        *[fetch_yahoo_adj_close_series(t, days) for t, _ in stock_weights]
    )
    by_date: dict[datetime, float] = {}
    for (ticker, qty), series in zip(stock_weights, series_list):
        for dt, price in series:
            by_date[dt] = by_date.get(dt, 0.0) + qty * price
    ordered = sorted(by_date.items(), key=lambda x: x[0])
    return ordered
