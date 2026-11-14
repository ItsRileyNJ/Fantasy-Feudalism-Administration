package dev.fantasyfeudalism.administration.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.ParseResults;
import dev.fantasyfeudalism.administration.*;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(net.minecraft.commands.Commands.class)
public abstract class RulesCommandsMixin {
  @WrapMethod(method = "performCommand")
  private void ffa$commands(
      ParseResults<CommandSourceStack> parse, String command, Operation<Void> original) {
    var a = Administration.INSTANCE;
    var player = parse.getContext().getSource().getPlayer();
    if (a != null
        && a.rules != null
        && player != null
        && !a.rules.commandAllowed(player, command)) {
      a.rules.prompt(player, false);
      return;
    }
    try (var ignored = Rules.from(player)) {
      original.call(parse, command);
    }
  }
}
