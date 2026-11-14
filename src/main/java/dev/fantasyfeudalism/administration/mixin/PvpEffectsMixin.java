package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.Administration;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PvpEffectsMixin {
  @Inject(
      method =
          "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
      at = @At("HEAD"),
      cancellable = true)
  private void ffa$effect(
      MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> ci) {
    var app = Administration.INSTANCE;
    if (app != null
        && app.server != null
        && app.combat != null
        && effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL
        && app.combat.denied((LivingEntity) (Object) this, source)) ci.setReturnValue(false);
  }

  @Inject(
      method =
          "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
      at = @At("RETURN"))
  private void ffa$effectHit(
      MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> ci) {
    var app = Administration.INSTANCE;
    if (ci.getReturnValue()
        && app != null
        && app.server != null
        && app.combat != null
        && effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
      app.combat.effectHit((LivingEntity) (Object) this, source);
  }
}
