package net.hollowcube.polar.loader;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.DynamicChunk;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.light.Light;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@UtilityClass
final class UnsafeOps {
  private static final MethodHandle CACHE_CHUNK_HANDLE;
  private static final MethodHandle CHUNK_ON_LOAD_HANDLE;
  private static final MethodHandle NEEDS_HEIGHTMAP_REFRESH_SETTER;
  private static final MethodHandle DYNAMIC_CHUNK_ENTRIES_GETTER;
  private static final MethodHandle DYNAMIC_CHUNK_TICKABLE_MAP_GETTER;

  private static final LightFieldHandles BLOCK_LIGHT_HANDLES;
  private static final LightFieldHandles SKY_LIGHT_HANDLES;

  static {
    try {
      var lookup = MethodHandles.privateLookupIn(InstanceContainer.class, MethodHandles.lookup());
      var method = InstanceContainer.class.getDeclaredMethod("cacheChunk", Chunk.class);
      CACHE_CHUNK_HANDLE = lookup.unreflect(method);
    } catch (IllegalAccessException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }

    try {
      var lookup = MethodHandles.privateLookupIn(DynamicChunk.class, MethodHandles.lookup());
      NEEDS_HEIGHTMAP_REFRESH_SETTER =
          lookup.unreflectSetter(
              DynamicChunk.class.getDeclaredField("needsCompleteHeightmapRefresh"));
      DYNAMIC_CHUNK_ENTRIES_GETTER =
          lookup.unreflectGetter(DynamicChunk.class.getDeclaredField("entries"));
      DYNAMIC_CHUNK_TICKABLE_MAP_GETTER =
          lookup.unreflectGetter(DynamicChunk.class.getDeclaredField("tickableMap"));
      CHUNK_ON_LOAD_HANDLE = lookup.unreflect(Chunk.class.getDeclaredMethod("onLoad"));
    } catch (IllegalAccessException | NoSuchFieldException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }

    BLOCK_LIGHT_HANDLES =
        LightFieldHandles.resolve("net.minestom.server.instance.light.BlockLight");
    SKY_LIGHT_HANDLES = LightFieldHandles.resolve("net.minestom.server.instance.light.SkyLight");
  }

  static void unsafeCacheChunk(@NotNull InstanceContainer instance, @NotNull Chunk chunk) {
    invokeVoid(CACHE_CHUNK_HANDLE, instance, chunk);
  }

  static void unsafeChunkOnLoad(@NotNull Chunk chunk) {
    invokeVoid(CHUNK_ON_LOAD_HANDLE, chunk);
  }

  static void unsafeSetNeedsCompleteHeightmapRefresh(@NotNull Chunk chunk, boolean value) {
    if (chunk instanceof DynamicChunk dynamicChunk) {
      invokeVoid(NEEDS_HEIGHTMAP_REFRESH_SETTER, dynamicChunk, value);
    }
  }

  static @Nullable Int2ObjectOpenHashMap<Block> unsafeGetEntries(@NotNull Chunk chunk) {
    if (chunk instanceof DynamicChunk dynamicChunk) {
      return invoke(DYNAMIC_CHUNK_ENTRIES_GETTER, dynamicChunk);
    }
    return null;
  }

  static @Nullable Int2ObjectOpenHashMap<Block> unsafeGetTickableMap(@NotNull Chunk chunk) {
    if (chunk instanceof DynamicChunk dynamicChunk) {
      return invoke(DYNAMIC_CHUNK_TICKABLE_MAP_GETTER, dynamicChunk);
    }
    return null;
  }

  static void unsafeUpdateBlockLightArray(@NotNull Light light, byte[] content) {
    BLOCK_LIGHT_HANDLES.update(light, content);
  }

  static void unsafeUpdateSkyLightArray(@NotNull Light light, byte[] content) {
    SKY_LIGHT_HANDLES.update(light, content);
  }

  @SneakyThrows
  private static void invokeVoid(MethodHandle handle, Object... args) {
    handle.invokeWithArguments(args);
  }

  @SneakyThrows
  @SuppressWarnings("unchecked")
  private static <T> T invoke(MethodHandle handle, Object... args) {
    return (T) handle.invokeWithArguments(args);
  }

  private record LightFieldHandles(
      MethodHandle contentSetter,
      MethodHandle contentPropagationSetter,
      MethodHandle isValidBordersSetter,
      MethodHandle needsSendGetter) {
    @SneakyThrows
    static LightFieldHandles resolve(@NotNull String className) {
      var lightClass = Class.forName(className);
      var lookup = MethodHandles.privateLookupIn(lightClass, MethodHandles.lookup());
      return new LightFieldHandles(
          lookup.unreflectSetter(lightClass.getDeclaredField("content")),
          lookup.unreflectSetter(lightClass.getDeclaredField("contentPropagation")),
          lookup.unreflectSetter(lightClass.getDeclaredField("isValidBorders")),
          lookup.unreflectGetter(lightClass.getDeclaredField("needsSend")));
    }

    @SneakyThrows
    void update(@NotNull Light light, byte[] content) {
      contentSetter.invoke(light, content);
      contentPropagationSetter.invoke(light, content);
      isValidBordersSetter.invoke(light, true);
      var needsSend = (AtomicBoolean) needsSendGetter.invoke(light);
      needsSend.set(true);
    }
  }
}
