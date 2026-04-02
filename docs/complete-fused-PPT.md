# 投资组合管理系统 · 项目演示 PPT（完整逐页版，15–20 分钟）

> 受众：讲师、直属经理、公司相关利益方（3 人小组同场演示）
>
> 结构：开头（任务与协作）→ 主体（成果、数据模型、架构、现场演示）→ 结尾（挑战反思、未来规划、提问）
>
> 小组成员简称（按你们项目约定）：
> - `Sen` = senmiao1226
> - `Miaofighting` = miaofighting
> - `Wwovo` = wwovo

---

## Slide 1｜封面（0:30）
**讲稿标题**
- 投资组合管理系统 · 项目演示（Java Spring Boot 3 + Vue 3）
**要点**
- 团队：`Sen / Miaofighting / Wwovo`
- 日期：XXXX-XX-XX
- 版本：v1.0
**Speaker（谁讲）**：任意一人共同开场
**一句话讲清**：我们用“一条数据链路”把组合管理、行情获取、估值分析和可视化串起来。

---

## Slide 2｜团队介绍（1:00）
**讲稿标题**
- 团队与分工（按端到端亮点归属）
**要点（每人 2 行）**
- `Miaofighting`：组合/持仓/估值口径与分析链路（Portfolio、Analytics、DTO 字段契约）
- `Sen`：行情多源/故障转移/缓存策略与 Market 体验（PricingService、MarketService）
- `Wwovo`：挑战复盘与演示叙事统一（把问题-教训-修复-改进讲清楚；材料对齐）
**Speaker**：负责人轮流介绍（建议 1 人一句）

---

## Slide 3｜开场：项目与学习背景（任务要求、可用时间）（1:30）
**讲稿标题**
- 我们做什么？为什么做？在什么约束下完成？
**要点**
- 项目定位：全栈投资组合管理平台（持仓管理、行情追踪、业绩分析、可视化）
- 学习任务（3–5 条，按你们课程要求选用）
  - 前后端分离 + REST API
  - 持久化（MySQL + JPA）
  - 行情数据集成与可用性保障（缓存/降级）
  - 图表展示与交互体验（Dashboard/Market/Analytics）
- 可用时间与节奏：**总周期 4 天**（建议口径，可直接读）
  - Day1：环境搭建 + 数据模型（`Portfolio/ Holding`）落库 + 基础接口联通
  - Day2：组合/持仓 CRUD + 估值摘要（市值/成本/盈亏）对齐字段口径
  - Day3：行情聚合（多源策略 + 缓存/降级）+ 前端页面联调（Dashboard/Market）
  - Day4：分析页（Analytics）+ 可视化（饼图/SVG）+ 统一修复与演示打磨
**Speaker**：`Wwovo`（开场叙事最适合）

---

## Slide 4｜开场：项目实施思路（2:00）
**讲稿标题**
- 从“闭环”出发：先跑通主链路，再加可靠性与体验
**要点**
- 主链路：`组合/持仓` → `估值摘要（市值/盈亏/分布）` → `分析（配置/走势/集中度）`
- 可靠性链路：`价格多源` → `缓存（TTL）` → `故障转移（逐源尝试）`
- 工程化链路：`Swagger/Health` → `配置化（application.yml）` → `文档可复现`
**Speaker**：`Miaofighting`

---

## Slide 5｜角色分工与协作方式（1:30）
**讲稿标题**
- 如何在小组内把“亮点”共同做出来（不是简单前后端切分）
**要点（可直接放进 PPT 的表格）**
| 端到端亮点 | 主负责 | 协作方 | 交付物 |
|---|---|---|---|
| 组合/持仓 CRUD + 估值口径（成本/市值/盈亏/cash 规则） | `Miaofighting` | `Sen`（价格/降级语义） | Portfolio/PortfolioService + DTO/字段定义 |
| 行情多源与故障转移、缓存 TTL、历史序列可用性 | `Sen` | `Miaofighting`（估值与降级联动） | PricingService/MarketService + 稳定响应行为 |
| Dashboard/Market/Analytics 页面联动、加载/错误/重试体验 | `Miaofighting`（主体）/ `Sen`（数据演示） | `Wwovo`（演示脚本与对齐） | 对应 `.vue` 页面 + i18n |
| 踩坑复盘：NPE / CORS / 日期格式 / double 精度 & 改进空间 | `Wwovo` | `Miaofighting`/`Sen`（确认根因与修复） | Challenges & Lessons + 下一步规划 |
**协作方式（补一句话）**
- 联调以“页面能跑通 + 数据可解释”为准，遇到外部 API 不稳定先保证降级路径可展示。
**Speaker**：`Wwovo`

