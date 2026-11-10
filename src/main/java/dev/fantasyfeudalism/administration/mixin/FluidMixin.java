package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.*;
import net.minecraft.core.*;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowingFluid.class)
public abstract class FluidMixin {
  @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
  private void ffa$fluid(
      LevelAccessor l, BlockPos p, BlockState b, Direction d, FluidState f, CallbackInfo ci) {
    if (Protection.block(l, p, Model.Flag.FLUIDS)
        || Protection.block(l, p.relative(d.getOpposite()), Model.Flag.FLUIDS)) ci.cancel();
  }
}
