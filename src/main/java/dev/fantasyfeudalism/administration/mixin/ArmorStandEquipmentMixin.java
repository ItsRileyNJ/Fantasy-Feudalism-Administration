package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.Administration;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArmorStand.class)
public abstract class ArmorStandEquipmentMixin {
  @Unique
  private boolean ffa$denied(Player player) {
    var app = Administration.INSTANCE;
    return player instanceof ServerPlayer server
        && app != null
        && app.protection != null
        && app.protection.standEquipmentDenied(server, (ArmorStand) (Object) this);
  }

  @Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
  private void ffa$interact(
      Player player,
      Vec3 hit,
      InteractionHand hand,
      CallbackInfoReturnable<InteractionResult> callback) {
    if (ffa$denied(player)) callback.setReturnValue(InteractionResult.FAIL);
  }

  @Inject(method = "swapItem", at = @At("HEAD"), cancellable = true)
  private void ffa$swap(
      Player player,
      EquipmentSlot slot,
      ItemStack held,
      InteractionHand hand,
      CallbackInfoReturnable<Boolean> callback) {
    if (ffa$denied(player)) callback.setReturnValue(false);
  }
}
