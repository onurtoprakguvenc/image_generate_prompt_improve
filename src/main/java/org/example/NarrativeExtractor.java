package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Collapses multi-event narrative prose into a single staged keyframe before contract extraction.
 */
public class NarrativeExtractor {

    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int MAX_OUTPUT_TOKENS = 2000;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public NarrativeExtractor() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String extractKeyframe(String narrativeSequence, String modelEndpoint, String apiKey)
            throws IOException, InterruptedException {

        String endpointUrl = API_BASE_URL + modelEndpoint + ":generateContent";

        ObjectNode rootNode = objectMapper.createObjectNode();

        ObjectNode systemInstructionNode = rootNode.putObject("systemInstruction");
        ArrayNode sysParts = systemInstructionNode.putArray("parts");
        sysParts.addObject().put("text",
                "Select the single most visually arresting and mechanically grounded moment from the "
                        + "narrative. Discard internal monologue and metaphor; convert emotional weight into "
                        + "concrete physical markers. Output one paragraph of objective single-moment staging "
                        + "text. No markdown, no preamble, no commentary.");

        ArrayNode contentsArray = rootNode.putArray("contents");
        ObjectNode contentObj = contentsArray.addObject();
        contentObj.put("role", "user");
        ArrayNode userParts = contentObj.putArray("parts");
        userParts.addObject().put("text", narrativeSequence);

        ObjectNode generationConfig = rootNode.putObject("generationConfig");
        generationConfig.put("temperature", 0.4);
        generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS);

        String jsonPayload = objectMapper.writeValueAsString(rootNode);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Gemini API rejected request (HTTP " + response.statusCode() + "): " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode candidates = root.path("candidates");

        if (candidates.isMissingNode() || !candidates.isArray() || candidates.isEmpty()) {
            throw new IOException("Keyframe extraction failed: empty candidates array.");
        }

        JsonNode candidate = candidates.get(0);

        String finishReason = candidate.path("finishReason").asText("");
        if ("MAX_TOKENS".equals(finishReason)) {
            throw new IOException("Keyframe extraction truncated at maxOutputTokens (" + MAX_OUTPUT_TOKENS + ").");
        }
        if ("SAFETY".equals(finishReason) || "RECITATION".equals(finishReason)) {
            throw new IOException("Keyframe extraction halted by the provider (finishReason=" + finishReason + ").");
        }

        JsonNode parts = candidate.path("content").path("parts");
        if (parts.isMissingNode() || !parts.isArray() || parts.isEmpty()) {
            throw new IOException("Keyframe extraction failed: empty parts array.");
        }

        String text = parts.get(0).path("text").asText().trim();
        if (text.isEmpty()) {
            throw new IOException("Keyframe extraction returned empty text.");
        }
        return text;
    }
}