package com.cryptoplatform.marketgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.boot.context.properties.EnableConfigurationProperties(com.cryptoplatform.marketgateway.config.KrakenProperties.class)
public class MarketGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketGatewayApplication.class, args);
    }

}
