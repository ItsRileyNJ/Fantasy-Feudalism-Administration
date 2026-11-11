package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.Vanish;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntitySelector.class)
public abstract class VisibleSelectorsMixin {
  @Inject(method = "findPlayers", at = @At("RETURN"), cancellable = true)
  private void ffa$players(
      CommandSourceStack source, CallbackInfoReturnable<List<ServerPlayer>> ci) {
    if (Vanish.active())
      ci.setReturnValue(
          ci.getReturnValue().stream().filter(p -> Vanish.canSee(source, p)).toList());
  }

  @Inject(method = "findEntities", at = @At("RETURN"), cancellable = true)
  private void ffa$entities(
      CommandSourceStack source, CallbackInfoReturnable<List<? extends Entity>> ci) {
    if (Vanish.active())
      ci.setReturnValue(
          ci.getReturnValue().stream()
              .filter(e -> !(e instanceof ServerPlayer p) || Vanish.canSee(source, p))
              .toList());
  }
}
