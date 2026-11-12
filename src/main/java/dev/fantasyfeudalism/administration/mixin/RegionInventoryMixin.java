package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.RegionActions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class RegionInventoryMixin {
  @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
  private void ffa$throw(
      int slot, int button, ClickType type, Player actor, CallbackInfo callback) {
    if (actor instanceof ServerPlayer player
        && !RegionActions.canDrop(player)
        && (type == ClickType.THROW || slot == -999 && type == ClickType.PICKUP)) {
      player.containerMenu.sendAllDataToRemote();
      callback.cancel();
    }
  }
}
