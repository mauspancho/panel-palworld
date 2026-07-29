package com.palworldadmin.app.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
public class RconWelcomeMessageConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "server_id", nullable = false, unique = true)
    private PalworldServer server;

    @Column(columnDefinition = "boolean default false")
    private Boolean enabled = false;

    @Column(nullable = false)
    private Integer delaySeconds = 20;

    @Column(nullable = false)
    private Integer nextMessageIndex = 0;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "rcon_welcome_message", joinColumns = @JoinColumn(name = "config_id"))
    @OrderColumn(name = "message_order")
    @Column(name = "message", length = 300)
    private List<String> messages = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        normalizeDefaults();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        normalizeDefaults();
    }

    public Long getId() {
        return id;
    }

    public PalworldServer getServer() {
        return server;
    }

    public void setServer(PalworldServer server) {
        this.server = server;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getDelaySeconds() {
        return delaySeconds == null ? 20 : delaySeconds;
    }

    public void setDelaySeconds(Integer delaySeconds) {
        this.delaySeconds = delaySeconds;
    }

    public Integer getNextMessageIndex() {
        return nextMessageIndex == null ? 0 : nextMessageIndex;
    }

    public void setNextMessageIndex(Integer nextMessageIndex) {
        this.nextMessageIndex = nextMessageIndex;
    }

    public List<String> getMessages() {
        return messages;
    }

    public void setMessages(List<String> messages) {
        this.messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    private void normalizeDefaults() {
        if (enabled == null) {
            enabled = false;
        }
        if (delaySeconds == null) {
            delaySeconds = 20;
        }
        if (nextMessageIndex == null || nextMessageIndex < 0) {
            nextMessageIndex = 0;
        }
        if (messages == null) {
            messages = new ArrayList<>();
        }
    }
}
