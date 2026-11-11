package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class InspectionClicksMixin {
  @Redirect(
      method = "handleContainerClick",
      at =
          @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isSpectator()Z"))
  private boolean ffa$edit(ServerPlayer player) {
    var app = Administration.INSTANCE;
    return player.isSpectator()
        && !(player.containerMenu instanceof InspectionMenu
            && app != null
            && app.compat.admin(player));
  }
}
