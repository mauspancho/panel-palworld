package com.palworldadmin.app.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IniParser {
    public static final String SETTINGS_SECTION = "[/Script/Pal.PalGameWorldSettings]";
    public static final String OPTION_PREFIX = "OptionSettings=";

    private IniParser() {
    }

    public static Map<String, String> parseOptionSettings(String content) {
        OptionRange range = findOptionRange(content);
        Map<String, String> values = new LinkedHashMap<>();
        if (range == null) {
            return values;
        }
        for (String token : splitTopLevel(range.payload())) {
            int equals = token.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            values.put(token.substring(0, equals).trim(), token.substring(equals + 1).trim());
        }
        return values;
    }

    public static String updateOptionSettings(String original, Map<String, String> updates) {
        if (original == null || original.isBlank()) {
            return renderContent(original, updates);
        }
        OptionRange range = findOptionRange(original);
        if (range == null) {
            if (original.contains(OPTION_PREFIX)) {
                throw new IllegalArgumentException("No se pudo leer OptionSettings completo. Use modo avanzado para revisar el archivo.");
            }
            return renderContent(original, updates);
        }

        List<String> tokens = splitTopLevel(range.payload());
        List<String> updatedTokens = new ArrayList<>();
        List<String> applied = new ArrayList<>();
        for (String token : tokens) {
            int equals = token.indexOf('=');
            if (equals <= 0) {
                updatedTokens.add(token);
                continue;
            }
            String key = token.substring(0, equals).trim();
            String update = updates.get(key);
            if (update == null) {
                updatedTokens.add(token);
            } else {
                updatedTokens.add(token.substring(0, equals + 1) + update);
                applied.add(key);
            }
        }
        updates.forEach((key, value) -> {
            if (value != null && !applied.contains(key)) {
                updatedTokens.add(key + "=" + value);
            }
        });

        String optionLine = OPTION_PREFIX + "(" + String.join(",", updatedTokens) + ")";
        return original.substring(0, range.lineStart()) + optionLine + original.substring(range.closeIndex() + 1);
    }

    public static String renderContent(String original, Map<String, String> values) {
        String optionLine = OPTION_PREFIX + "(" + renderOptions(values) + ")";
        if (original == null || original.isBlank()) {
            return SETTINGS_SECTION + System.lineSeparator() + optionLine + System.lineSeparator();
        }
        OptionRange range = findOptionRange(original);
        if (range != null) {
            return original.substring(0, range.lineStart()) + optionLine + original.substring(range.closeIndex() + 1);
        }
        if (original.contains(SETTINGS_SECTION)) {
            return original.replace(SETTINGS_SECTION, SETTINGS_SECTION + System.lineSeparator() + optionLine);
        }
        return SETTINGS_SECTION + System.lineSeparator() + optionLine + System.lineSeparator() + original;
    }

    private static OptionRange findOptionRange(String content) {
        if (content == null) {
            return null;
        }
        int prefix = content.indexOf(OPTION_PREFIX);
        if (prefix < 0) {
            return null;
        }
        int open = content.indexOf('(', prefix + OPTION_PREFIX.length());
        if (open < 0) {
            return null;
        }

        boolean quoted = false;
        int depth = 0;
        for (int i = open; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '"' && (i == 0 || content.charAt(i - 1) != '\\')) {
                quoted = !quoted;
            }
            if (quoted) {
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    int lineStart = content.lastIndexOf('\n', prefix);
                    lineStart = lineStart < 0 ? prefix : lineStart + 1;
                    return new OptionRange(lineStart, open, i, content.substring(open + 1, i));
                }
            }
        }
        return null;
    }

    private static List<String> splitTopLevel(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                quoted = !quoted;
            }
            if (!quoted) {
                if (c == '(' || c == '[' || c == '{') {
                    depth++;
                } else if ((c == ')' || c == ']' || c == '}') && depth > 0) {
                    depth--;
                }
            }
            if (c == ',' && !quoted && depth == 0) {
                tokens.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static String renderOptions(Map<String, String> values) {
        return values.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private record OptionRange(int lineStart, int openIndex, int closeIndex, String payload) {
    }
}
