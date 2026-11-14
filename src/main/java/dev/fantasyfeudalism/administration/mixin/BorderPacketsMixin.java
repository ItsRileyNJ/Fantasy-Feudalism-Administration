package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.WorldBorders;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class BorderPacketsMixin {
  @Shadow
  public abstract void send(Packet<?> packet, PacketSendListener listener);

  @Inject(
      method =
          "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void ffa$border(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
    if ((Object) this instanceof ServerGamePacketListenerImpl play) {
      Packet<?> filtered = WorldBorders.filter(play.player, packet);
      if (filtered != packet) {
        send(filtered, listener);
        ci.cancel();
      }
    }
  }
}
