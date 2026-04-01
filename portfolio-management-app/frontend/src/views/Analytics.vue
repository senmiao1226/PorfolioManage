<template>
  <div class="analytics">
    <header class="page-header">
      <h1>{{ t('analytics.title') }}</h1>
      <p class="subtitle">{{ t('analytics.subtitle') }}</p>
    </header>

    <!-- 组合选择器 -->
    <div class="portfolio-selector">
      <label>选择组合：</label>
      <select v-model="selectedPortfolioId" @change="onPortfolioChange">
        <option value="">全局汇总</option>
        <option v-for="p in portfolios" :key="p.id" :value="p.id">
          {{ p.name }}
        </option>
      </select>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading">
      <div class="spinner"></div>
      <span>加载中...</span>
    </div>

    <!-- 错误提示 -->
    <div v-else-if="error" class="error-message">
      {{ error }}
      <button @click="loadData" class="retry-btn">重试</button>
    </div>

    <!-- 数据展示 -->
    <div v-else class="analytics-content">
      <!-- 业绩统计 -->
      <section class="card">
        <h2>业绩统计</h2>
        <div v-if="performanceStats" class="stats-grid">
          <div class="stat-card">
            <div class="stat-label">总成本</div>
            <div class="stat-value">{{ fmtMoney(performanceStats.totalCost) }}</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">总市值</div>
            <div class="stat-value">{{ fmtMoney(performanceStats.totalMarketValue) }}</div>
          </div>
          <div class="stat-card" :class="{ 'positive': performanceStats.unrealizedPnl >= 0, 'negative': performanceStats.unrealizedPnl < 0 }">
            <div class="stat-label">未实现盈亏</div>
            <div class="stat-value">
              {{ fmtMoney(performanceStats.unrealizedPnl) }}
              <span class="percent">({{ fmtPercent(performanceStats.returnPercent) }})</span>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-label">持仓数量</div>
            <div class="stat-value">{{ performanceStats.holdingCount }}个</div>
          </div>
        </div>
        <p v-else class="empty">暂无业绩数据</p>
      </section>

      <!-- 资产配置饼图 -->
      <section class="card">
        <h2>资产配置</h2>
        <div v-if="assetAllocation?.length" class="allocation-section">
          <div class="pie-chart">
            <div class="pie" :style="pieStyle"></div>
            <div class="pie-legend">
              <div v-for="item in assetAllocation" :key="item.assetType" class="legend-item">
                <span class="dot" :style="{ background: getColor(item.assetType) }"></span>
                <span class="label">{{ formatAssetType(item.assetType) }}</span>
                <span class="value">{{ item.percentage }}%</span>
              </div>
            </div>
          </div>
          <div class="allocation-table">
            <table>
              <thead>
                <tr>
                  <th>资产类型</th>
                  <th>市值</th>
                  <th>占比</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in assetAllocation" :key="item.assetType">
                  <td>{{ formatAssetType(item.assetType) }}</td>
                  <td>{{ fmtMoney(item.value) }}</td>
                  <td>
                    <div class="pct-bar">
                      <div class="pct-fill" :style="{ width: item.percentage + '%', background: getColor(item.assetType) }"></div>
                      <span>{{ item.percentage }}%</span>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <p v-else class="empty">暂无资产配置数据</p>
      </section>

      <!-- 历史走势 -->
      <section class="card">
        <div class="card-header">
          <h2>历史走势</h2>
          <div class="time-selector">
            <button 
              v-for="days in [7, 30, 90, 365]" 
              :key="days"
              :class="{ active: selectedDays === days }"
              @click="changeDays(days)"
            >
              {{ days }}天
            </button>
          </div>
        </div>
        <div v-if="historyData?.length" class="chart-container">
          <div class="chart-area">
            <svg :viewBox="`0 0 ${chartWidth} ${chartHeight}`" preserveAspectRatio="none">
              <!-- 网格线 -->
              <line v-for="i in 5" :key="'h'+i"
                :x1="0" :y1="chartHeight * i / 5" 
                :x2="chartWidth" :y2="chartHeight * i / 5"
                stroke="#f3f4f6" stroke-width="1"
              />
              <!-- 折线 -->
              <polyline
                :points="chartPoints"
                fill="none"
                stroke="#4f46e5"
                stroke-width="2"
              />
              <!-- 数据点 -->
              <circle
                v-for="(point, idx) in chartPointsArray"
                :key="idx"
                :cx="point.x"
                :cy="point.y"
                r="3"
                fill="#4f46e5"
              />
            </svg>
          </div>
          <div class="chart-info">
            <div class="info-item">
              <span>最高:</span>
              <span class="positive">{{ fmtMoney(maxValue) }}</span>
            </div>
            <div class="info-item">
              <span>最低:</span>
              <span class="negative">{{ fmtMoney(minValue) }}</span>
            </div>
            <div class="info-item">
              <span>平均:</span>
              <span>{{ fmtMoney(avgValue) }}</span>
            </div>
          </div>
        </div>
        <p v-else class="empty">暂无历史数据</p>
      </section>

      <!-- 前N大持仓 -->
      <section class="card" v-if="selectedPortfolioId">
        <h2>前10大持仓</h2>
        <div v-if="topHoldings?.length" class="holdings-list">
          <div v-for="(item, idx) in topHoldings" :key="item.ticker" class="holding-item">
            <div class="holding-rank">{{ idx + 1 }}</div>
            <div class="holding-info">
              <div class="holding-name">
                <span class="ticker">{{ item.ticker }}</span>
                <span class="type">{{ formatAssetType(item.assetType) }}</span>
              </div>
              <div class="holding-detail">
                <span>数量: {{ item.quantity }}</span>
                <span>成本: {{ fmtMoney(item.costBasis) }}</span>
              </div>
            </div>
            <div class="holding-value">
              <div class="market-value">{{ fmtMoney(item.marketValue) }}</div>
              <div class="unrealized" :class="{ 'positive': item.unrealizedPnl >= 0, 'negative': item.unrealizedPnl < 0 }">
                {{ item.unrealizedPnl >= 0 ? '+' : '' }}{{ fmtMoney(item.unrealizedPnl) }}
              </div>
            </div>
          </div>
        </div>
        <p v-else class="empty">暂无持仓数据</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { api } from '../api';
