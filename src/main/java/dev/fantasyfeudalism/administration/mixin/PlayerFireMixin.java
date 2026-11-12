package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.RegionActions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class PlayerFireMixin {
  @Inject(method = "setRemainingFireTicks", at = @At("HEAD"), cancellable = true)
  private void ffa$ignite(int ticks, CallbackInfo callback) {
    if (ticks > 0
        && (Object) this instanceof ServerPlayer player
        && !RegionActions.canCatchFire(player)) callback.cancel();
  }

  @Inject(method = "baseTick", at = @At("HEAD"))
  private void ffa$extinguish(CallbackInfo callback) {
    if ((Object) this instanceof ServerPlayer player
        && player.getRemainingFireTicks() > 0
        && !RegionActions.canCatchFire(player)) player.clearFire();
  }
}
