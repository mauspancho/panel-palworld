package com.palworldadmin.app.util;

import java.time.Duration;
import java.util.List;

public record CommandResult(
        List<String> command,
        int exitCode,
        String stdout,
        String stderr,
        Duration duration,
        boolean timedOut
) {
    public boolean success() {
        return exitCode == 0 && !timedOut;
    }

    public String combinedOutput() {
        if (stderr == null || stderr.isBlank()) {
            return stdout;
        }
        if (stdout == null || stdout.isBlank()) {
            return stderr;
        }
        return stdout + System.lineSeparator() + stderr;
    }
}
