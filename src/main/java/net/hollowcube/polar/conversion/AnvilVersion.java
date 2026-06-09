package net.hollowcube.polar.conversion;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.utils.nbt.BinaryTagReader;
import org.jetbrains.annotations.NotNull;

/** Utilities for validating anvil world compatibility before conversion. */
@UtilityClass
public class AnvilVersion {
  /**
   * Minimum supported {@code DataVersion} from {@code level.dat}. Corresponds to Minecraft 1.18,
   * when the -64..320 world height was introduced.
   */
  public static final int MIN_DATA_VERSION = 2860;

  /**
   * Reads {@code level.dat} and rejects worlds older than {@link #MIN_DATA_VERSION}.
   *
   * @throws IOException if the world is missing, unreadable, or too old to convert reliably
   */
  public static void validateWorld(@NotNull Path worldDirectory) throws IOException {
    Path levelDat = worldDirectory.resolve("level.dat");
    if (!Files.isRegularFile(levelDat)) {
      throw new IOException("Not a valid anvil world: missing level.dat at " + levelDat);
    }

    int dataVersion;
    try (var input = new GZIPInputStream(Files.newInputStream(levelDat));
        var dataInput = new DataInputStream(input)) {
      var tag = new BinaryTagReader(dataInput).readNamed().getValue();
      if (!(tag instanceof CompoundBinaryTag compound) || !compound.contains("DataVersion")) {
        throw new IOException("Not a valid anvil world: level.dat is missing DataVersion");
      }
      dataVersion = compound.getInt("DataVersion");
    }

    if (dataVersion < MIN_DATA_VERSION) {
      throw new IOException(
          "Anvil world DataVersion "
              + dataVersion
              + " is too old (minimum supported: "
              + MIN_DATA_VERSION
              + ", Minecraft 1.18+). Upgrade the world before converting to Polar.");
    }
  }
}
