package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;
import static dev.fantasyfeudalism.administration.RegressionTests.*;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real disk-backed full chunks and genuinely ungenerated destinations, not simulated load jobs. */
final class AdminChunkRegression {
  private static Point point(ServerLevel level, ChunkPos chunk) {
    return new Point(
        level.dimension().location().toString(),
        chunk.getMiddleBlockX() + .5,
        level.getMaxBuildHeight() - 8,
        chunk.getMiddleBlockZ() + .5,
        0,
        0);
  }

  private static ChunkPos fresh(GameTestHelper h, ServerLevel level, int offset) {
    var center = new ChunkPos(h.absolutePos(BlockPos.ZERO));
    for (int i = 0; i < 50; i++) {
      var pos = new ChunkPos(center.x + offset + i * 8, center.z + 192);
      if (!level.hasChunk(pos.x, pos.z)
          && level.getChunkSource().chunkMap.read(pos).join().isEmpty()) return pos;
    }
    throw new AssertionError("No unused destination fixture found");
  }

  private static void save(
      ServerLevel level, ChunkPos pos, Point point, boolean hazard, boolean obstructed) {
    var chunk = new LevelChunk(level, pos);
    var feet = BlockPos.containing(point.x, point.y, point.z);
    chunk.setBlockState(
        feet.below(), (hazard ? Blocks.MAGMA_BLOCK : Blocks.GOLD_BLOCK).defaultBlockState(), false);
    if (obstructed) chunk.setBlockState(feet, Blocks.STONE.defaultBlockState(), false);
    level.getChunkSource().chunkMap.write(pos, ChunkSerializer.write(level, chunk)).join();
  }

  private static String command(Point point) {
    return String.format(
        Locale.ROOT,
        "execute in %s run tp @s %.3f %.3f %.3f",
        point.dimension,
        point.x,
        point.y,
        point.z);
  }

  private static void expectFailure(
      GameTestHelper h, Administration app, ServerPlayer p, Point point, String error)
      throws Exception {
    var before = Teleports.point(p);
    try {
      app.server
          .getCommands()
          .getDispatcher()
          .execute(command(point), p.createCommandSourceStack());
      h.fail("Expected failed native teleport: " + error);
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
      h.assertTrue(
          ex.getRawMessage().getString().contains(error),
          "Specific native teleport failure: " + error);
    }
    h.assertTrue(
        !BackHistory.different(before, Teleports.point(p)),
        "Rejected teleport preserves the player position");
  }

