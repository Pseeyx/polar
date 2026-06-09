package net.hollowcube.polar.model;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;
import net.hollowcube.polar.WorldHeightUtil;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.world.DimensionType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static net.minestom.server.instance.Chunk.CHUNK_SECTION_SIZE;

/**
 * A Java type representing the latest version of the world format.
 */
@SuppressWarnings("UnstableApiUsage")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PolarWorld {
    public static final int MAGIC_NUMBER = 0x506F6C72; // `Polr`
    public static final short LATEST_VERSION = 7;

    @ApiStatus.Internal
    public static final short VERSION_UNIFIED_LIGHT = 1;
    @ApiStatus.Internal
    public static final short VERSION_USERDATA_OPT_BLOCK_ENT_NBT = 2;
    @ApiStatus.Internal
    public static final short VERSION_MINESTOM_NBT_READ_BREAK = 3;
    @ApiStatus.Internal
    public static final short VERSION_WORLD_USERDATA = 4;
    @ApiStatus.Internal
    public static final short VERSION_SHORT_GRASS = 5; // >:(
    @ApiStatus.Internal
    public static final short VERSION_DATA_CONVERTER = 6;
    @ApiStatus.Internal
    public static final short VERSION_IMPROVED_LIGHT = 7;

    public static CompressionType DEFAULT_COMPRESSION = CompressionType.ZSTD;

    @Getter
    @Accessors(fluent = true)
    final short version;
    @Getter
    @Accessors(fluent = true)
    final int dataVersion;
    final Long2ObjectMap<PolarChunk> chunks = new Long2ObjectOpenHashMap<>();
    @Getter
    @Accessors(fluent = true)
    CompressionType compression;
    @Getter
    @Accessors(fluent = true)
    byte minSection;
    @Getter
    @Accessors(fluent = true)
    byte maxSection;
    @Getter
    @Setter
    @Accessors(fluent = true)
    byte @NotNull [] userData;

    public PolarWorld() {
        this(builder());
    }

    public PolarWorld(@NotNull DimensionType dimensionType) {
        this(from(dimensionType));
    }

    public PolarWorld(
            short version,
            int dataVersion,
            @NotNull CompressionType compression,
            byte minSection, byte maxSection,
            byte @NotNull [] userData,
            @Nullable List<PolarChunk> chunks
    ) {
        this.version = version;
        this.dataVersion = dataVersion;
        this.compression = compression;
        this.minSection = minSection;
        this.maxSection = maxSection;
        this.userData = userData;

        if (chunks != null) {
            for (var chunk : chunks) {
                updateChunkAt(chunk.x(), chunk.z(), chunk);
            }
        }
    }

    private PolarWorld(@NotNull Builder builder) {
        this(
                builder.version,
                builder.dataVersion,
                builder.compression,
                builder.minSection,
                builder.maxSection,
                builder.userData,
                builder.chunks
        );
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static @NotNull PolarWorld empty() {
        return builder().build();
    }

    public static @NotNull Builder from(@NotNull DimensionType dimensionType) {
        return builder()
                .minSection((byte) (dimensionType.minY() / CHUNK_SECTION_SIZE))
                .maxSection((byte) (dimensionType.maxY() / CHUNK_SECTION_SIZE - 1));
    }

    public int sectionCount() {
        return maxSection - minSection + 1;
    }

    public void setCompression(@NotNull CompressionType compression) {
        this.compression = compression;
    }

    public void setSectionCount(byte minSection, byte maxSection) {
        for (long l : chunks.keySet()) {
            chunks.put(l, WorldHeightUtil.updateChunkHeight(chunks.get(l), minSection, maxSection));
        }

        this.minSection = minSection;
        this.maxSection = maxSection;
    }

    public @Nullable PolarChunk chunkAt(int x, int z) {
        return chunks.getOrDefault(CoordConversion.chunkIndex(x, z), null);
    }

    public void updateChunkAt(int x, int z, @NotNull PolarChunk chunk) {
        chunks.put(CoordConversion.chunkIndex(x, z), chunk);
    }

    public @NotNull Collection<PolarChunk> chunks() {
        return chunks.values();
    }

    public static final class Builder {
        short version = LATEST_VERSION;
        int dataVersion = MinecraftServer.DATA_VERSION;
        CompressionType compression = DEFAULT_COMPRESSION;
        byte minSection = -4;
        byte maxSection = 19;
        byte @NotNull [] userData = new byte[0];
        final List<PolarChunk> chunks = new ArrayList<>();

        public @NotNull Builder version(short version) {
            this.version = version;
            return this;
        }

        public @NotNull Builder dataVersion(int dataVersion) {
            this.dataVersion = dataVersion;
            return this;
        }

        public @NotNull Builder compression(@NotNull CompressionType compression) {
            this.compression = compression;
            return this;
        }

        public @NotNull Builder minSection(byte minSection) {
            this.minSection = minSection;
            return this;
        }

        public @NotNull Builder maxSection(byte maxSection) {
            this.maxSection = maxSection;
            return this;
        }

        public @NotNull Builder userData(byte @NotNull [] userData) {
            this.userData = userData;
            return this;
        }

        public @NotNull Builder chunk(@NotNull PolarChunk chunk) {
            this.chunks.add(chunk);
            return this;
        }

        public @NotNull Builder chunks(@NotNull List<PolarChunk> chunks) {
            this.chunks.clear();
            this.chunks.addAll(chunks);
            return this;
        }

        public @NotNull PolarWorld build() {
            return new PolarWorld(this);
        }
    }

    public enum CompressionType {
        NONE,
        ZSTD;

        private static final CompressionType[] VALUES = values();

        public static @Nullable CompressionType fromId(int id) {
            if (id < 0 || id >= VALUES.length) return null;
            return VALUES[id];
        }
    }
}
