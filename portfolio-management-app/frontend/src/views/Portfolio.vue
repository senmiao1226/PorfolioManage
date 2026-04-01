<template>
  <div class="portfolio-page">
    <header class="page-header">
      <h1>投资组合</h1>
      <p class="subtitle">管理您的投资组合和持仓</p>
    </header>

    <div class="portfolio-content">
      <!-- 组合列表 -->
      <section class="card">
        <div class="card-header">
          <h2>我的组合</h2>
          <button class="btn-primary" @click="showCreateModal = true">
            + 新建组合
          </button>
        </div>
        
        <div v-if="loading" class="loading">加载中...</div>
        
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
        
        <p v-else class="empty">暂无组合，点击上方按钮创建</p>
      </section>

      <!-- 组合详情 -->
      <section v-if="selectedPortfolio" class="card detail-card">
        <div class="card-header">
          <h2>{{ selectedPortfolio.name }}</h2>
          <div class="header-actions">
            <button class="btn-secondary" @click="refreshData">刷新</button>
            <button class="btn-primary" @click="showAddHolding = true">+ 添加持仓</button>
          </div>
        </div>

        <!-- 汇总信息 -->
        <div v-if="summary" class="summary-section">
          <div class="summary-grid">
            <div class="summary-item">
              <span class="label">总成本</span>
              <span class="value">{{ fmtMoney(summary.totalCost) }}</span>
            </div>
            <div class="summary-item">
              <span class="label">总市值</span>
              <span class="value">{{ fmtMoney(summary.totalMarketValue) }}</span>
            </div>
            <div class="summary-item" :class="{ 'positive': summary.unrealizedPnl >= 0, 'negative': summary.unrealizedPnl < 0 }">
              <span class="label">未实现盈亏</span>
              <span class="value">{{ fmtMoney(summary.unrealizedPnl) }}</span>
            </div>
          </div>

          <!-- 资产分布 -->
          <div class="allocation-section">
            <h3>资产分布</h3>
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
          <h3>持仓明细</h3>
          <table v-if="summary?.holdings?.length" class="data-table">
            <thead>
              <tr>
                <th>类型</th>
                <th>标的</th>
                <th>数量</th>
                <th>成本价</th>
                <th>市价</th>
                <th>市值</th>
                <th>盈亏</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="h in summary.holdings" :key="h.holdingId">
                <td>{{ formatAssetType(h.assetType) }}</td>
                <td>{{ h.ticker || '—' }}</td>
                <td>{{ h.quantity }}</td>
                <td>{{ fmtMoney(h.costBasis / h.quantity) }}</td>
                <td>{{ h.marketPrice ? fmtMoney(h.marketPrice) : '—' }}</td>
                <td>{{ h.marketValue ? fmtMoney(h.marketValue) : '—' }}</td>
                <td :class="{ 'positive': (h.unrealizedPnl || 0) >= 0, 'negative': (h.unrealizedPnl || 0) < 0 }">
                  {{ h.unrealizedPnl != null ? fmtMoney(h.unrealizedPnl) : '—' }}
                </td>
                <td>
                  <button class="btn-small" @click="editHolding(h)">编辑</button>
                  <button class="btn-small btn-danger" @click="deleteHolding(h.holdingId)">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
          <p v-else class="empty">暂无持仓</p>
        </div>
      </section>

      <section v-else class="card empty-card">
        <p>请选择一个组合查看详情</p>
      </section>
    </div>

    <!-- 新建组合弹窗 -->
    <div v-if="showCreateModal" class="modal-backdrop" @click.self="showCreateModal = false">
      <div class="modal">
        <h3>新建组合</h3>
        <div class="form-group">
          <label>名称</label>
          <input v-model="newPortfolio.name" placeholder="组合名称" />
        </div>
        <div class="form-group">
          <label>描述</label>
          <input v-model="newPortfolio.description" placeholder="描述（可选）" />
        </div>
        <div class="form-group">
          <label>本币</label>
          <select v-model="newPortfolio.baseCurrency">
            <option value="USD">USD</option>
            <option value="CNY">CNY</option>
            <option value="EUR">EUR</option>
          </select>
        </div>
        <div class="modal-actions">
          <button class="btn-primary" @click="createPortfolio">创建</button>
          <button class="btn-secondary" @click="showCreateModal = false">取消</button>
        </div>
      </div>
    </div>

    <!-- 添加/编辑持仓弹窗 -->
    <div v-if="showAddHolding" class="modal-backdrop" @click.self="closeHoldingModal">
      <div class="modal">
        <h3>{{ editingHolding ? '编辑持仓' : '添加持仓' }}</h3>
        <div class="form-group">
          <label>资产类型</label>
          <select v-model="holdingForm.assetType">
            <option value="stock">股票</option>
            <option value="bond">债券</option>
            <option value="cash">现金</option>
          </select>
        </div>
        <div class="form-group" v-if="holdingForm.assetType !== 'cash'">
          <label>股票代码</label>
          <input v-model="holdingForm.ticker" @change="onTickerInput" placeholder="eg:AAPL" />
        </div>
        <div class="form-group">
          <label>名称（可选）</label>
          <input v-model="holdingForm.name" placeholder="名称" />
        </div>
        <div class="form-group">
          <label>数量</label>
          <input v-model.number="holdingForm.quantity" type="number" step="0.01" />
        </div>
        <div class="form-group">
          <label>平均成本</label>
          <input v-model.number="holdingForm.averageCost" type="number" step="0.01" />
        </div>
        <div class="modal-actions">
          <button class="btn-primary" @click="saveHolding">保存</button>
          <button class="btn-secondary" @click="closeHoldingModal">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { api } from '../api';

