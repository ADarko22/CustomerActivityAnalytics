package io.github.adarko22.customeractivityanalytics.risk.engine;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Exercises the real {@code local-environment/wiremock} fixture set used by {@code ./gradlew dev} —
 * unlike {@link AiRiskAssessmentWireMockReplayTest}, which boots its own isolated, always-matching
 * stub and never touches these files. Guards the offline-demo fix from {@code
 * docs/development/PHASE_7.md}: each recorded scenario is matched by a {@code transactionId}-scoped
 * regex, so a request for a known transaction must still hit its own recorded stub even when its
 * "Prior assessments" history differs from what was originally captured. Any transaction not among
 * the recorded scenarios must still get a 200 via the generic, lower-priority catch-all.
 */
class AiRiskAssessmentLocalWireMockFixturesTest {

  private static final Path WIREMOCK_ROOT = Path.of("../local-environment/wiremock");
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static final String KNOWN_TRANSACTION_ID = "b0000000-0000-0000-0000-000000000003";

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .options(
              wireMockConfig().dynamicPort().usingFilesUnderDirectory(WIREMOCK_ROOT.toString()))
          .build();

  @Test
  void aKnownTransactionMatchesItsStubRegardlessOfGrowingAssessmentHistory() throws Exception {
    String originalHistory =
        """
        - triggeredAt: 2026-09-05T15:39:57Z
          riskLevel: LOW
        """;
    String grownHistory =
        """
        - triggeredAt: 2026-09-06T09:00:00Z
          riskLevel: LOW
        - triggeredAt: 2026-09-05T15:39:57Z
          riskLevel: LOW
        - triggeredAt: 2026-01-01T00:00:00Z
          riskLevel: HIGH
        """;

    for (String history : List.of(originalHistory, grownHistory)) {
      HttpResponse<String> response = post(requestBodyFor(KNOWN_TRANSACTION_ID, history));

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("ACH payment of €627.50 to Germany");
    }
  }

  @Test
  void aNovelTransactionStillGetsTheGenericFallbackInsteadOf404() throws Exception {
    HttpResponse<String> response =
        post(requestBodyFor("ffffffff-ffff-ffff-ffff-ffffffffffff", ""));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).doesNotContain("ACH payment of €627.50 to Germany");
  }

  private String requestBodyFor(String transactionId, String priorAssessmentsSection)
      throws IOException {
    String content =
        "Assess the following transaction for risk.\n\n"
            + "## Transaction context\n"
            + "transactionId: "
            + transactionId
            + "\nactivityType: PAYMENT\namount: 627.50\ncurrency: EUR\nstatus: COMPLETED\n\n"
            + "## Prior assessments for this transaction (most recent first)\n"
            + priorAssessmentsSection;
    Map<String, Object> body =
        Map.of(
            "max_tokens",
            4096,
            "model",
            "claude-haiku-4-5",
            "messages",
            List.of(Map.of("content", content, "role", "user")));
    return MAPPER.writeValueAsString(body);
  }

  private HttpResponse<String> post(String body) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(wireMock.baseUrl() + "/v1/messages"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
