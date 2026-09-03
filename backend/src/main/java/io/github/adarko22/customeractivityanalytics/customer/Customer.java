package io.github.adarko22.customeractivityanalytics.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

  @Id
  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "last_name", nullable = false)
  private String lastName;

  protected Customer() {}

  public Customer(UUID customerId, String firstName, String lastName) {
    this.customerId = customerId;
    this.firstName = firstName;
    this.lastName = lastName;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }
}
