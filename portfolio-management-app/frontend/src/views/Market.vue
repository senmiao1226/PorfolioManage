<template>
  <div class="market">
    <header class="page-header">
      <h1>Market</h1>
      <p class="subtitle">View market data and search stocks</p>
    </header>

    <!-- 组合选择器 -->
    <div class="portfolio-selector">
      <label>Select Portfolio:</label>
      <select v-model="selectedPortfolioId" @change="onPortfolioChange">
        <option value="">All Holdings</option>
        <option v-for="p in portfolios" :key="p.id" :value="p.id">
          {{ p.name }}
        </option>
      </select>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading">
      <div class="spinner"></div>
      <span>Loading...</span>
    </div>

    <!-- 错误提示 -->
    <div v-else-if="error" class="error-message">
      {{ error }}
      <button @click="loadData" class="retry-btn">Retry</button>
    </div>

    <!-- 数据展示 -->
    <div v-else class="market-content">
      <!-- 我的持仓行情 -->
      <section class="card">
        <div class="card-header">
          <h2>My Holdings</h2>
          <span class="update-time">Updated: {{ updateTime }}</span>
        </div>
        <div v-if="holdingsData?.length" class="stock-grid">
          <div 
            v-for="stock in holdingsData" 
            :key="stock.ticker"
            class="stock-card"
            :class="{ 'up': stock.priceChangePercent > 0, 'down': stock.priceChangePercent < 0 }"
            @click="showDetail(stock.ticker)"
          >
            <div class="stock-header">
              <span class="ticker">{{ stock.ticker }}</span>
              <span class="name">{{ stock.name }}</span>
            </div>
            <div class="stock-price">
              <span class="current">{{ fmtMoney(stock.currentPrice) }}</span>
              <span class="change" :class="{ 'positive': stock.priceChangePercent > 0, 'negative': stock.priceChangePercent < 0 }">
                {{ stock.priceChangePercent > 0 ? '+' : '' }}{{ stock.priceChangePercent?.toFixed(2) }}%
              </span>
            </div>
            <div class="stock-holding">
              <span class="quantity">Shares: {{ stock.holdingQuantity }}</span>
              <span class="value">Position: {{ fmtMoney(stock.holdingValue) }}</span>
            </div>
          </div>
        </div>
        <p v-else class="empty">No holdings data available</p>
      </section>

      <!-- 涨跌排行 -->
      <div class="movers-grid">
        <section class="card">
          <h2>Top Gainers</h2>
          <div v-if="gainers?.length" class="mover-list">
            <div v-for="(item, idx) in gainers" :key="item.ticker" class="mover-item" :class="{ 'champion': idx === 0 }">
              <span class="rank">
                <span v-if="idx === 0" class="champion-icon">👑</span>
                <span v-else>{{ idx + 1 }}</span>
              </span>
              <div class="mover-info">
                <span class="ticker">{{ item.ticker }}</span>
                <span class="name">{{ item.name }}</span>
              </div>
              <div class="mover-pnl">
                <span class="pnl-value positive">+{{ fmtMoney(item.unrealizedPnl) }}</span>
                <span class="pnl-percent positive">+{{ item.unrealizedPnlPercent?.toFixed(2) }}%</span>
              </div>
            </div>
          </div>
          <p v-else class="empty">{{ holdingsData?.length ? '暂无盈利持仓' : '请先添加持仓' }}</p>
        </section>

        <section class="card">
          <h2>Top Losers</h2>
          <div v-if="losers?.length" class="mover-list">
            <div v-for="(item, idx) in losers" :key="item.ticker" class="mover-item">
              <span class="rank">{{ idx + 1 }}</span>
              <div class="mover-info">
                <span class="ticker">{{ item.ticker }}</span>
                <span class="name">{{ item.name }}</span>
              </div>
              <div class="mover-pnl">
                <span class="pnl-value negative">{{ fmtMoney(item.unrealizedPnl) }}</span>
                <span class="pnl-percent negative">{{ item.unrealizedPnlPercent?.toFixed(2) }}%</span>
              </div>
            </div>
          </div>
          <p v-else class="empty">{{ holdingsData?.length ? '暂无亏损持仓' : '请先添加持仓' }}</p>
        </section>
      </div>

      <!-- 搜索股票 -->
      <section class="card">
        <h2>Search Stock</h2>
        <div class="search-box">
          <input 
            v-model="searchTicker" 
            placeholder="Enter ticker (e.g. AAPL)"
            @keyup.enter="search"
          />
          <button @click="search" :disabled="searching">{{ searching ? 'Searching...' : 'Search' }}</button>
        </div>
        <div v-if="searchResult" class="search-result">
          <div 
            class="result-item" 
            :class="{ 'available': searchResult.isAvailable, 'clickable': searchResult.isAvailable }"
            @click="searchResult.isAvailable && showStockHistory(searchResult.ticker)"
          >
            <span class="ticker">{{ searchResult.ticker }}</span>
            <span class="price">{{ fmtMoney(searchResult.currentPrice) }}</span>
            <span v-if="searchResult.isAvailable" class="click-hint">Click to view chart</span>
            <span v-else class="status">Not available</span>
          </div>
        </div>
      </section>

      <!-- 热门股票 -->
      <section class="card">
        <h2>Popular Stocks</h2>
        <div v-if="popularStocks?.length" class="stock-table">
          <table>
            <thead>
              <tr>
                <th>Ticker</th>
                <th>Name</th>
                <th>Price</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="stock in popularStocks"
                :key="stock.ticker"
                class="clickable-row"
                @click="showStockHistory(stock.ticker)"
              >
                <td class="ticker">{{ stock.ticker }}</td>
                <td class="company-name">{{ stock.name }}</td>
                <td class="price">{{ formatPriceWithCurrency(stock.currentPrice, stock.priceSource) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-else class="empty">No data available</p>
      </section>
    </div>

    <!-- 股票历史走势弹窗 -->
    <div v-if="selectedStockTicker" class="modal-backdrop" @click.self="closeStockHistory">
      <div class="modal chart-modal">
        <div class="modal-header">
          <h3>{{ selectedStockTicker.toUpperCase() }} &mdash; Price History</h3>
          <button class="close-btn" @click="closeStockHistory">&times;</button>
        </div>

        <!-- 时间范围选择器 -->
        <div class="time-selector modal-time-selector">
          <button 
            v-for="option in timeRangeOptions" 
            :key="option.days"
            :class="{ active: selectedStockDays === option.days }"
            @click="changeStockDays(option.days)"
          >
            {{ option.label }}
          </button>
        </div>

        <!-- 图表内容 -->
        <div class="modal-chart-body">
          <div v-if="stockHistoryLoading" class="loading">
            <div class="spinner"></div>
            <span>Loading...</span>
          </div>
          <div v-else-if="stockHistoryData?.length && stockHistoryData.length >= 2">
            <div
              class="chart-area chart-area--history"
              ref="stockHistoryChartAreaRef"
            >
              <svg
                ref="stockHistorySvgRef"
                :viewBox="`0 0 ${chartWidth} ${chartHeight}`"
                preserveAspectRatio="xMidYMid meet"
                class="price-history-svg"
                @mousemove="onStockHistoryMouseMove"
                @mouseleave="clearStockHistoryHover"
              >
                <defs>
                  <linearGradient id="market-stock-area-gradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" :stop-color="chartAccent" stop-opacity="0.38" />
                    <stop offset="50%" :stop-color="chartAccent" stop-opacity="0.1" />
                    <stop offset="100%" :stop-color="chartAccent" stop-opacity="0" />
                  </linearGradient>
                </defs>
                <rect
                  :x="padding.left"
                  :y="padding.top"
                  :width="chartWidth - padding.left - padding.right"
                  :height="chartHeight - padding.top - padding.bottom"
                  rx="12"
                  fill="#f8fafc"
                  stroke="#e2e8f0"
                  stroke-width="1"
                />
                <line
                  v-for="i in 6"
                  :key="'gh' + i"
                  :x1="padding.left + 1"
                  :x2="chartWidth - padding.right - 1"
                  :y1="chartGridY(i)"
                  :y2="chartGridY(i)"
                  stroke="#e2e8f0"
                  stroke-width="1"
                  stroke-dasharray="5 7"
                  opacity="0.9"
                />
                <line
                  v-for="i in 6"
                  :key="'gv' + i"
                  :x1="chartGridX(i)"
                  :x2="chartGridX(i)"
                  :y1="padding.top + 1"
                  :y2="chartHeight - padding.bottom - 1"
                  stroke="#e8ecf1"
                  stroke-width="1"
                  stroke-dasharray="4 8"
                  opacity="0.65"
                />
                <path
                  v-if="stockChartAreaPath"
                  :d="stockChartAreaPath"
                  fill="url(#market-stock-area-gradient)"
                />
                <polyline
                  v-if="stockChartPoints"
                  :points="stockChartPoints"
                  fill="none"
                  :stroke="chartAccent"
                  stroke-width="3"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  class="price-line-glow"
                />
                <circle
                  v-for="(pt, idx) in chartLineEndpoints"
                  :key="'ep' + idx"
                  :cx="pt.x"
                  :cy="pt.y"
                  r="5.5"
                  fill="white"
                  :stroke="chartAccent"
                  stroke-width="2.5"
                />

                <!-- Hover crosshair + point -->
                <line
                  v-if="hoveredChartPoint"
                  :x1="hoveredChartPoint.x"
                  :x2="hoveredChartPoint.x"
                  :y1="padding.top"
                  :y2="chartHeight - padding.bottom"
                  stroke="#94a3b8"
                  stroke-width="1"
                  stroke-dasharray="4 6"
                  opacity="0.95"
                />
                <line
                  v-if="hoveredChartPoint"
                  :x1="padding.left"
                  :x2="chartWidth - padding.right"
                  :y1="hoveredChartPoint.y"
                  :y2="hoveredChartPoint.y"
                  stroke="#94a3b8"
                  stroke-width="1"
                  stroke-dasharray="3 7"
                  opacity="0.5"
                />
                <circle
                  v-if="hoveredChartPoint"
                  :cx="hoveredChartPoint.x"
                  :cy="hoveredChartPoint.y"
                  r="7"
                  fill="white"
                  :stroke="chartAccent"
                  stroke-width="2.5"
                />
              </svg>

              <!-- Tooltip -->
              <div
                v-if="hoveredChartPoint"
                class="chart-tooltip"
                :style="{ left: stockHistoryTooltipPos.left + 'px', top: stockHistoryTooltipPos.top + 'px' }"
              >
                <div class="tooltip-date">{{ hoveredChartPoint.date }}</div>
                <div class="tooltip-price">{{ fmtMoney(hoveredChartPoint.value) }}</div>
                <div class="tooltip-sub">
                  <span :class="hoveredReturnPercent >= 0 ? 'positive' : 'negative'">
                    {{ fmtPercent(hoveredReturnPercent) }}
                  </span>
                  <span class="tooltip-sub-label">Return</span>
                </div>
              </div>
            </div>
            <div class="chart-info chart-info-row">
              <div class="info-item">
                <span>High:</span>
                <span class="positive">{{ fmtMoney(stockMaxValue) }}</span>
              </div>
              <div class="info-item">
                <span>Low:</span>
                <span class="negative">{{ fmtMoney(stockMinValue) }}</span>
              </div>
              <div class="info-item">
                <span>Avg:</span>
                <span>{{ fmtMoney(stockAvgValue) }}</span>
              </div>
              <div class="info-item">
                <span>Change:</span>
                <span :class="stockChangePercent >= 0 ? 'positive' : 'negative'">
                  {{ fmtPercent(stockChangePercent) }}
                </span>
              </div>
              <div class="info-item">
                <span>Data points:</span>
                <span>{{ stockHistoryData.length }}</span>
              </div>
            </div>
          </div>
          <p v-else class="empty">{{ stockHistoryError || 'No data available' }}</p>
        </div>
      </div>
    </div>

    <!-- 股票详情弹窗 -->
    <div v-if="detailModalOpen" class="modal-backdrop" @click.self="closeDetail">
      <div class="modal">
        <div class="modal-header">
          <h3>{{ detailData?.ticker }} Stock Detail</h3>
          <button class="close-btn" @click="closeDetail">×</button>
        </div>
        <div v-if="detailLoading" class="modal-loading">Loading...</div>
        <div v-else-if="detailData" class="modal-content">
          <div class="detail-price">
            <span class="current">{{ fmtMoney(detailData.currentPrice) }}</span>
            <span class="change" :class="{ 'positive': detailData.priceChangePercent > 0, 'negative': detailData.priceChangePercent < 0 }">
              {{ detailData.priceChangePercent > 0 ? '+' : '' }}{{ detailData.priceChangePercent?.toFixed(2) }}%
            </span>
          </div>
          <div class="detail-info">
            <div class="info-row">
              <span>Previous Close:</span>
              <span>{{ fmtMoney(detailData.previousClose) }}</span>
            </div>
            <div class="info-row">
              <span>Price Change:</span>
              <span :class="{ 'positive': detailData.priceChange > 0, 'negative': detailData.priceChange < 0 }">
                {{ detailData.priceChange > 0 ? '+' : '' }}{{ fmtMoney(detailData.priceChange) }}
              </span>
            </div>
            <div class="info-row" v-if="detailData.isInPortfolio">
              <span>My Position:</span>
              <span>{{ detailData.holdingQuantity }} shares</span>
            </div>
          </div>
          <div v-if="detailData.priceHistory?.length" class="mini-chart">
            <h4>Recent Trend</h4>
            <div class="chart-bars">
              <div 
                v-for="(point, idx) in detailData.priceHistory" 
                :key="idx"
                class="bar"
                :style="{ height: getBarHeight(point.price) + '%' }"
                :title="`${point.date}: ${fmtMoney(point.price)}`"
              ></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { api } from '../api';
import { t, currentLang } from '../locales';

const loading = ref(false);
const error = ref('');
const portfolios = ref([]);
const selectedPortfolioId = ref('');
const holdingsData = ref([]);
const gainers = ref([]);
const losers = ref([]);
const popularStocks = ref([]);
const updateTime = ref('');

// 搜索相关
const searchTicker = ref('');
const searching = ref(false);
const searchResult = ref(null);

// 详情弹窗
const detailModalOpen = ref(false);
const detailLoading = ref(false);
const detailData = ref(null);

// ========== 股票历史走势功能 ==========
const selectedStockTicker = ref('');
const selectedStockDays = ref(30);
const stockHistoryData = ref([]);
const stockHistoryLoading = ref(false);
const stockHistoryError = ref('');
const stockHistoryCache = ref({
  ticker: '',
  fullData: [],
  lastFetchTime: null
});

// 时间范围选项
const timeRangeOptions = [
  { days: 30, label: '30天' },
  { days: 180, label: '半年' },
  { days: 365, label: '一年' }
];

// 图表配置（宽屏 viewBox，由 CSS 拉伸填满弹窗）
const chartWidth = 1400;
const chartHeight = 480;
const padding = { top: 40, right: 50, bottom: 60, left: 100 };

function chartGridY(i) {
  const plotH = chartHeight - padding.top - padding.bottom;
  return padding.top + (plotH * (i - 1)) / 5;
}

function chartGridX(i) {
  const plotW = chartWidth - padding.left - padding.right;
  return padding.left + (plotW * (i - 1)) / 5;
}

function fmtMoney(v) {
  if (v == null || Number.isNaN(Number(v))) return '—';
  return new Intl.NumberFormat('zh-CN', { 
    style: 'currency', 
    currency: 'USD',
    maximumFractionDigits: 2 
  }).format(Number(v));
}

function fmtPercent(v) {
  if (v == null || Number.isNaN(Number(v))) return '—';
  return (v >= 0 ? '+' : '') + Number(v).toFixed(2) + '%';
}

function tOr(key, fallback) {
  const v = t(key);
  return v === key ? fallback : v;
}

// 根据货币类型格式化价格（USD或CNY）
function formatPriceWithCurrency(price, currency) {
  if (price == null || Number.isNaN(Number(price))) return '—';
  const curr = currency === 'CNY' ? 'CNY' : 'USD';
  return new Intl.NumberFormat('zh-CN', { 
    style: 'currency', 
    currency: curr,
    maximumFractionDigits: 2 
  }).format(Number(price));
}

// ========== 股票历史走势功能 ==========

// 显示股票历史走势
async function showStockHistory(ticker) {
  selectedStockTicker.value = ticker;
  selectedStockDays.value = 30; // 默认30天
  await loadStockHistory(true); // true = 强制刷新，获取完整一年数据
}

// 关闭股票历史走势
function closeStockHistory() {
  selectedStockTicker.value = '';
  stockHistoryData.value = [];
  stockHistoryError.value = '';
  stockHistoryCache.value = {
    ticker: '',
    fullData: [],
    lastFetchTime: null
  };
}

// 加载个股历史数据
async function loadStockHistory(forceRefresh = false) {
  if (!selectedStockTicker.value.trim()) {
    stockHistoryError.value = '请输入股票代码';
    return;
  }
  
  const ticker = selectedStockTicker.value.trim().toUpperCase();
  
  if (!forceRefresh && 
      stockHistoryCache.value.ticker === ticker && 
      stockHistoryCache.value.fullData.length > 0) {
    filterDataByTimeRange();
    return;
  }
  
  stockHistoryLoading.value = true;
  stockHistoryError.value = '';
  
  try {
    const response = await api.getStockHistory(ticker, 365);
    
    if (response.found && response.data && response.data.length > 0) {
      const fullData = response.data.map(d => ({
        date: d.date,
        value: d.price
      }));
      
      stockHistoryCache.value = {
        ticker: ticker,
        fullData: fullData,
        lastFetchTime: new Date()
      };
      
      filterDataByTimeRange();
    } else {
      stockHistoryData.value = [];
      stockHistoryCache.value.fullData = [];
      stockHistoryError.value = response.message || '未找到该股票的历史数据';
    }
  } catch (e) {
    console.error('加载个股历史数据失败:', e);
    stockHistoryError.value = e.message || '加载失败，请稍后重试';
    stockHistoryData.value = [];
  } finally {
    stockHistoryLoading.value = false;
  }
}

// 根据时间范围过滤缓存的数据
function filterDataByTimeRange() {
  const fullData = stockHistoryCache.value.fullData;
  if (!fullData || fullData.length === 0) {
    stockHistoryData.value = [];
    return;
  }
  
  const days = selectedStockDays.value;
  
  if (days >= 365) {
    stockHistoryData.value = fullData;
    return;
  }
  
  const dataPointsNeeded = Math.min(days, fullData.length);
  const filteredData = fullData.slice(-dataPointsNeeded);
  
  stockHistoryData.value = filteredData;
}

// 切换时间范围
function changeStockDays(days) {
  selectedStockDays.value = days;
  filterDataByTimeRange();
}

// 个股走势图计算
const stockChartPointsArray = computed(() => {
  if (!stockHistoryData.value?.length || stockHistoryData.value.length < 2) return [];
  
  const values = stockHistoryData.value.map(d => d.value);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  
  const usableWidth = chartWidth - padding.left - padding.right;
  const usableHeight = chartHeight - padding.top - padding.bottom;
  const dataLength = stockHistoryData.value.length;
  
  return stockHistoryData.value.map((d, i) => ({
    x: padding.left + (usableWidth * i / (dataLength - 1)),
    y: chartHeight - padding.bottom - (usableHeight * (d.value - min) / range)
  }));
});

const stockChartPoints = computed(() => {
  return stockChartPointsArray.value.map(p => `${p.x},${p.y}`).join(' ');
});

const stockMaxValue = computed(() => {
  if (!stockHistoryData.value?.length) return 0;
  return Math.max(...stockHistoryData.value.map(d => d.value));
});

const stockMinValue = computed(() => {
  if (!stockHistoryData.value?.length) return 0;
  return Math.min(...stockHistoryData.value.map(d => d.value));
});

const stockAvgValue = computed(() => {
  if (!stockHistoryData.value?.length) return 0;
  const sum = stockHistoryData.value.reduce((acc, d) => acc + d.value, 0);
  return sum / stockHistoryData.value.length;
});

const stockChangePercent = computed(() => {
  if (!stockHistoryData.value?.length || stockHistoryData.value.length < 2) return 0;
  const first = stockHistoryData.value[0].value;
  const last = stockHistoryData.value[stockHistoryData.value.length - 1].value;
  return ((last - first) / first) * 100;
});

const chartAccent = computed(() => {
  const p = stockChangePercent.value;
  if (p > 0.15) return '#0d9488';
  if (p < -0.15) return '#e11d48';
  return '#6366f1';
});

const stockChartAreaPath = computed(() => {
  const pts = stockChartPointsArray.value;
  if (pts.length < 2) return '';
  const baseY = chartHeight - padding.bottom;
  const parts = [`M ${pts[0].x} ${baseY}`];
  for (const p of pts) {
    parts.push(`L ${p.x} ${p.y}`);
  }
  parts.push(`L ${pts[pts.length - 1].x} ${baseY} Z`);
  return parts.join(' ');
});

const chartLineEndpoints = computed(() => {
  const pts = stockChartPointsArray.value;
  if (pts.length < 2) return [];
  return [pts[0], pts[pts.length - 1]];
});

// ===== Hover interaction for stock history chart =====
const stockHistorySvgRef = ref(null);
const stockHistoryChartAreaRef = ref(null);
const stockHistoryHoverIndex = ref(null);
const stockHistoryTooltipPos = ref({ left: 12, top: 12 });

const hoveredChartPoint = computed(() => {
  if (stockHistoryHoverIndex.value === null) return null;
  const idx = stockHistoryHoverIndex.value;
  const pt = stockChartPointsArray.value[idx];
  const meta = stockHistoryData.value[idx];
  if (!pt || !meta) return null;
  return { ...pt, date: meta.date, value: meta.value, index: idx };
});

const hoveredReturnPercent = computed(() => {
  const hc = hoveredChartPoint.value;
  if (!hc) return 0;
  const first = stockHistoryData.value?.[0]?.value ?? 0;
  if (!first || first === 0) return 0;
  return ((hc.value - first) / first) * 100;
});

function clamp(n, min, max) {
  return Math.min(Math.max(n, min), max);
}

function onStockHistoryMouseMove(e) {
  if (!stockHistorySvgRef.value || !stockHistoryChartAreaRef.value) return;
  if (!stockHistoryData.value?.length || stockHistoryData.value.length < 2) return;

  const svgRect = stockHistorySvgRef.value.getBoundingClientRect();
  const areaRect = stockHistoryChartAreaRef.value.getBoundingClientRect();

  const xInSvg = e.clientX - svgRect.left;
  const xRatio = clamp(xInSvg / svgRect.width, 0, 1);
  const xInViewBox = xRatio * chartWidth;

  const plotW = chartWidth - padding.left - padding.right;
  const t = clamp((xInViewBox - padding.left) / plotW, 0, 1);
  const len = stockHistoryData.value.length;
  const idx = Math.round(t * (len - 1));
  stockHistoryHoverIndex.value = clamp(idx, 0, len - 1);

  // Tooltip position relative to chart area
  const tooltipWidth = 220;
  const tooltipHeight = 74;
  const rawLeft = e.clientX - areaRect.left + 12;
  const rawTop = e.clientY - areaRect.top - 12;

  stockHistoryTooltipPos.value = {
    left: clamp(rawLeft, 12, areaRect.width - tooltipWidth - 12),
    top: clamp(rawTop, 12, areaRect.height - tooltipHeight - 12),
  };
}

function clearStockHistoryHover() {
  stockHistoryHoverIndex.value = null;
}

async function loadPortfolios() {
  try {
    portfolios.value = await api.listPortfolios();
  } catch (e) {
    console.error('加载组合列表失败:', e);
  }
}

async function loadData() {
  loading.value = true;
  error.value = '';
  try {
    // 先加载组合列表
    await loadPortfolios();
    
    // 等待 portfolios 更新后再决定使用哪个组合
    const effectivePortfolioId = selectedPortfolioId.value || (portfolios.value.length > 0 ? portfolios.value[0].id : null);
    console.log('Effective portfolio ID:', effectivePortfolioId);
    
    // 加载持仓行情
    if (effectivePortfolioId) {
      holdingsData.value = await api.getMyHoldingsMarketData(effectivePortfolioId);
    } else {
      holdingsData.value = await api.getAllHoldingsMarketData();
    }
    
    // 加载涨跌榜
    if (effectivePortfolioId) {
      try {
        console.log('Loading gainers/losers for portfolio:', effectivePortfolioId);
        const bundle = await api.getMarketMoversBundle(effectivePortfolioId);
        gainers.value = bundle?.gainers || [];
        losers.value = bundle?.losers || [];
      } catch (e) {
        console.error('Failed to load gainers/losers:', e);
        gainers.value = [];
        losers.value = [];
      }
    } else {
      console.log('No portfolio available for gainers/losers');
      gainers.value = [];
      losers.value = [];
    }
    
    // 加载热门股票
    popularStocks.value = await api.getPopularStocks();
    
    updateTime.value = new Date().toLocaleString(currentLang.value === 'zh' ? 'zh-CN' : 'en-US');
  } catch (e) {
    error.value = e.message || 'Failed to load data';
  } finally {
    loading.value = false;
  }
}

function onPortfolioChange() {
  loadData();
}

async function search() {
  if (!searchTicker.value.trim()) return;
  searching.value = true;
  try {
    searchResult.value = await api.searchStock(searchTicker.value.trim().toUpperCase());
  } catch (e) {
    searchResult.value = null;
  } finally {
    searching.value = false;
  }
}

async function showDetail(ticker) {
  detailModalOpen.value = true;
  detailLoading.value = true;
  try {
    detailData.value = await api.getAssetDetail(
      ticker, 
      selectedPortfolioId.value || null
    );
  } catch (e) {
    detailData.value = null;
  } finally {
    detailLoading.value = false;
  }
}

function closeDetail() {
  detailModalOpen.value = false;
  detailData.value = null;
}

function getBarHeight(price) {
  if (!detailData.value?.priceHistory?.length) return 0;
  const prices = detailData.value.priceHistory.map(p => p.price);
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  const range = max - min || 1;
  return ((price - min) / range) * 80 + 10;
}

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.market {
  padding: 1.5rem;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 1rem;
}

.page-header h1 {
  margin: 0;
  font-size: 1.75rem;
}

.subtitle {
  margin: 0.25rem 0 0;
  color: #666;
}

.portfolio-selector {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
  padding: 0.75rem 1rem;
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.portfolio-selector select {
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 6px;
  min-width: 200px;
}

.loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 3rem;
}

.spinner {
  width: 24px;
  height: 24px;
  border: 2px solid #e5e5e5;
  border-top-color: #4f46e5;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.card {
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.card h2 {
  margin: 0;
  font-size: 1.25rem;
}

.update-time {
  font-size: 0.75rem;
  color: #999;
}

.stock-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 1rem;
}

.stock-card {
  padding: 1rem;
  background: #f9fafb;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  border-left: 4px solid transparent;
}

.stock-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

.stock-card.up {
  border-left-color: #10b981;
}

.stock-card.down {
  border-left-color: #ef4444;
}

.stock-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.ticker {
  font-weight: 700;
  font-size: 1.1rem;
}

.name {
  font-size: 0.75rem;
  color: #666;
}

.stock-price {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.current {
  font-size: 1.5rem;
  font-weight: 700;
}

.change {
  font-size: 0.875rem;
  font-weight: 600;
}

.change.positive {
  color: #10b981;
}

.change.negative {
  color: #ef4444;
}

.stock-holding {
  display: flex;
  justify-content: space-between;
  font-size: 0.75rem;
  color: #666;
}

.movers-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1.5rem;
}

.mover-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.mover-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  background: #f9fafb;
  border-radius: 8px;
  transition: all 0.2s;
}

.mover-item:hover {
  background: #f3f4f6;
}

.mover-item.champion {
  background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%);
  border: 2px solid #f59e0b;
  transform: scale(1.02);
}

