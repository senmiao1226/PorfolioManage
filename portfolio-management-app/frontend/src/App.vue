<template>
  <div class="app">
    <header class="header">
      <div class="header-row">
        <h1>投资组合管理</h1>
        <div class="badges">
          <span class="badge">缓存行情（服务端内存缓存）</span>
          <span class="badge">持仓 CRUD</span>
          <span class="badge">业绩曲线（SVG Hover）</span>
        </div>
      </div>
      <p class="sub">
        前端调用 Java API（经由 Vite 代理到 <code>8080</code>，后端支持：/portfolios、持仓、/performance）
      </p>
    </header>

    <div class="grid">
      <section class="card">
        <h2>新建组合</h2>
        <form class="form" @submit.prevent="createPortfolio">
          <input v-model="portfolioForm.name" placeholder="名称" required />
          <input v-model="portfolioForm.description" placeholder="描述（可选）" />
          <input v-model="portfolioForm.baseCurrency" placeholder="本币，如 USD" />
          <button type="submit" :disabled="portfolioBusy">创建</button>
        </form>
        <p v-if="error" class="err">{{ error }}</p>
      </section>

      <section class="card">
        <h2>组合列表</h2>
        <p v-if="listBusy && !items.length" class="muted">加载中…</p>
        <ul v-else class="list">
          <li v-for="p in items" :key="p.id" class="row">
            <button type="button" class="link" @click="selectPortfolio(p.id)">
              {{ p.name }} <span class="muted">#{{ p.id }}</span>
            </button>
            <button type="button" class="danger" @click="deletePortfolio(p.id)" :disabled="listBusy">
              删除
            </button>
          </li>
        </ul>
        <p v-if="!listBusy && !items.length" class="muted">
          暂无数据，请先创建组合或确认后端已启动。
        </p>
      </section>

      <section v-if="detail" class="card card-wide">
        <div class="detail-head">
          <div>
            <h2>详情：{{ detail.name }}</h2>
            <p class="muted">{{ detail.description || "—" }} · {{ detail.baseCurrency }}</p>
          </div>
          <div class="detail-actions">
            <button class="ghost" @click="refreshAll" :disabled="detailBusy">刷新</button>
          </div>
        </div>

        <div class="subgrid">
          <div class="panel">
            <div class="panel-head">
              <h3>持仓</h3>
              <button class="ghost" @click="openCreateHolding">新增持仓</button>
            </div>

            <table v-if="detail.holdings?.length" class="table">
              <thead>
                <tr>
                  <th>类型</th>
                  <th>标的</th>
                  <th>数量</th>
                  <th>平均成本</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="h in detail.holdings" :key="h.id">
                  <td>{{ h.assetType }}</td>
                  <td>{{ h.ticker || h.name || "—" }}</td>
                  <td>{{ h.quantity }}</td>
                  <td>{{ h.averageCost }}</td>
                  <td class="td-actions">
                    <button class="small" @click="openEditHolding(h)">编辑</button>
                    <button class="small danger" @click="deleteHolding(h.id)" :disabled="holdingBusy">
                      删除
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>

            <p v-else class="muted">暂无持仓，点击“新增持仓”开始配置。</p>
          </div>

          <div class="panel">
            <div class="panel-head">
              <h3>汇总</h3>
            </div>

            <div v-if="summaryLoading" class="muted">加载汇总…</div>
            <div v-else-if="summary">
              <div class="kpi-row">
                <div class="kpi">
                  <div class="kpi-label">总成本</div>
                  <div class="kpi-value">{{ fmtMoney(summary.totalCost) }}</div>
                </div>
                <div class="kpi">
                  <div class="kpi-label">总市值</div>
                  <div class="kpi-value">{{ fmtMoney(summary.totalMarketValue) }}</div>
                </div>
                <div class="kpi">
                  <div class="kpi-label">未实现盈亏</div>
                  <div class="kpi-value" :class="summary.unrealizedPnl >= 0 ? 'pos' : 'neg'">
                    {{ fmtMoney(summary.unrealizedPnl) }}
                  </div>
                </div>
              </div>

              <div class="alloc">
                <div class="alloc-title">资产分布（按市值）</div>
                <div v-if="summary.allocationPct && Object.keys(summary.allocationPct).length" class="bars">
                  <div
                    v-for="(pct, key) in summary.allocationPct"
                    :key="key"
                    class="bar-row"
                  >
                    <div class="bar-label">{{ key }}</div>
                    <div class="bar-track">
                      <div class="bar-fill" :style="{ width: pct + '%' }"></div>
                    </div>
                    <div class="bar-pct">{{ pct }}%</div>
                  </div>
                </div>
                <p v-else class="muted">暂无分布数据</p>
              </div>

              <div class="alloc">
                <div class="alloc-title">持仓估值</div>
                <table class="table compact">
                  <thead>
                    <tr>
                      <th>标的</th>
                      <th>市值</th>
                      <th>成本</th>
                      <th>未实现盈亏</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="h in summary.holdings" :key="h.holdingId">
                      <td>{{ h.ticker || h.assetType }}</td>
                      <td>{{ h.marketValue == null ? "—" : fmtMoney(h.marketValue) }}</td>
                      <td>{{ fmtMoney(h.costBasis) }}</td>
                      <td :class="(h.unrealizedPnl ?? 0) >= 0 ? 'pos' : 'neg'">
                        {{ h.unrealizedPnl == null ? "—" : fmtMoney(h.unrealizedPnl) }}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
            <div v-else class="muted">暂无汇总数据</div>
          </div>

          <div class="panel panel-full">
            <div class="panel-head perf-head">
              <h3>业绩曲线</h3>
              <div class="perf-controls">
                <label class="muted">近 {{ performanceDays }} 天</label>
                <input
                  type="range"
                  min="7"
                  max="365"
                  step="1"
                  v-model.number="performanceDays"
                  @change="loadPerformance"
                />
              </div>
            </div>

            <div v-if="performance?.points?.length" class="perf-meta">
              <div class="meta-line">
                近似最大：
                <span class="meta-val">{{ fmtMoney(perfMax?.value) }}</span>
                <span class="meta-date">({{ perfMax?.date }})</span>
              </div>
              <div class="meta-line">
                近似最小：
                <span class="meta-val">{{ fmtMoney(perfMin?.value) }}</span>
                <span class="meta-date">({{ perfMin?.date }})</span>
              </div>
            </div>

            <div v-if="performanceLoading" class="muted">加载曲线…</div>
            <div
              v-else-if="performance?.points?.length"
              class="chartWrap"
              ref="chartWrapRef"
              @mousemove="onChartMove"
              @mouseleave="hoverIndex = null"
              @click="onChartClick"
            >
              <svg class="chart" :viewBox="`0 0 ${chartW} ${chartH}`" preserveAspectRatio="none">
                <line
                  :x1="chartPadX"
                  :y1="chartH - chartPadY"
                  :x2="chartW - chartPadX"
                  :y2="chartH - chartPadY"
                  class="axis"
                />
                <polyline
                  :points="chartPolylinePoints"
                  class="poly"
                />
                <circle
                  v-if="hoverIndex !== null"
                  :cx="chartCoordOfHover?.x"
                  :cy="chartCoordOfHover?.y"
                  r="4"
                  class="hoverDot"
                />
                <circle
                  v-if="selectedIndex !== null"
                  :cx="chartCoordOfSelected?.x"
                  :cy="chartCoordOfSelected?.y"
                  r="5.5"
                  class="selectedDot"
                />
              </svg>
              <div v-if="hoverIndex !== null" class="chartTooltip">
                <div class="tt-date">{{ chartPointOfHover?.date }}</div>
                <div class="tt-val">{{ fmtMoney(chartPointOfHover?.value) }}</div>
              </div>
            </div>
            <div v-else class="muted">暂无曲线数据</div>
          </div>
        </div>
      </section>
    </div>

    <!-- Holding modal -->
    <div v-if="holdingModalOpen" class="modal-backdrop" @click.self="closeHoldingModal">
      <div class="modal">
        <h3 class="modal-title">
          {{ holdingModalMode === "create" ? "新增持仓" : "编辑持仓" }}
        </h3>

        <div class="modal-form">
          <div class="field">
            <label>资产类型</label>
            <select v-model="holdingForm.assetType">
              <option value="stock">stock</option>
              <option value="bond">bond</option>
              <option value="cash">cash</option>
            </select>
          </div>

          <div class="field" v-if="holdingForm.assetType === 'stock' || holdingForm.assetType === 'bond'">
            <label>Ticker</label>
            <input v-model="holdingForm.ticker" placeholder="如 AAPL" />
          </div>

          <div class="field">
            <label>名称（可选）</label>
            <input v-model="holdingForm.name" placeholder="如 Apple Inc.（可选）" />
          </div>

          <div class="field">
            <label>数量</label>
            <input type="number" step="0.01" v-model.number="holdingForm.quantity" />
          </div>

          <div class="field">
            <label>平均成本</label>
            <input type="number" step="0.01" v-model.number="holdingForm.averageCost" />
          </div>

          <div class="field">
            <label>备注（可选）</label>
            <textarea v-model="holdingForm.notes" rows="3" placeholder="添加备注（可选）"></textarea>
          </div>
        </div>

        <div v-if="modalError" class="err modal-err">{{ modalError }}</div>

        <div class="modal-actions">
          <button @click="submitHolding" :disabled="holdingBusy">
            {{ holdingModalMode === "create" ? "保存新增" : "保存修改" }}
          </button>
          <button class="ghost" @click="closeHoldingModal" :disabled="holdingBusy">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { api } from "./api";

