package com.training.portfolio.service;

import com.training.portfolio.domain.AssetType;
import com.training.portfolio.domain.Holding;
import com.training.portfolio.domain.Portfolio;
import com.training.portfolio.dto.PortfolioDtos;
import com.training.portfolio.repo.HoldingRepository;
import com.training.portfolio.repo.PortfolioRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final PricingService pricingService;

    @Transactional(readOnly = true)
    public List<PortfolioDtos.PortfolioResponse> listPortfolios() {
        return portfolioRepository.findAll(Sort.by("id")).stream().map(this::toPortfolioResponse).toList();
    }

    @Transactional(readOnly = true)
    public PortfolioDtos.PortfolioDetailResponse getPortfolio(Long id) {
        Portfolio p =
                portfolioRepository
                        .findDetailById(id)
                        .orElseThrow(() -> notFound("Portfolio not found"));
        return toDetail(p);
    }

    public PortfolioDtos.PortfolioResponse createPortfolio(PortfolioDtos.PortfolioCreateRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        Portfolio p = new Portfolio();
        p.setName(req.name());
        p.setDescription(req.description());
        if (req.baseCurrency() != null && !req.baseCurrency().isBlank()) {
            p.setBaseCurrency(req.baseCurrency());
        }
        p = portfolioRepository.save(p);
        return toPortfolioResponse(p);
    }

    public PortfolioDtos.PortfolioResponse updatePortfolio(Long id, PortfolioDtos.PortfolioUpdateRequest req) {
        Portfolio p = portfolioRepository.findById(id).orElseThrow(() -> notFound("Portfolio not found"));
        if (req.name() != null) {
            p.setName(req.name());
        }
        if (req.description() != null) {
            p.setDescription(req.description());
        }
        if (req.baseCurrency() != null) {
            p.setBaseCurrency(req.baseCurrency());
        }
        p = portfolioRepository.save(p);
        return toPortfolioResponse(p);
    }

    public void deletePortfolio(Long id) {
        if (!portfolioRepository.existsById(id)) {
            throw notFound("Portfolio not found");
        }
        portfolioRepository.deleteById(id);
    }

    public PortfolioDtos.HoldingResponse addHolding(Long portfolioId, PortfolioDtos.HoldingCreateRequest req) {
        if (req.quantity() == null || req.quantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be positive");
        }
        if (req.averageCost() == null || req.averageCost() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "averageCost must be >= 0");
        }
        Portfolio p = portfolioRepository.findById(portfolioId).orElseThrow(() -> notFound("Portfolio not found"));
        validateCreate(req);
        
        // 如果是股票或债券，尝试获取当前市场价格（仅查询当前添加的股票，避免 API 限制）
        Double marketPrice = null;
        if ((req.assetType() == AssetType.stock || req.assetType() == AssetType.bond) 
            && req.ticker() != null && !req.ticker().isBlank()) {
            System.out.println("\n========== [添加持仓 - 获取市价] ==========");
            System.out.println("[DEBUG] 持仓类型：" + req.assetType());
            System.out.println("[DEBUG] 股票代码：" + req.ticker());
            System.out.println("[DEBUG] 尝试获取市场价格...");
            
            Optional<Double> priceOpt = pricingService.priceForHolding(req.assetType(), req.ticker());
            if (priceOpt.isPresent()) {
                marketPrice = priceOpt.get();
                System.out.println("[DEBUG] ✓ 成功获取市场价格：" + marketPrice);
            } else {
                System.out.println("[DEBUG] ✗ 未能获取市场价格，将为空");
            }
            System.out.println("============================================\n");
        }
        
        Holding h = new Holding();
        h.setPortfolio(p);
        h.setAssetType(req.assetType());
        h.setTicker(normalizeTicker(req.ticker()));
        h.setName(req.name());
        h.setQuantity(req.quantity());
        h.setAverageCost(req.averageCost());
        h.setNotes(req.notes());
        h = holdingRepository.save(h);
        
        // 返回响应时包含市场价格
        return new PortfolioDtos.HoldingResponse(
                h.getId(),
                h.getPortfolio().getId(),
                h.getAssetType(),
                h.getTicker(),
                h.getName(),
                h.getQuantity(),
                h.getAverageCost(),
                marketPrice,
                h.getNotes());
    }

    public PortfolioDtos.HoldingResponse updateHolding(Long holdingId, PortfolioDtos.HoldingUpdateRequest req) {
        Holding h = holdingRepository.findById(holdingId).orElseThrow(() -> notFound("Holding not found"));
        if (req.assetType() != null) {
            h.setAssetType(req.assetType());
        }
        if (req.ticker() != null) {
            h.setTicker(normalizeTicker(req.ticker()));
        }
        if (req.name() != null) {
            h.setName(req.name());
        }
        if (req.quantity() != null) {
            h.setQuantity(req.quantity());
        }
        if (req.averageCost() != null) {
            h.setAverageCost(req.averageCost());
        }
        if (req.notes() != null) {
            h.setNotes(req.notes());
        }
        h = holdingRepository.save(h);
        return toHoldingResponse(h);
    }

    public void deleteHolding(Long holdingId) {
        if (!holdingRepository.existsById(holdingId)) {
            throw notFound("Holding not found");
        }
        holdingRepository.deleteById(holdingId);
    }

    @Transactional(readOnly = true)
    public PortfolioDtos.PortfolioSummaryResponse getSummary(Long portfolioId) {
        Portfolio p = portfolioRepository.findById(portfolioId).orElseThrow(() -> notFound("Portfolio not found"));
        List<Holding> holdings = holdingRepository.findByPortfolioIdOrderById(portfolioId);
        return buildSummary(p, holdings);
    }

    private PortfolioDtos.PortfolioSummaryResponse buildSummary(Portfolio p, List<Holding> holdings) {
        List<PortfolioDtos.HoldingValuation> hv = new ArrayList<>();
        double totalCost = 0.0;
        List<Double> navParts = new ArrayList<>();

        for (Holding h : holdings) {
            double costBasis = round4(h.getQuantity() * h.getAverageCost());
            totalCost += costBasis;
            Optional<Double> mpOpt = pricingService.priceForHolding(h.getAssetType(), h.getTicker());
            Double marketPrice;
            Double marketValue;
            Double unrealized;
            if (h.getAssetType() == AssetType.cash) {
                marketPrice = 1.0;
                marketValue = round4(h.getQuantity());
                unrealized = round4(marketValue - costBasis);
                navParts.add(marketValue);
            } else if (mpOpt.isPresent()) {
                marketPrice = mpOpt.get();
                marketValue = round4(h.getQuantity() * marketPrice);
                unrealized = round4(marketValue - costBasis);
                navParts.add(marketValue);
            } else {
                marketPrice = null;
                marketValue = null;
                unrealized = null;
                navParts.add(costBasis);
            }
            hv.add(
                    new PortfolioDtos.HoldingValuation(
                            h.getId(),
                            h.getAssetType().name(),
                            h.getTicker(),
                            h.getQuantity(),
                            marketPrice,
                            marketValue,
                            costBasis,
                            unrealized));
        }

        double totalMarketValue = round2(navParts.stream().mapToDouble(Double::doubleValue).sum());
        double unrealizedPnl = round2(totalMarketValue - totalCost);

        Map<String, Double> allocation = new HashMap<>();
        if (totalMarketValue > 0) {
            for (PortfolioDtos.HoldingValuation row : hv) {
                double part = row.marketValue() != null ? row.marketValue() : row.costBasis();
                String key = row.assetType();
                allocation.merge(key, part, Double::sum);
            }
            for (String k : new ArrayList<>(allocation.keySet())) {
                allocation.put(k, round2(100.0 * allocation.get(k) / totalMarketValue));
            }
        }

        return new PortfolioDtos.PortfolioSummaryResponse(
                p.getId(),
                p.getName(),
                p.getBaseCurrency(),
                round2(totalCost),
                totalMarketValue,
                unrealizedPnl,
                allocation,
                hv);
    }

    @Transactional(readOnly = true)
    public PortfolioDtos.PerformanceSeriesResponse getPerformance(Long portfolioId, int days) {
        Portfolio p = portfolioRepository.findById(portfolioId).orElseThrow(() -> notFound("Portfolio not found"));
        days = Math.max(7, Math.min(days, 365));
        List<Holding> holdings = holdingRepository.findByPortfolioIdOrderById(portfolioId);
        double cashTotal =
                holdings.stream()
                        .filter(h -> h.getAssetType() == AssetType.cash)
                        .mapToDouble(Holding::getQuantity)
                        .sum();
        List<Map.Entry<String, Double>> tickerQty = new ArrayList<>();
        for (Holding h : holdings) {
            if ((h.getAssetType() == AssetType.stock || h.getAssetType() == AssetType.bond)
                    && h.getTicker() != null
                    && !h.getTicker().isBlank()) {
                tickerQty.add(Map.entry(h.getTicker().trim().toUpperCase(), h.getQuantity()));
            }
        }
        List<DateValue> series = pricingService.portfolioValueSeries(tickerQty, cashTotal, days);
        List<PortfolioDtos.PerformancePoint> points =
                series.stream()
                        .map(dv -> new PortfolioDtos.PerformancePoint(dv.date(), dv.value()))
                        .toList();
        return new PortfolioDtos.PerformanceSeriesResponse(p.getId(), days, points);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private void validateCreate(PortfolioDtos.HoldingCreateRequest req) {
        if (req.assetType() == AssetType.stock || req.assetType() == AssetType.bond) {
            if (req.ticker() == null || req.ticker().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ticker is required for stock and bond");
            }
        }
    }

    private static String normalizeTicker(String ticker) {
        return ticker == null ? null : ticker.trim().toUpperCase();
    }

    private PortfolioDtos.PortfolioResponse toPortfolioResponse(Portfolio p) {
        return new PortfolioDtos.PortfolioResponse(
                p.getId(), p.getName(), p.getDescription(), p.getBaseCurrency(), p.getCreatedAt());
    }

    private PortfolioDtos.PortfolioDetailResponse toDetail(Portfolio p) {
        List<PortfolioDtos.HoldingResponse> list =
                p.getHoldings().stream()
                        .sorted(Comparator.comparing(Holding::getId))
                        .map(this::toHoldingResponse)
                        .toList();
        return new PortfolioDtos.PortfolioDetailResponse(
                p.getId(), p.getName(), p.getDescription(), p.getBaseCurrency(), p.getCreatedAt(), list);
    }

    private PortfolioDtos.HoldingResponse toHoldingResponse(Holding h) {
        return new PortfolioDtos.HoldingResponse(
                h.getId(),
                h.getPortfolio().getId(),
                h.getAssetType(),
                h.getTicker(),
                h.getName(),
                h.getQuantity(),
                h.getAverageCost(),
                null,  // marketPrice 仅在添加持仓时返回，列表查询时不返回以避免频繁 API 调用
                h.getNotes());
    }

    private static ResponseStatusException notFound(String msg) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, msg);
    }
}
