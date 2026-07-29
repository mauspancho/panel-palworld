package com.palworldadmin.app.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServerLogFilterServiceTest {
    private final ServerLogFilterService filter = new ServerLogFilterService();

    @Test
    void removesOnlyRconNoiseLines() {
        String output = String.join("\n",
                "[Info] Player Alice joined the server",
                "[Info] RCON connection accepted",
                "[Info] RCON command ShowPlayers",
                "[Info] RCON connection closed",
                "[Chat] Alice: hola",
                "[Info] Server autosave complete"
        );

        String filtered = filter.compactRconNoise(output);

        assertThat(filtered).contains("Player Alice joined");
        assertThat(filtered).contains("[Chat] Alice: hola");
        assertThat(filtered).contains("Server autosave complete");
        assertThat(filtered).doesNotContain("RCON connection accepted");
        assertThat(filtered).doesNotContain("RCON command ShowPlayers");
        assertThat(filtered).doesNotContain("RCON connection closed");
    }

    @Test
    void keepsNonRconOutputUnchanged() {
        String output = String.join("\n",
                "[Info] Player Bob joined the server",
                "[Chat] Admin: reinicio en 5 minutos"
        );

        assertThat(filter.compactRconNoise(output).replace("\r\n", "\n")).isEqualTo(output);
    }

    @Test
    void tailsAfterRemovingRconNoise() {
        String output = String.join("\n",
                "[Info] Linea util 1",
                "[Info] RCON command ShowPlayers",
                "[Info] RCON connection accepted",
                "[Info] Linea util 2",
                "[Info] RCON connection closed",
                "[Chat] Admin: hola",
                "[Info] Linea util 4"
        );

        String filtered = filter.compactRconNoiseAndTail(output, 3).replace("\r\n", "\n");

        assertThat(filtered).isEqualTo(String.join("\n",
                "[Info] Linea util 2",
                "[Chat] Admin: hola",
                "[Info] Linea util 4"
        ));
    }
}
