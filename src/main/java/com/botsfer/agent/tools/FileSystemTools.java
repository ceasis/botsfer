package com.botsfer.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.*;

@Component
public class FileSystemTools {

    private final ToolExecutionNotifier notifier;

    public FileSystemTools(ToolExecutionNotifier notifier) {
        this.notifier = notifier;
    }

    @Tool(description = "Open a file or folder on the PC by its full path in file explorer")
    public String openPath(
            @ToolParam(description = "Full path to the file or folder to open") String path) {
        notifier.notify("Opening " + path + "...");
        try {
            Path p = Paths.get(path).toAbsolutePath();
            if (!Files.exists(p)) {
                return "Path not found: " + p;
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(p.toFile());
                return "Opened: " + p;
            }
            new ProcessBuilder("explorer.exe", p.toString()).start();
            return "Opened: " + p;
        } catch (Exception e) {
            return "Failed to open: " + e.getMessage();
        }
    }

    @Tool(description = "Copy a file from a source path to a destination path")
    public String copyFile(
            @ToolParam(description = "Source file path") String source,
            @ToolParam(description = "Destination file or folder path") String destination) {
        notifier.notify("Copying file to " + destination + "...");
        try {
            Path src = Paths.get(source).toAbsolutePath();
            Path dst = Paths.get(destination).toAbsolutePath();
            if (!Files.exists(src)) {
                return "Source not found: " + src;
            }
            if (Files.isDirectory(dst)) {
                dst = dst.resolve(src.getFileName());
            }
            Files.createDirectories(dst.getParent());
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            return "Copied " + src.getFileName() + " to " + dst;
        } catch (IOException e) {
            return "Copy failed: " + e.getMessage();
        }
    }

    @Tool(description = "Delete a single file by its full path (cannot delete directories)")
    public String deleteFile(
            @ToolParam(description = "Full path to the file to delete") String path) {
        notifier.notify("Deleting " + path + "...");
        try {
            Path p = Paths.get(path).toAbsolutePath();
            if (!Files.exists(p)) {
                return "File not found: " + p;
            }
            if (Files.isDirectory(p)) {
                return "I can only delete individual files, not directories. Path: " + p;
            }
            long size = Files.size(p);
            Files.delete(p);
            return "Deleted: " + p + " (" + FileTools.formatSize(size) + ")";
        } catch (IOException e) {
            return "Delete failed: " + e.getMessage();
        }
    }

    @Tool(description = "Count how many files and directories are in a given directory (non-recursive, top-level only)")
    public String countDirectoryContents(
            @ToolParam(description = "Full path to the directory to inspect") String path) {
        notifier.notify("Counting contents of " + path + "...");
        try {
            Path dir = Paths.get(path).toAbsolutePath();
            if (!Files.exists(dir)) {
                return "Path not found: " + dir;
            }
            if (!Files.isDirectory(dir)) {
                return "Not a directory: " + dir;
            }
            long fileCount = 0;
            long dirCount = 0;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        dirCount++;
                    } else {
                        fileCount++;
                    }
                }
            }
            return dir + " contains " + fileCount + " file(s) and " + dirCount + " folder(s).";
        } catch (IOException e) {
            return "Failed to read directory: " + e.getMessage();
        }
    }
}
