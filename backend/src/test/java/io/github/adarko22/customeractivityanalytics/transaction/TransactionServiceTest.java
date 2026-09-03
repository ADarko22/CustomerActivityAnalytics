package io.github.adarko22.customeractivityanalytics.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.adarko22.customeractivityanalytics.customer.CustomerService;
import io.github.adarko22.customeractivityanalytics.transaction.card.CardActivity;
import io.github.adarko22.customeractivityanalytics.transaction.card.CardActivityRepository;
import io.github.adarko22.customeractivityanalytics.transaction.crypto.CryptoActivityRepository;
import io.github.adarko22.customeractivityanalytics.transaction.dto.CardTransactionDto;
import io.github.adarko22.customeractivityanalytics.transaction.payment.PaymentActivityRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

  @Mock private CustomerService customerService;
  @Mock private TransactionRepository transactionRepository;
  @Mock private CardActivityRepository cardActivityRepository;
  @Mock private PaymentActivityRepository paymentActivityRepository;
  @Mock private CryptoActivityRepository cryptoActivityRepository;

  private TransactionService transactionService;

  private final UUID customerId = UUID.randomUUID();

  private final TransactionService.TypeFilters noFilters =
      new TransactionService.TypeFilters(
          null, null, null, null, null, null, null, null, null, null, null, null);

  @BeforeEach
  void setUp() {
    transactionService =
        new TransactionService(
            customerService,
            transactionRepository,
            cardActivityRepository,
            paymentActivityRepository,
            cryptoActivityRepository);
  }

  @Test
  void dispatchesToCardRepositoryWhenActivityTypeIsCard() {
    CardActivity card = card();
    when(cardActivityRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(card)));

    Page<?> page =
        transactionService.findOverview(
            customerId,
            ActivityType.CARD,
            null,
            null,
            null,
            null,
            null,
            null,
            noFilters,
            PageRequest.of(0, 10));

    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().get(0)).isInstanceOf(CardTransactionDto.class);
  }

  @Test
  void rejectsUnknownSortProperty() {
    assertThatThrownBy(
            () ->
                transactionService.findOverview(
                    customerId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    noFilters,
                    PageRequest.of(0, 10, Sort.by("cardPan"))))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void throws404WhenTransactionNotFoundForCustomer() {
    when(transactionRepository.findById(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> transactionService.findDetail(customerId, UUID.randomUUID()))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void throws404WhenTransactionBelongsToAnotherCustomer() {
    when(transactionRepository.findById(any())).thenReturn(Optional.of(card()));

    assertThatThrownBy(() -> transactionService.findDetail(UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(ResponseStatusException.class);
  }

  private CardActivity card() {
    return new CardActivity(
        UUID.randomUUID(),
        customerId,
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
}
