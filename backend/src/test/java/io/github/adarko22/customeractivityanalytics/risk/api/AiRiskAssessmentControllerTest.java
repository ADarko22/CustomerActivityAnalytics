package io.github.adarko22.customeractivityanalytics.risk.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.adarko22.customeractivityanalytics.config.SecurityConfig;
import io.github.adarko22.customeractivityanalytics.risk.dto.AiRiskAssessmentDto;
import io.github.adarko22.customeractivityanalytics.risk.engine.AiRiskAssessmentOrchestrator;
import io.github.adarko22.customeractivityanalytics.risk.engine.RiskAssessmentProperties;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskAssessmentHistoryFilters;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskLevel;
import io.github.adarko22.customeractivityanalytics.transaction.ActivityType;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionService;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionStatus;
import io.github.adarko22.customeractivityanalytics.transaction.dto.CardTransactionDto;
import io.github.adarko22.customeractivityanalytics.transaction.dto.TransactionDto;
import jakarta.servlet.AsyncEvent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(AiRiskAssessmentController.class)
@Import({SecurityConfig.class, AiRiskAssessmentControllerTest.RiskPropertiesTestConfig.class})
class AiRiskAssessmentControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TransactionService transactionService;
  @MockitoBean private AiRiskAssessmentOrchestrator orchestrator;
  @MockitoBean private AiRiskAssessmentHistoryService historyService;

  private final UUID customerId = UUID.randomUUID();
  private final UUID transactionId = UUID.randomUUID();

  private TransactionDto transactionDto() {
    return new CardTransactionDto(
        transactionId,
        customerId,
        ActivityType.CARD,
        new BigDecimal("10.00"),
        "EUR",
        TransactionStatus.COMPLETED,
        Instant.now(),
        "****1234",
        "DEBIT",
        "Amazon",
        "5732",
        true,
        "AUTH1",
        null);
  }

  @Test
  void streamReturns404BeforeStartingWhenTransactionNotFound() throws Exception {
    when(transactionService.findDetail(customerId, transactionId))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

    mockMvc
        .perform(
            get("/api/v1/customers/{customerId}/ai-assessments/stream", customerId)
                .param("transactionId", transactionId.toString())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
        .andExpect(status().isNotFound());

    verify(orchestrator, never()).run(any(), any());
  }

  @Test
  void streamStartsAsyncWithSseContentType() throws Exception {
    TransactionDto transaction = transactionDto();
    when(transactionService.findDetail(customerId, transactionId)).thenReturn(transaction);

    mockMvc
        .perform(
            get("/api/v1/customers/{customerId}/ai-assessments/stream", customerId)
                .param("transactionId", transactionId.toString())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
        .andExpect(request().asyncStarted())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

    verify(orchestrator, times(1)).run(eq(transaction), any());
  }

  @Test
  void sseTimeoutDoesNotTriggerAnAdditionalOrchestratorRun() throws Exception {
    when(transactionService.findDetail(customerId, transactionId)).thenReturn(transactionDto());

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/customers/{customerId}/ai-assessments/stream", customerId)
                    .param("transactionId", transactionId.toString())
                    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
            .andExpect(request().asyncStarted())
            .andReturn();

    MockAsyncContext asyncContext = (MockAsyncContext) result.getRequest().getAsyncContext();
    for (var listener : asyncContext.getListeners()) {
      listener.onTimeout(new AsyncEvent(asyncContext));
    }

    verify(orchestrator, times(1)).run(any(), any());
  }

  @Test
  void findHistoryWiresQueryParamsToTheService() throws Exception {
    AiRiskAssessmentDto dto =
        new AiRiskAssessmentDto(
            UUID.randomUUID(),
            transactionId,
            Instant.now(),
            RiskLevel.HIGH,
            new BigDecimal("80.00"),
            "findings",
            "recommendations",
            List.of());
    when(historyService.findHistory(
            eq(customerId),
            eq(
                new RiskAssessmentHistoryFilters(
                    transactionId,
                    RiskLevel.HIGH,
                    null,
                    null,
                    new BigDecimal("10"),
                    new BigDecimal("90"))),
            any()))
        .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));

    mockMvc
        .perform(
            get("/api/v1/customers/{customerId}/ai-assessments", customerId)
                .param("transactionId", transactionId.toString())
                .param("riskLevel", "HIGH")
                .param("minScore", "10")
                .param("maxScore", "90")
                .param("page", "0")
                .param("size", "10")
                .param("sort", "triggeredAt,desc")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].riskLevel").value("HIGH"));
  }

  @Test
  void streamReturns401WhenUnauthenticated() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/customers/{customerId}/ai-assessments/stream", customerId)
                .param("transactionId", transactionId.toString()))
        .andExpect(status().isUnauthorized());
  }

  @TestConfiguration
  static class RiskPropertiesTestConfig {
    @Bean
    RiskAssessmentProperties riskAssessmentProperties() {
      return new RiskAssessmentProperties(
          5,
          Duration.ofSeconds(30),
          Duration.ofSeconds(35),
          new RiskAssessmentProperties.LevelThresholds(new BigDecimal("30"), new BigDecimal("70")),
          5);
    }
  }
}
