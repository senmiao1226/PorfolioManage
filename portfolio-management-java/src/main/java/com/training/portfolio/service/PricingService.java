
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
import java.util.Set;
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
    private final ConcurrentHashMap<String, CacheEntry<List<TimePrice>>> cachedDailySeriesCache = new ConcurrentHashMap<>();

    /** Serialize fetches per stripe so parallel HTTP handlers do not each hit Massive for the same ticker. */
    private static final int PRICE_STRIPE_COUNT = 256;
    private final Object[] priceFetchStripes = new Object[PRICE_STRIPE_COUNT];

    {
        for (int i = 0; i < PRICE_STRIPE_COUNT; i++) {
            priceFetchStripes[i] = new Object();
        }
    }

    private Object priceStripeFor(String tickerUpper) {
        return priceFetchStripes[Math.floorMod(tickerUpper.hashCode(), PRICE_STRIPE_COUNT)];
    }

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

    /**
     * 获取调用来源信息（用于日志追踪）
     */
    private String getCallerInfo() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        // 跳过当前方法和Thread.getStackTrace
        for (int i = 3; i < stack.length && i < 8; i++) {
            String className = stack[i].getClassName();
            if (!className.contains("PricingService") && !className.contains("java.lang")) {
                String simpleName = className.substring(className.lastIndexOf('.') + 1);
                return simpleName + "." + stack[i].getMethodName() + "()";
            }
        }
        return "unknown";
    }

    /**
     * 判断股票类型
     * - 中概股/A股：纯数字 6位(sh/sz) 或 4-5位(hk) 或带前缀 SH/SZ/HK
     * - 美股：字母组成，通常1-5个字母
     */
    private StockType detectStockType(String ticker) {
        String t = ticker.trim().toUpperCase();
        
        // A股：6位数字 或 SH/SZ开头+6位数字
        if (t.matches("^\\d{6}$") || t.matches("^(SH|SZ)\\d{6}$")) {
            return StockType.A_SHARE;
        }
        
        // 港股：4-5位数字 或 HK开头+4-5位数字
        if (t.matches("^\\d{4,5}$") || t.matches("^HK\\d{4,5}$")) {
            return StockType.HK_STOCK;
        }
        
        // 中概股：在美股上市的中国公司（通过常见代码识别）
        // 这些股票在Massive API中可能也能查到，但我们可以优先尝试其他源
        Set<String> chineseConceptStocks = Set.of(
            "BABA", "JD", "PDD", "NIO", "LI", "XPEV", "BEKE", "ZTO", "TME", 
            "BILI", "IQ", "YY", "VIPS", "FUTU", "TIGR", "DADA", "YMM"
        );
        if (chineseConceptStocks.contains(t)) {
            return StockType.CHINESE_CONCEPT;
        }
        
        // 默认美股
        return StockType.US_STOCK;
    }
    
    private enum StockType {
        US_STOCK,      // 美股
        A_SHARE,       // A股
        HK_STOCK,      // 港股
        CHINESE_CONCEPT // 中概股（美股上市的中国公司）
    }
    
    /**
     * 根据股票类型获取适用的数据源列表
     * 避免所有股票都尝试Massive API，节省API调用次数
     */
    private String[] getProvidersForStockType(StockType type) {
        switch (type) {
            case US_STOCK:
                // 美股：优先使用Massive（专业美股数据）
                return new String[]{"massive", "alpha-vantage", "cached"};
            case CHINESE_CONCEPT:
                // 中概股：可以尝试多个源，但优先非Massive
                return new String[]{"cached", "alpha-vantage", "massive"};
            case A_SHARE:
            case HK_STOCK:
                // A股/港股：使用Sina（国内数据源），完全不调用Massive
                return new String[]{"sina", "cached"};
            default:
                return new String[]{"cached"};
        }
    }

    public Optional<Double> priceForHolding(AssetType assetType, String ticker) {
        String caller = getCallerInfo();
        
        if (assetType == AssetType.cash) {
            System.out.println("[PricingService.priceForHolding] ← 被 [" + caller + "] 调用 | 类型=cash, 直接返回1.0");
            return Optional.of(1.0);
        }
        if (ticker == null || ticker.isBlank()) {
            System.out.println("[PricingService.priceForHolding] ← 被 [" + caller + "] 调用 | ticker为空");
            return Optional.empty();
        }
        
        String t = ticker.trim().toUpperCase();
        
        // 【优先检查内存缓存】如果 5 分钟内查询过，直接返回，不再请求任何 API
        CacheEntry<Double> cached = priceCache.get("cached:" + t);
        if (cached != null && isFresh(cached.createdAtMs, PRICE_CACHE_TTL_MS)) {
            System.out.println("\n========== [PricingService.priceForHolding] ← 被 [" + caller + "] 调用 ==========");
            System.out.println("[DEBUG] 股票：" + t);
            System.out.println("[DEBUG] ✓ 命中内存缓存（5分钟内），价格=" + cached.value);
            System.out.println("[DEBUG] 跳过所有API请求");
            System.out.println("=================================================================\n");
            return Optional.ofNullable(cached.value);
        }

        StockType stockType = detectStockType(t);
        String[] providers = getProvidersForStockType(stockType);

        synchronized (priceStripeFor(t)) {
            cached = priceCache.get("cached:" + t);
            if (cached != null && isFresh(cached.createdAtMs, PRICE_CACHE_TTL_MS)) {
                return Optional.ofNullable(cached.value);
            }

            System.out.println("\n========== [PricingService.priceForHolding] ← 被 [" + caller + "] 调用 ==========");
            System.out.println("[DEBUG] 股票：" + t);
            System.out.println("[DEBUG] 检测到股票类型：" + stockType);
            System.out.println("[DEBUG] 使用数据源优先级：" + String.join(" > ", providers));
            System.out.println("[DEBUG] 缓存未命中，单飞锁内查询外部API");
            System.out.println("=================================================================\n");

            for (String provider : providers) {
                System.out.println("[DEBUG] 尝试数据源 [" + provider.trim() + "]...");

                Optional<Double> price = switch (provider.trim()) {
                    case "massive" -> fetchMassivePreviousClose(t);
                    case "alpha-vantage" -> fetchAlphaVantagePreviousClose(t);
                    case "sina" -> fetchSinaPreviousClose(t);
                    case "cached" -> fetchCachedPrice(t);
                    default -> Optional.empty();
                };

                if (price.isPresent()) {
                    System.out.println("\n[DEBUG] ✓ 数据源 [" + provider.trim() + "] 成功获取价格=" + price.get());
                    System.out.println("[DEBUG] 存入缓存，有效期5分钟");
                    System.out.println("========== [PricingService.priceForHolding] 完成 ==========\n");
                    priceCache.put("cached:" + t, new CacheEntry<>(price.get(), System.currentTimeMillis()));
                    return price;
                } else {
                    System.out.println("[DEBUG] ✗ 数据源 [" + provider.trim() + "] 未获取到价格，尝试下一个...");
                }
            }

            System.out.println("\n[DEBUG] ✗ 所有数据源都未能获取到价格");
            System.out.println("========== [PricingService.priceForHolding] 失败 ==========\n");

            return Optional.empty();
        }
    }

    /**
     * 获取前收盘价（用于计算涨跌幅）
     * 当前价格就是前收盘价，直接复用 priceForHolding 的缓存结果
     */
    public Optional<Double> fetchPreviousClose(AssetType assetType, String ticker) {
        String caller = getCallerInfo();
        System.out.println("[PricingService.fetchPreviousClose] ← 被 [" + caller + "] 调用 | ticker=" + ticker);
        // 当前价格本身就是前收盘价，直接返回（会复用 priceForHolding 的缓存）
        return priceForHolding(assetType, ticker);
    }

    /** 获取 Massive.com 实时价格 */
    private Optional<Double> fetchMassivePrice(String ticker) {
        String base = appProperties.getPricing().getMassiveBase();
        
        // 构建所有API密钥列表（主密钥 + 备用密钥）
        List<String> apiKeys = new ArrayList<>();
        apiKeys.add(appProperties.getPricing().getMassiveApiKey());
        apiKeys.addAll(appProperties.getPricing().getMassiveApiKeyFallbacks());
        
        System.out.println("\n========== [MASSIVE API 请求详情] ==========");
        System.out.println("[DEBUG] 尝试从 Massive.com 获取 " + ticker + " 的价格...");
        System.out.println("[DEBUG] 可用 API Key 数量：" + apiKeys.size());
        System.out.println("============================================\n");
        
        for (int i = 0; i < apiKeys.size(); i++) {
            String apiKey = apiKeys.get(i);
            String url = base + "/reference/tickers?ticker=" + ticker + "&market=stocks&active=true&apiKey=" + apiKey;
            
            System.out.println("[DEBUG] 尝试使用 API Key #" + (i + 1) + ": " + apiKey.substring(0, Math.min(8, apiKey.length())) + "...");
            
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("accept", "application/json");
                
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
                    continue;
                }
                
                JsonNode root = objectMapper.readTree(body);
                
                // 解析 Massive v3 API 响应
                if (root.has("results")) {
                    JsonNode results = root.get("results");
                    if (results.isArray() && results.size() > 0) {
                        JsonNode firstResult = results.get(0);
                        
                        // 尝试多个可能的价格字段
                        for (String field : List.of("price", "last_price", "close", "current_price", "lastPrice")) {
                            if (firstResult.has(field) && firstResult.get(field).isNumber()) {
                                double price = firstResult.get(field).asDouble();
                                System.out.println("[DEBUG] ✓ 从 Massive.com 获取到价格 (" + field + "): " + price);
                                if (price > 0) {
                                    priceCache.put("massive:" + ticker, 
                                        new CacheEntry<>(price, System.currentTimeMillis()));
                                    return Optional.of(price);
                                }
                            }
                        }
                    }
                }
                
            } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
                System.out.println("[DEBUG] ✗ Massive.com 429 错误：请求频率超限，尝试下一个 API Key...");
                if (i < apiKeys.size() - 1) {
                    System.out.println("[DEBUG] 切换到备用 API Key #" + (i + 2));
                    continue;
                } else {
                    System.out.println("[DEBUG] ✗ 所有 API Key 都已用完");
                }
            } catch (Exception e) {
                System.out.println("[DEBUG] ✗ Massive.com 请求失败：" + e.getMessage());
            }
        }
        
        System.out.println("[DEBUG] ✗ Massive.com 未找到有效价格");
        System.out.println("============================================\n");
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
        
        // 处理多种输入格式：600519, SH600519, sh600519, 000001, SZ000001 等
        String cleanTicker = ticker.trim().toUpperCase();
        
        // 如果已经包含前缀，提取纯数字部分
        if (cleanTicker.matches("^(SH|SZ|HK)\\d{4,6}$")) {
            String prefix = cleanTicker.substring(0, 2).toLowerCase();
            String digits = cleanTicker.substring(2);
            
            if (digits.length() == 6) {
                // A 股（6 位数字）
                symbol = prefix + digits;
                System.out.println("[DEBUG] 识别为带前缀的 A 股/港股代码，标准化为：" + symbol);
            } else if (digits.length() >= 4 && digits.length() <= 5) {
                // 港股（4-5 位数字）
                symbol = "hk" + digits;
                System.out.println("[DEBUG] 识别为港股代码，标准化为：" + symbol);
            }
        } else if (cleanTicker.matches("\\d{6}")) {
            // 纯 6 位数字 - A 股
            String prefix = cleanTicker.startsWith("6") || cleanTicker.startsWith("9") ? "sh" : "sz";
            symbol = prefix + cleanTicker;
            System.out.println("[DEBUG] 识别为 A 股（纯 6 位数字），添加前缀：" + prefix + "，最终：" + symbol);
        } else if (cleanTicker.matches("\\d{4,5}")) {
            // 纯 4-5 位数字 - 港股
            symbol = "hk" + cleanTicker;
            System.out.println("[DEBUG] 识别为港股（纯 4-5 位数字），添加前缀：hk，最终：" + symbol);
        } else {
            System.out.println("[DEBUG] 使用原始 ticker（可能是美股或其他）: " + symbol);
        }
        
        // 正确的新浪财经 URL 格式：http://hq.sinajs.cn/list=sz000001
        String url = base + "/list=" + symbol;
        System.out.println("[DEBUG] 最终 Symbol: " + symbol);
        System.out.println("[DEBUG] 完整 URL: " + url);
        System.out.println("============================================\n");
        
        try {
            System.out.println("[DEBUG] 发送 HTTP GET 请求...");
            System.out.println("[DEBUG] 设置请求头...");
            System.out.println("  - User-Agent: Mozilla/5.0 ");
            System.out.println("  - Referer: https://finance.sina.com.cn/");
            System.out.println("  - Accept: */*");
            
            // 新浪财经需要 User-Agent 和 Referer，否则返回 403 Forbidden
            String body = restClient.get()
                .uri(url)
                .header("User-Agent", "Mozilla/5.0 ")
                .header("Referer", "https://finance.sina.com.cn/")
                .header("Accept", "*/*")
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
                        System.out.println("[DEBUG] 可能原因：");
                        System.out.println("  1. 股票代码不存在或已退市");
                        System.out.println("  2. 股票代码格式不正确");
                        System.out.println("  3. API 请求过于频繁被限流");
                    }
                } else {
                    System.out.println("[DEBUG] ✗ 新浪财经响应中没有找到引号包裹的数据");
                }
            } else {
                System.out.println("[DEBUG] ✗ 新浪财经返回空响应");
                System.out.println("[DEBUG] 可能原因：");
                System.out.println("  1. 股票代码格式错误（应为 sh600519 或 sz000001）");
                System.out.println("  2. 该股票不存在或已退市");
                System.out.println("  3. 网络问题或 API 限流");
            }
        } catch (Exception e) {
            System.out.println("[DEBUG] ✗ 新浪财经请求失败：" + e.getMessage());
            System.out.println("[DEBUG] 错误堆栈：");
            e.printStackTrace();
        }
        System.out.println("============================================\n");
        return Optional.empty();
    }

    /** 获取 Massive.com 前一天收盘价 */
    private Optional<Double> fetchMassivePreviousClose(String ticker) {
        String base = appProperties.getPricing().getMassiveBase();
        LocalDate yesterday = LocalDate.now().minusDays(2);
        
        // 构建所有API密钥列表（主密钥 + 备用密钥）
        List<String> apiKeys = new ArrayList<>();
        apiKeys.add(appProperties.getPricing().getMassiveApiKey());
        apiKeys.addAll(appProperties.getPricing().getMassiveApiKeyFallbacks());
        
        System.out.println("\n========== [MASSIVE 前一天收盘价请求] ==========");
        System.out.println("[DEBUG] 尝试从 Massive.com 获取 " + ticker + " 的前一天收盘价...");
        System.out.println("[DEBUG] 查询日期：" + yesterday);
        System.out.println("[DEBUG] 可用 API Key 数量：" + apiKeys.size());
        System.out.println("============================================\n");
        
        for (int i = 0; i < apiKeys.size(); i++) {
            String apiKey = apiKeys.get(i);
            String url = base + "/open-close/" + ticker + "/" + yesterday + "?adjusted=true&apiKey=" + apiKey;
            
            System.out.println("[DEBUG] 尝试使用 API Key #" + (i + 1) + ": " + apiKey.substring(0, Math.min(8, apiKey.length())) + "...");
            
            try {
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
                    continue;
                }
                
                JsonNode root = objectMapper.readTree(body);
                
                // 解析 Massive v1 API 响应
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
                }
                
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
                System.out.println("[DEBUG] ✗ Massive.com 404 错误：该股票不存在或数据不可用");
                return Optional.empty();
            } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
                System.out.println("[DEBUG] ✗ Massive.com 429 错误：请求频率超限，尝试下一个 API Key...");
                if (i < apiKeys.size() - 1) {
                    System.out.println("[DEBUG] 切换到备用 API Key #" + (i + 2));
                    continue;
                } else {
                    System.out.println("[DEBUG] ✗ 所有 API Key 都已用完，返回空");
                }
            } catch (Exception e) {
                System.out.println("[DEBUG] ✗ Massive.com 请求失败：" + e.getMessage());
            }
        }
        
        System.out.println("============================================\n");
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
        
        // 处理多种输入格式：600519, SH600519, sh600519, 000001, SZ000001 等
        String cleanTicker = ticker.trim().toUpperCase();
        
        // 如果已经包含前缀，提取纯数字部分
        if (cleanTicker.matches("^(SH|SZ|HK)\\d{4,6}$")) {
            String prefix = cleanTicker.substring(0, 2).toLowerCase();
            String digits = cleanTicker.substring(2);
            
            if (digits.length() == 6) {
                // A 股（6 位数字）
                symbol = prefix + digits;
                System.out.println("[DEBUG] 识别为带前缀的 A 股/港股代码，标准化为：" + symbol);
            } else if (digits.length() >= 4 && digits.length() <= 5) {
                // 港股（4-5 位数字）
                symbol = "hk" + digits;
                System.out.println("[DEBUG] 识别为港股代码，标准化为：" + symbol);
            }
        } else if (cleanTicker.matches("\\d{6}")) {
            // 纯 6 位数字 - A 股
            String prefix = cleanTicker.startsWith("6") || cleanTicker.startsWith("9") ? "sh" : "sz";
            symbol = prefix + cleanTicker;
            System.out.println("[DEBUG] 识别为 A 股（纯 6 位数字），添加前缀：" + prefix + "，最终：" + symbol);
        } else if (cleanTicker.matches("\\d{4,5}")) {
            // 纯 4-5 位数字 - 港股
            symbol = "hk" + cleanTicker;
            System.out.println("[DEBUG] 识别为港股（纯 4-5 位数字），添加前缀：hk，最终：" + symbol);
        } else {
            System.out.println("[DEBUG] 使用原始 ticker（可能是美股或其他）: " + symbol);
        }
        
        // 新浪财经 URL 格式：http://hq.sinajs.cn/list=sz000001
        String url = base + "/list=" + symbol;
        System.out.println("[DEBUG] 最终 Symbol: " + symbol);
        System.out.println("[DEBUG] 完整 URL: " + url);
        System.out.println("============================================\n");
        
        try {
            System.out.println("[DEBUG] 发送 HTTP GET 请求...");
            System.out.println("[DEBUG] 设置请求头...");
            System.out.println("  - User-Agent: Mozilla/5.0");
            System.out.println("  - Referer: https://finance.sina.com.cn/");
            System.out.println("  - Accept: */*");
            
            // 新浪财经需要 User-Agent 和 Referer，否则返回 403 Forbidden
            String body = restClient.get()
                .uri(url)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://finance.sina.com.cn/")
                .header("Accept", "*/*")
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
                        System.out.println("[DEBUG] 可能原因：");
                        System.out.println("  1. 股票代码不存在或已退市");
                        System.out.println("  2. 股票代码格式不正确");
                        System.out.println("  3. API 请求过于频繁被限流");
                    }
                } else {
                    System.out.println("[DEBUG] ✗ 新浪财经响应中没有找到引号包裹的数据");
                }
            } else {
                System.out.println("[DEBUG] ✗ 新浪财经返回空响应");
                System.out.println("[DEBUG] 可能原因：");
                System.out.println("  1. 股票代码格式错误（应为 sh600519 或 sz000001）");
                System.out.println("  2. 该股票不存在或已退市");
                System.out.println("  3. 网络问题或 API 限流");
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

    // ========== 汇率换算功能 ==========

    /**
     * 获取汇率（从 fromCurrency 到 toCurrency）
     * 优先使用固定汇率配置，简化实现
     */
    public double getExchangeRate(String fromCurrency, String toCurrency) {
        if (fromCurrency == null || toCurrency == null || fromCurrency.equalsIgnoreCase(toCurrency)) {
            return 1.0;
        }
        String from = fromCurrency.toUpperCase();
        String to = toCurrency.toUpperCase();
        
        // 使用配置中的固定汇率
        AppProperties.ExchangeRate rates = appProperties.getExchangeRate();
        return switch (from + "_" + to) {
            case "USD_CNY" -> rates.getUsdToCny();
            case "USD_EUR" -> rates.getUsdToEur();
            case "CNY_USD" -> rates.getCnyToUsd();
            case "CNY_EUR" -> rates.getCnyToEur();
            case "EUR_USD" -> rates.getEurToUsd();
            case "EUR_CNY" -> rates.getEurToCny();
            // 港币汇率（从配置读取）
            case "HKD_CNY" -> rates.getHkdToCny();
            case "CNY_HKD" -> rates.getCnyToHkd();
            case "HKD_USD" -> rates.getHkdToUsd();
            case "USD_HKD" -> rates.getUsdToHkd();
            default -> {
                System.out.println("[DEBUG] ⚠ 未找到汇率配置：" + from + " -> " + to + "，返回 1.0");
                yield 1.0;  // 不支持的货币对，直接返回 1.0，避免无限递归
            }
        };
    }

    /**
     * 将价格从数据源货币转换为目标货币
     * @param price 原始价格
     * @param sourceCurrency 数据源货币（USD/CNY等）
     * @param targetCurrency 目标货币
     */
    public double convertCurrency(double price, String sourceCurrency, String targetCurrency) {
        if (sourceCurrency == null || targetCurrency == null || 
            sourceCurrency.equalsIgnoreCase(targetCurrency)) {
            return price;
        }
        double rate = getExchangeRate(sourceCurrency, targetCurrency);
        return round2(price * rate);
    }

    /**
     * 根据数据源判断货币类型
     * Massive/Alpha Vantage -> USD
     * 新浪财经 -> CNY
     */
    public String detectCurrencyBySource(String provider) {
        return switch (provider.trim().toLowerCase()) {
            case "sina" -> "CNY";
            case "massive", "alpha-vantage", "cached" -> "USD";
            default -> "USD";
        };
    }

    /**
     * 带货币换算的价格查询（用于基金、股票、债券）
     * @param assetType 资产类型
     * @param ticker 代码
     * @param targetCurrency 目标货币（组合本币）
     * @return 换算后的价格
     */
    public Optional<Double> priceForHoldingWithCurrency(AssetType assetType, String ticker, String targetCurrency) {
        // cash 直接返回 1.0，不需要换算
        if (assetType == AssetType.cash) {
            return Optional.of(1.0);
        }
        
        Optional<Double> priceOpt = priceForHolding(assetType, ticker);
        if (priceOpt.isEmpty() || targetCurrency == null) {
            return priceOpt;
        }
        
        double originalPrice = priceOpt.get();
        
        // 根据成功查询的数据源确定原始货币
        // 简化处理：尝试查询并记录哪个数据源成功
        String sourceCurrency = detectSourceCurrency(ticker);
        
        if (sourceCurrency.equalsIgnoreCase(targetCurrency)) {
            return Optional.of(originalPrice);
        }
        
        double convertedPrice = convertCurrency(originalPrice, sourceCurrency, targetCurrency);
        System.out.println("\n========== [货币换算] ==========");
        System.out.println("[DEBUG] 原始价格: " + originalPrice + " " + sourceCurrency);
        System.out.println("[DEBUG] 目标货币: " + targetCurrency);
        System.out.println("[DEBUG] 汇率: " + getExchangeRate(sourceCurrency, targetCurrency));
        System.out.println("[DEBUG] 换算后: " + convertedPrice + " " + targetCurrency);
        System.out.println("================================\n");
        
        return Optional.of(convertedPrice);
    }

    /**
     * 检测股票代码所属市场，返回对应货币
     */
    public String detectSourceCurrency(String ticker) {
        String t = ticker.toUpperCase();
        // A股代码特征：6位数字，sh/sz开头
        if (t.matches("^\\d{6}$") || t.matches("^(SH|SZ)\\d{6}$")) {
            return "CNY";
        }
        // 港股代码特征：4-5位数字，hk开头
        if (t.matches("^\\d{4,5}$") || t.matches("^HK\\d{4,5}$")) {
            return "HKD"; // 港股用港币，但新浪财经返回的人民币价格
        }
        // 默认美股等用 USD
        return "USD";
    }

    // ========== 历史价格查询（用于填充成交价） ==========

    /**
     * 查询指定日期的历史收盘价（优先使用 Massive API）
     * URL格式: https://api.massive.com/v2/aggs/ticker/{ticker}/range/1/year/{startDate}/{endDate}?adjusted=true&sort=asc&limit=120&apiKey={apiKey}
     * @param ticker 股票代码
     * @param date 日期
     * @return 该日期的收盘价
     */
    public Optional<Double> fetchHistoricalPrice(String ticker, LocalDate date) {
        if (ticker == null || ticker.isBlank() || date == null) {
            return Optional.empty();
        }
        
        String t = ticker.trim().toUpperCase();
        System.out.println("\n========== [历史价格查询 - Massive API] ==========");
        System.out.println("[DEBUG] 股票代码: " + t);
        System.out.println("[DEBUG] 查询日期: " + date);
        
        // 优先使用 Massive API 查询历史价格
        Optional<Double> massivePrice = fetchMassiveHistoricalPrice(t, date);
        if (massivePrice.isPresent()) {
            return massivePrice;
        }
        
        // Massive 失败，回退到 cachedDailyCloseSeries
        System.out.println("[DEBUG] Massive API 查询失败，回退到缓存数据源");
        return fetchCachedHistoricalPrice(t, date);
    }
    
    // 缓存历史价格查询结果 (ticker:date -> price)
    private final ConcurrentHashMap<String, CacheEntry<Double>> historicalPriceCache = new ConcurrentHashMap<>();
    private static final long HISTORICAL_PRICE_CACHE_TTL_MS = 30 * 60 * 1000L; // 30分钟

    /**
     * 使用 Massive API 查询指定日期的历史价格
     * URL格式: /v1/open-close/{ticker}/{date}?adjusted=true&apiKey={apiKey}
     * 带缓存机制，减少API调用次数
     */
    private Optional<Double> fetchMassiveHistoricalPrice(String ticker, LocalDate date) {
        // Massive v1 `open-close` 对非交易日会返回 404。
        // 为了让买入日基准在周末/节假日仍可用，这里向前回退到最近交易日。
        int maxBackDays = 10;
        
        // 构建所有API密钥列表（主密钥 + 备用密钥）
        List<String> apiKeys = new ArrayList<>();
        apiKeys.add(appProperties.getPricing().getMassiveApiKey());
        apiKeys.addAll(appProperties.getPricing().getMassiveApiKeyFallbacks());

        for (int i = 0; i <= maxBackDays; i++) {
            LocalDate queryDate = date.minusDays(i);
            String cacheKey = ticker + ":" + date.toString();

            // 检查缓存（按原始 date 缓存，保证“买入日基准”一致）
            CacheEntry<Double> cached = historicalPriceCache.get(cacheKey);
            if (cached != null && isFresh(cached.createdAtMs, HISTORICAL_PRICE_CACHE_TTL_MS)) {
                return Optional.of(cached.value);
            }

            String base = appProperties.getPricing().getMassiveBase();

            // 尝试所有API密钥
            for (int keyIndex = 0; keyIndex < apiKeys.size(); keyIndex++) {
                String apiKey = apiKeys.get(keyIndex);
                // URL格式: /v1/open-close/{ticker}/{date}
                String url = base + "/open-close/" + ticker + "/" + queryDate
                        + "?adjusted=true&apiKey=" + apiKey;

                System.out.println("[DEBUG] Massive API URL (Key #" + (keyIndex + 1) + "): " + url);

                try {
                    String body = restClient.get()
                            .uri(url)
                            .header("accept", "application/json")
                            .retrieve()
                            .body(String.class);

                    if (body == null || body.isBlank()) {
                        continue;
                    }

                    JsonNode root = objectMapper.readTree(body);

                    if (root.has("close")) {
                        double closePrice = root.get("close").asDouble();
                        String fromDate = root.has("from") ? root.get("from").asText() : queryDate.toString();

                        // 存入缓存：用“原始买入日 date”作为 key
                        historicalPriceCache.put(cacheKey, new CacheEntry<>(closePrice, System.currentTimeMillis()));

                        System.out.println("[DEBUG] ✓ 从 Massive API 找到历史价格: " + closePrice + " (" + fromDate + ")，已缓存30分钟");
                        System.out.println("================================================\n");
                        return Optional.of(closePrice);
                    }

                    System.out.println("[DEBUG] Massive API 响应中未找到 close 字段");
                } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
                    // 非交易日：继续向前回退
                    System.out.println("[DEBUG] Massive API 对该日期无数据（404），回退到前一交易日。尝试日期: " + queryDate);
                    break; // 跳出key循环，继续日期回退
                } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
                    System.out.println("[DEBUG] ✗ Massive API 429 错误：请求频率超限，尝试下一个 API Key...");
                    if (keyIndex < apiKeys.size() - 1) {
                        System.out.println("[DEBUG] 切换到备用 API Key #" + (keyIndex + 2));
                        continue;
                    } else {
                        System.out.println("[DEBUG] ✗ 所有 API Key 都已用完，继续日期回退");
                    }
                } catch (Exception e) {
                    System.out.println("[DEBUG] Massive API 请求失败: " + e.getMessage());
                    break;
                }
            }
        }

        return Optional.empty();
    }
    
    /**
     * 使用缓存数据源查询历史价格（回退方案）
     */
    private Optional<Double> fetchCachedHistoricalPrice(String ticker, LocalDate date) {
        System.out.println("\n========== [历史价格查询 - 缓存数据源] ==========");
        
        // 计算需要查询多少天的数据才能覆盖到目标日期
        int daysBack = (int) java.time.temporal.ChronoUnit.DAYS.between(date, LocalDate.now()) + 5;
        daysBack = Math.max(daysBack, 30); // 至少查询30天
        
        // 使用 cachedDailyCloseSeries 获取历史数据
        List<TimePrice> series = fetchCachedDailyCloseSeries(ticker, daysBack);
        
        // 查找目标日期的价格
        TimePrice candidate = null;
        for (TimePrice tp : series) {
            LocalDate priceDate = tp.day().atZone(ZoneOffset.UTC).toLocalDate();
            // 买入日基准：优先使用 <= date 的最近交易日
            if ((priceDate.isEqual(date) || priceDate.isBefore(date))) {
                candidate = tp;
            } else {
                // series 按天升序时，超过 date 后就可以停止
                break;
            }
        }

        if (candidate != null) {
            LocalDate candidateDate = candidate.day().atZone(ZoneOffset.UTC).toLocalDate();
            if (candidateDate.equals(date)) {
                System.out.println("[DEBUG] ✓ 从缓存找到历史价格: " + candidate.price() + " (" + date + ")");
            } else {
                System.out.println("[DEBUG] ⚠ 未找到 " + date + " 的精确价格，使用最近日期 " + candidateDate + " 的价格: " + candidate.price());
            }
            System.out.println("===============================================\n");
            return Optional.of(candidate.price());
        }

        // 如果缓存数据都比 date 更新（很少见），则返回第一个可用点
        if (!series.isEmpty()) {
            TimePrice first = series.get(0);
            LocalDate firstDate = first.day().atZone(ZoneOffset.UTC).toLocalDate();
            System.out.println("[DEBUG] ⚠ 缓存中没有 <= " + date + " 的数据，使用最早日期 " + firstDate + " 的价格: " + first.price());
            System.out.println("===============================================\n");
            return Optional.of(first.price());
        }
        
        System.out.println("[DEBUG] ✗ 未找到历史价格数据");
        System.out.println("===============================================\n");
        return Optional.empty();
    }

    /**
     * 带货币换算的历史价格查询
     */
    public Optional<Double> fetchHistoricalPriceWithCurrency(String ticker, LocalDate date, String targetCurrency) {
        Optional<Double> priceOpt = fetchHistoricalPrice(ticker, date);
        if (priceOpt.isEmpty() || targetCurrency == null) {
            return priceOpt;
        }
        
        double originalPrice = priceOpt.get();
        String sourceCurrency = detectSourceCurrency(ticker);
        
        if (sourceCurrency.equalsIgnoreCase(targetCurrency)) {
            return Optional.of(originalPrice);
        }
        
        double convertedPrice = convertCurrency(originalPrice, sourceCurrency, targetCurrency);
        System.out.println("\n========== [历史价格货币换算] ==========");
        System.out.println("[DEBUG] 原始历史价格: " + originalPrice + " " + sourceCurrency);
        System.out.println("[DEBUG] 目标货币: " + targetCurrency);
        System.out.println("[DEBUG] 汇率: " + getExchangeRate(sourceCurrency, targetCurrency));
        System.out.println("[DEBUG] 换算后: " + convertedPrice + " " + targetCurrency);
        System.out.println("=======================================\n");
        
        return Optional.of(convertedPrice);
    }

    // 缓存股票历史走势数据 (ticker:from:to -> List<TimePrice>)
    private final ConcurrentHashMap<String, CacheEntry<List<TimePrice>>> historicalSeriesCache = new ConcurrentHashMap<>();
    private static final long HISTORICAL_SERIES_CACHE_TTL_MS = 60 * 60 * 1000L; // 1小时

    /**
     * 获取股票历史价格数据（用于图表展示）
     * 使用 Massive API v2: /v2/aggs/ticker/{ticker}/range/1/day/{from}/{to}
     * 带缓存机制，减少API调用次数
     *
     * @param ticker 股票代码
     * @param from 开始日期
     * @param to 结束日期
     * @return 历史价格数据列表 (date, price)
     */
    public List<TimePrice> fetchStockHistoricalSeries(String ticker, LocalDate from, LocalDate to) {
        String t = ticker.toUpperCase();
        String cacheKey = String.format("%s:%s:%s", t, from.toString(), to.toString());
        
        // 检查缓存
        CacheEntry<List<TimePrice>> cached = historicalSeriesCache.get(cacheKey);
        if (cached != null && isFresh(cached.createdAtMs, HISTORICAL_SERIES_CACHE_TTL_MS)) {
            System.out.println("\n========== [股票历史数据查询 - 缓存命中] ==========");
            System.out.println("[DEBUG] 股票: " + t);
            System.out.println("[DEBUG] 日期范围: " + from + " 至 " + to);
            System.out.println("[DEBUG] ✓ 使用缓存数据，共 " + cached.value.size() + " 条");
            System.out.println("=======================================================\n");
            return cached.value;
        }
        
        // 构建所有API密钥列表（主密钥 + 备用密钥）
        List<String> apiKeys = new ArrayList<>();
        apiKeys.add(appProperties.getPricing().getMassiveApiKey());
        apiKeys.addAll(appProperties.getPricing().getMassiveApiKeyFallbacks());

        System.out.println("\n========== [股票历史数据查询 - Massive API v2] ==========");
        System.out.println("[DEBUG] 股票: " + t);
        System.out.println("[DEBUG] 日期范围: " + from + " 至 " + to);
        System.out.println("[DEBUG] 可用 API Key 数量：" + apiKeys.size());
        System.out.println("=======================================================\n");

        // 尝试所有API密钥
        for (int keyIndex = 0; keyIndex < apiKeys.size(); keyIndex++) {
            String apiKey = apiKeys.get(keyIndex);
            // Massive API v2 格式: /v2/aggs/ticker/AAPL/range/1/day/2025-11-03/2025-11-28
            String url = String.format(
                "https://api.massive.com/v2/aggs/ticker/%s/range/1/day/%s/%s?adjusted=true&sort=asc&limit=500&apiKey=%s",
                t, from.toString(), to.toString(), apiKey
            );

            System.out.println("[DEBUG] 尝试使用 API Key #" + (keyIndex + 1) + ": " + apiKey.substring(0, Math.min(8, apiKey.length())) + "...");

            try {
                String body = restClient.get()
                    .uri(url)
                    .header("accept", "application/json")
                    .retrieve()
                    .body(String.class);

                if (body == null || body.isBlank()) {
                    System.out.println("[DEBUG] ✗ Massive API 返回空响应");
                    continue;
                }

            JsonNode root = objectMapper.readTree(body);

            // 解析 Massive API v2 响应
            // 格式: {"ticker": "AAPL", "resultsCount": 20, "results": [{"v": 70790813, "vw": 131.5, "o": 130.47, "c": 130.73, "h": 133.41, "l": 129.95, "t": 1673270400000, "n": 12345}, ...]}
            if (root.has("results") && root.get("results").isArray()) {
                JsonNode results = root.get("results");
                List<TimePrice> series = new ArrayList<>();

                for (JsonNode result : results) {
                    if (result.has("c") && result.has("t")) {
                        double closePrice = result.get("c").asDouble();
                        long timestamp = result.get("t").asLong();
                        // timestamp 是毫秒级 Unix 时间戳
                        Instant instant = Instant.ofEpochMilli(timestamp);
                        series.add(new TimePrice(instant, closePrice));
                    }
                }

                // 存入缓存
                historicalSeriesCache.put(cacheKey, new CacheEntry<>(series, System.currentTimeMillis()));
                
                System.out.println("[DEBUG] ✓ 获取到 " + series.size() + " 条历史数据，已缓存1小时");
                System.out.println("=======================================================\n");
                return series;
            } else {
                System.out.println("[DEBUG] ✗ 响应中没有 results 字段或为空");
                if (root.has("error")) {
                    System.out.println("[DEBUG] 错误信息: " + root.get("error").asText());
                }
            }
            } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
                System.out.println("[DEBUG] ✗ Massive API 429 错误：请求频率超限，尝试下一个 API Key...");
                if (keyIndex < apiKeys.size() - 1) {
                    System.out.println("[DEBUG] 切换到备用 API Key #" + (keyIndex + 2));
                    continue;
                } else {
                    System.out.println("[DEBUG] ✗ 所有 API Key 都已用完");
                    // 所有key都用完了，尝试返回过期缓存
                    if (cached != null) {
                        System.out.println("[DEBUG] ⚠ 返回过期缓存数据作为降级方案");
                        return cached.value;
                    }
                }
            } catch (org.springframework.web.client.HttpClientErrorException.BadRequest e) {
                System.out.println("[DEBUG] ✗ Massive API 400 错误：请求参数错误");
                System.out.println("[DEBUG] 可能原因：日期范围无效或股票代码不存在");
                break; // 400错误不需要尝试其他key
            } catch (Exception e) {
                System.out.println("[DEBUG] ✗ Massive API 请求失败: " + e.getMessage());
                break; // 其他错误不需要尝试其他key
            }
        }

        System.out.println("=======================================================\n");
        return List.of();
    }
}
