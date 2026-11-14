package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;

import dev.fantasyfeudalism.administration.mixin.PlayerListAccess;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.storage.LevelResource;

/** All operations run on the server thread. Durable prepare -> player save -> balance commit. */
public final class Economy {
  private final Administration app;
  private final java.util.function.LongSupplier clock;

  public Economy(Administration app) {
    this(app, System::currentTimeMillis);
  }

  Economy(Administration app, java.util.function.LongSupplier clock) {
    this.app = app;
    this.clock = clock;
  }

  public String shopId(String name) {
    var matches =
        app.storage.state().shops.keySet().stream()
            .filter(id -> id.equalsIgnoreCase(name))
            .limit(2)
            .toList();
    if (matches.size() > 1)
      throw new IllegalArgumentException("Ambiguous shop name; contact an administrator.");
    return matches.isEmpty() ? name : matches.getFirst();
  }

  private String quotaKey(Shop shop, UUID player) {
    return (shop.limitScope.equals("player") ? player.toString() : "*")
        + "|"
        + (shop.limitReset.equals("daily")
            ? Long.toString(Math.floorDiv(clock.getAsLong(), 86400000L))
            : "manual");
  }

  public boolean pendingShop(String id) {
    return app.storage.state().trades.values().stream()
        .anyMatch(
            t ->
                (t.npc == null || t.npc.isEmpty())
                    && (t.shop == null || t.shop.isEmpty() || id.equals(t.shop)));
  }

  public long remaining(String id, UUID player, boolean buy) {
    id = shopId(id);
    var shop = app.storage.state().shops.get(id);
    if (shop == null) throw new IllegalArgumentException("Unknown shop.");
    long limit = buy ? shop.buyLimit : shop.sellLimit;
    if (limit < 0) return Long.MAX_VALUE;
    String key = quotaKey(shop, player);
    var usage = shop.usage.get(key);
    long used = usage == null ? 0 : buy ? usage.bought : usage.sold;
    for (var trade : app.storage.state().trades.values())
      if (id.equals(trade.shop) && key.equals(trade.quotaKey) && buy == trade.buy)
        used = Math.addExact(used, trade.quantity);
    return Math.max(0, limit - used);
  }

  public void limits(String name, String scope, String reset, long buy, long sell)
      throws Exception {
    String id = shopId(name);
    if (!app.storage.state().shops.containsKey(id))
      throw new IllegalArgumentException("Unknown shop.");
    if (!Set.of("shared", "player").contains(scope)
        || !Set.of("manual", "daily").contains(reset)
        || buy < -1
        || sell < -1)
      throw new IllegalArgumentException(
          "Choose shared/player, manual/daily, and limits of -1 or more.");
    if (pendingShop(id))
      throw new IllegalArgumentException("Recover this shop's pending transactions first.");
    app.storage.update(
        s -> {
          var shop = s.shops.get(id);
          boolean policyChanged = !shop.limitScope.equals(scope) || !shop.limitReset.equals(reset);
          shop.limitScope = scope;
          shop.limitReset = reset;
          shop.buyLimit = buy;
          shop.sellLimit = sell;
          if (policyChanged) shop.usage.clear();
        });
  }

  public void resetLimits(String name, UUID player) throws Exception {
    String id = shopId(name);
    var shop = app.storage.state().shops.get(id);
    if (shop == null) throw new IllegalArgumentException("Unknown shop.");
    if (pendingShop(id))
      throw new IllegalArgumentException("Recover this shop's pending transactions first.");
    if (player != null && !shop.limitScope.equals("player"))
      throw new IllegalArgumentException("This shop uses a shared limit; omit the player.");
    app.storage.update(
        s -> {
          if (player == null) s.shops.get(id).usage.clear();
          else s.shops.get(id).usage.keySet().removeIf(k -> k.startsWith(player + "|"));
        });
  }

