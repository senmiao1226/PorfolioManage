package com.training.portfolio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.portfolio.config.AppProperties;
import com.training.portfolio.domain.AssetType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class PricingService {

    private static final DateTimeFormatter YAHOO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter CACHED_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 简单内存缓存：减少外部行情请求次数，提升前端交互体验
    private static final long PRICE_CACHE_TTL_MS = 5 * 60 * 1000L;
    private static final long SERIES_CACHE_TTL_MS = 10 * 60 * 1000L;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    private static final class CacheEntry<T> {
        private final T value;
        private final long createdAtMs;

        private CacheEntry(T value, long createdAtMs) {
            this.value = value;
            this.createdAtMs = createdAtMs;
        }
    }

    private final ConcurrentHashMap<String, CacheEntry<Double>> priceCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<List<TimePrice>>> yahooSeriesCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<List<TimePrice>>> cachedDailySeriesCache = new ConcurrentHashMap<>();

    private static boolean isFresh(long createdAtMs, long ttlMs) {
        return System.currentTimeMillis() - createdAtMs <= ttlMs;
    }

    public Optional<Double> fetchCachedPrice(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return Optional.empty();
        }
        String t = ticker.trim().toUpperCase();
        CacheEntry<Double> cached = priceCache.get("cached:" + t);
        if (cached != null && isFresh(cached.createdAtMs, PRICE_CACHE_TTL_MS)) {
            return Optional.ofNullable(cached.value);
        }
        String base = appProperties.getPricing().getCachedPriceBase();
        String url = base + "?ticker=" + URLEncoder.encode(t, StandardCharsets.UTF_8);
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            Optional<Double> parsed = parsePriceJson(body);
            parsed.ifPresent(v -> priceCache.put("cached:" + t, new CacheEntry<>(v, System.currentTimeMillis())));
            return parsed;
        } catch (RestClientException e) {
            return Optional.empty();
        }
    }

    private Optional<Double> parsePriceJson(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.isNumber()) {
                return Optional.of(root.asDouble());
            }
            for (String key : List.of("price", "lastPrice", "close", "value", "data")) {
                if (root.has(key) && root.get(key).isNumber()) {
                    return Optional.of(root.get(key).asDouble());
                }
            }
            // 课程缓存接口 cachedPriceData 返回结构类似：
            // { "price_data": { "close": [..] }, "timestamp": [..], ... }
            JsonNode priceData = root.get("price_data");
            if (priceData != null) {
                JsonNode closeArr = priceData.get("close");
                if (closeArr != null && closeArr.isArray() && closeArr.size() > 0) {
                    JsonNode last = closeArr.get(closeArr.size() - 1);
                    if (last.isNumber()) {
                        return Optional.of(last.asDouble());
                    }
                }
            }
            // 兜底：如果根节点直接是数组形式 close
            JsonNode closeRoot = root.get("close");
            if (closeRoot != null && closeRoot.isArray() && closeRoot.size() > 0) {
                JsonNode last = closeRoot.get(closeRoot.size() - 1);
                if (last.isNumber()) {
                    return Optional.of(last.asDouble());
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return Optional.empty();
    }

    public Optional<Double> priceForHolding(AssetType assetType, String ticker) {
        if (assetType == AssetType.cash) {
            return Optional.of(1.0);
        }
        if (ticker == null || ticker.isBlank()) {
            return Optional.empty();
        }
        Optional<Double> p = fetchCachedPrice(ticker);
        return p.or(() -> yahooLastClose(ticker));
    }

    private Optional<Double> yahooLastClose(String ticker) {
        List<TimePrice> series = fetchYahooAdjCloseSeries(ticker, 7);
        if (series.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(series.get(series.size() - 1).price());
    }

    public record TimePrice(Instant day, double price) {}

    public List<TimePrice> fetchYahooAdjCloseSeries(String ticker, int days) {
        int span = Math.max(days + 5, 7);
        Instant end = Instant.now();
        Instant start = end.minusSeconds(span * 86400L);
        long p1 = start.getEpochSecond();
        long p2 = end.getEpochSecond();
        String symbol = ticker.trim().toUpperCase();
        String cacheKey = symbol + ":" + days;
        CacheEntry<List<TimePrice>> cached = yahooSeriesCache.get(cacheKey);
        if (cached != null && isFresh(cached.createdAtMs, SERIES_CACHE_TTL_MS)) {
            return cached.value;
        }
        String url =
                "https://query1.finance.yahoo.com/v7/finance/download/"
                        + symbol
                        + "?period1="
                        + p1
                        + "&period2="
                        + p2
                        + "&interval=1d&events=history&includeAdjustedClose=true";
        String body;
        try {
            body =
                    restClient
                            .get()
                            .uri(url)
                            .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; PortfolioDemo/1.0)")
                            .retrieve()
                            .body(String.class);
        } catch (RestClientException e) {
            return List.of();
        }
        if (body == null) {
            return List.of();
        }
        List<TimePrice> out = new ArrayList<>();
        String[] lines = body.split("\\R");
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("Date")) {
                continue;
            }
            String[] parts = line.split(",");
            if (parts.length < 6) {
                continue;
            }
            try {
                LocalDate d = LocalDate.parse(parts[0], YAHOO_DATE);
                double adj = Double.parseDouble(parts[5]);
                Instant day = d.atStartOfDay().toInstant(ZoneOffset.UTC);
                out.add(new TimePrice(day, adj));
            } catch (Exception ignored) {
                // skip bad row
            }
        }
        out.sort(Comparator.comparing(TimePrice::day));
        if (out.size() > days) {
            out = out.subList(out.size() - days, out.size());
        }
        List<TimePrice> result = List.copyOf(out);
        if (!result.isEmpty()) {
            yahooSeriesCache.put(cacheKey, new CacheEntry<>(result, System.currentTimeMillis()));
        }
        return result;
    }

    /** 按日汇总股票/债券持仓市值（现金按每日加常数 cashTotal）。 */
    public List<DateValue> portfolioValueSeries(
            List<Map.Entry<String, Double>> tickerQty, double cashTotal, int days) {
        days = Math.max(7, Math.min(days, 365));
        if (tickerQty.isEmpty()) {
            if (cashTotal <= 0) {
                return List.of();
            }
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            List<DateValue> flat = new ArrayList<>();
            for (int i = 0; i < Math.min(days, 30); i++) {
                flat.add(new DateValue(today.minusDays(days - 1 - i), round2(cashTotal)));
            }
            return flat;
        }
        Map<LocalDate, Double> byDate = new TreeMap<>();
        for (Map.Entry<String, Double> e : tickerQty) {
            List<TimePrice> series = fetchCachedDailyCloseSeries(e.getKey(), days);
            if (series.isEmpty()) {
                // 回退：如果缓存接口失败/不支持，才使用 Yahoo 历史数据
                series = fetchYahooAdjCloseSeries(e.getKey(), days);
            }
            for (TimePrice tp : series) {
                LocalDate d = tp.day().atZone(ZoneOffset.UTC).toLocalDate();
                // e.getValue() 理论上不为空（qty 来自数据库 nullable=false 字段），但这里做防御式处理
                Double qtyObj = e.getValue();
                if (qtyObj == null) continue;
                byDate.put(d, byDate.getOrDefault(d, 0.0) + qtyObj * tp.price());
            }
        }
        List<DateValue> points = new ArrayList<>();
        for (Map.Entry<LocalDate, Double> e : byDate.entrySet()) {
            points.add(new DateValue(e.getKey(), round2(e.getValue() + cashTotal)));
        }
        return points;
    }

    /**
     * 使用 cachedPriceData 的 close 序列，聚合为日频（同一天取最后一个 close）。
     *
     * <p>cachedPriceData 的 timestamp 粒度是分钟级；该方法把同一天的数据聚合成一个日值，以便与 UI
     * 的“按天曲线”对齐。
     */
    public List<TimePrice> fetchCachedDailyCloseSeries(String ticker, int days) {
        days = Math.max(7, Math.min(days, 365));
        if (ticker == null || ticker.isBlank()) {
            return List.of();
        }
        String symbol = ticker.trim().toUpperCase();
        String cacheKey = "cachedDaily:" + symbol + ":" + days;
        CacheEntry<List<TimePrice>> cached = cachedDailySeriesCache.get(cacheKey);
        if (cached != null && isFresh(cached.createdAtMs, SERIES_CACHE_TTL_MS)) {
            return cached.value;
        }

        String base = appProperties.getPricing().getCachedPriceBase();
        String url = base + "?ticker=" + URLEncoder.encode(symbol, StandardCharsets.UTF_8);
        String body;
        try {
            body = restClient.get().uri(url).retrieve().body(String.class);
        } catch (RestClientException e) {
            return List.of();
        }
        if (body == null || body.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode priceData = root.get("price_data");
            JsonNode closeArr = priceData != null ? priceData.get("close") : null;
            JsonNode tsArr = root.get("timestamp");
            if (closeArr == null || tsArr == null || !closeArr.isArray() || !tsArr.isArray()) {
                return List.of();
            }

            int len = Math.min(closeArr.size(), tsArr.size());
            Map<LocalDate, Double> byDate = new TreeMap<>();
            for (int i = 0; i < len; i++) {
                JsonNode close = closeArr.get(i);
                JsonNode ts = tsArr.get(i);
                if (close == null || !close.isNumber() || ts == null || ts.isNull()) {
                    continue;
                }
                String tsText = ts.asText();
                if (tsText == null || tsText.isBlank()) {
                    continue;
                }
                try {
                    LocalDateTime ldt = LocalDateTime.parse(tsText, CACHED_TIMESTAMP);
                    LocalDate d = ldt.toLocalDate();
                    // 同一天取最后一个 close
                    byDate.put(d, close.asDouble());
                } catch (Exception ignored) {
                    // skip bad row
                }
            }

            if (byDate.isEmpty()) {
                return List.of();
            }

            List<LocalDate> allDates = new ArrayList<>(byDate.keySet());
            int from = Math.max(0, allDates.size() - days);
            List<TimePrice> out = new ArrayList<>();
            for (int i = from; i < allDates.size(); i++) {
                LocalDate d = allDates.get(i);
                out.add(new TimePrice(d.atStartOfDay().toInstant(ZoneOffset.UTC), byDate.get(d)));
            }

            List<TimePrice> result = List.copyOf(out);
            cachedDailySeriesCache.put(cacheKey, new CacheEntry<>(result, System.currentTimeMillis()));
            return result;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
