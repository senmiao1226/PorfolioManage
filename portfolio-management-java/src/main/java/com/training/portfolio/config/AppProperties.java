package com.training.portfolio.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Cors cors = new Cors();
    private Pricing pricing = new Pricing();
    private ExchangeRate exchangeRate = new ExchangeRate();

    @Data
    public static class Cors {
        /** 逗号分隔，如 http://localhost:5173 */
        private String allowedOrigins = "http://localhost:5173";
    }

    @Data
    public static class Pricing {
        private String cachedPriceBase =
                "https://c4rm9elh30.execute-api.us-east-1.amazonaws.com/default/cachedPriceData";
        private String massiveBase = "https://api.massive.com/v2";
        private String massiveApiKey = "YOUR_MASSIVE_API_KEY";
        // 备用API密钥列表（当主密钥触发429错误时使用）
        private List<String> massiveApiKeyFallbacks = List.of();
        private String alphaVantageBase = "https://www.alphavantage.co/query";
        private String alphaVantageApiKey = "demo";
        private String sinaBase = "https://hq.sinajs.cn";
        private String providers = "massive,alphavantage,sina,cached";
        // 汇率API配置（使用Alpha Vantage的外汇接口）
        private String exchangeRateApiKey = "demo";
    }

    @Data
    public static class ExchangeRate {
        // 固定汇率配置（当 API 不可用时使用）
        private double usdToCny = 7.2;
        private double usdToEur = 0.92;
        private double cnyToUsd = 0.139;
        private double cnyToEur = 0.128;
        private double eurToUsd = 1.09;
        private double eurToCny = 7.83;
        // 港币汇率（港币联系汇率制）
        private double hkdToCny = 0.92;
        private double cnyToHkd = 1.09;
        private double hkdToUsd = 0.13;
        private double usdToHkd = 7.80;
    }
}
