package io.github.adarko22.customeractivityanalytics;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Started once via a static initializer (rather than the {@code @Testcontainers} JUnit extension)
 * so every subclass genuinely shares a single running container instead of each test class
 * triggering its own container start.
 */
public abstract class AbstractPostgresIntegrationTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }
}