.rank {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #e5e7eb;
  border-radius: 50%;
  font-size: 0.75rem;
  font-weight: 700;
  flex-shrink: 0;
}

.champion-icon {
  font-size: 1.25rem;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}

.mover-item:first-child .rank {
  background: #fde68a;
}

.mover-item:nth-child(2) .rank {
  background: #e5e7eb;
}

.mover-item:nth-child(3) .rank {
  background: #fed7aa;
}

.mover-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.mover-info .ticker {
  font-weight: 700;
  font-size: 0.875rem;
}

.mover-info .name {
  font-size: 0.75rem;
  color: #6b7280;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mover-pnl {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.25rem;
}

.pnl-value {
  font-weight: 700;
  font-size: 0.875rem;
}

.pnl-percent {
  font-size: 0.75rem;
  font-weight: 500;
}

.stock-table table {
  width: 100%;
  border-collapse: collapse;
}

.stock-table th,
.stock-table td {
  padding: 0.75rem;
  text-align: left;
  border-bottom: 1px solid #e5e7eb;
}

.stock-table th {
  font-weight: 600;
  color: #666;
  font-size: 0.875rem;
}

.stock-table tbody tr {
  cursor: pointer;
}

.stock-table tbody tr:hover {
  background: #f9fafb;
}

.stock-table .ticker {
  font-weight: 700;
}

