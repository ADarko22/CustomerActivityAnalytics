package io.github.adarko22.customeractivityanalytics.customer.dto;

import java.util.UUID;

public record CustomerDto(UUID customerId, String firstName, String lastName) {}
