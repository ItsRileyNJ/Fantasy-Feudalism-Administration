package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DispenserBlock.class)
public abstract class DispenserMixin {
  @Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
  private void ffa$dispense(ServerLevel l, BlockState s, BlockPos p, CallbackInfo ci) {
    if (Protection.block(l, p, Model.Flag.DISPENSERS)
        || Protection.block(
            l, p.relative(s.getValue(DispenserBlock.FACING)), Model.Flag.DISPENSERS)) ci.cancel();
  }
}