const items = ref([]);
const detail = ref(null);
const summary = ref(null);
const performance = ref(null);

const error = ref("");
const modalError = ref("");

const listBusy = ref(false);
const portfolioBusy = ref(false);
const detailBusy = ref(false);
const summaryLoading = ref(false);
const performanceLoading = ref(false);
const holdingBusy = ref(false);

const portfolioForm = reactive({
  name: "",
  description: "",
  baseCurrency: "USD",
});

const performanceDays = ref(30);

const chartWrapRef = ref(null);
const hoverIndex = ref(null);
const selectedIndex = ref(null);

const chartW = 640;
const chartH = 240;
const chartPadX = 28;
const chartPadY = 22;

const holdingModalOpen = ref(false);
const holdingModalMode = ref("create"); // create | edit
const holdingForm = reactive({
  id: null,
  assetType: "stock",
  ticker: "",
  name: "",
  quantity: 1,
  averageCost: 0,
  notes: "",
});

function fmtMoney(v) {
  if (v == null || Number.isNaN(Number(v))) return "—";
  // 不依赖 currency code（避免未知货币导致 Intl 报错）
  const n = Number(v);
  return new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 2 }).format(n);
}

function clearError() {
  error.value = "";
  modalError.value = "";
}

async function loadList() {
  clearError();
  listBusy.value = true;
  try {
    items.value = await api.listPortfolios();
  } catch (e) {
    error.value = e.message || String(e);
  } finally {
    listBusy.value = false;
  }
}

