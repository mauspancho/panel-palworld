package com.palworldadmin.app.entity;

public enum ServerType {
    DOCKER,
    SYSTEMD,
    SYSTEM;

    public boolean isSystemd() {
        return this == SYSTEMD || this == SYSTEM;
    }
}
