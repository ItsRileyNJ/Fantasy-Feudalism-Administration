package dev.fantasyfeudalism.administration.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.fantasyfeudalism.administration.Rules;
import java.util.function.Predicate;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlayerList.class)
public abstract class RulesBroadcastMixin {
  @WrapMethod(
      method =
          "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V")
  private void ffa$author(
      PlayerChatMessage message,
      Predicate<ServerPlayer> filter,
      ServerPlayer sender,
      ChatType.Bound type,
      Operation<Void> original) {
    try (var ignored = Rules.from(sender)) {
      original.call(message, filter, sender, type);
    }
  }
}
