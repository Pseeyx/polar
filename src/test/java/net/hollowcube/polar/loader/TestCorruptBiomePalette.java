package net.hollowcube.polar.loader;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.hollowcube.polar.model.PolarChunk;
import net.hollowcube.polar.model.PolarSection;
import net.hollowcube.polar.model.PolarSection.LightContent;
import net.hollowcube.polar.model.PolarWorld;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.world.DimensionType;
import net.minestom.server.world.biome.Biome;
import org.junit.jupiter.api.Test;

class TestCorruptBiomePalette {

  static {
    MinecraftServer.init();
  }

  @Test
  void invalidBiomePaletteIndexFallsBackToPlains() {
    var world = new PolarWorld();
    int sectionCount = world.maxSection() - world.minSection() + 1;
    var sections = new PolarSection[sectionCount];
    Arrays.fill(sections, new PolarSection());

    var biomePalette = new String[] {"minecraft:plains", "minecraft:desert"};
    var biomeData = new int[PolarSection.BIOME_PALETTE_SIZE];
    Arrays.fill(biomeData, 5);

    sections[4] =
        new PolarSection(
            new String[] {"minecraft:air"},
            null,
            biomePalette,
            biomeData,
            LightContent.MISSING,
            null,
            LightContent.MISSING,
            null);

    world.updateChunkAt(
        0,
        0,
        new PolarChunk(
            0, 0, sections, List.of(), new int[PolarChunk.MAX_HEIGHTMAPS][], new byte[0]));

    var loader = new PolarLoader(world).setWorldAccess(PolarWorldAccess.DEFAULT);
    var instance = new InstanceContainer(UUID.randomUUID(), DimensionType.OVERWORLD, loader);
    int plainsId = PolarWorldAccess.DEFAULT.getBiomeId(Biome.PLAINS.name());

    var chunk = assertDoesNotThrow(() -> loader.loadChunk(instance, 0, 0));
    assertEquals(plainsId, chunk.getSection(0).biomePalette().get(0, 0, 0));
  }
}
