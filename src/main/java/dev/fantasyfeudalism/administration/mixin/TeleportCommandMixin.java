package dev.fantasyfeudalism.administration.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.brigadier.exceptions.*;
import dev.fantasyfeudalism.administration.*;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Validate after coordinate-changing events, with the actual command dimension available. */
@Mixin(TeleportCommand.class)
public abstract class TeleportCommandMixin {
  @WrapOperation(
      method = "performTeleport",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/Entity;teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z"))
  private static boolean ffa$move(
      Entity entity,
      ServerLevel level,
      double x,
      double y,
      double z,
      Set<RelativeMovement> relative,
      float yaw,
      float pitch,
      Operation<Boolean> original)
      throws CommandSyntaxException {
    var app = Administration.INSTANCE;
    if (entity instanceof ServerPlayer player
        && app != null
        && app.server != null
        && app.teleports != null) {
      String error =
          app.teleports.validate(
              player,
              new Model.Point(level.dimension().location().toString(), x, y, z, yaw, pitch));
      if (error != null) throw new SimpleCommandExceptionType(Component.literal(error)).create();
    }
    boolean moved = original.call(entity, level, x, y, z, relative, yaw, pitch);
    if (entity instanceof ServerPlayer player
        && (!moved
            || player.serverLevel() != level
            || player.position().distanceToSqr(new Vec3(x, y, z)) > .01))
      throw new SimpleCommandExceptionType(Component.literal("Teleport was blocked.")).create();
    return moved;
  }
}
