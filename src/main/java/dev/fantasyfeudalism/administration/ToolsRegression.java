package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.RegressionTests.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Exercises consent, real packet/menu paths, visibility and public stats with connected players.
 */
final class ToolsRegression {
  private static String messages(ServerPlayer player) {
    return outgoing(player).stream()
        .filter(p -> p instanceof ClientboundSystemChatPacket)
        .map(p -> ((ClientboundSystemChatPacket) p).content().getString())
        .collect(java.util.stream.Collectors.joining("\n"));
  }

  private static int command(Administration app, String text, CommandSourceStack source)
      throws Exception {
    try {
      return app.server.getCommands().getDispatcher().execute(text, source);
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
      return 0;
    }
  }

  static void verify(GameTestHelper h, ServerPlayer sender, Administration app) throws Exception {
    var saved = app.storage.copy();
    var originalWhispers = app.whispers;
    var clock = new AtomicLong(1000);
    NeoForge.EVENT_BUS.unregister(originalWhispers);
    app.whispers = new Whispers(app, clock::get);
    NeoForge.EVENT_BUS.register(app.whispers);
    ServerPlayer recipient = player(h, "whisperTo"), watcher = player(h, "staffWatch");
    UUID staffId = watcher.getUUID();
    var ordinary = sender.createCommandSourceStack().withPermission(0);
    try {
      app.server
          .getPlayerList()
          .getOps()
          .add(
              new net.minecraft.server.players.ServerOpListEntry(
                  watcher.getGameProfile(), 4, false));
      app.refreshPermissions();
      outgoing(sender);
      outgoing(recipient);
      outgoing(watcher);
      var publicTree = clientCommands(sender, true).getChild("ffa");
      h.assertTrue(
          publicTree.getChild("stats") != null
              && publicTree.getChild("whisper") != null
              && publicTree.getChild("vanish") == null
              && publicTree.getChild("invisee") == null
              && publicTree.getChild("whispers") == null,
          "Actual ordinary client tree exposes public tools while omitting all new administration"
              + " commands");
      h.assertTrue(
          command(app, "ffa whisper whisperTo First secret", ordinary) == 1,
          "Whisper command queues a consent request");
      String prompt = messages(recipient);
      h.assertTrue(
          prompt.contains("ffaPlayer wants to send you a message")
              && prompt.contains("[Accept]")
              && prompt.contains("[Deny]")
              && !prompt.contains("First secret"),
          "Recipient sees request and buttons before message body");
      h.assertTrue(
          messages(watcher).contains("(ffaPlayer --> whisperTo) [pending consent] First secret"),
          "Administrator monitor sees an explicitly labeled pending whisper");
      var first = app.whispers.request(recipient.getUUID());
      h.assertTrue(
          command(app, "ffa whisperaccept " + first.token(), recipient.createCommandSourceStack())
              == 1,
          "Current consent button accepts message");
      h.assertTrue(messages(recipient).contains("First secret"), "Accepted message delivers once");
      h.assertTrue(
          new Storage(app.storage.directory)
              .player(recipient.getUUID())
              .whisperAllowed
              .contains(sender.getUUID().toString()),
          "Accepted sender UUID is durably remembered");
      h.assertTrue(
          command(app, "ffa whisperaccept " + first.token(), recipient.createCommandSourceStack())
              == 0,
          "Accepted button cannot be replayed");
      h.assertTrue(
          command(app, "ffa whispers off", watcher.createCommandSourceStack()) == 1,
          "Admin can switch monitoring off");
      outgoing(watcher);
      clock.addAndGet(11000);
      h.assertTrue(
          command(app, "ffa whisper whisperTo Second secret", ordinary) == 1
              && !app.whispers.waiting(recipient),
          "Trusted sender bypasses future requests");
      h.assertTrue(
          messages(recipient).contains("Second secret")
              && !messages(watcher).contains("Second secret"),
          "Monitoring off suppresses administrator copies while delivery continues");
      h.assertFalse(
          new Storage(app.storage.directory).player(staffId).whisperMonitor,
          "Monitor preference persists");
      h.assertTrue(
          command(app, "ffa whispers on", watcher.createCommandSourceStack()) == 1,
          "Admin can resume monitoring");
      h.assertTrue(
          command(app, "ffa whispers off", ordinary) == 0,
          "Ordinary players cannot use monitoring controls");
      h.assertTrue(
          command(app, "ffa whisperforget ffaPlayer", recipient.createCommandSourceStack()) == 1,
          "Recipient can revoke permanent consent");
      clock.addAndGet(11000);
      command(app, "ffa whisper whisperTo Third secret", ordinary);
      var third = app.whispers.request(recipient.getUUID());
      h.assertTrue(
          command(app, "ffa whisperaccept " + first.token(), recipient.createCommandSourceStack())
                  == 0
              && app.whispers.request(recipient.getUUID()).token().equals(third.token()),
          "Stale button cannot accept or consume replacement request");
      h.assertTrue(
          command(app, "ffa whisperdeny " + third.token(), recipient.createCommandSourceStack())
                  == 1
              && !app.storage
                  .player(recipient.getUUID())
                  .whisperAllowed
                  .contains(sender.getUUID().toString()),
          "Deny discards message without granting trust");
      clock.addAndGet(11000);
      command(app, "ffa whisper whisperTo Expiring secret", ordinary);
      var expiry = app.whispers.request(recipient.getUUID());
      clock.set(expiry.expires() + 1);
      boolean expired = false;
      try {
        app.whispers.answer(recipient, true, expiry.token());
      } catch (IllegalArgumentException expected) {
        expired = true;
      }
      h.assertTrue(
          expired && !app.whispers.waiting(recipient),
          "Expired requests never grant consent or deliver");
      h.assertTrue(
          command(app, "ffa whisper ffaPlayer Self", ordinary) == 0, "Self whisper is rejected");
      recipient.getStats().setValue(recipient, Stats.CUSTOM.get(Stats.PLAY_TIME), 6048000);
      recipient.getStats().setValue(recipient, Stats.CUSTOM.get(Stats.DEATHS), 2);
      recipient.getStats().setValue(recipient, Stats.CUSTOM.get(Stats.PLAYER_KILLS), 4);
      app.storage.update(s -> s.players.get(recipient.getUUID().toString()).balance = 42);
      outgoing(sender);
      h.assertTrue(
          command(app, "ffa stats whisperTo", ordinary) == 1,
          "Stats is available to ordinary players");
      String stats = messages(sender);
      h.assertTrue(
          stats.contains("Days played: 3.50")
              && stats.contains("Deaths: 2")
              && stats.contains("Player kills: 4")
              && stats.contains("Money: 42 Denar")
              && stats.contains("FFA:member")
              && stats.contains("Settlements:"),
          "Stats includes playtime, deaths, kills, Denar, settlements and ranks");
      h.assertTrue(
          command(app, "ffa invisee whisperTo", ordinary) == 0
              && command(app, "ffa vanish", ordinary) == 0,
          "Inventory and vanish remain administrator only");
      watcher.setGameMode(GameType.CREATIVE);
      outgoing(sender);
      outgoing(recipient);
      h.assertTrue(
          command(app, "ffa vanish", watcher.createCommandSourceStack()) == 1
              && Vanish.hidden(staffId)
              && watcher.isSpectator(),
          "Vanish persists flag and uses noninteracting spectator mode");
      var hiddenPackets = outgoing(sender);
      h.assertTrue(
          hiddenPackets.stream()
              .anyMatch(
                  p ->
                      p instanceof ClientboundPlayerInfoRemovePacket r
                          && r.profileIds().contains(staffId)),
          "Vanish immediately removes administrator from ordinary tab list");
      h.assertTrue(
          hiddenPackets.stream().anyMatch(p -> p instanceof ClientboundRemoveEntitiesPacket),
          "Vanish immediately removes tracked entity");
      outgoing(sender);
      sender.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(staffId)));
      h.assertTrue(
          outgoing(sender).isEmpty(),
          "Repeated hidden-player removal packets do not disclose presence changes");
      var newcomer = player(h, "newObserver");
      var joinedPackets = outgoing(newcomer);
      h.assertFalse(
          joinedPackets.stream()
              .anyMatch(
                  packet ->
                      packet instanceof ClientboundPlayerInfoRemovePacket info
                          && info.profileIds().contains(staffId)),
          "New observer never receives hidden UUID in unnecessary removal packet");
      app.server.getPlayerList().remove(newcomer);
      h.assertFalse(
          ordinary.getOnlinePlayerNames().contains("staffWatch"),
          "Vanished names are omitted from native name suggestions");
      h.assertFalse(
          complete(app, ordinary, "ffa stats ").contains("staffWatch"),
          "FFA target suggestions hide vanished staff");
      h.assertTrue(
          command(app, "list", ordinary) == 2 && command(app, "list uuids", ordinary) == 2,
          "Vanilla list counts/names hide staff for ordinary sources");
      h.assertTrue(
          command(app, "ffa stats staffWatch", ordinary) == 0
              && command(app, "ffa whisper staffWatch Guess", ordinary) == 0,
          "Stats and messaging report vanished target as offline");
      var selector =
          new net.minecraft.commands.arguments.selector.EntitySelectorParser(
                  new com.mojang.brigadier.StringReader("staffWatch"), true)
              .parse();
      h.assertTrue(
          selector.findPlayers(ordinary).isEmpty(),
          "Direct vanilla player selector hides vanished identity");
      h.assertTrue(
          command(app, "tell staffWatch Guess", ordinary) == 0,
          "Vanilla tell cannot resolve a vanished administrator");
      sender.setGameMode(GameType.SPECTATOR);
      h.assertFalse(
          watcher.broadcastToPlayer(sender),
          "Spectator observers also cannot track vanished staff");
      sender.setGameMode(GameType.SURVIVAL);
      var info =
          ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(watcher, recipient));
      outgoing(sender);
      sender.connection.send(info);
      var updates =
          outgoing(sender).stream()
              .filter(p -> p instanceof ClientboundPlayerInfoUpdatePacket)
              .map(p -> (ClientboundPlayerInfoUpdatePacket) p)
              .toList();
      h.assertTrue(
          updates.size() == 1
              && updates.getFirst().entries().size() == 1
              && updates.getFirst().entries().getFirst().profileId().equals(recipient.getUUID())
              && info.entries().size() == 2,
          "Per-recipient tab filtering preserves shared packet data");
      outgoing(sender);
      sender.sendSystemMessage(
          Component.translatable("multiplayer.player.joined", watcher.getDisplayName()));
      h.assertTrue(messages(sender).isEmpty(), "Join announcements cannot reveal vanished staff");
      var status =
          new net.minecraft.network.protocol.status.ServerStatus(
              Component.literal("test"),
              Optional.of(
                  new net.minecraft.network.protocol.status.ServerStatus.Players(
                      20,
                      3,
                      List.of(
                          sender.getGameProfile(),
                          recipient.getGameProfile(),
                          watcher.getGameProfile()))),
              Optional.empty(),
              Optional.empty(),
              false,
              false);
      var publicStatus = Vanish.publicStatus(status);
      h.assertTrue(
          publicStatus.players().orElseThrow().online() == 2
              && publicStatus.players().orElseThrow().sample().stream()
                  .noneMatch(p -> p.getId().equals(staffId)),
          "Public status count and sample omit vanished administrator");
      statusAndQuery(h, status);
      inventory(h, app, watcher, recipient);
      var profile = watcher.getGameProfile();
      outgoing(sender);
      app.server.getPlayerList().remove(watcher);
      watcher = player(h, profile);
      h.assertTrue(
          Vanish.hidden(staffId) && watcher.isSpectator(),
          "Vanish survives reconnect with spectator restoration");
      var reconnect = outgoing(sender);
      h.assertFalse(
          reconnect.stream()
              .anyMatch(
                  p ->
                      p instanceof ClientboundPlayerInfoUpdatePacket update
                          && update.entries().stream()
                              .anyMatch(e -> e.profileId().equals(staffId))),
          "Reconnect never leaks vanished tab entry");
      h.assertFalse(
          reconnect.stream()
              .anyMatch(
                  p ->
                      p instanceof ClientboundSystemChatPacket chat
                          && chat.content().getString().contains("staffWatch")),
          "Reconnect never leaks vanished join announcement");
      h.assertTrue(
          command(app, "ffa vanish", watcher.createCommandSourceStack()) == 1
              && !Vanish.hidden(staffId)
              && watcher.gameMode.getGameModeForPlayer() == GameType.CREATIVE,
          "Leaving vanish restores saved game mode after reconnect");
      command(app, "ffa vanish", watcher.createCommandSourceStack());
      app.server.getPlayerList().deop(watcher.getGameProfile());
      app.refreshPermissions();
      h.assertTrue(
          !Vanish.hidden(staffId) && watcher.gameMode.getGameModeForPlayer() == GameType.SURVIVAL,
          "Revoking admin rights removes vanish and does not restore privileged Creative mode");
    } finally {
      var current = app.server.getPlayerList().getPlayer(staffId);
      if (current != null && Vanish.hidden(staffId)) {
        app.server
            .getPlayerList()
            .getOps()
            .add(
                new net.minecraft.server.players.ServerOpListEntry(
                    current.getGameProfile(), 4, false));
        app.vanish.toggle(current);
      }
      if (current != null) {
        app.server.getPlayerList().deop(current.getGameProfile());
        app.server.getPlayerList().remove(current);
      }
      app.server.getPlayerList().remove(recipient);
      NeoForge.EVENT_BUS.unregister(app.whispers);
      app.whispers = originalWhispers;
      NeoForge.EVENT_BUS.register(originalWhispers);
      app.storage.commit(saved);
      app.refreshPermissions();
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void statusAndQuery(
      GameTestHelper h, net.minecraft.network.protocol.status.ServerStatus status)
      throws Exception {
    var connection =
        new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
    var channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
    var listener =
        new net.minecraft.server.network.ServerStatusPacketListenerImpl(
            status, connection, "STALE JSON WITH staffWatch");
    listener.handleStatusRequest(
        net.minecraft.network.protocol.status.ServerboundStatusRequestPacket.INSTANCE);
    channel.runPendingTasks();
    channel.flushOutbound();
    Object packet = channel.readOutbound();
    h.assertTrue(
        packet
                instanceof
                net.minecraft.network.protocol.status.ClientboundStatusResponsePacket response
            && response.status().players().orElseThrow().online() == 2
            && response.cachedStatus() == null,
        "Real status listener filters public counts and bypasses stale unfiltered JSON cache");
    channel.finishAndReleaseAll();
    Class<?> info = Class.forName("net.minecraft.server.ServerInfo");
    Object infoServer =
        java.lang.reflect.Proxy.newProxyInstance(
            info.getClassLoader(),
            new Class<?>[] {info},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getPlayerCount" -> 3;
                  case "getMaxPlayers" -> 20;
                  case "getMotd" -> "test";
                  case "getServerVersion" -> "1.21.1";
                  default -> null;
                });
    Class<?> legacy = Class.forName("net.minecraft.server.network.LegacyQueryHandler");
    var legacy0 = legacy.getDeclaredMethod("createVersion0Response", info);
    legacy0.setAccessible(true);
    var legacy1 = legacy.getDeclaredMethod("createVersion1Response", info);
    legacy1.setAccessible(true);
    h.assertTrue(
        ((String) legacy0.invoke(null, infoServer)).equals("test§2§20")
            && ((String) legacy1.invoke(null, infoServer))
                .split(Character.toString(0), -1)[4].equals("2"),
        "Both legacy ping formats filter public player count");
    Class<?> api = Class.forName("net.minecraft.server.ServerInterface");
    Object server =
        java.lang.reflect.Proxy.newProxyInstance(
            api.getClassLoader(),
            new Class<?>[] {api},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getServerIp" -> "127.0.0.1";
                  case "getServerPort" -> 25565;
                  case "getServerName", "getLevelIdName", "getPluginNames" -> "FFA test";
                  case "getMaxPlayers" -> 20;
                  case "getServerVersion" -> "1.21.1";
                  case "getPlayerCount" -> 3;
                  case "getPlayerNames" -> new String[] {"ffaPlayer", "whisperTo", "staffWatch"};
                  default -> null;
                });
    Class<?> query = Class.forName("net.minecraft.server.rcon.thread.QueryThreadGs4");
    var constructor = query.getDeclaredConstructor(api, int.class);
    constructor.setAccessible(true);
    Object instance = constructor.newInstance(server, 0);
    var request =
        new java.net.DatagramPacket(
            new byte[16], 16, java.net.InetAddress.getLoopbackAddress(), 12345);
    Class<?> challenge =
        Class.forName("net.minecraft.server.rcon.thread.QueryThreadGs4$RequestChallenge");
    var challengeConstructor = challenge.getDeclaredConstructor(java.net.DatagramPacket.class);
    challengeConstructor.setAccessible(true);
    var challenges = query.getDeclaredField("validChallenges");
    challenges.setAccessible(true);
    ((Map) challenges.get(instance))
        .put(request.getSocketAddress(), challengeConstructor.newInstance(request));
    var build = query.getDeclaredMethod("buildRuleResponse", java.net.DatagramPacket.class);
    build.setAccessible(true);
    byte[] data = (byte[]) build.invoke(instance, request);
    String result = new String(data, java.nio.charset.StandardCharsets.ISO_8859_1);
    h.assertTrue(
        result.contains("numplayers" + "\0" + "2" + "\0") && !result.contains("staffWatch"),
        "Actual GS4 full-query response filters both count and player names without opening"
            + " sockets");
  }

  private static void inventory(
      GameTestHelper h, Administration app, ServerPlayer admin, ServerPlayer target)
      throws Exception {
    target.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 10));
    h.assertTrue(
        command(app, "ffa invisee whisperTo", admin.createCommandSourceStack()) == 1
            && admin.containerMenu instanceof InspectionMenu,
        "Administrator opens a native live inventory screen while vanished");
    var menu = (InspectionMenu) admin.containerMenu;
    h.assertTrue(
        menu.getContainer().getItem(0).getCount() == 10 && menu.slots.size() == 90,
        "Inspection exposes target inventory and viewer slots");
    admin.connection.handleContainerClick(
        new ServerboundContainerClickPacket(
            menu.containerId,
            menu.getStateId(),
            0,
            0,
            ClickType.PICKUP,
            ItemStack.EMPTY,
            new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>()));
    h.assertTrue(
        target.getInventory().getItem(0).isEmpty() && menu.getCarried().getCount() == 10,
        "Actual click packet can edit target inventory even in authorized vanish spectator mode");
    admin.connection.handleContainerClick(
        new ServerboundContainerClickPacket(
            menu.containerId,
            menu.getStateId(),
            81,
            0,
            ClickType.PICKUP,
            ItemStack.EMPTY,
            new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>()));
    h.assertTrue(
        admin.getInventory().countItem(Items.DIAMOND) == 10 && menu.getCarried().isEmpty(),
        "Inventory edits transfer items without duplicating target stacks");
    h.assertFalse(
        menu.slots.get(41).mayPlace(new ItemStack(Items.STONE)),
        "Padding slots cannot accept items");
    target.getInventory().setItem(39, new ItemStack(Items.DIAMOND_HELMET));
    h.assertTrue(
        menu.getContainer().getItem(39).is(Items.DIAMOND_HELMET), "Armor updates are visible live");
    var world =
        app.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR);
    var saved =
        net.minecraft.nbt.NbtIo.readCompressed(
            world.resolve(target.getUUID() + ".dat"),
            net.minecraft.nbt.NbtAccounter.unlimitedHeap());
    var inventory = new net.minecraft.world.entity.player.Inventory(target);
    inventory.load(saved.getList("Inventory", 10));
    h.assertTrue(
        inventory.countItem(Items.DIAMOND) == 0,
        "Target inventory edits are persisted in vanilla player data");
    outgoing(target);
    app.economy.trade(target, "test", 1, true);
    h.assertTrue(
        admin.containerMenu == admin.inventoryMenu
            && target.getInventory().countItem(Items.STONE) >= 1,
        "Item trading closes staff inspection before preparing a financial transaction");
    h.assertFalse(
        messages(target).toLowerCase(Locale.ROOT).contains("inspection"),
        "Ordinary player receives no inspection-presence error");
  }
}
