package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.quest.task.CustomTask", remap = false)
public abstract class FtbDenarTaskMixin {
  @Inject(method = "readData", at = @At("RETURN"))
  private void ffa$button(CompoundTag data, HolderLookup.Provider provider, CallbackInfo callback) {
    FtbQuests.configureTask(this);
  }

  @Inject(method = "autoSubmitOnPlayerTick", at = @At("HEAD"), cancellable = true)
  private void ffa$manualOnly(CallbackInfoReturnable<Integer> callback) {
    if (FtbQuests.tagged(this)) callback.setReturnValue(0);
  }

  @Inject(method = "submitTask", at = @At("HEAD"), cancellable = true)
  private void ffa$pay(
      @Coerce Object team, ServerPlayer player, ItemStack item, CallbackInfo callback) {
    if (!FtbQuests.tagged(this)) return;
    callback.cancel();
    var app = Administration.INSTANCE;
    if (app != null && app.quests != null) app.quests.pay(this, team, player);
  }
}
