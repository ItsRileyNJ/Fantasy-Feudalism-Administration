package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.PortalCreation;
import java.util.Optional;
import net.minecraft.BlockUtil;
import net.minecraft.core.*;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PortalForcer.class)
public abstract class PortalForcerMixin {
  @Inject(method = "createPortal", at = @At("HEAD"), cancellable = true)
  private void ffa$create(
      BlockPos at,
      Direction.Axis axis,
      CallbackInfoReturnable<Optional<BlockUtil.FoundRectangle>> ci) {
    if (!PortalCreation.permitted()) ci.setReturnValue(Optional.empty());
  }
}
