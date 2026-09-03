package io.github.adarko22.customeractivityanalytics.analytics.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record AnalyticsBucketDto(
    Instant bucketStart, long transactionCount, Map<String, BigDecimal> amountByCurrency) {}
