package io.github.adarko22.customeractivityanalytics.customer;

import io.github.adarko22.customeractivityanalytics.customer.dto.CustomerDto;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerService {

  private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

  private final CustomerRepository customerRepository;

  public CustomerService(CustomerRepository customerRepository) {
    this.customerRepository = customerRepository;
  }

  public Page<CustomerDto> search(String query, Pageable pageable) {
    String normalizedQuery = query == null ? "" : query.trim();
    log.info(
        "Searching customers: querySupplied={}, page={}, size={}",
        !normalizedQuery.isEmpty(),
        pageable.getPageNumber(),
        pageable.getPageSize());
    log.debug("Customer search query text: '{}'", normalizedQuery);
    return customerRepository.search(normalizedQuery, pageable).map(CustomerService::toDto);
  }

  public void requireExists(UUID customerId) {
    if (!customerRepository.existsById(customerId)) {
      log.warn("Customer not found: customerId={}", customerId);
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + customerId);
    }
  }

  public CustomerDto findById(UUID customerId) {
    return customerRepository
        .findById(customerId)
        .map(CustomerService::toDto)
        .orElseThrow(
            () -> {
              log.warn("Customer not found: customerId={}", customerId);
              return new ResponseStatusException(
                  HttpStatus.NOT_FOUND, "Customer not found: " + customerId);
            });
  }

  private static CustomerDto toDto(Customer customer) {
    return new CustomerDto(
        customer.getCustomerId(), customer.getFirstName(), customer.getLastName());
  }
}
