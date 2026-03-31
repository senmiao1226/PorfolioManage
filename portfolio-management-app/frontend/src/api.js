const base = "/api";

async function req(path, options = {}) {
  // 前端调试日志：查看请求详情
  console.log("\n========== [前端请求详情] ==========");
  console.log("[DEBUG] 请求路径：", path);
  console.log("[DEBUG] 完整 URL:", `${base}${path}`);
  console.log("[DEBUG] 请求方法:", options.method || "GET");
  console.log("[DEBUG] 请求头:", { "Content-Type": "application/json", ...options.headers });
  if (options.body) {
    console.log("[DEBUG] 请求体:", options.body);
    try {
      const bodyObj = JSON.parse(options.body);
      console.log("[DEBUG] 请求体 (格式化):", JSON.stringify(bodyObj, null, 2));
    } catch (e) {
      // 忽略解析错误
    }
  }
  console.log("============================================\n");
  
  const res = await fetch(`${base}${path}`, {
    headers: { "Content-Type": "application/json", ...options.headers },
    ...options,
  });
  
  if (res.status === 204) {
    console.log("[DEBUG] 响应状态：204 No Content");
    return null;
  }
  
  const text = await res.text();
  
  console.log("\n========== [前端响应详情] ==========");
  console.log("[DEBUG] 响应状态:", res.status, res.ok ? "✓" : "✗");
  console.log("[DEBUG] 响应头:", Object.fromEntries(res.headers.entries()));
  console.log("[DEBUG] 响应体原始内容:", text);
  try {
    if (text) {
      const json = JSON.parse(text);
      console.log("[DEBUG] 响应体 (格式化):", JSON.stringify(json, null, 2));
    }
  } catch (e) {
    // 忽略解析错误
  }
  console.log("============================================\n");
  
  if (!res.ok) {
    throw new Error(text || res.statusText);
  }
  return text ? JSON.parse(text) : null;
}

export const api = {
  // 原有组合管理接口
  listPortfolios: () => req("/portfolios"),
  getPortfolio: (id) => req(`/portfolios/${id}`),
  createPortfolio: (body) =>
    req("/portfolios", { method: "POST", body: JSON.stringify(body) }),
  deletePortfolio: (id) =>
    req(`/portfolios/${id}`, { method: "DELETE" }),
  getSummary: (id) => req(`/portfolios/${id}/summary`),
  addHolding: (portfolioId, body) =>
    req(`/portfolios/${portfolioId}/holdings`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
  updateHolding: (holdingId, body) =>
    req(`/portfolios/holdings/${holdingId}`, {
      method: "PATCH",
      body: JSON.stringify(body),
    }),
  deleteHolding: (holdingId) =>
    req(`/portfolios/holdings/${holdingId}`, { method: "DELETE" }),
  getPerformance: (portfolioId, days) =>
    req(`/portfolios/${portfolioId}/performance?days=${days}`),

  // Dashboard 仪表盘接口
  getDashboardSummary: () => req("/dashboard/summary"),
  getDashboardPortfolios: () => req("/dashboard/portfolios"),
  getAssetDistribution: () => req("/dashboard/asset-distribution"),
  getMarketOverview: () => req("/dashboard/market-overview"),
  getFullDashboard: () => req("/dashboard/full"),

  // Market 市场行情接口
  getMyHoldingsMarketData: (portfolioId) => req(`/market/my-holdings/${portfolioId}`),
  getAllHoldingsMarketData: () => req("/market/all-holdings"),
  getPopularStocks: () => req("/market/stocks/popular"),
  getMarketIndexes: () => req("/market/indexes"),
  searchStock: (ticker) => req(`/market/search?ticker=${ticker}`),
  getAssetDetail: (ticker, portfolioId) => 
    req(`/market/asset/${ticker}${portfolioId ? `?portfolioId=${portfolioId}` : ""}`),
  getTopGainers: (portfolioId, topN = 5) => req(`/market/movers/gainers/${portfolioId}?topN=${topN}`),
  getTopLosers: (portfolioId, topN = 5) => req(`/market/movers/losers/${portfolioId}?topN=${topN}`),
  getDataProviders: () => req("/market/providers"),

  // Analytics 分析接口
  getAssetAllocation: (portfolioId) => req(`/analytics/allocation/${portfolioId}`),
  getGlobalAssetAllocation: () => req("/analytics/allocation/global"),
  getPortfolioHistory: (portfolioId, days = 30) => req(`/analytics/history/${portfolioId}?days=${days}`),
  getGlobalHistory: (days = 30) => req(`/analytics/history/global?days=${days}`),
  getPerformanceStats: (portfolioId) => req(`/analytics/performance/${portfolioId}`),
  getGlobalPerformanceStats: () => req("/analytics/performance/global"),
  getTopHoldings: (portfolioId, topN = 10) => req(`/analytics/top-holdings/${portfolioId}?topN=${topN}`),
};