---

## Slide 6｜使用的技术与工具（1:00）
**讲稿标题**
- 技术栈与工程能力（能支撑亮点）
**要点**
- 后端：Java 17、Spring Boot 3.x、JPA/Hibernate、MySQL 8
- API 文档：SpringDoc OpenAPI（`/swagger-ui.html`）
- 前端：Vue 3 + Vite、Vue Router、i18n（中英文切换）、并行加载（Dashboard）
- 外部服务：行情数据源（多 provider + 逐源尝试）与课程缓存接口
- 可运维基础：`/health` 健康检查、`application.yml` 配置化（数据源/跨域/汇率）
**Speaker**：`Sen`

---

## Slide 7｜成果展示（产品能力全景，客户可看懂）（2:00）
**讲稿标题**
- 一站式：从“总览”到“明细”，再到“分析”
**要点**
- Dashboard：KPI 卡片（总资产/总成本/未实现盈亏/组合数量）、资产分布、市场概览、组合列表
- Portfolio：创建组合、管理持仓、展示估值摘要与资产分布，支持查看股票历史走势
- Market：热门股票/指数、持仓行情、涨跌榜、搜索与个股详情弹窗（含历史展示）
- Analytics：资产配置（饼图）、历史走势（SVG）、业绩统计、前 N 大持仓/集中度
**Speaker**：`Miaofighting`

---

## Slide 8｜数据模型概览与设计思路（2:30）【主体核心】
**讲稿标题**
- 数据怎么存、怎么关联、为什么这样设计？
**要点**
- 核心实体（来自代码 domain）
  - `Portfolio`（表：`portfolios`）：`id/name/description/baseCurrency/createdAt`
  - `Holding`（表：`holdings`）：`portfolio_id/assetType/ticker/name/quantity/averageCost/purchaseDate/notes`
  - `AssetType`：stock/bond/fund/cash
- 关系设计：`Portfolio 1:N Holding`（JPA `@OneToMany` + `orphanRemoval`）
- 设计原则（口径一句话）
  - 持仓结构化落库，估值/收益/分析按需计算；对外部价格不可用做缓存与降级，保证交互闭环。
**Speaker**：`Miaofighting`

---

## Slide 9｜应用高层架构（1:30）
**讲稿标题**
- 请求如何走？数据如何闭环？（画简单图即可）
**要点（画图文字说明）**
- 浏览器（Vue）→ REST API（后端 Controller）→ 业务层 Service → MySQL（JPA）
- 估值/行情链路：`Service` 调用 `PricingService` → 外部行情 HTTP → 内存缓存 → 返回前端展示
**Speaker**：`Sen`（更贴合 Pricing 与数据流）

---

## Slide 10｜数据源、缓存与故障转移（1:45）【亮点页】
**讲稿标题**
- 为什么“看起来实时”？为什么“不容易坏”？
**要点**
- 价格缓存（TTL）
  - 价格 TTL：5 分钟（减少重复请求）
  - 历史序列 TTL：10 分钟（提升图表加载稳定性）
- 多数据源优先级 + 故障转移
  - 逐源尝试：主源失败则继续下一个 provider
  - 按股票类型选择 provider（减少无效调用，提高命中率）
- 降级语义（演示可讲）
  - 如果最终仍不可用：后端返回空/`—`，前端不崩溃，页面保持可用
**Speaker**：`Sen`

---

## Slide 11｜现场演示（8:00）【主体演示页：把亮点“跑起来”】
**讲稿标题**
- 现场演示路径（Dashboard → Portfolio → Market → Analytics）
**建议你们在这一页按“步骤条”写演示口径：每步 60–90 秒**
1. `Dashboard`（KPI + 资产分布 + 组合列表）
   - 展示：总资产、总成本、未实现盈亏、组合数量、资产分布条形
   - 讲点：Dashboard 通过聚合多个接口并行加载，让“总览”快且稳定
2. `Portfolio`（持仓表 + 估值摘要 + 资产分布）
   - 展示：组合摘要（总成本/总市值/盈亏）与持仓明细
   - 讲点：估值口径在后端统一（cash 按面值，其他用 quantity * price；price 不可用走降级）
