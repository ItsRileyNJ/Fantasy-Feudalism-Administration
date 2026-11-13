package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;
import static dev.fantasyfeudalism.administration.RegressionTests.*;

import io.netty.buffer.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.common.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

final class CommerceRegression {
  private static int cmd(Administration app, ServerPlayer p, String command) throws Exception {
    try {
      return app.server
          .getCommands()
          .getDispatcher()
          .execute(command, p.createCommandSourceStack());
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException denied) {
      return 0;
    }
  }

  private static String messages(ServerPlayer p) {
    return outgoing(p).stream()
        .filter(x -> x instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket)
        .map(
            x ->
                ((net.minecraft.network.protocol.game.ClientboundSystemChatPacket) x)
                    .content()
                    .getString())
        .collect(java.util.stream.Collectors.joining("\n"));
  }

  static void verify(GameTestHelper h, ServerPlayer p, Administration app) throws Exception {
    var state = app.storage.copy();
    var inventory = p.getInventory().save(new ListTag());
    var oldEconomy = app.economy;
    var oldPayments = app.payments;
    var clock = new AtomicLong(1700000000000L);
    var staff = player(h, "moneyAdmin");
    var other = player(h, "moneyTo");
    NeoForge.EVENT_BUS.unregister(oldPayments);
    app.payments = new Payments(app, clock::get);
    NeoForge.EVENT_BUS.register(app.payments);
    app.economy = new Economy(app, clock::get);
    app.server
        .getPlayerList()
        .getOps()
        .add(new net.minecraft.server.players.ServerOpListEntry(staff.getGameProfile(), 4, false));
    try {
      p.setPos(h.absoluteVec(new Vec3(12.5, 1, 12.5)));
      other.setPos(h.absoluteVec(new Vec3(13.5, 1, 12.5)));
      staff.setPos(h.absoluteVec(new Vec3(14.5, 1, 12.5)));
      app.storage.update(
          s -> {
            s.players.get(p.getUUID().toString()).balance = 1000;
            s.players.get(other.getUUID().toString()).balance = 1000;
          });
      h.assertTrue(
          cmd(app, p, "ffa balance send moneyTo 25") == 1
              && app.storage.player(p.getUUID()).balance == 975
              && app.storage.player(other.getUUID()).balance == 1025,
          "Balance send transfers whole Denar without enabling the legacy pay module");
      h.assertTrue(
          cmd(app, p, "ffa balance request moneyTo 50") == 1
              && app.storage.player(other.getUUID()).balance == 1025,
          "Payment request never debits the payer before consent");
      var request = app.payments.request(other.getUUID());
      h.assertTrue(
          cmd(app, other, "ffa balance accept " + UUID.randomUUID()) == 0
              && app.storage.player(other.getUUID()).balance == 1025,
          "A mismatched payment token cannot debit funds or consume a different pending request");

      h.assertTrue(
          messages(other).contains("[Accept]") && messages(p).contains("Payment request sent"),
          "Payment requests offer Accept/Deny");
      h.assertTrue(
          cmd(app, other, "ffa balance accept " + request.token()) == 1
              && app.storage.player(other.getUUID()).balance == 975
              && app.storage.player(p.getUUID()).balance == 1025,
          "Accept transfers exactly the agreed amount");
      h.assertTrue(
          cmd(app, other, "ffa balance accept " + request.token()) == 0
              && app.storage.player(other.getUUID()).balance == 975,
          "Payment buttons are single-use");
      clock.addAndGet(20000);
      cmd(app, p, "ffa balance request moneyTo 10");
      var denied = app.payments.request(other.getUUID());
      cmd(app, other, "ffa balance deny " + denied.token());
      h.assertTrue(
          app.storage.player(other.getUUID()).balance == 975, "Deny does not transfer funds");
      clock.addAndGet(20000);
      cmd(app, p, "ffa balance request moneyTo 10");
      var expired = app.payments.request(other.getUUID());
      clock.addAndGet(61000);
      h.assertTrue(
          cmd(app, other, "ffa balance accept " + expired.token()) == 0
              && app.storage.player(other.getUUID()).balance == 975,
          "Expired payment requests cannot debit funds");
      h.assertTrue(
          cmd(app, staff, "ffa tp moneyTo") == 1
              && staff.position().distanceToSqr(other.position()) < .01,
          "Admin TP is immediate without a request");
      h.assertTrue(
          cmd(app, p, "ffa tp moneyTo") == 0,
          "Admin TP is hidden and rejected for ordinary players");
      h.assertTrue(
          cmd(app, staff, "ffa shop set UpperApples minecraft:apple 2 1") == 1
              && app.storage.state().shops.containsKey("UpperApples"),
          "New shops preserve uppercase letters");
      p.getInventory().clearContent();
      other.getInventory().clearContent();
      h.assertTrue(
          cmd(app, staff, "ffa shop limits upperapples shared manual 3 2") == 1,
          "Shared manual shop quotas configure through native commands");
      app.economy.trade(p, "UPPERAPPLES", 2, true);
      app.economy.trade(other, "upperapples", 1, true);
      boolean cap = false;
      try {
        app.economy.trade(other, "UpperApples", 1, true);
      } catch (IllegalArgumentException expected) {
        cap = true;
      }
      h.assertTrue(
          cap && app.economy.remaining("upperapples", p.getUUID(), true) == 0,
          "Shared buy quota spans every player and case-insensitive shop lookup");
      app.economy.trade(p, "UpperApples", 2, false);
      cap = false;
      try {
        app.economy.trade(other, "UpperApples", 1, false);
      } catch (IllegalArgumentException expected) {
        cap = true;
      }
      h.assertTrue(cap, "Sell quota is independent of buy quota");
      cmd(app, staff, "ffa shop set upperapples minecraft:apple 3 1");
      h.assertTrue(
          app.economy.remaining("UpperApples", p.getUUID(), true) == 0,
          "Updating prices preserves quota counters");
      app.economy.limits("UpperApples", "shared", "manual", 5, 2);
      h.assertTrue(
          app.economy.remaining("UpperApples", p.getUUID(), true) == 2,
          "Changing numeric limits preserves already used capacity");
      app.economy.limits("UpperApples", "shared", "manual", 3, 2);
      cmd(app, staff, "ffa shop reset upperapples");
      h.assertTrue(
          app.economy.remaining("UpperApples", p.getUUID(), true) == 3,
          "Manual shared reset restores capacity");
      app.economy.limits("UpperApples", "player", "manual", 2, -1);
      app.economy.trade(p, "UpperApples", 2, true);
      h.assertTrue(
          app.economy.remaining("UpperApples", other.getUUID(), true) == 2
              && app.economy.remaining("UpperApples", p.getUUID(), true) == 0,
          "Per-player manual quotas are independent");
      app.economy.resetLimits("UpperApples", p.getUUID());
      h.assertTrue(
          app.economy.remaining("UpperApples", p.getUUID(), true) == 2,
          "Individual manual quota reset works");
      app.economy.limits("UpperApples", "player", "daily", 1, -1);
      app.economy.trade(p, "UpperApples", 1, true);
      h.assertTrue(
          app.economy.remaining("UpperApples", p.getUUID(), true) == 0,
          "Daily player quota records a trade");
      clock.addAndGet(86400000);
      h.assertTrue(
          app.economy.remaining("UpperApples", p.getUUID(), true) == 1,
          "Daily quotas reset with real elapsed calendar days");
      app.economy.limits("UpperApples", "shared", "manual", 2, -1);
      var pending = new PendingTrade();
      pending.id = UUID.randomUUID().toString();
      pending.player = p.getUUID().toString();
      pending.shop = "UpperApples";
      pending.quotaKey = "*|manual";
      pending.quantity = 2;
      pending.buy = true;
      pending.before = "previous inventory";
      pending.after = p.getInventory().save(new ListTag()).toString();
      pending.oldBalance = app.storage.player(p.getUUID()).balance;
      pending.newBalance = pending.oldBalance - 6;
      app.storage.update(s -> s.trades.put(pending.player, pending));
      h.assertTrue(
          app.economy.remaining("UpperApples", other.getUUID(), true) == 0,
          "Unresolved journals reserve shared shop capacity");
      cap = false;
      try {
        app.economy.resetLimits("UpperApples", null);
      } catch (IllegalArgumentException expected) {
        cap = true;
      }
      h.assertTrue(cap, "Quota reset cannot bypass a prepared transaction");
      app.economy.recover(p);
      app.economy.recover(p);
      h.assertTrue(
          app.economy.remaining("UpperApples", p.getUUID(), true) == 0
              && app.storage.player(p.getUUID()).balance == pending.newBalance,
          "Recovery commits balance and quota exactly once");
      h.assertTrue(
          new Storage(app.storage.directory)
                  .state()
                  .shops
                  .get("UpperApples")
                  .usage
                  .get("*|manual")
                  .bought
              == 2,
          "Shop usage counters persist to disk");
      portals(h, staff, app);
      pack(h, p, app);
      // An administrator recipient still receives pending-body monitoring, as explicitly requested.
      app.whispers.cancelFor(p.getUUID());
      app.whispers.cancelFor(staff.getUUID());
      outgoing(staff);
      app.whispers.send(p, staff, "Monitored pending admin message");
      h.assertTrue(
          messages(staff).contains("Monitored pending admin message"),
          "Administrator pending-whisper monitoring is preserved");
    } finally {
      app.whispers.cancelFor(p.getUUID());
      app.whispers.cancelFor(staff.getUUID());
      for (var portal : app.storage.state().portals.values())
        if (!state.portals.containsKey(portal.id)) Portals.removeVisuals(app, portal);
      NeoForge.EVENT_BUS.unregister(app.payments);
      app.payments = oldPayments;
      NeoForge.EVENT_BUS.register(oldPayments);
      app.economy = oldEconomy;
      app.server.getPlayerList().deop(staff.getGameProfile());
      app.server.getPlayerList().remove(staff);
      app.server.getPlayerList().remove(other);
      p.getInventory().load(inventory);
      app.storage.commit(state);
      Portals.reindex(app);
    }
  }

