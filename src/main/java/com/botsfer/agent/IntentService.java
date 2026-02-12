package com.botsfer.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Uses OpenAI to classify user messages as actionable PC commands or plain conversation.
 * Returns structured intent data that PcAgentService can execute.
 */
@Service
public class IntentService {

    private static final Logger log = LoggerFactory.getLogger(IntentService.class);

    @Value("${app.openai.enabled:false}")
    private boolean openAiEnabled;

    @Value("${app.openai.api-key:}")
    private String openAiApiKey;

    @Value("${app.openai.base-url:https://api.openai.com/v1}")
    private String openAiBaseUrl;

    @Value("${app.openai.text-model:gpt-4o-mini}")
    private String openAiTextModel;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are Botsfer, a PC assistant that controls a Windows computer. Analyze the user's message and determine if it's an actionable command or just conversation.

            Available actions (return the exact action ID):

            FILE OPERATIONS:
            - collect_files: Scan and collect files by category. params: {"category": "photos|videos|music|documents|archives"}
            - list_collected: Show what files have been collected. params: {}
            - open_collected: Open the collected files folder. params: {}
            - search_files: Search for files by name pattern. params: {"pattern": "search term"}

            WINDOW/APP CONTROL:
            - close_all_windows: Close all user application windows. params: {}
            - close_browsers: Close all browser windows. params: {}
            - close_app: Close a specific application. params: {"app_name": "name of app"}
            - open_app: Launch an application. params: {"app_name": "name of app"}
            - minimize_all: Minimize all windows / show desktop. params: {}
            - lock_screen: Lock the computer. params: {}
            - take_screenshot: Capture a screenshot. params: {}
            - list_running_apps: List currently running applications. params: {}
            - task_status: Show status of background tasks. params: {}

            BROWSER:
            - youtube_search: Search YouTube. params: {"query": "search terms"}
            - google_search: Search Google. params: {"query": "search terms"}
            - open_url: Open a URL in browser. params: {"url": "the URL"}
            - list_browser_tabs: List open browser windows/tabs. params: {}

            FILE SYSTEM:
            - open_path: Open a file or folder by path. params: {"path": "file/folder path"}
            - copy_file: Copy a file to destination. params: {"source": "source path", "destination": "dest path"}
            - delete_file: Delete a file. params: {"path": "file path"}

            ADVANCED:
            - run_powershell: Execute a PowerShell command. params: {"command": "the command"}

            Respond with ONLY valid JSON (no markdown, no code fences):
            - If actionable: {"actionable": true, "action": "action_id", "params": {…}, "reply": "brief acknowledgment"}
            - If NOT actionable (greeting, question, conversation): {"actionable": false, "reply": "your conversational response"}

            Be smart about intent — "get my pictures" means collect_files with category photos.
            "shut everything down" means close_all_windows.
            "find me some cat videos" means youtube_search.
            """;

    /**
     * Returns true if OpenAI-based intent classification is available.
     */
    public boolean isAvailable() {
        return openAiEnabled && openAiApiKey != null && !openAiApiKey.isBlank();
    }

    /**
     * Classifies a user message via OpenAI.
     * Returns an IntentResult with actionable flag, action ID, params, and reply.
     * Returns null on failure (caller should fall back to regex matching).
     */
    public IntentResult classify(String message) {
        if (!isAvailable()) return null;

        try {
            Map<String, Object> payload = Map.of(
                    "model", openAiTextModel,
                    "input", new Object[]{
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", message)
                    },
                    "text", Map.of("format", Map.of("type", "text"))
            );

            String requestBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(openAiBaseUrl + "/responses"))
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("[IntentService] API call failed. status={}, body={}", response.statusCode(), response.body());
                return null;
            }

            String text = extractTextFromResponse(response.body());
            if (text == null || text.isBlank()) {
                log.warn("[IntentService] Empty response from API");
                return null;
            }

            log.info("[IntentService] Raw response: {}", text);
            return parseIntentResponse(text);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[IntentService] Interrupted during classify: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("[IntentService] Exception during classify: {}", e.getMessage());
            return null;
        }
    }

    private IntentResult parseIntentResponse(String text) {
        try {
            // Strip markdown code fences if present
            String json = text.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            JsonNode root = objectMapper.readTree(json);
            boolean actionable = root.path("actionable").asBoolean(false);
            String action = root.path("action").asText(null);
            String reply = root.path("reply").asText("");

            Map<String, String> params = new HashMap<>();
            JsonNode paramsNode = root.get("params");
            if (paramsNode != null && paramsNode.isObject()) {
                paramsNode.fields().forEachRemaining(entry ->
                        params.put(entry.getKey(), entry.getValue().asText("")));
            }

            return new IntentResult(actionable, action, params, reply);
        } catch (Exception e) {
            log.error("[IntentService] Failed to parse intent JSON: {} — raw: {}", e.getMessage(), text);
            return null;
        }
    }

    private String extractTextFromResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        // Try output_text shortcut first
        JsonNode outputText = root.get("output_text");
        if (outputText != null && outputText.isTextual() && !outputText.asText().isBlank()) {
            return outputText.asText();
        }

        // Traverse output array
        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (!content.isArray()) continue;
                for (JsonNode c : content) {
                    String type = c.path("type").asText("");
                    if ("output_text".equals(type)) {
                        String t = c.path("text").asText("");
                        if (!t.isBlank()) return t;
                    }
                    JsonNode textNode = c.path("text");
                    if (textNode.isTextual() && !textNode.asText().isBlank()) {
                        return textNode.asText();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Structured result from intent classification.
     */
    public record IntentResult(
            boolean actionable,
            String action,
            Map<String, String> params,
            String reply
    ) {}
}