.stock-table .price {
  font-family: monospace;
}

.stock-table .company-name {
  font-size: 0.875rem;
  color: #374151;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stock-table .clickable-row {
  cursor: pointer;
}

.stock-table .clickable-row:hover {
  background: #f3f4f6;
}

/* 股票历史走势样式 */
.stock-history-card {
  margin-top: 1rem;
}

.stock-history-card .card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.close-btn {
  font-size: 1.25rem;
  color: #666;
  background: none;
  border: none;
  cursor: pointer;
  padding: 0.25rem 0.5rem;
}

.close-btn:hover {
  color: #333;
}

.time-selector {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.time-selector button {
  padding: 0.5rem 1rem;
  border: 1px solid #ddd;
  background: white;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.time-selector button.active {
  background: #4f46e5;
  color: white;
  border-color: #4f46e5;
}

.time-selector button:hover:not(.active) {
  background: #f3f4f6;
}

.chart-container {
  margin-top: 1rem;
}

.chart-header {
  margin-bottom: 0.5rem;
  padding: 0 0.5rem;
}

.data-points {
  font-size: 0.75rem;
  color: #6b7280;
}

.chart-area {
  height: 200px;
  background: #f9fafb;
  border-radius: 8px;
  padding: 1rem;
}

.chart-area svg {
  width: 100%;
  height: 100%;
}

.chart-info {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1rem;
  margin-top: 1rem;
  padding: 0.75rem;
  background: #f9fafb;
  border-radius: 8px;
}

.info-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
}

.info-item span:first-child {
  font-size: 0.75rem;
  color: #6b7280;
}

.info-item span:last-child {
  font-weight: 600;
  font-size: 0.875rem;
}

.search-box {
  display: flex;
  gap: 0.5rem;
}

.search-box input {
  flex: 1;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 8px;
}

.search-box button {
  padding: 0.75rem 1.5rem;
  background: #4f46e5;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
}

.search-box button:disabled {
  opacity: 0.6;
}

.search-result {
  margin-top: 1rem;
}

.result-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
  background: #f9fafb;
  border-radius: 8px;
}