  private void enabled() {
    if (!app.server.isSameThread())
      throw new IllegalStateException("Economy operations must run on the server thread");
    if (!app.settings.economy) throw new IllegalArgumentException("Economy is disabled.");
  }

  private void available(UUID id) {
    if (app.storage.state().trades.containsKey(id.toString()))
      throw new IllegalArgumentException(
          "Account has a pending transaction; reconnect for recovery.");
  }

  public void pay(ServerPlayer from, ServerPlayer to, long amount) throws Exception {
    enabled();
    available(from.getUUID());
    available(to.getUUID());
    if (from == to || amount <= 0 || amount > app.settings.maxTransfer)
      throw new IllegalArgumentException("Invalid transfer amount or recipient.");
    app.storage.update(
        s -> {
          var a = s.players.get(from.getUUID().toString());
          var b = s.players.get(to.getUUID().toString());
          if (a.balance < amount || b.balance > app.settings.maxBalance - amount)
            throw new IllegalArgumentException("Insufficient balance or recipient balance limit.");
          a.balance -= amount;
          b.balance += amount;
        });
    app.storage.audit(
        app.settings,
        from.getUUID().toString(),
        "pay " + amount + " to " + to.getUUID(),
        Teleports.point(from).describe(),
        "SUCCESS");
  }

  public void set(ServerPlayer admin, UUID target, long amount) throws Exception {
    enabled();
    available(target);
    if (amount < 0 || amount > app.settings.maxBalance)
      throw new IllegalArgumentException("Amount outside configured range.");
    long previous = app.storage.player(target).balance;
    app.storage.update(s -> s.players.get(target.toString()).balance = amount);
    app.storage.audit(
        app.settings,
        admin.getUUID().toString(),
        "balance " + target + " " + previous + " -> " + amount,
        Teleports.point(admin).describe(),
        "SUCCESS");
  }

