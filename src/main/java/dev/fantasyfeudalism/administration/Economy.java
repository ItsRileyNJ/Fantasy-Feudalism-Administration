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
import net.minecraft.world.level.storage.LevelResource;

/** All operations run on the server thread. Durable prepare -> player save -> balance commit. */
public final class Economy {
  private final Administration app;

  public Economy(Administration app) {
    this.app = app;
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
    Shop shop = app.storage.state().shops.get(shopId);
    if (shop == null) throw new IllegalArgumentException("Unknown shop.");
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
    trade.player = p.getUUID().toString();
    trade.before = p.getInventory().save(new ListTag()).toString();
    trade.after = simulated.save(new ListTag()).toString();
    trade.oldBalance = balance;
    trade.newBalance = buy ? balance - total : balance + total;
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
    app.storage.audit(
        app.settings,
        p.getUUID().toString(),
        (buy ? "buy " : "sell ") + shopId + " quantity=" + quantity + " transaction=" + trade.id,
        Teleports.point(p).describe(),
        "SUCCESS");
  }

  private void finish(PendingTrade trade, boolean applied) throws Exception {
    app.storage.update(
        s -> {
          PlayerData data = s.players.get(trade.player);
          if (data.balance != trade.oldBalance)
            throw new IllegalArgumentException(
                "Balance changed during prepared transaction; recovery required");
          if (applied) data.balance = trade.newBalance;
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
