package net.hollowcube.polar.loader;

import lombok.extern.slf4j.Slf4j;
import net.minestom.server.instance.Chunk;
import net.minestom.server.network.NetworkBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnstableApiUsage")
@Slf4j
public class UpdateTimeWorldAccess implements PolarWorldAccess {
    public long saveTime = 0;
    public long loadTime = 0;

    @Override
    public void loadChunkData(@NotNull Chunk chunk, @Nullable NetworkBuffer userData) {
        if (userData == null) return; // No saved data, probably first load

        long lastSaveTime = userData.read(NetworkBuffer.LONG);
        log.info("loading chunk {}, {} which was saved at {}.", chunk.getChunkX(), chunk.getChunkZ(), lastSaveTime);
        loadTime = lastSaveTime;
    }

    @Override
    public void saveChunkData(@NotNull Chunk chunk, @NotNull NetworkBuffer userData) {
        saveTime = System.currentTimeMillis();
        userData.write(NetworkBuffer.LONG, saveTime);
    }
}
