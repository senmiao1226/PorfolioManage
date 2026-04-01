package com.training.portfolio.web;

import com.training.portfolio.dto.DashboardDtos;
import com.training.portfolio.dto.MarketDtos;
import com.training.portfolio.dto.PortfolioDtos;
import com.training.portfolio.service.MarketService;
import com.training.portfolio.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 仪表盘控制器
 * 提供系统总览、关键指标等接口
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DashboardController {

    private final PortfolioService portfolioService;
    private final MarketService marketService;

    /**
     * 获取仪表盘总览数据
     *
     * @return 仪表盘摘要
     */
    @GetMapping("/summary")
    public ResponseEntity<DashboardDtos.DashboardSummaryDto> getDashboardSummary() {
        // 获取所有组合
        List<PortfolioDtos.PortfolioResponse> portfolios = portfolioService.listPortfolios();

        double totalValue = 0.0;
        double totalCost = 0.0;
        double totalUnrealizedPnl = 0.0;
        int totalAssetCount = 0;
        int stockCount = 0;
        int bondCount = 0;
        int cashCount = 0;

        List<DashboardDtos.TopMoverDto> allHoldings = new ArrayList<>();

        // 汇总所有组合数据
        for (PortfolioDtos.PortfolioResponse portfolio : portfolios) {
            try {
                PortfolioDtos.PortfolioSummaryResponse summary = 
                        portfolioService.getSummary(portfolio.id());

                totalValue += summary.totalMarketValue();
                totalCost += summary.totalCost();
                totalUnrealizedPnl += summary.unrealizedPnl();
                totalAssetCount += summary.holdings().size();

                // 统计资产类型
                for (PortfolioDtos.HoldingValuation holding : summary.holdings()) {
                    switch (holding.assetType()) {
                        case "stock" -> stockCount++;
                        case "bond" -> bondCount++;
                        case "cash" -> cashCount++;
                    }

                    // 收集用于计算涨跌的数据
                    if (holding.marketPrice() != null) {
                        allHoldings.add(new DashboardDtos.TopMoverDto(
                                holding.ticker(),
                                holding.ticker(),
                                holding.marketPrice(),
                                null,  // priceChange - 需要额外计算
                                null,  // priceChangePercent - 需要额外计算
                                holding.marketValue(),
                                holding.assetType()
                        ));
                    }
                }
            } catch (Exception e) {
                // 跳过无法获取数据的组合
            }
        }

        // 计算总收益率
        double totalReturnPercent = totalCost > 0 ? (totalUnrealizedPnl / totalCost) * 100 : 0;

        // 计算涨跌排行（简化版，实际应该基于价格变动）
        // 注意：由于TopMoverDto没有priceChangePercent字段，暂时使用holdingValue排序
        List<DashboardDtos.TopMoverDto> topGainers = allHoldings.stream()
                .filter(h -> h.holdingValue() != null && h.holdingValue() > 0)
                .sorted((a, b) -> Double.compare(b.holdingValue(), a.holdingValue()))
                .limit(5)
                .collect(Collectors.toList());

        List<DashboardDtos.TopMoverDto> topLosers = allHoldings.stream()
                .filter(h -> h.holdingValue() != null && h.holdingValue() > 0)
                .sorted((a, b) -> Double.compare(a.holdingValue(), b.holdingValue()))
                .limit(5)
                .collect(Collectors.toList());

        DashboardDtos.DashboardSummaryDto summary = new DashboardDtos.DashboardSummaryDto(
                round2(totalValue),
                round2(totalCost),
                round2(totalUnrealizedPnl),
                round2(totalReturnPercent),
                portfolios.size(),
                totalAssetCount,
                stockCount,
                bondCount,
                cashCount,
                topGainers,
                topLosers,
                new ArrayList<>(), // recentActivities - 需要额外实现
                Instant.now()
        );

        return ResponseEntity.ok(summary);
    }

    /**
     * 获取所有组合卡片列表
     *
     * @return 组合卡片列表
     */
    @GetMapping("/portfolios")
    public ResponseEntity<List<DashboardDtos.PortfolioCardDto>> getPortfolioCards() {
        List<PortfolioDtos.PortfolioResponse> portfolios = portfolioService.listPortfolios();

        List<DashboardDtos.PortfolioCardDto> cards = portfolios.stream()
                .map(p -> {
                    try {
                        PortfolioDtos.PortfolioSummaryResponse summary = 
                                portfolioService.getSummary(p.id());
                        return new DashboardDtos.PortfolioCardDto(
                                p.id(),
                                p.name(),
                                p.baseCurrency(),
                                summary.totalMarketValue(),
                                summary.unrealizedPnl(),
                                summary.holdings().size(),
                                p.createdAt()
                        );
                    } catch (Exception e) {
                        return new DashboardDtos.PortfolioCardDto(
                                p.id(),
                                p.name(),
                                p.baseCurrency(),
                                0.0,
                                0.0,
                                0,
                                p.createdAt()
                        );
                    }
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(cards);
    }

    /**
     * 获取资产分布
     *
     * @return 资产分布列表
     */
    @GetMapping("/asset-distribution")
    public ResponseEntity<List<DashboardDtos.AssetDistributionDto>> getAssetDistribution() {
        List<PortfolioDtos.PortfolioResponse> portfolios = portfolioService.listPortfolios();

        double totalValue = 0.0;
        java.util.Map<String, Double> assetValues = new java.util.HashMap<>();

        for (PortfolioDtos.PortfolioResponse portfolio : portfolios) {
            try {
                PortfolioDtos.PortfolioSummaryResponse summary = 
                        portfolioService.getSummary(portfolio.id());

                totalValue += summary.totalMarketValue();

                for (java.util.Map.Entry<String, Double> entry : summary.allocationPct().entrySet()) {
                    double value = entry.getValue() * summary.totalMarketValue() / 100;
                    assetValues.merge(entry.getKey(), value, Double::sum);
                }
            } catch (Exception e) {
                // 跳过
            }
        }

        final double finalTotalValue = totalValue;
        List<DashboardDtos.AssetDistributionDto> distribution = assetValues.entrySet().stream()
                .map(e -> new DashboardDtos.AssetDistributionDto(
                        e.getKey(),
                        round2(e.getValue()),
                        finalTotalValue > 0 ? round2(e.getValue() / finalTotalValue * 100) : 0
                ))
                .sorted((a, b) -> Double.compare(b.value(), a.value()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(distribution);
    }

    /**
     * 获取市场概览（指数行情）
     *
     * @return 市场概览
     */
    @GetMapping("/market-overview")
    public ResponseEntity<List<DashboardDtos.MarketOverviewDto>> getMarketOverview() {
        System.out.println("\n[DashboardController.getMarketOverview] 开始 | 获取指数行情...");
        
        List<MarketDtos.MarketPriceDto> indexes = marketService.getMarketIndexes();
        System.out.println("[DEBUG] 获取到指数数量：" + indexes.size());

        List<DashboardDtos.MarketOverviewDto> overview = indexes.stream()
                .map(idx -> {
                    // 获取前收盘价计算涨跌
                    System.out.println("[DEBUG] 计算 " + idx.ticker() + " 的涨跌幅...");
                    Optional<Double> prevCloseOpt = marketService.getPreviousClose(idx.ticker());
                    double currentPrice = idx.currentPrice() != null ? idx.currentPrice() : 0.0;
                    double previousClose = prevCloseOpt.orElse(currentPrice);
                    double change = currentPrice - previousClose;
                    double changePercent = previousClose > 0 ? (change / previousClose) * 100 : 0.0;
                    String trend = change > 0 ? "UP" : (change < 0 ? "DOWN" : "FLAT");
                    
                    System.out.println("[DEBUG] " + idx.ticker() + " | 当前=" + currentPrice + ", 前收=" + previousClose + ", 涨跌=" + round2(changePercent) + "%");
                    
                    return new DashboardDtos.MarketOverviewDto(
                            idx.ticker(),
                            currentPrice,
                            round2(change),
                            round2(changePercent),
                            trend
                    );
                })
                .collect(Collectors.toList());

        System.out.println("[DashboardController.getMarketOverview] 完成 | 返回记录数=" + overview.size());
        return ResponseEntity.ok(overview);
    }

    /**
     * 获取完整仪表盘数据（聚合接口）
     *
     * @return 完整仪表盘
     */
    @GetMapping("/full")
    public ResponseEntity<DashboardDtos.FullDashboardDto> getFullDashboard() {
        DashboardDtos.DashboardSummaryDto summary = getDashboardSummary().getBody();
        List<DashboardDtos.PortfolioCardDto> portfolios = getPortfolioCards().getBody();
        List<DashboardDtos.AssetDistributionDto> assetDistribution = getAssetDistribution().getBody();
        List<DashboardDtos.MarketOverviewDto> marketOverview = getMarketOverview().getBody();
        List<MarketDtos.MarketPriceDto> watchedStocks = marketService.getPopularStocks();

        DashboardDtos.FullDashboardDto fullDashboard = new DashboardDtos.FullDashboardDto(
                summary,
                portfolios,
                assetDistribution,
                marketOverview,
                watchedStocks
        );

        return ResponseEntity.ok(fullDashboard);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
