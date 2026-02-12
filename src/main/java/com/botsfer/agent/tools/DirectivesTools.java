package com.botsfer.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Tools for reading and updating the user's primary directives file.
 * The directives are loaded into the AI system prompt on every request,
 * so changes take effect immediately.
 */
@Component
public class DirectivesTools {

    private static final Path DIRECTIVES_FILE =
            Paths.get(System.getProperty("user.home"), "botsfer_data", "primary_directives.dat");

    private final ToolExecutionNotifier notifier;

    public DirectivesTools(ToolExecutionNotifier notifier) {
        this.notifier = notifier;
    }

    @Tool(description = "Read the user's primary directives. These are persistent instructions that guide your behavior across all conversations. Use when the user asks to see their current directives.")
    public String getDirectives() {
        notifier.notify("Reading directives...");
        try {
            if (!Files.exists(DIRECTIVES_FILE)) {
                return "No directives set yet. The user can ask you to set directives.";
            }
            String content = Files.readString(DIRECTIVES_FILE, StandardCharsets.UTF_8).trim();
            return content.isEmpty() ? "Directives file is empty." : content;
        } catch (IOException e) {
            return "Failed to read directives: " + e.getMessage();
        }
    }

    @Tool(description = "Set or replace the user's primary directives. These are persistent instructions that guide your behavior (e.g. 'always respond in Spanish', 'call me Boss'). This overwrites the entire directives file.")
    public String setDirectives(
            @ToolParam(description = "The full directives text to save") String directives) {
        notifier.notify("Updating directives...");
        try {
            Files.createDirectories(DIRECTIVES_FILE.getParent());
            Files.writeString(DIRECTIVES_FILE, directives != null ? directives.trim() : "", StandardCharsets.UTF_8);
            return "Directives updated. They will be applied to all future conversations.";
        } catch (IOException e) {
            return "Failed to save directives: " + e.getMessage();
        }
    }

    @Tool(description = "Append a line or paragraph to the existing directives without replacing them.")
    public String appendDirective(
            @ToolParam(description = "The directive text to add") String directive) {
        notifier.notify("Adding directive...");
        try {
            Files.createDirectories(DIRECTIVES_FILE.getParent());
            String existing = "";
            if (Files.exists(DIRECTIVES_FILE)) {
                existing = Files.readString(DIRECTIVES_FILE, StandardCharsets.UTF_8).trim();
            }
            String updated = existing.isEmpty() ? directive.trim() : existing + "\n" + directive.trim();
            Files.writeString(DIRECTIVES_FILE, updated, StandardCharsets.UTF_8);
            return "Directive added. Current directives:\n" + updated;
        } catch (IOException e) {
            return "Failed to append directive: " + e.getMessage();
        }
    }

    @Tool(description = "Clear all primary directives, removing all custom behavior instructions.")
    public String clearDirectives() {
        notifier.notify("Clearing directives...");
        try {
            if (Files.exists(DIRECTIVES_FILE)) {
                Files.delete(DIRECTIVES_FILE);
            }
            return "All directives cleared.";
        } catch (IOException e) {
            return "Failed to clear directives: " + e.getMessage();
        }
    }

    /** Called by SystemContextProvider to include directives in the system prompt. */
    public static String loadDirectivesForPrompt() {
        try {
            if (Files.exists(DIRECTIVES_FILE)) {
                String content = Files.readString(DIRECTIVES_FILE, StandardCharsets.UTF_8).trim();
                if (!content.isEmpty()) return content;
            }
        } catch (IOException ignored) {
        }
        return null;
    }
}
