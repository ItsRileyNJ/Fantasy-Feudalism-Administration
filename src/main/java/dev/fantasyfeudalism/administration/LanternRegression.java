package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;
import static dev.fantasyfeudalism.administration.RegressionTests.*;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Exact Items 0.1.7 fuel/weather handlers plus FFA region inheritance and native interactions. */
final class LanternRegression {
  private static final String ITEMS = "com.fantasyfeudalism.items.lighting.";

  private static Object invoke(Class<?> owner, String name, Class<?>[] types, Object... args)
      throws Exception {
    try {
      return owner.getMethod(name, types).invoke(null, args);
    } catch (InvocationTargetException error) {
      if (error.getCause() instanceof Exception cause) throw cause;
      throw error;
    }
  }

  private static long fuel(Class<?> owner, ServerLevel level, BlockPos pos) throws Exception {
    return (long)
        invoke(owner, "remaining", new Class<?>[] {ServerLevel.class, BlockPos.class}, level, pos);
  }

  private static void fuel(Class<?> owner, ServerLevel level, BlockPos pos, long amount)
      throws Exception {
    invoke(
        owner,
        "setRemaining",
        new Class<?>[] {ServerLevel.class, BlockPos.class, long.class},
        level,
        pos,
        amount);
  }

  private static boolean lit(ServerLevel level, BlockPos pos) {
    return level.getBlockState(pos).getValue(BlockStateProperties.LIT);
  }

  private static void tick(Class<?> owner, ServerLevel level) throws Exception {
    var data = (ServerLevelData) level.getLevelData();
    data.setGameTime(Math.floorDiv(data.getGameTime(), 20) * 20 + 20);
    invoke(
        owner,
        "tick",
        new Class<?>[] {LevelTickEvent.Post.class},
        new LevelTickEvent.Post(() -> true, level));
  }

  private static void weather(Class<?> owner, ServerLevel level, BlockPos pos) throws Exception {
    invoke(
        owner,
        "check",
        new Class<?>[] {ServerLevel.class, BlockPos.class, double.class},
        level,
        pos,
        -1d);
  }

  private static Region region(
      String id, Kind kind, String dimension, BlockPos low, BlockPos high) {
    var r = new Region();
    r.id = r.name = id;
    r.kind = kind;
    r.dimension = dimension;
    r.minX = low.getX();
    r.minZ = low.getZ();
    r.maxX = high.getX();
    r.maxZ = high.getZ();
    r.settlementOverride = true;
    return r;
  }

