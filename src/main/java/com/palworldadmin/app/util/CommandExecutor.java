package com.palworldadmin.app.util;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class CommandExecutor {

    public CommandResult execute(List<String> command, Duration timeout) {
        Instant started = Instant.now();
        Process process = null;
        try {
            process = new ProcessBuilder(command).start();
            Process running = process;
            CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> read(running.getInputStream()));
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> read(running.getErrorStream()));
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(command, -1, stdout.join(), stderr.join(), Duration.between(started, Instant.now()), true);
            }
            return new CommandResult(command, process.exitValue(), stdout.join(), stderr.join(), Duration.between(started, Instant.now()), false);
        } catch (IOException e) {
            return new CommandResult(command, -1, "", e.getMessage(), Duration.between(started, Instant.now()), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return new CommandResult(command, -1, "", e.getMessage(), Duration.between(started, Instant.now()), false);
        }
    }

    private String read(InputStream input) {
        try (input) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return e.getMessage();
        }
    }
}
