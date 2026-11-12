package dev.fantasyfeudalism.administration.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.fantasyfeudalism.administration.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerPlayer.class)
public abstract class PortalTravelAuthorityMixin {
  @WrapMethod(method = "changeDimension")
  private Entity ffa$travel(DimensionTransition transition, Operation<Entity> original) {
    PortalCreation.enter(PortalCreation.admin((ServerPlayer) (Object) this));
    try {
      return original.call(transition);
    } finally {
      PortalCreation.leave();
    }
  }
}
