package net.hollowcube.polar;

import lombok.experimental.UtilityClass;
import net.hollowcube.polar.model.PolarChunk;
import net.hollowcube.polar.model.PolarSection;
import net.hollowcube.polar.model.PolarWorld;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

@UtilityClass
public class WorldHeightUtil {
    public static @NotNull PolarWorld updateWorldHeight(@NotNull PolarWorld world, byte minSection, byte maxSection) {
        assert minSection <= maxSection : "minSection cannot be less than maxSection";

        ArrayList<PolarChunk> chunks = new ArrayList<>(world.chunks().size());

        for (PolarChunk chunk : world.chunks()) {
            chunks.add(updateChunkHeight(chunk, minSection, maxSection));
        }

        var builder = PolarWorld.builder()
                .version(world.version())
                .dataVersion(world.dataVersion())
                .compression(world.compression())
                .minSection(minSection)
                .maxSection(maxSection)
                .userData(world.userData());
        for (var chunk : chunks) builder.chunk(chunk);
        return builder.build();
    }

    public static @NotNull PolarChunk updateChunkHeight(@NotNull PolarChunk chunk, byte minSection, byte maxSection) {
        PolarSection[] sections = new PolarSection[maxSection - minSection + 1];

        for (int i = 0; i <= maxSection - minSection; i++) {
            sections[i] = i < chunk.sections().length ? chunk.sections()[i] : PolarSection.empty();
        }

        return PolarChunk.builder()
                .x(chunk.x())
                .z(chunk.z())
                .sections(sections)
                .blockEntities(chunk.blockEntities())
                .heightmaps(chunk.heightmaps())
                .userData(chunk.userData())
                .build();
    }
}
