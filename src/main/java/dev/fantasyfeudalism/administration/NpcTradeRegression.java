package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;
import static dev.fantasyfeudalism.administration.RegressionTests.*;

import java.util.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.world.entity.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.phys.Vec3;

final class NpcTradeRegression {
  @SuppressWarnings({"unchecked", "rawtypes"})
  static void verify(GameTestHelper h, Administration app) throws Exception {
    if (!net.neoforged.fml.ModList.get().isLoaded("easy_npc")) {
      h.assertFalse(
          app.npcTrades.available(), "Optional trading adapter loads without Easy NPC installed");
      return;
    }
    h.assertTrue(
        app.npcTrades.available(),
        "Exact uploaded Easy NPC version has a working reflection adapter");
    var state = app.storage.copy();
    var regions = app.regions.copy();
    var player = player(h, "denarCustomer");
    var type =
        BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
            .filter(
                e ->
                    e.getKey().location().getNamespace().equals("easy_npc")
                        && e.getKey().location().getPath().contains("human"))
            .findFirst()
            .orElseThrow()
            .getValue();
    Entity entity = type.create(h.getLevel());
    try {
      app.regions.edit(List.of(), app.server, app.settings, app.compat);
      player.setPos(h.absoluteVec(new Vec3(10.5, 1, 10.5)));
      entity.setPos(h.absoluteVec(new Vec3(12.5, 1, 10.5)));
      h.getLevel().addFreshEntity(entity);
      ((Mob) entity).setNoAi(true);
      Class<?> trading =
          Class.forName("de.markusbordihn.easynpc.entity.easynpc.data.TradingDataCapable");
      Object data = trading.getMethod("getTradingDataSet").invoke(entity);
      Class enumType = Class.forName("de.markusbordihn.easynpc.data.trading.TradingType");
      data.getClass().getMethod("setType", enumType).invoke(data, Enum.valueOf(enumType, "CUSTOM"));
      var product = new ItemStack(Items.DIAMOND, 2);
      product.set(DataComponents.CUSTOM_NAME, Component.literal("Engraved seal"));
      var offers = new MerchantOffers();
      offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), product, 2, 0, 0));
      var exact =
          new ItemCost(Items.DIAMOND, 1)
              .withComponents(
                  b -> b.expect(DataComponents.CUSTOM_NAME, Component.literal("Engraved seal")));
      offers.add(
          new MerchantOffer(
              exact,
              Optional.of(new ItemCost(Items.DIAMOND, 1)),
              new ItemStack(Items.EMERALD),
              10,
              0,
              0));
      trading.getMethod("setTradingOffers", MerchantOffers.class).invoke(entity, offers);
      Merchant merchant = (Merchant) trading.getMethod("getMerchant").invoke(entity);
      var npc = app.npcTrades.handle(merchant);
      h.assertTrue(
          npc != null && npc.entity() == entity, "SafeMerchantData resolves to the real NPC UUID");
      h.assertTrue(
          clientCommands(player, true).getChild("ffa").getChild("npctrade") == null,
          "NPC price-management commands are hidden from normal players");
      player.setYRot(-90);
      player.setXRot(0);
      h.assertTrue(
          app.npcTrades.looking(player).entity() == entity,
          "NPC look targeting resolves the selected real entity");
      open(player, merchant);
      h.assertTrue(
          player.containerMenu instanceof MerchantMenu
              && !(player.containerMenu instanceof NpcCurrencyMenu),
          "Unconfigured NPC retains its original item trading menu");
      player.closeContainer();
      h.assertTrue(
          app.server
                  .getCommands()
                  .getDispatcher()
                  .execute(
                      "ffa npctrade price 1 buy 20",
                      player.createCommandSourceStack().withPermission(4))
              == 1,
          "Native admin pricing command selects and persists an offer");
      app.npcTrades.price(npc, 2, false, 7);
      String key = entity.getUUID().toString(), id = player.getUUID().toString();
      app.storage.update(s -> s.players.get(id).balance = 100);
      player.getInventory().clearContent();
      outgoing(player);
      open(player, merchant);
      h.assertTrue(
          player.containerMenu instanceof NpcCurrencyMenu,
          "Native Easy NPC menu is adapted without a client mod dependency");
      var packets = outgoing(player);
      h.assertTrue(
          packets.stream()
              .anyMatch(
                  p ->
                      p instanceof ClientboundMerchantOffersPacket m
                          && m.getOffers()
                              .getFirst()
                              .getCostA()
                              .getHoverName()
                              .getString()
                              .equals("Pay 20 Denar")),
          "Native offer packet contains the exact wallet price");
      var menu = (NpcCurrencyMenu) player.containerMenu;
      for (var click : ClickType.values())
        if (click != ClickType.PICKUP && click != ClickType.QUICK_MOVE)
          menu.clicked(2, 0, click, player);
      menu.clicked(0, 0, ClickType.PICKUP, player);
      player.closeContainer();
      h.assertTrue(
          player.getInventory().isEmpty() && player.inventoryMenu.getCarried().isEmpty(),
          "Closing, dragging, hotkeys and preview-input clicks cannot extract virtual currency or"
              + " items");
      app.storage.update(s -> s.players.get(id).balance = 0);
      open(player, merchant);
      menu = (NpcCurrencyMenu) player.containerMenu;
      menu.clicked(2, 0, ClickType.PICKUP, player);
      h.assertTrue(
          player.getInventory().isEmpty() && app.storage.state().trades.isEmpty(),
          "Insufficient funds changes neither items nor journal");
      player.closeContainer();
      app.storage.update(s -> s.players.get(id).balance = 100);
      open(player, merchant);
      menu = (NpcCurrencyMenu) player.containerMenu;
      menu.clicked(2, 0, ClickType.PICKUP, player);
      h.assertTrue(
          app.storage.player(player.getUUID()).balance == 80
              && count(player, product) == 2
              && app.storage.state().npcShops.get(key).offers.get(1).used == 1,
          "Buy exchanges full component-preserving product for wallet Denar and commits stock");
      menu.clicked(2, 0, ClickType.PICKUP, player);
      h.assertTrue(
          app.storage.player(player.getUUID()).balance == 80 && count(player, product) == 2,
          "Replaying a stale closed menu cannot buy again");
      open(player, merchant);
      menu = (NpcCurrencyMenu) player.containerMenu;
      menu.setSelectionHint(1);
      menu.clicked(2, 0, ClickType.PICKUP, player);
      h.assertTrue(
          app.storage.player(player.getUUID()).balance == 87 && player.getInventory().isEmpty(),
          "Sell consumes both matching costs and credits Denar without giving the old barter"
              + " output");
      open(player, merchant);
      menu = (NpcCurrencyMenu) player.containerMenu;
      menu.clicked(2, 0, ClickType.QUICK_MOVE, player);
      h.assertTrue(
          app.storage.player(player.getUUID()).balance == 67 && count(player, product) == 2,
          "Shift-click executes exactly one wallet transaction");
      open(player, merchant);
      menu = (NpcCurrencyMenu) player.containerMenu;
      menu.clicked(2, 0, ClickType.PICKUP, player);
      player.closeContainer();
      h.assertTrue(
          app.storage.player(player.getUUID()).balance == 67
              && app.storage.state().npcShops.get(key).offers.get(1).used == 2,
          "Native maximum-use stock is enforced");
      trading.getMethod("resetTradingOffers").invoke(entity);
      open(player, merchant);
      player.closeContainer();
      h.assertTrue(
          app.storage.state().npcShops.get(key).offers.get(1).used == 0,
          "Native NPC restock resets durable Denar stock");
      for (int slot = 0; slot < 36; slot++)
        player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
      open(player, merchant);
      menu = (NpcCurrencyMenu) player.containerMenu;
      menu.clicked(2, 0, ClickType.PICKUP, player);
      player.closeContainer();
      h.assertTrue(
          app.storage.player(player.getUUID()).balance == 67
              && app.storage.state().npcShops.get(key).offers.get(1).used == 0,
          "Full inventory does not charge or consume stock");
      player.getInventory().clearContent();
      // Simulate the durable prepared phase after inventory save, before wallet commit.
      var pending = new PendingTrade();
      pending.id = UUID.randomUUID().toString();
      pending.player = id;
      pending.npc = key;
      pending.npcOffer = 1;
      pending.before = player.getInventory().save(new ListTag()).toString();
      player.getInventory().setItem(0, product.copy());
      pending.after = player.getInventory().save(new ListTag()).toString();
      pending.oldBalance = 67;
      pending.newBalance = 47;
      app.storage.update(s -> s.trades.put(id, pending));
      try {
        app.npcTrades.clear(npc, null);
        h.fail("Pending NPC binding must be locked");
      } catch (IllegalArgumentException expected) {
      }
      app.economy.recover(player);
      app.economy.recover(player);
      h.assertTrue(
          app.storage.player(player.getUUID()).balance == 47
              && app.storage.state().npcShops.get(key).offers.get(1).used == 1
              && app.storage.state().trades.isEmpty(),
          "Interrupted trade recovers money and stock once without replaying NPC scripts");
      open(player, merchant);
      menu = (NpcCurrencyMenu) player.containerMenu;
      var changed = merchant.getOffers().copy();
      changed.getFirst().getResult().setCount(3);
      trading.getMethod("setTradingOffers", MerchantOffers.class).invoke(entity, changed);
      menu.clicked(2, 0, ClickType.PICKUP, player);
      player.closeContainer();
      h.assertTrue(
          app.storage.player(player.getUUID()).balance == 47,
          "Changed underlying recipe cannot spend a stale quote");
      app.npcTrades.clear(npc, null);
      open(player, merchant);
      h.assertTrue(
          !(player.containerMenu instanceof NpcCurrencyMenu),
          "Removing all prices restores original Easy NPC trading");
      player.closeContainer();
    } finally {
      player.closeContainer();
      entity.discard();
      app.server.getPlayerList().remove(player);
      app.storage.commit(state);
      app.regions.edit(regions, app.server, app.settings, app.compat);
    }
  }

  private static void open(net.minecraft.server.level.ServerPlayer p, Merchant merchant) {
    merchant.setTradingPlayer(p);
    merchant.openTradingScreen(p, Component.literal("Denar merchant"), 1);
  }

  private static int count(net.minecraft.server.level.ServerPlayer p, ItemStack prototype) {
    int total = 0;
    for (int i = 0; i < 36; i++)
      if (ItemStack.isSameItemSameComponents(p.getInventory().getItem(i), prototype))
        total += p.getInventory().getItem(i).getCount();
    return total;
  }
}
