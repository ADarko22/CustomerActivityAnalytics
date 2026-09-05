package io.github.adarko22.customeractivityanalytics.risk.engine;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Exercises the real {@code local-environment/wiremock} fixture set used by {@code ./gradlew dev} —
 * unlike {@link AiRiskAssessmentWireMockReplayTest}, which boots its own isolated, always-matching
 * stub and never touches these files. Guards the offline-demo fix from {@code
 * docs/development/PHASE_7.md}: replaying one of the 15 originally-recorded scenarios must still
 * return its own recorded response, and any other request must still get a 200 via the generic,
 * lower-priority catch-all stub, never a 404.
 */
class AiRiskAssessmentLocalWireMockFixturesTest {

  private static final Path WIREMOCK_ROOT = Path.of("../local-environment/wiremock");
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .options(
              wireMockConfig().dynamicPort().usingFilesUnderDirectory(WIREMOCK_ROOT.toString()))
          .build();

  @Test
  void replayingAnOriginalRecordedScenarioReturnsItsOwnRecordedContent() throws Exception {
    String recordedBody = recordedRequestBody("anthropic-messages-3AWDA.json");

    HttpResponse<String> response = post(recordedBody);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("ACH payment of €627.50 to Germany");
  }

  @Test
  void aNovelTransactionStillGetsTheGenericFallbackInsteadOf404() throws Exception {
    String recordedBody = recordedRequestBody("anthropic-messages-3AWDA.json");
    ObjectNode requestJson = (ObjectNode) MAPPER.readTree(recordedBody);
    ObjectNode firstMessage = (ObjectNode) requestJson.get("messages").get(0);
    String novelContent =
        firstMessage
            .get("content")
            .asText()
            .replace(
                "b0000000-0000-0000-0000-000000000003", "ffffffff-ffff-ffff-ffff-ffffffffffff");
    firstMessage.put("content", novelContent);

    HttpResponse<String> response = post(MAPPER.writeValueAsString(requestJson));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).doesNotContain("ACH payment of €627.50 to Germany");
  }

  private HttpResponse<String> post(String body) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(wireMock.baseUrl() + "/v1/messages"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private String recordedRequestBody(String mappingFileName) throws IOException {
    JsonNode mapping =
        MAPPER.readTree(WIREMOCK_ROOT.resolve("mappings").resolve(mappingFileName).toFile());
    return mapping.get("request").get("bodyPatterns").get(0).get("equalToJson").asText();
  }
}
