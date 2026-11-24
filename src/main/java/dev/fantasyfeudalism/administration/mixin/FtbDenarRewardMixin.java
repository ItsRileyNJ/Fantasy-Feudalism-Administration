package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.*;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.quest.reward.CustomReward", remap = false)
public abstract class FtbDenarRewardMixin {
  @Inject(method = "claim", at = @At("HEAD"), cancellable = true)
  private void ffa$reward(
      ServerPlayer player, boolean notify, CallbackInfoReturnable<Boolean> callback) {
    if (!FtbQuests.tagged(this)) return;
    var app = Administration.INSTANCE;
    callback.setReturnValue(app != null && app.quests != null && app.quests.reward(this, player));
  }
}
