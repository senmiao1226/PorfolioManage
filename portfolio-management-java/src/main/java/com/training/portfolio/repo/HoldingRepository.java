package com.training.portfolio.repo;

import com.training.portfolio.domain.Holding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findByPortfolioIdOrderById(Long portfolioId);
}
