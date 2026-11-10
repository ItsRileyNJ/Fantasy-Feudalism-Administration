package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public abstract class FoodMixin {
  @Shadow private int foodLevel;
  @Shadow private float saturationLevel;
  @Shadow private float exhaustionLevel;
  @Unique private boolean ffa$freeze;
  @Unique private int ffa$food;
  @Unique private float ffa$saturation;

  @Inject(method = "tick", at = @At("HEAD"))
  private void ffa$before(Player p, CallbackInfo ci) {
    var a = Administration.INSTANCE;
    ffa$freeze =
        p instanceof ServerPlayer s
            && a.server != null
            && !a.compat.admin(s)
            && Protection.block(s.serverLevel(), s.blockPosition(), Model.Flag.HUNGER);
    if (ffa$freeze) {
      ffa$food = foodLevel;
      ffa$saturation = saturationLevel;
      exhaustionLevel = 0;
    }
  }

  @Inject(method = "tick", at = @At("RETURN"))
  private void ffa$after(Player p, CallbackInfo ci) {
    if (ffa$freeze) {
      foodLevel = Math.max(foodLevel, ffa$food);
      saturationLevel = Math.max(saturationLevel, ffa$saturation);
      exhaustionLevel = 0;
    }
  }
}
