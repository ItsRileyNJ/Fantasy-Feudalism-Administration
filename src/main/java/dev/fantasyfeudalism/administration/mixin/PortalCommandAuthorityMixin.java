package dev.fantasyfeudalism.administration.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.ParseResults;
import dev.fantasyfeudalism.administration.*;
import net.minecraft.commands.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(net.minecraft.commands.Commands.class)
public abstract class PortalCommandAuthorityMixin {
  @WrapMethod(method = "performCommand")
  private void ffa$command(
      ParseResults<CommandSourceStack> parse, String text, Operation<Void> original) {
    PortalCreation.enter(PortalCreation.admin(parse.getContext().getSource()));
    try {
      original.call(parse, text);
    } finally {
      PortalCreation.leave();
    }
  }
}
