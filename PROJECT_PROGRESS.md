# Portfolio Management Project - Progress Tracker

## Project Overview

A full-stack portfolio management application for tracking investments across stocks, bonds, funds, ETFs, crypto, commodities, and cash.

## Tech Stack

### Backend (Java)
- **Framework:** Spring Boot 3.x
- **Database:** MySQL with JPA/Hibernate
- **Build Tool:** Maven
- **API Docs:** SpringDoc OpenAPI (Swagger UI at `/swagger-ui.html`)

### Backend (Python - Legacy)
- **Framework:** FastAPI
- **Database:** SQLAlchemy with MySQL
- **Status:** Deprecated, kept for reference

### Frontend
- **Framework:** Vue.js 3
- **Build Tool:** Vite
- **HTTP Client:** Native fetch API

## Features

### Completed Features

#### Portfolio Management
- [x] Create, read, update, delete portfolios
- [x] Add/remove/update holdings in portfolios
- [x] Portfolio summary with total cost, market value, unrealized P&L
- [x] Asset allocation by type

#### Market Data Integration
- [x] Multi-source pricing service with fallback chain:
  - Massive.com (primary)
  - Alpha Vantage (secondary)
  - Sina Finance (tertiary - A-shares, HK, US stocks)
  - Yahoo Finance (fallback)
  - Cached price data (last resort)
- [x] Price caching (5-minute TTL)
- [x] Historical price series for charts

#### Dashboard
- [x] Portfolio overview with key metrics
- [x] Asset distribution visualization
- [x] Market overview with popular stocks
- [x] Top gainers/losers tracking

#### Analytics
- [x] Portfolio performance over time
- [x] Asset allocation analysis
- [x] Historical value tracking
- [x] Performance statistics

#### API & Configuration
- [x] CORS configuration for frontend integration
- [x] Environment-based configuration (YAML)
- [x] Database initialization scripts

### In Progress / Known Issues

- [ ] Sina Finance API - May require additional header adjustments for production use
- [ ] Massive.com API - Rate limiting handling improvements
- [ ] Frontend form validation for holding creation (quantity > 0)

### Planned Features

- [ ] User authentication & authorization
- [ ] Real-time price updates via WebSocket
- [ ] Dividend tracking
- [ ] Transaction history
- [ ] Tax reporting
- [ ] Mobile app (React Native/Flutter)
- [ ] Data export (CSV, PDF)
- [ ] Email alerts for price movements

## API Endpoints

### Portfolios
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/portfolios` | List all portfolios |
| POST | `/api/portfolios` | Create new portfolio |
| GET | `/api/portfolios/{id}` | Get portfolio details |
| PATCH | `/api/portfolios/{id}` | Update portfolio |
| DELETE | `/api/portfolios/{id}` | Delete portfolio |
| GET | `/api/portfolios/{id}/summary` | Get portfolio summary |
| GET | `/api/portfolios/{id}/performance` | Get performance history |
| POST | `/api/portfolios/{id}/holdings` | Add holding to portfolio |

### Holdings
| Method | Endpoint | Description |
|--------|----------|-------------|
| PATCH | `/api/portfolios/holdings/{holdingId}` | Update holding |
| DELETE | `/api/portfolios/holdings/{holdingId}` | Delete holding |

### Dashboard
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/dashboard/summary` | Dashboard summary |
| GET | `/api/dashboard/portfolios` | Portfolio list for dashboard |
| GET | `/api/dashboard/asset-distribution` | Global asset distribution |
| GET | `/api/dashboard/market-overview` | Market overview |
| GET | `/api/dashboard/full` | Full dashboard data |

### Market Data
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/market/my-holdings/{portfolioId}` | Market data for portfolio holdings |
| GET | `/api/market/all-holdings` | Market data for all holdings |
| GET | `/api/market/stocks/popular` | Popular stocks |
| GET | `/api/market/indexes` | Market indexes |
| GET | `/api/market/search?ticker={ticker}` | Search stock by ticker |
| GET | `/api/market/asset/{ticker}` | Asset detail |
| GET | `/api/market/movers/gainers/{portfolioId}` | Top gainers |
| GET | `/api/market/movers/losers/{portfolioId}` | Top losers |
| GET | `/api/market/providers` | List data providers |

### Analytics
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/analytics/allocation/{portfolioId}` | Portfolio asset allocation |
| GET | `/api/analytics/allocation/global` | Global asset allocation |
| GET | `/api/analytics/history/{portfolioId}?days={days}` | Portfolio history |
| GET | `/api/analytics/history/global?days={days}` | Global history |
| GET | `/api/analytics/performance/{portfolioId}` | Performance stats |
| GET | `/api/analytics/performance/global` | Global performance |
| GET | `/api/analytics/top-holdings/{portfolioId}?topN={n}` | Top holdings |

## Recent Changes

### 2026-04-01
- Fixed CORS configuration to support `localhost:5174`
- Updated Sina Finance API integration with improved headers and stock symbol handling
- Fixed HoldingCreateRequest validation (quantity must be > 0)

## Development Setup

### Prerequisites
- Java 17+
- Maven 3.8+
- MySQL 8.0+
- Node.js 18+

### Backend (Java)
```bash
cd portfolio-management-java
mvn spring-boot:run
```

### Frontend
```bash
cd portfolio-management-app/frontend
npm install
npm run dev
```

### Database Setup
```bash
mysql -u root -p < portfolio-management-java/sql/init_portfolio_db.sql
```

## Configuration

Key configuration in `application.yml`:
- Database connection (MySQL)
- CORS allowed origins
- API keys for market data providers
- Data source priority order

## Notes

- Sina Finance API requires proper browser headers to avoid 403 errors
- Price data is cached for 5 minutes to reduce API calls
- All monetary values are stored and returned in the portfolio's base currency
