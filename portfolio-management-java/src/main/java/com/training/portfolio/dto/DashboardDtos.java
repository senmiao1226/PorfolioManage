package com.training.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘相关 DTO
 */
public final class DashboardDtos {

    private DashboardDtos() {}

    /**
     * 仪表盘总览数据
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DashboardSummaryDto(
            Double totalPortfolioValue,
            Double totalCost,
            Double totalUnrealizedPnl,
            Double totalUnrealizedPnlPercent,
            Integer portfolioCount,
            Integer totalAssetCount,
            Integer stockCount,
            Integer bondCount,
            Integer cashCount,
            List<TopMoverDto> topGainers,
            List<TopMoverDto> topLosers,
            List<RecentActivityDto> recentActivities,
            Instant lastUpdated
    ) {}

    /**
     * 涨跌排行
     */
    public record TopMoverDto(
            String ticker,
            String name,
            Double currentPrice,
            Double priceChange,
            Double priceChangePercent,
            Double holdingValue,
            String assetType
    ) {}

    /**
     * 近期活动
     */
    public record RecentActivityDto(
            String type,
            String description,
            Instant timestamp,
            Double value
    ) {}

    /**
     * 资产分布
     */
    public record AssetDistributionDto(
            String assetType,
            Double value,
            Double percentage
    ) {}

    /**
     * 组合概览卡片
     */
    public record PortfolioCardDto(
            Long portfolioId,
            String name,
            String baseCurrency,
            Double totalValue,
            Double unrealizedPnl,
            Integer holdingCount,
            Instant lastUpdated
    ) {}

    /**
     * 市场概览
     */
    public record MarketOverviewDto(
            String indexName,
            Double currentValue,
            Double change,
            Double changePercent,
            String trend
    ) {}

    /**
     * 完整仪表盘数据
     */
    public record FullDashboardDto(
            DashboardSummaryDto summary,
            List<PortfolioCardDto> portfolios,
            List<AssetDistributionDto> assetDistribution,
            List<MarketOverviewDto> marketOverview,
            List<MarketDtos.MarketPriceDto> watchedStocks
    ) {}
}
