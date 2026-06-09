package net.hollowcube.polar.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.jetbrains.annotations.NotNull;

/** Abstraction for persisting serialized polar world bytes. */
public interface PolarByteStorage {
  byte @NotNull [] load() throws IOException;

  void save(byte @NotNull [] data) throws IOException;

  final class FileStorage implements PolarByteStorage {
    private final Path path;

    public FileStorage(@NotNull Path path) {
      this.path = path;
    }

    public @NotNull Path path() {
      return path;
    }

    @Override
    public byte @NotNull [] load() throws IOException {
      return Files.readAllBytes(path);
    }

    @Override
    public void save(byte @NotNull [] data) throws IOException {
      Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
  }
}