async function createPortfolio() {
  clearError();
  portfolioBusy.value = true;
  try {
    await api.createPortfolio({
      name: portfolioForm.name,
      description: portfolioForm.description || null,
      baseCurrency: portfolioForm.baseCurrency || "USD",
    });
    portfolioForm.name = "";
    portfolioForm.description = "";
    await loadList();
  } catch (e) {
    error.value = e.message || String(e);
  } finally {
    portfolioBusy.value = false;
  }
}

async function selectPortfolio(id) {
  clearError();
  detailBusy.value = true;
  detail.value = null;
  summary.value = null;
  performance.value = null;
  hoverIndex.value = null;
  selectedIndex.value = null;
  try {
    detail.value = await api.getPortfolio(id);
    await loadSummary(id);
    await loadPerformance();
  } catch (e) {
    error.value = e.message || String(e);
  } finally {
    detailBusy.value = false;
  }
}

async function loadSummary(id) {
  summaryLoading.value = true;
  try {
    summary.value = await api.getSummary(id);
  } catch {
    summary.value = null;
  } finally {
    summaryLoading.value = false;
  }
}

async function loadPerformance() {
  if (!detail.value) return;
  performanceLoading.value = true;
  hoverIndex.value = null;
  selectedIndex.value = null;
  try {
    performance.value = await api.getPerformance(detail.value.id, performanceDays.value);
  } catch (e) {
    performance.value = null;
    error.value = e.message || String(e);
  } finally {
    performanceLoading.value = false;
  }
}

async function refreshAll() {
  if (!detail.value) return;
  await selectPortfolio(detail.value.id);
}

async function deletePortfolio(id) {
  if (!confirm("确定删除该组合？")) return;
  clearError();
  try {
    await api.deletePortfolio(id);
    if (detail.value?.id === id) {
      detail.value = null;
      summary.value = null;
      performance.value = null;
    }
    await loadList();
  } catch (e) {
    error.value = e.message || String(e);
  }
}

