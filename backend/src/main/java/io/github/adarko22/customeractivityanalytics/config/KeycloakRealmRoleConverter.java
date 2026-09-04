package io.github.adarko22.customeractivityanalytics.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Maps Keycloak's {@code realm_access.roles} claim to Spring Security authorities ({@code
 * ROLE_<UPPERCASE ROLE>}). A token with no realm roles simply grants no role-gated authority (it
 * still passes {@code anyRequest().authenticated()} for read access).
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

  private static final String REALM_ACCESS_CLAIM = "realm_access";
  private static final String ROLES_CLAIM = "roles";

  @Override
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
    if (realmAccess == null || !(realmAccess.get(ROLES_CLAIM) instanceof List<?> roles)) {
      return Set.of();
    }
    return roles.stream()
        .map(Object::toString)
        .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
        .collect(Collectors.toUnmodifiableSet());
  }
}
