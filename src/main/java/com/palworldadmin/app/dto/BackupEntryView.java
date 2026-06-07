package com.palworldadmin.app.dto;

import java.time.LocalDateTime;

public record BackupEntryView(
        String worldId,
        String relativePath,
        String absolutePath,
        LocalDateTime detectedDate,
        long sizeBytes
) {
    public String sizeLabel() {
        if (sizeBytes > 1024L * 1024L * 1024L) {
            return String.format("%.2f GB", sizeBytes / 1024d / 1024d / 1024d);
        }
        if (sizeBytes > 1024L * 1024L) {
            return String.format("%.2f MB", sizeBytes / 1024d / 1024d);
        }
        if (sizeBytes > 1024L) {
            return String.format("%.2f KB", sizeBytes / 1024d);
        }
        return sizeBytes + " B";
    }
}
