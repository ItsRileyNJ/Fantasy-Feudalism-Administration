package dev.fantasyfeudalism.administration.mixin;

import net.minecraft.world.inventory.*;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantMenu.class)
public interface MerchantMenuAccess {
  @Accessor("trader")
  Merchant ffa$trader();

  @Accessor("tradeContainer")
  MerchantContainer ffa$container();
}