function resetHoldingFormForCreate() {
  holdingForm.id = null;
  holdingForm.assetType = "stock";
  holdingForm.ticker = "";
  holdingForm.name = "";
  holdingForm.quantity = 1;
  holdingForm.averageCost = 0;
  holdingForm.notes = "";
}

function openCreateHolding() {
  clearError();
  resetHoldingFormForCreate();
  holdingModalMode.value = "create";
  holdingModalOpen.value = true;
}

function openEditHolding(h) {
  clearError();
  holdingModalMode.value = "edit";
  holdingForm.id = h.id;
  holdingForm.assetType = h.assetType;
  holdingForm.ticker = h.ticker || "";
  holdingForm.name = h.name || "";
  holdingForm.quantity = h.quantity ?? 0;
  holdingForm.averageCost = h.averageCost ?? 0;
  holdingForm.notes = h.notes || "";
  holdingModalOpen.value = true;
}

function closeHoldingModal() {
  holdingModalOpen.value = false;
  modalError.value = "";
}

function holdingPayload() {
  const isCash = holdingForm.assetType === "cash";
  const ticker =
    isCash ? null : (holdingForm.ticker ?? "").trim() || null;

  return {
    assetType: holdingForm.assetType,
    ticker,
    name: holdingForm.name ? holdingForm.name : null,
    quantity: Number(holdingForm.quantity),
    averageCost: Number(holdingForm.averageCost),
    notes: holdingForm.notes ? holdingForm.notes : null,
  };
}

function validateHoldingForSubmit() {
  const payload = holdingPayload();
  if (payload.quantity == null || !Number.isFinite(payload.quantity) || payload.quantity <= 0) {
    return "数量必须为大于 0 的数字";
  }
  if (payload.averageCost == null || !Number.isFinite(payload.averageCost) || payload.averageCost < 0) {
    return "平均成本必须为 >= 0 的数字";
  }
  if (holdingForm.assetType === "stock" || holdingForm.assetType === "bond") {
    if (!payload.ticker) return "stock/bond 类型需要填写 ticker";
  }
  return "";
}

async function submitHolding() {
  if (!detail.value) return;
  modalError.value = "";
  const msg = validateHoldingForSubmit();
  if (msg) {
    modalError.value = msg;
    return;
  }

  holdingBusy.value = true;
  try {
    if (holdingModalMode.value === "create") {
      await api.addHolding(detail.value.id, holdingPayload());
    } else {
      await api.updateHolding(holdingForm.id, holdingPayload());
    }
    holdingModalOpen.value = false;
    await refreshAll();
  } catch (e) {
    modalError.value = e.message || String(e);
  } finally {
    holdingBusy.value = false;
  }
}

async function deleteHolding(holdingId) {
  if (!confirm("确定删除该持仓？")) return;
  clearError();
  holdingBusy.value = true;
  try {
    await api.deleteHolding(holdingId);
    await refreshAll();
  } catch (e) {
    error.value = e.message || String(e);
  } finally {
    holdingBusy.value = false;
  }
}

const performancePoints = computed(() => performance.value?.points ?? []);

const perfMax = computed(() => {
  const pts = performancePoints.value;
  if (!pts.length) return null;
  let bestIdx = 0;
  let bestVal = Number(pts[0]?.value);
  for (let i = 1; i < pts.length; i++) {
    const v = Number(pts[i]?.value);
    if (!Number.isFinite(v)) continue;
    if (v > bestVal) {
      bestVal = v;
      bestIdx = i;
    }
  }
  const p = pts[bestIdx];
  return p ? { date: p.date, value: p.value } : null;
});

const perfMin = computed(() => {
  const pts = performancePoints.value;
  if (!pts.length) return null;
  let bestIdx = 0;
  let bestVal = Number(pts[0]?.value);
  for (let i = 1; i < pts.length; i++) {
    const v = Number(pts[i]?.value);
    if (!Number.isFinite(v)) continue;
    if (v < bestVal) {
      bestVal = v;
      bestIdx = i;
    }
  }
  const p = pts[bestIdx];
  return p ? { date: p.date, value: p.value } : null;
});

