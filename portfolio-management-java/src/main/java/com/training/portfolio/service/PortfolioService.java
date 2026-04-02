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
import java.util.concurrent.ConcurrentHashMap;
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

    /** 短 TTL：Dashboard 并行请求会多次 getSummary；合并为一次定价遍历后的缓存。 */
    private static final long SUMMARY_CACHE_TTL_MS = 25_000L;

    private static final class PortfolioSummaryCacheEntry {
        final PortfolioDtos.PortfolioSummaryResponse summary;
        final long createdAtMs;

        PortfolioSummaryCacheEntry(PortfolioDtos.PortfolioSummaryResponse summary, long createdAtMs) {
            this.summary = summary;
            this.createdAtMs = createdAtMs;
        }
    }

    private final ConcurrentHashMap<Long, PortfolioSummaryCacheEntry> portfolioSummaryCache = new ConcurrentHashMap<>();
    private static final int SUMMARY_STRIPE_COUNT = 64;
    private final Object[] summaryStripes = new Object[SUMMARY_STRIPE_COUNT];

    {
        for (int i = 0; i < SUMMARY_STRIPE_COUNT; i++) {
            summaryStripes[i] = new Object();
        }
    }

    private Object summaryStripeFor(Long portfolioId) {
        return summaryStripes[Math.floorMod(Long.hashCode(portfolioId), SUMMARY_STRIPE_COUNT)];
    }

    private void invalidatePortfolioSummaryCache(Long portfolioId) {
        portfolioSummaryCache.remove(portfolioId);
    }

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
        invalidatePortfolioSummaryCache(id);
        portfolioRepository.deleteById(id);
    }

    public PortfolioDtos.HoldingResponse addHolding(Long portfolioId, PortfolioDtos.HoldingCreateRequest req) {
        if (req.quantity() == null || req.quantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be positive");
        }
        // averageCost 可以为 null 或 0，系统会自动查询历史价格填充
        if (req.averageCost() != null && req.averageCost() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "averageCost must be >= 0");
        }
        Portfolio p = portfolioRepository.findById(portfolioId).orElseThrow(() -> notFound("Portfolio not found"));
        validateCreate(req);
        
        // 如果是股票、债券或基金，尝试获取当前市场价格（带货币换算）
        Double marketPrice = null;
        if ((req.assetType() == AssetType.stock || req.assetType() == AssetType.bond || req.assetType() == AssetType.fund)
            && req.ticker() != null && !req.ticker().isBlank()) {
            System.out.println("\n========== [添加持仓 - 获取市价] ==========");
            System.out.println("[DEBUG] 持仓类型：" + req.assetType());
            System.out.println("[DEBUG] 股票代码：" + req.ticker());
            System.out.println("[DEBUG] 组合本币：" + p.getBaseCurrency());
            System.out.println("[DEBUG] 尝试获取市场价格...");
            
            Optional<Double> priceOpt = pricingService.priceForHoldingWithCurrency(
                req.assetType(), req.ticker(), p.getBaseCurrency());
            if (priceOpt.isPresent()) {
                marketPrice = priceOpt.get();
                System.out.println("[DEBUG] ✓ 成功获取市场价格（已换算）：" + marketPrice + " " + p.getBaseCurrency());
            } else {
                System.out.println("[DEBUG] ✗ 未能获取市场价格，将为空");
            }
            System.out.println("============================================\n");
        }
        
        // 处理成本价：如果提供了purchaseDate但没有提供averageCost，查询历史价格；否则对用户输入的成本价进行汇率换算
        Double averageCost = req.averageCost();
        String baseCurrency = p.getBaseCurrency();
        
        if ((req.assetType() == AssetType.stock || req.assetType() == AssetType.bond || req.assetType() == AssetType.fund)
            && req.ticker() != null && !req.ticker().isBlank()) {
            
            if (averageCost == null || averageCost == 0) {
                // 没有提供成本价，尝试查询历史价格
                if (req.purchaseDate() != null) {
                    System.out.println("\n========== [添加持仓 - 查询历史成交价] ==========");
                    System.out.println("[DEBUG] 股票代码: " + req.ticker());
                    System.out.println("[DEBUG] 购入日期: " + req.purchaseDate());
                    System.out.println("[DEBUG] 组合本币: " + baseCurrency);
                    
                    Optional<Double> historicalPrice = pricingService.fetchHistoricalPriceWithCurrency(
                        req.ticker(), req.purchaseDate(), baseCurrency);
                    if (historicalPrice.isPresent()) {
                        averageCost = historicalPrice.get();
                        System.out.println("[DEBUG] ✓ 成功获取历史价格作为成交价: " + averageCost + " " + baseCurrency);
                    } else {
                        System.out.println("[DEBUG] ✗ 未能获取历史价格，需要手动输入成交价");
                    }
                    System.out.println("============================================\n");
                }
            } else {
                // 用户提供了成本价，需要进行汇率换算
                System.out.println("\n========== [添加持仓 - 成本价汇率换算] ==========");
                System.out.println("[DEBUG] 股票代码: " + req.ticker());
                System.out.println("[DEBUG] 原始成本价: " + averageCost);
                System.out.println("[DEBUG] 组合本币: " + baseCurrency);
                
                // 检测股票所属市场的货币
                String sourceCurrency = pricingService.detectSourceCurrency(req.ticker());
                if (!sourceCurrency.equalsIgnoreCase(baseCurrency)) {
                    double convertedCost = pricingService.convertCurrency(averageCost, sourceCurrency, baseCurrency);
                    System.out.println("[DEBUG] 原始货币: " + sourceCurrency);
                    System.out.println("[DEBUG] 汇率: " + pricingService.getExchangeRate(sourceCurrency, baseCurrency));
                    System.out.println("[DEBUG] 换算后成本价: " + convertedCost + " " + baseCurrency);
                    averageCost = convertedCost;
                } else {
                    System.out.println("[DEBUG] 成本价货币与组合本币相同，无需换算");
                }
                System.out.println("============================================\n");
            }
        }
        
        if (averageCost == null) {
            averageCost = 0.0;
        }
        
        Holding h = new Holding();
        h.setPortfolio(p);
        h.setAssetType(req.assetType());
        h.setTicker(normalizeTicker(req.ticker()));
        h.setName(req.name());
        h.setQuantity(req.quantity());
        h.setAverageCost(averageCost);
        h.setPurchaseDate(req.purchaseDate());
        h.setNotes(req.notes());
        h = holdingRepository.save(h);
        invalidatePortfolioSummaryCache(portfolioId);

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
                h.getPurchaseDate(),
                h.getNotes());
    }

    public PortfolioDtos.HoldingResponse updateHolding(Long holdingId, PortfolioDtos.HoldingUpdateRequest req) {
        Holding h = holdingRepository.findById(holdingId).orElseThrow(() -> notFound("Holding not found"));
        long portfolioId = h.getPortfolio().getId();
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
        if (req.purchaseDate() != null) {
            h.setPurchaseDate(req.purchaseDate());
        }
        if (req.notes() != null) {
            h.setNotes(req.notes());
        }
        h = holdingRepository.save(h);
        invalidatePortfolioSummaryCache(portfolioId);
        return toHoldingResponse(h);
    }

    public void deleteHolding(Long holdingId) {
        Holding h = holdingRepository.findById(holdingId).orElseThrow(() -> notFound("Holding not found"));
        long portfolioId = h.getPortfolio().getId();
        holdingRepository.delete(h);
        invalidatePortfolioSummaryCache(portfolioId);
    }

    @Transactional(readOnly = true)
    public PortfolioDtos.PortfolioSummaryResponse getSummary(Long portfolioId) {
        PortfolioSummaryCacheEntry cached = portfolioSummaryCache.get(portfolioId);
        if (cached != null && System.currentTimeMillis() - cached.createdAtMs <= SUMMARY_CACHE_TTL_MS) {
            return cached.summary;
        }
        synchronized (summaryStripeFor(portfolioId)) {
            cached = portfolioSummaryCache.get(portfolioId);
            if (cached != null && System.currentTimeMillis() - cached.createdAtMs <= SUMMARY_CACHE_TTL_MS) {
                return cached.summary;
            }
            Portfolio p = portfolioRepository.findById(portfolioId).orElseThrow(() -> notFound("Portfolio not found"));
            List<Holding> holdings = holdingRepository.findByPortfolioIdOrderById(portfolioId);
            PortfolioDtos.PortfolioSummaryResponse built = buildSummary(p, holdings);
            portfolioSummaryCache.put(
                    portfolioId, new PortfolioSummaryCacheEntry(built, System.currentTimeMillis()));
            return built;
        }
    }

    private PortfolioDtos.PortfolioSummaryResponse buildSummary(Portfolio p, List<Holding> holdings) {
        List<PortfolioDtos.HoldingValuation> hv = new ArrayList<>();
        double totalCost = 0.0;
        List<Double> navParts = new ArrayList<>();
        String baseCurrency = p.getBaseCurrency();

        for (Holding h : holdings) {
            double costBasis = round4(h.getQuantity() * h.getAverageCost());
            totalCost += costBasis;
            // 使用带货币换算的价格查询
            Optional<Double> mpOpt = pricingService.priceForHoldingWithCurrency(
                h.getAssetType(), h.getTicker(), baseCurrency);
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
                            h.getPurchaseDate(),
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
            if ((h.getAssetType() == AssetType.stock || h.getAssetType() == AssetType.bond || h.getAssetType() == AssetType.fund)
                    && h.getTicker() != null
                    && !h.getTicker().isBlank()) {
                tickerQty.add(Map.entry(h.getTicker().trim().toUpperCase(), h.getQuantity()));
            }
        }
        // TODO: portfolioValueSeries 也需要支持货币换算
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
        if (req.assetType() == AssetType.stock || req.assetType() == AssetType.bond || req.assetType() == AssetType.fund) {
            if (req.ticker() == null || req.ticker().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ticker is required for stock, bond and fund");
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
                h.getPurchaseDate(),
                h.getNotes());
    }

    private static ResponseStatusException notFound(String msg) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, msg);
    }
}
