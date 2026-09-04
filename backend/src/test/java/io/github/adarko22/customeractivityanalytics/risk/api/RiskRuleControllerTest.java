package io.github.adarko22.customeractivityanalytics.risk.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.adarko22.customeractivityanalytics.config.SecurityConfig;
import io.github.adarko22.customeractivityanalytics.risk.dto.CreateRiskRuleDto;
import io.github.adarko22.customeractivityanalytics.risk.dto.RiskRuleDto;
import io.github.adarko22.customeractivityanalytics.risk.dto.UpdateRiskRuleDto;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RuleScope;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(RiskRuleController.class)
@Import(SecurityConfig.class)
class RiskRuleControllerTest {

  @Autowired private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @MockitoBean private RiskRuleService riskRuleService;

  private final UUID ruleId = UUID.randomUUID();

  private RiskRuleDto ruleDto() {
    return new RiskRuleDto(
        ruleId, "High-value transaction", RuleScope.ALL, "amount > 5000", new BigDecimal("30"));
  }

  private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor operator() {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"));
  }

  private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor admin() {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  // --- GET: operator and admin allowed, anonymous rejected ---

  @Test
  void findAllReturns200ForOperator() throws Exception {
    when(riskRuleService.findAll(any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(ruleDto()), PageRequest.of(0, 20), 1));

    mockMvc
        .perform(get("/api/v1/risk-rules").with(operator()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].ruleName").value("High-value transaction"));
  }

  @Test
  void findAllReturns200ForAdmin() throws Exception {
    when(riskRuleService.findAll(any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(ruleDto()), PageRequest.of(0, 20), 1));

    mockMvc.perform(get("/api/v1/risk-rules").with(admin())).andExpect(status().isOk());
  }

  @Test
  void findAllReturns401WhenUnauthenticated() throws Exception {
    mockMvc
        .perform(get("/api/v1/risk-rules"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists("WWW-Authenticate"));
  }

  @Test
  void findAllForwardsAllFilterParamsToTheService() throws Exception {
    when(riskRuleService.findAll(any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(ruleDto()), PageRequest.of(0, 20), 1));

    mockMvc
        .perform(
            get("/api/v1/risk-rules")
                .param("appliesTo", "CARD")
                .param("ruleName", "high-value")
                .param("thresholdLogic", "amount")
                .param("minWeight", "5")
                .param("maxWeight", "40")
                .with(operator()))
        .andExpect(status().isOk());

    verify(riskRuleService)
        .findAll(
            eq(RuleScope.CARD),
            eq("high-value"),
            eq("amount"),
            eq(new BigDecimal("5")),
            eq(new BigDecimal("40")),
            any());
  }

  // --- POST/PUT/DELETE: admin allowed, operator forbidden, anonymous rejected ---

  @Test
  void createReturns201ForAdmin() throws Exception {
    CreateRiskRuleDto request =
        new CreateRiskRuleDto(
            "High-value transaction", RuleScope.ALL, "amount > 5000", new BigDecimal("30"));
    when(riskRuleService.create(any())).thenReturn(ruleDto());

    mockMvc
        .perform(
            post("/api/v1/risk-rules")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ruleName").value("High-value transaction"));
  }

  @Test
  void createReturns403ForOperator() throws Exception {
    CreateRiskRuleDto request =
        new CreateRiskRuleDto(
            "High-value transaction", RuleScope.ALL, "amount > 5000", new BigDecimal("30"));

    mockMvc
        .perform(
            post("/api/v1/risk-rules")
                .with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(header().exists("WWW-Authenticate"));
  }

  @Test
  void createReturns401WhenUnauthenticated() throws Exception {
    CreateRiskRuleDto request =
        new CreateRiskRuleDto(
            "High-value transaction", RuleScope.ALL, "amount > 5000", new BigDecimal("30"));

    mockMvc
        .perform(
            post("/api/v1/risk-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createReturns400WhenRuleNameBlank() throws Exception {
    CreateRiskRuleDto request =
        new CreateRiskRuleDto("", RuleScope.ALL, "amount > 5000", new BigDecimal("30"));

    mockMvc
        .perform(
            post("/api/v1/risk-rules")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateReturns200ForAdmin() throws Exception {
    UpdateRiskRuleDto request =
        new UpdateRiskRuleDto(
            "Updated rule", RuleScope.CARD, "amount > 1000", new BigDecimal("20"));
    when(riskRuleService.update(eq(ruleId), any())).thenReturn(ruleDto());

    mockMvc
        .perform(
            put("/api/v1/risk-rules/{ruleId}", ruleId)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  void updateReturns403ForOperator() throws Exception {
    UpdateRiskRuleDto request =
        new UpdateRiskRuleDto(
            "Updated rule", RuleScope.CARD, "amount > 1000", new BigDecimal("20"));

    mockMvc
        .perform(
            put("/api/v1/risk-rules/{ruleId}", ruleId)
                .with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateReturns404WhenRuleMissing() throws Exception {
    UpdateRiskRuleDto request =
        new UpdateRiskRuleDto(
            "Updated rule", RuleScope.CARD, "amount > 1000", new BigDecimal("20"));
    when(riskRuleService.update(eq(ruleId), any()))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

    mockMvc
        .perform(
            put("/api/v1/risk-rules/{ruleId}", ruleId)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteReturns204ForAdmin() throws Exception {
    mockMvc
        .perform(delete("/api/v1/risk-rules/{ruleId}", ruleId).with(admin()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteReturns403ForOperator() throws Exception {
    mockMvc
        .perform(delete("/api/v1/risk-rules/{ruleId}", ruleId).with(operator()))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteReturns404WhenRuleMissing() throws Exception {
    doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(riskRuleService).delete(ruleId);

    mockMvc
        .perform(delete("/api/v1/risk-rules/{ruleId}", ruleId).with(admin()))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteReturns401WhenUnauthenticated() throws Exception {
    mockMvc
        .perform(delete("/api/v1/risk-rules/{ruleId}", ruleId))
        .andExpect(status().isUnauthorized());
  }
}
