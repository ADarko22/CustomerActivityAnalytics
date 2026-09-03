package io.github.adarko22.customeractivityanalytics.analytics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.adarko22.customeractivityanalytics.config.SecurityConfig;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalyticsConfigController.class)
@Import({SecurityConfig.class, AnalyticsConfigControllerTest.TestConfig.class})
class AnalyticsConfigControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void returnsConfiguredRangeConstraints() throws Exception {
    mockMvc
        .perform(get("/api/v1/analytics/range-constraints"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.DAY.minAmount").value(1))
        .andExpect(jsonPath("$.DAY.minUnit").value("DAYS"))
        .andExpect(jsonPath("$.DAY.maxAmount").value(1))
        .andExpect(jsonPath("$.DAY.maxUnit").value("MONTHS"))
        .andExpect(jsonPath("$.YEAR.minAmount").value(1))
        .andExpect(jsonPath("$.YEAR.maxAmount").value(5))
        .andExpect(jsonPath("$.YEAR.maxUnit").value("YEARS"));
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    AnalyticsRangeProperties analyticsRangeProperties() {
      return new AnalyticsRangeProperties(
          Map.of(
              Granularity.DAY,
                  new AnalyticsRangeProperties.Bound(1, ChronoUnit.DAYS, 1, ChronoUnit.MONTHS),
              Granularity.WEEK,
                  new AnalyticsRangeProperties.Bound(1, ChronoUnit.WEEKS, 30, ChronoUnit.WEEKS),
              Granularity.MONTH,
                  new AnalyticsRangeProperties.Bound(1, ChronoUnit.MONTHS, 2, ChronoUnit.YEARS),
              Granularity.YEAR,
                  new AnalyticsRangeProperties.Bound(1, ChronoUnit.YEARS, 5, ChronoUnit.YEARS)));
    }
  }
}
