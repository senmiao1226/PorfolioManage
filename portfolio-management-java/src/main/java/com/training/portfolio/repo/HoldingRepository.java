package com.training.portfolio.repo;

import com.training.portfolio.domain.Holding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findByPortfolioIdOrderById(Long portfolioId);

    /**
     * 查询指定组合中唯一的股票代码列表（去重）
     *
     * @param portfolioId 组合ID
     * @return 股票代码列表
     */
    @Query("SELECT DISTINCT h.ticker FROM Holding h " +
           "WHERE h.portfolio.id = :portfolioId " +
           "AND h.assetType != 'cash' " +
           "AND h.ticker IS NOT NULL " +
           "AND h.ticker != ''")
    List<String> findDistinctTickersByPortfolioId(@Param("portfolioId") Long portfolioId);

    /**
     * 查询所有持仓中唯一的股票代码列表（全局去重）
     *
     * @return 股票代码列表
     */
    @Query("SELECT DISTINCT h.ticker FROM Holding h " +
           "WHERE h.assetType != 'cash' " +
           "AND h.ticker IS NOT NULL " +
           "AND h.ticker != ''")
    List<String> findAllDistinctTickers();

    /**
     * 查询指定组合中指定股票的持仓列表
     *
     * @param portfolioId 组合ID
     * @param ticker 股票代码
     * @return 持仓列表
     */
    List<Holding> findByPortfolioIdAndTicker(Long portfolioId, String ticker);

    /**
     * 统计指定组合中指定股票的总持仓数量
     *
     * @param portfolioId 组合ID
     * @param ticker 股票代码
     * @return 总持仓数量
     */
    @Query("SELECT COALESCE(SUM(h.quantity), 0) FROM Holding h " +
           "WHERE h.portfolio.id = :portfolioId " +
           "AND h.ticker = :ticker")
    Double sumQuantityByPortfolioIdAndTicker(@Param("portfolioId") Long portfolioId, 
                                              @Param("ticker") String ticker);
    
    /**
     * 查询所有持仓（带组合信息，避免懒加载问题）
     */
    @Query("SELECT DISTINCT h FROM Holding h JOIN FETCH h.portfolio " +
           "WHERE h.assetType != 'cash' " +
           "AND h.ticker IS NOT NULL " +
           "AND h.ticker != ''")
    List<Holding> findAllWithPortfolio();
    
    /**
     * 查询所有持仓（包含现金，用于调试）
     */
    @Query("SELECT h FROM Holding h JOIN FETCH h.portfolio ORDER BY h.id")
    List<Holding> findAllWithPortfolioIncludingCash();
}
