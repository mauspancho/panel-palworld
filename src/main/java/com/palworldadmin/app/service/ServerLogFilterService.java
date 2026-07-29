package com.palworldadmin.app.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class ServerLogFilterService {
    public String compactRconNoise(String output) {
        if (output == null || output.isBlank()) {
            return output == null ? "" : output;
        }

        return output.lines()
                .filter(line -> !isRconNoise(line))
                .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
    }

    public String compactRconNoiseAndTail(String output, int visibleLines) {
        String filtered = compactRconNoise(output);
        if (filtered.isBlank()) {
            return filtered;
        }
        int safeVisibleLines = Math.max(1, visibleLines);
        List<String> lines = filtered.lines().toList();
        int start = Math.max(0, lines.size() - safeVisibleLines);
        return String.join(System.lineSeparator(), lines.subList(start, lines.size()));
    }

    private boolean isRconNoise(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String normalized = line.toLowerCase(Locale.ROOT);
        if (isPlayerOrChatLine(normalized)) {
            return false;
        }
        return normalized.contains("rcon")
                || normalized.contains("showplayers")
                || normalized.contains("remote console");
    }

    private boolean isPlayerOrChatLine(String normalized) {
        return normalized.contains("chat")
                || normalized.contains("join")
                || normalized.contains("joined")
                || normalized.contains("leave")
                || normalized.contains("left")
                || normalized.contains("login")
                || normalized.contains("logout");
    }
}
