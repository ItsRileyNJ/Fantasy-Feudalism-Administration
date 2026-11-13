package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.PortalPack;
import io.netty.channel.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.network.ServerConnectionListener$1")
public abstract class PortalPackChannelMixin {
  @Inject(method = "initChannel", at = @At("RETURN"))
  private void ffa$pack(Channel channel, CallbackInfo callback) {
    channel.pipeline().addFirst("ffa_portal_pack", new PortalPack.HttpHandler());
  }
}
