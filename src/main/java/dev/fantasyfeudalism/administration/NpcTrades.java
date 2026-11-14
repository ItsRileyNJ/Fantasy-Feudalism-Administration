package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;

import com.mojang.serialization.JsonOps;
import dev.fantasyfeudalism.administration.mixin.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.component.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.fml.ModList;

/** Optional Easy NPC 7.18.0 adapter. No Easy NPC classes are linked or shipped. */
public final class NpcTrades {
  final Administration app;
  private Class<?> tradingType, safeType;
  private Field tradingField;
  private Method entityMethod, dataMethod, resetMethod, offersMethod;
  private String unavailable = "Easy NPC 7.18.0 is not installed.";

  public NpcTrades(Administration app) {
    this.app = app;
    if (!ModList.get().isLoaded("easy_npc")) return;
    try {
      String version =
          ModList.get()
              .getModContainerById("easy_npc")
              .orElseThrow()
              .getModInfo()
              .getVersion()
              .toString();
      if (!version.equals("7.18.0")) {
        unavailable = "This adapter supports Easy NPC 7.18.0; installed: " + version + ".";
        return;
      }
      tradingType =
          Class.forName("de.markusbordihn.easynpc.entity.easynpc.data.TradingDataCapable");
      safeType = Class.forName("de.markusbordihn.easynpc.data.trading.SafeMerchantData");
      tradingField = safeType.getDeclaredField("tradingData");
      tradingField.setAccessible(true);
      entityMethod = tradingType.getMethod("getEntity");
      dataMethod = tradingType.getMethod("getTradingDataSet");
      resetMethod = dataMethod.getReturnType().getMethod("getLastReset");
      offersMethod = tradingType.getMethod("setTradingOffers", MerchantOffers.class);
      unavailable = null;
    } catch (ReflectiveOperationException ex) {
      tradingType = null;
      unavailable = "Easy NPC trading adapter is unavailable; check the server log.";
      Administration.LOG.error("Easy NPC trading API mismatch", ex);
    }
  }

  public record Handle(Entity entity, Object trading, Merchant merchant) {}

  record Quote(int index, String revision, boolean buy, long price, String fingerprint) {}

  public boolean available() {
    return unavailable == null;
  }

  public Handle handle(Merchant merchant) {
    if (!available()) return null;
    try {
      Object trading =
          safeType.isInstance(merchant)
              ? tradingField.get(merchant)
              : tradingType.isInstance(merchant) ? merchant : null;
      return trading == null
          ? null
          : new Handle((Entity) entityMethod.invoke(trading), trading, merchant);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalArgumentException("Cannot read this NPC's trade data.", ex);
    }
  }

  long reset(Handle handle) throws ReflectiveOperationException {
    return ((Number) resetMethod.invoke(dataMethod.invoke(handle.trading))).longValue();
  }

  String fingerprint(MerchantOffer offer) {
    var json =
        MerchantOffer.CODEC
            .encodeStart(
                app.server.registryAccess().createSerializationContext(JsonOps.INSTANCE), offer)
            .getOrThrow()
            .getAsJsonObject();
    for (String field :
        List.of("uses", "specialPrice", "demand", "priceMultiplier", "xp", "rewardExp"))
      json.remove(field);
    return json.toString();
  }

  boolean pending(String npc) {
    return app.storage.state().trades.values().stream().anyMatch(t -> npc.equals(t.npc));
  }

  private void requireAvailable() {
    if (!available()) throw new IllegalArgumentException(unavailable);
  }

