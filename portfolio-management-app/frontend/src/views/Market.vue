<template>
  <div class="market">
    <header class="page-header">
      <h1>{{ t('market.title') }}</h1>
      <p class="subtitle">{{ t('market.subtitle') }}</p>
    </header>

    <!-- 组合选择器 -->
    <div class="portfolio-selector">
      <label>{{ t('market.selectPortfolio') }}：</label>
      <select v-model="selectedPortfolioId" @change="onPortfolioChange">
        <option value="">{{ t('market.allHoldings') }}</option>
        <option v-for="p in portfolios" :key="p.id" :value="p.id">
          {{ p.name }}
        </option>
      </select>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading">
      <div class="spinner"></div>
      <span>{{ t('common.loading') }}</span>
    </div>

    <!-- 错误提示 -->
    <div v-else-if="error" class="error-message">
      {{ error }}
      <button @click="loadData" class="retry-btn">{{ t('common.retry') }}</button>
    </div>

    <!-- 数据展示 -->
    <div v-else class="market-content">
      <!-- 我的持仓行情 -->
      <section class="card">
        <div class="card-header">
          <h2>{{ t('market.myHoldings') }}</h2>
          <span class="update-time">{{ t('market.updateTime') }}：{{ updateTime }}</span>
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
              <span class="quantity">{{ t('market.holding') }}: {{ stock.holdingQuantity }}</span>
              <span class="value">{{ t('market.position') }}: {{ fmtMoney(stock.holdingValue) }}</span>
            </div>
          </div>
        </div>
        <p v-else class="empty">{{ t('market.noHoldingsData') }}</p>
      </section>

      <!-- 涨跌排行 -->
      <div class="movers-grid">
        <section class="card">
          <h2>{{ t('market.gainers') || '盈利冠军' }}</h2>
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
          <h2>{{ t('market.losers') || '亏损预警' }}</h2>
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
        <h2>{{ t('market.searchStock') }}</h2>
        <div class="search-box">
          <input 
            v-model="searchTicker" 
            :placeholder="t('market.searchPlaceholder')"
            @keyup.enter="search"
          />
          <button @click="search" :disabled="searching">{{ searching ? t('market.searching') : t('common.search') }}</button>
        </div>
        <div v-if="searchResult" class="search-result">
          <div class="result-item" :class="{ 'available': searchResult.isAvailable }">
            <span class="ticker">{{ searchResult.ticker }}</span>
            <span class="price">{{ fmtMoney(searchResult.currentPrice) }}</span>
            <span class="status">{{ searchResult.isAvailable ? t('market.available') : t('market.unavailable') }}</span>
          </div>
        </div>
      </section>

      <!-- 热门股票 -->
      <section class="card">
        <h2>{{ t('market.popularStocks') }}</h2>
        <div v-if="popularStocks?.length" class="stock-table">
          <table>
            <thead>
              <tr>
                <th>{{ t('market.ticker') }}</th>
                <th>{{ t('market.name') }}</th>
                <th>{{ t('market.price') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="stock in popularStocks" :key="stock.ticker" @click="showDetail(stock.ticker)">
                <td class="ticker">{{ stock.ticker }}</td>
                <td class="company-name">{{ stock.name }}</td>
                <td class="price">{{ formatPriceWithCurrency(stock.currentPrice, stock.priceSource) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-else class="empty">{{ t('common.noData') }}</p>
      </section>
    </div>

    <!-- 股票详情弹窗 -->
    <div v-if="detailModalOpen" class="modal-backdrop" @click.self="closeDetail">
      <div class="modal">
        <div class="modal-header">
          <h3>{{ detailData?.ticker }} {{ t('market.stockDetail') }}</h3>
          <button class="close-btn" @click="closeDetail">×</button>
        </div>
        <div v-if="detailLoading" class="modal-loading">{{ t('common.loading') }}</div>
        <div v-else-if="detailData" class="modal-content">
          <div class="detail-price">
            <span class="current">{{ fmtMoney(detailData.currentPrice) }}</span>
            <span class="change" :class="{ 'positive': detailData.priceChangePercent > 0, 'negative': detailData.priceChangePercent < 0 }">
              {{ detailData.priceChangePercent > 0 ? '+' : '' }}{{ detailData.priceChangePercent?.toFixed(2) }}%
            </span>
          </div>
          <div class="detail-info">
            <div class="info-row">
              <span>{{ t('market.previousClose') }}:</span>
              <span>{{ fmtMoney(detailData.previousClose) }}</span>
            </div>
            <div class="info-row">
              <span>{{ t('market.priceChange') }}:</span>
              <span :class="{ 'positive': detailData.priceChange > 0, 'negative': detailData.priceChange < 0 }">
                {{ detailData.priceChange > 0 ? '+' : '' }}{{ fmtMoney(detailData.priceChange) }}
              </span>
            </div>
            <div class="info-row" v-if="detailData.isInPortfolio">
              <span>{{ t('market.myPosition') }}:</span>
              <span>{{ detailData.holdingQuantity }} {{ t('market.shares') }}</span>
            </div>
          </div>
          <div v-if="detailData.priceHistory?.length" class="mini-chart">
            <h4>{{ t('market.recentTrend') }}</h4>
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

function fmtMoney(v) {
  if (v == null || Number.isNaN(Number(v))) return '—';
  return new Intl.NumberFormat('zh-CN', { 
    style: 'currency', 
    currency: 'USD',
    maximumFractionDigits: 2 
  }).format(Number(v));
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
        const [gainersRes, losersRes] = await Promise.all([
          api.getTopGainers(effectivePortfolioId),
          api.getTopLosers(effectivePortfolioId)
        ]);
        console.log('Gainers response:', gainersRes);
        console.log('Losers response:', losersRes);
        gainers.value = gainersRes || [];
        losers.value = losersRes || [];
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
    error.value = e.message || t('market.loadFailed');
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
