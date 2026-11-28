package dev.fantasyfeudalism.administration;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Only natural burnout of placed lanterns; manual switches and other lights keep their own rules.
 */
public final class LanternProtection {
  private record WeatherTick(ServerLevel level, BlockPos pos) {}

  private static final ThreadLocal<WeatherTick> WEATHER_TICK = new ThreadLocal<>();

  private LanternProtection() {}

  public static void scheduledWeatherTick(ServerLevel level, BlockPos pos, Runnable callback) {
    WeatherTick previous = WEATHER_TICK.get();
    WEATHER_TICK.set(new WeatherTick(level, pos.immutable()));
    try {
      callback.run();
    } finally {
      if (previous == null) WEATHER_TICK.remove();
      else WEATHER_TICK.set(previous);
    }
  }

  public static boolean preventWeatherChange(Level level, BlockPos pos, BlockState after) {
    WeatherTick tick = WEATHER_TICK.get();
    if (tick == null
        || tick.level != level
        || !tick.pos.equals(pos)
        || !after.hasProperty(BlockStateProperties.LIT)
        || after.getValue(BlockStateProperties.LIT)) return false;
    BlockState before = level.getBlockState(pos);
    return before.is(after.getBlock())
        && before.hasProperty(BlockStateProperties.LIT)
        && before.getValue(BlockStateProperties.LIT)
        && paused(tick.level, pos, before);
  }

  public static boolean paused(ServerLevel level, BlockPos pos, BlockState state) {
    return (state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN))
        && state.hasProperty(BlockStateProperties.LIT)
        && Protection.block(level, pos, Model.Flag.LANTERN_BURNOUT);
  }
}
