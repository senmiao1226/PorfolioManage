package com.training.portfolio.web;

import com.training.portfolio.dto.MarketDtos;
import com.training.portfolio.service.MarketService;
import com.training.portfolio.service.PricingService;
import com.training.portfolio.service.PricingService.TimePrice;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 市场行情控制器
 * 提供股票行情、持仓行情、市场数据等接口
 */
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MarketController {

    private final MarketService marketService;
    private final PricingService pricingService;

    /**
     * 获取指定组合的持仓股票行情
     *
     * @param portfolioId 组合ID
     * @return 持仓股票行情列表
     */
    @GetMapping("/my-holdings/{portfolioId}")
    public ResponseEntity<List<MarketDtos.MarketPriceWithHoldingDto>> getMyHoldingsMarketData(
            @PathVariable Long portfolioId) {
        List<MarketDtos.MarketPriceWithHoldingDto> data = 
                marketService.getHoldingsMarketData(portfolioId);
        return ResponseEntity.ok(data);
    }

    /**
     * 获取所有组合的持仓股票行情（去重）
     *
     * @return 全局持仓行情列表
     */
    @GetMapping("/all-holdings")
    public ResponseEntity<List<MarketDtos.MarketPriceWithHoldingDto>> getAllHoldingsMarketData() {
        List<MarketDtos.MarketPriceWithHoldingDto> data = 
                marketService.getAllHoldingsMarketData();
        return ResponseEntity.ok(data);
    }

    /**
     * 获取热门股票行情
     *
     * @return 热门股票价格列表
     */
    @GetMapping("/stocks/popular")
    public ResponseEntity<List<MarketDtos.MarketPriceDto>> getPopularStocks() {
        List<MarketDtos.MarketPriceDto> stocks = marketService.getPopularStocks();
        return ResponseEntity.ok(stocks);
    }

    /**
     * 获取市场指数行情
     *
     * @return 指数价格列表
     */
    @GetMapping("/indexes")
    public ResponseEntity<List<MarketDtos.MarketPriceDto>> getMarketIndexes() {
        List<MarketDtos.MarketPriceDto> indexes = marketService.getMarketIndexes();
        return ResponseEntity.ok(indexes);
    }

    /**
     * 搜索股票
     *
     * @param ticker 股票代码
     * @return 搜索结果
     */
    @GetMapping("/search")
    public ResponseEntity<MarketDtos.StockSearchResultDto> searchStock(
            @RequestParam String ticker) {
        MarketDtos.StockSearchResultDto result = marketService.searchStock(ticker);
        return result != null ? ResponseEntity.ok(result) : ResponseEntity.notFound().build();
    }

    /**
     * 获取个股详细信息
     *
     * @param ticker 股票代码
     * @param portfolioId 可选的组合ID（用于查询持仓信息）
     * @return 个股详情
     */
    @GetMapping("/asset/{ticker}")
    public ResponseEntity<MarketDtos.MarketDetailDto> getAssetDetail(
            @PathVariable String ticker,
            @RequestParam(required = false) Long portfolioId) {
        MarketDtos.MarketDetailDto detail = marketService.getAssetDetail(ticker, portfolioId);
        return detail != null ? ResponseEntity.ok(detail) : ResponseEntity.notFound().build();
    }

    /**
     * 获取涨幅榜（基于持仓）
     *
     * @param portfolioId 组合ID
     * @param topN 取前N个（默认5）
     * @return 涨幅排行
     */
    @GetMapping("/movers/gainers/{portfolioId}")
    public ResponseEntity<List<MarketDtos.MarketMoverDto>> getTopGainers(
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "5") int topN) {
        List<MarketDtos.MarketMoverDto> gainers = 
                marketService.getMarketMovers(portfolioId, topN, true);
        return ResponseEntity.ok(gainers);
    }

    /**
     * 获取跌幅榜（基于持仓）
     *
     * @param portfolioId 组合ID
     * @param topN 取前N个（默认5）
     * @return 跌幅排行
     */
    @GetMapping("/movers/losers/{portfolioId}")
    public ResponseEntity<List<MarketDtos.MarketMoverDto>> getTopLosers(
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "5") int topN) {
        List<MarketDtos.MarketMoverDto> losers = 
                marketService.getMarketMovers(portfolioId, topN, false);
        return ResponseEntity.ok(losers);
    }

    /**
     * 涨跌榜合并接口：一次请求返回盈利榜与亏损榜，避免前端并行打两次价
     */
    @GetMapping("/movers/{portfolioId}")
    public ResponseEntity<MarketDtos.MarketMoversBundleDto> getMarketMoversBundle(
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "5") int topN) {
        return ResponseEntity.ok(marketService.getMarketMoversBundle(portfolioId, topN));
    }

    /**
     * 获取数据源提供商列表
     *
     * @return 提供商列表
     */
    @GetMapping("/providers")
    public ResponseEntity<List<MarketDtos.DataProviderDto>> getDataProviders() {
        List<MarketDtos.DataProviderDto> providers = marketService.getDataProviders();
        return ResponseEntity.ok(providers);
    }

    /**
     * 查询历史价格（用于填充成交价）
     *
     * @param ticker 股票代码
     * @param date 日期（格式：yyyy-MM-dd）
     * @return 历史价格
     */
    @GetMapping("/historical-price")
    public ResponseEntity<Map<String, Object>> getHistoricalPrice(
            @RequestParam String ticker,
            @RequestParam String date) {
        try {
            LocalDate queryDate = LocalDate.parse(date);
            Optional<Double> price = pricingService.fetchHistoricalPrice(ticker, queryDate);
            if (price.isPresent()) {
                return ResponseEntity.ok(Map.of(
                    "ticker", ticker,
                    "date", date,
                    "price", price.get(),
                    "found", true
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "ticker", ticker,
                    "date", date,
                    "price", null,
                    "found", false,
                    "message", "未找到该日期的价格数据"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "查询失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取股票历史价格走势数据（用于图表）
     * 使用 Massive API v2 获取日K线数据
     *
     * @param ticker 股票代码
     * @param days 时间范围（30=30天, 180=半年, 365=一年）
     * @return 历史价格列表 [{date, price}]
     */
    @GetMapping("/history/{ticker}")
    public ResponseEntity<Map<String, Object>> getStockHistory(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "30") int days) {
        try {
            // 计算日期范围：结束日期为今天的前两天（确保有收盘数据）
            // 使用当前系统日期，避免年份固定导致历史窗口滞后（例如 2026 年仍查询 2024-2025）
            LocalDate to = LocalDate.now().minusDays(2);
            LocalDate from = to.minusDays(days);

            // 限制最大范围
            if (days > 365) {
                days = 365;
                from = to.minusDays(365);
            }

            List<TimePrice> series = pricingService.fetchStockHistoricalSeries(ticker, from, to);

            if (series.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("ticker", ticker.toUpperCase());
                response.put("from", from.toString());
                response.put("to", to.toString());
                response.put("days", days);
                response.put("data", List.of());
                response.put("found", false);
                response.put("message", "未找到该股票的历史数据");
                return ResponseEntity.ok(response);
            }

            // 转换为前端需要的格式
            List<Map<String, Object>> data = series.stream()
                .map(tp -> {
                    Map<String, Object> point = new HashMap<>();
                    point.put("date", tp.day().atZone(java.time.ZoneOffset.UTC).toLocalDate().toString());
                    point.put("price", tp.price());
                    return point;
                })
                .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("ticker", ticker.toUpperCase());
            response.put("from", from.toString());
            response.put("to", to.toString());
            response.put("days", days);
            response.put("data", data);
            response.put("count", data.size());
            response.put("found", true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "查询失败: " + e.getMessage()
            ));
        }
    }
}
