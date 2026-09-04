package io.github.adarko22.customeractivityanalytics.user;

import io.github.adarko22.customeractivityanalytics.user.dto.UserProfileDto;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserProfileController {

  @GetMapping("/api/v1/me")
  public UserProfileDto me(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
    List<String> roles =
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(authority -> authority.replaceFirst("^ROLE_", ""))
            .toList();
    return new UserProfileDto(
        jwt.getClaimAsString("preferred_username"),
        jwt.getClaimAsString("given_name"),
        jwt.getClaimAsString("family_name"),
        jwt.getClaimAsString("email"),
        roles);
  }
}
