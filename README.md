# 投资组合管理系统

一个基于 Spring Boot 3 + Vue 3 的现代化投资组合管理平台，支持多市场、多数据源的实时价格追踪和业绩分析。

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen)
![Vue](https://img.shields.io/badge/Vue-3.2-green)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)

## 📁 项目结构

```
portfolio/
├── portfolio-management-java/      # 后端 (Spring Boot)
│   ├── src/main/java/
│   │   └── com/training/portfolio/
│   │       ├── config/             # 配置类
│   │       ├── domain/             # 实体类
│   │       ├── dto/                # 数据传输对象
│   │       ├── repository/         # JPA Repository
│   │       ├── service/            # 业务逻辑
│   │       └── web/                # Controller
│   ├── src/main/resources/
│   │   └── application.yml         # 配置文件
│   └── sql/                        # 数据库脚本
├── portfolio-management-app/
│   └── frontend/                   # 前端 (Vue 3 + Vite)
│       └── src/
│           ├── api.js              # API 调用封装
│           ├── views/              # 页面组件
│           └── router/             # 路由配置
└── README.md
```

## ✨ 功能特性

### 1. 投资组合管理
- ✅ 创建、查看、更新、删除投资组合
- ✅ 支持多币种（RMB、USD 等）
- ✅ 持仓管理（股票、债券、现金）
- ✅ 实时计算持仓市值和盈亏

### 2. 多数据源价格获取
- ✅ **Massive.com** - 全球金融市场数据（主数据源）
- ✅ **Alpha Vantage** - 美股、外汇数据（备用）
- ✅ **新浪财经** - A 股、港股数据（备用）
- ✅ **Yahoo Finance** - 全球市场数据（备用）
- ✅ **缓存接口** - 减少 API 调用（最后备选）

### 3. 智能故障转移
```
价格查询流程：
Massive → Alpha Vantage → 新浪财经 → Yahoo → 缓存
```
- 自动检测数据源可用性
- 故障时自动切换到备用数据源
- 5 分钟内存缓存，减少重复请求

### 4. 业绩分析
- ✅ 投资组合历史走势（7/30/90/365 天）
- ✅ 资产配置饼图
- ✅ 涨跌排行榜
- ✅ 业绩统计指标

### 4.1 新增：交互式图表与买入日收益率基准
- ✅ 股票历史走势弹窗图表支持 **鼠标 hover 交互**（十字定位线 + tooltip：日期/价格/收益率）
- ✅ 图表中的收益率基准支持切换为持仓的 **买入日期 `purchaseDate`**：
  - 在买入日 hover 时显示“相对买入日”的 Return%
  - 若买入日为非交易日，后端会回退到最近交易日作为基准，避免 Massive `404 Data not found`
- ✅ 图表弹窗整体视觉增强：更大宽高、更合理的图下方统计布局（更贴近专业股票网站的观感）
- ✅ 持仓表展示 `Buy-in Date`，点击对应持仓直接打开该股票的图表

### 4.2 新增：Fund 类型支持
- ✅ 前端已支持 `fund` 添加到 portfolio，并在保存失败场景下修复后端数据库 enum：
  - 运行时自动确保 `holdings.asset_type` 包含 `fund`，避免 `fund` 保存失败

### 4.3 新增：搜索结果直接打开图表
- ✅ 市场页面“股票搜索结果”可直接点击行打开图表（不用依赖额外的历史/market history 按钮）

### 5. 市场行情
- ✅ 实时股票价格
- ✅ 市场指数行情
- ✅ 热门股票监控
- ✅ 持仓股票排行

## 🚀 快速开始

### 环境要求
- JDK 17+
- Node.js 16+
- MySQL 8.0+
- Maven 3.6+

### 1. 数据库初始化

```bash
# 进入后端目录
cd portfolio-management-java

# 执行建库脚本
mysql -u root -p < sql/init_portfolio_db.sql
```

### 2. 配置后端

编辑 `portfolio-management-java/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/portfolio_db?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password

app:
  pricing:
    massive-base: "https://api.massive.com/v1"
    massive-api-key: "YOUR_MASSIVE_API_KEY"
    alpha-vantage-base: "https://www.alphavantage.co/query"
    alpha-vantage-api-key: "YOUR_ALPHA_VANTAGE_API_KEY"
    sina-base: "https://hq.sinajs.cn"
    providers: "massive,alphavantage,sina,yahoo,cached"
```

### 3. 启动后端

```bash
cd portfolio-management-java
mvn clean spring-boot:run
```

访问：
- 健康检查：http://localhost:8080/health
- API 文档：http://localhost:8080/swagger-ui.html

### 4. 启动前端

```bash
cd portfolio-management-app/frontend
npm install
npm run dev
```

访问：http://localhost:5173

## 📊 API 接口

### 投资组合接口
```
GET    /api/portfolios              # 获取所有组合
POST   /api/portfolios              # 创建组合
GET    /api/portfolios/{id}         # 获取组合详情
PATCH  /api/portfolios/{id}         # 更新组合
DELETE /api/portfolios/{id}         # 删除组合
GET    /api/portfolios/{id}/summary # 获取组合摘要
GET    /api/portfolios/{id}/performance?days=30  # 获取业绩走势
```

### 仪表盘接口
```
GET /api/dashboard/summary              # 仪表盘总览
GET /api/dashboard/portfolios           # 组合卡片列表
GET /api/dashboard/asset-distribution   # 资产分布
GET /api/dashboard/market-overview      # 市场概览
```

### 市场行情接口
```
GET /api/market/my-holdings/{portfolioId}    # 持仓行情
GET /api/market/all-holdings                 # 全局持仓行情
GET /api/market/stocks/popular               # 热门股票
GET /api/market/indexes                      # 市场指数
GET /api/market/asset/{ticker}               # 个股详情
```

### 投资分析接口
```
GET /api/analytics/allocation/{portfolioId}  # 资产配置
GET /api/analytics/history/{portfolioId}     # 历史走势
GET /api/analytics/performance/{portfolioId} # 业绩统计
GET /api/analytics/top-holdings/{portfolioId}# 前 N 大持仓
```

## 🔧 配置说明

### 数据源配置

```yaml
app:
  pricing:
    # 数据源优先级（从左到右）
    providers: "massive,alphavantage,sina,yahoo,cached"
    
    # Massive API（美股推荐）
    massive-base: "https://api.massive.com/v1"
    massive-api-key: "YOUR_KEY"
    
    # Alpha Vantage（美股备用）
    alpha-vantage-base: "https://www.alphavantage.co/query"
    alpha-vantage-api-key: "YOUR_KEY"
    
    # 新浪财经（A 股/港股）
    sina-base: "https://hq.sinajs.cn"
```

### 缓存配置

```yaml
# 价格缓存时间（毫秒）
PRICE_CACHE_TTL_MS = 5 * 60 * 1000L  # 5 分钟
SERIES_CACHE_TTL_MS = 10 * 60 * 1000L  # 10 分钟
```

## 📝 使用示例

### 1. 创建投资组合

```json
POST /api/portfolios
{
  "name": "成长组合",
  "baseCurrency": "RMB"
}
```

### 2. 添加持仓

```json
POST /api/portfolios/{id}/holdings
{
  "assetType": "stock",
  "ticker": "AAPL",
  "quantity": 100,
  "averageCost": 150.50
}
```

### 3. 查看组合摘要

```json
GET /api/portfolios/{id}/summary

{
  "totalCost": 15050.00,
  "totalMarketValue": 17543.00,
  "unrealizedPnl": 2493.00,
  "allocationPct": {
    "stock": 100.0
  },
  "holdings": [
    {
      "ticker": "AAPL",
      "quantity": 100,
      "marketPrice": 175.43,
      "marketValue": 17543.00,
      "unrealizedPnl": 2493.00
    }
  ]
}
```

## 🛠️ 开发指南

### 添加新数据源

1. 在 `application.yml` 添加配置
2. 在 `PricingService` 添加获取方法
3. 在 `priceForHolding` 添加到调度逻辑

### 添加新接口

1. 创建 Controller 类
2. 添加 `@RestController` 和 `@RequestMapping`
3. 定义接口方法
4. 在 Service 层实现业务逻辑

### 前端页面开发

```vue
<template>
  <div class="card">
    <h2>{{ title }}</h2>
    <div v-if="loading">加载中...</div>
    <div v-else>{{ data }}</div>
  </div>
</template>

<script>
import { req } from './api'

export default {
  async mounted() {
    this.data = await req('/api/endpoint')
  }
}
</script>
```

## 🐛 常见问题

### 1. 429 Too Many Requests
**原因：** API 请求频率超限  
**解决：** 等待 1-2 分钟或切换到备用数据源

### 2. 404 Not Found
**原因：** Controller 路径配置错误  
**解决：** 检查 `@RequestMapping` 是否包含 `/api` 前缀

### 3. CORS 错误
**原因：** 跨域配置问题  
**解决：** 检查 `WebConfig.java` 和 Vite 代理配置

### 4. 数据库连接失败
**原因：** MySQL 未启动或配置错误  
**解决：** 检查 MySQL 服务和 `application.yml` 配置

## 📈 性能优化

1. **缓存策略**
   - 价格数据缓存 5 分钟
   - 历史数据缓存 10 分钟
   - 减少重复 API 调用

2. **数据库优化**
   - 使用 JPA 懒加载
   - 添加索引优化查询
   - 批量操作减少事务次数

3. **前端优化**
   - 组件懒加载
   - 按需引入 UI 库
   - 使用虚拟滚动优化长列表

## 🔐 安全建议

1. **生产环境**
   - 使用环境变量存储 API Key
   - 启用 HTTPS
   - 添加身份验证

2. **数据验证**
   - 前端输入验证
   - 后端参数校验
   - SQL 注入防护

3. **错误处理**
   - 统一异常处理
   - 不暴露敏感信息
   - 记录详细日志

## 📄 许可证

MIT License

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📧 联系方式

- 作者：senmiao1226、miaofighting、wwovo
- 项目地址：https://github.com/senmiao1226/PorfolioManage

---

**最后更新：** 2024-03-31
