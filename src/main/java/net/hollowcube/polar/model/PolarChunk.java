package net.hollowcube.polar.model;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.instance.Chunk;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * A Java type representing the latest version of the chunk format.
 */
public record PolarChunk(
        int x,
        int z,
        PolarSection[] sections,
        List<BlockEntity> blockEntities,
        int[][] heightmaps,
        byte[] userData
) {

    public static final int HEIGHTMAP_NONE = 0b0;
    public static final int HEIGHTMAP_MOTION_BLOCKING = 0b1;
    public static final int HEIGHTMAP_MOTION_BLOCKING_NO_LEAVES = 0b10;
    public static final int HEIGHTMAP_OCEAN_FLOOR = 0b100;
    public static final int HEIGHTMAP_OCEAN_FLOOR_WG = 0b1000;
    public static final int HEIGHTMAP_WORLD_SURFACE = 0b10000;
    public static final int HEIGHTMAP_WORLD_SURFACE_WG = 0b100000;
    @ApiStatus.Internal
    public static final int HEIGHTMAP_SIZE = Chunk.CHUNK_SIZE_X * Chunk.CHUNK_SIZE_Z;
    @ApiStatus.Internal
    public static final int MAX_HEIGHTMAPS = 32;
    static final int[] HEIGHTMAPS = new int[]{
            HEIGHTMAP_NONE,
            HEIGHTMAP_MOTION_BLOCKING,
            HEIGHTMAP_MOTION_BLOCKING_NO_LEAVES,
            HEIGHTMAP_OCEAN_FLOOR,
            HEIGHTMAP_OCEAN_FLOOR_WG,
            HEIGHTMAP_WORLD_SURFACE,
            HEIGHTMAP_WORLD_SURFACE_WG,
    };

    public PolarChunk {
        if (blockEntities == null) blockEntities = List.of();
        if (heightmaps == null) heightmaps = new int[MAX_HEIGHTMAPS][];
        if (userData == null) userData = new byte[0];
    }

    public static @NotNull Builder at(int x, int z) {
        return builder().x(x).z(z);
    }

    public static @NotNull PolarChunk of(int x, int z, @NotNull PolarSection[] sections) {
        return builder().x(x).z(z).sections(sections).build();
    }

    public static @NotNull PolarSection[] emptySections(int sectionCount) {
        var sections = new PolarSection[sectionCount];
        Arrays.fill(sections, PolarSection.empty());
        return sections;
    }

    public static @NotNull PolarChunk at(int x, int z, int sectionCount) {
        return of(x, z, emptySections(sectionCount));
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public int @Nullable [] heightmap(int type) {
        return heightmaps[type];
    }

    public record BlockEntity(
            int x,
            int y,
            int z,
            @Nullable String id,
            @Nullable CompoundBinaryTag data
    ) {
        public static @NotNull BlockEntityBuilder builder() {
            return new BlockEntityBuilder();
        }

        public static final class BlockEntityBuilder {
            int x;
            int y;
            int z;
            @Nullable String id;
            @Nullable CompoundBinaryTag data;

            public @NotNull BlockEntityBuilder x(int x) {
                this.x = x;
                return this;
            }

            public @NotNull BlockEntityBuilder y(int y) {
                this.y = y;
                return this;
            }

            public @NotNull BlockEntityBuilder z(int z) {
                this.z = z;
                return this;
            }

            public @NotNull BlockEntityBuilder id(@Nullable String id) {
                this.id = id;
                return this;
            }

            public @NotNull BlockEntityBuilder data(@Nullable CompoundBinaryTag data) {
                this.data = data;
                return this;
            }

            public @NotNull BlockEntity build() {
                return new BlockEntity(x, y, z, id, data);
            }
        }
    }

    public static final class Builder {
        int x;
        int z;
        PolarSection[] sections;
        List<BlockEntity> blockEntities = List.of();
        int[][] heightmaps = new int[MAX_HEIGHTMAPS][];
        byte[] userData = new byte[0];

        public @NotNull Builder x(int x) {
            this.x = x;
            return this;
        }

        public @NotNull Builder z(int z) {
            this.z = z;
            return this;
        }

        public @NotNull Builder sections(@NotNull PolarSection[] sections) {
            this.sections = sections;
            return this;
        }

        public @NotNull Builder blockEntities(@NotNull List<BlockEntity> blockEntities) {
            this.blockEntities = blockEntities;
            return this;
        }

        public @NotNull Builder heightmaps(int[][] heightmaps) {
            this.heightmaps = heightmaps;
            return this;
        }

        public @NotNull Builder userData(byte[] userData) {
            this.userData = userData;
            return this;
        }

        public @NotNull PolarChunk build() {
            return new PolarChunk(x, z, sections, blockEntities, heightmaps, userData);
        }
    }
}
