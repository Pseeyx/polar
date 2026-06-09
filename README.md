# Polar

[![license](https://img.shields.io/github/license/Minestom/MinestomDataGenerator.svg)](LICENSE)

A world format for Minestom designed for simpler and smaller handling of small worlds, particularly for user generated
content where size matters.

Polar generally should not be used for large worlds, since it stores worlds in a single file and does not
allow random access of chunks (the entire world must be loaded to read chunks). As a general rule of thumb,
Polar should only be used for worlds small enough that they are OK being completely kept in memory.

The Polar format is described in [FORMAT.md](FORMAT.md).

## Features

* [Fast to load](#benchmark)
* [Small file size](#benchmark)
* Simple builder-based API
* [Anvil conversion](#anvil-interop)

## Install

Polar is available on [maven central](https://search.maven.org/search?q=g:dev.hollowcube%20AND%20a:polar).

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'dev.hollowcube:polar:<see releases>'
}
```

## Usage

Polar provides a `ChunkLoader` implementation for use with Minestom `Instance`s.

### Loading and saving

```java
// Load from a file on disk
Instance instance = ...;
instance.setChunkLoader(new PolarLoader(Path.of("/path/to/file.polar")));

// Save all loaded chunks back to storage
instance.saveChunksToStorage();
```

### Builder API

Polar uses fluent builders for worlds, chunks, sections, and loaders. Existing constructors still work; builders are the
recommended way to create or configure data.

#### PolarWorld

```java
// Empty overworld-height world (sections -4 through 19)
PolarWorld world = PolarWorld.builder().build();
// or
PolarWorld world = PolarWorld.empty();

// Match a dimension's height
PolarWorld world = PolarWorld.from(DimensionType.OVERWORLD).build();

// Full control
PolarWorld world = PolarWorld.builder()
        .compression(PolarWorld.CompressionType.ZSTD)
        .minSection((byte) -4)
        .maxSection((byte) 19)
        .userData(myBytes)
        .chunk(someChunk)
        .build();
```

#### PolarChunk

```java
int sectionCount = world.sectionCount();

// Empty chunk at 0, 0 with the correct number of sections
PolarChunk chunk = PolarChunk.at(0, 0, sectionCount);

// Or build manually
PolarChunk chunk = PolarChunk.builder()
        .x(0)
        .z(0)
        .sections(PolarChunk.emptySections(sectionCount))
        .build();

// Add block entities with a nested builder
PolarChunk.BlockEntity entity = PolarChunk.BlockEntity.builder()
        .x(1).y(64).z(2)
        .id("minecraft:chest")
        .data(nbt)
        .build();
```

#### PolarSection

`PolarSection` is internal API, but useful when constructing worlds programmatically (for example in tests).

```java
// Empty section (air blocks, plains biome, no lighting)
PolarSection section = PolarSection.empty();

// Filled section
PolarSection section = PolarSection.filled()
        .blockPalette(new String[]{"minecraft:stone"})
        .biomePalette(new String[]{"minecraft:plains"})
        .blockLightContent(PolarSection.LightContent.FULL)
        .skyLightContent(PolarSection.LightContent.FULL)
        .build();
```

#### PolarLoader

```java
// From file (creates an empty world if the file does not exist yet)
PolarLoader loader = new PolarLoader(Path.of("world.polar"));

// Recommended: configure with the builder
PolarLoader loader = PolarLoader.builder()
        .savePath(Path.of("world.polar"))
        .worldData(PolarWorld.empty())
        .worldAccess(new UpdateTimeWorldAccess())
        .parallel(true)
        .loadLighting(true)
        .build();

// Shorthand when you already have a world
PolarLoader loader = PolarLoader.forWorld(world)
        .savePath(path)
        .build();
```

#### Read and write bytes

```java
byte[] bytes = PolarWriter.write(world);
PolarWorld loaded = PolarReader.read(bytes);
```

### Anvil interop

Anvil conversion utilities are also included.

> Note: Anvil conversion is only guaranteed to work on the latest version worlds. Try loading and saving the world in a
> vanilla client or server if it doesn't work!

```java
PolarWorld polarWorld = AnvilPolar.anvilToPolar(Path.of("/path/to/anvil/world/dir"));
byte[] polarWorldBytes = PolarWriter.write(polarWorld);
```

### ChunkSelector

Most Polar functions take a `ChunkSelector` as an optional parameter to select which chunks to include in that
operation.
For example, to convert an anvil world while only selecting a 5 chunk radius around 0,0:

```java
AnvilPolar.anvilToPolar(Path.of("/path/to/anvil/world/dir"), ChunkSelector.radius(5));
```

### User data & callbacks

By default, Polar only stores blocks, biomes, block entities, and light data. However, in many cases it is desirable
to have some additional user specific data stored in the world. To accommodate this use case, Polar chunks each have
a "user data" field, which can contain any arbitrary data. To work with it, implement `PolarWorldAccess` and attach
it to the loader.

```java
@Slf4j
public class UpdateTimeWorldAccess implements PolarWorldAccess {
    public long saveTime = 0;
    public long loadTime = 0;

    @Override
    public void loadChunkData(@NotNull Chunk chunk, @Nullable NetworkBuffer userData) {
        if (userData == null) return;

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
```

```java
PolarLoader loader = PolarLoader.forWorld(world)
        .worldAccess(new UpdateTimeWorldAccess())
        .build();
```

## Comparison to others

### Benchmark

Using a very basic benchmark, we can make some rough guesses about performance between Polar, Anvil, and TNT.
The benchmark loads a single region 10 times, averaging the runtime of each iteration.
More information about the test can be found in [BENCHMARK.md](BENCHMARK.md)

| Scenario        | Iterations | Polar (v1, zstd) | Polar (v1, uncompressed) | TNT (v1)       | Anvil          |
|-----------------|------------|------------------|--------------------------|----------------|----------------|
| 1.19.4 Region   | 10         | 0.61400 s/iter   | 0.56449 s/iter           | 3.56732 s/iter | 9.65274 s/iter |
| EmortalMC Lobby | 10         | 0.06565 s/iter   | 0.04759 s/iter           | 0.05501 s/iter | 0.56378 s/iter |
| EmortalMC Lobby | 500        | 0.06777 s/iter   | 0.06650 s/iter           | 0.07553 s/iter | -              |

| Scenario        | Polar (v1, zstd) | Polar (v1, uncompressed) | TNT (v1) | Anvil |
|-----------------|------------------|--------------------------|----------|-------|
| 1.19.4 Region   | 5.9mb            | 26.1mb                   | 115.9mb  | 9.7mb |
| EmortalMC Lobby | 105kb            | 800kb                    | 1.3mb    | 13mb* |

* This is not a fair comparison. Polar and TNT are only covering the 10x10 relevant chunks, anvil has 4 regions.

1.19.4 Region is a single 32x32 chunk region created in 1.19.4, see `src/test/resources/bench` for the world.

EmortalMC Lobby is 10x10 chunk world, see `mc.emortal.dev` for more info.

## Contributing

Contributions via PRs and issues are always welcome.

## License

This project is licensed under the [MIT License](LICENSE).
