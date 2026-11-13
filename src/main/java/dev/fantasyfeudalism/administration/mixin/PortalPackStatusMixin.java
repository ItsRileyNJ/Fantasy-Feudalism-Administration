package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.PortalPack;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class PortalPackStatusMixin {

  @Inject(method = "handleResourcePackResponse", at = @At("RETURN"))
  private void ffa$status(ServerboundResourcePackPacket packet, CallbackInfo callback) {
    PortalPack.response((ServerCommonPacketListenerImpl) (Object) this, packet);
  }
}
