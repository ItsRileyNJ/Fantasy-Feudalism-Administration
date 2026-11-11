package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.Vanish;
import java.util.*;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.ListPlayersCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ListPlayersCommand.class)
public abstract class VisibleListMixin {
  @Redirect(
      method = "format",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/server/players/PlayerList;getPlayers()Ljava/util/List;"))
  private static List<ServerPlayer> ffa$list(
      PlayerList list, CommandSourceStack source, Function<ServerPlayer, Component> formatter) {
    return list.getPlayers().stream().filter(p -> Vanish.canSee(source, p)).toList();
  }
}
