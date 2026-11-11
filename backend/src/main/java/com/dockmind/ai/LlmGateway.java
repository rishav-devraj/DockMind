package com.dockmind.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Minimal Gemini gateway. Starting point, not gospel. You may swap in Groq, OpenAI or anything vision capable.
 * Set LLM_API_KEY in your environment. Model name is configurable: llm.model (any vision-capable model).
 * Throws on any failure. What you do about that is part of the exercise.
 */
@Component
public class LlmGateway {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${llm.api-key:${LLM_API_KEY:}}") private String apiKey;
    @Value("${llm.model:gemini-3.1-flash-lite}")  private String model;

    /** Text only. Returns the model's raw text (may include markdown fences even when you ask for JSON). */
    public String callLLM(String prompt) throws Exception {
        return call(prompt, null, null);
    }

    /** Text plus one image. Returns the model's raw text. */
    public String callVision(String prompt, byte[] image, String mimeType) throws Exception {
        return call(prompt, image, mimeType);
    }

    private String call(String prompt, byte[] image, String mime) throws Exception {
        var parts = new java.util.ArrayList<Map<String, Object>>();
        parts.add(Map.of("text", prompt));
        if (image != null) {
            parts.add(Map.of("inlineData", Map.of("mimeType", mime, "data", Base64.getEncoder().encodeToString(image))));
        }
        var body = Map.of(
            "contents", List.of(Map.of("parts", parts)),
            // Thinking off: the reader only transcribes, and a thinking pass pushed calls past the timeout.
            "generationConfig", Map.of("responseMimeType", "application/json", "temperature", 0.1,
                "thinkingConfig", Map.of("thinkingBudget", 0)));
        var req = HttpRequest.newBuilder(URI.create(
                "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
            .build();
        var res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw new IllegalStateException("LLM HTTP " + res.statusCode() + ": " + res.body());
        JsonNode root = JSON.readTree(res.body());
        return root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
    }
}
