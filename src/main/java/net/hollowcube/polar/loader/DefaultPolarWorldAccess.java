package net.hollowcube.polar.loader;

import lombok.extern.slf4j.Slf4j;
import net.minestom.server.MinecraftServer;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.world.biome.Biome;
import org.jetbrains.annotations.NotNull;

@Slf4j
final class DefaultPolarWorldAccess implements PolarWorldAccess {
    @Override
    public @NotNull String getBiomeName(int id) {
        var biomeRegistry = MinecraftServer.getBiomeRegistry();
        var biome = biomeRegistry.getKey(id);
        if (biome == null) {
            log.error("Failed to find biome: {}", id);
            return Biome.PLAINS.name();
        }
        return biome.name();
    }

    @Override
    public int getBiomeId(@NotNull String name) {
        var biomeRegistry = MinecraftServer.getBiomeRegistry();
        return biomeRegistry.getId(RegistryKey.unsafeOf(name));
    }
}
