package com.palworldadmin.app.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
public class PlayerOnlineSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private PalworldServer server;

    private LocalDateTime capturedAt;
    private int playerCount;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlayerOnlineSnapshotPlayer> players = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public PalworldServer getServer() {
        return server;
    }

    public void setServer(PalworldServer server) {
        this.server = server;
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(LocalDateTime capturedAt) {
        this.capturedAt = capturedAt;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public void setPlayerCount(int playerCount) {
        this.playerCount = playerCount;
    }

    public List<PlayerOnlineSnapshotPlayer> getPlayers() {
        return players;
    }

    public void addPlayer(PlayerOnlineSnapshotPlayer player) {
        player.setSnapshot(this);
        players.add(player);
    }
}
