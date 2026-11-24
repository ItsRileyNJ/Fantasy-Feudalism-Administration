package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.RegressionTests.*;

import io.netty.buffer.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/** Native packet checks; audio contains no executable client code. Enabled only by ffa.tests. */
final class SoundRegression {
  private static <T> List<T> packets(ServerPlayer player, Class<T> type) {
    return outgoing(player).stream().filter(type::isInstance).map(type::cast).toList();
  }

  private static void cmd(Administration app, ServerPlayer player, String command)
      throws Exception {
    if (app.server.getCommands().getDispatcher().execute(command, player.createCommandSourceStack())
        != 1) throw new AssertionError("Sound command failed: " + command);
  }

  static void verify(GameTestHelper h, Administration app) throws Exception {
    var oldState = app.storage.copy();
    var oldPack = app.soundPack;
    var oldRegions = app.soundRegions;
    boolean enabled = app.settings.audioEnabled;
    String oldUrl = app.settings.audioPackUrl;
    var folder = Files.createTempDirectory(app.storage.directory, "test-audio-");
    var p = player(h, "audioAdmin");
    var other = player(h, "audioOther");
    var clock = new AtomicLong(1_000_000_000L);
    app.server
        .getPlayerList()
        .getOps()
        .add(new net.minecraft.server.players.ServerOpListEntry(p.getGameProfile(), 4, false));
    try {
      app.settings.audioEnabled = true;
      app.settings.audioPackUrl = "";
      app.storage.update(s -> s.soundRegions.clear());
      app.soundRegions = new SoundRegions(app, clock::get);
      app.soundPack = new SoundPack(app, folder);
      PortalPack.address(p.connection.getConnection(), "localhost", 25565);
      PortalPack.address(other.connection.getConnection(), "localhost", 25565);
      try (var source =
          SoundRegression.class.getResourceAsStream("/data/ffa/testing/ambience.ogg")) {
        Files.write(folder.resolve("village.ogg"), source.readAllBytes());
      }
      outgoing(p);
      cmd(app, p, "ffa sound reload");
      var pushes = packets(p, ClientboundResourcePackPushPacket.class);
      h.assertTrue(
          pushes.size() == 1
              && !pushes.getFirst().required()
              && pushes
                  .getFirst()
                  .url()
                  .equals("http://localhost:25565" + app.soundPack.snapshot.path()),
          "Vanilla resource-pack delivery needs no FFA client channel and permits silent joining");
      var root = clientCommands(other, true).getChild("ffa");
      h.assertTrue(
          root.getChild("sound") == null && root.getChild("soundregion") == null,
          "Audio administration is hidden from ordinary players");
      var adminRoot = clientCommands(p, true).getChild("ffa");
      h.assertTrue(
          adminRoot.getChild("soundregion") != null, "Audio commands are visible to admins");

      p.setPos(h.absoluteVec(new Vec3(2.5, 1, 2.5)));
      cmd(app, p, "ffa pos1");
      p.setPos(h.absoluteVec(new Vec3(8.5, 1, 8.5)));
      cmd(app, p, "ffa pos2");
      cmd(app, p, "ffa soundregion create Village village");
      cmd(app, p, "ffa soundregion volume Village 0.8");
      cmd(app, p, "ffa soundregion fade Village 2");
      var region = app.soundRegions.get("village");
      h.assertTrue(
          region.volume == .8f
              && region.fadeTicks == 40
              && new Storage(app.storage.directory).state().soundRegions.containsKey("Village"),
          "Mixed-case selected sound region, settings and dimensions persist");
      p.setPos(h.absoluteVec(new Vec3(4.5, 1, 4.5)));
      other.setPos(h.absoluteVec(new Vec3(12.5, 1, 12.5)));
      outgoing(p);
      outgoing(other);
      app.soundRegions.update(p);
      h.assertTrue(
          packets(p, ClientboundSoundEntityPacket.class).isEmpty(),
          "Audio waits for successful resource-pack loading");
      SoundPack.response(
          p.connection,
          new ServerboundResourcePackPacket(
              app.soundPack.snapshot.id(),
              ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
      app.soundRegions.update(p);
      var entered = outgoing(p);
      var add =
          entered.stream()
              .filter(ClientboundAddEntityPacket.class::isInstance)
              .map(ClientboundAddEntityPacket.class::cast)
              .findFirst()
              .orElseThrow();
      var sound =
          entered.stream()
              .filter(ClientboundSoundEntityPacket.class::isInstance)
              .map(ClientboundSoundEntityPacket.class::cast)
              .findFirst()
              .orElseThrow();
      h.assertTrue(
          add.getType() == EntityType.ITEM_DISPLAY
              && sound.getId() == add.getId()
              && sound.getVolume() == 1
              && sound.getSound().value().getLocation().getNamespace().equals("ffa")
              && h.getLevel().getEntity(add.getId()) == null
              && outgoing(other).isEmpty(),
          "Private empty native audio anchor is absent from the world and other players' packets");
      h.assertTrue(
          Math.abs(add.getY() - p.getEyeY() - SoundRegions.distance(0)) < .00001,
          "Entry begins near silence using vanilla linear attenuation");
      clock.addAndGet(1_000_000_000L);
      app.soundRegions.update(p);
      var half = packets(p, ClientboundTeleportEntityPacket.class).getFirst();
      h.assertTrue(
          Math.abs(half.getY() - p.getEyeY() - 9.6) < .00001,
          "Halfway through entry the native sound reaches half target gain");
      clock.addAndGet(1_000_000_000L);
      app.soundRegions.update(p);
      var repeated = outgoing(p);
      h.assertTrue(
          repeated.stream().filter(ClientboundSoundEntityPacket.class::isInstance).count() == 1
              && repeated.stream().noneMatch(ClientboundAddEntityPacket.class::isInstance),
          "Track restarts automatically at its Vorbis duration without resetting the fade or"
              + " anchor");
      clock.addAndGet(100_000_000L);
      app.soundRegions.update(p);
      h.assertTrue(
          outgoing(p).isEmpty(), "Standing at steady gain sends no per-tick movement packets");
      p.setPos(p.getX() + .25, p.getY(), p.getZ());
      app.soundRegions.update(p);
      h.assertTrue(
          packets(p, ClientboundTeleportEntityPacket.class).getFirst().getX() == p.getX(),
          "The private sound follows listener movement");

      p.setPos(h.absoluteVec(new Vec3(12.5, 1, 12.5)));
      app.soundRegions.update(p);
      h.assertTrue(
          outgoing(p).stream().noneMatch(ClientboundStopSoundPacket.class::isInstance),
          "Leaving starts a fade instead of abruptly stopping audio");
      clock.addAndGet(1_000_000_000L);
      app.soundRegions.update(p);
      h.assertTrue(
          Math.abs(
                  packets(p, ClientboundTeleportEntityPacket.class).getFirst().getY()
                      - p.getEyeY()
                      - 9.6)
              < .00001,
          "Exit smoothly reduces gain over the configured duration");
      p.setPos(h.absoluteVec(new Vec3(4.5, 1, 4.5)));
      app.soundRegions.update(p);
      h.assertTrue(
          outgoing(p).stream().noneMatch(ClientboundSoundEntityPacket.class::isInstance),
          "Reentering reverses the existing fade without duplicating or restarting the same track");
      clock.addAndGet(1_000_000_000L);
      app.soundRegions.update(p);
      outgoing(p);
      p.setPos(h.absoluteVec(new Vec3(12.5, 1, 12.5)));
      app.soundRegions.update(p);
      outgoing(p);
      clock.addAndGet(2_000_000_000L);
      app.soundRegions.update(p);
      var ended = outgoing(p);
      h.assertTrue(
          ended.stream().anyMatch(ClientboundStopSoundPacket.class::isInstance)
              && ended.stream().anyMatch(ClientboundRemoveEntitiesPacket.class::isInstance),
          "Fade completion stops the native sound and removes its private anchor");
      app.soundRegions.update(p);
      h.assertTrue(outgoing(p).isEmpty(), "A completed exit retains no playback session");

      p.setPos(h.absoluteVec(new Vec3(4.5, 1, 4.5)));
      app.soundRegions.update(p);
      outgoing(p);
      Files.copy(folder.resolve("village.ogg"), folder.resolve("inn.ogg"));
      var oldPackId = app.soundPack.snapshot.id();
      app.soundPack.reload();
      var replaced = outgoing(p);
      h.assertTrue(
          !oldPackId.equals(app.soundPack.snapshot.id())
              && !app.soundPack.ready(p)
              && replaced.stream().anyMatch(ClientboundResourcePackPopPacket.class::isInstance)
              && replaced.stream().anyMatch(ClientboundResourcePackPushPacket.class::isInstance),
          "Changed assets replace the old pack and wait for the new pack acknowledgment");
      h.assertTrue(
          Arrays.equals(
              Files.readAllBytes(app.storage.directory.resolve("sound-pack/ffa-sounds.zip")),
              app.soundPack.snapshot.bytes()),
          "Exported pack for CDN hosting exactly matches the offered hash");
      SoundPack.response(
          p.connection,
          new ServerboundResourcePackPacket(
              app.soundPack.snapshot.id(),
              ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
      app.soundRegions.update(p);
      outgoing(p);
      clock.addAndGet(1_000_000_000L);
      app.soundRegions.update(p);
      outgoing(p);
      var inn = new Model.SoundRegion();
      inn.id = "Inn";
      inn.dimension = region.dimension;
      inn.track = "inn";
      inn.volume = .8f;
      inn.fadeTicks = 40;
      inn.priority = 10;
      inn.minX = region.minX + 1;
      inn.maxX = region.maxX - 1;
      inn.minZ = region.minZ + 1;
      inn.maxZ = region.maxZ - 1;
      app.storage.update(state -> state.soundRegions.put(inn.id, inn));
      app.soundRegions.update(p);
      var cross = packets(p, ClientboundSoundEntityPacket.class);
      h.assertTrue(
          cross.size() == 1
              && cross
                  .getFirst()
                  .getSound()
                  .value()
                  .getLocation()
                  .getPath()
                  .startsWith("ambience.inn_"),
          "Higher-priority overlapping area starts its own selected track");
      clock.addAndGet(1_000_000_000L);
      app.soundRegions.update(p);
      h.assertTrue(
          packets(p, ClientboundTeleportEntityPacket.class).size() == 2,
          "Old and new mono sound anchors crossfade independently");
      app.storage.update(state -> state.soundRegions.get("Inn").track = "village");
      app.soundRegions.update(p);
      h.assertTrue(
          packets(p, ClientboundSoundEntityPacket.class).isEmpty(),
          "Returning to the same track during a crossfade reuses its original anchor");
      app.storage.update(state -> state.soundRegions.remove("Inn"));
      SoundPack.response(
          p.connection,
          new ServerboundResourcePackPacket(
              app.soundPack.snapshot.id(), ServerboundResourcePackPacket.Action.DISCARDED));
      h.assertTrue(
          !packets(p, ClientboundStopSoundPacket.class).isEmpty() && !app.soundPack.ready(p),
          "Removing the audio pack stops playback without disconnecting a player");
      var snapshot = app.soundPack.snapshot;
      Files.write(folder.resolve("bad.ogg"), new byte[] {1, 2, 3});
      boolean refused = false;
      try {
        app.soundPack.reload();
      } catch (IllegalArgumentException expected) {
        refused = true;
      }
      h.assertTrue(
          refused && app.soundPack.snapshot == snapshot,
          "Bad audio reload preserves the last valid resource pack and track catalog");
      Files.delete(folder.resolve("bad.ogg"));

      var channel = new EmbeddedChannel(new PortalPack.HttpHandler());
      channel.writeInbound(
          Unpooled.copiedBuffer(
              "GET " + snapshot.path() + " HTTP/1.1\r\n\r\n", StandardCharsets.US_ASCII));
      var bytes = new java.io.ByteArrayOutputStream();
      ByteBuf buffer;
      while ((buffer = channel.readOutbound()) != null) {
        byte[] part = new byte[buffer.readableBytes()];
        buffer.readBytes(part);
        buffer.release();
        bytes.write(part);
      }
      channel.finishAndReleaseAll();
      byte[] response = bytes.toByteArray();
      String text = new String(response, StandardCharsets.ISO_8859_1);
      int split = text.indexOf("\r\n\r\n") + 4;
      h.assertTrue(
          text.startsWith("HTTP/1.1 200")
              && Arrays.equals(
                  Arrays.copyOfRange(response, split, response.length), snapshot.bytes()),
          "Existing Minecraft-port HTTP handler serves exactly the advertised immutable audio ZIP");
      cmd(app, p, "ffa soundregion delete Village");
    } finally {
      app.soundRegions.stop(p);
      app.soundRegions.stop(other);
      app.soundPack.close();
      app.soundPack = oldPack;
      app.soundRegions = oldRegions;
      SoundPack.active = oldPack;
      app.settings.audioEnabled = enabled;
      app.settings.audioPackUrl = oldUrl;
      app.server.getPlayerList().deop(p.getGameProfile());
      app.server.getPlayerList().remove(p);
      app.server.getPlayerList().remove(other);
      app.storage.commit(oldState);
      try (var files = Files.list(folder)) {
        for (var file : files.toList()) Files.delete(file);
      }
      Files.delete(folder);
    }
  }
}
