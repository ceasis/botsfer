package com.botsfer;

import com.botsfer.agent.PcAgentService;
import com.botsfer.agent.SystemContextProvider;
import com.botsfer.agent.tools.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.File;
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
import java.util.function.Consumer;

@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String AUDIO_RESULT_PREFIX = "__AUDIO_RESULT__";

    private final TranscriptService transcriptService;
    private final PcAgentService pcAgent;
    private final SystemContextProvider systemCtx;

    // Tool beans
    private final SystemTools systemTools;
    private final BrowserTools browserTools;
    private final FileTools fileTools;
    private final FileSystemTools fileSystemTools;
    private final TaskStatusTool taskStatusTool;
    private final ChatHistoryTool chatHistoryTool;
    private final ClipboardTools clipboardTools;
    private final MemoryTools memoryTools;
    private final ToolExecutionNotifier toolNotifier;

    /** Spring AI ChatClient — null when no API key is configured. */
    @Autowired(required = false)
    private ChatClient chatClient;

    /** Spring AI ChatMemory — null when Spring AI is not active. */
    @Autowired(required = false)
    private ChatMemory chatMemory;

    /** Async results from background agent tasks (polled by frontend). */
    private final ConcurrentLinkedQueue<String> asyncResults = new ConcurrentLinkedQueue<>();

    public ChatService(TranscriptService transcriptService,
                       PcAgentService pcAgent,
                       SystemContextProvider systemCtx,
                       SystemTools systemTools,
                       BrowserTools browserTools,
                       FileTools fileTools,
                       FileSystemTools fileSystemTools,
                       TaskStatusTool taskStatusTool,
                       ChatHistoryTool chatHistoryTool,
                       ClipboardTools clipboardTools,
                       MemoryTools memoryTools,
                       ToolExecutionNotifier toolNotifier) {
        this.transcriptService = transcriptService;
        this.pcAgent = pcAgent;
        this.systemCtx = systemCtx;
        this.systemTools = systemTools;
        this.browserTools = browserTools;
        this.fileTools = fileTools;
        this.fileSystemTools = fileSystemTools;
        this.taskStatusTool = taskStatusTool;
        this.chatHistoryTool = chatHistoryTool;
        this.clipboardTools = clipboardTools;
        this.memoryTools = memoryTools;
        this.toolNotifier = toolNotifier;
    }

    @PostConstruct
    public void diagnostics() {
        String cwd = System.getProperty("user.dir");
        File secretsFile = new File(cwd, "application-secrets.properties");
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║  [ChatService] Working directory: {}", cwd);
        log.info("║  [ChatService] Secrets file exists: {} ({})", secretsFile.exists(), secretsFile.getAbsolutePath());
        log.info("║  [ChatService] ChatClient injected: {}", chatClient != null);
        log.info("║  [ChatService] OpenAI API key (audio): {}", (openAiApiKey != null && !openAiApiKey.isBlank()) ? "SET" : "NOT SET");
        log.info("╚══════════════════════════════════════════════════════════════╝");

        // Seed Spring AI ChatMemory with transcript history so AI remembers previous conversations
        seedChatMemory();
    }

    private void seedChatMemory() {
        if (chatMemory == null) {
            log.info("[ChatService] ChatMemory not available — skipping history seed.");
            return;
        }
        var history = transcriptService.getStructuredHistory();
        if (history.isEmpty()) {
            log.info("[ChatService] No chat history to seed into ChatMemory.");
            return;
        }
        java.util.List<Message> messages = new java.util.ArrayList<>();
        for (var entry : history) {
            String text = (String) entry.get("text");
            boolean isUser = (Boolean) entry.get("isUser");
            if (isUser) {
                messages.add(new UserMessage(text));
            } else {
                messages.add(new AssistantMessage(text));
            }
        }
        chatMemory.add("botsfer-local", messages);
        log.info("[ChatService] Seeded ChatMemory with {} messages from transcript history.", messages.size());
    }

    /** Returns and removes the next async result, or null if none. */
    public String pollAsyncResult() {
        return asyncResults.poll();
    }

    /** Returns and removes all pending tool execution status messages. */
    public java.util.List<String> drainToolStatus() {
        return toolNotifier.drain();
    }

    // Audio transcription properties (still uses raw HTTP)
    @Value("${app.openai.api-key:}")
    private String openAiApiKey;
    @Value("${app.openai.base-url:https://api.openai.com/v1}")
    private String openAiBaseUrl;
    @Value("${app.openai.transcription-model:gpt-4o-mini-transcribe}")
    private String openAiTranscriptionModel;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .version(Version.HTTP_1_1)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get bot reply for a given message. Used by the in-app chat and all platform integrations.
     *
     * Flow:
     * 1. If Spring AI ChatClient is available → tool-calling path
     * 2. If unavailable → regex fallback via PcAgentService.tryExecute()
     * 3. If nothing matched → placeholder reply
     */
    public String getReply(String message) {
        if (message == null) {
            message = "";
        }
        String trimmed = message.trim();
        toolNotifier.clear();
        transcriptService.save("USER", trimmed);

        Consumer<String> asyncCallback = result -> {
            transcriptService.save("BOT(agent)", result);
            asyncResults.add(result);
        };

        // 1. Spring AI tool-calling path
        if (chatClient != null) {
            try {
                fileTools.setAsyncCallback(asyncCallback);

                String reply = chatClient.prompt()
                        .system(systemCtx.buildSystemMessage())
                        .user(trimmed)
                        .tools(systemTools, browserTools, fileTools, fileSystemTools, taskStatusTool, chatHistoryTool, clipboardTools, memoryTools)
                        .call()
                        .content();

                if (reply != null && !reply.isBlank()) {
                    transcriptService.save("BOT", reply);
                    return reply;
                }
            } catch (Exception e) {
                log.error("[ChatService] Spring AI error: {}", e.getMessage(), e);
                // Tell the user what went wrong instead of silently falling back
                String errorReply = "AI error: " + e.getMessage();
                transcriptService.save("BOT(error)", errorReply);
                return errorReply;
            }
        }

        // 2. Fallback: regex-based command matching (works without API key)
        String agentReply = pcAgent.tryExecute(trimmed, asyncCallback);
        if (agentReply != null) {
            transcriptService.save("BOT", agentReply);
            return agentReply;
        }

        // 3. No AI configured and no regex match
        if (chatClient == null) {
            String noAiReply = "ChatClient is null. AI is not connected. Set your OpenAI API key in application-secrets.properties (project root) or set the OPENAI_API_KEY environment variable, then restart.";
            transcriptService.save("BOT", noAiReply);
            return noAiReply;
        }

        // 4. AI returned empty — shouldn't normally happen
        String fallback = "I'm not sure how to respond to that. Could you rephrase?";
        transcriptService.save("BOT", fallback);
        return fallback;
    }

    /** Native audio pipeline: WAV -> transcription -> text response. */
    public String getReplyFromAudio(byte[] wavAudio) {
        if (wavAudio == null || wavAudio.length == 0) {
            return "No audio captured.";
        }
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            return "Audio chat is not configured. Set spring.ai.openai.api-key.";
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
            String reply = getReply(transcript);
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

}
