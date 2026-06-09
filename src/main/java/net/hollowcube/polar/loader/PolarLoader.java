package net.hollowcube.polar.loader;

import static net.minestom.server.instance.Chunk.CHUNK_SECTION_SIZE;

import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import net.hollowcube.polar.conversion.PolarDataConverter;
import net.hollowcube.polar.io.PolarReader;
import net.hollowcube.polar.io.PolarWriter;
import net.hollowcube.polar.model.PolarChunk;
import net.hollowcube.polar.model.PolarSection;
import net.hollowcube.polar.model.PolarSection.LightContent;
import net.hollowcube.polar.model.PolarWorld;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.minecraft.ArgumentBlockState;
import net.minestom.server.command.builder.exception.ArgumentSyntaxException;
import net.minestom.server.exception.ExceptionManager;
import net.minestom.server.instance.*;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockManager;
import net.minestom.server.instance.light.LightCompute;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.world.DimensionType;
import net.minestom.server.world.biome.Biome;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnstableApiUsage")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class PolarLoader implements ChunkLoader {
  static final BlockManager BLOCK_MANAGER = MinecraftServer.getBlockManager();
  static final ExceptionManager EXCEPTION_HANDLER = MinecraftServer.getExceptionManager();
  final Map<String, Integer> biomeReadCache = new ConcurrentHashMap<>();
  final Map<Integer, String> biomeWriteCache = new ConcurrentHashMap<>();
  @Nullable PolarByteStorage byteStorage;
  final ReentrantReadWriteLock worldDataLock = new ReentrantReadWriteLock();
  final PolarWorld worldData;
  PolarWorldAccess worldAccess = PolarWorldAccess.DEFAULT;

  /**
   * Sets the loader to save and load in parallel. <br>
   * <br>
   * The Polar loader on its own supports parallel load out of the box, but a user implementation of
   * {@link PolarWorldAccess} may not support parallel operations, so care must be taken when
   * enabling this option.
   */
  @Setter
  @Accessors(chain = true)
  boolean parallel = false;

  @Setter
  @Accessors(chain = true)
  boolean loadLighting = true;

  int plainsBiomeId = 0; // Always 0 in minestom

  public PolarLoader(@NotNull Path path) throws IOException {
    var storage = new PolarByteStorage.FileStorage(path);
    this(storage, Files.exists(path) ? PolarReader.read(storage.load()) : PolarWorld.empty());
  }

  public PolarLoader(@NotNull PolarByteStorage byteStorage) throws IOException {
    this(byteStorage, PolarReader.read(byteStorage.load()));
  }

  public PolarLoader(@NotNull Path savePath, @NotNull PolarWorld worldData) {
    this(savePath != null ? new PolarByteStorage.FileStorage(savePath) : null, worldData);
  }

  public PolarLoader(@Nullable PolarByteStorage byteStorage, @NotNull PolarWorld worldData) {
    this(byteStorage, worldData, PolarWorldAccess.DEFAULT, false, true);
  }

  public PolarLoader(@NotNull InputStream inputStream) throws IOException {
    byte[] data;
    try (inputStream) {
      data = inputStream.readAllBytes();
    }
    this((PolarByteStorage) null, PolarReader.read(data));
  }

  public PolarLoader(@NotNull PolarWorld world) {
    this((PolarByteStorage) null, world);
  }

  PolarLoader(
      @Nullable PolarByteStorage byteStorage,
      @NotNull PolarWorld worldData,
      @NotNull PolarWorldAccess worldAccess,
      boolean parallel,
      boolean loadLighting) {
    this.byteStorage = byteStorage;
    this.worldData = worldData;
    this.worldAccess = worldAccess;
    this.parallel = parallel;
    this.loadLighting = loadLighting;
    initPlainsBiomeId();
  }

  public static @NotNull Builder builder() {
    return new Builder();
  }

  public static @NotNull Builder forWorld(@NotNull PolarWorld worldData) {
    return builder().worldData(worldData);
  }

  private void initPlainsBiomeId() {
    this.plainsBiomeId = this.worldAccess.getBiomeId(Biome.PLAINS.name());
    if (this.plainsBiomeId == -1) {
      throw new IllegalStateException("Plains biome not found");
    }
  }

  /**
   * Loads a polar world into an instance in a streaming manner.
   *
   * <p>This method significantly reduces the memory overhead of loading a world and should
   * generally be preferrable. It is still in an experimental state and could have issues. It also
   * accesses some internal Minestom APIs and as such only works with {@link InstanceContainer} and
   * is more prone to issues across Minestom versions.
   *
   * @param instance The instance to load the world data into, all chunks in the polar world will be
   *     replaced.
   * @param is The input stream to read the polar world data from. The stream will be closed after
   *     reading.
   * @return A future that completes when the world has been fully loaded.
   */
  @ApiStatus.Experimental
  public static @NotNull CompletableFuture<Void> streamLoad(
      @NotNull InstanceContainer instance,
      @NotNull ReadableByteChannel is,
      long fileSize,
      @Nullable PolarDataConverter dataConverter,
      @Nullable PolarWorldAccess worldAccess,
      boolean loadLighting) {
    final var loader =
        new StreamingPolarLoader(
            instance,
            Objects.requireNonNullElse(dataConverter, PolarDataConverter.NOOP),
            worldAccess,
            loadLighting);
    final var future = new CompletableFuture<Void>();
    Thread.startVirtualThread(
        () -> {
          try {
            loader.loadAllSequential(is, fileSize);
            future.complete(null);
          } catch (Throwable e) {
            future.completeExceptionally(e);
          }
        });
    return future;
  }

  @ApiStatus.Internal
  public static byte[] getLightArray(@NotNull LightContent content, byte @Nullable [] data) {
    return switch (content) {
      case MISSING -> null;
      case EMPTY -> LightCompute.EMPTY_CONTENT;
      case FULL -> LightCompute.CONTENT_FULLY_LIT;
      case PRESENT -> data;
    };
  }

  @ApiStatus.Internal
  public static @NotNull Block createBlockEntity(
      @NotNull Chunk chunk, @NotNull PolarChunk.BlockEntity blockEntity) {
    // Fetch the block type, we can ignore Handler/NBT since we are about to replace it
    var block =
        chunk.getBlock(
            blockEntity.x(), blockEntity.y(), blockEntity.z(), Block.Getter.Condition.TYPE);
    if (blockEntity.id() != null)
      block = block.withHandler(BLOCK_MANAGER.getHandlerOrDummy(blockEntity.id()));
    if (blockEntity.data() != null) block = block.withNbt(blockEntity.data());
    return block;
  }

  @ApiStatus.Internal
  public static void loadBlockEntity(
      @NotNull Chunk chunk, @NotNull PolarChunk.BlockEntity blockEntity) {
    var block = createBlockEntity(chunk, blockEntity);
    chunk.setBlock(blockEntity.x(), blockEntity.y(), blockEntity.z(), block);
  }

  public @NotNull PolarWorld world() {
    return worldData;
  }

  // Loading

  @Contract("_ -> this")
  public @NotNull PolarLoader setWorldAccess(@NotNull PolarWorldAccess worldAccess) {
    this.worldAccess = worldAccess;
    initPlainsBiomeId();
    return this;
  }

  @Override
  public boolean supportsParallelLoading() {
    return parallel;
  }

  @Override
  public void loadInstance(@NotNull Instance instance) {
    var userData = worldData.userData();
    if (userData.length > 0) {
      worldAccess.loadWorldData(instance, NetworkBuffer.wrap(userData, 0, userData.length));
    }
  }

  @Override
  public @Nullable Chunk loadChunk(@NotNull Instance instance, int chunkX, int chunkZ) {
    // Only need to lock for this tiny part, chunks are immutable.
    worldDataLock.readLock().lock();
    var chunkData = worldData.chunkAt(chunkX, chunkZ);
    worldDataLock.readLock().unlock();
    if (chunkData == null) return null;

    // We are making the assumption here that the chunk height is the same as this world.
    // Polar includes world height metadata in the prelude and assumes all chunks match
    // those values. We check that the dimension settings match in #loadInstance, so
    // here it can be ignored/assumed.

    // Load the chunk
    var chunk = instance.getChunkSupplier().createChunk(instance, chunkX, chunkZ);
    chunk.lockWriteLock();
    try {
      int sectionY = chunk.getMinSection();
      for (var sectionData : chunkData.sections()) {
        if (sectionData.isEmpty()) {
          sectionY++;
          continue;
        }

        var section = chunk.getSection(sectionY);
        loadSection(sectionData, section);
        sectionY++;
      }

      for (var blockEntity : chunkData.blockEntities()) {
        loadBlockEntity(chunk, blockEntity);
      }

      worldAccess.loadHeightmaps(chunk, chunkData.heightmaps());

      var userData = chunkData.userData();
      if (userData.length > 0) {
        worldAccess.loadChunkData(chunk, NetworkBuffer.wrap(userData, 0, userData.length));
      }
    } finally {
      chunk.unlockWriteLock();
    }

    return chunk;
  }

  private void loadSection(@NotNull PolarSection sectionData, @NotNull Section section) {
    // assumed that section is _not_ empty

    // Blocks
    var rawBlockPalette = sectionData.blockPalette();
    var blockPalette = new Block[rawBlockPalette.length];
    for (int i = 0; i < rawBlockPalette.length; i++) {
      try {
        //noinspection deprecation
        blockPalette[i] = ArgumentBlockState.staticParse(rawBlockPalette[i]);
      } catch (ArgumentSyntaxException e) {
        log.error("Failed to parse block state: {} ({})", rawBlockPalette[i], e.getMessage());
        blockPalette[i] = Block.AIR;
      }
    }
    if (blockPalette.length == 1) {
      section.blockPalette().fill(blockPalette[0].stateId());
    } else {
      final var paletteData = sectionData.blockData();
      for (int y = 0; y < CHUNK_SECTION_SIZE; y++) {
        for (int z = 0; z < CHUNK_SECTION_SIZE; z++) {
          for (int x = 0; x < CHUNK_SECTION_SIZE; x++) {
            int index = y * CHUNK_SECTION_SIZE * CHUNK_SECTION_SIZE + z * CHUNK_SECTION_SIZE + x;
            section.blockPalette().set(x, y, z, blockPalette[paletteData[index]].stateId());
          }
        }
      }
    }

    // Biomes
    var rawBiomePalette = sectionData.biomePalette();
    var biomePalette = new int[rawBiomePalette.length];
    for (int i = 0; i < rawBiomePalette.length; i++) {
      biomePalette[i] =
          biomeReadCache.computeIfAbsent(
              rawBiomePalette[i],
              name -> {
                var biomeId = this.worldAccess.getBiomeId(name);
                if (biomeId == -1) {
                  log.error("Failed to find biome: {}", name);
                  biomeId = plainsBiomeId;
                }
                return biomeId;
              });
    }
    if (biomePalette.length == 1) {
      section.biomePalette().fill(biomePalette[0]);
    } else {
      final var paletteData = sectionData.biomeData();
      for (int y = 0; y < CHUNK_SECTION_SIZE / 4; y++) {
        for (int z = 0; z < CHUNK_SECTION_SIZE / 4; z++) {
          for (int x = 0; x < CHUNK_SECTION_SIZE / 4; x++) {
            int index = x + (z) * 4 + (y) * 16;

            var paletteIndex = paletteData[index];
            if (paletteIndex >= biomePalette.length) {
              log.error(
                  "Invalid biome palette index. This is probably a corrupted world, "
                      + "but it has been loaded with plains instead. No data has been written.");
              section.biomePalette().set(x, y, z, plainsBiomeId);
            } else {
              section.biomePalette().set(x, y, z, biomePalette[paletteIndex]);
            }
          }
        }
      }
    }

    // Light
    if (loadLighting && sectionData.blockLightContent() != LightContent.MISSING)
      UnsafeOps.unsafeUpdateBlockLightArray(
          section.blockLight(),
          getLightArray(sectionData.blockLightContent(), sectionData.blockLight()));
    if (loadLighting && sectionData.skyLightContent() != LightContent.MISSING)
      UnsafeOps.unsafeUpdateSkyLightArray(
          section.skyLight(), getLightArray(sectionData.skyLightContent(), sectionData.skyLight()));
  }

  // Unloading/saving

  @Override
  public boolean supportsParallelSaving() {
    return parallel;
  }

  @Override
  public void saveInstance(@NotNull Instance instance) {
    worldData.userData(NetworkBuffer.makeArray(b -> worldAccess.saveWorldData(instance, b)));
    DimensionType dimensionType =
        MinecraftServer.getDimensionTypeRegistry().get(instance.getDimensionType());

    byte minSection = (byte) (dimensionType.minY() / CHUNK_SECTION_SIZE);

    byte maxSection = (byte) (dimensionType.maxY() / CHUNK_SECTION_SIZE - 1);

    if (minSection == this.worldData.minSection() && this.worldData.maxSection() == maxSection) {
      saveChunks(instance.getChunks());
      return;
    }

    worldDataLock.writeLock().lock();

    worldData.setSectionCount(minSection, maxSection);

    worldDataLock.writeLock().unlock();

    saveChunks(instance.getChunks());
  }

  @Override
  public void unloadChunk(Chunk chunk) {
    updateChunkData(new Short2ObjectOpenHashMap<>(), chunk);
  }

  @Override
  public void saveChunks(@NotNull Collection<Chunk> chunks) {
    var blockCache = new Short2ObjectOpenHashMap<String>();

    // Update state of each chunk locally
    chunks.forEach(c -> updateChunkData(blockCache, c));

    if (byteStorage != null) {
      try {
        byteStorage.save(PolarWriter.write(worldData));
      } catch (IOException e) {
        EXCEPTION_HANDLER.handleException(new RuntimeException("Failed to save world", e));
      }
    }
  }

  private void updateChunkData(@NotNull Short2ObjectMap<String> blockCache, @NotNull Chunk chunk) {
    var dimension = chunk.getInstance().getCachedDimensionType();

    var blockEntities = new ArrayList<PolarChunk.BlockEntity>();
    var sections = new PolarSection[dimension.height() / CHUNK_SECTION_SIZE];
    assert sections.length == chunk.getSections().size() : "World height mismatch";

    var heightmaps = new int[PolarChunk.MAX_HEIGHTMAPS][];

    var userData = new byte[0];

    chunk.lockReadLock();
    try {
      for (int i = 0; i < sections.length; i++) {
        int sectionY = i + chunk.getMinSection();
        var section = chunk.getSection(sectionY);
        if (isUnmodifiedSection(section)) {
          sections[i] = new PolarSection();
          continue;
        }

        var blockPalette = new ArrayList<String>();
        var localBlockData = new int[PolarSection.BLOCK_PALETTE_SIZE];

        section
            .blockPalette()
            .getAll(
                (x, sectionLocalY, z, blockStateId) -> {
                  final int blockIndex = x + sectionLocalY * 16 * 16 + z * 16;

                  // Section palette
                  var namespace =
                      blockCache.computeIfAbsent(
                          (short) blockStateId,
                          unused -> blockToString(Block.fromStateId(blockStateId)));
                  int paletteId = blockPalette.indexOf(namespace);
                  if (paletteId == -1) {
                    paletteId = blockPalette.size();
                    blockPalette.add(namespace);
                  }
                  localBlockData[blockIndex] = paletteId;
                });

        int[] blockData = localBlockData;

        // Block entities
        for (int sectionLocalY = 0; sectionLocalY < CHUNK_SECTION_SIZE; sectionLocalY++) {
          for (int z = 0; z < Chunk.CHUNK_SIZE_Z; z++) {
            for (int x = 0; x < Chunk.CHUNK_SIZE_X; x++) {
              int y = sectionLocalY + sectionY * CHUNK_SECTION_SIZE;
              var block = chunk.getBlock(x, y, z, Block.Getter.Condition.CACHED);
              if (block == null) continue;

              var handlerId = block.handler() == null ? null : block.handler().getKey().asString();
              if (handlerId != null || block.hasNbt()) {
                blockEntities.add(new PolarChunk.BlockEntity(x, y, z, handlerId, block.nbt()));
              }
            }
          }
        }

        var biomePalette = new ArrayList<String>();
        var biomeData = new int[PolarSection.BIOME_PALETTE_SIZE];

        section
            .biomePalette()
            .getAll(
                (x, y, z, id) -> {
                  var biomeId = biomeWriteCache.computeIfAbsent(id, worldAccess::getBiomeName);

                  var paletteId = biomePalette.indexOf(biomeId);
                  if (paletteId == -1) {
                    paletteId = biomePalette.size();
                    biomePalette.add(biomeId);
                  }

                  biomeData[x + z * 4 + y * 4 * 4] = paletteId;
                });

        byte[] blockLight = section.blockLight().array();
        byte[] skyLight = section.skyLight().array();

        sections[i] =
            new PolarSection(
                blockPalette.toArray(new String[0]), blockData,
                biomePalette.toArray(new String[0]), biomeData,
                getLightContent(blockLight), blockLight,
                getLightContent(skyLight), skyLight);
      }

      worldAccess.saveHeightmaps(chunk, heightmaps);

      userData = NetworkBuffer.makeArray(b -> worldAccess.saveChunkData(chunk, b));
    } finally {
      chunk.unlockReadLock();
    }

    worldDataLock.writeLock().lock();
    worldData.updateChunkAt(
        chunk.getChunkX(),
        chunk.getChunkZ(),
        new PolarChunk(
            chunk.getChunkX(), chunk.getChunkZ(), sections, blockEntities, heightmaps, userData));
    worldDataLock.writeLock().unlock();
  }

  private boolean isUnmodifiedSection(@NotNull Section section) {
    if (section.blockPalette().count() != 0) return false;
    if (section.biomePalette().count() != 0) return false;
    return getLightContent(section.blockLight().array()) == LightContent.MISSING
        && getLightContent(section.skyLight().array()) == LightContent.MISSING;
  }

  private @NotNull LightContent getLightContent(byte @Nullable [] data) {
    if (data == null) return LightContent.MISSING;
    if (data.length == 0 || Arrays.equals(data, LightCompute.EMPTY_CONTENT))
      return LightContent.EMPTY;
    if (Arrays.equals(data, LightCompute.CONTENT_FULLY_LIT)) return LightContent.FULL;
    return LightContent.PRESENT;
  }

  @Override
  public void saveChunk(@NotNull Chunk chunk) {
    saveChunks(List.of(chunk));
  }

  private @NotNull String blockToString(@NotNull Block block) {
    var builder = new StringBuilder(block.name());
    if (block.properties().isEmpty()) return builder.toString();

    builder.append('[');
    for (var entry : block.properties().entrySet()) {
      builder.append(entry.getKey()).append('=').append(entry.getValue()).append(',');
    }
    builder.deleteCharAt(builder.length() - 1);
    builder.append(']');

    return builder.toString();
  }

  public static final class Builder {
    @Nullable PolarByteStorage byteStorage;
    @NotNull PolarWorld worldData;
    @NotNull PolarWorldAccess worldAccess = PolarWorldAccess.DEFAULT;
    boolean parallel = false;
    boolean loadLighting = true;

    public @NotNull Builder byteStorage(@Nullable PolarByteStorage byteStorage) {
      this.byteStorage = byteStorage;
      return this;
    }

    public @NotNull Builder savePath(@Nullable Path savePath) {
      this.byteStorage = savePath != null ? new PolarByteStorage.FileStorage(savePath) : null;
      return this;
    }

    public @NotNull Builder worldData(@NotNull PolarWorld worldData) {
      this.worldData = worldData;
      return this;
    }

    public @NotNull Builder worldAccess(@NotNull PolarWorldAccess worldAccess) {
      this.worldAccess = worldAccess;
      return this;
    }

    public @NotNull Builder parallel(boolean parallel) {
      this.parallel = parallel;
      return this;
    }

    public @NotNull Builder loadLighting(boolean loadLighting) {
      this.loadLighting = loadLighting;
      return this;
    }

    public @NotNull PolarLoader build() {
      return new PolarLoader(byteStorage, worldData, worldAccess, parallel, loadLighting);
    }
  }
}