const route = useRoute();

const loading = ref(false);
const portfolios = ref([]);
const selectedPortfolio = ref(null);
const summary = ref(null);

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
  averageCost: 0
});

function fmtMoney(v) {
  if (v == null || Number.isNaN(Number(v))) return '—';
  return new Intl.NumberFormat('zh-CN', { 
    style: 'currency', 
    currency: selectedPortfolio.value?.baseCurrency || 'USD',
    maximumFractionDigits: 2 
  }).format(Number(v));
}

function formatAssetType(type) {
  const map = { 'stock': '股票', 'bond': '债券', 'cash': '现金' };
  return map[type] || type;
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
    alert('创建失败: ' + e.message);
  }
}

async function deletePortfolio(id) {
  if (!confirm('确定删除该组合？')) return;
  try {
    await api.deletePortfolio(id);
    if (selectedPortfolio.value?.id === id) {
      selectedPortfolio.value = null;
      summary.value = null;
    }
    await loadPortfolios();
  } catch (e) {
    alert('删除失败: ' + e.message);
  }
}

function editHolding(h) {
  editingHolding.value = h;
  holdingForm.value = {
    assetType: h.assetType,
    ticker: h.ticker || '',
    name: '',
    quantity: h.quantity,
    averageCost: h.costBasis / h.quantity
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
    averageCost: 0
  };
}

async function saveHolding() {
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
    alert('保存失败：' + e.message);
  }
}

// 当用户输入股票代码时，自动获取价格
let tickerDebounceTimer = null;

async function onTickerInput() {
  const ticker = holdingForm.value.ticker;
  if (!ticker || holdingForm.value.assetType === 'cash' || editingHolding.value) {
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
        console.log('自动填充价格:', detail.currentPrice);
      }
    } catch (e) {
      console.log('获取股票价格失败:', e.message);
    }
  }, 500); // 500ms 防抖
}

async function deleteHolding(id) {
  if (!confirm('确定删除该持仓？')) return;
  try {
    await api.deleteHolding(id);
    await refreshData();
  } catch (e) {
    alert('删除失败: ' + e.message);
  }
}

onMounted(loadPortfolios);
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
</style>
