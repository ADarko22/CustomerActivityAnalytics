package io.github.adarko22.customeractivityanalytics.user;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.adarko22.customeractivityanalytics.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserProfileController.class)
@Import(SecurityConfig.class)
class UserProfileControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void meReturnsClaimsAndRolesForAdmin() throws Exception {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("preferred_username", "admin")
            .claim("given_name", "Aiden")
            .claim("family_name", "Admin")
            .claim("email", "admin@example.com")
            .build();

    mockMvc
        .perform(
            get("/api/v1/me")
                .with(
                    jwt()
                        .jwt(jwt)
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_ADMIN"),
                            new SimpleGrantedAuthority("ROLE_OPERATOR"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("admin"))
        .andExpect(jsonPath("$.firstName").value("Aiden"))
        .andExpect(jsonPath("$.lastName").value("Admin"))
        .andExpect(jsonPath("$.email").value("admin@example.com"))
        .andExpect(jsonPath("$.roles", containsInAnyOrder("ADMIN", "OPERATOR")));
  }

  @Test
  void meReturns401WhenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
  }
}
