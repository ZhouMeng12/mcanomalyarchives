package net.mcreator.mcanomalyarchives.anomaly.nametag;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NamedMaterialRulesTest {
    @Test void liquidExpiresAtElevenMinutesAndChildrenInheritRemainingLife() {
        int age = 0, ticks = 0;
        while (age >= 0) {
            age = NamedMaterialRules.nextLiquidAge(age); ticks += NamedMaterialRules.LIQUID_STEP_TICKS;
            assertTrue(ticks <= 13200);
        }
        assertEquals(13200, ticks);
        assertEquals(-1, NamedMaterialRules.nextLiquidAge(131));
        assertEquals(131, NamedMaterialRules.nextLiquidAge(130));
    }
    @Test void packagedWaterNameProvidesTheSupportedBlockToken() throws Exception {
        // No Minecraft registry bootstrap in plain JUnit; validate the shipped index, not a mock registry.
        try (var stream = getClass().getResourceAsStream("/data/mcanomalyarchives/name_index/zh_cn.json")) {
            assertNotNull(stream);
            var index = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("block:minecraft:water", index.getAsJsonArray("水").get(0).getAsString());
        }
    }
}