  static void verify(GameTestHelper h, Administration app) throws Exception {
    var regions = app.regions.copy();
    var saved = app.storage.copy();
    var admin = player(h, "chunkAdmin");
    var ordinary = player(h, "chunkUser");
    var fakeProfile = new GameProfile(UUID.randomUUID(), "chunkAutomation");
    try {
      app.regions.edit(List.of(), app.server, app.settings, app.compat);
      app.server
          .getPlayerList()
          .getOps()
          .add(
              new net.minecraft.server.players.ServerOpListEntry(admin.getGameProfile(), 4, false));
      admin.setGameMode(GameType.CREATIVE);
      admin.setPos(h.absoluteVec(new Vec3(4.5, 1, 4.5)));
      var origin = Teleports.point(admin);
      ordinary.setPos(h.absoluteVec(new Vec3(5.5, 1, 4.5)));
      var level = h.getLevel();
      var unknown = fresh(h, level, 128);
      var destination = point(level, unknown);
      h.assertTrue(
          app.teleports.validate(ordinary, destination).contains("unloaded")
              && !level.hasChunk(unknown.x, unknown.z),
          "Ordinary validation cannot load or generate a destination");
      app.teleports.cooldowns.remove(ordinary.getUUID());
      app.teleports.teleport(ordinary, destination, false);
      var queued = app.teleports.loading.get(ordinary.getUUID());
      h.assertTrue(queued != null, "Ordinary FFA travel still uses the saved-chunk queue");
      queued.future().join();
      app.teleports.tick(new ServerTickEvent.Post(() -> true, app.server));
      h.assertTrue(
          !app.teleports.loading.containsKey(ordinary.getUUID())
              && !level.hasChunk(unknown.x, unknown.z)
              && ordinary
                      .position()
                      .distanceToSqr(new Vec3(destination.x, destination.y, destination.z))
                  > 1,
          "The real saved-chunk read refuses ungenerated ordinary-player travel");
      var fake = FakePlayerFactory.get(level, fakeProfile);
      app.server
          .getPlayerList()
          .getOps()
          .add(new net.minecraft.server.players.ServerOpListEntry(fakeProfile, 4, false));
      h.assertTrue(
          app.teleports.validate(fake, destination).contains("unloaded")
              && !level.hasChunk(unknown.x, unknown.z),
          "An OP-named automation fake player does not gain terrain-generation authority");

      // Invalid coordinates/bounds are refused before the first chunk request.
      var invalid =
          new Point(
              destination.dimension, destination.x, level.getMaxBuildHeight(), destination.z, 0, 0);
      h.assertTrue(
          app.teleports.validate(admin, invalid).contains("bounds")
              && !level.hasChunk(unknown.x, unknown.z),
          "Administrator bounds rejection cannot generate an invalid destination");
      app.server
          .getCommands()
          .getDispatcher()
          .execute(command(destination), admin.createCommandSourceStack());
      h.assertTrue(
          level.hasChunk(unknown.x, unknown.z)
              && !BackHistory.different(destination, Teleports.point(admin)),
          "A real native OP teleport generates and reaches an unused destination");
      h.assertTrue(
          !app.teleports.loading.containsKey(admin.getUUID()),
          "Admin travel does not enter the ordinary saved-only queue");

      var savedPos = fresh(h, level, 256);
      var savedPoint = point(level, savedPos);
      save(level, savedPos, savedPoint, false, false);
      h.assertTrue(
          !level.hasChunk(savedPos.x, savedPos.z)
              && level
                  .getChunkSource()
                  .chunkMap
                  .read(savedPos)
                  .join()
                  .orElseThrow()
                  .getString("Status")
                  .equals("minecraft:full"),
          "Disk-backed fixture is fully saved but absent from the live chunk cache");
      app.teleports.teleport(admin, savedPoint, false);
      h.assertTrue(
          !BackHistory.different(savedPoint, Teleports.point(admin))
              && level
                  .getBlockState(
                      BlockPos.containing(savedPoint.x, savedPoint.y, savedPoint.z).below())
                  .is(Blocks.GOLD_BLOCK),
          "FFA travel loads existing saved terrain and preserves its custom blocks");

      var other = app.server.getLevel(Level.END);
      var otherPos = fresh(h, other, 384);
      var otherPoint = point(other, otherPos);
      app.server
          .getCommands()
          .getDispatcher()
          .execute(command(otherPoint), admin.createCommandSourceStack());
      h.assertTrue(
          admin.serverLevel() == other
              && other.hasChunk(otherPos.x, otherPos.z)
              && !BackHistory.different(otherPoint, Teleports.point(admin)),
          "Cross-dimension admin TP generates in the destination world");

      var edgeChunk = fresh(h, level, 512);
      var edge = point(level, edgeChunk);
      edge.x = edgeChunk.getMinBlockX() + .1;
      edge.z = edgeChunk.getMinBlockZ() + .1;
      h.assertTrue(
          app.teleports.validate(admin, edge) == null
              && level.hasChunk(edgeChunk.x - 1, edgeChunk.z - 1),
          "Chunk-edge validation loads the neighboring collision footprint");

      var blockedPos = fresh(h, level, 640);
      var blocked = point(level, blockedPos);
      save(level, blockedPos, blocked, false, true);
      expectFailure(h, app, admin, blocked, "obstructed");
      var hotPos = fresh(h, level, 768);
      var hot = point(level, hotPos);
      save(level, hotPos, hot, true, false);
      expectFailure(h, app, admin, hot, "hazard");
      h.assertTrue(
          level
              .getBlockState(BlockPos.containing(hot.x, hot.y, hot.z).below())
              .is(Blocks.MAGMA_BLOCK),
          "Rejected unsafe loads preserve saved block data");
      app.teleports.teleport(admin, origin, true);
      app.server.getPlayerList().deop(admin.getGameProfile());
      var revoked = fresh(h, level, 896);
      h.assertTrue(
          app.teleports.validate(admin, point(level, revoked)).contains("unloaded")
              && !level.hasChunk(revoked.x, revoked.z),
          "Revoking administration removes chunk-generation privilege immediately");
    } finally {
      app.server.getPlayerList().getOps().remove(fakeProfile);
      app.server.getPlayerList().deop(admin.getGameProfile());
      app.teleports.cancel(admin, false);
      app.teleports.cancel(ordinary, false);
      app.server.getPlayerList().remove(admin);
      app.server.getPlayerList().remove(ordinary);
      app.regions.edit(regions, app.server, app.settings, app.compat);
      app.storage.commit(saved);
    }
  }
}
