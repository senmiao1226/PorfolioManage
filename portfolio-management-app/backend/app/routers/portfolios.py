from datetime import date, datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import AssetType, Holding, Portfolio
from app.schemas import (
    AssetTypeEnum,
    HoldingCreate,
    HoldingRead,
    HoldingUpdate,
    HoldingValuation,
    PerformancePoint,
    PerformanceSeries,
    PortfolioCreate,
    PortfolioDetail,
    PortfolioRead,
    PortfolioSummary,
    PortfolioUpdate,
)
from app.services import pricing

router = APIRouter(prefix="/portfolios", tags=["portfolios"])


def _to_holding_read(h: Holding) -> HoldingRead:
    return HoldingRead(
        id=h.id,
        portfolio_id=h.portfolio_id,
        asset_type=AssetTypeEnum(h.asset_type.value),
        ticker=h.ticker,
        name=h.name,
        quantity=h.quantity,
        average_cost=h.average_cost,
        notes=h.notes,
    )


@router.get("", response_model=list[PortfolioRead])
def list_portfolios(db: Session = Depends(get_db)):
    rows = db.query(Portfolio).order_by(Portfolio.id).all()
    return rows


@router.post("", response_model=PortfolioRead, status_code=201)
def create_portfolio(body: PortfolioCreate, db: Session = Depends(get_db)):
    p = Portfolio(
        name=body.name,
        description=body.description,
        base_currency=body.base_currency,
    )
    db.add(p)
    db.commit()
    db.refresh(p)
    return p


@router.get("/{portfolio_id}", response_model=PortfolioDetail)
def get_portfolio(portfolio_id: int, db: Session = Depends(get_db)):
    p = db.query(Portfolio).filter(Portfolio.id == portfolio_id).first()
    if not p:
        raise HTTPException(status_code=404, detail="Portfolio not found")
    return PortfolioDetail(
        id=p.id,
        name=p.name,
        description=p.description,
        base_currency=p.base_currency,
        created_at=p.created_at,
        holdings=[_to_holding_read(h) for h in p.holdings],
    )


@router.patch("/{portfolio_id}", response_model=PortfolioRead)
def update_portfolio(
    portfolio_id: int, body: PortfolioUpdate, db: Session = Depends(get_db)
):
    p = db.query(Portfolio).filter(Portfolio.id == portfolio_id).first()
    if not p:
        raise HTTPException(status_code=404, detail="Portfolio not found")
    if body.name is not None:
        p.name = body.name
    if body.description is not None:
        p.description = body.description
    if body.base_currency is not None:
        p.base_currency = body.base_currency
    db.commit()
    db.refresh(p)
    return p


@router.delete("/{portfolio_id}", status_code=204)
def delete_portfolio(portfolio_id: int, db: Session = Depends(get_db)):
    p = db.query(Portfolio).filter(Portfolio.id == portfolio_id).first()
    if not p:
        raise HTTPException(status_code=404, detail="Portfolio not found")
    db.delete(p)
    db.commit()
    return None


@router.post("/{portfolio_id}/holdings", response_model=HoldingRead, status_code=201)
def add_holding(portfolio_id: int, body: HoldingCreate, db: Session = Depends(get_db)):
    p = db.query(Portfolio).filter(Portfolio.id == portfolio_id).first()
    if not p:
        raise HTTPException(status_code=404, detail="Portfolio not found")
    h = Holding(
        portfolio_id=portfolio_id,
        asset_type=AssetType(body.asset_type.value),
        ticker=body.ticker,
        name=body.name,
        quantity=body.quantity,
        average_cost=body.average_cost,
        notes=body.notes,
    )
    db.add(h)
    db.commit()
    db.refresh(h)
    return _to_holding_read(h)


@router.patch("/holdings/{holding_id}", response_model=HoldingRead)
def update_holding(holding_id: int, body: HoldingUpdate, db: Session = Depends(get_db)):
    h = db.query(Holding).filter(Holding.id == holding_id).first()
    if not h:
        raise HTTPException(status_code=404, detail="Holding not found")
    if body.asset_type is not None:
        h.asset_type = AssetType(body.asset_type.value)
    if body.ticker is not None:
        h.ticker = body.ticker
    if body.name is not None:
        h.name = body.name
    if body.quantity is not None:
        h.quantity = body.quantity
    if body.average_cost is not None:
        h.average_cost = body.average_cost
    if body.notes is not None:
        h.notes = body.notes
    db.commit()
    db.refresh(h)
    return _to_holding_read(h)


