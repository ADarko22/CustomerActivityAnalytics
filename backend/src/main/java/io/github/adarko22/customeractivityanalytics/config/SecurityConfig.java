package io.github.adarko22.customeractivityanalytics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permits all requests until Phase 5 wires up real OAuth2/OIDC login and role-based access (see
 * docs/DECISIONS.md D2). Without this, the OAuth2 resource-server starter already on the classpath
 * makes Spring Security deny every request by default, which would block every Phase 2-5 endpoint
 * before login exists.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }
}
