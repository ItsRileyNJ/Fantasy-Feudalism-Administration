package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class FlightMixin {
  @Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
  private void ffa$glide(CallbackInfoReturnable<Boolean> ci) {
    var a = Administration.INSTANCE;
    if ((Object) this instanceof ServerPlayer p
        && a.server != null
        && !a.compat.admin(p)
        && Protection.block(p.level(), p.blockPosition(), Model.Flag.FLIGHT))
      ci.setReturnValue(false);
  }
}
