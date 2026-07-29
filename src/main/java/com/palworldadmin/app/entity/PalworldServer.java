package com.palworldadmin.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class PalworldServer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    private ServerType type = ServerType.SYSTEMD;

    private String serviceName;
    private String containerName;
    private String composeProjectName;
    private String linuxUser;
    private String linuxGroup;
    private Integer publicPort;

    @Column(columnDefinition = "boolean default false")
    private Boolean rconEnabled = false;

    private String rconHost;
    private Integer rconPort;

    @Column(length = 1024)
    private String rconPassword;

    @NotBlank
    @Column(nullable = false, length = 1024)
    private String rootPath;

    private String steamcmdPath;

    @Column(length = 2048)
    private String updateCommand;

    @Column(columnDefinition = "boolean default false")
    private Boolean autoRestartEnabled = false;

    @Column(length = 5)
    private String autoRestartTime;

    private LocalDate autoRestartLastWarningDate;
    private LocalDate autoRestartLastRunDate;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean enabled = true;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ServerType getType() {
        return type;
    }

    public void setType(ServerType type) {
        this.type = type;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public String getComposeProjectName() {
        return composeProjectName;
    }

    public void setComposeProjectName(String composeProjectName) {
        this.composeProjectName = composeProjectName;
    }

    public String getLinuxUser() {
        return linuxUser;
    }

    public void setLinuxUser(String linuxUser) {
        this.linuxUser = linuxUser;
    }

    public String getLinuxGroup() {
        return linuxGroup;
    }

    public void setLinuxGroup(String linuxGroup) {
        this.linuxGroup = linuxGroup;
    }

    public Integer getPublicPort() {
        return publicPort;
    }

    public void setPublicPort(Integer publicPort) {
        this.publicPort = publicPort;
    }

    public boolean isRconEnabled() {
        return Boolean.TRUE.equals(rconEnabled);
    }

    public void setRconEnabled(boolean rconEnabled) {
        this.rconEnabled = rconEnabled;
    }

    public void setRconEnabled(Boolean rconEnabled) {
        this.rconEnabled = Boolean.TRUE.equals(rconEnabled);
    }

    public String getRconHost() {
        return rconHost;
    }

    public void setRconHost(String rconHost) {
        this.rconHost = rconHost;
    }

    public Integer getRconPort() {
        return rconPort;
    }

    public void setRconPort(Integer rconPort) {
        this.rconPort = rconPort;
    }

    public String getRconPassword() {
        return rconPassword;
    }

    public void setRconPassword(String rconPassword) {
        this.rconPassword = rconPassword;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public String getSteamcmdPath() {
        return steamcmdPath;
    }

    public void setSteamcmdPath(String steamcmdPath) {
        this.steamcmdPath = steamcmdPath;
    }

    public String getUpdateCommand() {
        return updateCommand;
    }

    public void setUpdateCommand(String updateCommand) {
        this.updateCommand = updateCommand;
    }

    public boolean isAutoRestartEnabled() {
        return Boolean.TRUE.equals(autoRestartEnabled);
    }

    public void setAutoRestartEnabled(boolean autoRestartEnabled) {
        this.autoRestartEnabled = autoRestartEnabled;
    }

    public void setAutoRestartEnabled(Boolean autoRestartEnabled) {
        this.autoRestartEnabled = Boolean.TRUE.equals(autoRestartEnabled);
    }

    public String getAutoRestartTime() {
        return autoRestartTime;
    }

    public void setAutoRestartTime(String autoRestartTime) {
        this.autoRestartTime = autoRestartTime;
    }

    public LocalDate getAutoRestartLastWarningDate() {
        return autoRestartLastWarningDate;
    }

    public void setAutoRestartLastWarningDate(LocalDate autoRestartLastWarningDate) {
        this.autoRestartLastWarningDate = autoRestartLastWarningDate;
    }

    public LocalDate getAutoRestartLastRunDate() {
        return autoRestartLastRunDate;
    }

    public void setAutoRestartLastRunDate(LocalDate autoRestartLastRunDate) {
        this.autoRestartLastRunDate = autoRestartLastRunDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
