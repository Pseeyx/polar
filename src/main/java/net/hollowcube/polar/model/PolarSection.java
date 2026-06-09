package net.hollowcube.polar.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Representation of the latest version of the section format.
 * <p>
 * Marked as internal because of the use of mutable arrays. These arrays must _not_ be mutated.
 * This class should be considered immutable.
 */
@ApiStatus.Internal
@Getter
@Accessors(fluent = true)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PolarSection {
    public static final int BLOCK_PALETTE_SIZE = 4096;
    public static final int BIOME_PALETTE_SIZE = 64;
    @Getter(AccessLevel.NONE)
    boolean empty;
    String @NotNull [] blockPalette;
    @Getter(AccessLevel.NONE)
    int @Nullable [] blockData;
    String @NotNull [] biomePalette;
    @Getter(AccessLevel.NONE)
    int @Nullable [] biomeData;
    LightContent blockLightContent;
    byte @Nullable [] blockLight;
    LightContent skyLightContent;
    byte @Nullable [] skyLight;

    public PolarSection() {
        this(builder());
    }

    public PolarSection(
            String @NotNull [] blockPalette, int @Nullable [] blockData,
            String @NotNull [] biomePalette, int @Nullable [] biomeData,
            @NotNull LightContent blockLightContent, byte @Nullable [] blockLight,
            @NotNull LightContent skyLightContent, byte @Nullable [] skyLight
    ) {
        this(filled()
                .blockPalette(blockPalette)
                .blockData(blockData)
                .biomePalette(biomePalette)
                .biomeData(biomeData)
                .blockLightContent(blockLightContent)
                .blockLight(blockLight)
                .skyLightContent(skyLightContent)
                .skyLight(skyLight));
    }

    private PolarSection(@NotNull Builder builder) {
        this.empty = builder.empty;
        this.blockPalette = builder.blockPalette;
        this.blockData = builder.blockData;
        this.biomePalette = builder.biomePalette;
        this.biomeData = builder.biomeData;
        this.blockLightContent = builder.blockLightContent;
        this.blockLight = builder.blockLight;
        this.skyLightContent = builder.skyLightContent;
        this.skyLight = builder.skyLight;
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static @NotNull PolarSection empty() {
        return builder().build();
    }

    public static @NotNull Builder filled() {
        return builder().empty(false);
    }

    public boolean isEmpty() {
        return empty;
    }

    public int[] blockData() {
        assert blockData != null : "must check length of blockPalette() before using blockData()";
        return blockData;
    }

    public int[] biomeData() {
        assert biomeData != null : "must check length of biomePalette() before using biomeData()";
        return biomeData;
    }

    public static final class Builder {
        boolean empty = true;
        String @NotNull [] blockPalette = new String[]{"minecraft:air"};
        int @Nullable [] blockData = null;
        String @NotNull [] biomePalette = new String[]{"minecraft:plains"};
        int @Nullable [] biomeData = null;
        LightContent blockLightContent = LightContent.MISSING;
        byte @Nullable [] blockLight = null;
        LightContent skyLightContent = LightContent.MISSING;
        byte @Nullable [] skyLight = null;

        public @NotNull Builder empty(boolean empty) {
            this.empty = empty;
            return this;
        }

        public @NotNull Builder blockPalette(String @NotNull [] blockPalette) {
            this.blockPalette = blockPalette;
            return this;
        }

        public @NotNull Builder blockData(int @Nullable [] blockData) {
            this.blockData = blockData;
            return this;
        }

        public @NotNull Builder biomePalette(String @NotNull [] biomePalette) {
            this.biomePalette = biomePalette;
            return this;
        }

        public @NotNull Builder biomeData(int @Nullable [] biomeData) {
            this.biomeData = biomeData;
            return this;
        }

        public @NotNull Builder blockLightContent(@NotNull LightContent blockLightContent) {
            this.blockLightContent = blockLightContent;
            return this;
        }

        public @NotNull Builder blockLight(byte @Nullable [] blockLight) {
            this.blockLight = blockLight;
            return this;
        }

        public @NotNull Builder skyLightContent(@NotNull LightContent skyLightContent) {
            this.skyLightContent = skyLightContent;
            return this;
        }

        public @NotNull Builder skyLight(byte @Nullable [] skyLight) {
            this.skyLight = skyLight;
            return this;
        }

        public @NotNull PolarSection build() {
            return new PolarSection(this);
        }
    }

    public enum LightContent {
        MISSING, EMPTY, FULL, PRESENT;

        public static final LightContent[] VALUES = values();
    }
}
