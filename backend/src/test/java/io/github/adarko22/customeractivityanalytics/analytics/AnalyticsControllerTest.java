package io.github.adarko22.customeractivityanalytics.analytics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.adarko22.customeractivityanalytics.analytics.dto.AnalyticsBucketDto;
import io.github.adarko22.customeractivityanalytics.analytics.dto.AnalyticsTimeSeriesDto;
import io.github.adarko22.customeractivityanalytics.config.SecurityConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(AnalyticsController.class)
@Import(SecurityConfig.class)
class AnalyticsControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AnalyticsService analyticsService;

  private final UUID customerId = UUID.randomUUID();

  @Test
  void findTimeSeriesReturnsBucketedJson() throws Exception {
    AnalyticsTimeSeriesDto series =
        new AnalyticsTimeSeriesDto(
            null,
            Granularity.DAY,
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-02T00:00:00Z"),
            List.of(
                new AnalyticsBucketDto(
                    Instant.parse("2026-01-01T00:00:00Z"),
                    2,
                    Map.of("EUR", new BigDecimal("15.00")))));
    when(analyticsService.findTimeSeries(
            eq(customerId), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(series);

    mockMvc
        .perform(
            get("/api/v1/customers/{customerId}/analytics", customerId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.granularity").value("DAY"))
        .andExpect(jsonPath("$.buckets[0].transactionCount").value(2))
        .andExpect(jsonPath("$.buckets[0].amountByCurrency.EUR").value(15.00));
  }

  @Test
  void returns400WhenRangeInvalidForGranularity() throws Exception {
    when(analyticsService.findTimeSeries(
            eq(customerId), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid range"));

    mockMvc
        .perform(
            get("/api/v1/customers/{customerId}/analytics", customerId)
                .param("granularity", "YEAR")
                .param("from", "2026-01-01T00:00:00Z")
                .param("to", "2026-02-01T00:00:00Z")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void returns404WhenCustomerMissing() throws Exception {
    when(analyticsService.findTimeSeries(
            eq(customerId), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

    mockMvc
        .perform(
            get("/api/v1/customers/{customerId}/analytics", customerId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void findTimeSeriesReturns401WhenUnauthenticated() throws Exception {
    mockMvc
        .perform(get("/api/v1/customers/{customerId}/analytics", customerId))
        .andExpect(status().isUnauthorized());
  }
}