.result-item.available {
  background: #ecfdf5;
}

.result-item .status {
  margin-left: auto;
  font-size: 0.875rem;
}

.view-history-btn {
  margin-left: auto;
  padding: 0.5rem 1rem;
  background: #4f46e5;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.875rem;
  font-weight: 500;
  transition: all 0.2s;
}

.view-history-btn:hover {
  background: #4338ca;
}

.result-item.clickable {
  cursor: pointer;
  transition: all 0.2s;
}

.result-item.clickable:hover {
  background: #e0e7ff;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(79, 70, 229, 0.15);
}

.click-hint {
  margin-left: auto;
  font-size: 0.8rem;
  color: #4f46e5;
  font-weight: 500;
}

/* Chart Modal Styles */
.modal.chart-modal {
  max-width: min(1280px, 98vw);
  width: 96%;
  box-shadow:
    0 25px 50px -12px rgba(15, 23, 42, 0.18),
    0 0 0 1px rgba(15, 23, 42, 0.04);
  border-radius: 18px;
  overflow: hidden;
}

.modal-time-selector {
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #e5e7eb;
  justify-content: center;
  background: linear-gradient(180deg, #fafbfc 0%, #ffffff 100%);
}

.modal-time-selector button {
  border-radius: 999px;
  padding: 0.45rem 1.15rem;
  font-weight: 500;
  border-color: #e5e7eb;
  transition: background 0.2s, color 0.2s, border-color 0.2s, box-shadow 0.2s;
}

.modal-time-selector button.active {
  box-shadow: 0 2px 8px rgba(79, 70, 229, 0.25);
}

.modal-chart-body {
  padding: 1.5rem 1.75rem 2rem;
  min-height: 520px;
  background: #ffffff;
}

.chart-area--history {
  height: min(520px, 62vh);
  min-height: 340px;
  margin-bottom: 1.25rem;
  border-radius: 14px;
  padding: 0.35rem;
  background: linear-gradient(165deg, #f8fafc 0%, #f1f5f9 55%, #eef2f7 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);
  position: relative;
}

.price-history-svg {
  width: 100%;
  height: 100%;
  display: block;
  cursor: crosshair;
}

.chart-tooltip {
  position: absolute;
  z-index: 5;
  pointer-events: none;
  min-width: 160px;
  max-width: 220px;
  background: rgba(15, 23, 42, 0.96);
  color: white;
  border-radius: 12px;
  padding: 10px 12px;
  box-shadow: 0 18px 40px -18px rgba(2, 6, 23, 0.45);
  border: 1px solid rgba(148, 163, 184, 0.25);
}

.tooltip-date {
  font-size: 0.75rem;
  color: rgba(226, 232, 240, 0.9);
  margin-bottom: 6px;
}

.tooltip-price {
  font-size: 1.1rem;
  font-weight: 800;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  margin-bottom: 6px;
}

.tooltip-sub {
  font-size: 0.85rem;
  display: flex;
  gap: 6px;
  align-items: baseline;
}

.tooltip-sub-label {
  font-size: 0.75rem;
  color: rgba(226, 232, 240, 0.85);
}

.price-line-glow {
  filter: drop-shadow(0 2px 6px rgba(15, 23, 42, 0.12));
}

.modal-chart-body .chart-info-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  align-items: stretch;
}

.modal-chart-body .chart-info-row .info-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0.75rem 1rem;
  background: #f8fafc;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  min-width: 100px;
}

