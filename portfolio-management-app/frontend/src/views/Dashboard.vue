<template>
  <div class="dashboard">
    <header class="page-header">
      <h1>{{ t('dashboard.title') }}</h1>
      <p class="subtitle">{{ t('dashboard.subtitle') }}</p>
    </header>

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
    <div v-else class="dashboard-content">
      <!-- 核心指标卡片 -->
      <section class="kpi-cards">
        <div class="kpi-card">
          <div class="kpi-icon">💰</div>
          <div class="kpi-info">
            <div class="kpi-label">{{ t('dashboard.totalValue') }}</div>
            <div class="kpi-value">{{ fmtMoney(summary?.totalPortfolioValue) }}</div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon">📈</div>
          <div class="kpi-info">
            <div class="kpi-label">{{ t('dashboard.totalCost') }}</div>
            <div class="kpi-value">{{ fmtMoney(summary?.totalCost) }}</div>
          </div>
        </div>
        <div class="kpi-card" :class="{ 'positive': summary?.totalUnrealizedPnl >= 0, 'negative': summary?.totalUnrealizedPnl < 0 }">
          <div class="kpi-icon">{{ summary?.totalUnrealizedPnl >= 0 ? '📊' : '📉' }}</div>
          <div class="kpi-info">
            <div class="kpi-label">{{ t('dashboard.unrealizedPnl') }}</div>
            <div class="kpi-value">
              {{ fmtMoney(summary?.totalUnrealizedPnl) }}
              <span class="percent">({{ fmtPercent(summary?.totalUnrealizedPnlPercent) }})</span>
            </div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon">🎯</div>
          <div class="kpi-info">
            <div class="kpi-label">{{ t('dashboard.portfolioCount') }}</div>
            <div class="kpi-value">{{ summary?.portfolioCount || 0 }}</div>
          </div>
        </div>
      </section>

      <!-- 市场概览 -->
      <section class="card">
        <h2>{{ t('dashboard.marketOverview') || '市场概览' }}</h2>
        <div v-if="marketIndexes?.length" class="market-indexes">
          <div v-for="index in marketIndexes" :key="index.indexName" class="index-item">
            <span class="index-name">{{ index.indexName }}</span>
            <div class="index-info">
              <span class="index-value">{{ fmtMoney(index.currentValue) }}</span>
              <span class="index-change" :class="{ 'positive': index.trend === 'UP', 'negative': index.trend === 'DOWN' }">
                {{ index.changePercent > 0 ? '+' : '' }}{{ index.changePercent?.toFixed(2) }}%
              </span>
            </div>
          </div>
        </div>
        <p v-else class="empty">{{ t('dashboard.noMarketData') || '暂无市场数据' }}</p>
      </section>

      <!-- 资产分布 -->
      <section class="card">
        <h2>{{ t('dashboard.assetDistribution') }}</h2>
        <div v-if="assetDistribution?.length" class="distribution">
          <div v-for="item in assetDistribution" :key="item.assetType" class="dist-item">
            <div class="dist-label">{{ formatAssetType(item.assetType) }}</div>
            <div class="dist-bar-wrap">
              <div class="dist-bar" :style="{ width: item.percentage + '%' }"></div>
            </div>
            <div class="dist-value">
              <span class="money">{{ fmtMoney(item.value) }}</span>
              <span class="pct">{{ item.percentage }}%</span>
            </div>
          </div>
        </div>
        <p v-else class="empty">{{ t('dashboard.noAssetDistribution') }}</p>
      </section>

      <!-- 组合列表 -->
      <section class="card">
        <h2>{{ t('dashboard.myPortfolios') }}</h2>
        <div v-if="portfolios?.length" class="portfolio-list">
          <div 
            v-for="p in portfolios" 
            :key="p.portfolioId" 
            class="portfolio-item"
            @click="goToPortfolio(p.portfolioId)"
          >
            <div class="portfolio-info">
              <h3>{{ p.name }}</h3>
              <span class="currency">{{ p.baseCurrency }}</span>
            </div>
            <div class="portfolio-stats">
              <div class="stat">
                <span class="stat-label">{{ t('dashboard.marketValue') }}</span>
                <span class="stat-value">{{ fmtMoney(p.totalValue) }}</span>
              </div>
              <div class="stat" :class="{ 'positive': p.unrealizedPnl >= 0, 'negative': p.unrealizedPnl < 0 }">
                <span class="stat-label">{{ t('dashboard.profitLoss') }}</span>
                <span class="stat-value">{{ fmtMoney(p.unrealizedPnl) }}</span>
              </div>
              <div class="stat">
                <span class="stat-label">{{ t('dashboard.holdings') }}</span>
                <span class="stat-value">{{ p.holdingCount }}{{ currentLang.value === 'zh' ? '个' : '' }}</span>
              </div>
            </div>
          </div>
        </div>
        <p v-else class="empty">{{ t('dashboard.noPortfolios') }}</p>
      </section>

    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api';
