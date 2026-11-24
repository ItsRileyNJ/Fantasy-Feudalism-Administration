package dev.fantasyfeudalism.administration;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Only natural burnout of placed lanterns; manual switches and other lights keep their own rules.
 */
public final class LanternProtection {
  private LanternProtection() {}

  public static boolean paused(ServerLevel level, BlockPos pos, BlockState state) {
    return (state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN))
        && state.hasProperty(BlockStateProperties.LIT)
        && Protection.block(level, pos, Model.Flag.LANTERN_BURNOUT);
  }
}
