
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
import java.util.Iterator;
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
        
        String t = ticker.trim().toUpperCase();
        String[] providers = appProperties.getPricing().getProviders().split(",");
        
        System.out.println("\n========== [价格查询开始] ==========");
        System.out.println("[DEBUG] 查询股票：" + t);
        System.out.println("[DEBUG] 使用前一天收盘价（非实时价格）");
        System.out.println("[DEBUG] 数据源优先级：" + String.join(" > ", providers));
        System.out.println("============================================\n");
        
        // 按优先级尝试各个数据源
        for (String provider : providers) {
            System.out.println("[DEBUG] 尝试数据源 [" + provider.trim() + "]...");
            
            Optional<Double> price = switch (provider.trim()) {
                case "massive" -> fetchMassivePreviousClose(t);
                case "alpha-vantage" -> fetchAlphaVantagePreviousClose(t);
                case "sina" -> fetchSinaPreviousClose(t);
                case "yahoo" -> yahooLastClose(t);
                case "cached" -> fetchCachedPrice(t);
                default -> Optional.empty();
            };
            
            if (price.isPresent()) {
                System.out.println("\n========== [价格查询成功] ==========");
                System.out.println("[DEBUG] ✓ 数据源 [" + provider.trim() + "] 成功获取到前一天收盘价");
                System.out.println("[DEBUG] 价格：" + price.get());
                System.out.println("[DEBUG] 不再请求其他数据源");
                System.out.println("============================================\n");
                return price;
            } else {
                System.out.println("[DEBUG] ✗ 数据源 [" + provider.trim() + "] 未获取到价格，尝试下一个...");
            }
        }
        
        System.out.println("\n========== [价格查询失败] ==========");
        System.out.println("[DEBUG] ✗ 所有数据源都未能获取到价格");
        System.out.println("============================================\n");
        
        return Optional.empty();
    }

    /** 获取 Massive.com 实时价格 */
    private Optional<Double> fetchMassivePrice(String ticker) {
        String apiKey = appProperties.getPricing().getMassiveApiKey();
        String base = appProperties.getPricing().getMassiveBase();
        
        // 使用 Massive.com v3 API 获取股票信息
        // 文档：https://massive.com/docs/rest
        // 正确格式：https://api.massive.com/v3/reference/tickers?ticker=AAPL&market=stocks&active=true&apiKey=MY_KEY
        String url = base + "/reference/tickers?ticker=" + ticker + "&market=stocks&active=true&apiKey=" + apiKey;
        
        System.out.println("\n========== [MASSIVE API 请求详情] ==========");
        System.out.println("[DEBUG] 尝试从 Massive.com 获取 " + ticker + " 的价格...");
        System.out.println("[DEBUG] API Key: " + apiKey);
        System.out.println("[DEBUG] Base URL: " + base);
        System.out.println("[DEBUG] 完整 URL: " + url);
        System.out.println("[DEBUG] 请求方法：GET");
        System.out.println("============================================\n");
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("accept", "application/json");
            // Massive v3 API 使用 apiKey 查询参数，不需要额外的 header
            
            System.out.println("[DEBUG] 发送 HTTP 请求...");
            String body = restClient.get()
                .uri(url)
                .headers(h -> h.addAll(headers))
                .retrieve()
                .body(String.class);
            
            System.out.println("\n========== [MASSIVE API 响应详情] ==========");
            System.out.println("[DEBUG] 响应体原始内容：");
            System.out.println(body);
            System.out.println("============================================\n");
                
            if (body == null || body.isBlank()) {
                System.out.println("[DEBUG] ✗ Massive.com 返回空响应");
                return Optional.empty();
            }
            
            JsonNode root = objectMapper.readTree(body);
            
            // 解析 Massive v3 API 响应
            // 典型响应格式：{"results": [{"ticker": "AAPL", "price": 175.43, ...}]}
            if (root.has("results")) {
                JsonNode results = root.get("results");
                if (results.isArray() && results.size() > 0) {
                    JsonNode firstResult = results.get(0);
                    System.out.println("[DEBUG] 获取到结果：" + firstResult.toString());
                    
                    // 尝试多个可能的价格字段
                    for (String field : List.of("price", "last_price", "close", "current_price", "lastPrice")) {
                        if (firstResult.has(field) && firstResult.get(field).isNumber()) {
                            double price = firstResult.get(field).asDouble();
                            System.out.println("[DEBUG] ✓ 从 Massive.com 获取到价格 (" + field + "): " + price);
                            System.out.println("============================================\n");
                            if (price > 0) {
                                priceCache.put("massive:" + ticker, 
                                    new CacheEntry<>(price, System.currentTimeMillis()));
                                return Optional.of(price);
                            }
                        }
                    }
                    System.out.println("[DEBUG] ✗ 结果中未找到价格字段");
                } else {
                    System.out.println("[DEBUG] ✗ results 数组为空");
                }
            } else {
                System.out.println("[DEBUG] ✗ 响应中没有 results 字段");
                // 打印所有字段名
                System.out.println("[DEBUG] 响应中的字段：");
                root.fieldNames().forEachRemaining(name -> 
                    System.out.println("  - " + name + ": " + root.get(name)));
            }
            
            System.out.println("[DEBUG] ✗ Massive.com 未找到有效价格");
            System.out.println("============================================\n");
        } catch (Exception e) {
            System.out.println("[DEBUG] ✗ Massive.com 请求失败：" + e.getMessage());
            System.out.println("[DEBUG] 错误堆栈：");
            e.printStackTrace();
            System.out.println("============================================\n");
        }
        return Optional.empty();
    }

    /** 获取 Alpha Vantage 实时价格 */
    private Optional<Double> fetchAlphaVantagePrice(String ticker) {
        String apiKey = appProperties.getPricing().getAlphaVantageApiKey();
        String base = appProperties.getPricing().getAlphaVantageBase();
        String url = base + "?function=GLOBAL_QUOTE&symbol=" + ticker + "&apikey=" + apiKey;
        
        System.out.println("\n========== [ALPHA VANTAGE 请求详情] ==========");
        System.out.println("[DEBUG] 尝试从 Alpha Vantage 获取 " + ticker + " 的价格...");
        System.out.println("[DEBUG] URL: " + url);
        
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            System.out.println("[DEBUG] 响应体：" + body);
            
            JsonNode root = objectMapper.readTree(body);
            JsonNode quote = root.get("Global Quote");
            
            if (quote != null && quote.has("05. price")) {
                double price = quote.get("05. price").asDouble();
                System.out.println("[DEBUG] ✓ 从 Alpha Vantage 获取到价格：" + price);
                System.out.println("============================================\n");
                if (price > 0) {
                    priceCache.put("alphavantage:" + ticker, 
                        new CacheEntry<>(price, System.currentTimeMillis()));
                    return Optional.of(price);
                }
            } else {
                System.out.println("[DEBUG] ✗ Alpha Vantage 响应中没有价格数据");
            }
        } catch (Exception e) {
            System.out.println("[DEBUG] ✗ Alpha Vantage 请求失败：" + e.getMessage());
        }
        System.out.println("============================================\n");
        return Optional.empty();
    }

    /** 获取新浪财经实时价格 */
    private Optional<Double> fetchSinaPrice(String ticker) {
        String base = appProperties.getPricing().getSinaBase();
        // 支持 A 股和港股
        String symbol = ticker;
        
        System.out.println("\n========== [SINA 请求详情] ==========");
        System.out.println("[DEBUG] 尝试从新浪财经获取 " + ticker + " 的价格...");
        System.out.println("[DEBUG] 原始 ticker: " + ticker);
        
        if (ticker.matches("\\d{6}")) {
            // A 股代码，需要添加市场前缀
            String prefix = ticker.startsWith("6") || ticker.startsWith("9") ? "sh" : "sz";
            symbol = prefix + ticker;
            System.out.println("[DEBUG] 识别为 A 股，添加前缀：" + prefix);
        } else if (ticker.matches("\\d{4,5}")) {
            // 港股代码（4-5 位数字）
            symbol = "hk" + ticker;
            System.out.println("[DEBUG] 识别为港股，添加前缀：hk");
        } else {
            System.out.println("[DEBUG] 使用原始 ticker: " + symbol);
        }
        
        // 正确的新浪财经 URL 格式：http://hq.sinajs.cn/list=sz000001
        String url = base + "/list=" + symbol;
        System.out.println("[DEBUG] 最终 Symbol: " + symbol);
        System.out.println("[DEBUG] 完整 URL: " + url);
        System.out.println("============================================\n");
        
        try {
            System.out.println("[DEBUG] 发送 HTTP GET 请求...");
            // 新浪财经需要 User-Agent 和 Referer，否则返回 403 Forbidden
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Referer", "https://finance.sina.com.cn/");
            headers.set("Accept", "*/*");
            
            String body = restClient.get()
                .uri(url)
                .headers(h -> h.addAll(headers))
                .retrieve()
                .body(String.class);
            
            System.out.println("\n========== [SINA 响应详情] ==========");
            System.out.println("[DEBUG] 响应体原始内容：");
            System.out.println(body);
            
            if (body != null && !body.isBlank()) {
                // 解析格式：var hq_str_sz000001="平安银行，10.50,10.48,10.45,10.52,..."
                // 第 4 个字段（索引 3）是当前价
                if (body.contains("\"")) {
                    String quotePart = body.substring(body.indexOf("\"") + 1, body.lastIndexOf("\""));
                    System.out.println("[DEBUG] 提取报价部分：" + quotePart);
                    
                    String[] parts = quotePart.split(",");
                    System.out.println("[DEBUG] 分割后的字段数：" + parts.length);
                    
                    if (parts.length > 3) {
                        System.out.println("[DEBUG] 字段详情:");
                        System.out.println("  [0] 股票名称：" + parts[0]);
                        System.out.println("  [1] 今日开盘价：" + parts[1]);
                        System.out.println("  [2] 昨日收盘价：" + parts[2]);
                        System.out.println("  [3] 当前价格：" + parts[3]);
                        
                        double price = Double.parseDouble(parts[3].trim());
                        System.out.println("[DEBUG] ✓ 从新浪财经获取到价格：" + price);
                        System.out.println("============================================\n");
                        if (price > 0) {
                            priceCache.put("sina:" + ticker, 
                                new CacheEntry<>(price, System.currentTimeMillis()));
                            return Optional.of(price);
                        }
                    } else {
                        System.out.println("[DEBUG] ✗ 新浪财经响应格式错误，字段数不足：" + parts.length);
                    }
                } else {
                    System.out.println("[DEBUG] ✗ 新浪财经响应中没有找到引号包裹的数据");
                }
            } else {
                System.out.println("[DEBUG] ✗ 新浪财经返回空响应");
            }
        } catch (Exception e) {
            System.out.println("[DEBUG] ✗ 新浪财经请求失败：" + e.getMessage());
            System.out.println("[DEBUG] 错误堆栈：");
            e.printStackTrace();
        }
        System.out.println("============================================\n");
        return Optional.empty();
    }

    private Optional<Double> yahooLastClose(String ticker) {
        List<TimePrice> series = fetchYahooAdjCloseSeries(ticker, 7);
        if (series.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(series.get(series.size() - 1).price());
    }

    /** 获取 Massive.com 前一天收盘价 */
    private Optional<Double> fetchMassivePreviousClose(String ticker) {
        String apiKey = appProperties.getPricing().getMassiveApiKey();
        String base = appProperties.getPricing().getMassiveBase();
        
        // 使用 Massive.com v1 API 获取前一天收盘价
        // 文档：https://massive.com/docs/rest
        // 正确格式：https://api.massive.com/v1/open-close/AAPL/2023-01-09?adjusted=true&apiKey=MY_KEY
        LocalDate yesterday = LocalDate.now().minusDays(1);
        String url = base + "/open-close/" + ticker + "/" + yesterday + "?adjusted=true&apiKey=" + apiKey;
        
        System.out.println("\n========== [MASSIVE 前一天收盘价请求] ==========");
        System.out.println("[DEBUG] 尝试从 Massive.com 获取 " + ticker + " 的前一天收盘价...");
        System.out.println("[DEBUG] 查询日期：" + yesterday);
        System.out.println("[DEBUG] API Key: " + apiKey);
        System.out.println("[DEBUG] Base URL: " + base);
        System.out.println("[DEBUG] 完整 URL: " + url);
        System.out.println("============================================\n");
        
        try {
            System.out.println("[DEBUG] 发送 HTTP 请求...");
            String body = restClient.get()
                .uri(url)
                .header("accept", "application/json")
                .retrieve()
                .body(String.class);
            
            System.out.println("\n========== [MASSIVE 响应详情] ==========");
            System.out.println("[DEBUG] 响应体原始内容：");
            System.out.println(body);
            
            if (body == null || body.isBlank()) {
                System.out.println("[DEBUG] ✗ Massive.com 返回空响应");
                return Optional.empty();
            }
            
            JsonNode root = objectMapper.readTree(body);
            
            // 解析 Massive v1 API 响应
            // 典型响应格式：{"ticker": "AAPL", "close": 175.43, "open": 174.50, ...}
            if (root.has("close")) {
                double closePrice = root.get("close").asDouble();
                System.out.println("[DEBUG] ✓ 从 Massive.com 获取到前一天收盘价 (" + yesterday + "): " + closePrice);
                System.out.println("============================================\n");
                if (closePrice > 0) {
                    priceCache.put("massive:" + ticker, 
                        new CacheEntry<>(closePrice, System.currentTimeMillis()));
                    return Optional.of(closePrice);
                }
            } else {
                System.out.println("[DEBUG] ✗ 响应中没有 close 字段");
                // 打印所有字段名
                System.out.println("[DEBUG] 响应中的字段：");
                root.fieldNames().forEachRemaining(name -> 
                    System.out.println("  - " + name + ": " + root.get(name)));
            }
            
            System.out.println("[DEBUG] ✗ Massive.com 未找到有效收盘价");
            System.out.println("============================================\n");
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            // 429 错误：请求频率超限
            System.out.println("[DEBUG] ✗ Massive.com 429 错误：请求频率超限，请稍后重试");
            System.out.println("[DEBUG] 错误信息：" + e.getMessage());
            System.out.println("============================================\n");
            return Optional.empty();
        } catch (Exception e) {
            System.out.println("[DEBUG] ✗ Massive.com 请求失败：" + e.getMessage());
            System.out.println("[DEBUG] 错误堆栈：");
            e.printStackTrace();
            System.out.println("============================================\n");
        }
        return Optional.empty();
    }

    /** 获取 Alpha Vantage 前一天收盘价 */
    private Optional<Double> fetchAlphaVantagePreviousClose(String ticker) {
        String apiKey = appProperties.getPricing().getAlphaVantageApiKey();
        String base = appProperties.getPricing().getAlphaVantageBase();
        
        // 使用 Alpha Vantage 的 TIME_SERIES_DAILY 接口获取历史数据
        String url = base + "?function=TIME_SERIES_DAILY&symbol=" + ticker + "&apikey=" + apiKey;
        
        System.out.println("\n========== [ALPHA VANTAGE 前一天收盘价请求] ==========");
        System.out.println("[DEBUG] 尝试从 Alpha Vantage 获取 " + ticker + " 的前一天收盘价...");
        System.out.println("[DEBUG] URL: " + url);
        
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            System.out.println("[DEBUG] 响应体：" + body);
            
            JsonNode root = objectMapper.readTree(body);
            JsonNode timeSeries = root.get("Time Series (Daily)");
            
            if (timeSeries != null && timeSeries.isObject()) {
                // 获取第一个日期（最近一天）
                Iterator<Map.Entry<String, JsonNode>> fields = timeSeries.fields();
                if (fields.hasNext()) {
                    Map.Entry<String, JsonNode> latestDate = fields.next();
                    JsonNode dailyData = latestDate.getValue();
                    
                    // "4. close" 字段是收盘价
                    if (dailyData.has("4. close") && dailyData.get("4. close").isNumber()) {
                        double closePrice = dailyData.get("4. close").asDouble();
                        System.out.println("[DEBUG] ✓ 从 Alpha Vantage 获取到前一天收盘价 (" + latestDate.getKey() + "): " + closePrice);
                        System.out.println("============================================\n");
                        if (closePrice > 0) {
                            priceCache.put("alphavantage:" + ticker, 
                                new CacheEntry<>(closePrice, System.currentTimeMillis()));
                            return Optional.of(closePrice);
                        }
                    }
                }
            } else {
                System.out.println("[DEBUG] ✗ Alpha Vantage 响应中没有时间序列数据");
            }
        } catch (Exception e) {
            System.out.println("[DEBUG] ✗ Alpha Vantage 请求失败：" + e.getMessage());
        }
        System.out.println("============================================\n");
        return Optional.empty();
    }

    /** 获取新浪财经前一天收盘价 */
    private Optional<Double> fetchSinaPreviousClose(String ticker) {
        String base = appProperties.getPricing().getSinaBase();
        String symbol = ticker;
        
        System.out.println("\n========== [SINA 前一天收盘价请求] ==========");
        System.out.println("[DEBUG] 尝试从新浪财经获取 " + ticker + " 的前一天收盘价...");
        System.out.println("[DEBUG] 原始 ticker: " + ticker);
        
        if (ticker.matches("\\d{6}")) {
            // A 股代码，需要添加市场前缀
            String prefix = ticker.startsWith("6") || ticker.startsWith("9") ? "sh" : "sz";
            symbol = prefix + ticker;
            System.out.println("[DEBUG] 识别为 A 股，添加前缀：" + prefix);
        } else if (ticker.matches("\\d{4,5}")) {
            // 港股代码（4-5 位数字）
            symbol = "hk" + ticker;
            System.out.println("[DEBUG] 识别为港股，添加前缀：hk");
        } else {
            System.out.println("[DEBUG] 使用原始 ticker: " + symbol);
        }
        
        // 新浪财经 URL 格式：http://hq.sinajs.cn/list=sz000001
        String url = base + "/list=" + symbol;
        System.out.println("[DEBUG] 最终 Symbol: " + symbol);
        System.out.println("[DEBUG] 完整 URL: " + url);
        System.out.println("============================================\n");
        
        try {
            System.out.println("[DEBUG] 发送 HTTP GET 请求...");
            // 新浪财经需要 User-Agent 和 Referer，否则返回 403 Forbidden
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Referer", "https://finance.sina.com.cn/");
            headers.set("Accept", "*/*");
            
            String body = restClient.get()
                .uri(url)
                .headers(h -> h.addAll(headers))
                .retrieve()
                .body(String.class);
            
            System.out.println("\n========== [SINA 响应详情] ==========");
            System.out.println("[DEBUG] 响应体原始内容：");
            System.out.println(body);
            
            if (body != null && !body.isBlank()) {
                // 解析格式：var hq_str_sz000001="平安银行，10.50,10.48,10.45,10.52,..."
                // 第 3 个字段（索引 2）是昨日收盘价
                if (body.contains("\"")) {
                    String quotePart = body.substring(body.indexOf("\"") + 1, body.lastIndexOf("\""));
                    System.out.println("[DEBUG] 提取报价部分：" + quotePart);
                    
                    String[] parts = quotePart.split(",");
                    System.out.println("[DEBUG] 分割后的字段数：" + parts.length);
                    
                    if (parts.length > 2) {
                        System.out.println("[DEBUG] 字段详情:");
                        System.out.println("  [0] 股票名称：" + parts[0]);
                        System.out.println("  [1] 今日开盘价：" + parts[1]);
                        System.out.println("  [2] 昨日收盘价：" + parts[2]);
                        
                        double previousClose = Double.parseDouble(parts[2].trim());
                        System.out.println("[DEBUG] ✓ 从新浪财经获取到昨日收盘价：" + previousClose);
                        System.out.println("============================================\n");
                        if (previousClose > 0) {
                            priceCache.put("sina:" + ticker, 
                                new CacheEntry<>(previousClose, System.currentTimeMillis()));
                            return Optional.of(previousClose);
                        }
                    } else {
                        System.out.println("[DEBUG] ✗ 新浪财经响应格式错误，字段数不足：" + parts.length);
                    }
                } else {
                    System.out.println("[DEBUG] ✗ 新浪财经响应中没有找到引号包裹的数据");
                }
            } else {
                System.out.println("[DEBUG] ✗ 新浪财经返回空响应");
            }
        } catch (Exception e) {
            System.out.println("[DEBUG] ✗ 新浪财经请求失败：" + e.getMessage());
            System.out.println("[DEBUG] 错误堆栈：");
            e.printStackTrace();
        }
        System.out.println("============================================\n");
        return Optional.empty();
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
        System.out.println("[DEBUG] portfolioValueSeries 调用：tickerQty=" + tickerQty.size() + ", cashTotal=" + cashTotal + ", days=" + days);
        
        days = Math.max(7, Math.min(days, 365));
        if (tickerQty.isEmpty()) {
            if (cashTotal <= 0) {
                System.out.println("[DEBUG] 无持仓且无现金，返回空列表");
                return List.of();
            }
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            List<DateValue> flat = new ArrayList<>();
            for (int i = 0; i < Math.min(days, 30); i++) {
                flat.add(new DateValue(today.minusDays(days - 1 - i), round2(cashTotal)));
            }
            System.out.println("[DEBUG] 仅现金模式，返回 " + flat.size() + " 天数据");
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
