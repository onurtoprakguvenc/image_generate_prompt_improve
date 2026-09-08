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

public class NarrativeExtractor {

    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public NarrativeExtractor() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String extractKeyframe(String narrativeSequence, String modelEndpoint, String apiKey) throws IOException, InterruptedException {
        String endpointUrl = API_BASE_URL + modelEndpoint + ":generateContent?key=" + apiKey;

        ObjectNode rootNode = objectMapper.createObjectNode();

        ObjectNode systemInstructionNode = rootNode.putObject("systemInstruction");
        ArrayNode sysParts = systemInstructionNode.putArray("parts");
        sysParts.addObject().put("text",
                "Analyze narrative prose containing multiple temporal events, plot progression, internal monologues, and metaphorical rhetoric. Resolve temporal conflicts by selecting the single most visually arresting, dramatic, and mechanically grounded keyframe. Strip out purely abstract internal questions and convert emotional gravity into concrete, positive physical markers. Output strictly a single paragraph of raw, objective, single-moment physical staging text with zero markdown formatting, zero meta-announcements, and zero conversational filler."
        );

        ArrayNode contentsArray = rootNode.putArray("contents");
        ObjectNode contentObj = contentsArray.addObject();
        contentObj.put("role", "user");
        ArrayNode userParts = contentObj.putArray("parts");
        userParts.addObject().put("text", narrativeSequence);

        ObjectNode generationConfig = rootNode.putObject("generationConfig");
        generationConfig.put("temperature", 0.4);
        generationConfig.put("maxOutputTokens", 2000);

        String jsonPayload = objectMapper.writeValueAsString(rootNode);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl))
                .header("Content-Type", "application/json")
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
            throw new IOException("Failed to extract keyframe: Gemini API returned empty candidates array.");
        }

        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (parts.isMissingNode() || !parts.isArray() || parts.isEmpty()) {
            throw new IOException("Failed to extract keyframe: Gemini API returned empty parts array.");
        }

        return parts.get(0).path("text").asText().trim();
    }
}