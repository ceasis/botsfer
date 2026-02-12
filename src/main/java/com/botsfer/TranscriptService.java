package com.botsfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class TranscriptService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptService.class);
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MEMORY_SIZE = 100;

    private Path historyDir;

    /** In-memory ring buffer of the last 100 messages. */
    private final LinkedList<String> recentMemory = new LinkedList<>();

    @PostConstruct
    public void init() throws IOException {
        historyDir = Paths.get(System.getProperty("user.home"), "botsfer_data", "botsfer_history");
        Files.createDirectories(historyDir);
        log.info("Chat history directory: {}", historyDir);
    }

    /**
     * Saves a chat entry to file and in-memory buffer.
     * One file per day: chat_history_yyyymmdd.dat, appended.
     */
    public void save(String speaker, String text) {
        if (text == null || text.isBlank()) return;
        try {
            LocalDateTime now = LocalDateTime.now();
            String filename = "chat_history_" + now.format(FILE_FMT) + ".dat";
            Path file = historyDir.resolve(filename);
            String line = "[" + now.format(TIME_FMT) + "] " + speaker + ": " + text.trim();
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            synchronized (recentMemory) {
                recentMemory.addLast(line);
                while (recentMemory.size() > MEMORY_SIZE) {
                    recentMemory.removeFirst();
                }
            }
        } catch (IOException e) {
            log.error("Failed to save chat history", e);
        }
    }

    /** Returns a snapshot of the last 100 messages from memory. */
    public List<String> getRecentMemory() {
        synchronized (recentMemory) {
            return new ArrayList<>(recentMemory);
        }
    }

    /** Searches in-memory buffer for lines containing the query (case-insensitive). */
    public List<String> searchMemory(String query) {
        String lower = query.toLowerCase();
        synchronized (recentMemory) {
            return recentMemory.stream()
                    .filter(line -> line.toLowerCase().contains(lower))
                    .collect(Collectors.toList());
        }
    }

    /** Searches all chat_history_*.dat files for lines matching the query. Returns up to maxResults lines. */
    public List<String> searchHistoryFiles(String query, int maxResults) {
        List<String> results = new ArrayList<>();
        String lower = query.toLowerCase();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(historyDir, "chat_history_*.dat")) {
            // Collect and sort files newest-first
            List<Path> files = new ArrayList<>();
            stream.forEach(files::add);
            files.sort((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()));

            for (Path file : files) {
                try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
                    lines.filter(line -> line.toLowerCase().contains(lower))
                            .forEach(line -> {
                                if (results.size() < maxResults) results.add(line);
                            });
                }
                if (results.size() >= maxResults) break;
            }
        } catch (IOException e) {
            log.error("Failed to search history files", e);
        }
        return results;
    }

    /** Returns the history directory path. */
    public Path getHistoryDir() {
        return historyDir;
    }
}
