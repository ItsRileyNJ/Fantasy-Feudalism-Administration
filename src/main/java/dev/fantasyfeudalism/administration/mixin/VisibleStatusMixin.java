package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.Vanish;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.status.*;
import net.minecraft.server.network.ServerStatusPacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerStatusPacketListenerImpl.class)
public abstract class VisibleStatusMixin {
  @Shadow @Final private ServerStatus status;
  @Shadow @Final private Connection connection;
  @Shadow private boolean hasRequestedStatus;

  @Inject(method = "handleStatusRequest", at = @At("HEAD"), cancellable = true)
  private void ffa$status(ServerboundStatusRequestPacket packet, CallbackInfo ci) {
    if (!Vanish.active()) return;
    if (hasRequestedStatus)
      connection.disconnect(Component.translatable("multiplayer.status.request_handled"));
    else {
      hasRequestedStatus = true;
      connection.send(new ClientboundStatusResponsePacket(Vanish.publicStatus(status), null));
    }
    ci.cancel();
  }
}