@router.delete("/holdings/{holding_id}", status_code=204)
def delete_holding(holding_id: int, db: Session = Depends(get_db)):
    h = db.query(Holding).filter(Holding.id == holding_id).first()
    if not h:
        raise HTTPException(status_code=404, detail="Holding not found")
    db.delete(h)
    db.commit()
    return None


async def _build_summary(p: Portfolio, db: Session) -> PortfolioSummary:
    holdings = db.query(Holding).filter(Holding.portfolio_id == p.id).all()
    hv: list[HoldingValuation] = []
    total_cost = 0.0
    nav_components: list[float] = []

    for h in holdings:
        cost_basis = round(h.quantity * h.average_cost, 4)
        total_cost += cost_basis
        mp = await pricing.price_for_holding(h.asset_type.value, h.ticker)
        if h.asset_type == AssetType.cash:
            mv = round(h.quantity, 4)
            upnl = round(mv - cost_basis, 4)
            mp_out = 1.0
            nav_components.append(mv)
        elif mp is not None:
            mv = round(h.quantity * mp, 4)
            upnl = round(mv - cost_basis, 4)
            mp_out = mp
            nav_components.append(mv)
        else:
            mv = None
            upnl = None
            mp_out = None
            nav_components.append(cost_basis)

        hv.append(
            HoldingValuation(
                holding_id=h.id,
                asset_type=h.asset_type.value,
                ticker=h.ticker,
                quantity=h.quantity,
                market_price=mp_out,
                market_value=mv,
                cost_basis=cost_basis,
                unrealized_pnl=upnl,
            )
        )

    total_market_value = round(sum(nav_components), 2)
    unrealized = round(total_market_value - total_cost, 2)

    allocation: dict[str, float] = {}
    if total_market_value > 0:
        for h in hv:
            part = h.market_value if h.market_value is not None else h.cost_basis
            key = h.asset_type
            allocation[key] = allocation.get(key, 0.0) + part
        for k in list(allocation.keys()):
            allocation[k] = round(100.0 * allocation[k] / total_market_value, 2)

    return PortfolioSummary(
        portfolio_id=p.id,
        name=p.name,
        base_currency=p.base_currency,
        total_cost=round(total_cost, 2),
        total_market_value=total_market_value,
        unrealized_pnl=unrealized,
        allocation_pct=allocation,
        holdings=hv,
    )


@router.get("/{portfolio_id}/summary", response_model=PortfolioSummary)
async def portfolio_summary(portfolio_id: int, db: Session = Depends(get_db)):
    p = db.query(Portfolio).filter(Portfolio.id == portfolio_id).first()
    if not p:
        raise HTTPException(status_code=404, detail="Portfolio not found")
    return await _build_summary(p, db)


@router.get("/{portfolio_id}/performance", response_model=PerformanceSeries)
async def portfolio_performance(
    portfolio_id: int, days: int = 30, db: Session = Depends(get_db)
):
    p = db.query(Portfolio).filter(Portfolio.id == portfolio_id).first()
    if not p:
        raise HTTPException(status_code=404, detail="Portfolio not found")
    days = max(7, min(days, 365))
    holdings = db.query(Holding).filter(Holding.portfolio_id == p.id).all()

    cash_total = sum(
        h.quantity for h in holdings if h.asset_type == AssetType.cash
    )
    stock_weights: list[tuple[str, float]] = []
    for h in holdings:
        if h.asset_type in (AssetType.stock, AssetType.bond) and h.ticker:
            stock_weights.append((h.ticker, h.quantity))

    series = await pricing.portfolio_value_series(stock_weights, days)
    points: list[PerformancePoint] = []
    for dt, v in series:
        points.append(
            PerformancePoint(date=dt.date(), value=round(v + cash_total, 2))
        )
    if not points and cash_total > 0:
        today = datetime.now(timezone.utc).date()
        for i in range(min(days, 30)):
            d = today - timedelta(days=days - 1 - i)
            points.append(PerformancePoint(date=d, value=round(cash_total, 2)))
    return PerformanceSeries(portfolio_id=p.id, days=days, points=points)