import { useI18n } from '../composables/useI18n';

const { lang, t } = useI18n();

const loading = ref(false);
const error = ref('');
const portfolios = ref([]);
const selectedPortfolioId = ref('');
const selectedDays = ref(30);

const performanceStats = ref(null);
const assetAllocation = ref([]);
const historyData = ref([]);
const topHoldings = ref([]);

// 图表配置
const chartWidth = 800;
const chartHeight = 300;
const padding = { top: 20, right: 20, bottom: 30, left: 60 };

const colors = {
  'stock': '#4f46e5',
  'bond': '#06b6d4',
  'fund': '#f59e0b',
  'cash': '#10b981',
  'default': '#8b5cf6'
};

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
  return (v >= 0 ? '+' : '') + v.toFixed(2) + '%';
}

function formatAssetType(type) {
  const map = {
    'stock': '股票',
    'bond': '债券',
    'fund': '基金',
    'cash': '现金'
  };
  return map[type] || type;
}

function getColor(type) {
  return colors[type] || colors.default;
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
    const isGlobal = !selectedPortfolioId.value;
    const portfolioId = selectedPortfolioId.value;

    // 并行加载数据
    const [
      allocationRes,
      historyRes,
      statsRes
    ] = await Promise.all([
      isGlobal 
        ? api.getGlobalAssetAllocation()
        : api.getAssetAllocation(portfolioId),
      isGlobal
        ? api.getGlobalHistory(selectedDays.value)
        : api.getPortfolioHistory(portfolioId, selectedDays.value),
      isGlobal
        ? api.getGlobalPerformanceStats()
        : api.getPerformanceStats(portfolioId)
    ]);

    assetAllocation.value = allocationRes;
    historyData.value = historyRes;
    performanceStats.value = statsRes;

    // 只有具体组合才加载前N大持仓
    if (!isGlobal) {
      topHoldings.value = await api.getTopHoldings(portfolioId, 10);
    } else {
      topHoldings.value = [];
    }
  } catch (e) {
    error.value = e.message || '加载失败';
  } finally {
    loading.value = false;
  }
}

function onPortfolioChange() {
  loadData();
}

function changeDays(days) {
  selectedDays.value = days;
  loadData();
}

// 饼图样式
const pieStyle = computed(() => {
  if (!assetAllocation.value?.length) return {};
  
  let gradient = 'conic-gradient(';
  let currentDeg = 0;
  
  assetAllocation.value.forEach((item, idx) => {
    const deg = (item.percentage / 100) * 360;
    const color = getColor(item.assetType);
    gradient += `${color} ${currentDeg}deg ${currentDeg + deg}deg`;
    if (idx < assetAllocation.value.length - 1) {
      gradient += ', ';
    }
    currentDeg += deg;
  });
  
  gradient += ')';
  return { background: gradient };
});

