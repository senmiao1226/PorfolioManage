package com.training.portfolio.web;

import com.training.portfolio.dto.PortfolioDtos;
import com.training.portfolio.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolios/holdings")
@RequiredArgsConstructor
public class HoldingController {

    private final PortfolioService portfolioService;

    @PatchMapping("/{holdingId}")
    public PortfolioDtos.HoldingResponse update(
            @PathVariable Long holdingId, @RequestBody PortfolioDtos.HoldingUpdateRequest body) {
        return portfolioService.updateHolding(holdingId, body);
    }

    @DeleteMapping("/{holdingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long holdingId) {
        portfolioService.deleteHolding(holdingId);
    }
}
