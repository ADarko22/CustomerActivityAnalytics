package io.github.adarko22.customeractivityanalytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CustomerActivityAnalyticsApplication {

  public static void main(String[] args) {
    SpringApplication.run(CustomerActivityAnalyticsApplication.class, args);
  }
}
