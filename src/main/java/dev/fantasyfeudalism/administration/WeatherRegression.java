package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;

import java.lang.reflect.Proxy;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.storage.ServerLevelData;

/** Exercises vanilla precipitation and deterministic natural lightning, not just flag lookups. */
final class WeatherRegression {
  private static Region region(GameTestHelper h, String id, Kind kind, int min, int max) {
    var r = new Region();
    r.id = r.name = id;
    r.kind = kind;
    r.dimension = h.getLevel().dimension().location().toString();
    r.minX = h.absolutePos(new BlockPos(min, 1, min)).getX();
    r.minZ = h.absolutePos(new BlockPos(min, 1, min)).getZ();
    r.maxX = h.absolutePos(new BlockPos(max, 1, max)).getX();
    r.maxZ = h.absolutePos(new BlockPos(max, 1, max)).getZ();
    r.settlementOverride = true;
    return r;
  }

  private static void weather(Administration app, String id, Boolean value) throws Exception {
    var copy = app.regions.copy();
    var flags = copy.stream().filter(r -> r.id.equals(id)).findFirst().orElseThrow().flags;
    if (value == null) flags.remove(Flag.WEATHER);
    else flags.put(Flag.WEATHER, value);
    app.regions.edit(copy, app.server, app.settings, app.compat);
  }

  private static void biome(Collection<LevelChunkSection> sections, Holder<Biome> biome) {
    for (var section : sections)
      section.fillBiomesFromNoise((x, y, z, climate) -> biome, null, 0, 0, 0);
  }

