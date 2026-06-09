# Polar v1.0

The polar format resembles the anvil format in many ways, though it is binary, not NBT.

## Java API

Polar exposes the on-disk layout through builder-based Java types. These mirror the binary structure below.

| Type | Builder entry point | Notes |
|------|---------------------|-------|
| `PolarWorld` | `PolarWorld.builder()` | Defaults to latest version, current data version, zstd compression, overworld section range |
| `PolarChunk` | `PolarChunk.builder()` / `PolarChunk.at(x, z, sectionCount)` | `sectionCount` should match `world.sectionCount()` |
| `PolarSection` | `PolarSection.builder()` / `PolarSection.filled()` | Internal API; defaults to an empty section |
| `PolarChunk.BlockEntity` | `PolarChunk.BlockEntity.builder()` | Nested builder for block entity records |
| `PolarLoader` | `PolarLoader.builder()` / `PolarLoader.forWorld(world)` | Implements Minestom `ChunkLoader` |
| `PolarByteStorage` | `PolarByteStorage.FileStorage` | Pluggable persistence for serialized world bytes |

Example assembling a minimal world in memory:

```java
PolarWorld template = PolarWorld.empty();
PolarWorld world = PolarWorld.builder()
        .chunk(PolarChunk.at(0, 0, template.sectionCount()))
        .build();

byte[] file = PolarWriter.write(world);
PolarWorld roundTrip = PolarReader.read(file);
```

### Header

| Name           | Type   | Notes                                                                                      |
|----------------|--------|--------------------------------------------------------------------------------------------|
| Magic Number   | int    | `Polr`                                                                                     |
| Version        | short  | Current latest is 8                                                                        |
| Data version   | varint | Present when `Version >= 6`. Minecraft data version of the world contents                    |
| Compression    | byte   | 0 = None, 1 = Zstd                                                                         |
| Length of data | varint | Uncompressed length of data (or just length of data if `Compression=0`)                    |
| World          | world  | Compressed or raw depending on `Compression`                                               |

### World

| Name             | Type         | Notes                                    |
|------------------|--------------|------------------------------------------|
| Min Section      | byte         | For example, -4 in a vanilla world       |
| Max Section      | byte         | For example, 19 in a vanilla world       |
| User data        | array[byte]  | Present when `Version > 4`. Varint length prefix followed by bytes |
| Number of Chunks | varint       | Number of entries in the following array |
| Chunks           | array[chunk] | Chunk data                               |

Corresponds to `PolarWorld.minSection()`, `PolarWorld.maxSection()`, `PolarWorld.userData()`, and `PolarWorld.chunks()`.

### Chunk

Entities or some other extra data field needs to be added to chunks in the future.

| Name                     | Type                | Notes                                                                                |
|--------------------------|---------------------|--------------------------------------------------------------------------------------|
| Chunk X                  | varint              | Zig-zag encoded varint when `Version >= 8`, otherwise signed varint                  |
| Chunk Z                  | varint              | Zig-zag encoded varint when `Version >= 8`, otherwise signed varint                  |
| Sections                 | array[section]      | `maxSection-minSection+1` entries                                                    |
| Number of Block Entities | varint              | Number of entries in the following array                                             |
| Block Entities           | array[block entity] |                                                                                      |
| Heightmap Mask           | int                 | A mask indicating which heightmaps are present. See `PolarChunk` for flag constants. |
| Heightmaps               | array[long]         | One packed long array for each bit present in Heightmap Mask                         |
| User data                | array[byte]         | Present when `Version > 2`. Varint length prefix followed by bytes                 |

Corresponds to `PolarChunk` record components. Build with `PolarChunk.builder()` or `PolarChunk.at(x, z, sectionCount)`.

### Sections

| Name                      | Type          | Notes                                                             |
|---------------------------|---------------|-------------------------------------------------------------------|
| Is Empty                  | bool          | If set, nothing follows                                           |
| Block Palette             | array[string] | Varint length prefix. Entries are block state strings             |
| Block Palette Data        | array[long]   | Only present if palette size > 1. Packed palette indices          |
| Biome Palette             | array[string] | Varint length prefix                                              |
| Biome Palette Data        | array[long]   | Only present if palette size > 1                                  |
| Block Light Data Content  | byte          | Present when `Version > 1`. 0 = missing, 1 = empty, 2 = full, 3 = present |
| Block Light               | bytes         | 2048-byte nibble array, only present when content = 3             |
| Sky Light Data Content    | byte          | Present when `Version > 1`                                        |
| Sky Light                 | bytes         | 2048-byte nibble array, only present when content = 3             |

Build non-empty sections with `PolarSection.filled()` and empty sections with `PolarSection.empty()`.

### Block Entity

| Name            | Type   | Notes                                                                 |
|-----------------|--------|-----------------------------------------------------------------------|
| Chunk Pos       | int    | Packed chunk block index                                              |
| Block Entity ID | string | Optional when `Version > 2`                                         |
| NBT Data        | nbt    | Optional when `Version > 2`                                           |

Build with `PolarChunk.BlockEntity.builder()`.
