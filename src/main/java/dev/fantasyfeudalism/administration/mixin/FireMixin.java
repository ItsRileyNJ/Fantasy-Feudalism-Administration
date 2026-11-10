package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public abstract class FireMixin {
  @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
  private void ffa$tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r, CallbackInfo ci) {
    if (Protection.block(l, p, Model.Flag.FIRE)) ci.cancel();
  }

  @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
  private void ffa$burn(
      Level l, BlockPos p, int chance, RandomSource r, int age, Direction d, CallbackInfo ci) {
    if (Protection.block(l, p, Model.Flag.FIRE)) ci.cancel();
  }
}
