package com.training.portfolio.service;

import com.training.portfolio.domain.AssetType;
import com.training.portfolio.domain.Holding;
import com.training.portfolio.domain.Portfolio;
import com.training.portfolio.dto.PortfolioDtos;
import com.training.portfolio.repo.HoldingRepository;
import com.training.portfolio.repo.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 投资组合分析服务
 * 提供资产配置分析、业绩分析、历史走势等功能
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final PricingService pricingService;
    private final PortfolioService portfolioService;

    /**
     * 获取资产配置分析
     *
     * @param portfolioId 组合ID
     * @return 各类资产的配置比例
     */
    public List<AllocationDto> getAssetAllocation(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new RuntimeException("Portfolio not found"));

        List<Holding> holdings = holdingRepository.findByPortfolioIdOrderById(portfolioId);

        // 计算各类型资产的总市值
        Map<String, Double> assetValues = new HashMap<>();
        double totalValue = 0.0;

        for (Holding holding : holdings) {
            double marketValue;
            String assetType = holding.getAssetType().name();

            if (holding.getAssetType() == AssetType.cash) {
                marketValue = holding.getQuantity();
            } else {
                Optional<Double> priceOpt = pricingService.priceForHolding(
                        holding.getAssetType(),
                        holding.getTicker()
                );
                marketValue = priceOpt.map(price -> price * holding.getQuantity())
                        .orElse(holding.getQuantity() * holding.getAverageCost());
            }

            assetValues.merge(assetType, marketValue, Double::sum);
            totalValue += marketValue;
        }

        // 计算百分比
        List<AllocationDto> allocations = new ArrayList<>();
        for (Map.Entry<String, Double> entry : assetValues.entrySet()) {
            double percentage = totalValue > 0 ? (entry.getValue() / totalValue) * 100 : 0;
            allocations.add(new AllocationDto(
                    entry.getKey(),
                    round2(entry.getValue()),
                    round2(percentage)
            ));
        }

        // 按价值降序排序
        allocations.sort((a, b) -> Double.compare(b.value(), a.value()));
        return allocations;
    }

    /**
     * 获取所有组合的整体资产配置
     *
     * @return 全局资产配置
     */
    public List<AllocationDto> getGlobalAssetAllocation() {
        List<Portfolio> portfolios = portfolioRepository.findAll();
        Map<String, Double> globalAssetValues = new HashMap<>();
        double totalValue = 0.0;

        for (Portfolio portfolio : portfolios) {
            List<Holding> holdings = holdingRepository.findByPortfolioIdOrderById(portfolio.getId());

            for (Holding holding : holdings) {
                double marketValue;
                String assetType = holding.getAssetType().name();

                if (holding.getAssetType() == AssetType.cash) {
                    marketValue = holding.getQuantity();
                } else {
                    Optional<Double> priceOpt = pricingService.priceForHolding(
                            holding.getAssetType(),
                            holding.getTicker()
                    );
                    marketValue = priceOpt.map(price -> price * holding.getQuantity())
                            .orElse(holding.getQuantity() * holding.getAverageCost());
                }

                globalAssetValues.merge(assetType, marketValue, Double::sum);
                totalValue += marketValue;
            }
        }

        List<AllocationDto> allocations = new ArrayList<>();
        for (Map.Entry<String, Double> entry : globalAssetValues.entrySet()) {
            double percentage = totalValue > 0 ? (entry.getValue() / totalValue) * 100 : 0;
            allocations.add(new AllocationDto(
                    entry.getKey(),
                    round2(entry.getValue()),
                    round2(percentage)
            ));
        }

        allocations.sort((a, b) -> Double.compare(b.value(), a.value()));
        return allocations;
    }

    /**
     * 获取组合历史业绩
     *
     * @param portfolioId 组合ID
     * @param days 天数
     * @return 历史价值数据点
     */
    public List<HistoryPointDto> getPortfolioHistory(Long portfolioId, int days) {
        PortfolioDtos.PerformanceSeriesResponse performance =
                portfolioService.getPerformance(portfolioId, days);

        return performance.points().stream()
                .map(p -> new HistoryPointDto(
                        p.date().toString(),
                        p.value()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 获取所有组合的总历史业绩（汇总）
     *
     * @param days 天数
     * @return 汇总历史价值
     */
    public List<HistoryPointDto> getGlobalPortfolioHistory(int days) {
        List<Portfolio> portfolios = portfolioRepository.findAll();
        Map<LocalDate, Double> dateValues = new TreeMap<>();

        for (Portfolio portfolio : portfolios) {
            try {
                List<HistoryPointDto> history = getPortfolioHistory(portfolio.getId(), days);
                for (HistoryPointDto point : history) {
                    LocalDate date = LocalDate.parse(point.date());
                    dateValues.merge(date, point.value(), Double::sum);
                }
            } catch (Exception e) {
                // 跳过无法获取历史的组合
            }
        }

        return dateValues.entrySet().stream()
                .map(e -> new HistoryPointDto(e.getKey().toString(), round2(e.getValue())))
                .collect(Collectors.toList());
    }

    /**
     * 获取组合业绩统计
     *
     * @param portfolioId 组合ID
     * @return 业绩统计数据
     */
    public PerformanceStatsDto getPerformanceStats(Long portfolioId) {
        PortfolioDtos.PortfolioSummaryResponse summary =
                portfolioService.getSummary(portfolioId);

        double totalCost = summary.totalCost();
        double totalValue = summary.totalMarketValue();
        double unrealizedPnl = summary.unrealizedPnl();

        double returnPercent = totalCost > 0 ? (unrealizedPnl / totalCost) * 100 : 0;

        // 计算持仓数量
        List<Holding> holdings = holdingRepository.findByPortfolioIdOrderById(portfolioId);
        int holdingCount = holdings.size();

        return new PerformanceStatsDto(
                round2(totalCost),
                round2(totalValue),
                round2(unrealizedPnl),
                round2(returnPercent),
                holdingCount,
                summary.allocationPct()
        );
    }

    /**
     * 获取全局业绩统计（所有组合汇总）
     *
     * @return 全局业绩统计
     */
    public PerformanceStatsDto getGlobalPerformanceStats() {
        List<Portfolio> portfolios = portfolioRepository.findAll();

        double globalCost = 0.0;
        double globalValue = 0.0;
        int totalHoldings = 0;
        Map<String, Double> globalAllocation = new HashMap<>();

        for (Portfolio portfolio : portfolios) {
            try {
                PortfolioDtos.PortfolioSummaryResponse summary =
                        portfolioService.getSummary(portfolio.getId());

                globalCost += summary.totalCost();
                globalValue += summary.totalMarketValue();
                totalHoldings += portfolio.getHoldings().size();

                // 汇总资产配置
                for (Map.Entry<String, Double> entry : summary.allocationPct().entrySet()) {
                    // 按市值加权
                    double weightedValue = entry.getValue() * summary.totalMarketValue();
                    globalAllocation.merge(entry.getKey(), weightedValue, Double::sum);
                }
            } catch (Exception e) {
                // 跳过无法获取统计的组合
            }
        }

        double globalPnl = globalValue - globalCost;
        double globalReturnPercent = globalCost > 0 ? (globalPnl / globalCost) * 100 : 0;

        // 重新计算百分比
        Map<String, Double> globalAllocationPct = new HashMap<>();
        for (Map.Entry<String, Double> entry : globalAllocation.entrySet()) {
            double pct = globalValue > 0 ? (entry.getValue() / globalValue) : 0;
            globalAllocationPct.put(entry.getKey(), round2(pct));
        }

        return new PerformanceStatsDto(
                round2(globalCost),
                round2(globalValue),
                round2(globalPnl),
                round2(globalReturnPercent),
                totalHoldings,
                globalAllocationPct
        );
    }

    /**
     * 获取持仓集中度分析（前N大持仓）
     *
     * @param portfolioId 组合ID
     * @param topN 前N个
     * @return 持仓集中度
     */
    public List<TopHoldingDto> getTopHoldings(Long portfolioId, int topN) {
        PortfolioDtos.PortfolioSummaryResponse summary =
                portfolioService.getSummary(portfolioId);

        return summary.holdings().stream()
                .filter(h -> h.marketValue() != null)
                .sorted((a, b) -> Double.compare(b.marketValue(), a.marketValue()))
                .limit(topN)
                .map(h -> new TopHoldingDto(
                        h.ticker(),
                        h.assetType(),
                        h.quantity(),
                        h.marketPrice(),
                        h.marketValue(),
                        h.costBasis(),
                        h.unrealizedPnl()
                ))
                .collect(Collectors.toList());
    }

    // ========== DTO Records ==========

    public record AllocationDto(
            String assetType,
            Double value,
            Double percentage
    ) {}

    public record HistoryPointDto(
            String date,
            Double value
    ) {}

    public record PerformanceStatsDto(
            Double totalCost,
            Double totalMarketValue,
            Double unrealizedPnl,
            Double returnPercent,
            Integer holdingCount,
            Map<String, Double> allocationPct
    ) {}

    public record TopHoldingDto(
            String ticker,
            String assetType,
            Double quantity,
            Double marketPrice,
            Double marketValue,
            Double costBasis,
            Double unrealizedPnl
    ) {}

    // ========== 私有辅助方法 ==========

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
