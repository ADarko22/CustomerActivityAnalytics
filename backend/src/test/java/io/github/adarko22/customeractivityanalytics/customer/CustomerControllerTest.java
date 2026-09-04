package io.github.adarko22.customeractivityanalytics.customer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.adarko22.customeractivityanalytics.config.SecurityConfig;
import io.github.adarko22.customeractivityanalytics.customer.dto.CustomerDto;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerController.class)
@Import(SecurityConfig.class)
class CustomerControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CustomerService customerService;

  @Test
  void searchReturnsPagedCustomers() throws Exception {
    CustomerDto customer = new CustomerDto(UUID.randomUUID(), "Angelo", "Buono");
    when(customerService.search(any(), any()))
        .thenReturn(new PageImpl<>(List.of(customer), PageRequest.of(0, 5), 1));

    mockMvc
        .perform(
            get("/api/v1/customers")
                .param("query", "ang")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].firstName").value("Angelo"));
  }

  @Test
  void searchReturns401WhenUnauthenticated() throws Exception {
    mockMvc
        .perform(get("/api/v1/customers").param("query", "ang"))
        .andExpect(status().isUnauthorized());
  }
}