  private static void portals(GameTestHelper h, ServerPlayer staff, Administration app)
      throws Exception {
    staff.setPos(h.absoluteVec(new Vec3(12.5, 1, 13.5)));
    cmd(app, staff, "ffa pos1");
    staff.setPos(h.absoluteVec(new Vec3(14.5, 3, 13.5)));
    cmd(app, staff, "ffa pos2");
    h.assertTrue(
        cmd(app, staff, "ffa portal create guided") == 1
            && app.storage.state().portals.get("guided").warp.isEmpty(),
        "Portal creation can begin without separately creating a warp");
    var portal = app.storage.state().portals.get("guided");
    h.assertTrue(
        h.getLevel().getEntities().getAll().iterator().hasNext(),
        "Portal creates world-visible render entities");
    boolean surface = false;
    for (var entity : h.getLevel().getEntities().getAll())
      if (entity.getTags().contains("ffa.portal.guided") && entity instanceof Display.ItemDisplay)
        surface = true;
    h.assertTrue(
        surface,
        "Bundled-pack portals render a filled animated item-display surface rather than corner"
            + " glass markers");
    staff.setPos(h.absoluteVec(new Vec3(12.5, 1, 10.5)));
    h.assertTrue(
        cmd(app, staff, "ffa portal destination guided") == 1
            && app.storage.state().warps.containsKey("guided")
            && app.storage.state().portals.get("guided").managedWarp,
        "Setting a portal destination creates and links its named warp atomically");
    h.assertTrue(
        cmd(app, staff, "ffa portal access guided admin") == 1
            && app.storage.state().warps.get("guided").access.mode.equals("admin"),
        "Auto-created portal warps inherit access protection");
    h.assertTrue(
        cmd(app, staff, "ffa warp create easywarp") == 1,
        "Warp create saves the administrator's current safe location");
    h.assertTrue(
        cmd(app, staff, "ffa portal link guided easywarp") == 1,
        "Existing named warps remain linkable");
    for (var color : net.minecraft.world.item.DyeColor.values()) {
      Portals.color(app, staff, "guided", color.getName());
      boolean valid = false;
      for (var entity : h.getLevel().getEntities().getAll())
        if (entity.getTags().contains("ffa.portal.guided")
            && entity instanceof Display.ItemDisplay display) {
          var data = new net.minecraft.nbt.CompoundTag();
          display.saveWithoutId(data);
          var item =
              ItemStack.parse(h.getLevel().registryAccess(), data.getCompound("item"))
                  .orElseThrow();
          valid =
              item.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA).value()
                  == 600000 + color.getId();
          break;
        }
      h.assertTrue(valid, "Portal surface model uses the configured dye: " + color.getName());
    }
  }

  private static byte[] http(PortalPack pack, String request) throws Exception {
    var channel = new EmbeddedChannel(new PortalPack.HttpHandler());
    channel.writeInbound(Unpooled.copiedBuffer(request, StandardCharsets.US_ASCII));
    var bytes = new java.io.ByteArrayOutputStream();
    io.netty.buffer.ByteBuf buffer;
    while ((buffer = channel.readOutbound()) != null) {
      var copy = new byte[buffer.readableBytes()];
      buffer.readBytes(copy);
      buffer.release();
      bytes.write(copy);
    }
    channel.finishAndReleaseAll();
    return bytes.toByteArray();
  }

  private static void pack(GameTestHelper h, ServerPlayer p, Administration app) throws Exception {
    Class.forName(
        "net.minecraft.server.network.ServerConnectionListener$1"); // Force real initializer mixin
    // validation.
    var pack = app.portalPack;
    var listener = new net.minecraft.server.network.ServerConnectionListener(app.server);
    try {
      listener.startTcpServerListener(java.net.InetAddress.getLoopbackAddress(), 0);
      var field =
          net.minecraft.server.network.ServerConnectionListener.class.getDeclaredField("channels");
      field.setAccessible(true);
      var bound = (java.util.List<?>) field.get(listener);
      int port =
          ((java.net.InetSocketAddress)
                  ((io.netty.channel.ChannelFuture) bound.getFirst()).channel().localAddress())
              .getPort();
      try (var socket = new java.net.Socket(java.net.InetAddress.getLoopbackAddress(), port)) {
        socket.setSoTimeout(5000);
        socket
            .getOutputStream()
            .write(
                ("GET " + pack.path + " HTTP/1.1\r\nHost: localhost\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
        var wire = socket.getInputStream().readAllBytes();
        String text = new String(wire, StandardCharsets.ISO_8859_1);
        int body = text.indexOf("\r\n\r\n") + 4;
        h.assertTrue(
            text.startsWith("HTTP/1.1 200")
                && java.util.Arrays.equals(
                    java.util.Arrays.copyOfRange(wire, body, wire.length), pack.bytes),
            "The real Minecraft TCP listener serves the bundled pack without an extra HTTP port");
      }
    } finally {
      listener.stop();
    }

    var response = http(pack, "GET " + pack.path + " HTTP/1.1\r\nHost: localhost\r\n\r\n");
    String all = new String(response, StandardCharsets.ISO_8859_1);
    int split = all.indexOf("\r\n\r\n") + 4;
    h.assertTrue(
        all.startsWith("HTTP/1.1 200")
            && Arrays.equals(Arrays.copyOfRange(response, split, response.length), pack.bytes),
        "Bundled pack HTTP route serves exactly the advertised ZIP on the Minecraft channel");
    h.assertTrue(
        new String(
                http(pack, "GET /../../server.properties HTTP/1.1\r\n\r\n"),
                StandardCharsets.US_ASCII)
            .startsWith("HTTP/1.1 404"),
        "Pack route cannot read arbitrary server files");
    var head = http(pack, "HEAD " + pack.path + " HTTP/1.1\r\n\r\n");
    String headers = new String(head, StandardCharsets.US_ASCII);
    h.assertTrue(
        headers.endsWith("\r\n\r\n") && headers.contains("Content-Length: " + pack.bytes.length),
        "HEAD returns only immutable pack metadata");
    var vanilla = new EmbeddedChannel(new PortalPack.HttpHandler());
    byte[] handshake = {7, 0, 1, 2, 3};
    vanilla.writeInbound(Unpooled.wrappedBuffer(handshake));
    ByteBuf forwarded = vanilla.readInbound();
    byte[] actual = new byte[forwarded.readableBytes()];
    forwarded.readBytes(actual);
    forwarded.release();
    vanilla.finishAndReleaseAll();
    h.assertTrue(
        Arrays.equals(handshake, actual),
        "Normal Minecraft handshake bytes pass through unchanged");

    var fragmented = new EmbeddedChannel(new PortalPack.HttpHandler());
    fragmented.writeInbound(Unpooled.copiedBuffer("GE", StandardCharsets.US_ASCII));
    h.assertTrue(
        fragmented.readOutbound() == null, "Partial HTTP headers wait for the complete request");
    fragmented.writeInbound(
        Unpooled.copiedBuffer("T " + pack.path + " HTTP/1.1\r\n\r\n", StandardCharsets.US_ASCII));
    var header = (ByteBuf) fragmented.readOutbound();
    h.assertTrue(
        header.toString(StandardCharsets.US_ASCII).startsWith("HTTP/1.1 200"),
        "Fragmented HTTP resource-pack requests complete correctly");
    header.release();
    fragmented.finishAndReleaseAll();
    var oversized = new String(http(pack, "GET /" + "x".repeat(9000)), StandardCharsets.US_ASCII);
    h.assertTrue(
        oversized.startsWith("HTTP/1.1 431"), "Oversized HTTP headers are bounded and rejected");
    var legacy = new EmbeddedChannel(new PortalPack.HttpHandler());
    legacy.writeInbound(Unpooled.wrappedBuffer(new byte[] {(byte) 0xFE}));
    var legacyByte = (ByteBuf) legacy.readInbound();
    h.assertTrue(
        legacyByte.readUnsignedByte() == 0xFE,
        "Legacy Minecraft status ping is not held waiting for an HTTP header");
    legacyByte.release();
    legacy.finishAndReleaseAll();
    PortalPack.address(p.connection.getConnection(), "example.org\u0000FML3\u0000", 25565);
    outgoing(p);
    pack.offer(p);
    h.assertTrue(
        outgoing(p).stream()
            .anyMatch(
                packet ->
                    packet instanceof ClientboundResourcePackPushPacket push
                        && push.required()
                        && push.hash().equals(pack.sha1)
                        && push.url().equals("http://example.org:25565" + pack.path)),
        "Native clients receive the bundled pack URL/hash without a separate client mod or extra"
            + " port");
    pack.offer(p);
    h.assertTrue(
        outgoing(p).isEmpty(), "Reloading unchanged pack settings does not duplicate offers");
  }
}
