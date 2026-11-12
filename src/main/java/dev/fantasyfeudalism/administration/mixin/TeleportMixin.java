package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.*;
import java.util.Set;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ServerPlayer.class)
public abstract class TeleportMixin {
  private String ffa$validate(ServerLevel l, double x, double y, double z, float yaw, float pitch) {
    var a = Administration.INSTANCE;
    if (a == null || a.teleports == null || a.server == null) return null;
    return a.teleports.validate(
        (ServerPlayer) (Object) this,
        new Model.Point(l.dimension().location().toString(), x, y, z, yaw, pitch));
  }

  @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"), cancellable = true)
  private void ffa$same(double x, double y, double z, CallbackInfo ci) {
    var p = (ServerPlayer) (Object) this;
    String error = ffa$validate(p.serverLevel(), x, y, z, p.getYRot(), p.getXRot());
    if (error != null) {
      Teleports.tell(p, error);
      ci.cancel();
    }
  }

  @Inject(method = "teleportRelative", at = @At("HEAD"), cancellable = true)
  private void ffa$relative(double x, double y, double z, CallbackInfo ci) {
    var p = (ServerPlayer) (Object) this;
    String error =
        ffa$validate(
            p.serverLevel(), p.getX() + x, p.getY() + y, p.getZ() + z, p.getYRot(), p.getXRot());
    if (error != null) {
      Teleports.tell(p, error);
      ci.cancel();
    }
  }

  @Inject(
      method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z",
      at = @At("HEAD"),
      cancellable = true)
  private void ffa$command(
      ServerLevel l,
      double x,
      double y,
      double z,
      Set<RelativeMovement> relative,
      float yaw,
      float pitch,
      CallbackInfoReturnable<Boolean> ci) {
    ServerPlayer p = (ServerPlayer) (Object) this;
    double ax = relative.contains(RelativeMovement.X) ? p.getX() + x : x,
        ay = relative.contains(RelativeMovement.Y) ? p.getY() + y : y,
        az = relative.contains(RelativeMovement.Z) ? p.getZ() + z : z;
    String error = ffa$validate(l, ax, ay, az, yaw, pitch);
    if (error != null) {
      Teleports.tell(p, error);
      ci.setReturnValue(false);
    }
  }

  @Inject(
      method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V",
      at = @At("HEAD"),
      cancellable = true)
  private void ffa$direct(
      ServerLevel l, double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
    String error = ffa$validate(l, x, y, z, yaw, pitch);
    if (error != null) {
      Teleports.tell((ServerPlayer) (Object) this, error);
      ci.cancel();
    }
  }

  @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
  private void ffa$dimension(DimensionTransition d, CallbackInfoReturnable<Entity> ci) {
    String error = ffa$validate(d.newLevel(), d.pos().x, d.pos().y, d.pos().z, d.yRot(), d.xRot());
    if (error != null) {
      Teleports.tell((ServerPlayer) (Object) this, error);
      ci.setReturnValue(null);
    }
  }

  @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("RETURN"))
  private void ffa$arrived(
      ServerLevel l, double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
    var a = Administration.INSTANCE;
    if (a != null && a.protection != null && a.server != null)
      a.protection.update((ServerPlayer) (Object) this);
  }
}
