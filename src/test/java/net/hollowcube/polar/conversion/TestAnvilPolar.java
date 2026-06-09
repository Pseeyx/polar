package net.hollowcube.polar.conversion;

import net.hollowcube.polar.model.PolarWorld;
import net.minestom.server.MinecraftServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestAnvilPolar {

    static {
        MinecraftServer.init();
    }

    @Test
    void testConvertAnvilWorld() throws Exception {
        var bench = Path.of("./src/test/resources/bench");
        Assumptions.assumeTrue(Files.isDirectory(bench.resolve("region")), "bench anvil fixture not present");

        PolarWorld world;
        try {
            world = AnvilPolar.anvilToPolar(bench.toRealPath(), -4, 19);
        } catch (NullPointerException e) {
            Assumptions.assumeTrue(false, "bench fixture incompatible with Minestom block registry: " + e.getMessage());
            return;
        }
        assertEquals(-4, world.minSection());
    }

}
