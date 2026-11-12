package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class JailInventoryMixin {
  @Shadow public ServerPlayer player;

  private boolean ffa$jailed() {
    var a = Administration.INSTANCE;
    return a != null && a.moderation != null && a.moderation.confined(player);
  }

  @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
  private void ffa$inventory(ServerboundContainerClickPacket packet, CallbackInfo ci) {
    if (ffa$jailed()) {
      player.containerMenu.sendAllDataToRemote();
      ci.cancel();
    }
  }

  @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
  private void ffa$drop(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
    if (ffa$jailed()
        && (packet.getAction() == ServerboundPlayerActionPacket.Action.DROP_ITEM
            || packet.getAction() == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
            || packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND)) {
      player.inventoryMenu.sendAllDataToRemote();
      ci.cancel();
    }
  }
}
