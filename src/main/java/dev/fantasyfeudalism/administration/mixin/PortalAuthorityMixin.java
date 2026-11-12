package dev.fantasyfeudalism.administration.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.fantasyfeudalism.administration.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemStack.class)
public abstract class PortalAuthorityMixin {
  @WrapMethod(method = "useOn")
  private InteractionResult ffa$use(UseOnContext context, Operation<InteractionResult> original) {
    if (PortalCreation.denyEye(context)) {
      context
          .getPlayer()
          .displayClientMessage(
              net.minecraft.network.chat.Component.literal(
                  "Only administrators can create portals."),
              false);
      return InteractionResult.FAIL;
    }
    PortalCreation.enter(
        context.getPlayer() instanceof ServerPlayer player && PortalCreation.admin(player));
    try {
      return original.call(context);
    } finally {
      PortalCreation.leave();
    }
  }
}
