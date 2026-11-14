package dev.fantasyfeudalism.administration.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.fantasyfeudalism.administration.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ServerPlayer.class)
public abstract class NpcMenuMixin {
  @WrapOperation(
      method =
          "openMenu(Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)Ljava/util/OptionalInt;",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/MenuProvider;createMenu(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/inventory/AbstractContainerMenu;"))
  private AbstractContainerMenu ffa$menu(
      MenuProvider provider,
      int id,
      Inventory inventory,
      Player player,
      Operation<AbstractContainerMenu> original) {
    AbstractContainerMenu menu = original.call(provider, id, inventory, player);
    var a = Administration.INSTANCE;
    return a == null || a.server == null || a.npcTrades == null
        ? menu
        : a.npcTrades.adapt(menu, (ServerPlayer) player);
  }

  @ModifyVariable(method = "sendMerchantOffers", at = @At("HEAD"), argsOnly = true)
  private MerchantOffers ffa$offers(MerchantOffers offers) {
    var player = (ServerPlayer) (Object) this;
    return player.containerMenu instanceof NpcCurrencyMenu menu ? menu.offers : offers;
  }
}
