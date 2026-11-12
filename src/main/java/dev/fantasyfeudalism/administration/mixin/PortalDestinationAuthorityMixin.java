package dev.fantasyfeudalism.administration.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.fantasyfeudalism.administration.PortalCreation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(NetherPortalBlock.class)
public abstract class PortalDestinationAuthorityMixin {
  @WrapMethod(method = "getPortalDestination")
  private DimensionTransition ffa$destination(
      ServerLevel level, Entity entity, BlockPos at, Operation<DimensionTransition> original) {
    PortalCreation.enter(entity instanceof ServerPlayer player && PortalCreation.admin(player));
    try {
      return original.call(level, entity, at);
    } finally {
      PortalCreation.leave();
    }
  }
}
