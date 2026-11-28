package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Real scheduled weather callbacks, also run with version-specific adapters deliberately absent.
 */
final class RainLanternRegression {
  private static boolean lit(ServerLevel level, BlockPos pos) {
    return level.getBlockState(pos).getValue(BlockStateProperties.LIT);
  }

  private static void tick(ServerLevel level, BlockPos pos) throws Exception {
    level.getBlockTicks().clearArea(BoundingBox.fromCorners(pos, pos));
    // Some lantern layouts count their own block in MOTION_BLOCKING. Force exposed
    // weather for this column to test the actual extinguishing branch, not merely
    // a fixture whose own heightmap makes isRainingAt false. No production policy changes.
    level
        .getChunkAt(pos)
        .getOrCreateHeightmapUnprimed(
            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING)
        .update(pos.getX() & 15, pos.getY(), pos.getZ() & 15, Blocks.AIR.defaultBlockState());
    var method = ServerLevel.class.getDeclaredMethod("tickBlock", BlockPos.class, Block.class);
    method.setAccessible(true);
    method.invoke(level, pos, level.getBlockState(pos).getBlock());
  }

  static void verify(GameTestHelper h, Administration app) throws Exception {
    if (!Blocks.LANTERN.defaultBlockState().hasProperty(BlockStateProperties.LIT)) {
      h.assertFalse(
          LanternProtection.paused(
              h.getLevel(), h.absolutePos(BlockPos.ZERO), Blocks.LANTERN.defaultBlockState()),
          "Vanilla lanterns without a modded lit state need no adapter");
      return;
    }
    boolean additions = ModList.get().isLoaded("fantasy_feudalism_additions");
    if (!additions && !ModList.get().isLoaded("fantasy_feudalism_items")) return;
    String prefix =
        additions
            ? "com.fantasyfeudalism.additions.lighting."
            : "com.fantasyfeudalism.items.lighting.";
    var chance =
        (ModConfigSpec.DoubleValue)
            Class.forName(prefix + "WeatherLighting").getField("LANTERN_STORM").get(null);
    double oldChance = chance.get();
    var regions = app.regions.copy();
    var level = h.getLevel();
    var world = (ServerLevelData) level.getLevelData();
    int clear = world.getClearWeatherTime(),
        rain = world.getRainTime(),
        thunder = world.getThunderTime();
    boolean raining = world.isRaining(), thundering = world.isThundering();
    float rainLevel = level.getRainLevel(1), thunderLevel = level.getThunderLevel(1);
    var randomField = Level.class.getField("random");
    randomField.setAccessible(true);
    var random = (RandomSource) randomField.get(level);
    var before = new LinkedHashMap<BlockPos, BlockState>();
    BlockPos inside = h.absolutePos(new BlockPos(2, 33, 2)),
        childPos = h.absolutePos(new BlockPos(5, 33, 5)),
        outside = h.absolutePos(new BlockPos(12, 33, 2)),
        torch = h.absolutePos(new BlockPos(3, 33, 2));
    try {
      var parent = new Region();
      parent.id = parent.name = "StormSpawn";
      parent.kind = Kind.SPAWN;
      parent.dimension = level.dimension().location().toString();
      parent.settlementOverride = true;
      parent.minX = h.absolutePos(new BlockPos(1, 0, 1)).getX();
      parent.maxX = parent.minX + 7;
      parent.minZ = h.absolutePos(new BlockPos(1, 0, 1)).getZ();
      parent.maxZ = parent.minZ + 7;
      var child = new Region();
      child.id = child.name = "StormChild";
      child.kind = Kind.CHILD;
      child.dimension = parent.dimension;
      child.parent = parent.id;
      child.settlementOverride = true;
      child.minX = parent.minX + 3;
      child.maxX = parent.minX + 5;
      child.minZ = parent.minZ + 3;
      child.maxZ = parent.minZ + 5;
      app.regions.edit(List.of(parent, child), app.server, app.settings, app.compat);
      for (var pos : List.of(inside, childPos, outside, torch)) {
        for (int dy = -1; dy <= 5; dy++) {
          var at = pos.above(dy);
          before.put(at, level.getBlockState(at));
          level.setBlockAndUpdate(
              at, dy == -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(
            pos,
            (pos.equals(torch) ? Blocks.TORCH : Blocks.LANTERN)
                .defaultBlockState()
                .setValue(BlockStateProperties.LIT, true));
      }
      level.setWeatherParameters(0, 600, true, false);
      level.setRainLevel(1);
      level.setThunderLevel(0);
      chance.set(1d);
      var forced =
          (RandomSource)
              Proxy.newProxyInstance(
                  RandomSource.class.getClassLoader(),
                  new Class<?>[] {RandomSource.class},
                  (proxy, method, args) ->
                      method.getName().equals("nextDouble") ? 0d : method.invoke(random, args));
      randomField.set(level, forced);
      tick(level, inside);
      tick(level, outside);
      h.assertTrue(
          level.isRainingAt(inside) && level.isRainingAt(outside),
          "Weather fixture deliberately exposes the native lantern columns");
      h.assertTrue(
          lit(level, inside) && lit(level, outside),
          "Ordinary rain keeps the mod's native lantern rules");
      world.setThundering(true);
      level.setThunderLevel(1);
      tick(level, inside);
      tick(level, childPos);
      tick(level, outside);
      tick(level, torch);
      h.assertTrue(
          lit(level, inside) && lit(level, childPos) && !lit(level, outside),
          "Actual scheduled storm callbacks preserve spawn/child lanterns and extinguish exposed"
              + " wilderness lanterns");
      h.assertFalse(
          lit(level, torch),
          "Lantern storm protection leaves ordinary torches under their own rules");
      h.assertTrue(
          level.getBlockTicks().hasScheduledTick(inside, Blocks.LANTERN),
          "Protected weather callbacks still schedule their next check");
      var edited = app.regions.copy();
      edited.stream()
          .filter(r -> r.id.equals(child.id))
          .findFirst()
          .orElseThrow()
          .flags
          .put(Flag.LANTERN_BURNOUT, true);
      app.regions.edit(edited, app.server, app.settings, app.compat);
      tick(level, childPos);
      h.assertFalse(
          lit(level, childPos),
          "An explicit child burnout allowance restores normal storm extinguishing");

      try {
        LanternProtection.scheduledWeatherTick(
            level,
            inside,
            () -> {
              throw new IllegalStateException("fixture");
            });
      } catch (IllegalStateException expected) {
        /* Ensure the weather scope closes on failure. */
      }
      h.assertTrue(
          level.setBlockAndUpdate(
                  inside, level.getBlockState(inside).setValue(BlockStateProperties.LIT, false))
              && !lit(level, inside),
          "An exception cannot leave automatic-weather protection active during a manual state"
              + " change");
      level.setBlockAndUpdate(
          inside, level.getBlockState(inside).setValue(BlockStateProperties.LIT, true));
      app.regions.edit(List.of(), app.server, app.settings, app.compat);
      tick(level, inside);
      h.assertFalse(
          lit(level, inside),
          "The next weather callback resumes ordinary extinguishing after protection is removed");
    } finally {
      randomField.set(level, random);
      chance.set(oldChance);
      before.forEach(level::setBlockAndUpdate);
      app.regions.edit(regions, app.server, app.settings, app.compat);
      world.setClearWeatherTime(clear);
      world.setRainTime(rain);
      world.setThunderTime(thunder);
      world.setRaining(raining);
      world.setThundering(thundering);
      level.setRainLevel(rainLevel);
      level.setThunderLevel(thunderLevel);
    }
  }
}
