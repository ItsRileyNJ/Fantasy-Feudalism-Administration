package dev.fantasyfeudalism.administration.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.fantasyfeudalism.administration.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlayerList.class)
public abstract class RespawnHistoryMixin {
  @WrapMethod(method = "respawn")
  private ServerPlayer ffa$respawn(
      ServerPlayer player,
      boolean keepInventory,
      Entity.RemovalReason reason,
      Operation<ServerPlayer> original) {
    var app = Administration.INSTANCE;
    var history = app == null ? null : app.backHistory;
    Model.Point origin = history == null ? null : Teleports.point(player);
    boolean death = player.getHealth() <= 0;
    if (history != null) history.suppress();
    ServerPlayer result;
    try {
      result = original.call(player, keepInventory, reason);
    } finally {
      if (history != null) history.resume();
    }
    if (history != null && !death && BackHistory.different(origin, Teleports.point(result)))
      history.record(result, origin);
    return result;
  }
}
