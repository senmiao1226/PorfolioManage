package com.training.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.training.portfolio.domain.AssetType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class PortfolioDtos {

    private PortfolioDtos() {}

    public record PortfolioCreateRequest(String name, String description, String baseCurrency) {}

    public record PortfolioUpdateRequest(String name, String description, String baseCurrency) {}

    public record PortfolioResponse(Long id, String name, String description, String baseCurrency, Instant createdAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HoldingResponse(
            Long id,
            Long portfolioId,
            AssetType assetType,
            String ticker,
            String name,
            Double quantity,
            Double averageCost,
            Double marketPrice,
            String notes) {}

    public record PortfolioDetailResponse(
            Long id,
            String name,
            String description,
            String baseCurrency,
            Instant createdAt,
            List<HoldingResponse> holdings) {}

    public record HoldingCreateRequest(
            AssetType assetType,
            String ticker,
            String name,
            Double quantity,
            Double averageCost,
            String notes) {}

    public record HoldingUpdateRequest(
            AssetType assetType,
            String ticker,
            String name,
            Double quantity,
            Double averageCost,
            String notes) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HoldingValuation(
            Long holdingId,
            String assetType,
            String ticker,
            Double quantity,
            Double marketPrice,
            Double marketValue,
            Double costBasis,
            Double unrealizedPnl) {}

    public record PortfolioSummaryResponse(
            Long portfolioId,
            String name,
            String baseCurrency,
            Double totalCost,
            Double totalMarketValue,
            Double unrealizedPnl,
            Map<String, Double> allocationPct,
            List<HoldingValuation> holdings) {}

    public record PerformancePoint(LocalDate date, Double value) {}

    public record PerformanceSeriesResponse(Long portfolioId, int days, List<PerformancePoint> points) {}
}
