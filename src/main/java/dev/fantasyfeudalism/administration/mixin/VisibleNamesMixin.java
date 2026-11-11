package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.Vanish;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CommandSourceStack.class)
public abstract class VisibleNamesMixin {
  @Inject(method = "getOnlinePlayerNames", at = @At("HEAD"), cancellable = true)
  private void ffa$names(CallbackInfoReturnable<Collection<String>> ci) {
    if (Vanish.active()) {
      var source = (CommandSourceStack) (Object) this;
      ci.setReturnValue(
          source.getServer().getPlayerList().getPlayers().stream()
              .filter(p -> Vanish.canSee(source, p))
              .map(p -> p.getGameProfile().getName())
              .toList());
    }
  }
}
