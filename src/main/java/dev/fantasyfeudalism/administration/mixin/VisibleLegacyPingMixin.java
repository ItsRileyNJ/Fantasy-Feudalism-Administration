package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.Vanish;
import net.minecraft.server.ServerInfo;
import net.minecraft.server.network.LegacyQueryHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(LegacyQueryHandler.class)
public abstract class VisibleLegacyPingMixin {
  @Redirect(
      method = {"createVersion0Response", "createVersion1Response"},
      at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ServerInfo;getPlayerCount()I"))
  private static int ffa$count(ServerInfo server) {
    return Vanish.active() ? Vanish.publicProfiles().size() : server.getPlayerCount();
  }
}