  public void trade(ServerPlayer p, String shopId, int quantity, boolean buy) throws Exception {
    enabled();
    if (app.moderation != null && app.moderation.confined(p))
      throw new IllegalArgumentException("You cannot trade while jailed.");
    InspectionMenu.closeFor(app, p);
    available(p.getUUID());
    if (quantity < 1 || quantity > 2304)
      throw new IllegalArgumentException("Quantity must be 1..2304.");
    shopId = shopId(shopId);
    Shop shop = app.storage.state().shops.get(shopId);
    if (shop == null) throw new IllegalArgumentException("Unknown shop.");
    long allowance = remaining(shopId, p.getUUID(), buy);
    if (quantity > allowance)
      throw new IllegalArgumentException(
          "Only "
              + allowance
              + " items remain in "
              + (shop.limitScope.equals("player") ? "your" : "the shop's")
              + " "
              + (buy ? "buy" : "sell")
              + " allowance.");
    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(shop.item));
    if (item == Items.AIR) throw new IllegalArgumentException("Shop item is unavailable.");
    long price = buy ? shop.buy : shop.sell;
    if (price < 0) throw new IllegalArgumentException("This trade direction is disabled.");
    long total = Math.multiplyExact(price, quantity),
        balance = app.storage.player(p.getUUID()).balance;
    if (buy && total > balance || !buy && total > app.settings.maxBalance - balance)
      throw new IllegalArgumentException("Insufficient balance or maximum balance exceeded.");
    Inventory simulated = new Inventory(p);
    simulated.load(p.getInventory().save(new ListTag()));
    ItemStack prototype = new ItemStack(item);
    int remaining = quantity;
    if (buy) {
      for (int i = 0; i < 36 && remaining > 0; i++) {
        ItemStack slot = simulated.getItem(i);
        if (slot.isEmpty() || ItemStack.isSameItemSameComponents(slot, prototype)) {
          int capacity = prototype.getMaxStackSize() - (slot.isEmpty() ? 0 : slot.getCount()),
              n = Math.min(capacity, remaining);
          if (n > 0) {
            if (slot.isEmpty()) simulated.setItem(i, prototype.copyWithCount(n));
            else slot.grow(n);
            remaining -= n;
          }
        }
      }
    } else
      for (int i = 0; i < 36 && remaining > 0; i++) {
        ItemStack slot = simulated.getItem(i);
        if (ItemStack.isSameItemSameComponents(slot, prototype)) {
          int n = Math.min(slot.getCount(), remaining);
          slot.shrink(n);
          remaining -= n;
        }
      }
    if (remaining != 0)
      throw new IllegalArgumentException(
          buy ? "Not enough inventory space." : "Not enough matching unmodified items.");
    PendingTrade trade = new PendingTrade();
    trade.id = UUID.randomUUID().toString();
    trade.shop = shopId;
    trade.quotaKey = quotaKey(shop, p.getUUID());
    trade.quantity = quantity;
    trade.buy = buy;
    trade.player = p.getUUID().toString();
    trade.before = p.getInventory().save(new ListTag()).toString();
    trade.after = simulated.save(new ListTag()).toString();
    trade.oldBalance = balance;
    trade.newBalance = buy ? balance - total : balance + total;
    commitInventory(p, simulated, trade);
    app.storage.audit(
        app.settings,
        p.getUUID().toString(),
        (buy ? "buy " : "sell ") + shopId + " quantity=" + quantity + " transaction=" + trade.id,
        Teleports.point(p).describe(),
        "SUCCESS");
  }

  private void commitInventory(ServerPlayer p, Inventory simulated, PendingTrade trade)
      throws Exception {
    app.storage.update(s -> s.trades.put(trade.player, trade));
    try {
      p.closeContainer();
      p.getInventory().load(simulated.save(new ListTag()));
      p.containerMenu.broadcastChanges();
      ((PlayerListAccess) app.server.getPlayerList()).ffa$save(p);
      Path file =
          app.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID() + ".dat");
      CompoundTag saved = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
      if (!saved.getList("Inventory", 10).equals(p.getInventory().save(new ListTag())))
        throw new IllegalStateException("Player inventory save did not verify");
      finish(trade, true);
    } catch (Exception e) {
      p.connection.disconnect(
          net.minecraft.network.chat.Component.literal(
              "Shop save interrupted. Reconnect to recover your pending transaction."));
      throw e;
    }
  }

  void npcTrade(ServerPlayer player, String npc, int index, NpcPrice price, MerchantOffer offer)
      throws Exception {
    enabled();
    available(player.getUUID());
    if (app.rules.pending(player) || app.moderation.confined(player))
      throw new IllegalArgumentException("You cannot trade right now.");
    if (!player.containerMenu.getCarried().isEmpty())
      throw new IllegalArgumentException("Put the item on your cursor away before trading.");
    InspectionMenu.closeFor(app, player);
    long balance = app.storage.player(player.getUUID()).balance;
    if (price.buy && balance < price.price
        || !price.buy && price.price > app.settings.maxBalance - balance)
      throw new IllegalArgumentException("Insufficient balance or maximum balance exceeded.");
    var inventory = new Inventory(player);
    inventory.load(player.getInventory().save(new ListTag()));
    if (price.buy) {
      ItemStack product = offer.getResult();
      int left = product.getCount();
      if (product.isEmpty()) throw new IllegalArgumentException("The NPC's item is unavailable.");
      for (int slot = 0; slot < 36 && left > 0; slot++) {
        var stack = inventory.getItem(slot);
        if (stack.isEmpty() || ItemStack.isSameItemSameComponents(stack, product)) {
          int count =
              Math.min(
                  left,
                  Math.max(
                      0, product.getMaxStackSize() - (stack.isEmpty() ? 0 : stack.getCount())));
          if (count > 0) {
            if (stack.isEmpty()) inventory.setItem(slot, product.copyWithCount(count));
            else stack.grow(count);
            left -= count;
          }
        }
      }
      if (left > 0) throw new IllegalArgumentException("Not enough inventory space.");
    } else {
      var costs = new ArrayList<ItemCost>();
      costs.add(offer.getItemCostA());
      offer.getItemCostB().ifPresent(costs::add);
      int[] left = costs.stream().mapToInt(ItemCost::count).toArray();
      // Consume exclusive matches before shared matches, preserving both component predicates.
      for (int phase = 0; phase < 2; phase++)
        for (int i = 0; i < costs.size(); i++)
          for (int slot = 0; slot < 36 && left[i] > 0; slot++) {
            var stack = inventory.getItem(slot);
            if (stack.isEmpty() || !costs.get(i).test(stack)) continue;
            boolean shared = costs.size() == 2 && costs.get(1 - i).test(stack);
            if (phase == 0 && shared) continue;
            int count = Math.min(left[i], stack.getCount());
            stack.shrink(count);
            left[i] -= count;
          }
      if (Arrays.stream(left).anyMatch(n -> n > 0))
        throw new IllegalArgumentException("Not enough matching items.");
    }
    var trade = new PendingTrade();
    trade.id = UUID.randomUUID().toString();
    trade.player = player.getUUID().toString();
    trade.npc = npc;
    trade.npcOffer = index;
    trade.npcBaseUses = offer.getUses();
    trade.buy = price.buy;
    trade.before = player.getInventory().save(new ListTag()).toString();
    trade.after = inventory.save(new ListTag()).toString();
    trade.oldBalance = balance;
    trade.newBalance = price.buy ? balance - price.price : balance + price.price;
    commitInventory(player, inventory, trade);
    app.storage.audit(
        app.settings,
        trade.player,
        "NPC trade " + npc + " offer=" + index + " transaction=" + trade.id,
        Teleports.point(player).describe(),
        "SUCCESS");
  }

  private void finish(PendingTrade trade, boolean applied) throws Exception {
    app.storage.update(
        s -> {
          PlayerData data = s.players.get(trade.player);
          if (data.balance != trade.oldBalance)
            throw new IllegalArgumentException(
                "Balance changed during prepared transaction; recovery required");
          if (applied) {
            data.balance = trade.newBalance;
            if (trade.npc != null && !trade.npc.isEmpty()) {
              var shop = s.npcShops.get(trade.npc);
              var price = shop == null ? null : shop.offers.get(trade.npcOffer);
              if (price == null)
                throw new IllegalArgumentException(
                    "Prepared NPC offer is missing; administrator recovery required.");
              price.used = Math.addExact(Math.max(price.used, trade.npcBaseUses), 1);
            }
            if (trade.shop != null && !trade.shop.isEmpty()) {
              var shop = s.shops.get(trade.shop);
              if (shop == null)
                throw new IllegalArgumentException(
                    "Prepared shop is missing; administrator recovery required.");
              var usage = shop.usage.computeIfAbsent(trade.quotaKey, k -> new ShopUsage());
              if (trade.buy) usage.bought = Math.addExact(usage.bought, trade.quantity);
              else usage.sold = Math.addExact(usage.sold, trade.quantity);
              if (shop.limitReset.equals("daily")) {
                String current = "|" + Math.floorDiv(clock.getAsLong(), 86400000L);
                shop.usage
                    .keySet()
                    .removeIf(
                        k ->
                            !k.endsWith(current)
                                && s.trades.values().stream()
                                    .noneMatch(
                                        t -> trade.shop.equals(t.shop) && k.equals(t.quotaKey)));
              }
            }
          }
          s.trades.remove(trade.player);
        });
  }

  public void recover(ServerPlayer p) throws Exception {
    PendingTrade trade = app.storage.state().trades.get(p.getUUID().toString());
    if (trade == null) return;
    String actual = p.getInventory().save(new ListTag()).toString();
    if (actual.equals(trade.after)) finish(trade, true);
    else if (actual.equals(trade.before)) finish(trade, false);
    else
      throw new IllegalStateException(
          "Inventory differs from both transaction snapshots. Offline administrator recovery"
              + " required; account locked.");
  }
}