import { t, currentLang } from '../locales';

const router = useRouter();

const loading = ref(false);
const error = ref('');
const summary = ref(null);
const portfolios = ref([]);
const assetDistribution = ref([]);
const marketIndexes = ref([]);


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

async function loadData() {
  loading.value = true;
  error.value = '';
  try {
    const [summaryRes, portfoliosRes, distributionRes, marketRes] = await Promise.all([
      api.getDashboardSummary().catch(() => null),
      api.getDashboardPortfolios().catch(() => []),
      api.getAssetDistribution().catch(() => []),
      api.getMarketOverview().catch(() => [])
    ]);
    summary.value = summaryRes;
    portfolios.value = portfoliosRes;
    assetDistribution.value = distributionRes;
    marketIndexes.value = marketRes;
  } catch (e) {
    error.value = e.message || t('common.error');
  } finally {
    loading.value = false;
  }
}

function goToPortfolio(id) {
  router.push(`/portfolio/${id}`);
}

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.dashboard {
  padding: 1.5rem;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 1.5rem;
}

.page-header h1 {
  margin: 0;
  font-size: 1.75rem;
  color: #1a1a1a;
}

.subtitle {
  margin: 0.25rem 0 0;
  color: #666;
}

.loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 3rem;
  color: #666;
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

.error-message {
  text-align: center;
  padding: 2rem;
  color: #b3261e;
}

.retry-btn {
  margin-left: 0.5rem;
  padding: 0.25rem 0.75rem;
  background: #4f46e5;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

.kpi-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.kpi-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem;
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.kpi-card.positive {
  background: linear-gradient(135deg, #ecfdf5 0%, #d1fae5 100%);
}

.kpi-card.negative {
  background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%);
}

.kpi-icon {
  font-size: 2rem;
}

.kpi-label {
  font-size: 0.875rem;
  color: #666;
  margin-bottom: 0.25rem;
}

.kpi-value {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1a1a1a;
}

.percent {
  font-size: 0.875rem;
  font-weight: 500;
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
  color: #1a1a1a;
}

.distribution {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.dist-item {
  display: grid;
  grid-template-columns: 80px 1fr 150px;
  align-items: center;
  gap: 1rem;
}

.dist-label {
  font-size: 0.875rem;
  color: #666;
}

.dist-bar-wrap {
  height: 8px;
  background: #f3f4f6;
  border-radius: 4px;
  overflow: hidden;
}

.dist-bar {
  height: 100%;
  background: linear-gradient(90deg, #4f46e5, #06b6d4);
  border-radius: 4px;
  transition: width 0.3s ease;
}

.dist-value {
  display: flex;
  justify-content: space-between;
  font-size: 0.875rem;
}

.dist-value .money {
  font-weight: 600;
  color: #1a1a1a;
}

.dist-value .pct {
  color: #666;
}

.portfolio-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.portfolio-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  background: #f9fafb;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.portfolio-item:hover {
  background: #f3f4f6;
  transform: translateX(4px);
}

.portfolio-info h3 {
  margin: 0 0 0.25rem;
  font-size: 1rem;
}

.currency {
  font-size: 0.75rem;
  color: #666;
  background: #e5e7eb;
  padding: 0.125rem 0.5rem;
  border-radius: 4px;
}

.portfolio-stats {
  display: flex;
  gap: 1.5rem;
}

.stat {
  text-align: right;
}

.stat-label {
  display: block;
  font-size: 0.75rem;
  color: #666;
  margin-bottom: 0.25rem;
}

.stat-value {
  font-size: 0.875rem;
  font-weight: 600;
  color: #1a1a1a;
}

.stat.positive .stat-value {
  color: #059669;
}

.stat.negative .stat-value {
  color: #dc2626;
}

.market-indexes {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}

.index-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  background: #f9fafb;
  border-radius: 8px;
}

.index-name {
  font-weight: 600;
  color: #1a1a1a;
}

.index-info {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.25rem;
}

.index-value {
  font-family: monospace;
  color: #1a1a1a;
  font-weight: 600;
}

.index-change {
  font-size: 0.75rem;
  font-weight: 500;
}

.index-change.positive {
  color: #059669;
}

.index-change.negative {
  color: #dc2626;
}

.empty {
  text-align: center;
  padding: 2rem;
  color: #9ca3af;
}
</style>
