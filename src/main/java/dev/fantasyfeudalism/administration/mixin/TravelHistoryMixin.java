package dev.fantasyfeudalism.administration.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.fantasyfeudalism.administration.*;
import java.util.Set;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerPlayer.class)
public abstract class TravelHistoryMixin {
  private BackHistory ffa$history() {
    var a = Administration.INSTANCE;
    return a == null || a.server == null ? null : a.backHistory;
  }

  @WrapMethod(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z")
  private boolean ffa$command(
      ServerLevel level,
      double x,
      double y,
      double z,
      Set<RelativeMovement> relative,
      float yaw,
      float pitch,
      Operation<Boolean> original) {
    var h = ffa$history();
    var p = (ServerPlayer) (Object) this;
    if (h != null) h.begin(p);
    try {
      return original.call(level, x, y, z, relative, yaw, pitch);
    } finally {
      if (h != null) h.end(p);
    }
  }

  @WrapMethod(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V")
  private void ffa$direct(
      ServerLevel level,
      double x,
      double y,
      double z,
      float yaw,
      float pitch,
      Operation<Void> original) {
    var h = ffa$history();
    var p = (ServerPlayer) (Object) this;
    if (h != null) h.begin(p);
    try {
      original.call(level, x, y, z, yaw, pitch);
    } finally {
      if (h != null) h.end(p);
    }
  }

  @WrapMethod(method = "teleportTo(DDD)V")
  private void ffa$same(double x, double y, double z, Operation<Void> original) {
    var h = ffa$history();
    var p = (ServerPlayer) (Object) this;
    if (h != null) h.begin(p);
    try {
      original.call(x, y, z);
    } finally {
      if (h != null) h.end(p);
    }
  }

  @WrapMethod(method = "teleportRelative")
  private void ffa$relative(double x, double y, double z, Operation<Void> original) {
    var h = ffa$history();
    var p = (ServerPlayer) (Object) this;
    if (h != null) h.begin(p);
    try {
      original.call(x, y, z);
    } finally {
      if (h != null) h.end(p);
    }
  }

  @WrapMethod(method = "changeDimension")
  private Entity ffa$dimension(DimensionTransition transition, Operation<Entity> original) {
    var h = ffa$history();
    var p = (ServerPlayer) (Object) this;
    if (h != null) h.begin(p);
    try {
      return original.call(transition);
    } finally {
      if (h != null) h.end(p);
    }
  }
}