// 图表计算
const chartPointsArray = computed(() => {
  if (!historyData.value?.length) return [];
  
  const values = historyData.value.map(d => d.value);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  
  const usableWidth = chartWidth - padding.left - padding.right;
  const usableHeight = chartHeight - padding.top - padding.bottom;
  
  return historyData.value.map((d, i) => ({
    x: padding.left + (usableWidth * i / (historyData.value.length - 1)),
    y: chartHeight - padding.bottom - (usableHeight * (d.value - min) / range)
  }));
});

const chartPoints = computed(() => {
  return chartPointsArray.value.map(p => `${p.x},${p.y}`).join(' ');
});

const maxValue = computed(() => {
  if (!historyData.value?.length) return 0;
  return Math.max(...historyData.value.map(d => d.value));
});

const minValue = computed(() => {
  if (!historyData.value?.length) return 0;
  return Math.min(...historyData.value.map(d => d.value));
});

const avgValue = computed(() => {
  if (!historyData.value?.length) return 0;
  const sum = historyData.value.reduce((acc, d) => acc + d.value, 0);
  return sum / historyData.value.length;
});

onMounted(async () => {
  await loadPortfolios();
  await loadData();
});
</script>

<style scoped>
.analytics {
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

.card h2 {
  margin: 0 0 1rem;
  font-size: 1.25rem;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}

.stat-card {
  padding: 1.25rem;
  background: #f9fafb;
  border-radius: 10px;
  text-align: center;
}

.stat-card.positive {
  background: linear-gradient(135deg, #ecfdf5 0%, #d1fae5 100%);
}

.stat-card.negative {
  background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%);
}

.stat-label {
  font-size: 0.875rem;
  color: #666;
  margin-bottom: 0.5rem;
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1a1a1a;
}

.percent {
  font-size: 0.875rem;
  font-weight: 500;
  margin-left: 0.5rem;
}

.allocation-section {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2rem;
}

.pie-chart {
  display: flex;
  align-items: center;
  gap: 2rem;
}

.pie {
  width: 200px;
  height: 200px;
  border-radius: 50%;
}

.pie-legend {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.label {
  flex: 1;
}

.value {
  font-weight: 600;
}

.allocation-table table {
  width: 100%;
  border-collapse: collapse;
}

.allocation-table th,
.allocation-table td {
  padding: 0.75rem;
  text-align: left;
  border-bottom: 1px solid #e5e7eb;
}

.pct-bar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.pct-fill {
  height: 8px;
  border-radius: 4px;
  min-width: 20px;
}

.time-selector {
  display: flex;
  gap: 0.5rem;
}

.time-selector button {
  padding: 0.5rem 1rem;
  border: 1px solid #ddd;
  background: white;
  border-radius: 6px;
  cursor: pointer;
}

.time-selector button.active {
  background: #4f46e5;
  color: white;
  border-color: #4f46e5;
}

.chart-container {
  display: flex;
  gap: 1.5rem;
}

.chart-area {
  flex: 1;
  height: 300px;
}

.chart-area svg {
  width: 100%;
  height: 100%;
}

.chart-info {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  min-width: 150px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  padding: 0.75rem;
  background: #f9fafb;
  border-radius: 8px;
}

.holdings-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.holding-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
  background: #f9fafb;
  border-radius: 10px;
}

.holding-rank {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #e5e7eb;
  border-radius: 50%;
  font-weight: 700;
}

.holding-item:nth-child(1) .holding-rank {
  background: #fde68a;
}

.holding-item:nth-child(2) .holding-rank {
  background: #e5e7eb;
}

.holding-item:nth-child(3) .holding-rank {
  background: #fed7aa;
}

.holding-info {
  flex: 1;
}

.holding-name {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.25rem;
}

.holding-name .ticker {
  font-weight: 700;
  font-size: 1.1rem;
}

.holding-name .type {
  font-size: 0.75rem;
  color: #666;
  background: #e5e7eb;
  padding: 0.125rem 0.5rem;
  border-radius: 4px;
}

.holding-detail {
  font-size: 0.75rem;
  color: #666;
  display: flex;
  gap: 1rem;
}

.holding-value {
  text-align: right;
}

.market-value {
  font-size: 1.1rem;
  font-weight: 700;
  margin-bottom: 0.25rem;
}

.unrealized {
  font-size: 0.875rem;
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

@media (max-width: 768px) {
  .allocation-section {
    grid-template-columns: 1fr;
  }
  
  .chart-container {
    flex-direction: column;
  }
}
</style>
