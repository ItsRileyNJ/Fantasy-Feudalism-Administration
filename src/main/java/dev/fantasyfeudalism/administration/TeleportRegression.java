package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.RegressionTests.*;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;

/** Exercise the real /execute + /tp command, including its early NeoForge event. */
final class TeleportRegression {
  public static final class CommandOffset {
    final net.minecraft.server.level.ServerPlayer player;
    final double x;

    CommandOffset(net.minecraft.server.level.ServerPlayer player, double x) {
      this.player = player;
      this.x = x;
    }

    @net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public void change(
        net.neoforged.neoforge.event.entity.EntityTeleportEvent.TeleportCommand event) {
      if (event.getEntity() == player) event.setTargetX(x);
    }
  }

  static void verify(GameTestHelper h, Administration app) throws Exception {
    var regions = app.regions.copy();
    var p = player(h, "dimensionAdmin");
    var source = h.getLevel();
    var spawn =
        app.server.getLevel(
            net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse("ffa:spawn")));
    var destination = spawn != null ? spawn : app.server.getLevel(Level.NETHER);
    var target = h.absolutePos(new BlockPos(12, 1, 12)).atY(80);
    var sourceBefore = new HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
    for (var pos : BlockPos.betweenClosed(target.below(), target.above()))
      sourceBefore.put(pos.immutable(), source.getBlockState(pos));
    destination.getChunk(target);
    var destinationBefore =
        new HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
    for (var pos : BlockPos.betweenClosed(target.offset(-1, -1, -1), target.offset(2, 3, 1)))
      destinationBefore.put(pos.immutable(), destination.getBlockState(pos));
    try {
      app.regions.edit(List.of(), app.server, app.settings, app.compat);
      app.server
          .getPlayerList()
          .getOps()
          .add(new net.minecraft.server.players.ServerOpListEntry(p.getGameProfile(), 4, false));
      p.setGameMode(GameType.CREATIVE);
      p.setPos(h.absoluteVec(new Vec3(4.5, 1, 12.5)));
      h.assertTrue(
          app.compat.admin(p), "The teleporting player is a real level-four administrator");
      source.setBlock(target, Blocks.STONE.defaultBlockState(), 3);
      for (var pos : destinationBefore.keySet())
        destination.setBlock(
            pos,
            pos.getY() == 79 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(),
            3);
      h.assertTrue(
          app.teleports.validate(p, point(source, target)) != null,
          "These coordinates are obstructed in the source world");
      h.assertTrue(
          app.teleports.validate(p, point(destination, target)) == null,
          "The same coordinates are safe in the destination world");
      String command = command(destination, "@s", target);
      outgoing(p);
      app.server.getCommands().getDispatcher().execute(command, p.createCommandSourceStack());
      h.assertTrue(
          p.serverLevel() == destination
              && p.position().distanceToSqr(target.getBottomCenter()) < .001,
          "Real OP /execute in ... run tp reaches the destination despite source-world"
              + " obstruction");
      var offset = new CommandOffset(p, target.getX() + 1.5);
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(offset);
      try {
        app.server
            .getCommands()
            .getDispatcher()
            .execute(command(destination, "@s", target), p.createCommandSourceStack());
        h.assertTrue(
            Math.abs(p.getX() - offset.x) < .001,
            "Coordinate-changing mod events still affect the actual teleport");
      } finally {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(offset);
      }
      expectFailure(h, app, p, command(source, "@s", target), "obstructed");
      h.assertTrue(p.serverLevel() == destination, "An unsafe destination really remains blocked");
      var beforeRelative = p.position();
      app.server
          .getCommands()
          .getDispatcher()
          .execute("tp @s ~1 ~ ~", p.createCommandSourceStack());
      h.assertTrue(
          p.position().distanceToSqr(beforeRelative.add(1, 0, 0)) < .001,
          "Relative command coordinates are not added to the player position twice");
      p.teleportRelative(-.25, 0, 0);
      h.assertTrue(
          p.position().distanceToSqr(beforeRelative.add(.75, 0, 0)) < .001,
          "The relative ServerPlayer API still adds offsets exactly once");

      var region = new Model.Region();
      region.id = region.name = "teleport_test";
      region.kind = Model.Kind.SPAWN;
      region.dimension = destination.dimension().location().toString();
      region.minX = target.getX() - 2;
      region.maxX = target.getX() + 2;
      region.minZ = target.getZ() - 2;
      region.maxZ = target.getZ() + 2;
      region.settlementOverride = true;
      region.flags.put(Model.Flag.TELEPORT_IN, false);
      app.regions.edit(List.of(region), app.server, app.settings, app.compat);
      var ordinary = player(h, "dimensionUser");
      try {
        ordinary.setGameMode(GameType.SURVIVAL);
        expectFailure(
            h,
            app,
            p,
            command(destination, ordinary.getGameProfile().getName(), target),
            app.settings.denied);
        h.assertTrue(
            ordinary.serverLevel() == source,
            "Deferring the early event does not bypass region permissions");
      } finally {
        app.server.getPlayerList().remove(ordinary);
      }
      source.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
      source.setBlock(target.above(), Blocks.AIR.defaultBlockState(), 3);
      source.setBlock(target.below(), Blocks.STONE.defaultBlockState(), 3);
      app.server
          .getCommands()
          .getDispatcher()
          .execute(command(source, "@s", target), p.createCommandSourceStack());
      app.server
          .getCommands()
          .getDispatcher()
          .execute(command(destination, "@s", target), p.createCommandSourceStack());
      h.assertTrue(
          p.serverLevel() == destination,
          "OP bypass remains effective with destination teleport-in disabled");
    } finally {
      app.server.getPlayerList().deop(p.getGameProfile());
      app.server.getPlayerList().remove(p);
      sourceBefore.forEach((pos, block) -> source.setBlock(pos, block, 3));
      destinationBefore.forEach((pos, block) -> destination.setBlock(pos, block, 3));
      app.regions.edit(regions, app.server, app.settings, app.compat);
    }
  }

  private static String command(
      net.minecraft.server.level.ServerLevel level, String targetPlayer, BlockPos pos) {
    return String.format(
        Locale.ROOT,
        "execute in %s run tp %s %.1f %d %.1f",
        level.dimension().location(),
        targetPlayer,
        pos.getX() + .5,
        pos.getY(),
        pos.getZ() + .5);
  }

  private static void expectFailure(
      GameTestHelper h,
      Administration app,
      net.minecraft.server.level.ServerPlayer player,
      String command,
      String error)
      throws Exception {
    outgoing(player);
    try {
      app.server.getCommands().getDispatcher().execute(command, player.createCommandSourceStack());
      h.fail("Expected command failure: " + command);
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException blocked) {
      h.assertTrue(
          blocked.getRawMessage().getString().contains(error),
          "Specific teleport rejection is preserved: " + blocked.getMessage());
    }
    h.assertFalse(
        outgoing(player).stream()
            .anyMatch(
                packet ->
                    packet
                            instanceof
                            net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat
                        && chat.content().getContents()
                            instanceof net.minecraft.network.chat.contents.TranslatableContents text
                        && text.getKey().startsWith("commands.teleport.success")),
        "Blocked FFA commands do not also print a native teleport-success message");
  }

  private static Model.Point point(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
    return new Model.Point(
        level.dimension().location().toString(),
        pos.getX() + .5,
        pos.getY(),
        pos.getZ() + .5,
        0,
        0);
  }
}