const chartPolylinePoints = computed(() => {
  const pts = performancePoints.value;
  if (pts.length < 2) return "";
  return chartCoords.value.map((c) => `${c.x},${c.y}`).join(" ");
});

const chartCoords = computed(() => {
  const pts = performancePoints.value;
  if (pts.length === 0) return [];
  const values = pts.map((p) => Number(p.value)).filter((v) => Number.isFinite(v));
  if (values.length === 0) return [];
  const min = Math.min(...values);
  const max = Math.max(...values);
  const denom = max - min === 0 ? 1 : max - min;
  const n = pts.length;
  const usableW = chartW - chartPadX * 2;
  const usableH = chartH - chartPadY * 2;
  return pts.map((p, i) => {
    const x = chartPadX + usableW * (n === 1 ? 0 : i / (n - 1));
    const t = (Number(p.value) - min) / denom;
    const y = chartH - chartPadY - usableH * t;
    return { x, y };
  });
});

const chartCoordOfHover = computed(() => {
  if (hoverIndex.value === null) return null;
  return chartCoords.value[hoverIndex.value] ?? null;
});

const chartCoordOfSelected = computed(() => {
  if (selectedIndex.value === null) return null;
  return chartCoords.value[selectedIndex.value] ?? null;
});

const chartPointOfHover = computed(() => {
  if (hoverIndex.value === null) return null;
  return performancePoints.value[hoverIndex.value] ?? null;
});

function onChartMove(e) {
  const wrap = chartWrapRef.value;
  if (!wrap) return;
  const pts = performancePoints.value;
  if (!pts.length) return;
  const rect = wrap.getBoundingClientRect();
  const x = e.clientX - rect.left;
  const ratio = rect.width === 0 ? 0 : x / rect.width;
  const idx = Math.round(ratio * (pts.length - 1));
  hoverIndex.value = Math.max(0, Math.min(pts.length - 1, idx));
}

function onChartClick(e) {
  const wrap = chartWrapRef.value;
  if (!wrap) return;
  const pts = performancePoints.value;
  if (!pts.length) return;
  const rect = wrap.getBoundingClientRect();
  const x = e.clientX - rect.left;
  const ratio = rect.width === 0 ? 0 : x / rect.width;
  const idx = Math.round(ratio * (pts.length - 1));
  selectedIndex.value = Math.max(0, Math.min(pts.length - 1, idx));
}

onMounted(loadList);
</script>

<style scoped>
.app {
  max-width: 1100px;
  margin: 0 auto;
  padding: 1.5rem;
  font-family: system-ui, "Segoe UI", sans-serif;
  color: #1a1a1a;
}

.header-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.header h1 {
  margin: 0 0 0.25rem;
  font-size: 1.5rem;
}

.sub {
  margin: 0.25rem 0 1.25rem;
  font-size: 0.875rem;
  color: #555;
}

.badges {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.badge {
  background: #eef2ff;
  color: #3730a3;
  border: 1px solid #e0e7ff;
  border-radius: 999px;
  padding: 0.25rem 0.6rem;
  font-size: 0.75rem;
  white-space: nowrap;
}

.grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
}

.card {
  border: 1px solid #e5e5e5;
  border-radius: 12px;
  padding: 1rem 1.25rem;
  background: #fafafa;
}

.card-wide {
  padding: 1rem 1.25rem;
}

.card h2 {
  margin: 0 0 0.75rem;
  font-size: 1.1rem;
}

.form {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}

.form input,
select,
textarea {
  padding: 0.45rem 0.55rem;
  border: 1px solid #ccc;
  border-radius: 8px;
  background: #fff;
}

.form input {
  min-width: 140px;
}

button {
  padding: 0.45rem 0.9rem;
  border-radius: 8px;
  border: 1px solid #333;
  background: #222;
  color: #fff;
  cursor: pointer;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.ghost {
  background: transparent;
  color: #0b57d0;
  border-color: #d7e7ff;
}

.err {
  color: #b3261e;
  margin: 0.5rem 0 0;
  font-size: 0.9rem;
}

.muted {
  color: #777;
  font-size: 0.9rem;
}

.list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.35rem 0;
  border-bottom: 1px solid #eee;
}

button.link {
  background: transparent;
  border: none;
  color: #0b57d0;
  padding: 0;
  text-align: left;
}

.danger {
  background: #b3261e;
  border-color: #8c1d18;
}

.detail-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 0.75rem;
}

.subgrid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
}

