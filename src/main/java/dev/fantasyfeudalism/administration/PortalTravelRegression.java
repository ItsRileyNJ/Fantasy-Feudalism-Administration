package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;
import static dev.fantasyfeudalism.administration.RegressionTests.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.*;

/** Exercise body contact, cross-world travel, saved-chunk waits and ordinary command warmups. */
final class PortalTravelRegression {
  static void verify(GameTestHelper h, Administration app) throws Exception {
    var state = app.storage.copy();
    var regions = app.regions.copy();
    boolean enabled = app.settings.portals, cancelMove = app.settings.cancelMove;
    int warmup = app.settings.warmup;
    var p = player(h, "portalWalker");
    var source = h.getLevel();
    var destination = app.server.getLevel(Level.NETHER);
    var blocks = new HashMap<ServerLevel, Map<BlockPos, BlockState>>();
    try {
      app.settings.portals = app.settings.cancelMove = true;
      app.settings.warmup = 10;
      app.regions.edit(List.of(), app.server, app.settings, app.compat);
      p.setGameMode(GameType.SURVIVAL);
      int x = ((h.absolutePos(BlockPos.ZERO).getX() >> 4) + 1) << 4;
      int y = h.absolutePos(new BlockPos(0, 1, 0)).getY();
      int z = h.absolutePos(new BlockPos(0, 1, 4)).getZ();
      Portal portal = new Portal();
      portal.id = "walk_portal";
      portal.dimension = source.dimension().location().toString();
      portal.minX = portal.maxX = x;
      portal.minY = y;
      portal.maxY = y + 2;
      portal.minZ = z;
      portal.maxZ = z + 2;
      portal.warp = "walk_warp";
      Point target =
          new Point(destination.dimension().location().toString(), x + 8.5, 80, z + .5, 0, 0);
      floor(destination, target, blocks);
      Point home = new Point(portal.dimension, x - 1.5, y, z + .5, 0, 0);
      floor(source, home, blocks);
      Portal arrival = new Portal();
      arrival.id = "arrival_portal";
      arrival.dimension = target.dimension;
      arrival.warp = "return_warp";
      arrival.minX = arrival.maxX = (int) Math.floor(target.x);
      arrival.minY = (int) target.y;
      arrival.maxY = arrival.minY + 2;
      arrival.minZ = arrival.maxZ = (int) Math.floor(target.z);
      app.storage.update(
          s -> {
            s.portals.put(portal.id, portal);
            s.portals.put(arrival.id, arrival);
            Warp w = new Warp();
            w.point = target;
            s.warps.put(portal.warp, w);
            Warp back = new Warp();
            back.point = home;
            s.warps.put(arrival.warp, back);
          });
      Portals.reindex(app);
      double[][] entries = {
        {x - .8, z + .5, x - .15, z + .5}, {x + 1.8, z + .5, x + 1.15, z + .5},
        {x + .5, z - .8, x + .5, z - .15}, {x + .5, z + 3.8, x + .5, z + 3.15}
      };
      for (int i = 0; i < entries.length; i++) {
        var edge = entries[i];
        Point outside = new Point(portal.dimension, edge[0], y, edge[1], i * 91, 0);
        floor(source, outside, blocks);
        p.teleportTo(source, outside.x, outside.y, outside.z, outside.yaw, 0);
        reset(app, p);
        app.protection.tick(new PlayerTickEvent.Post(p));
        BlockPos before = p.blockPosition();
        p.setPos(edge[2], y, edge[3]);
        p.setDeltaMovement(edge[2] - edge[0], 0, edge[3] - edge[1]);
        h.assertTrue(
            p.blockPosition().equals(before),
            "Body enters from face " + i + " without changing feet block");
        if (i == 0)
          h.assertTrue(
              p.chunkPosition().x != portal.minX >> 4, "Player's feet are in the adjacent chunk");
        app.protection.tick(new PlayerTickEvent.Post(p));
        h.assertTrue(
            p.serverLevel() == destination && !BackHistory.different(target, Teleports.point(p)),
            "Moving body contact triggers a cross-dimension portal from face " + i);
        h.assertFalse(
            app.teleports.busyFor(p), "Portal does not inherit the ten-second command warmup");
        Portals.check(app, p);
        app.teleports.portalCooldowns.remove(p.getUUID());
        app.teleports.cooldowns.remove(p.getUUID());
        Portals.check(app, p);
        h.assertTrue(
            p.serverLevel() == destination,
            "Remaining in an arrival portal never bounces after cooldown expiry");
      }
      // A queue must originate through the real portal path while its target is unloaded.
      Point unloaded = new Point(target.dimension, target.x + 512, target.y, target.z, 0, 0);
      h.assertFalse(
          destination.hasChunkAt(BlockPos.containing(unloaded.x, unloaded.y, unloaded.z)),
          "Saved-load fixture starts unloaded");
      app.storage.update(s -> s.warps.get(portal.warp).point = unloaded);
      enter(p, source, portal, app);
      var queued = app.teleports.loading.get(p.getUUID());
      h.assertTrue(
          queued != null && queued.portal() != null && queued.instant(),
          "Unloaded portal destination records its portal context and skips command warmup");
      var future = new CompletableFuture<Optional<CompoundTag>>();
      app.teleports.loading.put(
          p.getUUID(),
          new Teleports.Loading(
              queued.point(),
              queued.origin(),
              queued.instant(),
              queued.expiry(),
              future,
              queued.portal()));
      p.setPos(home.x - 2, home.y, home.z);
      app.teleports.tick(new ServerTickEvent.Post(() -> true, app.server));
      h.assertTrue(
          app.teleports.loading.containsKey(p.getUUID()),
          "Walking past the entrance does not cancel a portal chunk wait");
      floor(destination, unloaded, blocks);
      future.complete(Optional.of(fullChunk()));
      app.teleports.tick(new ServerTickEvent.Post(() -> true, app.server));
      h.assertTrue(
          p.serverLevel() == destination
              && !BackHistory.different(unloaded, Teleports.point(p))
              && !app.teleports.busyFor(p),
          "Portal travel completes across dimensions after movement and saved-chunk readiness");

      Point missing = new Point(target.dimension, target.x + 1024, target.y, target.z, 0, 0);
      app.storage.update(s -> s.warps.get(portal.warp).point = missing);
      enter(p, source, portal, app);
      queued = app.teleports.loading.get(p.getUUID());
      h.assertTrue(queued != null, "Missing saved destination is checked through the queue");
      app.teleports.loading.put(
          p.getUUID(),
          new Teleports.Loading(
              queued.point(),
              queued.origin(),
              true,
              queued.expiry(),
              CompletableFuture.completedFuture(Optional.empty()),
              queued.portal()));
      app.teleports.tick(new ServerTickEvent.Post(() -> true, app.server));
      h.assertTrue(
          p.serverLevel() == source
              && !destination.hasChunkAt(BlockPos.containing(missing.x, missing.y, missing.z)),
          "An unsaved destination still refuses terrain generation");

      // Permission checks run again at completion, against the actual entry location.
      app.storage.update(s -> s.warps.get(portal.warp).point = unloaded);
      enter(p, source, portal, app);
      // Loaded portals finish immediately; create a controlled pending read with the same context.
      p.teleportTo(source, home.x, home.y, home.z, 0, 0);
      reset(app, p);
      var transit =
          new Teleports.PortalTransit(portal.id, portal.warp, Teleports.point(p), unloaded);
      app.teleports.loading.put(p.getUUID(), pending(unloaded, p, transit));
      app.storage.update(s -> s.warps.get(portal.warp).access.mode = "admin");
      app.teleports.tick(new ServerTickEvent.Post(() -> true, app.server));
      h.assertTrue(
          p.serverLevel() == source && !app.teleports.busyFor(p),
          "Revoked warp access cancels pending portal travel");
      app.storage.update(s -> s.warps.get(portal.warp).access.mode = "public");
      Region locked = new Region();
      locked.id = locked.name = "portal_lock";
      locked.kind = Kind.SPAWN;
      locked.dimension = portal.dimension;
      locked.minX = x - 3;
      locked.maxX = x + 2;
      locked.minZ = z - 1;
      locked.maxZ = z + 3;
      locked.settlementOverride = true;
      locked.flags.put(Flag.TELEPORT_OUT, false);
      app.regions.edit(List.of(locked), app.server, app.settings, app.compat);
      app.teleports.loading.put(p.getUUID(), pending(unloaded, p, transit));
      p.setPos(x - 5, y, z + .5);
      app.teleports.tick(new ServerTickEvent.Post(() -> true, app.server));
      h.assertTrue(
          p.serverLevel() == source && !app.teleports.busyFor(p),
          "Moving out of a denied entry region cannot evade the original portal-source rules");
      app.regions.edit(List.of(), app.server, app.settings, app.compat);

      // Ordinary warp commands retain stationary warmups and stationary saved-chunk waits.
      reset(app, p);
      app.teleports.warp(p, portal.warp);
      h.assertTrue(
          app.teleports.waiting.containsKey(p.getUUID()),
          "Typed warp still uses configured command warmup");
      p.setPos(p.getX() + .3, p.getY(), p.getZ());
      app.teleports.tick(new ServerTickEvent.Post(() -> true, app.server));
      h.assertTrue(
          p.serverLevel() == source && !app.teleports.busyFor(p),
          "Movement still cancels an ordinary command warmup");
      app.teleports.loading.put(
          p.getUUID(),
          new Teleports.Loading(
              unloaded,
              Teleports.point(p),
              false,
              Teleports.now() + 10000,
              CompletableFuture.completedFuture(Optional.of(fullChunk()))));
      p.setPos(p.getX() + .3, p.getY(), p.getZ());
      app.teleports.tick(new ServerTickEvent.Post(() -> true, app.server));
      h.assertTrue(
          p.serverLevel() == source && !app.teleports.busyFor(p),
          "Movement still cancels an ordinary command's chunk wait");
    } finally {
      app.teleports.cancel(p, false);
      app.server.getPlayerList().remove(p);
      app.settings.portals = enabled;
      app.settings.cancelMove = cancelMove;
      app.settings.warmup = warmup;
      for (var level : blocks.entrySet())
        for (var block : level.getValue().entrySet())
          level.getKey().setBlock(block.getKey(), block.getValue(), 3);
      app.storage.commit(state);
      app.regions.edit(regions, app.server, app.settings, app.compat);
      Portals.reindex(app);
    }
  }

