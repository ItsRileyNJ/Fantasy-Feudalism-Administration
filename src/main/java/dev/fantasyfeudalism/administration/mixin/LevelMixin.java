package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin {
  @Inject(
      method =
          "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
      at = @At("HEAD"),
      cancellable = true)
  private void ffa$fire(
      BlockPos p, BlockState s, int f, int depth, CallbackInfoReturnable<Boolean> ci) {
    if (s.getBlock() instanceof BaseFireBlock
        && Protection.block((Level) (Object) this, p, Model.Flag.FIRE)) ci.setReturnValue(false);
  }
}
