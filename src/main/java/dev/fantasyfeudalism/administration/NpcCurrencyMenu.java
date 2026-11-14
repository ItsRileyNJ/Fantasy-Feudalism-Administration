package dev.fantasyfeudalism.administration;

import dev.fantasyfeudalism.administration.mixin.MerchantMenuAccess;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.*;

/** Vanilla merchant screen; all visible trade items are read-only previews. */
public final class NpcCurrencyMenu extends MerchantMenu {
  final NpcTrades adapter;
  final NpcTrades.Handle npc;
  private final List<NpcTrades.Quote> quotes;
  public final MerchantOffers offers;
  private int selected;

  NpcCurrencyMenu(
      int id,
      Inventory inventory,
      NpcTrades adapter,
      NpcTrades.Handle npc,
      List<NpcTrades.Quote> quotes) {
    this(id, inventory, adapter, npc, quotes, adapter.visuals(npc, quotes));
  }

  private NpcCurrencyMenu(
      int id,
      Inventory inventory,
      NpcTrades adapter,
      NpcTrades.Handle npc,
      List<NpcTrades.Quote> quotes,
      MerchantOffers offers) {
    super(id, inventory, new PreviewMerchant(npc.merchant(), offers));
    this.adapter = adapter;
    this.npc = npc;
    this.quotes = List.copyOf(quotes);
    this.offers = offers;
    setSelectionHint(0);
  }

  @Override
  public void setSelectionHint(int index) {
    if (index < 0 || index >= offers.size()) return;
    selected = index;
    super.setSelectionHint(index);
    var container = ((MerchantMenuAccess) (Object) this).ffa$container();
    var offer = offers.get(index);
    container.setItem(0, offer.getCostA().copy());
    container.setItem(1, offer.getCostB().copy());
    container.updateSellItem();
    broadcastFullState();
  }

  @Override
  public void tryMoveItems(int index) {
    setSelectionHint(index);
  }

  @Override
  public boolean stillValid(Player player) {
    return player instanceof ServerPlayer p && adapter.valid(npc, p);
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player player) {
    if (!(player instanceof ServerPlayer p)) return;
    if (slot == 2 && button == 0 && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) {
      try {
        adapter.trade(npc, quotes.get(selected), p);
      } catch (Exception ex) {
        Teleports.tell(
            p, ex.getMessage() == null ? "Trade could not be completed." : ex.getMessage());
      }
    }
    if (p.containerMenu == this) broadcastFullState();
    else p.inventoryMenu.broadcastFullState();
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    return ItemStack.EMPTY;
  }

  @Override
  public boolean canDragTo(Slot slot) {
    return false;
  }

  @Override
  public void removed(Player player) {
    // Vanilla returns merchant inputs on close. They are previews here, never real currency/items.
    ((MerchantMenuAccess) (Object) this).ffa$container().clearContent();
    super.removed(player);
  }

  private record PreviewMerchant(Merchant original, MerchantOffers offers) implements Merchant {
    public void setTradingPlayer(Player player) {
      original.setTradingPlayer(player);
    }

    public Player getTradingPlayer() {
      return original.getTradingPlayer();
    }

    public MerchantOffers getOffers() {
      return offers;
    }

    public void overrideOffers(MerchantOffers value) {}

    public void notifyTrade(MerchantOffer offer) {
      throw new IllegalStateException("Preview offers cannot be traded directly");
    }

    public void notifyTradeUpdated(ItemStack stack) {}

    public int getVillagerXp() {
      return original.getVillagerXp();
    }

    public void overrideXp(int xp) {}

    public boolean showProgressBar() {
      return false;
    }

    public SoundEvent getNotifyTradeSound() {
      return original.getNotifyTradeSound();
    }

    public boolean isClientSide() {
      return false;
    }
  }
}
