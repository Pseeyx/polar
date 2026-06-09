package net.hollowcube.polar.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import net.hollowcube.polar.io.PolarReader;
import net.hollowcube.polar.io.PolarWriter;
import net.hollowcube.polar.model.PolarWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestPolarByteStorage {

  @Test
  void fileStorageRoundTrip(@TempDir Path tempDir) throws Exception {
    var path = tempDir.resolve("world.polar");
    var storage = new PolarByteStorage.FileStorage(path);
    var world = PolarWorld.empty();

    storage.save(PolarWriter.write(world));

    var loader =
        PolarLoader.builder()
            .byteStorage(storage)
            .worldData(PolarReader.read(storage.load()))
            .build();
    loader.saveChunks(java.util.List.of());

    assertEquals(world.minSection(), PolarReader.read(storage.load()).minSection());
  }
}
