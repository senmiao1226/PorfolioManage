package com.training.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * 市场行情相关 DTO
 */
public final class MarketDtos {

    private MarketDtos() {}

    /**
     * 市场价格基础信息
     */
    public record MarketPriceDto(
            String ticker,
            String name,
            Double currentPrice,
            String priceSource,
            Instant updatedAt
    ) {}

    /**
     * 带持仓信息的市场价格
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MarketPriceWithHoldingDto(
            String ticker,
            String name,
            Double currentPrice,
            Double priceChange,
            Double priceChangePercent,
            Double holdingQuantity,
            Double holdingValue,
            Boolean isInPortfolio,
            Boolean isWatched
    ) {}

    /**
     * 市场详情（个股详细信息）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MarketDetailDto(
            String ticker,
            String name,
            Double currentPrice,
            Double previousClose,
            Double priceChange,
            Double priceChangePercent,
            Double dayHigh,
            Double dayLow,
            Long volume,
            Double marketCap,
            String currency,
            List<PricePointDto> priceHistory,
            Boolean isInPortfolio,
            Double holdingQuantity
    ) {}

    /**
     * 价格历史点
     */
    public record PricePointDto(
            String date,
            Double price
    ) {}

    /**
     * 市场热门股票 - 基于持仓盈亏的涨跌排行
     */
    public record MarketMoverDto(
            String ticker,
            String name,
            Double currentPrice,
            Double priceChange,        // 当日价格变动
            Double priceChangePercent, // 当日涨跌幅
            String trend,              // "UP", "DOWN", "FLAT"
            Double unrealizedPnl,      // 未实现盈亏（基于成本价）
            Double unrealizedPnlPercent, // 未实现盈亏百分比
            Double totalCost,          // 总成本
            Double totalValue,         // 总市值
            Double holdingQuantity     // 持仓数量
    ) {}

    /**
     * 涨跌榜一次返回（避免重复为每只股票拉两次价）
     */
    public record MarketMoversBundleDto(
            List<MarketMoverDto> gainers,
            List<MarketMoverDto> losers
    ) {}

    /**
     * 市场分类响应
     */
    public record MarketCategoryDto(
            String category,
            List<MarketPriceDto> stocks
    ) {}

    /**
     * 数据源提供商信息
     */
    public record DataProviderDto(
            String name,
            String description,
            Boolean isActive,
            String priority
    ) {}

    /**
     * 关注列表请求
     */
    public record WatchlistRequest(String ticker) {}

    /**
     * 搜索股票响应
     */
    public record StockSearchResultDto(
            String ticker,
            String name,
            Double currentPrice,
            Boolean isAvailable
    ) {}
}
