package io.github.adarko22.customeractivityanalytics.analytics;

import io.github.adarko22.customeractivityanalytics.analytics.dto.RangeConstraintDto;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the active (configurable) analytics range↔granularity constraints to the frontend. */
@RestController
public class AnalyticsConfigController {

  private final AnalyticsRangeProperties rangeProperties;

  public AnalyticsConfigController(AnalyticsRangeProperties rangeProperties) {
    this.rangeProperties = rangeProperties;
  }

  @GetMapping("/api/v1/analytics/range-constraints")
  public Map<Granularity, RangeConstraintDto> rangeConstraints() {
    return rangeProperties.bounds().entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, entry -> RangeConstraintDto.from(entry.getValue())));
  }
}
