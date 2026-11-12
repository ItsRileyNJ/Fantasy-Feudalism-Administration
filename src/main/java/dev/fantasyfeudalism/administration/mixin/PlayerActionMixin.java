package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.RegionActions;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class PlayerActionMixin {
  @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
  private void ffa$drop(boolean stack, CallbackInfoReturnable<Boolean> callback) {
    var player = (ServerPlayer) (Object) this;
    if (!RegionActions.canDrop(player)) {
      player.inventoryMenu.sendAllDataToRemote();
      callback.setReturnValue(false);
    }
  }
}
