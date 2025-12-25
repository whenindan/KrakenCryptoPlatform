package com.cryptoplatform.api.model;

import java.math.BigDecimal;

public record Ticker(
    String symbol,
    long tsEpochMs,
    BigDecimal bid,
    BigDecimal ask,
    BigDecimal last,
    BigDecimal volume24h,
    BigDecimal change24h
) {}
