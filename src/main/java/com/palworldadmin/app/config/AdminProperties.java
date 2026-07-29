package com.palworldadmin.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "palworld-admin")
public class AdminProperties {
    private InitialAdmin initialAdmin = new InitialAdmin();
    private long commandTimeoutSeconds = 120;
    private long updateTimeoutSeconds = 1800;
    private long restoreTimeoutSeconds = 600;
    private String sudoCommand = "sudo -n";
    private String systemctlCommand = "/usr/bin/systemctl";
    private String journalctlCommand = "/usr/bin/journalctl";
    private String cpCommand = "/usr/bin/cp";
    private String chownCommand = "/usr/bin/chown";
    private String chmodCommand = "/usr/bin/chmod";
    private List<String> corsAllowedOrigins = List.of("http://localhost:5173");
    private List<String> corsAllowedOriginPatterns = List.of(
            "http://localhost:[*]",
            "http://127.0.0.1:[*]",
            "http://192.168.*:[*]",
            "http://10.*:[*]",
            "http://172.*:[*]",
            "https://localhost:[*]",
            "https://127.0.0.1:[*]",
            "https://192.168.*:[*]",
            "https://10.*:[*]",
            "https://172.*:[*]",
            "https://pal.linuxred.lat",
            "https://*.linuxred.lat",
            "https://*.trycloudflare.com",
            "https://*.cfargotunnel.com",
            "https://*.cloudflareaccess.com"
    );

    public InitialAdmin getInitialAdmin() {
        return initialAdmin;
    }

    public void setInitialAdmin(InitialAdmin initialAdmin) {
        this.initialAdmin = initialAdmin;
    }

    public long getCommandTimeoutSeconds() {
        return commandTimeoutSeconds;
    }

    public void setCommandTimeoutSeconds(long commandTimeoutSeconds) {
        this.commandTimeoutSeconds = commandTimeoutSeconds;
    }

    public long getUpdateTimeoutSeconds() {
        return updateTimeoutSeconds;
    }

    public void setUpdateTimeoutSeconds(long updateTimeoutSeconds) {
        this.updateTimeoutSeconds = updateTimeoutSeconds;
    }

    public long getRestoreTimeoutSeconds() {
        return restoreTimeoutSeconds;
    }

    public void setRestoreTimeoutSeconds(long restoreTimeoutSeconds) {
        this.restoreTimeoutSeconds = restoreTimeoutSeconds;
    }

    public String getSudoCommand() {
        return sudoCommand;
    }

    public void setSudoCommand(String sudoCommand) {
        this.sudoCommand = sudoCommand;
    }

    public List<String> sudoCommandParts() {
        if (sudoCommand == null || sudoCommand.isBlank()) {
            return List.of("sudo", "-n");
        }
        return Arrays.stream(sudoCommand.trim().split("\\s+"))
                .filter(part -> !part.isBlank())
                .toList();
    }

    public String getSystemctlCommand() {
        return systemctlCommand;
    }

    public void setSystemctlCommand(String systemctlCommand) {
        this.systemctlCommand = systemctlCommand;
    }

    public String getJournalctlCommand() {
        return journalctlCommand;
    }

    public void setJournalctlCommand(String journalctlCommand) {
        this.journalctlCommand = journalctlCommand;
    }

    public String getCpCommand() {
        return cpCommand;
    }

    public void setCpCommand(String cpCommand) {
        this.cpCommand = cpCommand;
    }

    public String getChownCommand() {
        return chownCommand;
    }

    public void setChownCommand(String chownCommand) {
        this.chownCommand = chownCommand;
    }

    public String getChmodCommand() {
        return chmodCommand;
    }

    public void setChmodCommand(String chmodCommand) {
        this.chmodCommand = chmodCommand;
    }

    public List<String> getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    public List<String> getCorsAllowedOriginPatterns() {
        return corsAllowedOriginPatterns;
    }

    public void setCorsAllowedOriginPatterns(List<String> corsAllowedOriginPatterns) {
        this.corsAllowedOriginPatterns = corsAllowedOriginPatterns;
    }

    public static class InitialAdmin {
        private String username = "admin";
        private String password = "admin";

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
