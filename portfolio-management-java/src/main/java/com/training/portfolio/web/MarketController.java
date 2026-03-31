package com.training.portfolio.web;

import com.training.portfolio.dto.MarketDtos;
import com.training.portfolio.service.MarketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
     * 获取数据源提供商列表
     *
     * @return 提供商列表
     */
    @GetMapping("/providers")
    public ResponseEntity<List<MarketDtos.DataProviderDto>> getDataProviders() {
        List<MarketDtos.DataProviderDto> providers = marketService.getDataProviders();
        return ResponseEntity.ok(providers);
    }
}
