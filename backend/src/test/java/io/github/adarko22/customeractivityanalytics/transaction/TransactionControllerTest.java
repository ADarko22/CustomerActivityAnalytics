package io.github.adarko22.customeractivityanalytics.transaction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.adarko22.customeractivityanalytics.config.SecurityConfig;
import io.github.adarko22.customeractivityanalytics.transaction.dto.CardTransactionDto;
import io.github.adarko22.customeractivityanalytics.transaction.dto.CryptoTransactionDto;
import io.github.adarko22.customeractivityanalytics.transaction.dto.PaymentTransactionDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(TransactionController.class)
@Import(SecurityConfig.class)
class TransactionControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TransactionService transactionService;

  private final UUID customerId = UUID.randomUUID();
  private final UUID transactionId = UUID.randomUUID();

  @Test
  void findOverviewReturnsCardDiscriminatedJson() throws Exception {
    CardTransactionDto card =
        new CardTransactionDto(
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
    when(transactionService.findOverview(
            eq(customerId), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(card)));

    mockMvc
        .perform(get("/api/v1/customers/{customerId}/transactions", customerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].activityType").value("CARD"))
        .andExpect(jsonPath("$.content[0].merchantName").value("Amazon"));
  }

  @Test
  void findOverviewReturnsPaymentDiscriminatedJson() throws Exception {
    PaymentTransactionDto payment =
        new PaymentTransactionDto(
            transactionId,
            customerId,
            ActivityType.PAYMENT,
            new BigDecimal("500.00"),
            "USD",
            TransactionStatus.COMPLETED,
            Instant.now(),
            "WIRE",
            "SENDER",
            "RECEIVER",
            "US");
    when(transactionService.findOverview(
            eq(customerId), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(payment)));

    mockMvc
        .perform(get("/api/v1/customers/{customerId}/transactions", customerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].activityType").value("PAYMENT"))
        .andExpect(jsonPath("$.content[0].paymentMethod").value("WIRE"));
  }

  @Test
  void findOverviewReturnsCryptoDiscriminatedJson() throws Exception {
    CryptoTransactionDto crypto =
        new CryptoTransactionDto(
            transactionId,
            customerId,
            ActivityType.CRYPTO,
            new BigDecimal("0.5"),
            "BTC",
            TransactionStatus.COMPLETED,
            Instant.now(),
            "BTC",
            "wallet-from",
            "wallet-to",
            "tx-hash",
            "Kraken");
    when(transactionService.findOverview(
            eq(customerId), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(crypto)));

    mockMvc
        .perform(get("/api/v1/customers/{customerId}/transactions", customerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].activityType").value("CRYPTO"))
        .andExpect(jsonPath("$.content[0].blockchain").value("BTC"));
  }

  @Test
  void findDetailReturns404WhenTransactionMissing() throws Exception {
    when(transactionService.findDetail(eq(customerId), eq(transactionId)))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

    mockMvc
        .perform(
            get(
                "/api/v1/customers/{customerId}/transactions/{transactionId}",
                customerId,
                transactionId))
        .andExpect(status().isNotFound());
  }
}
