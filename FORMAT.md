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

| Name           | Type   | Notes                                                                   |
|----------------|--------|-------------------------------------------------------------------------|
| Magic Number   | int    | `Polr`                                                                  |
| Version        | short  |                                                                         |
| Compression    | byte   | 0 = None, 1 = Zstd                                                      |
| Length of data | varint | Uncompressed length of data (or just length of data if `Compression=0`) |
| World          | world  |                                                                         |

### World

| Name             | Type         | Notes                                    |
|------------------|--------------|------------------------------------------|
| Min Section      | byte         | For example, -4 in a vanilla world       |
| Max Section      | byte         | For example, 19 in a vanilla world       |
| User data        | array[byte]  | Arbitrary user data segment              |
| Number of Chunks | varint       | Number of entries in the following array |
| Chunks           | array[chunk] | Chunk data                               |

Corresponds to `PolarWorld.minSection()`, `PolarWorld.maxSection()`, `PolarWorld.userData()`, and `PolarWorld.chunks()`.

### Chunk

Entities or some other extra data field needs to be added to chunks in the future.

| Name                     | Type                | Notes                                                                                |
|--------------------------|---------------------|--------------------------------------------------------------------------------------|
| Chunk X                  | varint              |                                                                                      |
| Chunk Z                  | varint              |                                                                                      |
| Sections                 | array[section]      | `maxSection-minSection+1` entries                                                    |
| Number of Block Entities | varint              | Number of entries in the following array                                             |
| Block Entities           | array[block entity] |                                                                                      |
| Heightmap Mask           | int                 | A mask indicating which heightmaps are present. See `PolarChunk` for flag constants. |
| Heightmaps               | array[bytes]        | One heightmap for each bit present in Heightmap Mask                                 |
| Length of user data      | varint              | Number of entries in the following array                                             |
| User data                | array[byte]         | Arbitrary user data segment                                                          |

Corresponds to `PolarChunk` record components. Build with `PolarChunk.builder()` or `PolarChunk.at(x, z, sectionCount)`.

### Sections

| Name                      | Type          | Notes                                                             |
|---------------------------|---------------|-------------------------------------------------------------------|
| Is Empty                  | bool          | If set, nothing follows                                           |
| Block Palette Size        | varint        |                                                                   |
| Block Palette             | array[string] | Entries are in the form `minecraft:block[key1=value1,key2=value2] |
| Block Palette Data Length | varint        | Only present if `Block Palette Size > 1`                          |
| Block Palette Data        | array[long]   | See the anvil format for more information about this type         |
| Biome Palette Size        | varint        |                                                                   |
| Biome Palette             | array[string] |                                                                   |
| Biome Palette Data Length | varint        | Only present if `Biome Palette Size > 1`                          |
| Biome Palette Data        | array[long]   | See the anvil format for more information about this type         |
| Block Light Data Content  | byte          | 0 = no lighting, 1 = all zero, 2 = all max, 3 = present after     |
| Block Light               | bytes         | A 2048 byte long nibble array, only present if above = 3          |
| Sky Light Data Content    | byte          | 0 = no lighting, 1 = all zero, 2 = all max, 3 = present after     |
| Sky Light                 | bytes         | A 2048 byte long nibble array, only present if above = 3          |

Build non-empty sections with `PolarSection.filled()` and empty sections with `PolarSection.empty()`.

### Block Entity

| Name            | Type   | Notes                                |
|-----------------|--------|--------------------------------------|
| Chunk Pos       | int    |                                      |
| Has ID          | bool   | If unset, Block Entity ID is omitted |
| Block Entity ID | string |                                      |
| Has NBT Data    | bool   | If unset, NBT Data is omitted        |
| NBT Data        | nbt    |                                      |

Build with `PolarChunk.BlockEntity.builder()`.
