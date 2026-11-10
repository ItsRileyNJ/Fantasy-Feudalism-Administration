package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.*;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobMixin {
  @Inject(method = "canPickUpLoot", at = @At("HEAD"), cancellable = true)
  private void ffa$loot(CallbackInfoReturnable<Boolean> ci) {
    Mob m = (Mob) (Object) this;
    if (Administration.INSTANCE != null
        && Administration.INSTANCE.compat != null
        && !Administration.INSTANCE.compat.npc(m)
        && Protection.block(m.level(), m.blockPosition(), Model.Flag.MOB_GRIEFING))
      ci.setReturnValue(false);
  }
}