  static void verify(GameTestHelper h, Administration app) throws Exception {
    boolean additions = ModList.get().isLoaded("fantasy_feudalism_additions");
    String modId = additions ? "fantasy_feudalism_additions" : "fantasy_feudalism_items";
    boolean installed = ModList.get().isLoaded(modId);
    if (!installed
        || !ModList.get()
            .getModContainerById(modId)
            .orElseThrow()
            .getModInfo()
            .getVersion()
            .toString()
            .equals(additions ? "0.1.25" : "0.1.7")) {
      h.assertTrue(
          !app.settings.defaults.get(Flag.LANTERN_BURNOUT),
          "Lantern protection defaults safely without Items");
      return;
    }
    var level = h.getLevel();
    var world = (ServerLevelData) level.getLevelData();
    var regions = app.regions.copy();
    var state = app.storage.copy();
    var player = player(h, "lanternStaff");
    long time = world.getGameTime();
    int clear = world.getClearWeatherTime(),
        rainTime = world.getRainTime(),
        thunderTime = world.getThunderTime();
    boolean raining = world.isRaining(), thundering = world.isThundering();
    float rain = level.getRainLevel(1), thunder = level.getThunderLevel(1);
    var blocks = new LinkedHashMap<BlockPos, BlockState>();
    String packageName = additions ? "com.fantasyfeudalism.additions.lighting." : ITEMS;
    Class<?> fuel = Class.forName(packageName + "LightFuel"),
        weather = Class.forName(packageName + "WeatherLighting");
    Object manager = invoke(fuel, "get", new Class<?>[] {ServerLevel.class}, level);
    CompoundTag oldFuel =
        (CompoundTag)
            fuel.getMethod(
                    "save", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class)
                .invoke(manager, new CompoundTag(), app.server.registryAccess());
    Method load =
        fuel.getDeclaredMethod(
            "load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
    load.setAccessible(true);
    try {
      var low = h.absolutePos(new BlockPos(1, 1, 1));
      var high = h.absolutePos(new BlockPos(8, 1, 8));
      var parent =
          region("LanternSpawn", Kind.SPAWN, level.dimension().location().toString(), low, high);
      var child =
          region(
              "LanternChild",
              Kind.CHILD,
              parent.dimension,
              h.absolutePos(new BlockPos(4, 1, 4)),
              h.absolutePos(new BlockPos(6, 1, 6)));
      child.parent = parent.id;
      app.regions.edit(List.of(parent, child), app.server, app.settings, app.compat);
      var inside = h.absolutePos(new BlockPos(2, 1, 2));
      var inherited = h.absolutePos(new BlockPos(5, 1, 5));
      var outside = h.absolutePos(new BlockPos(12, 1, 2));
      var torch = h.absolutePos(new BlockPos(3, 1, 2));
      var soul = h.absolutePos(new BlockPos(2, 1, 4));
      for (var pos : List.of(inside, inherited, outside, torch, soul)) {
        for (int dy = -1; dy <= 6; dy++) {
          var at = pos.above(dy);
          blocks.putIfAbsent(at.immutable(), level.getBlockState(at));
          level.setBlockAndUpdate(
              at, dy == -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        var block =
            pos.equals(torch)
                ? Blocks.TORCH
                : pos.equals(soul) ? Blocks.SOUL_LANTERN : Blocks.LANTERN;
        level.setBlockAndUpdate(
            pos, block.defaultBlockState().setValue(BlockStateProperties.LIT, true));
        fuel(fuel, level, pos, 20);
      }
      tick(fuel, level);
      h.assertTrue(
          lit(level, inside) && fuel(fuel, level, inside) == 20,
          "Spawn lantern remains lit at its exact fuel deadline without consuming or refilling"
              + " fuel");
      h.assertTrue(
          lit(level, inherited) && fuel(fuel, level, inherited) == 20,
          "Child region inherits paused lantern fuel by default");
      h.assertTrue(
          !lit(level, outside) && fuel(fuel, level, outside) == 0,
          "Items' original fuel burnout still runs outside protection");
      h.assertTrue(
          !lit(level, torch) && fuel(fuel, level, torch) == 0,
          "Lantern protection does not freeze torches or unrelated lights");
      h.assertTrue(lit(level, soul), "Soul lantern behavior stays intact");
      for (int n = 0; n < 3; n++) tick(fuel, level);
      h.assertTrue(
          lit(level, inside) && fuel(fuel, level, inside) == 20,
          "Repeated fuel ticks never accumulate a catch-up charge inside spawn");

      var edited = app.regions.copy();
      edited.stream()
          .filter(r -> r.id.equals(child.id))
          .findFirst()
          .orElseThrow()
          .flags
          .put(Flag.LANTERN_BURNOUT, true);
      app.regions.edit(edited, app.server, app.settings, app.compat);
      tick(fuel, level);
      h.assertTrue(
          !lit(level, inherited) && fuel(fuel, level, inherited) == 0 && lit(level, inside),
          "An explicit child override resumes ordinary burnout without changing its parent");

      fuel(fuel, level, outside, 60);
      level.setBlockAndUpdate(
          outside, level.getBlockState(outside).setValue(BlockStateProperties.LIT, true));
      world.setClearWeatherTime(0);
      world.setRainTime(1000);
      world.setThunderTime(1000);
      world.setRaining(true);
      world.setThundering(true);
      level.setRainLevel(1);
      level.setThunderLevel(1);
      h.assertTrue(
          (double)
                  invoke(
                      weather,
                      "chance",
                      new Class<?>[] {BlockState.class, boolean.class, boolean.class},
                      level.getBlockState(outside),
                      true,
                      true)
              > 0,
          "Items config enables lantern storm extinguishing");
      // Force the handler's extinguishing branch independently of exposure/heightmap differences.
      // This verifies cancellation of an actual setBlock attempt, not merely absence of rain.
      weather(weather, level, inside);
      weather(weather, level, outside);
      h.assertTrue(
          lit(level, inside) && !lit(level, outside),
          "Forced Items weather extinguishing is cancelled only inside the protected region");
      h.assertTrue(
          fuel(fuel, level, inside) == 20 && fuel(fuel, level, outside) == 60,
          "Storm protection preserves fuel and leaves native outside weather accounting alone");

      app.server
          .getPlayerList()
          .getOps()
          .add(
              new net.minecraft.server.players.ServerOpListEntry(
                  player.getGameProfile(), 4, false));
      player.setGameMode(GameType.CREATIVE);
      player.setPos(Vec3.atCenterOf(inside).add(0, 0, 1));
      player.setShiftKeyDown(true);
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      player.gameMode.useItemOn(
          player,
          level,
          ItemStack.EMPTY,
          InteractionHand.MAIN_HAND,
          new BlockHitResult(Vec3.atCenterOf(inside), Direction.UP, inside, false));
      h.assertTrue(
          !lit(level, inside),
          "Administrators can still deliberately toggle a protected lantern off");
      player.gameMode.useItemOn(
          player,
          level,
          ItemStack.EMPTY,
          InteractionHand.MAIN_HAND,
          new BlockHitResult(Vec3.atCenterOf(inside), Direction.UP, inside, false));
      h.assertTrue(
          lit(level, inside) && fuel(fuel, level, inside) == 20,
          "Administrators can switch the same lantern back on without receiving free fuel");
      player.setShiftKeyDown(false);

      app.regions.edit(List.of(), app.server, app.settings, app.compat);
      tick(fuel, level);
      h.assertTrue(
          !lit(level, inside) && fuel(fuel, level, inside) == 0,
          "Removing protection resumes fuel consumption immediately with the saved remaining fuel");
    } finally {
      player.setShiftKeyDown(false);
      app.server.getPlayerList().deop(player.getGameProfile());
      app.regions.edit(regions, app.server, app.settings, app.compat);
      for (var entry : blocks.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
      level
          .getDataStorage()
          .set(
              "fantasy_feudalism_lights",
              (SavedData) load.invoke(null, oldFuel, app.server.registryAccess()));
      world.setGameTime(time);
      world.setClearWeatherTime(clear);
      world.setRainTime(rainTime);
      world.setThunderTime(thunderTime);
      world.setRaining(raining);
      world.setThundering(thundering);
      level.setRainLevel(rain);
      level.setThunderLevel(thunder);
      app.server.getPlayerList().remove(player);
      app.storage.commit(state);
    }
  }
}
