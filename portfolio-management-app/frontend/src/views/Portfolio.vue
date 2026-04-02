<template>
  <div class="portfolio-page">
    <header class="page-header">
      <h1>{{ t('portfolio.title') }}</h1>
      <p class="subtitle">{{ t('portfolio.subtitle') }}</p>
    </header>

    <div class="portfolio-content">
      <!-- 组合列表 -->
      <section class="card">
        <div class="card-header">
          <h2>{{ t('portfolio.myPortfolios') }}</h2>
          <button class="btn-primary" @click="showCreateModal = true">
            {{ t('portfolio.newPortfolio') }}
          </button>
        </div>
        
        <div v-if="loading" class="loading">{{ t('common.loading') }}</div>
        
        <div v-else-if="portfolios.length" class="portfolio-list">
          <div 
            v-for="p in portfolios" 
            :key="p.id"
            class="portfolio-item"
            :class="{ active: selectedPortfolio?.id === p.id }"
            @click="selectPortfolio(p)"
          >
            <div class="portfolio-info">
              <h3>{{ p.name }}</h3>
              <span class="currency">{{ p.baseCurrency }}</span>
            </div>
            <button class="btn-icon" @click.stop="deletePortfolio(p.id)">🗑️</button>
          </div>
        </div>
        
        <p v-else class="empty">{{ t('portfolio.noPortfolios') }}</p>
      </section>

      <!-- 组合详情 -->
      <section v-if="selectedPortfolio" class="card detail-card">
        <div class="card-header">
          <h2>{{ selectedPortfolio.name }}</h2>
          <div class="header-actions">
            <button class="btn-secondary" @click="refreshData">{{ t('portfolio.refresh') }}</button>
            <button class="btn-primary" @click="showAddHolding = true">{{ t('portfolio.addHolding') }}</button>
          </div>
        </div>

        <!-- 汇总信息 -->
        <div v-if="summary" class="summary-section">
          <div class="summary-grid">
            <div class="summary-item">
              <span class="label">{{ t('portfolio.totalCost') }}</span>
              <span class="value">{{ fmtMoney(summary.totalCost) }}</span>
            </div>
            <div class="summary-item">
              <span class="label">{{ t('portfolio.totalMarketValue') }}</span>
              <span class="value">{{ fmtMoney(summary.totalMarketValue) }}</span>
            </div>
            <div class="summary-item" :class="{ 'positive': summary.unrealizedPnl >= 0, 'negative': summary.unrealizedPnl < 0 }">
              <span class="label">{{ t('portfolio.unrealizedPnl') }}</span>
              <span class="value">{{ fmtMoney(summary.unrealizedPnl) }}</span>
            </div>
          </div>

          <!-- 资产分布 -->
          <div class="allocation-section">
            <h3>{{ t('portfolio.assetAllocation') }}</h3>
            <div class="allocation-bars">
              <div v-for="(pct, type) in summary.allocationPct" :key="type" class="alloc-item">
                <span class="type">{{ formatAssetType(type) }}</span>
                <div class="bar-wrap">
                  <div class="bar" :style="{ width: pct + '%' }"></div>
                </div>
                <span class="pct">{{ pct.toFixed(1) }}%</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 持仓列表 -->
        <div class="holdings-section">
          <h3>{{ t('portfolio.holdingsDetail') }}</h3>
          <table v-if="summary?.holdings?.length" class="data-table">
            <thead>
              <tr>
                <th>{{ t('portfolio.assetType') }}</th>
                <th>{{ t('portfolio.ticker') }}</th>
                <th>{{ t('portfolio.quantity') }}</th>
                <th>{{ t('portfolio.costPrice') }}</th>
                <th>{{ t('portfolio.marketPrice') }}</th>
                <th>{{ t('portfolio.marketValue') }}</th>
                <th>{{ t('portfolio.profitLoss') }}</th>
                <th>{{ t('portfolio.action') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="h in summary.holdings" :key="h.holdingId" 
                  :class="{ 'clickable': h.ticker && h.assetType !== 'cash' }"
                  @click="h.ticker && h.assetType !== 'cash' ? showStockHistory(h.ticker) : null">
                <td>{{ formatAssetType(h.assetType) }}</td>
                <td class="ticker-cell">{{ h.ticker || '—' }}</td>
                <td>{{ h.quantity }}</td>
                <td>{{ fmtMoney(h.costBasis / h.quantity) }}</td>
                <td>{{ h.marketPrice ? fmtMoney(h.marketPrice) : '—' }}</td>
                <td>{{ h.marketValue ? fmtMoney(h.marketValue) : '—' }}</td>
                <td :class="{ 'positive': (h.unrealizedPnl || 0) >= 0, 'negative': (h.unrealizedPnl || 0) < 0 }">
                  {{ h.unrealizedPnl != null ? fmtMoney(h.unrealizedPnl) : '—' }}
                </td>
                <td @click.stop>
                  <button class="btn-small" @click="editHolding(h)">{{ t('common.edit') }}</button>
                  <button class="btn-small btn-danger" @click="deleteHolding(h.holdingId)">{{ t('common.delete') }}</button>
                </td>
              </tr>
            </tbody>
          </table>
          <p v-else class="empty">{{ t('portfolio.noHoldings') }}</p>
        </div>
      </section>

      <section v-else class="card empty-card">
        <p>{{ t('portfolio.selectPortfolio') }}</p>
      </section>

      <!-- 股票历史走势 -->
      <section v-if="selectedStockTicker" class="card stock-history-card">
        <div class="card-header">
          <h2>{{ selectedStockTicker.toUpperCase() }} {{ t('portfolio.priceHistory') || '历史走势' }}</h2>
          <button class="btn-icon close-btn" @click="closeStockHistory">✕</button>
        </div>
        
        <!-- 时间范围选择器 -->
        <div class="time-selector">
          <button 
            v-for="option in timeRangeOptions" 
            :key="option.days"
            :class="{ active: selectedStockDays === option.days }"
            @click="changeStockDays(option.days)"
          >
            {{ option.label }}
          </button>
        </div>

        <!-- 股票走势图 -->
        <div v-if="stockHistoryLoading" class="loading">
          <div class="spinner"></div>
          <span>{{ t('common.loading') }}</span>
        </div>
        <div v-else-if="stockHistoryData?.length && stockHistoryData.length >= 2" class="chart-container">
          <div class="chart-header">
            <span class="data-points">{{ stockHistoryData.length }} 个数据点</span>
          </div>
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
                v-if="stockChartPoints"
                :points="stockChartPoints"
                fill="none"
                stroke="#4f46e5"
                stroke-width="2"
              />
              <!-- 数据点 -->
              <circle
                v-for="(point, idx) in stockChartPointsArray"
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
              <span>{{ t('analytics.highest') }}:</span>
              <span class="positive">{{ fmtMoney(stockMaxValue) }}</span>
            </div>
            <div class="info-item">
              <span>{{ t('analytics.lowest') }}:</span>
              <span class="negative">{{ fmtMoney(stockMinValue) }}</span>
            </div>
            <div class="info-item">
              <span>{{ t('analytics.average') }}:</span>
              <span>{{ fmtMoney(stockAvgValue) }}</span>
            </div>
            <div class="info-item">
              <span>{{ t('analytics.change') || '涨跌幅' }}:</span>
              <span :class="stockChangePercent >= 0 ? 'positive' : 'negative'">
                {{ fmtPercent(stockChangePercent) }}
              </span>
            </div>
          </div>
        </div>
        <p v-else class="empty">{{ stockHistoryError || t('analytics.noStockData') || '暂无数据' }}</p>
      </section>
    </div>

    <!-- 新建组合弹窗 -->
    <div v-if="showCreateModal" class="modal-backdrop" @click.self="showCreateModal = false">
      <div class="modal">
        <h3>{{ t('portfolio.createNew') }}</h3>
        <div class="form-group">
          <label>{{ t('portfolio.name') }}</label>
          <input v-model="newPortfolio.name" :placeholder="t('portfolio.placeholder.portfolioName')" />
        </div>
        <div class="form-group">
          <label>{{ t('portfolio.description') }}</label>
          <input v-model="newPortfolio.description" :placeholder="t('portfolio.placeholder.description')" />
        </div>
        <div class="form-group">
          <label>{{ t('portfolio.baseCurrency') }}</label>
          <select v-model="newPortfolio.baseCurrency">
            <option value="USD">USD</option>
            <option value="CNY">CNY</option>
            <option value="EUR">EUR</option>
          </select>
        </div>
        <div class="modal-actions">
          <button class="btn-primary" @click="createPortfolio">{{ t('common.create') }}</button>
          <button class="btn-secondary" @click="showCreateModal = false">{{ t('common.cancel') }}</button>
        </div>
      </div>
    </div>

    <!-- 添加/编辑持仓弹窗 -->
    <div v-if="showAddHolding" class="modal-backdrop" @click.self="closeHoldingModal">
      <div class="modal">
        <h3>{{ editingHolding ? t('portfolio.editHoldingTitle') : t('portfolio.addHoldingTitle') }}</h3>
        <div class="form-group">
          <label>{{ t('portfolio.assetType') }}</label>
          <select v-model="holdingForm.assetType">
            <option value="stock">{{ t('assetType.stock') }}</option>
            <option value="bond">{{ t('assetType.bond') }}</option>
            <option value="fund">{{ t('assetType.fund') }}</option>
            <option value="cash">{{ t('assetType.cash') }}</option>
          </select>
        </div>
        <div class="form-group" v-if="holdingForm.assetType !== 'cash'">
          <label>{{ t('portfolio.ticker') }}</label>
          <input v-model="holdingForm.ticker" @change="onTickerInput" :placeholder="t('portfolio.placeholder.ticker')" />
        </div>
        <div class="form-group">
          <label>{{ t('portfolio.name') }} ({{ t('common.optional') }})</label>
          <input v-model="holdingForm.name" :placeholder="t('portfolio.placeholder.name')" />
        </div>
        <div class="form-group">
          <label>{{ t('portfolio.quantity') }}</label>
          <input v-model.number="holdingForm.quantity" type="number" step="0.01" />
        </div>
        <div class="form-group">
          <label>{{ t('portfolio.purchaseDate') }}</label>
          <input v-model="holdingForm.purchaseDate" type="date" @change="onPurchaseDateChange" />
        </div>
        <div class="form-group">
          <label>{{ t('portfolio.averageCost') }} <small v-if="holdingForm.purchaseDate && !holdingForm.averageCost" style="color: #666;">({{ t('portfolio.autoFill') }})</small></label>
          <input v-model.number="holdingForm.averageCost" type="number" step="0.01" :placeholder="t('portfolio.autoFill')" />
        </div>
        <div class="modal-actions">
          <button class="btn-primary" @click="saveHolding">{{ t('common.save') }}</button>
          <button class="btn-secondary" @click="closeHoldingModal">{{ t('common.cancel') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRoute } from 'vue-router';
import { api } from '../api';
import { t } from '../locales';

const route = useRoute();

const loading = ref(false);
const portfolios = ref([]);
const selectedPortfolio = ref(null);
const summary = ref(null);

// 股票历史走势相关
const selectedStockTicker = ref('');
const selectedStockDays = ref(30);
const stockHistoryData = ref([]); // 当前显示的数据（根据选择的时间范围过滤）
const stockHistoryLoading = ref(false);
const stockHistoryError = ref('');

// 缓存一整年的数据，避免重复API调用
const stockHistoryCache = ref({
  ticker: '',
  fullData: [], // 存储一整年的数据
  lastFetchTime: null
});

// 时间范围选项
const timeRangeOptions = [
  { days: 30, label: '30天' },
  { days: 180, label: '半年' },
  { days: 365, label: '一年' }
];

// 图表配置
const chartWidth = 800;
const chartHeight = 300;
const padding = { top: 20, right: 20, bottom: 30, left: 60 };

// 弹窗状态
const showCreateModal = ref(false);
const showAddHolding = ref(false);
const editingHolding = ref(null);

const newPortfolio = ref({
  name: '',
  description: '',
  baseCurrency: 'USD'
});

const holdingForm = ref({
  assetType: 'stock',
  ticker: '',
  name: '',
  quantity: 0,
  averageCost: 0,
  purchaseDate: null
});

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
  const keyMap = {
    'stock': 'assetType.stock',
    'bond': 'assetType.bond',
    'fund': 'assetType.fund',
    'cash': 'assetType.cash'
  };
  return t(keyMap[type]) || type;
}

async function loadPortfolios() {
  loading.value = true;
  try {
    portfolios.value = await api.listPortfolios();
    // 如果URL中有id，自动选中
    const id = route.params.id;
    if (id) {
      const p = portfolios.value.find(p => p.id === Number(id));
      if (p) await selectPortfolio(p);
    }
  } finally {
    loading.value = false;
  }
}

async function selectPortfolio(p) {
  selectedPortfolio.value = p;
  await loadSummary(p.id);
}

async function loadSummary(id) {
  try {
    summary.value = await api.getSummary(id);
  } catch (e) {
    console.error('加载汇总失败:', e);
  }
}

async function refreshData() {
  if (selectedPortfolio.value) {
    await loadSummary(selectedPortfolio.value.id);
  }
}

async function createPortfolio() {
  try {
    await api.createPortfolio(newPortfolio.value);
    showCreateModal.value = false;
    newPortfolio.value = { name: '', description: '', baseCurrency: 'USD' };
    await loadPortfolios();
  } catch (e) {
    alert(t('messages.createFailed') + ': ' + e.message);
  }
}

async function deletePortfolio(id) {
  if (!confirm(t('messages.confirmDeletePortfolio'))) return;
  try {
    await api.deletePortfolio(id);
    if (selectedPortfolio.value?.id === id) {
      selectedPortfolio.value = null;
      summary.value = null;
    }
    await loadPortfolios();
  } catch (e) {
    alert(t('messages.deleteFailed') + ': ' + e.message);
  }
}

function editHolding(h) {
  editingHolding.value = h;
  holdingForm.value = {
    assetType: h.assetType,
    ticker: h.ticker || '',
    name: '',
    quantity: h.quantity,
    averageCost: h.costBasis / h.quantity,
    purchaseDate: h.purchaseDate || null
  };
  showAddHolding.value = true;
}

function closeHoldingModal() {
  showAddHolding.value = false;
  editingHolding.value = null;
  holdingForm.value = {
    assetType: 'stock',
    ticker: '',
    name: '',
    quantity: 0,
    averageCost: 0,
    purchaseDate: null
  };
}

async function saveHolding() {
  // 校验数量必须大于0
  if (!holdingForm.value.quantity || holdingForm.value.quantity <= 0) {
    alert(t('messages.quantityMustBePositive'));
    return;
  }
  
  // 校验股票代码（非现金类型必须有ticker）
  if (holdingForm.value.assetType !== 'cash') {
    if (!holdingForm.value.ticker || holdingForm.value.ticker.trim() === '') {
      alert(t('messages.tickerRequired'));
      return;
    }
    
    // 验证股票代码是否有效（通过查询价格验证）
    try {
      const detail = await api.getAssetDetail(holdingForm.value.ticker.toUpperCase(), null);
      if (!detail || !detail.currentPrice) {
        alert(t('messages.invalidTicker'));
        return;
      }
    } catch (e) {
      alert(t('messages.invalidTicker'));
      return;
    }
  }
  
  try {
    const payload = {
      ...holdingForm.value,
      ticker: holdingForm.value.assetType === 'cash' ? null : holdingForm.value.ticker
    };
    
    if (editingHolding.value) {
      await api.updateHolding(editingHolding.value.holdingId, payload);
    } else {
      await api.addHolding(selectedPortfolio.value.id, payload);
    }
    closeHoldingModal();
    await refreshData();
  } catch (e) {
    alert(t('messages.saveFailed') + ': ' + e.message);
  }
}

// 当用户输入股票代码时，自动获取价格
let tickerDebounceTimer = null;

async function onTickerInput() {
  const ticker = holdingForm.value.ticker;
  if (!ticker || holdingForm.value.assetType === 'cash' || editingHolding.value) {
    return;
  }
  
  // 如果有购入日期，优先使用历史价格
  if (holdingForm.value.purchaseDate) {
    await fetchHistoricalPriceForDate();
    return;
  }
  
  // 防抖：避免用户每输入一个字母就触发请求
  clearTimeout(tickerDebounceTimer);
  tickerDebounceTimer = setTimeout(async () => {
    try {
      // 获取股票详情
      const detail = await api.getAssetDetail(ticker.toUpperCase(), null);
      if (detail && detail.currentPrice) {
        // 自动填充当前价格作为参考
        holdingForm.value.averageCost = detail.currentPrice;
        console.log('自动填充当前价格:', detail.currentPrice);
      }
    } catch (e) {
      console.log('获取股票价格失败:', e.message);
    }
  }, 500); // 500ms 防抖
}

// 当选择购入日期时，查询历史价格
async function onPurchaseDateChange() {
  const ticker = holdingForm.value.ticker;
  const purchaseDate = holdingForm.value.purchaseDate;
  
  if (!ticker || !purchaseDate || holdingForm.value.assetType === 'cash') {
    return;
  }
  
  await fetchHistoricalPriceForDate();
}

// 查询指定日期的历史价格
async function fetchHistoricalPriceForDate() {
  const ticker = holdingForm.value.ticker;
  const purchaseDate = holdingForm.value.purchaseDate;
  
  if (!ticker || !purchaseDate) return;
  
  try {
    // 将日期格式化为 yyyy-MM-dd 字符串（兼容 Date 对象和字符串）
    let dateStr;
    if (purchaseDate instanceof Date) {
      dateStr = purchaseDate.toISOString().split('T')[0];
    } else if (typeof purchaseDate === 'string') {
      // 如果已经是字符串格式（如 "2024-01-15"），直接使用
      dateStr = purchaseDate;
    } else {
      console.log('日期格式不正确:', purchaseDate);
      return;
    }
    
    console.log('查询历史价格:', ticker, dateStr);
    const result = await api.getHistoricalPrice(ticker.toUpperCase(), dateStr);
    if (result && result.found && result.price) {
      holdingForm.value.averageCost = result.price;
      console.log('自动填充历史价格:', result.price);
    } else {
      console.log('未找到历史价格:', result?.message || '未知原因');
    }
  } catch (e) {
    console.log('获取历史价格失败:', e.message);
  }
}

async function deleteHolding(id) {
  if (!confirm(t('messages.confirmDeleteHolding'))) return;
  try {
    await api.deleteHolding(id);
    await refreshData();
  } catch (e) {
    alert(t('messages.deleteFailed') + ': ' + e.message);
  }
}

onMounted(() => {
  loadPortfolios();
});

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
  // 清空缓存
  stockHistoryCache.value = {
    ticker: '',
    fullData: [],
    lastFetchTime: null
  };
}

// 加载个股历史数据
// forceRefresh: 是否强制从API获取新数据（首次打开或切换股票时）
async function loadStockHistory(forceRefresh = false) {
  if (!selectedStockTicker.value.trim()) {
    stockHistoryError.value = '请输入股票代码';
    return;
  }
  
  const ticker = selectedStockTicker.value.trim().toUpperCase();
  
  // 检查缓存：如果是同一支股票且缓存中有数据，且不需要强制刷新
  if (!forceRefresh && 
      stockHistoryCache.value.ticker === ticker && 
      stockHistoryCache.value.fullData.length > 0) {
    console.log('使用缓存数据，无需API调用');
    filterDataByTimeRange();
    return;
  }
  
  stockHistoryLoading.value = true;
  stockHistoryError.value = '';
  
  try {
    // 始终获取一整年的数据（365天）
    console.log('从API获取一整年数据...');
    const response = await api.getStockHistory(ticker, 365);
    
    console.log('Stock history response:', response);
    
    if (response.found && response.data && response.data.length > 0) {
      // 转换数据格式并缓存完整数据
      const fullData = response.data.map(d => ({
        date: d.date,
        value: d.price
      }));
      
      // 更新缓存
      stockHistoryCache.value = {
        ticker: ticker,
        fullData: fullData,
        lastFetchTime: new Date()
      };
      
      console.log('完整数据已缓存:', fullData.length, 'points');
      
      // 根据当前选择的时间范围过滤数据
      filterDataByTimeRange();
    } else {
      console.log('No data found:', response);
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
  
  // 如果选择的是一年，显示全部数据
  if (days >= 365) {
    stockHistoryData.value = fullData;
    console.log('显示全部数据:', fullData.length, 'points');
    return;
  }
  
  // 计算需要保留的数据点数量（取最近N天）
  // 数据是按日期升序排列的，所以取最后N个
  const dataPointsNeeded = Math.min(days, fullData.length);
  const filteredData = fullData.slice(-dataPointsNeeded);
  
  stockHistoryData.value = filteredData;
  console.log(`显示最近${days}天数据:`, filteredData.length, 'points (从缓存过滤)');
}

// 切换时间范围 - 只过滤缓存数据，不发送新API请求
function changeStockDays(days) {
  selectedStockDays.value = days;
  // 从缓存中过滤数据，不发送新请求
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

// 计算涨跌幅
const stockChangePercent = computed(() => {
  if (!stockHistoryData.value?.length || stockHistoryData.value.length < 2) return 0;
  const first = stockHistoryData.value[0].value;
  const last = stockHistoryData.value[stockHistoryData.value.length - 1].value;
  return ((last - first) / first) * 100;
});
</script>

<style scoped>
.portfolio-page {
  padding: 1.5rem;
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 1.5rem;
}

.page-header h1 {
  margin: 0;
  font-size: 1.75rem;
}

.subtitle {
  margin: 0.25rem 0 0;
  color: #666;
}

.portfolio-content {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 1.5rem;
}

.card {
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.card-header h2 {
  margin: 0;
  font-size: 1.25rem;
}

.btn-primary {
  padding: 0.5rem 1rem;
  background: #4f46e5;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
}

.btn-secondary {
  padding: 0.5rem 1rem;
  background: #f3f4f6;
  color: #374151;
  border: none;
  border-radius: 8px;
  cursor: pointer;
}

.btn-small {
  padding: 0.25rem 0.5rem;
  font-size: 0.75rem;
  border-radius: 6px;
  border: none;
  cursor: pointer;
}

.btn-danger {
  background: #ef4444;
  color: white;
}

.header-actions {
  display: flex;
  gap: 0.5rem;
}

.portfolio-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.portfolio-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  background: #f9fafb;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.portfolio-item:hover,
.portfolio-item.active {
  background: #4f46e5;
  color: white;
}

.portfolio-item.active .currency {
  color: rgba(255,255,255,0.8);
}

.portfolio-info h3 {
  margin: 0 0 0.25rem;
  font-size: 1rem;
}

.currency {
  font-size: 0.75rem;
  color: #666;
}

.btn-icon {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0.25rem;
}

.summary-section {
  margin-bottom: 1.5rem;
  padding-bottom: 1.5rem;
  border-bottom: 1px solid #e5e7eb;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.summary-item {
  text-align: center;
  padding: 1rem;
  background: #f9fafb;
  border-radius: 10px;
}

.summary-item.positive {
  background: #ecfdf5;
}

.summary-item.negative {
  background: #fef2f2;
}

.summary-item .label {
  display: block;
  font-size: 0.875rem;
  color: #666;
  margin-bottom: 0.5rem;
}

.summary-item .value {
  font-size: 1.5rem;
  font-weight: 700;
}

.allocation-section h3 {
  margin: 0 0 1rem;
  font-size: 1rem;
}

.allocation-bars {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.alloc-item {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.alloc-item .type {
  width: 60px;
  font-size: 0.875rem;
}

.bar-wrap {
  flex: 1;
  height: 8px;
  background: #f3f4f6;
  border-radius: 4px;
  overflow: hidden;
}

.bar {
  height: 100%;
  background: linear-gradient(90deg, #4f46e5, #06b6d4);
  border-radius: 4px;
}

.alloc-item .pct {
  width: 60px;
  text-align: right;
  font-size: 0.875rem;
}

.holdings-section h3 {
  margin: 0 0 1rem;
  font-size: 1rem;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}

.data-table th,
.data-table td {
  padding: 0.75rem;
  text-align: left;
  border-bottom: 1px solid #e5e7eb;
}

.data-table th {
  font-weight: 600;
  color: #666;
}

.positive {
  color: #10b981;
}

.negative {
  color: #ef4444;
}

.empty {
  text-align: center;
  padding: 2rem;
  color: #9ca3af;
}

.empty-card {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
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
  max-width: 400px;
  background: white;
  border-radius: 16px;
  padding: 1.5rem;
}

.modal h3 {
  margin: 0 0 1rem;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  font-size: 0.875rem;
  color: #666;
  margin-bottom: 0.25rem;
}

.form-group input,
.form-group select {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 8px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 1.5rem;
}

@media (max-width: 768px) {
  .portfolio-content {
    grid-template-columns: 1fr;
  }
  
  .summary-grid {
    grid-template-columns: 1fr;
  }
}

/* 股票历史走势样式 */
.stock-history-card {
  grid-column: 1 / -1;
  margin-top: 1rem;
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

.chart-header {
  margin-bottom: 0.5rem;
  padding: 0 0.5rem;
}

.data-points {
  font-size: 0.75rem;
  color: #6b7280;
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

/* 持仓列表可点击样式 */
.data-table tbody tr.clickable {
  cursor: pointer;
  transition: background-color 0.2s;
}

.data-table tbody tr.clickable:hover {
  background-color: #f3f4f6;
}

.ticker-cell {
  font-weight: 600;
  color: #4f46e5;
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

.loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 2rem;
}

.positive {
  color: #10b981;
}

.negative {
  color: #ef4444;
}
</style>
