package dev.fantasyfeudalism.administration.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.fantasyfeudalism.administration.LanternProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.fantasyfeudalism.items.lighting.LightFuel", remap = false)
public abstract class ItemsLanternFuelMixin {
  @ModifyExpressionValue(
      method = "tick",
      at =
          @At(
              value = "FIELD",
              target = "Lcom/fantasyfeudalism/items/lighting/LightFuel$Entry;soul:Z"))
  private static boolean ffa$pause(
      boolean soul, @Local ServerLevel level, @Local BlockPos pos, @Local BlockState state) {
    // Skip this iteration exactly like a non-burning light; never refill or rewrite its soul flag.
    return soul || LanternProtection.paused(level, pos, state);
  }
}
