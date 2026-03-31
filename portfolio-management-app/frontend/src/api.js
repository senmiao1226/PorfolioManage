const base = "/api";

async function req(path, options = {}) {
  const res = await fetch(`${base}${path}`, {
    headers: { "Content-Type": "application/json", ...options.headers },
    ...options,
  });
  if (res.status === 204) return null;
  const text = await res.text();
  if (!res.ok) {
    throw new Error(text || res.statusText);
  }
  return text ? JSON.parse(text) : null;
}

export const api = {
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
};
