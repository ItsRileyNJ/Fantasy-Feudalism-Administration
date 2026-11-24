package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class WeatherMixin {
  @Inject(method = "tickPrecipitation", at = @At("HEAD"), cancellable = true)
  private void ffa$precipitation(BlockPos pos, CallbackInfo callback) {
    if (Protection.block((ServerLevel) (Object) this, pos, Model.Flag.WEATHER)) callback.cancel();
  }

  // This invocation guards the natural lightning branch, before its horse/bolt entities exist.
  // Commands such as smite and summon do not use this branch.
  @Redirect(
      method = "tickChunk",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/level/ServerLevel;isRainingAt(Lnet/minecraft/core/BlockPos;)Z"))
  private boolean ffa$naturalLightning(ServerLevel level, BlockPos pos) {
    return !Protection.block(level, pos, Model.Flag.WEATHER) && level.isRainingAt(pos);
  }
}