  private static void reset(Administration app, ServerPlayer p) {
    app.teleports.cancel(p, false);
    app.teleports.cooldowns.remove(p.getUUID());
    app.teleports.portalCooldowns.remove(p.getUUID());
    app.teleports.portalContacts.remove(p.getUUID());
  }

  private static void enter(ServerPlayer p, ServerLevel source, Portal portal, Administration app) {
    p.teleportTo(source, portal.minX - .8, portal.minY, portal.minZ + .5, 0, 0);
    reset(app, p);
    Portals.check(app, p);
    p.setPos(portal.minX - .15, portal.minY, portal.minZ + .5);
    Portals.check(app, p);
  }

  private static Teleports.Loading pending(
      Point target, ServerPlayer p, Teleports.PortalTransit transit) {
    return new Teleports.Loading(
        target,
        Teleports.point(p),
        true,
        Teleports.now() + 10000,
        CompletableFuture.completedFuture(Optional.of(fullChunk())),
        transit);
  }

  private static CompoundTag fullChunk() {
    var tag = new CompoundTag();
    tag.putString("Status", "minecraft:full");
    return tag;
  }

  private static void floor(
      ServerLevel level, Point point, Map<ServerLevel, Map<BlockPos, BlockState>> before) {
    var pos = BlockPos.containing(point.x, point.y, point.z);
    level.getChunk(pos);
    var blocks = before.computeIfAbsent(level, k -> new HashMap<>());
    for (var b : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 2, 1))) {
      blocks.putIfAbsent(b.immutable(), level.getBlockState(b));
      level.setBlock(
          b,
          b.getY() == pos.getY() - 1
              ? Blocks.STONE.defaultBlockState()
              : Blocks.AIR.defaultBlockState(),
          3);
    }
  }
}