  public Handle looking(ServerPlayer player) throws ReflectiveOperationException {
    requireAvailable();
    Vec3 start = player.getEyePosition(), end = start.add(player.getLookAngle().scale(8));
    var block =
        player
            .level()
            .clip(
                new ClipContext(
                    start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    double distance =
        block.getType() == HitResult.Type.MISS ? 64 : start.distanceToSqr(block.getLocation());
    Entity chosen = null;
    for (Entity entity :
        player
            .level()
            .getEntities(
                player,
                new AABB(start, end).inflate(1),
                e -> tradingType.isInstance(e) && e.isAlive())) {
      var hit = entity.getBoundingBox().inflate(.15).clip(start, end);
      if (hit.isPresent() && start.distanceToSqr(hit.get()) <= distance) {
        chosen = entity;
        distance = start.distanceToSqr(hit.get());
      }
    }
    if (chosen == null)
      throw new IllegalArgumentException("Look at an Easy NPC within eight blocks.");
    Merchant merchant = (Merchant) tradingType.getMethod("getMerchant").invoke(chosen);
    return new Handle(chosen, chosen, merchant);
  }

  void price(Handle npc, int index, boolean buy, long amount) throws Exception {
    if (amount < 0 || amount > app.settings.maxBalance)
      throw new IllegalArgumentException("Price is outside the configured balance range.");
    MerchantOffer offer = offer(npc, index);
    if (pending(npc.entity.getUUID().toString()))
      throw new IllegalArgumentException("Recover this NPC's pending transaction first.");
    close(npc);
    String key = npc.entity.getUUID().toString(), fingerprint = fingerprint(offer);
    long reset = reset(npc);
    app.storage.update(
        s -> {
          var shop = s.npcShops.computeIfAbsent(key, k -> new NpcShop());
          var previous = shop.offers.get(index);
          var price = new NpcPrice();
          price.buy = buy;
          price.price = amount;
          price.revision = UUID.randomUUID().toString();
          price.fingerprint = fingerprint;
          price.reset = reset;
          price.used =
              previous != null
                      && previous.fingerprint.equals(fingerprint)
                      && previous.reset == reset
                  ? Math.max(previous.used, offer.getUses())
                  : offer.getUses();
          shop.offers.put(index, price);
        });
    sync(npc);
  }

  void clear(Handle npc, Integer index) throws Exception {
    String key = npc.entity.getUUID().toString();
    if (pending(key))
      throw new IllegalArgumentException("Recover this NPC's pending transaction first.");
    close(npc);
    sync(npc);
    app.storage.update(
        s -> {
          if (index == null) s.npcShops.remove(key);
          else {
            var shop = s.npcShops.get(key);
            if (shop != null) {
              shop.offers.remove(index);
              if (shop.offers.isEmpty()) s.npcShops.remove(key);
            }
          }
        });
  }

  private void close(Handle npc) {
    for (var p : app.server.getPlayerList().getPlayers()) {
      if (!(p.containerMenu instanceof MerchantMenu)) continue;
      Handle open =
          p.containerMenu instanceof NpcCurrencyMenu menu
              ? menu.npc
              : handle(((MerchantMenuAccess) p.containerMenu).ffa$trader());
      if (open != null && open.entity.getUUID().equals(npc.entity.getUUID())) p.closeContainer();
    }
  }

  MerchantOffer offer(Handle npc, int index) {
    var offers = npc.merchant.getOffers();
    if (index < 1 || index > offers.size())
      throw new IllegalArgumentException("Offer number must be 1–" + offers.size() + ".");
    return offers.get(index - 1);
  }

  private void sync(Handle npc) throws Exception {
    String key = npc.entity.getUUID().toString();
    var shop = app.storage.state().npcShops.get(key);
    if (shop == null) return;
    if (pending(key))
      throw new IllegalArgumentException("This NPC has a transaction awaiting recovery.");
    long reset = reset(npc);
    app.storage.update(
        s -> {
          for (var entry : s.npcShops.get(key).offers.entrySet()) {
            var price = entry.getValue();
            var offers = npc.merchant.getOffers();
            if (entry.getKey() > offers.size()) continue;
            var offer = offers.get(entry.getKey() - 1);
            if (!price.fingerprint.equals(fingerprint(offer))) continue;
            price.used =
                price.reset == reset ? Math.max(price.used, offer.getUses()) : offer.getUses();
            price.reset = reset;
          }
        });
    for (var entry : app.storage.state().npcShops.get(key).offers.entrySet()) {
      if (entry.getKey() > npc.merchant.getOffers().size()) continue;
      var offer = offer(npc, entry.getKey());
      if (entry.getValue().fingerprint.equals(fingerprint(offer)))
        ((MerchantOfferAccess) (Object) offer).ffa$uses(entry.getValue().used);
    }
    offersMethod.invoke(npc.trading, npc.merchant.getOffers());
  }

  public AbstractContainerMenu adapt(AbstractContainerMenu menu, ServerPlayer player) {
    if (!(menu instanceof MerchantMenu) || !available()) return menu;
    Handle npc = handle(((MerchantMenuAccess) menu).ffa$trader());
    if (npc == null || !app.storage.state().npcShops.containsKey(npc.entity.getUUID().toString()))
      return menu;
    try {
      if (app.rules.pending(player))
        throw new IllegalArgumentException("Accept the server rules before trading.");
      if (!app.settings.economy) throw new IllegalArgumentException("Economy is disabled.");
      sync(npc);
      var quotes = new ArrayList<Quote>();
      for (var entry :
          app.storage.state().npcShops.get(npc.entity.getUUID().toString()).offers.entrySet()) {
        var price = entry.getValue();
        if (!price.fingerprint.equals(fingerprint(offer(npc, entry.getKey()))))
          throw new IllegalArgumentException(
              "This NPC's offers changed. Ask an admin to set their Denar prices again.");
        quotes.add(
            new Quote(entry.getKey(), price.revision, price.buy, price.price, price.fingerprint));
      }
      return new NpcCurrencyMenu(menu.containerId, player.getInventory(), this, npc, quotes);
    } catch (Exception ex) {
      npc.merchant.setTradingPlayer(null);
      Teleports.tell(player, ex.getMessage());
      return null;
    }
  }

  boolean valid(Handle npc, ServerPlayer player) {
    return npc.entity.isAlive()
        && npc.entity.level() == player.level()
        && npc.entity.distanceToSqr(player) <= 64
        && npc.merchant.getTradingPlayer() == player
        && !app.rules.pending(player)
        && (app.moderation == null || !app.moderation.confined(player))
        && !app.protection.denied(
            player, player.serverLevel(), npc.entity.blockPosition(), Flag.NPC_TRADING);
  }

  void trade(Handle npc, Quote quote, ServerPlayer player) throws Exception {
    if (!valid(npc, player)) throw new IllegalArgumentException("This NPC is no longer available.");
    String key = npc.entity.getUUID().toString();
    var shop = app.storage.state().npcShops.get(key);
    var price = shop == null ? null : shop.offers.get(quote.index);
    if (price == null
        || !price.revision.equals(quote.revision)
        || !price.fingerprint.equals(fingerprint(offer(npc, quote.index)))
        || price.reset != reset(npc))
      throw new IllegalArgumentException("This offer changed. Reopen the trading menu.");
    if (pending(key))
      throw new IllegalArgumentException("This NPC has a transaction awaiting recovery.");
    var offer = offer(npc, quote.index);
    if (Math.max(price.used, offer.getUses()) >= offer.getMaxUses())
      throw new IllegalArgumentException("This offer is sold out.");
    app.economy.npcTrade(player, key, quote.index, price, offer);
    // Financial commit is complete. NPC scripts must never be retried as a failed money transfer.
    try {
      npc.merchant.setTradingPlayer(player);
      npc.merchant.notifyTrade(offer);
      var current = app.storage.state().npcShops.get(key).offers.get(quote.index);
      ((MerchantOfferAccess) (Object) offer).ffa$uses(Math.max(offer.getUses(), current.used));
      offersMethod.invoke(npc.trading, npc.merchant.getOffers());
    } catch (Exception ex) {
      Administration.LOG.error(
          "Denar trade committed, but NPC callback failed for {} offer {}", key, quote.index, ex);
    } finally {
      if (npc.merchant.getTradingPlayer() == player) npc.merchant.setTradingPlayer(null);
    }
    player.inventoryMenu.broadcastFullState();
    Teleports.tell(player, (quote.buy ? "Bought for " : "Sold for ") + quote.price + " Denar.");
  }

  static ItemStack currency(long price, boolean pay) {
    ItemStack icon = new ItemStack(Items.GOLD_NUGGET);
    icon.set(
        DataComponents.CUSTOM_NAME,
        Component.literal((pay ? "Pay " : "Receive ") + price + " Denar"));
    icon.set(
        DataComponents.LORE,
        new net.minecraft.world.item.component.ItemLore(
            List.of(Component.literal("Wallet payment — preview only"))));
    return icon;
  }

  MerchantOffers visuals(Handle npc, List<Quote> quotes) {
    var result = new MerchantOffers();
    for (var quote : quotes) {
      var nativeOffer = offer(npc, quote.index);
      ItemStack icon = currency(quote.price, quote.buy);
      ItemCost a =
          quote.buy
              ? new ItemCost(
                  icon.getItem().builtInRegistryHolder(),
                  1,
                  DataComponentPredicate.allOf(icon.getComponents()))
              : nativeOffer.getItemCostA();
      var b = quote.buy ? Optional.<ItemCost>empty() : nativeOffer.getItemCostB();
      result.add(
          new MerchantOffer(
              a,
              b,
              quote.buy ? nativeOffer.getResult().copy() : icon,
              nativeOffer.getUses(),
              nativeOffer.getMaxUses(),
              0,
              0));
    }
    return result;
  }
}
