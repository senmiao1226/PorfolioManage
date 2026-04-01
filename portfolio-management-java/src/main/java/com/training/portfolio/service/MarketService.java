package com.training.portfolio.service;

import com.training.portfolio.domain.AssetType;
import com.training.portfolio.domain.Holding;
import com.training.portfolio.dto.MarketDtos;
import com.training.portfolio.dto.MarketDtos.*;
import com.training.portfolio.repo.HoldingRepository;
import com.training.portfolio.repo.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 市场行情服务
 * 提供股票行情查询、持仓行情聚合、热门股票等功能
 */
@Service
@RequiredArgsConstructor
public class MarketService {

    private final PricingService pricingService;
    private final HoldingRepository holdingRepository;
    private final PortfolioRepository portfolioRepository;

    // 预定义热门美股列表
    private static final List<String> POPULAR_US_STOCKS = List.of(
            "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "NVDA", "META",
            "NFLX", "AMD", "INTC", "CRM", "ADBE", "PYPL", "UBER",
            "BABA", "JD", "PDD", "NIO", "LI", "XPEV"
    );

    // 预定义指数列表
    private static final List<String> MARKET_INDEXES = List.of(
            "SPY", "QQQ", "DIA", "IWM"
    );

    /**
     * 获取指定组合的持仓股票行情
     *
     * @param portfolioId 组合ID
     * @return 带持仓信息的市场价格列表
     */
    public List<MarketPriceWithHoldingDto> getHoldingsMarketData(Long portfolioId) {
        System.out.println("\n[MarketService.getHoldingsMarketData] 开始 | portfolioId=" + portfolioId);
        
        // 1. 查询该组合的所有持仓
        List<Holding> holdings = holdingRepository.findByPortfolioIdOrderById(portfolioId);
        System.out.println("[DEBUG] 从数据库获取持仓记录数：" + holdings.size());

        // 2. 按股票代码分组聚合（同一股票可能有多条记录）
        Map<String, List<Holding>> holdingsByTicker = holdings.stream()
                .filter(h -> h.getAssetType() != AssetType.cash)
                .filter(h -> h.getTicker() != null && !h.getTicker().isBlank())
                .collect(Collectors.groupingBy(h -> h.getTicker().trim().toUpperCase()));
        
        System.out.println("[DEBUG] 去重后的股票数量：" + holdingsByTicker.size());
        System.out.println("[DEBUG] 股票列表：" + holdingsByTicker.keySet());

        // 3. 为每只股票构建市场数据
        List<MarketPriceWithHoldingDto> result = holdingsByTicker.entrySet().stream()
                .map(entry -> buildMarketDataWithHolding(entry.getKey(), entry.getValue()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        System.out.println("[MarketService.getHoldingsMarketData] 完成 | 返回记录数=" + result.size());
        return result;
    }

    /**
     * 获取所有组合的持仓股票行情（去重）
     *
     * @return 全局持仓行情列表
     */
    public List<MarketPriceWithHoldingDto> getAllHoldingsMarketData() {
        // 获取所有持仓
        List<Holding> allHoldings = holdingRepository.findAll();

        // 全局按股票代码分组
        Map<String, List<Holding>> globalHoldings = allHoldings.stream()
                .filter(h -> h.getAssetType() != AssetType.cash)
                .filter(h -> h.getTicker() != null && !h.getTicker().isBlank())
                .collect(Collectors.groupingBy(h -> h.getTicker().trim().toUpperCase()));

        return globalHoldings.entrySet().stream()
                .map(entry -> buildMarketDataWithHolding(entry.getKey(), entry.getValue()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 获取热门股票行情
     *
     * @return 热门股票价格列表
     */
    public List<MarketPriceDto> getPopularStocks() {
        return POPULAR_US_STOCKS.stream()
                .map(this::getStockPrice)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 获取市场指数行情
     *
     * @return 指数价格列表
     */
    public List<MarketPriceDto> getMarketIndexes() {
        return MARKET_INDEXES.stream()
                .map(this::getStockPrice)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 搜索股票（实时查询）
     *
     * @param ticker 股票代码
     * @return 搜索结果
     */
    public StockSearchResultDto searchStock(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return null;
        }

        String normalizedTicker = ticker.trim().toUpperCase();
        Optional<Double> priceOpt = pricingService.priceForHolding(AssetType.stock, normalizedTicker);

        return new StockSearchResultDto(
                normalizedTicker,
                normalizedTicker, // 名称需要额外查询，暂用代码代替
                priceOpt.orElse(null),
                priceOpt.isPresent()
        );
    }

    /**
     * 获取个股详细信息
     *
     * @param ticker 股票代码
     * @param portfolioId 可选的组合ID（用于查询持仓信息）
     * @return 个股详情
     */
    public MarketDetailDto getAssetDetail(String ticker, Long portfolioId) {
        System.out.println("\n[MarketService.getAssetDetail] 开始 | ticker=" + ticker + ", portfolioId=" + portfolioId);
        
        if (ticker == null || ticker.isBlank()) {
            System.out.println("[DEBUG] ticker为空，返回null");
            return null;
        }

        String normalizedTicker = ticker.trim().toUpperCase();

        // 获取当前价格和前收盘价（复用PricingService的缓存）
        System.out.println("[DEBUG] 调用PricingService获取价格...");
        Optional<Double> priceOpt = pricingService.priceForHolding(AssetType.stock, normalizedTicker);
        Optional<Double> prevCloseOpt = pricingService.fetchPreviousClose(AssetType.stock, normalizedTicker);
        
        if (priceOpt.isEmpty()) {
            System.out.println("[DEBUG] 未获取到价格，返回null");
            return null;
        }

        Double currentPrice = priceOpt.get();
        Double previousClose = prevCloseOpt.orElse(currentPrice);

        Double priceChange = currentPrice - previousClose;
        Double priceChangePercent = previousClose != 0
                ? (priceChange / previousClose) * 100
                : 0.0;

        // 获取价格历史（复用已有方法）
        List<PricePointDto> priceHistory = fetchPriceHistory(normalizedTicker, 30);

        // 查询持仓信息
        Double holdingQuantity = null;
        Boolean isInPortfolio = false;
        if (portfolioId != null) {
            List<Holding> holdings = holdingRepository.findByPortfolioIdOrderById(portfolioId);
            holdingQuantity = holdings.stream()
                    .filter(h -> normalizedTicker.equalsIgnoreCase(h.getTicker()))
                    .mapToDouble(Holding::getQuantity)
                    .sum();
            isInPortfolio = holdingQuantity > 0;
        }

        return new MarketDetailDto(
                normalizedTicker,
                normalizedTicker,
                currentPrice,
                previousClose,
                round2(priceChange),
                round2(priceChangePercent),
                null, // dayHigh - 需要额外API
                null, // dayLow - 需要额外API
                null, // volume - 需要额外API
                null, // marketCap - 需要额外API
                "USD",
                priceHistory,
                isInPortfolio,
                holdingQuantity
        );
    }

    /**
     * 获取涨跌排行（基于持仓）
     *
     * @param portfolioId 组合ID
     * @param topN 取前N个
     * @param gainers true-涨幅榜, false-跌幅榜
     * @return 排行列表
     */
    public List<MarketDtos.MarketMoverDto> getMarketMovers(Long portfolioId, int topN, boolean gainers) {
        List<MarketPriceWithHoldingDto> holdings = getHoldingsMarketData(portfolioId);

        return holdings.stream()
                .filter(h -> h.currentPrice() != null && h.priceChangePercent() != null)
                .sorted((a, b) -> {
                    if (gainers) {
                        return Double.compare(b.priceChangePercent(), a.priceChangePercent());
                    } else {
                        return Double.compare(a.priceChangePercent(), b.priceChangePercent());
                    }
                })
                .limit(topN)
                .map(h -> new MarketDtos.MarketMoverDto(
                        h.ticker(),
                        h.name(),
                        h.currentPrice(),
                        h.priceChange(),
                        h.priceChangePercent(),
                        h.priceChangePercent() >= 0 ? "UP" : "DOWN"
                ))
                .collect(Collectors.toList());
    }

    /**
     * 获取数据源提供商列表
     *
     * @return 提供商列表
     */
    public List<DataProviderDto> getDataProviders() {
        return List.of(
                new DataProviderDto("Cached API", "课程提供的缓存价格数据", true, "1"),
                new DataProviderDto("Sina Finance", "新浪财经 - A股/港股", true, "2"),
                new DataProviderDto("Alpha Vantage", "Alpha Vantage API", false, "3"),
                new DataProviderDto("Massive", "Massive.com API", false, "4")
        );
    }

    // ========== 私有辅助方法 ==========

    private MarketPriceWithHoldingDto buildMarketDataWithHolding(String ticker, List<Holding> holdings) {
        System.out.println("[MarketService.buildMarketDataWithHolding] 处理 | ticker=" + ticker + ", 持仓记录数=" + holdings.size());
        
        if (holdings.isEmpty()) {
            return null;
        }

        // 汇总持仓数量
        double totalQuantity = holdings.stream()
                .mapToDouble(Holding::getQuantity)
                .sum();

        // 获取股票名称（取第一个持仓的名称）
        String name = holdings.stream()
                .map(Holding::getName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(ticker);

        // 查询当前价格和前收盘价（复用PricingService缓存）
        System.out.println("[DEBUG] 调用PricingService.priceForHolding获取价格...");
        Optional<Double> priceOpt = pricingService.priceForHolding(AssetType.stock, ticker);
        System.out.println("[DEBUG] 调用PricingService.fetchPreviousClose获取前收盘价...");
        Optional<Double> prevCloseOpt = pricingService.fetchPreviousClose(AssetType.stock, ticker);

        if (priceOpt.isEmpty()) {
            System.out.println("[DEBUG] 未获取到价格，返回无价格持仓信息");
            // 价格获取失败，仍返回持仓信息
            return new MarketPriceWithHoldingDto(
                    ticker,
                    name,
                    null,
                    null,
                    null,
                    totalQuantity,
                    null,
                    true,
                    false
            );
        }

        Double currentPrice = priceOpt.get();
        Double marketValue = currentPrice * totalQuantity;

        // 计算涨跌（复用前收盘价）
        Double priceChange = 0.0;
        Double priceChangePercent = 0.0;
        if (prevCloseOpt.isPresent() && prevCloseOpt.get() > 0) {
            Double previousClose = prevCloseOpt.get();
            priceChange = currentPrice - previousClose;
            priceChangePercent = (priceChange / previousClose) * 100;
            System.out.println("[DEBUG] 计算涨跌幅 | 当前价=" + currentPrice + ", 前收=" + previousClose + ", 涨跌=" + round2(priceChangePercent) + "%");
        } else {
            System.out.println("[DEBUG] 未获取到前收盘价，涨跌幅设为0");
        }

        return new MarketPriceWithHoldingDto(
                ticker,
                name,
                currentPrice,
                round2(priceChange),
                round2(priceChangePercent),
                totalQuantity,
                round2(marketValue),
                true,
                false
        );
    }

    private MarketPriceDto getStockPrice(String ticker) {
        Optional<Double> priceOpt = pricingService.priceForHolding(AssetType.stock, ticker);

        return priceOpt.map(price -> new MarketPriceDto(
                ticker,
                ticker,
                price,
                "PricingService",
                Instant.now()
        )).orElse(null);
    }

    private static double round2(Double v) {
        if (v == null) return 0.0;
        return Math.round(v * 100.0) / 100.0;
    }

    /**
     * 获取股票前收盘价（供Controller复用）
     */
    public Optional<Double> getPreviousClose(String ticker) {
        return pricingService.fetchPreviousClose(AssetType.stock, ticker);
    }

    /**
     * 获取股票价格历史（复用PricingService的缓存数据）
     */
    private List<PricePointDto> fetchPriceHistory(String ticker, int days) {
        try {
            List<PricingService.TimePrice> series = pricingService.fetchCachedDailyCloseSeries(ticker, days);
            return series.stream()
                    .map(tp -> new PricePointDto(
                            tp.day().toString(),
                            tp.price()
                    ))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
