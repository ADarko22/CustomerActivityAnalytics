package io.github.adarko22.customeractivityanalytics.analytics.dto;

import io.github.adarko22.customeractivityanalytics.analytics.Granularity;
import io.github.adarko22.customeractivityanalytics.transaction.ActivityType;
import java.time.Instant;
import java.util.List;

public record AnalyticsTimeSeriesDto(
    ActivityType activityType,
    Granularity granularity,
    Instant from,
    Instant to,
    List<AnalyticsBucketDto> buckets) {}
