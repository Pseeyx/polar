package net.hollowcube.polar.io;

import static net.minestom.server.network.NetworkBuffer.VAR_INT;

import lombok.experimental.UtilityClass;
import net.hollowcube.polar.model.PolarWorld;
import net.minestom.server.network.NetworkBuffer;
import org.jetbrains.annotations.NotNull;

@UtilityClass
public final class VarIntUtil {
  public static void writeChunkCoord(@NotNull NetworkBuffer buffer, int value, short version) {
    if (version >= PolarWorld.VERSION_ZIGZAG_CHUNK_COORDS) {
      buffer.write(VAR_INT, encodeZigZag32(value));
    } else {
      buffer.write(VAR_INT, value);
    }
  }

  public static int readChunkCoord(@NotNull NetworkBuffer buffer, short version) {
    int value = buffer.read(VAR_INT);
    if (version >= PolarWorld.VERSION_ZIGZAG_CHUNK_COORDS) {
      return decodeZigZag32(value);
    }
    return value;
  }

  static int encodeZigZag32(int value) {
    return (value << 1) ^ (value >> 31);
  }

  static int decodeZigZag32(int value) {
    return (value >>> 1) ^ -(value & 1);
  }
}
