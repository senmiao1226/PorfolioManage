## Portfolio Management (Java + Spring Boot)

### 功能亮点

1. **组合 / 持仓 CRUD**
   - `Portfolio`：创建、查询、更新、删除
   - `Holding`：新增、修改、删除（支持 `stock`、`bond`、`cash`）
2. **汇总与估值**
   - 计算 `totalCost`、`totalMarketValue`、`unrealizedPnl`
   - 给出按资产类型的 `allocationPct` 以及每个持仓的估值明细
3. **业绩曲线（Performance Series）**
   - `GET /portfolios/{id}/performance?days=30`
   - 后端按“日汇总市值”生成折线数据；前端提供 `SVG` Hover 提示
4. **行情获取：缓存 + 回退**
   - 股票/债券价格：先请求课程提供的 `cachedPriceData`
   - 失败则回退到 Yahoo 历史数据（最近几天），并对结果做了短期内存缓存，减少重复拉取

### 启动后端

1. 确保 MySQL 已启动，并执行建库脚本：

   - `sql/init_portfolio_db.sql`

2. 启动：

   - `mvn spring-boot:run`

3. 验证：
   - `http://127.0.0.1:8080/health`
   - `http://127.0.0.1:8080/swagger-ui.html`

### 启动前端（可选）

当前仓库里前端在 `portfolio-management-app/frontend`，启动后浏览器运行在 `5173`：

1. 先启动后端（8080）
2. 在前端目录执行：
   - `npm install`
   - `npm run dev`

浏览器打开：
- `http://127.0.0.1:5173`

前端会通过 Vite 代理把 `/api` 请求转发到后端 `8080`。

---

## 附录 C：实用链接

- 从雅虎财经读取实时价格并展示的简易 UI：https://bitbucket.org/fcallaly/simple-price-ui

## 附录 D：金融数据来源

金融数据可从雅虎财经获取。

Java 项目（推荐库）：
- https://github.com/sstrickx/yahoofinance-api

示例 REST API（用于获取模拟/缓存金融数据）：
- https://c4rm9elh30.execute-api.us-east-1.amazonaws.com/default/cachedPriceData?ticker=TSLA

默认支持标的（课程接口）：
- `C`、`AMZN`、`TSLA`、`FB`、`AAPL`

当前项目使用上述课程缓存接口减少请求次数：

- Spring 配置中 `app.pricing.cached-price-base` 指向 `cachedPriceData`
- 价格获取逻辑：
  - 先尝试课程缓存接口（`PricingService.fetchCachedPrice`）
  - 若失败则回退到 Yahoo 历史数据（`PricingService.fetchYahooAdjCloseSeries`）


