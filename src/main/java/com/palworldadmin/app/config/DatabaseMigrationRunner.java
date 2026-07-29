package com.palworldadmin.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseMigrationRunner implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public DatabaseMigrationRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!palworldServerTableExists()) {
            return;
        }
        jdbc.execute("alter table palworld_server add column if not exists rcon_enabled boolean default false");
        jdbc.execute("alter table palworld_server add column if not exists rcon_host varchar(255)");
        jdbc.execute("alter table palworld_server add column if not exists rcon_port integer");
        jdbc.execute("alter table palworld_server add column if not exists rcon_password varchar(1024)");
        jdbc.execute("alter table palworld_server add column if not exists auto_restart_enabled boolean default false");
        jdbc.execute("alter table palworld_server add column if not exists auto_restart_time varchar(5)");
        jdbc.execute("alter table palworld_server add column if not exists auto_restart_last_warning_date date");
        jdbc.execute("alter table palworld_server add column if not exists auto_restart_last_run_date date");
        jdbc.execute("""
                update palworld_server
                set rcon_enabled = true
                where (rcon_enabled is null or rcon_enabled = false)
                  and rcon_host is not null and trim(rcon_host) <> ''
                  and rcon_port is not null
                  and rcon_password is not null and trim(rcon_password) <> ''
                """);
        jdbc.execute("update palworld_server set rcon_enabled = false where rcon_enabled is null");
        jdbc.execute("update palworld_server set auto_restart_enabled = false where auto_restart_enabled is null");
        if (playerOnlineSnapshotTableExists()) {
            jdbc.execute("create index if not exists idx_player_online_snapshot_captured_at on player_online_snapshot(captured_at)");
            jdbc.execute("create index if not exists idx_player_online_snapshot_server_captured_at on player_online_snapshot(server_id, captured_at)");
            jdbc.execute("create unique index if not exists ux_player_online_snapshot_server_captured_at on player_online_snapshot(server_id, captured_at)");
        }
        if (playerOnlineSnapshotPlayerTableExists()) {
            jdbc.execute("create index if not exists idx_player_online_snapshot_player_snapshot on player_online_snapshot_player(snapshot_id)");
            jdbc.execute("create index if not exists idx_player_online_snapshot_player_id on player_online_snapshot_player(player_id)");
        }
    }

    private boolean palworldServerTableExists() {
        return tableExists("PALWORLD_SERVER");
    }

    private boolean playerOnlineSnapshotTableExists() {
        return tableExists("PLAYER_ONLINE_SNAPSHOT");
    }

    private boolean playerOnlineSnapshotPlayerTableExists() {
        return tableExists("PLAYER_ONLINE_SNAPSHOT_PLAYER");
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.tables where upper(table_name) = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }
}
