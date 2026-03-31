package com.training.portfolio.web;

import com.training.portfolio.dto.PortfolioDtos;
import com.training.portfolio.service.PortfolioService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/portfolios")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping
    public List<PortfolioDtos.PortfolioResponse> list() {
        return portfolioService.listPortfolios();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioDtos.PortfolioResponse create(@RequestBody PortfolioDtos.PortfolioCreateRequest body) {
        return portfolioService.createPortfolio(body);
    }

    @GetMapping("/{id}")
    public PortfolioDtos.PortfolioDetailResponse get(@PathVariable Long id) {
        return portfolioService.getPortfolio(id);
    }

    @PatchMapping("/{id}")
    public PortfolioDtos.PortfolioResponse update(
            @PathVariable Long id, @RequestBody PortfolioDtos.PortfolioUpdateRequest body) {
        return portfolioService.updatePortfolio(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        portfolioService.deletePortfolio(id);
    }

    @PostMapping("/{id}/holdings")
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioDtos.HoldingResponse addHolding(
            @PathVariable Long id, @RequestBody PortfolioDtos.HoldingCreateRequest body) {
        return portfolioService.addHolding(id, body);
    }

    @GetMapping("/{id}/summary")
    public PortfolioDtos.PortfolioSummaryResponse summary(@PathVariable Long id) {
        return portfolioService.getSummary(id);
    }

    @GetMapping("/{id}/performance")
    public PortfolioDtos.PerformanceSeriesResponse performance(
            @PathVariable Long id, @RequestParam(defaultValue = "30") int days) {
        return portfolioService.getPerformance(id, days);
    }
}
