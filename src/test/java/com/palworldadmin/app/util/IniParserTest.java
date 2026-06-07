package com.palworldadmin.app.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class IniParserTest {

    @Test
    void parsesQuotedCommas() {
        String content = "[/Script/Pal.PalGameWorldSettings]\n" +
                "OptionSettings=(ServerName=\"Uno,Dos\",ServerPlayerMaxNum=32,RCONEnabled=True)";

        var values = IniParser.parseOptionSettings(content);

        assertThat(values).containsEntry("ServerName", "\"Uno,Dos\"");
        assertThat(values).containsEntry("ServerPlayerMaxNum", "32");
        assertThat(values).containsEntry("RCONEnabled", "True");
    }

    @Test
    void rendersSectionWhenMissing() {
        var values = new LinkedHashMap<String, String>();
        values.put("ServerName", "\"Servidor\"");

        String rendered = IniParser.renderContent("", values);

        assertThat(rendered).contains(IniParser.SETTINGS_SECTION);
        assertThat(rendered).contains("OptionSettings=(ServerName=\"Servidor\")");
    }

    @Test
    void updatesOnlySubmittedKeysAndPreservesUnknownSettings() {
        String content = "[/Script/Pal.PalGameWorldSettings]\n" +
                "OptionSettings=(ServerName=\"Viejo\",NestedValue=(X=1,Y=2),UnknownSetting=KeepMe,ServerPlayerMaxNum=32,LastValue=True)\n";
        var updates = new LinkedHashMap<String, String>();
        updates.put("ServerName", "\"Nuevo\"");

        String rendered = IniParser.updateOptionSettings(content, updates);

        assertThat(rendered).contains("ServerName=\"Nuevo\"");
        assertThat(rendered).contains("NestedValue=(X=1,Y=2)");
        assertThat(rendered).contains("UnknownSetting=KeepMe");
        assertThat(rendered).contains("ServerPlayerMaxNum=32");
        assertThat(rendered).contains("LastValue=True");
    }

    @Test
    void parsesFullPayloadWhenValuesContainParentheses() {
        String content = "[/Script/Pal.PalGameWorldSettings]\n" +
                "OptionSettings=(ServerName=\"Uno\",NestedValue=(X=1,Y=2),LastValue=True)";

        var values = IniParser.parseOptionSettings(content);

        assertThat(values).containsEntry("ServerName", "\"Uno\"");
        assertThat(values).containsEntry("NestedValue", "(X=1,Y=2)");
        assertThat(values).containsEntry("LastValue", "True");
    }

    @Test
    void parsesRepresentativePalworldSettingsUntilLastField() {
        String content = "[/Script/Pal.PalGameWorldSettings]\n" +
                "OptionSettings=(Difficulty=None,RandomizerSeed=\"\",DayTimeSpeedRate=1.000000,ServerName=\"ExampleServer\",ServerDescription=\"Example description\",ServerPassword=\"\",CrossplayPlatforms=(Steam,Xbox,PS5,Mac),DenyTechnologyList=,AdditionalDropItemWhenPlayerKillingInPvPMode=\"PlayerDropItem\",bAllowEnhanceStat_WorkSpeed=True)";

        var values = IniParser.parseOptionSettings(content);

        assertThat(values).containsEntry("Difficulty", "None");
        assertThat(values).containsEntry("RandomizerSeed", "\"\"");
        assertThat(values).containsEntry("ServerName", "\"ExampleServer\"");
        assertThat(values).containsEntry("CrossplayPlatforms", "(Steam,Xbox,PS5,Mac)");
        assertThat(values).containsEntry("DenyTechnologyList", "");
        assertThat(values).containsEntry("AdditionalDropItemWhenPlayerKillingInPvPMode", "\"PlayerDropItem\"");
        assertThat(values).containsEntry("bAllowEnhanceStat_WorkSpeed", "True");
    }

    @Test
    void updatesRepresentativePalworldSettingsWithoutDroppingTail() {
        String content = "[/Script/Pal.PalGameWorldSettings]\n" +
                "OptionSettings=(Difficulty=None,ServerName=\"ExampleServer\",CrossplayPlatforms=(Steam,Xbox,PS5,Mac),DenyTechnologyList=,bAllowEnhanceStat_WorkSpeed=True)";
        var updates = new LinkedHashMap<String, String>();
        updates.put("ServerName", "\"Nuevo\"");

        String rendered = IniParser.updateOptionSettings(content, updates);

        assertThat(rendered).contains("ServerName=\"Nuevo\"");
        assertThat(rendered).contains("Difficulty=None");
        assertThat(rendered).contains("CrossplayPlatforms=(Steam,Xbox,PS5,Mac)");
        assertThat(rendered).contains("DenyTechnologyList=");
        assertThat(rendered).contains("bAllowEnhanceStat_WorkSpeed=True");
    }
}
