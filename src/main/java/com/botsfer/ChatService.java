package com.botsfer;

import com.botsfer.agent.IntentService;
import com.botsfer.agent.PcAgentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class ChatService {
    private static final String AUDIO_RESULT_PREFIX = "__AUDIO_RESULT__";

    private final TranscriptService transcriptService;
    private final PcAgentService pcAgent;
    private final IntentService intentService;

    /** Async results from background agent tasks (polled by frontend). */
    private final ConcurrentLinkedQueue<String> asyncResults = new ConcurrentLinkedQueue<>();

    public ChatService(TranscriptService transcriptService, PcAgentService pcAgent,
                       IntentService intentService) {
        this.transcriptService = transcriptService;
        this.pcAgent = pcAgent;
        this.intentService = intentService;
    }

    /** Returns and removes the next async result, or null if none. */
    public String pollAsyncResult() {
        return asyncResults.poll();
    }

    @Value("${app.chat.placeholder.enabled:true}")
    private boolean placeholderEnabled;
    @Value("${app.openai.enabled:false}")
    private boolean openAiEnabled;
    @Value("${app.openai.api-key:}")
    private String openAiApiKey;
    @Value("${app.openai.base-url:https://api.openai.com/v1}")
    private String openAiBaseUrl;
    @Value("${app.openai.transcription-model:gpt-4o-mini-transcribe}")
    private String openAiTranscriptionModel;
    @Value("${app.openai.text-model:gpt-4o-mini}")
    private String openAiTextModel;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .version(Version.HTTP_1_1)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get bot reply for a given message. Used by the in-app chat and all platform integrations.
     *
     * Flow:
     * 1. If OpenAI is available → classify intent via IntentService
     *    - Actionable → execute via PcAgentService.executeAction()
     *    - Not actionable → return AI's conversational reply
     * 2. If OpenAI unavailable → fall back to regex matching via PcAgentService.tryExecute()
     * 3. If nothing matched → placeholder reply
     */
    public String getReply(String message) {
        if (message == null) {
            message = "";
        }
        transcriptService.save("USER", message.trim());
        String trimmed = message.trim();

        java.util.function.Consumer<String> asyncCallback = result -> {
            transcriptService.save("BOT(agent)", result);
            asyncResults.add(result);
        };

        // 1. Try OpenAI intent classification
        if (intentService.isAvailable()) {
            try {
                IntentService.IntentResult intent = intentService.classify(trimmed);
                if (intent != null) {
                    if (intent.actionable() && intent.action() != null) {
                        // Execute the classified action
                        String actionReply = pcAgent.executeAction(
                                intent.action(), intent.params(), asyncCallback);
                        if (actionReply != null) {
                            transcriptService.save("BOT", actionReply);
                            return actionReply;
                        }
                        // Action unknown — use the AI's reply as fallback
                        if (intent.reply() != null && !intent.reply().isBlank()) {
                            transcriptService.save("BOT", intent.reply());
                            return intent.reply();
                        }
                    } else {
                        // Not actionable — return AI's conversational response
                        String conversational = intent.reply();
                        if (conversational != null && !conversational.isBlank()) {
                            transcriptService.save("BOT", conversational);
                            return conversational;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[ChatService] IntentService error, falling back to regex: " + e.getMessage());
            }
        }

        // 2. Fallback: regex-based command matching
        String agentReply = pcAgent.tryExecute(trimmed, asyncCallback);
        if (agentReply != null) {
            transcriptService.save("BOT", agentReply);
            return agentReply;
        }

        // 3. Placeholder
        String reply = placeholderEnabled ? getPlaceholderReply(trimmed) : "Chat not configured.";
        transcriptService.save("BOT", reply);
        return reply;
    }

    /** Native audio pipeline: WAV -> transcription -> text response. */
    public String getReplyFromAudio(byte[] wavAudio) {
        if (wavAudio == null || wavAudio.length == 0) {
            return "No audio captured.";
        }
        if (!openAiEnabled || openAiApiKey == null || openAiApiKey.isBlank()) {
            return "Audio chat is not configured. Set app.openai.enabled=true and app.openai.api-key.";
        }

        try {
            System.out.println("[AudioLLM] Sending transcription request. wavBytes=" + wavAudio.length
                    + ", transcriptionModel=" + openAiTranscriptionModel
                    + ", baseUrl=" + openAiBaseUrl);
            String transcript = transcribeAudio(wavAudio);
            if (transcript == null || transcript.isBlank()) {
                return "No speech detected.";
            }

            System.out.println("[AudioLLM] Transcript: " + transcript);
            transcriptService.save("USER(voice)", transcript);
            String reply = getReplyFromTextModel(transcript);
            transcriptService.save("BOT", reply == null ? "" : reply);
            Map<String, String> result = Map.of(
                    "transcript", transcript,
                    "reply", reply == null ? "" : reply
            );
            return AUDIO_RESULT_PREFIX + objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            System.err.println("[AudioLLM] Exception during audio request: " + e.getMessage());
            e.printStackTrace();
            return "Audio chat error: " + e.getMessage();
        }
    }

    private String transcribeAudio(byte[] wavAudio) throws Exception {
        String boundary = "----BotsferBoundary" + UUID.randomUUID();
        byte[] body = buildMultipartWavBody(boundary, wavAudio, openAiTranscriptionModel);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(openAiBaseUrl + "/audio/transcriptions"))
                .header("Authorization", "Bearer " + openAiApiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = sendWithRetry(request, 3);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            System.err.println("[AudioLLM] Transcription failed. status=" + response.statusCode());
            System.err.println("[AudioLLM] Response body: " + response.body());
            throw new IllegalStateException("Transcription failed (" + response.statusCode() + ")");
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.path("text").asText("");
    }

    private String getReplyFromTextModel(String message) throws Exception {
        Map<String, Object> payload = Map.of(
                "model", openAiTextModel,
                "input", new Object[]{
                        Map.of(
                                "role", "user",
                                "content", new Object[]{
                                        Map.of("type", "input_text", "text", message)
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

        HttpResponse<String> response = sendWithRetry(request, 3);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            System.err.println("[AudioLLM] Text response failed. status=" + response.statusCode());
            System.err.println("[AudioLLM] Response body: " + response.body());
            return "Audio text response failed (" + response.statusCode() + ").";
        }

        return extractTextFromResponse(response.body());
    }

    private byte[] buildMultipartWavBody(String boundary, byte[] wavAudio, String model) throws Exception {
        String crlf = "\r\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"model\"" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(model.getBytes(StandardCharsets.UTF_8));
        out.write(crlf.getBytes(StandardCharsets.UTF_8));

        out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"voice.wav\"" + crlf)
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: audio/wav" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(wavAudio);
        out.write(crlf.getBytes(StandardCharsets.UTF_8));

        out.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request, int maxAttempts) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException ioe) {
                last = ioe;
                System.err.println("[AudioLLM] Network error on attempt " + attempt + "/" + maxAttempts + ": " + ioe.getMessage());
                if (attempt == maxAttempts) break;
                Thread.sleep(300L * attempt);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw ie;
            }
        }
        throw last == null ? new IOException("Request failed after retries.") : last;
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
            return "Hi! I'm Botsfer. Type a command or say \"help\" to see what I can do.";
        }
        String lower = message.toLowerCase();
        if (lower.contains("hello") || lower.contains("hi") || lower.contains("hey")) {
            return "Hello! I can control your PC. Say \"help\" to see what I can do.";
        }
        if (lower.contains("help") || lower.contains("what can you do") || lower.contains("commands")) {
            return "Here's what I can do:\n"
                    + "- \"retrieve all photos\" — collect all photos from your PC\n"
                    + "- \"collect all videos/music/documents\" — same for other file types\n"
                    + "- \"close all windows\" — close everything except me\n"
                    + "- \"close chrome\" — close a specific app\n"
                    + "- \"open notepad\" — launch an app\n"
                    + "- \"open google.com\" — open a URL\n"
                    + "- \"google how to cook pasta\" — search Google\n"
                    + "- \"youtube funny cats\" — search YouTube\n"
                    + "- \"minimize all\" — show desktop\n"
                    + "- \"take a screenshot\" — capture screen now\n"
                    + "- \"list running apps\" — show what's open\n"
                    + "- \"close all browsers\" — close Chrome/Firefox/Edge\n"
                    + "- \"lock screen\" — lock your PC\n"
                    + "- \"search files named report\" — find files\n"
                    + "- \"what have you collected\" — see collected files\n"
                    + "- \"run powershell: Get-Date\" — run a command";
        }
        return "I didn't recognize that command. Say \"help\" to see what I can do, or try:\n"
                + "- \"retrieve all photos\"\n"
                + "- \"close all windows\"\n"
                + "- \"open chrome\"";
    }
}
