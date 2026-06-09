package net.hollowcube.polar.conversion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestAnvilVersion {

  @Test
  void rejectsMissingLevelDat(@TempDir Path worldDir) {
    var error = assertThrows(IOException.class, () -> AnvilVersion.validateWorld(worldDir));
    assertTrue(error.getMessage().contains("missing level.dat"));
  }

  @Test
  void rejectsWorldWithoutRegionDirectory(@TempDir Path worldDir) throws IOException {
    Files.createDirectory(worldDir.resolve("region"));

    var error = assertThrows(IOException.class, () -> AnvilPolar.anvilToPolar(worldDir));
    assertTrue(error.getMessage().contains("missing level.dat"));
  }

  @Test
  void acceptsBenchFixtureIfPresent() {
    var bench = Path.of("./src/test/resources/bench");
    if (!Files.isRegularFile(bench.resolve("level.dat"))) return;

    assertDoesNotThrow(() -> AnvilVersion.validateWorld(bench));
  }
}