  static void verify(GameTestHelper h, Administration app) throws Exception {
    var level = h.getLevel();
    var regions = app.regions.copy();
    var world = (ServerLevelData) level.getLevelData();
    int clear = world.getClearWeatherTime(),
        rainTime = world.getRainTime(),
        thunderTime = world.getThunderTime();
    boolean raining = world.isRaining(), thundering = world.isThundering();
    float rain = level.getRainLevel(1), thunder = level.getThunderLevel(1);
    int snowHeight = level.getGameRules().getInt(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT);
    boolean mobs = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
    var blocks = new HashMap<BlockPos, BlockState>();
    var biomes = new LinkedHashMap<LevelChunkSection, PalettedContainerRO<Holder<Biome>>>();
    var lightningBefore = new HashSet<UUID>();
    level
        .getAllEntities()
        .forEach(
            e -> {
              if (e instanceof LightningBolt) lightningBefore.add(e.getUUID());
            });
    var randomField = Level.class.getField("random");
    var originalRandom = (RandomSource) randomField.get(level);
    BlockPos rod = h.absolutePos(new BlockPos(3, 33, 3));
    try {
      var parent = region(h, "WeatherSpawn", Kind.SPAWN, 0, 10);
      var child = region(h, "SnowGarden", Kind.CHILD, 5, 8);
      child.parent = parent.id;
      app.regions.edit(List.of(parent, child), app.server, app.settings, app.compat);
      var center = new ChunkPos(rod);
      for (int x = center.x - 1; x <= center.x + 1; x++)
        for (int z = center.z - 1; z <= center.z + 1; z++)
          for (var section : level.getChunk(x, z).getSections())
            biomes.put(section, section.getBiomes());
      biome(
          biomes.keySet(),
          level
              .registryAccess()
              .registryOrThrow(Registries.BIOME)
              .getHolderOrThrow(Biomes.SNOWY_PLAINS));
      for (int x = 0; x < 16; x++)
        for (int z = 0; z < 16; z++)
          for (int y = 0; y <= 7; y++) {
            var pos = h.absolutePos(new BlockPos(x, y + 32, z));
            blocks.put(pos, level.getBlockState(pos));
            level.setBlockAndUpdate(
                pos, y == 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
          }
      level.setWeatherParameters(0, 600, true, true);
      level.setRainLevel(1);
      level.setThunderLevel(1);
      level.getGameRules().getRule(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT).set(8, app.server);
      level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, app.server);
      var protectedSnow = h.absolutePos(new BlockPos(2, 33, 2));
      var childSnow = h.absolutePos(new BlockPos(6, 33, 6));
      var outsideSnow = h.absolutePos(new BlockPos(13, 33, 13));
      level.tickPrecipitation(protectedSnow);
      level.tickPrecipitation(childSnow);
      level.tickPrecipitation(outsideSnow);
      h.assertTrue(
          level.getBlockState(protectedSnow).isAir() && level.getBlockState(childSnow).isAir(),
          "Default weather flag blocks new snow and inherits into children");
      h.assertTrue(
          level.getBlockState(outsideSnow).is(Blocks.SNOW),
          "Actual cold-biome vanilla precipitation still creates wilderness snow: "
              + level.getHeightmapPos(
                  net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, outsideSnow)
              + ", biome="
              + level.getBiome(outsideSnow).unwrapKey()
              + ", light="
              + level.getBrightness(LightLayer.BLOCK, outsideSnow)
              + ", below="
              + level.getBlockState(outsideSnow.below())
              + ", rain="
              + level.isRaining()
              + ", allowed="
              + app.protection.allowed(level, outsideSnow, Flag.WEATHER));
      level.setBlockAndUpdate(
          protectedSnow, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3));
      level.tickPrecipitation(protectedSnow);
      h.assertTrue(
          level.getBlockState(protectedSnow).getValue(SnowLayerBlock.LAYERS) == 3,
          "Existing protected snow cannot accumulate extra layers and is not erased");
      var protectedWater = h.absolutePos(new BlockPos(2, 33, 4));
      var outsideWater = h.absolutePos(new BlockPos(13, 33, 11));
      level.setBlockAndUpdate(protectedWater, Blocks.WATER.defaultBlockState());
      level.setBlockAndUpdate(outsideWater, Blocks.WATER.defaultBlockState());
      level.tickPrecipitation(protectedWater.above());
      level.tickPrecipitation(outsideWater.above());
      h.assertTrue(
          level.getBlockState(protectedWater).is(Blocks.WATER)
              && level.getBlockState(outsideWater).is(Blocks.ICE),
          "The same weather rule prevents protected water freezing without affecting wilderness"
              + " ice");
      weather(app, "SnowGarden", true);
      level.tickPrecipitation(childSnow);
      h.assertTrue(
          level.getBlockState(childSnow).is(Blocks.SNOW),
          "Explicit child weather override creates snow inside protected spawn");
      weather(app, "WeatherSpawn", true);
      level.tickPrecipitation(protectedSnow);
      h.assertTrue(
          level.getBlockState(protectedSnow).getValue(SnowLayerBlock.LAYERS) == 4,
          "Enabling weather restores native accumulation");
      weather(app, "WeatherSpawn", false);
      weather(app, "SnowGarden", null);
      level.tickPrecipitation(childSnow);
      h.assertTrue(
          level.getBlockState(childSnow).getValue(SnowLayerBlock.LAYERS) == 1,
          "Removing child override restores parent's protection");

      // Force only the native chance gates. The real biome, heightmap, rod targeting,
      // rain check, block callbacks and entity insertion still execute normally.
      var forced =
          (RandomSource)
              Proxy.newProxyInstance(
                  RandomSource.class.getClassLoader(),
                  new Class<?>[] {RandomSource.class},
                  (proxy, method, args) -> {
                    if (method.getName().equals("nextInt")
                        && args != null
                        && args.length == 1
                        && (int) args[0] == 100000) return 0;
                    if (method.getName().equals("nextFloat")) return 0f;
                    return method.invoke(originalRandom, args);
                  });
      randomField.setAccessible(true);
      randomField.set(level, forced);
      var protectedCauldron = h.absolutePos(new BlockPos(2, 33, 8));
      var outsideCauldron = h.absolutePos(new BlockPos(13, 33, 8));
      level.setBlockAndUpdate(protectedCauldron, Blocks.CAULDRON.defaultBlockState());
      level.setBlockAndUpdate(outsideCauldron, Blocks.CAULDRON.defaultBlockState());
      level.tickPrecipitation(protectedCauldron.above());
      level.tickPrecipitation(outsideCauldron.above());
      h.assertTrue(
          level.getBlockState(protectedCauldron).is(Blocks.CAULDRON)
              && level.getBlockState(outsideCauldron).is(Blocks.POWDER_SNOW_CAULDRON),
          "Natural precipitation block callbacks obey the protected weather rule");
      biome(
          biomes.keySet(),
          level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS));
      level.setBlockAndUpdate(rod, Blocks.LIGHTNING_ROD.defaultBlockState());
      if (level.getPoiManager().getType(rod).isEmpty())
        level
            .getPoiManager()
            .add(
                rod,
                level
                    .registryAccess()
                    .registryOrThrow(Registries.POINT_OF_INTEREST_TYPE)
                    .getHolderOrThrow(PoiTypes.LIGHTNING_ROD));
      h.assertTrue(level.isRainingAt(rod.above()), "Lightning fixture has genuine exposed rain");
      var chunk = level.getChunkAt(rod);
      level.tickChunk(chunk, 0);
      h.assertTrue(
          count(level, lightningBefore) == 0,
          "Natural thunder cannot add a lightning bolt in protected spawn");
      weather(app, "WeatherSpawn", true);
      level.tickChunk(chunk, 0);
      h.assertTrue(
          count(level, lightningBefore) > 0,
          "Enabling weather lets the actual natural lightning branch spawn a bolt");
      weather(app, "WeatherSpawn", false);
      var summoned = EntityType.LIGHTNING_BOLT.create(level);
      summoned.setPos(rod.getX(), rod.getY() + 1, rod.getZ());
      h.assertTrue(
          level.addFreshEntity(summoned),
          "Explicit administrator-style lightning creation is independent of natural weather");
      h.assertTrue(
          level.isRaining() && level.getRainLevel(1) == 1,
          "Regional weather protection never changes the world's visible weather");
    } finally {
      randomField.setAccessible(true);
      randomField.set(level, originalRandom);
      for (var e : level.getAllEntities())
        if (e instanceof LightningBolt && !lightningBefore.contains(e.getUUID())) e.discard();
      blocks.forEach(level::setBlockAndUpdate);
      for (var e : biomes.entrySet()) {
        var old = e.getValue();
        e.getKey().fillBiomesFromNoise((x, y, z, climate) -> old.get(x, y, z), null, 0, 0, 0);
      }
      world.setClearWeatherTime(clear);
      world.setRainTime(rainTime);
      world.setThunderTime(thunderTime);
      world.setRaining(raining);
      world.setThundering(thundering);
      level.setRainLevel(rain);
      level.setThunderLevel(thunder);
      level
          .getGameRules()
          .getRule(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT)
          .set(snowHeight, app.server);
      level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(mobs, app.server);
      app.regions.edit(regions, app.server, app.settings, app.compat);
    }
  }

  private static long count(ServerLevel level, Set<UUID> previous) {
    long count = 0;
    for (var e : level.getAllEntities())
      if (e instanceof LightningBolt && !previous.contains(e.getUUID())) count++;
    return count;
  }
}
