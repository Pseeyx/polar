package net.hollowcube.polar.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.List;
import net.hollowcube.polar.model.PolarChunk;
import net.hollowcube.polar.model.PolarSection;
import net.hollowcube.polar.model.PolarWorld;
import org.junit.jupiter.api.Test;

class TestZigZagChunkCoords {

  @Test
  void roundTripNegativeChunkCoordinates() {
    var world = PolarWorld.builder().build();
    var sections = new PolarSection[world.sectionCount()];
    Arrays.fill(sections, new PolarSection());

    world.updateChunkAt(
        -3,
        7,
        new PolarChunk(
            -3, 7, sections, List.of(), new int[PolarChunk.MAX_HEIGHTMAPS][], new byte[0]));
    world.updateChunkAt(
        12,
        -8,
        new PolarChunk(
            12, -8, sections, List.of(), new int[PolarChunk.MAX_HEIGHTMAPS][], new byte[0]));

    var roundTrip = PolarReader.read(PolarWriter.write(world));

    assertNotNull(roundTrip.chunkAt(-3, 7));
    assertNotNull(roundTrip.chunkAt(12, -8));
    assertEquals(PolarWorld.LATEST_VERSION, roundTrip.version());
  }
}