.modal-chart-body .chart-info-row .info-item span:first-child {
  font-size: 0.75rem;
  color: #64748b;
  margin-bottom: 0.25rem;
}

.modal-chart-body .chart-info-row .info-item span:last-child {
  font-weight: 600;
  font-size: 1rem;
}

@media (max-width: 900px) {
  .modal-chart-body .chart-info-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.modal.chart-modal .modal-header h3 {
  font-size: 1.15rem;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: #0f172a;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.modal {
  width: 90%;
  max-width: 500px;
  background: white;
  border-radius: 16px;
  overflow: hidden;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #e5e7eb;
}

.modal-header h3 {
  margin: 0;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: #666;
}

.modal-content {
  padding: 1.5rem;
}

.detail-price {
  display: flex;
  align-items: baseline;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.detail-price .current {
  font-size: 2.5rem;
  font-weight: 700;
}

.detail-price .change {
  font-size: 1.25rem;
  font-weight: 600;
}

.detail-info {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-bottom: 1.5rem;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 0.5rem 0;
  border-bottom: 1px solid #f3f4f6;
}

.mini-chart h4 {
  margin: 0 0 0.75rem;
}

.chart-bars {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 100px;
  padding: 0.5rem;
  background: #f9fafb;
  border-radius: 8px;
}

.bar {
  flex: 1;
  background: #4f46e5;
  border-radius: 2px;
  min-height: 5px;
  transition: all 0.3s;
}

.bar:hover {
  background: #6366f1;
}

.empty {
  text-align: center;
  padding: 2rem;
  color: #9ca3af;
}

.positive {
  color: #10b981;
}

.negative {
  color: #ef4444;
}
</style>