.panel {
  border: 1px solid #e7e7e7;
  border-radius: 12px;
  background: #fff;
  padding: 0.9rem 1rem;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.panel h3 {
  margin: 0;
  font-size: 1rem;
}

.panel-full {
  grid-column: 1 / -1;
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.table th,
.table td {
  padding: 0.5rem 0.25rem;
  border-bottom: 1px solid #eee;
  text-align: left;
  vertical-align: top;
}

.td-actions {
  white-space: nowrap;
  width: 120px;
}

button.small {
  padding: 0.25rem 0.5rem;
  font-size: 0.8rem;
  border-radius: 8px;
  margin-right: 0.25rem;
}

.kpi-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.kpi {
  background: #f6f7ff;
  border: 1px solid #e9ecff;
  border-radius: 12px;
  padding: 0.65rem 0.75rem;
}

.kpi-label {
  color: #555;
  font-size: 0.8rem;
}

.kpi-value {
  margin-top: 0.25rem;
  font-weight: 700;
}

.pos {
  color: #15803d;
}
.neg {
  color: #b91c1c;
}

.alloc-title {
  font-weight: 700;
  margin-bottom: 0.5rem;
}

.alloc {
  margin-top: 0.75rem;
}

.bars {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.bar-row {
  display: grid;
  grid-template-columns: 90px 1fr 70px;
  gap: 0.5rem;
  align-items: center;
}

.bar-label {
  font-size: 0.85rem;
  color: #444;
}

.bar-track {
  height: 10px;
  background: #f0f2f5;
  border-radius: 999px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #4f46e5, #06b6d4);
}

.bar-pct {
  text-align: right;
  font-size: 0.85rem;
  color: #444;
}

.chartWrap {
  position: relative;
  border: 1px solid #e8e8e8;
  border-radius: 12px;
  background: #fff;
  height: 280px;
}

.chart {
  width: 100%;
  height: 100%;
}

.axis {
  stroke: #d1d5db;
  stroke-width: 1;
}

.poly {
  fill: none;
  stroke: #1d4ed8;
  stroke-width: 2.2;
  filter: drop-shadow(0 0 6px rgba(29, 78, 216, 0.35));
}

.hoverDot {
  fill: #22d3ee;
  stroke: rgba(255, 255, 255, 0.95);
  stroke-width: 1.2;
}

.selectedDot {
  fill: #38bdf8;
  stroke: rgba(255, 255, 255, 0.98);
  stroke-width: 1.4;
  filter: drop-shadow(0 0 10px rgba(56, 189, 248, 0.55));
}

.chartTooltip {
  position: absolute;
  top: 10px;
  right: 10px;
  background: rgba(17, 24, 39, 0.92);
  color: #fff;
  padding: 0.45rem 0.6rem;
  border-radius: 10px;
  font-size: 0.8rem;
}

.perf-meta {
  margin: 0 0 0.75rem;
  padding: 0.55rem 0.75rem;
  border: 1px solid rgba(56, 189, 248, 0.35);
  border-radius: 12px;
  background: linear-gradient(
    90deg,
    rgba(29, 78, 216, 0.08),
    rgba(56, 189, 248, 0.06)
  );
}

.meta-line {
  font-size: 0.85rem;
  color: #1f2937;
}

.meta-val {
  font-weight: 800;
  color: #0ea5e9;
}

.meta-date {
  color: #6b7280;
  margin-left: 0.25rem;
}

.tt-date {
  opacity: 0.9;
  margin-bottom: 0.15rem;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
}

.modal {
  width: min(720px, calc(100vw - 40px));
  background: #fff;
  border-radius: 14px;
  padding: 1rem 1.1rem;
  border: 1px solid #e5e7eb;
}

.modal-title {
  margin: 0 0 0.75rem;
}

.modal-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem 1rem;
}

.field label {
  display: block;
  font-size: 0.85rem;
  color: #555;
  margin-bottom: 0.25rem;
}

.field textarea {
  width: 100%;
}

.modal-err {
  margin-top: 0.5rem;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 1rem;
}

@media (min-width: 920px) {
  .grid {
    grid-template-columns: 1fr 1fr;
  }
  .card-wide {
    grid-column: 1 / -1;
  }
  .subgrid {
    grid-template-columns: 1fr 1fr;
  }
  .panel-full {
    grid-column: 1 / -1;
  }
}
</style>
