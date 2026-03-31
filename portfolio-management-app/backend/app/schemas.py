from datetime import date, datetime
from enum import Enum

from pydantic import BaseModel, Field, field_validator, model_validator


class AssetTypeEnum(str, Enum):
    stock = "stock"
    bond = "bond"
    cash = "cash"


class HoldingBase(BaseModel):
    asset_type: AssetTypeEnum
    ticker: str | None = None
    name: str | None = None
    quantity: float = Field(gt=0)
    average_cost: float = Field(ge=0)
    notes: str | None = None

    @field_validator("ticker")
    @classmethod
    def normalize_ticker(cls, v: str | None) -> str | None:
        return v.upper().strip() if v else None

    @model_validator(mode="after")
    def ticker_required_for_instruments(self):
        if self.asset_type in (AssetTypeEnum.stock, AssetTypeEnum.bond) and not self.ticker:
            raise ValueError("ticker is required for stock and bond")
        return self


class HoldingCreate(HoldingBase):
    pass


class HoldingUpdate(BaseModel):
    asset_type: AssetTypeEnum | None = None
    ticker: str | None = None
    name: str | None = None
    quantity: float | None = Field(default=None, gt=0)
    average_cost: float | None = Field(default=None, ge=0)
    notes: str | None = None


class HoldingRead(HoldingBase):
    id: int
    portfolio_id: int

    class Config:
        from_attributes = True


class PortfolioBase(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    description: str | None = None
    base_currency: str = "USD"


class PortfolioCreate(PortfolioBase):
    pass


class PortfolioUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=200)
    description: str | None = None
    base_currency: str | None = None


class PortfolioRead(PortfolioBase):
    id: int
    created_at: datetime

    class Config:
        from_attributes = True


class PortfolioDetail(PortfolioRead):
    holdings: list[HoldingRead] = []


class HoldingValuation(BaseModel):
    holding_id: int
    asset_type: str
    ticker: str | None
    quantity: float
    market_price: float | None
    market_value: float | None
    cost_basis: float
    unrealized_pnl: float | None


class PortfolioSummary(BaseModel):
    portfolio_id: int
    name: str
    base_currency: str
    total_cost: float
    total_market_value: float | None
    unrealized_pnl: float | None
    allocation_pct: dict[str, float]
    holdings: list[HoldingValuation]


class PerformancePoint(BaseModel):
    date: date
    value: float


class PerformanceSeries(BaseModel):
    portfolio_id: int
    days: int
    points: list[PerformancePoint]
