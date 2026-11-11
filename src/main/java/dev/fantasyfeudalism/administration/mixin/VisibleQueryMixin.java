package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.Vanish;
import java.net.DatagramPacket;
import net.minecraft.server.ServerInterface;
import net.minecraft.server.rcon.thread.QueryThreadGs4;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(QueryThreadGs4.class)
public abstract class VisibleQueryMixin {
  @Shadow private long lastRulesResponse;

  @Inject(method = "buildRuleResponse", at = @At("HEAD"))
  private void ffa$cache(DatagramPacket request, CallbackInfoReturnable<byte[]> ci) {
    if (Vanish.active()) lastRulesResponse = 0;
  }

  @Redirect(
      method = {"processPacket", "buildRuleResponse"},
      at =
          @At(value = "INVOKE", target = "Lnet/minecraft/server/ServerInterface;getPlayerCount()I"))
  private int ffa$count(ServerInterface server) {
    return Vanish.active() ? Vanish.publicProfiles().size() : server.getPlayerCount();
  }

  @Redirect(
      method = "buildRuleResponse",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/server/ServerInterface;getPlayerNames()[Ljava/lang/String;"))
  private String[] ffa$names(ServerInterface server) {
    return Vanish.active()
        ? Vanish.publicProfiles().stream().map(p -> p.getName()).toArray(String[]::new)
        : server.getPlayerNames();
  }
}
