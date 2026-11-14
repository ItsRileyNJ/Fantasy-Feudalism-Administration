package dev.fantasyfeudalism.administration.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.fantasyfeudalism.administration.Rules;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.commands.MsgCommand;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MsgCommand.class)
public abstract class RulesWhisperMixin {
  @WrapMethod(method = "sendMessage")
  private static void ffa$author(
      CommandSourceStack source,
      Collection<ServerPlayer> targets,
      PlayerChatMessage message,
      Operation<Void> original) {
    try (var ignored = Rules.from(source.getPlayer())) {
      original.call(source, targets, message);
    }
  }
}
