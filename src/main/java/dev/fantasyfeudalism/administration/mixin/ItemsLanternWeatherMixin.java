package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.LanternProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets = {
      "com.fantasyfeudalism.items.lighting.WeatherLighting",
      "com.fantasyfeudalism.additions.lighting.WeatherLighting"
    },
    remap = false)
public abstract class ItemsLanternWeatherMixin {
  @Inject(method = "check", at = @At("HEAD"), cancellable = true)
  private static void ffa$weather(
      ServerLevel level, BlockPos pos, double roll, CallbackInfo callback) {
    if (LanternProtection.paused(level, pos, level.getBlockState(pos))) callback.cancel();
  }
}
