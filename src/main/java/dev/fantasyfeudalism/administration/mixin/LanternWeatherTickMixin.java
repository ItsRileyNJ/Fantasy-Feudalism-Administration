package dev.fantasyfeudalism.administration.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.fantasyfeudalism.administration.LanternProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;

/** Keep the real weather callback and its rescheduling, while guarding automatic extinguishing. */
@Mixin(ServerLevel.class)
public abstract class LanternWeatherTickMixin {
  @WrapMethod(method = "tickBlock")
  private void ffa$weather(BlockPos pos, Block block, Operation<Void> original) {
    if (block != Blocks.LANTERN && block != Blocks.SOUL_LANTERN) {
      original.call(pos, block);
      return;
    }
    LanternProtection.scheduledWeatherTick(
        (ServerLevel) (Object) this, pos, () -> original.call(pos, block));
  }
}
