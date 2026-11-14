package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.Rules;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RulesChatMixin {
  @Shadow public ServerPlayer player;

  // Filter before addPendingMessage so hidden signed messages cannot fill the acknowledgement
  // queue.
  @Inject(method = "sendPlayerChatMessage", at = @At("HEAD"), cancellable = true)
  private void ffa$chat(PlayerChatMessage message, ChatType.Bound type, CallbackInfo ci) {
    if (!Rules.chatAllowed(player, message.link().sender())) ci.cancel();
  }

  @Inject(method = "sendDisguisedChatMessage", at = @At("HEAD"), cancellable = true)
  private void ffa$unsigned(Component message, ChatType.Bound type, CallbackInfo ci) {
    if (!Rules.chatAllowed(player, null)) ci.cancel();
  }
}
