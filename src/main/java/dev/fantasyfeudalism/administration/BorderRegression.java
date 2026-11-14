package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.RegressionTests.outgoing;
import static dev.fantasyfeudalism.administration.RegressionTests.player;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

final class BorderRegression {
  private static int cmd(Administration app, ServerPlayer player, String command) throws Exception {
    try {
      return app.server
          .getCommands()
          .getDispatcher()
          .execute(command, player.createCommandSourceStack());
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException denied) {
      return 0;
    }
  }

  static void verify(GameTestHelper h, Administration app) throws Exception {
    var saved = app.storage.copy();
    int distance = app.settings.borderVisibilityDistance;
    var admin = player(h, "borderAdmin");
    var ordinary = player(h, "borderPlayer");
    var nativeBorder = h.getLevel().getWorldBorder();
    double oldMinX = nativeBorder.getMinX(), oldMaxZ = nativeBorder.getMaxZ();
    var otherBorder = app.server.getLevel(net.minecraft.world.level.Level.NETHER).getWorldBorder();
    double otherMinX = otherBorder.getMinX(), otherMaxZ = otherBorder.getMaxZ();
    var nativeSettings = nativeBorder.createSettings();
    try {
      app.server
          .getPlayerList()
          .getOps()
          .add(
              new net.minecraft.server.players.ServerOpListEntry(admin.getGameProfile(), 4, false));
      h.assertTrue(
          cmd(app, admin, "ffa worldborder set") == 0, "Missing border selection is rejected");
      var lo = h.absolutePos(new BlockPos(1, 1, 2));
      var hi = h.absolutePos(new BlockPos(10, 1, 6));
      admin.setPos(hi.getCenter());
      h.assertTrue(
          cmd(app, admin, "ffa worldborder pos1") == 1, "First border corner shortcut works");
      admin.setPos(lo.getCenter());
      h.assertTrue(cmd(app, admin, "ffa pos2") == 1, "Border reuses region selection");
      h.assertTrue(
          cmd(app, admin, "ffa worldborder set") == 1, "Rectangle border creation succeeds");
      h.assertTrue(
          nativeBorder.getMinX() == lo.getX()
              && nativeBorder.getMaxX() == hi.getX() + 1.0
              && nativeBorder.getMinZ() == lo.getZ()
              && nativeBorder.getMaxZ() == hi.getZ() + 1.0,
          "Reversed corners define an exact inclusive 10 by 5 rectangle");
      h.assertTrue(
          nativeBorder.getCenterX() == lo.getX() + 5
              && nativeBorder.getCenterZ() == lo.getZ() + 2.5,
          "Native spawn fallback and portal searches read the rectangular center");
      app.worldBorders.close();
      h.assertTrue(
          nativeBorder.getMinX() == oldMinX, "Detaching restores vanilla collision bounds");
      app.worldBorders.refresh();
      h.assertTrue(
          nativeBorder.getMinX() == lo.getX(), "Persisted rectangle reattaches at startup refresh");
      h.assertTrue(
          otherBorder.getMinX() == otherMinX && otherBorder.getMaxZ() == otherMaxZ,
          "Nether bounds remain unchanged");
      h.assertTrue(
          nativeBorder.isWithinBounds(lo)
              && nativeBorder.isWithinBounds(hi)
              && !nativeBorder.isWithinBounds(hi.east())
              && !nativeBorder.isWithinBounds(lo.north()),
          "Containment includes both corner blocks and excludes neighboring outside blocks");
      h.assertTrue(
          nativeBorder.createSettings().getSize() == nativeSettings.getSize()
              && nativeBorder.createSettings().getCenterX() == nativeSettings.getCenterX(),
          "FFA rectangles preserve underlying vanilla settings for saves and removal");
      var loaded =
          new Storage(app.storage.directory)
              .state()
              .worldBorders
              .get(h.getLevel().dimension().location().toString());
      h.assertTrue(
          loaded != null && loaded.minX == lo.getX() && loaded.maxZ == hi.getZ(),
          "World border persists in saved state");
      ordinary.setGameMode(GameType.SURVIVAL);
      ordinary.setPos(lo.getX() + 1.5, lo.getY() + 10, lo.getZ() + 2.5);
      ordinary.move(MoverType.SELF, new Vec3(-3, 0, 0));
      h.assertTrue(
          Math.abs(ordinary.getBoundingBox().minX - lo.getX()) < .0001,
          "Actual player movement collides with rectangular west boundary");
      ordinary.setPos(hi.getX() - .5, lo.getY() + 10, lo.getZ() + 2.5);
      ordinary.move(MoverType.SELF, new Vec3(3, 0, 0));
      h.assertTrue(
          Math.abs(ordinary.getBoundingBox().maxX - hi.getX() - 1) < .0001,
          "Actual player movement collides with rectangular east boundary");
      ordinary.setPos(lo.getX() + 4.5, lo.getY() + 10, lo.getZ() + .5);
      ordinary.move(MoverType.SELF, new Vec3(0, 0, -2));
      h.assertTrue(
          Math.abs(ordinary.getBoundingBox().minZ - lo.getZ()) < .0001,
          "Actual player movement collides with rectangular north boundary");
      ordinary.setPos(lo.getX() + 4.5, lo.getY() + 10, hi.getZ() + .5);
      ordinary.move(MoverType.SELF, new Vec3(0, 0, 2));
      h.assertTrue(
          Math.abs(ordinary.getBoundingBox().maxZ - hi.getZ() - 1) < .0001,
          "Actual player movement collides with rectangular south boundary");
      var outside =
          new Model.Point(
              h.getLevel().dimension().location().toString(),
              hi.getX() + 1.5,
              lo.getY(),
              lo.getZ() + 2.5,
              0,
              0);
      h.assertTrue(
          "Destination is outside world bounds.".equals(app.teleports.validate(ordinary, outside)),
          "Travel safety uses rectangle bounds");
      h.assertTrue(
          cmd(app, ordinary, "ffa worldborder clear") == 0, "Non-admin cannot remove border");
      h.assertTrue(
          RegressionTests.clientCommands(ordinary, true).getChild("ffa").getChild("worldborder")
              == null,
          "Actual ordinary client tree hides administrator border commands");

      // Use a large rectangle for independent near/far visibility checks.
      var large = new Model.Border();
      large.minX = lo.getX();
      large.minZ = lo.getZ();
      large.maxX = lo.getX() + 99;
      large.maxZ = lo.getZ() + 199;
      app.storage.update(
          s -> s.worldBorders.put(h.getLevel().dimension().location().toString(), large));
      app.worldBorders.refresh();
      app.settings.borderVisibilityDistance = 8;
      ordinary.setPos(large.minX + 50, lo.getY(), large.minZ + 100);
      outgoing(ordinary);
      app.worldBorders.update(ordinary);
      var hidden = latest(ordinary);
      h.assertTrue(
          hidden != null
              && hidden.getNewSize() == WorldBorders.VIEW_SIZE
              && hidden.getNewCenterX() == 0
              && hidden.getNewCenterZ() == 0,
          "Actual outbound packet hides all walls away from the border");
      ordinary.setPos(large.minX + 4, lo.getY(), large.minZ + 100);
      app.worldBorders.update(ordinary);
      var visible = latest(ordinary);
      h.assertTrue(
          visible != null
              && visible.getNewCenterX() - visible.getNewSize() / 2 == large.minX
              && visible.getNewCenterZ() == 0
              && visible.getWarningBlocks() == 0
              && visible.getWarningTime() == 0,
          "Actual outbound packet shows only a nearby true edge, without distant red warnings");
      var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
      try {
        ClientboundInitializeBorderPacket.STREAM_CODEC.encode(buffer, visible);
        var decoded = ClientboundInitializeBorderPacket.STREAM_CODEC.decode(buffer);
        h.assertTrue(
            decoded.getNewSize() == visible.getNewSize()
                && decoded.getNewCenterX() == visible.getNewCenterX(),
            "Visual packets round trip through Minecraft's ordinary network codec");
      } finally {
        buffer.release();
      }
      app.worldBorders.update(ordinary);
      h.assertTrue(outgoing(ordinary).isEmpty(), "Unchanged views do not spam border packets");
      ordinary.connection.send(new ClientboundSetBorderSizePacket(nativeBorder));
      h.assertTrue(
          latest(ordinary) != null,
          "Native border broadcasts are filtered through dimension visibility");
      app.settings.borderVisibilityDistance = 2;
      app.worldBorders.update(ordinary);
      h.assertTrue(
          latest(ordinary).getNewCenterX() == 0,
          "Changing visibility distance updates the next view");
      var joined = player(h, "borderJoin");
      try {
        h.assertTrue(
            outgoing(joined).stream().anyMatch(p -> p instanceof ClientboundInitializeBorderPacket),
            "New logins receive a vanilla-compatible border view");
      } finally {
        app.server.getPlayerList().remove(joined);
      }
      var overworld = ordinary.serverLevel();
      try {
        ordinary.setServerLevel(app.server.getLevel(net.minecraft.world.level.Level.NETHER));
        ordinary.connection.send(new ClientboundInitializeBorderPacket(nativeBorder));
        var untouched = latest(ordinary);
        h.assertTrue(
            untouched != null
                && untouched.getNewCenterX() == otherBorder.createSettings().getCenterX()
                && untouched.getNewSize() == otherBorder.getSize(),
            "Actual unmanaged Nether packet retains its own native border, not the custom Overworld"
                + " center");
      } finally {
        ordinary.setServerLevel(overworld);
      }
      var independent = new Model.Border();
      independent.minX = -500;
      independent.maxX = -401;
      independent.minZ = -200;
      independent.maxZ = -151;
      app.storage.update(s -> s.worldBorders.put("minecraft:the_nether", independent));
      app.worldBorders.refresh();
      h.assertTrue(
          otherBorder.getMinX() == -500 && otherBorder.getMaxZ() == -150,
          "A second dimension can have different rectangular bounds");
      h.assertTrue(cmd(app, admin, "ffa worldborder clear") == 1, "Border removal succeeds");
      h.assertTrue(
          otherBorder.getMinX() == -500,
          "Clearing the Overworld leaves the Nether rectangle intact");
      app.worldBorders.clear(app.server.getLevel(net.minecraft.world.level.Level.NETHER));
      h.assertTrue(
          otherBorder.getMinX() == otherMinX && otherBorder.getMaxZ() == otherMaxZ,
          "Removing the other dimension restores its native bounds too");
      h.assertTrue(
          nativeBorder.getMinX() == oldMinX && nativeBorder.getMaxZ() == oldMaxZ,
          "Removal restores vanilla bounds and collision");
    } finally {
      app.settings.borderVisibilityDistance = distance;
      app.server.getPlayerList().deop(admin.getGameProfile());
      app.server.getPlayerList().remove(admin);
      app.server.getPlayerList().remove(ordinary);
      app.storage.commit(saved);
      app.worldBorders.refresh();
    }
  }

  private static ClientboundInitializeBorderPacket latest(ServerPlayer player) {
    return outgoing(player).stream()
        .filter(p -> p instanceof ClientboundInitializeBorderPacket)
        .map(p -> (ClientboundInitializeBorderPacket) p)
        .reduce((a, b) -> b)
        .orElse(null);
  }
}
