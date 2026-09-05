package io.github.adarko22.customeractivityanalytics.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Real OAuth2/OIDC resource-server security (docs/DECISIONS.md D2), replacing the temporary {@code
 * permitAll} chain (D13). Every {@code /api/v1/**} endpoint requires a valid Keycloak-issued JWT;
 * the three risk-rule write verbs additionally require the {@code ADMIN} realm role.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
  private static final String ADMIN_ROLE = "ADMIN";

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) {
    try {
      http.csrf(
              // Stateless, bearer-JWT-only resource server (SessionCreationPolicy.STATELESS, no
              // cookies/sessions) — CSRF protects against an ambient credential the browser
              // attaches
              // automatically (a session cookie), which never applies here.
              AbstractHttpConfigurer::disable)
          .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .authorizeHttpRequests(
              auth ->
                  auth.requestMatchers("/actuator/**", "/swagger-ui/**", "/api-docs/**")
                      .permitAll()
                      .requestMatchers(HttpMethod.POST, "/api/v1/risk-rules")
                      .hasRole(ADMIN_ROLE)
                      .requestMatchers(HttpMethod.PUT, "/api/v1/risk-rules/**")
                      .hasRole(ADMIN_ROLE)
                      .requestMatchers(HttpMethod.DELETE, "/api/v1/risk-rules/**")
                      .hasRole(ADMIN_ROLE)
                      .anyRequest()
                      .authenticated())
          .exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler()))
          .oauth2ResourceServer(
              oauth2 ->
                  oauth2
                      .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                      .authenticationEntryPoint(authenticationEntryPoint()));
      return http.build();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to build the security filter chain", e);
    }
  }

  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
    return converter;
  }

  /**
   * Spring Security's default rejection handling only logs at DEBUG (silent under this project's
   * default INFO root level), so a request rejected here — e.g. the SSE endpoint's {@code
   * EventSource} connection, which cannot carry a bearer token — produces a frontend error with
   * zero backend log output. Both beans below log at WARN and then delegate the actual response to
   * Spring's own {@code Bearer*} handlers, rather than writing the response themselves — that
   * preserves the RFC 6750 {@code WWW-Authenticate} challenge header those handlers set (a bare
   * {@code response.sendError(...)} would silently drop it).
   */
  @Bean
  public AuthenticationEntryPoint authenticationEntryPoint() {
    AuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();
    return (request, response, authException) -> {
      logRejection(request, "unauthenticated", authException.getMessage());
      delegate.commence(request, response, authException);
    };
  }

  @Bean
  public AccessDeniedHandler accessDeniedHandler() {
    AccessDeniedHandler delegate = new BearerTokenAccessDeniedHandler();
    return (request, response, accessDeniedException) -> {
      logRejection(request, "forbidden", accessDeniedException.getMessage());
      delegate.handle(request, response, accessDeniedException);
    };
  }

  private static void logRejection(HttpServletRequest request, String kind, String reason) {
    log.warn(
        "Rejected {} request: method={}, uri={}, reason={}",
        kind,
        request.getMethod(),
        request.getRequestURI(),
        reason);
  }
}
