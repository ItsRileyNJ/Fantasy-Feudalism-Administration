package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.Vanish;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class VanishPlayerMixin {
  @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
  private void ffa$track(ServerPlayer viewer, CallbackInfoReturnable<Boolean> ci) {
    if (!Vanish.canSee(viewer, (ServerPlayer) (Object) this)) ci.setReturnValue(false);
  }

  @Inject(method = "setGameMode", at = @At("HEAD"), cancellable = true)
  private void ffa$mode(GameType type, CallbackInfoReturnable<Boolean> ci) {
    if (Vanish.hidden(((ServerPlayer) (Object) this).getUUID()) && type != GameType.SPECTATOR)
      ci.setReturnValue(false);
  }
}