3. `Market`（搜索/热门/涨跌榜/详情弹窗）
   - 展示：搜索股票详情弹窗、热门股票列表、持仓涨跌榜
   - 讲点：价格由多源+缓存提供；失败不影响页面结构
4. `Analytics`（资产配置饼图 + 历史走势曲线 + 业绩统计）
   - 展示：资产配置饼图（纯 CSS conic-gradient）、SVG 折线走势、业绩统计面板
   - 讲点：前端用轻量 SVG 展示历史走势；切换时间范围并刷新数据
**Speaker**：`Miaofighting` 讲 1–2，`Sen` 讲 3，`Miaofighting` 讲 4；`Wwovo` 负责串词与卡点控制

---

## Slide 12｜质量、可运维与交付（1:15）
**讲稿标题**
- 做得出来只是起点：可交付、可对接、可验证
**要点**
- 接口自证：SpringDoc Swagger UI（`/swagger-ui.html`）+ OpenAPI（`/v3/api-docs`）
- 服务可验证：`/health` 返回 `status=ok`
- 配置化：`application.yml` 管理数据源优先级、跨域 allowed-origins、汇率等
- 前后端联调问题处理：CORS 与代理（本地运行可用）
**Speaker**：`Wwovo`（以“评审视角”总结）

---

## Slide 13｜挑战与反思（1:45）【必须诚实具体】
**讲稿标题**
- 我们踩过哪些坑？怎么修？下次怎么做更好？
**要点（每条不超过 2 行）**
1. `NPE（空指针）`
   - 问题：外部 API 返回空时直接计算 `quantity * marketPrice`
   - 修复：用 `Optional` + 降级方案（不可用时使用成本/或空值逻辑）
2. `CORS 跨域`
   - 问题：浏览器拦截 `fetch` 请求
   - 修复：后端 CORS + 前端 Vite proxy 双保险
3. `日期格式不一致`
   - 问题：后端 `Instant` 序列化与前端解析不一致导致 Invalid Date
   - 修复：统一 ISO 8601 / JsonFormat 口径
4. `double 精度问题`
   - 风险：浮点计算误差累计影响显示
   - 改进：四舍五入到固定小数位（round4/round2）并在关键口径处统一
**Speaker**：`Wwovo`

---

## Slide 14｜团队协作情况（0:55）
**讲稿标题**
- 协作不是“分工表”，而是“闭环共担”
**要点**
- 快速迭代：每个 Sprint 一个可演示的增量（能跑通一条链路）
- 合并策略：接口字段与页面展示先对齐，再做 UI 完善
- 风险处理：当外部 API 不稳定时，先保证降级路径可展示，再继续增强
**Speaker**：三人共同（各一句，别抢）

---

## Slide 15｜未来规划（1:30）
**讲稿标题**
- 时间充裕时，我们下一步会做什么？
**要点（写 3–4 条即可）**
- 产品增强
  - 权限/认证（用户体系，支持多用户多组合）
  - 交易历史/分红/税务报表（更贴近真实投资管理）
- 工程增强
  - 更强缓存：引入 Redis（提升跨实例一致性与性能）
  - 实时推送：WebSocket（行情变更实时刷新）
  - CI/CD 自动化部署与测试覆盖率提升
**Speaker**：`Sen` 或 `Miaofighting`（都可，建议由更了解技术下一步的人讲）

---

## Slide 16｜总结与致谢 / 提问（0:40–0:60）
**讲稿标题**
- 总结 & Q&A
**要点**
- 总结一句话：我们用数据模型 + 分层架构 + 多源行情缓存，把组合管理与分析做成可演示的闭环
- 最大收获（两句，分别给业务/技术）
  - 业务：功能闭环与可解释的估值展示
  - 技术：多源数据可靠性 + 工程化交付
- 感谢聆听，接受提问
**Speaker**：三人共同

---

## 现场演示准备清单（你们可以在 PPT 最后附录里写，不算正文页）
- 准备演示组合（2 个 portfolio + 3–6 条 holding）
- 选择 3–4 个固定 ticker（确保命中率高、演示稳定）
- 说明跨域/端口：后端 `8080`、前端 `5173`（或你们实际端口）
- 演示时先演示 `Dashboard` 再进入 `Portfolio` 避免首屏等待过久

