package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.PortalPack;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerHandshakePacketListenerImpl.class)
public abstract class PortalPackHandshakeMixin {
  @Shadow @Final private Connection connection;

  @Inject(method = "handleIntention", at = @At("HEAD"))
  private void ffa$address(ClientIntentionPacket packet, CallbackInfo callback) {
    PortalPack.address(connection, packet.hostName(), packet.port());
  }
}
