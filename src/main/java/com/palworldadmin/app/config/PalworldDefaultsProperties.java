package com.palworldadmin.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "palworld.defaults")
public class PalworldDefaultsProperties {
    private String runUser = "palworld";
    private String runGroup = "palworld";
    private String basePath = "/opt/palworld-servers";
    private String steamcmdPath = "/usr/games/steamcmd";
    private Integer publicPort = 8211;
    private boolean usePerfThreads = true;
    private boolean publicLobby = true;

    public String getRunUser() {
        return runUser;
    }

    public void setRunUser(String runUser) {
        this.runUser = runUser;
    }

    public String getRunGroup() {
        return runGroup;
    }

    public void setRunGroup(String runGroup) {
        this.runGroup = runGroup;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getSteamcmdPath() {
        return steamcmdPath;
    }

    public void setSteamcmdPath(String steamcmdPath) {
        this.steamcmdPath = steamcmdPath;
    }

    public Integer getPublicPort() {
        return publicPort;
    }

    public void setPublicPort(Integer publicPort) {
        this.publicPort = publicPort;
    }

    public boolean isUsePerfThreads() {
        return usePerfThreads;
    }

    public void setUsePerfThreads(boolean usePerfThreads) {
        this.usePerfThreads = usePerfThreads;
    }

    public boolean isPublicLobby() {
        return publicLobby;
    }

    public void setPublicLobby(boolean publicLobby) {
        this.publicLobby = publicLobby;
    }
}
