package com.botsfer.agent;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Provides system context (username, OS, time, etc.) for the AI system message.
 */
@Component
public class SystemContextProvider {

    public String buildSystemMessage() {
        String username = System.getProperty("user.name", "unknown");
        String osName = System.getProperty("os.name", "unknown");
        String osVersion = System.getProperty("os.version", "");
        String osArch = System.getProperty("os.arch", "");
        String userHome = System.getProperty("user.home", "");
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (EEEE)"));

        String computerName = "unknown";
        try {
            computerName = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            String env = System.getenv("COMPUTERNAME");
            if (env != null && !env.isBlank()) computerName = env;
        }

        return """
                You are Botsfer, a helpful PC assistant that controls a Windows computer.
                You can run commands, open apps, manage files, search the web, and answer questions.

                SYSTEM CONTEXT:
                - Username: %s
                - Computer name: %s
                - OS: %s %s (%s)
                - Home directory: %s
                - Current date/time: %s

                When the user asks about their system, answer from the context above.
                When they need live system data (IP, disk, RAM, network, etc.), use the runPowerShell or runCmd tools.
                For file paths that include the home directory, use: %s
                Be concise and helpful. Use the available tools to fulfill user requests.
                """.formatted(username, computerName, osName, osVersion, osArch, userHome, now, userHome);
    }
}
