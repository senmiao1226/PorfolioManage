package com.training.portfolio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Cors cors = new Cors();
    private Pricing pricing = new Pricing();

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
        private String alphaVantageBase = "https://www.alphavantage.co/query";
        private String alphaVantageApiKey = "demo";
        private String sinaBase = "https://hq.sinajs.cn";
        private String providers = "massive,alphavantage,sina,yahoo,cached";
    }
}
