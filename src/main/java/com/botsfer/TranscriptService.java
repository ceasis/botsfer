package com.botsfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class TranscriptService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptService.class);
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Path transcriptDir;

    @PostConstruct
    public void init() throws IOException {
        transcriptDir = Paths.get(System.getProperty("user.home"), "botsfer_data", "transcriptions");
        Files.createDirectories(transcriptDir);
        log.info("Transcript directory: {}", transcriptDir);
    }

    /**
     * Saves a transcript entry. One file per day, appended.
     */
    public void save(String speaker, String text) {
        if (text == null || text.isBlank()) return;
        try {
            LocalDateTime now = LocalDateTime.now();
            String filename = now.format(FILE_FMT) + ".txt";
            Path file = transcriptDir.resolve(filename);
            String line = "[" + now.format(TIME_FMT) + "] " + speaker + ": " + text.trim() + System.lineSeparator();
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to save transcript", e);
        }
    }
}
