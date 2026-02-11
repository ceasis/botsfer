package com.botsfer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    @Value("${app.chat.placeholder.enabled:true}")
    private boolean placeholderEnabled;

    /**
     * Get bot reply for a given message. Used by the in-app chat and all platform integrations.
     */
    public String getReply(String message) {
        if (message == null) {
            message = "";
        }
        return placeholderEnabled ? getPlaceholderReply(message.trim()) : "Chat not configured.";
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
