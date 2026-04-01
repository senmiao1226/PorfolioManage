package com.training.portfolio.web;

import com.training.portfolio.domain.AssetType;
import com.training.portfolio.service.PricingService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final PricingService pricingService;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/market/test/{ticker}")
    public Map<String, Object> testMarketData(@PathVariable String ticker) {
        Map<String, Object> result = new HashMap<>();
        result.put("ticker", ticker);
        
        Optional<Double> price = pricingService.priceForHolding(AssetType.stock, ticker);
        result.put("price", price.orElse(null));
        result.put("timestamp", Instant.now());
        
        return result;
    }

    @GetMapping("/market/detail/{ticker}")
    public Map<String, Object> getMarketDetail(@PathVariable String ticker) {
        Map<String, Object> result = new HashMap<>();
        result.put("ticker", ticker);
        result.put("requestedAt", Instant.now());

        // 尝试获取价格
        Optional<Double> price = pricingService.priceForHolding(AssetType.stock, ticker);
        result.put("currentPrice", price.orElse(null));

        return result;
    }
}
