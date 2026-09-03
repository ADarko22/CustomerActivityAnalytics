package io.github.adarko22.customeractivityanalytics.customer;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

  @Query(
      """
      select c from Customer c
      where :query = ''
         or lower(c.firstName) like lower(concat('%', :query, '%'))
         or lower(c.lastName) like lower(concat('%', :query, '%'))
         or lower(str(c.customerId)) like lower(concat(:query, '%'))
      """)
  Page<Customer> search(@Param("query") String query, Pageable pageable);
}
