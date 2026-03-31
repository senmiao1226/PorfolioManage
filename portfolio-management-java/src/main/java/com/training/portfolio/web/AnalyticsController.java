package com.training.portfolio.web;

import com.training.portfolio.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 投资分析控制器
 * 提供资产配置、业绩分析、历史走势等接口
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * 获取指定组合的资产配置
     *
     * @param portfolioId 组合ID
     * @return 资产配置列表
     */
    @GetMapping("/allocation/{portfolioId}")
    public ResponseEntity<List<AnalyticsService.AllocationDto>> getAssetAllocation(
            @PathVariable Long portfolioId) {
        List<AnalyticsService.AllocationDto> allocation = 
                analyticsService.getAssetAllocation(portfolioId);
        return ResponseEntity.ok(allocation);
    }

    /**
     * 获取全局资产配置（所有组合汇总）
     *
     * @return 全局资产配置
     */
    @GetMapping("/allocation/global")
    public ResponseEntity<List<AnalyticsService.AllocationDto>> getGlobalAssetAllocation() {
        List<AnalyticsService.AllocationDto> allocation = 
                analyticsService.getGlobalAssetAllocation();
        return ResponseEntity.ok(allocation);
    }

    /**
     * 获取组合历史走势
     *
     * @param portfolioId 组合ID
     * @param days 天数（默认30天）
     * @return 历史数据点
     */
    @GetMapping("/history/{portfolioId}")
    public ResponseEntity<List<AnalyticsService.HistoryPointDto>> getPortfolioHistory(
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "30") int days) {
        List<AnalyticsService.HistoryPointDto> history = 
                analyticsService.getPortfolioHistory(portfolioId, days);
        return ResponseEntity.ok(history);
    }

    /**
     * 获取全局历史走势（所有组合汇总）
     *
     * @param days 天数（默认30天）
     * @return 全局历史数据
     */
    @GetMapping("/history/global")
    public ResponseEntity<List<AnalyticsService.HistoryPointDto>> getGlobalHistory(
            @RequestParam(defaultValue = "30") int days) {
        List<AnalyticsService.HistoryPointDto> history = 
                analyticsService.getGlobalPortfolioHistory(days);
        return ResponseEntity.ok(history);
    }

    /**
     * 获取组合业绩统计
     *
     * @param portfolioId 组合ID
     * @return 业绩统计
     */
    @GetMapping("/performance/{portfolioId}")
    public ResponseEntity<AnalyticsService.PerformanceStatsDto> getPerformanceStats(
            @PathVariable Long portfolioId) {
        AnalyticsService.PerformanceStatsDto stats = 
                analyticsService.getPerformanceStats(portfolioId);
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取全局业绩统计（所有组合汇总）
     *
     * @return 全局业绩统计
     */
    @GetMapping("/performance/global")
    public ResponseEntity<AnalyticsService.PerformanceStatsDto> getGlobalPerformanceStats() {
        AnalyticsService.PerformanceStatsDto stats = 
                analyticsService.getGlobalPerformanceStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取组合前N大持仓
     *
     * @param portfolioId 组合ID
     * @param topN 前N个（默认10）
     * @return 前N大持仓
     */
    @GetMapping("/top-holdings/{portfolioId}")
    public ResponseEntity<List<AnalyticsService.TopHoldingDto>> getTopHoldings(
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "10") int topN) {
        List<AnalyticsService.TopHoldingDto> topHoldings = 
                analyticsService.getTopHoldings(portfolioId, topN);
        return ResponseEntity.ok(topHoldings);
    }
}
