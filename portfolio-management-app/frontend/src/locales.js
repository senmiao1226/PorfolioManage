// 语言包配置
export const messages = {
  zh: {
    // 导航
    nav: {
      dashboard: '仪表盘',
      portfolio: '投资组合',
      market: '市场行情',
      analytics: '投资分析'
    },
    
    // 通用
    common: {
      title: '投资组合管理系统',
      subtitle: '专业的投资组合管理工具',
      loading: '加载中...',
      retry: '重试',
      error: '错误',
      success: '成功',
      cancel: '取消',
      confirm: '确认',
      delete: '删除',
      edit: '编辑',
      save: '保存',
      create: '创建',
      search: '搜索',
      refresh: '刷新',
      optional: '可选'
    },
    
    // Dashboard
    dashboard: {
      title: '仪表盘',
      subtitle: '投资组合总览',
      totalValue: '总资产',
      totalCost: '总成本',
      unrealizedPnl: '未实现盈亏',
      portfolioCount: '组合数量',
      assetDistribution: '资产分布',
      myPortfolios: '我的组合',
      marketOverview: '市场概览',
      marketValue: '市值',
      profitLoss: '盈亏',
      holdings: '持仓',
      noData: '暂无数据',
      noPortfolios: '暂无组合，请先创建',
      noMarketData: '暂无市场数据',
      noAssetDistribution: '暂无资产分布数据'
    },
    
    // Portfolio
    portfolio: {
      title: '投资组合',
      subtitle: '管理您的投资组合和持仓',
      myPortfolios: '我的组合',
      newPortfolio: '+ 新建组合',
      portfolioDetail: '组合详情',
      addHolding: '+ 添加持仓',
      totalCost: '总成本',
      totalMarketValue: '总市值',
      unrealizedPnl: '未实现盈亏',
      assetAllocation: '资产分布',
      holdingsDetail: '持仓明细',
      assetType: '类型',
      ticker: '标的',
      quantity: '数量',
      costPrice: '成本价',
      marketPrice: '市价',
      marketValue: '市值',
      profitLoss: '盈亏',
      action: '操作',
      selectPortfolio: '请选择一个组合查看详情',
      createNew: '新建组合',
      name: '名称',
      description: '描述',
      baseCurrency: '本币',
      addHoldingTitle: '添加持仓',
      editHoldingTitle: '编辑持仓',
      purchaseDate: '购入日期',
      averageCost: '平均成本',
      autoFill: '将自动填充购入日期收盘价',
      refresh: '刷新',
      edit: '编辑',
      delete: '删除',
      noHoldings: '暂无持仓',
      noPortfolios: '暂无组合，点击上方按钮创建',
      confirmDelete: '确定删除该组合？',
      confirmDeleteHolding: '确定删除该持仓？',
      placeholder: {
        portfolioName: '组合名称',
        description: '描述（可选）',
        ticker: '股票代码',
        name: '名称'
      }
    },
    
    // Market
    market: {
      title: '市场行情',
      subtitle: '实时股票行情与持仓监控',
      selectPortfolio: '选择组合',
      allHoldings: '全部持仓',
      myHoldings: '我的持仓',
      updateTime: '更新时间',
      gainers: '📈 涨幅榜',
      losers: '📉 跌幅榜',
      popularStocks: '🔥 热门股票',
      searchStock: '🔍 股票搜索',
      searchPlaceholder: '输入股票代码 (如：AAPL)',
      searching: '搜索中',
      available: '✓ 可交易',
      unavailable: '✗ 暂不可用',
      holding: '持仓',
      position: '市值',
      source: '数据来源',
      detail: '详情',
      previousClose: '昨收',
      priceChange: '涨跌',
      myPosition: '我的持仓',
      recentTrend: '近期走势',
      noHoldingsData: '暂无持仓数据',
      loading: '加载中...',
      ticker: '代码',
      name: '名称',
      price: '价格',
      stockDetail: '详情',
      shares: '股',
      loadFailed: '加载失败'
    },
    
    // Analytics
    analytics: {
      title: '投资分析',
      subtitle: '深度分析投资组合表现',
      selectPortfolio: '选择组合',
      globalSummary: '全局汇总',
      performanceStats: '业绩统计',
      assetAllocation: '资产配置',
      historicalTrend: '历史走势',
      topHoldings: '前 10 大持仓',
      totalCost: '总成本',
      totalMarketValue: '总市值',
      unrealizedPnl: '未实现盈亏',
      holdingCount: '持仓数量',
      marketValue: '市值',
      percentage: '占比',
      highest: '最高',
      lowest: '最低',
      average: '平均',
      days: '天',
      performanceStats: '业绩统计',
      assetAllocation: '资产配置',
      historicalTrend: '历史走势',
      topHoldings: '前 10 大持仓',
      totalCost: '总成本',
      totalMarketValue: '总市值',
      unrealizedPnl: '未实现盈亏',
      holdingCount: '持仓数量',
      marketValue: '市值',
      percentage: '占比',
      highest: '最高',
      lowest: '最低',
      average: '平均',
      days: '天',
      noPerformanceData: '暂无业绩数据',
      noAllocationData: '暂无资产配置数据',
      noHistoricalData: '暂无历史数据',
      noHoldingsData: '暂无持仓数据'
    },
    
    // Asset Types
    assetType: {
      stock: '股票',
      bond: '债券',
      fund: '基金',
      cash: '现金'
    },
    
    // Validations & Messages
    messages: {
      confirmDeletePortfolio: '确定删除该组合？',
      confirmDeleteHolding: '确定删除该持仓？',
      createFailed: '创建失败',
      deleteFailed: '删除失败',
      saveFailed: '保存失败',
      quantityMustBePositive: '数量必须大于0，请重新输入',
      tickerRequired: '请输入股票代码',
      invalidTicker: '股票代码无效或无法获取价格，请重新输入'
    }
  },
  
  en: {
    // 导航
    nav: {
      dashboard: 'Dashboard',
      portfolio: 'Portfolio',
      market: 'Market',
      analytics: 'Analytics'
    },
    
    // Common
    common: {
      title: 'Portfolio Management System',
      subtitle: 'Professional Portfolio Management Tool',
      loading: 'Loading...',
      retry: 'Retry',
      error: 'Error',
      success: 'Success',
      cancel: 'Cancel',
      confirm: 'Confirm',
      delete: 'Delete',
      edit: 'Edit',
      save: 'Save',
      create: 'Create',
      search: 'Search',
      refresh: 'Refresh',
      optional: 'Optional'
    },
    
    // Dashboard
    dashboard: {
      title: 'Dashboard',
      subtitle: 'Portfolio Overview',
      totalValue: 'Total Assets',
      totalCost: 'Total Cost',
      unrealizedPnl: 'Unrealized P/L',
      portfolioCount: 'Portfolios',
      assetDistribution: 'Asset Distribution',
      myPortfolios: 'My Portfolios',
      marketOverview: 'Market Overview',
      marketValue: 'Market Value',
      profitLoss: 'Profit/Loss',
      holdings: 'Holdings',
      noData: 'No Data',
      noPortfolios: 'No portfolios yet, please create one',
      noMarketData: 'No market data available',
      noAssetDistribution: 'No asset distribution data'
    },
    
    // Portfolio
    portfolio: {
      title: 'Portfolio',
      subtitle: 'Manage your investment portfolios and holdings',
      myPortfolios: 'My Portfolios',
      newPortfolio: '+ New Portfolio',
      portfolioDetail: 'Portfolio Details',
      addHolding: '+ Add Holding',
      totalCost: 'Total Cost',
      totalMarketValue: 'Total Market Value',
      unrealizedPnl: 'Unrealized P/L',
      assetAllocation: 'Asset Allocation',
      holdingsDetail: 'Holdings Details',
      assetType: 'Type',
      ticker: 'Ticker',
      quantity: 'Quantity',
      costPrice: 'Cost Price',
      marketPrice: 'Market Price',
      marketValue: 'Market Value',
      profitLoss: 'Profit/Loss',
      action: 'Action',
      selectPortfolio: 'Select a portfolio to view details',
      createNew: 'Create New Portfolio',
      name: 'Name',
      description: 'Description',
      baseCurrency: 'Base Currency',
      addHoldingTitle: 'Add Holding',
      editHoldingTitle: 'Edit Holding',
      purchaseDate: 'Purchase Date',
      averageCost: 'Average Cost',
      autoFill: 'Will auto-fill with closing price on purchase date',
      refresh: 'Refresh',
      edit: 'Edit',
      delete: 'Delete',
      placeholder: {
        ticker: 'Ticker (e.g. AAPL)',
        name: 'Asset name'
      }
    },
    
    // Market
    market: {
      title: 'Market',
      subtitle: 'Real-time stock quotes and holdings monitoring',
      selectPortfolio: 'Select Portfolio',
      allHoldings: 'All Holdings',
      myHoldings: 'My Holdings',
      updateTime: 'Updated At',
      gainers: '📈 Top Gainers',
      losers: '📉 Top Losers',
      popularStocks: '🔥 Popular Stocks',
      searchStock: '🔍 Search Stock',
      searchPlaceholder: 'Enter stock ticker (e.g., AAPL)',
      searching: 'Searching',
      available: '✓ Available',
      unavailable: '✗ Unavailable',
      holding: 'Holding',
      position: 'Value',
      source: 'Source',
      detail: 'Details',
      previousClose: 'Prev Close',
      priceChange: 'Change',
      myPosition: 'My Position',
      recentTrend: 'Recent Trend',
      noHoldingsData: 'No holdings data',
      loading: 'Loading...',
      ticker: 'Ticker',
      name: 'Name',
      price: 'Price',
      stockDetail: 'Details',
      shares: 'shares',
      loadFailed: 'Load failed'
    },
    
    // Analytics
    analytics: {
      title: 'Analytics',
      subtitle: 'In-depth portfolio performance analysis',
      selectPortfolio: 'Select Portfolio',
      globalSummary: 'Global Summary',
      performanceStats: 'Performance Stats',
      assetAllocation: 'Asset Allocation',
      historicalTrend: 'Historical Trend',
      topHoldings: 'Top 10 Holdings',
      totalCost: 'Total Cost',
      totalMarketValue: 'Total Market Value',
      unrealizedPnl: 'Unrealized P/L',
      holdingCount: 'Holdings Count',
      marketValue: 'Market Value',
      percentage: 'Percentage',
      highest: 'Highest',
      lowest: 'Lowest',
      average: 'Average',
      days: 'Days',
      performanceStats: 'Performance Stats',
      assetAllocation: 'Asset Allocation',
      historicalTrend: 'Historical Trend',
      topHoldings: 'Top 10 Holdings',
      totalCost: 'Total Cost',
      totalMarketValue: 'Total Market Value',
      unrealizedPnl: 'Unrealized P/L',
      holdingCount: 'Holdings Count',
      marketValue: 'Market Value',
      percentage: 'Percentage',
      highest: 'Highest',
      lowest: 'Lowest',
      average: 'Average',
      days: 'Days',
      noPerformanceData: 'No performance data',
      noAllocationData: 'No asset allocation data',
      noHistoricalData: 'No historical data',
      noHoldingsData: 'No holdings data',
      loading: 'Loading...',
      ticker: 'Ticker',
      name: 'Name',
      price: 'Price'
    },
    
    // Asset Types
    assetType: {
      stock: 'Stock',
      bond: 'Bond',
      fund: 'Fund',
      cash: 'Cash'
    },
    
    // Validations & Messages
    messages: {
      confirmDeletePortfolio: 'Delete this portfolio?',
      confirmDeleteHolding: 'Delete this holding?',
      createFailed: 'Create failed',
      deleteFailed: 'Delete failed',
      saveFailed: 'Save failed',
      quantityMustBePositive: 'Quantity must be greater than 0',
      tickerRequired: 'Please enter stock ticker',
      invalidTicker: 'Invalid ticker or unable to get price'
    }
  }
};

// 当前语言 - 使用 ref 使其响应式
import { ref } from 'vue';
export const currentLang = ref(localStorage.getItem('portfolio_lang') || 'zh');

// 获取翻译文本
export function t(key) {
  const keys = key.split('.');
  let value = messages[currentLang.value];
  for (const k of keys) {
    if (value && typeof value === 'object' && k in value) {
      value = value[k];
    } else {
      return key;
    }
  }
  return value || key;
}

// 设置语言 - 直接修改 ref，Vue 会自动触发重新渲染
export function setLang(lang) {
  if (messages[lang]) {
    currentLang.value = lang;
    localStorage.setItem('portfolio_lang', lang);
  }
}

// 初始化语言
export function initLang() {
  return currentLang;
}
