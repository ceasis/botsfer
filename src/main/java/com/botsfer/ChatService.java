package com.botsfer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Service
public class ChatService {

    @Value("${app.chat.placeholder.enabled:true}")
    private boolean placeholderEnabled;
    @Value("${app.openai.enabled:false}")
    private boolean openAiEnabled;
    @Value("${app.openai.api-key:}")
    private String openAiApiKey;
    @Value("${app.openai.base-url:https://api.openai.com/v1}")
    private String openAiBaseUrl;
    @Value("${app.openai.audio-model:gpt-4o-audio-preview}")
    private String openAiAudioModel;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get bot reply for a given message. Used by the in-app chat and all platform integrations.
     */
    public String getReply(String message) {
        if (message == null) {
            message = "";
        }
        return placeholderEnabled ? getPlaceholderReply(message.trim()) : "Chat not configured.";
    }

    /**
     * Send raw WAV audio directly to the LLM and return the assistant's text response.
     */
    public String getReplyFromAudio(byte[] wavAudio) {
        if (wavAudio == null || wavAudio.length == 0) {
            return "No audio captured.";
        }
        if (!openAiEnabled || openAiApiKey == null || openAiApiKey.isBlank()) {
            return "Audio chat is not configured. Set app.openai.enabled=true and app.openai.api-key.";
        }

        try {
            String audioB64 = Base64.getEncoder().encodeToString(wavAudio);
            Map<String, Object> payload = Map.of(
                    "model", openAiAudioModel,
                    "modalities", new String[]{"text"},
                    "input", new Object[]{
                            Map.of(
                                    "role", "user",
                                    "content", new Object[]{
                                            Map.of(
                                                    "type", "input_audio",
                                                    "input_audio", Map.of(
                                                            "data", audioB64,
                                                            "format", "wav"
                                                    )
                                            )
                                    }
                            )
                    }
            );

            String requestBody = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(openAiBaseUrl + "/responses"))
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(90))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "Audio chat request failed (" + response.statusCode() + ").";
            }

            return extractTextFromResponse(response.body());
        } catch (Exception e) {
            return "Audio chat error: " + e.getMessage();
        }
    }

    private String extractTextFromResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode outputText = root.get("output_text");
        if (outputText != null && outputText.isTextual() && !outputText.asText().isBlank()) {
            return outputText.asText();
        }

        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (!content.isArray()) continue;
                for (JsonNode c : content) {
                    String type = c.path("type").asText("");
                    if ("output_text".equals(type)) {
                        String text = c.path("text").asText("");
                        if (!text.isBlank()) return text;
                    }
                    JsonNode textNode = c.path("text");
                    if (textNode.isTextual() && !textNode.asText().isBlank()) {
                        return textNode.asText();
                    }
                }
            }
        }
        return "No reply from audio model.";
    }

    private String getPlaceholderReply(String message) {
        if (message.isEmpty()) {
            return "Hi! Say something and I'll reply. You can also use the microphone for voice.";
        }
        String lower = message.toLowerCase();
        if (lower.contains("hello") || lower.contains("hi")) {
            return "Hello! How can I help you today?";
        }
        if (lower.contains("help")) {
            return "I'm Botsfer. Type or use voice in the app, or chat with me on any connected platform.";
        }
        return "You said: \"" + message + "\". Add your own bot logic in ChatService.";
    }
}
