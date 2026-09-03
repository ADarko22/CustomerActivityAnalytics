package io.github.adarko22.customeractivityanalytics.customer;

import io.github.adarko22.customeractivityanalytics.customer.dto.CustomerDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomerController {

  private final CustomerService customerService;

  public CustomerController(CustomerService customerService) {
    this.customerService = customerService;
  }

  @GetMapping("/api/v1/customers")
  public Page<CustomerDto> search(
      @RequestParam(name = "query", defaultValue = "") String query,
      @PageableDefault(
              size = 5,
              sort = {"lastName", "firstName"})
          Pageable pageable) {
    return customerService.search(query, pageable);
  }
}
