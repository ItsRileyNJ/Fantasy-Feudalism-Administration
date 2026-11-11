package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Teleports {
  final Administration app;

  record Request(UUID from, UUID to, boolean here, long expiry, UUID token) {
    Request(UUID from, UUID to, boolean here, long expiry) {
      this(from, to, here, expiry, UUID.randomUUID());
    }
  }

  record Waiting(Point point, Point origin, long due) {}

  record Loading(
      Point point,
      Point origin,
      boolean instant,
      long expiry,
      java.util.concurrent.CompletableFuture<Optional<net.minecraft.nbt.CompoundTag>> future) {}

  final Map<UUID, Loading> loading = new HashMap<>();
  final Map<UUID, Request> requests = new HashMap<>();
  final Map<UUID, Waiting> waiting = new HashMap<>();
  final Map<UUID, Long> cooldowns = new HashMap<>(),
      requestCooldowns = new HashMap<>(),
      portalCooldowns = new HashMap<>();

  public Teleports(Administration app) {
    this.app = app;
  }

  static long now() {
    return System.nanoTime() / 1_000_000;
  }

  public boolean busy() {
    return !waiting.isEmpty() || !loading.isEmpty();
  }

  public static Point point(ServerPlayer p) {
    return new Point(
        p.serverLevel().dimension().location().toString(),
        p.getX(),
        p.getY(),
        p.getZ(),
        p.getYRot(),
        p.getXRot());
  }

  public ServerLevel level(Point p) {
    ResourceLocation id = ResourceLocation.tryParse(p.dimension);
    return id == null ? null : app.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
  }

  public String validate(ServerPlayer p, Point dest) {
    if (dest == null) return "Destination is not configured.";
    if (!Double.isFinite(dest.x)
        || !Double.isFinite(dest.y)
        || !Double.isFinite(dest.z)
        || !Float.isFinite(dest.yaw)
        || !Float.isFinite(dest.pitch)) return "Destination contains invalid coordinates.";
    ServerLevel level = level(dest);
    if (level == null) return "Destination dimension is unavailable.";
    BlockPos pos = BlockPos.containing(dest.x, dest.y, dest.z);
    if (level.isOutsideBuildHeight(pos)
        || level.isOutsideBuildHeight(pos.above())
        || !level.getWorldBorder().isWithinBounds(pos))
      return "Destination is outside world bounds.";
    if (!level.hasChunkAt(pos))
      return "Destination chunk is unloaded; have an administrator load it before travel.";
    AABB box = p.getBoundingBox().move(dest.x - p.getX(), dest.y - p.getY(), dest.z - p.getZ());
    if (level.getBlockCollisions(p, box).iterator().hasNext()) return "Destination is obstructed.";
    for (BlockPos b : BlockPos.betweenClosed(pos.below(), pos.above())) {
      var state = level.getBlockState(b);
      if (!state.getFluidState().isEmpty()
          || state.getBlock() instanceof BaseFireBlock
          || state.is(Blocks.MAGMA_BLOCK)
          || state.is(Blocks.CACTUS)
          || state.is(Blocks.POWDER_SNOW)
          || state.is(Blocks.SWEET_BERRY_BUSH)) return "Destination contains a hazard.";
    }
    if (!(app.compat.admin(p) && p.getAbilities().mayfly)
        && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty())
      return "Destination has no safe support.";
    if (!app.compat.admin(p)) {
      if (!app.regions.allowed(p.serverLevel(), p.blockPosition(), Flag.TELEPORT_OUT, app.settings)
          || !app.regions.allowed(level, pos, Flag.TELEPORT_IN, app.settings))
        return app.settings.denied;
      Region region = app.regions.at(level, pos);
      if (region != null && !app.compat.access(p, region.access, level, pos))
        return app.settings.denied;
    }
    return null;
  }

  public void teleport(ServerPlayer p, Point destination, boolean instant) throws Exception {
    if (waiting.containsKey(p.getUUID()) || loading.containsKey(p.getUUID()))
      throw new IllegalArgumentException("A teleport is already pending.");
    if (app.storage.state().trades.containsKey(p.getUUID().toString()))
      throw new IllegalArgumentException(
          "Resolve the pending shop transaction before teleporting.");
    if (!app.compat.admin(p) && cooldowns.getOrDefault(p.getUUID(), 0L) > now())
      throw new IllegalArgumentException("Teleport cooldown is active.");
    String error = validate(p, destination);
    if (error != null && error.startsWith("Destination chunk is unloaded")) {
      if (loading.size() >= app.settings.queuedLoads)
        throw new IllegalArgumentException("Destination loading is busy; try again shortly.");
      ServerLevel target = level(destination);
      net.minecraft.world.level.ChunkPos chunk =
          new net.minecraft.world.level.ChunkPos(
              BlockPos.containing(destination.x, destination.y, destination.z));
      var future = target.getChunkSource().chunkMap.read(chunk);
      loading.put(
          p.getUUID(),
          new Loading(
              destination, point(p), instant, now() + app.settings.loadTimeout * 1000L, future));
      tell(p, "Loading saved destination...");
      return;
    }
    if (error != null) throw new IllegalArgumentException(error);
    if (!instant && !app.compat.admin(p) && app.settings.warmup > 0) {
      waiting.put(
          p.getUUID(), new Waiting(destination, point(p), now() + app.settings.warmup * 1000L));
      tell(p, "Teleporting in " + app.settings.warmup + " seconds.");
      return;
    }
    execute(p, destination);
  }

  private void execute(ServerPlayer p, Point dest) throws Exception {
    String error = validate(p, dest);
    if (error != null) throw new IllegalArgumentException(error);
    Point old = point(p);
    p.closeContainer();
    p.teleportTo(level(dest), dest.x, dest.y, dest.z, dest.yaw, dest.pitch);
    // External protection mods may veto transit. Do not record successful arrival in that case.
    if (!point(p).dimension.equals(dest.dimension)
        || p.position().distanceToSqr(new Vec3(dest.x, dest.y, dest.z)) > 0.01)
      throw new IllegalArgumentException("Teleport was declined by another server rule.");
    app.storage.update(s -> s.players.get(p.getUUID().toString()).back = old);
    cooldowns.put(p.getUUID(), now() + app.settings.cooldown * 1000L);
    portalCooldowns.put(p.getUUID(), now() + app.settings.portalCooldown * 1000L);
    app.protection.update(p);
    tell(p, "Teleported.");
  }

  public void warp(ServerPlayer p, String name) throws Exception {
    Warp w = app.storage.state().warps.get(name);
    if (w == null) throw new IllegalArgumentException("Unknown warp: " + name);
    ServerLevel l = level(w.point);
    if (l == null
        || !app.compat.access(p, w.access, l, BlockPos.containing(w.point.x, w.point.y, w.point.z)))
      throw new IllegalArgumentException(app.settings.denied);
    teleport(p, w.point, false);
  }

  public void request(ServerPlayer from, ServerPlayer to, boolean here) {
    if (Vanish.hidden(from.getUUID()) || !Vanish.canSee(from, to))
      throw new IllegalArgumentException("Player is offline or messaging is unavailable");
    if (from == to) throw new IllegalArgumentException("Choose another player.");
    PlayerData target = app.storage.player(to.getUUID());
    if (target == null
        || !target.requestsEnabled
        || target.ignored.contains(from.getUUID().toString()))
      throw new IllegalArgumentException("That player is not accepting requests.");
    if (!app.compat.admin(from) && requestCooldowns.getOrDefault(from.getUUID(), 0L) > now())
      throw new IllegalArgumentException("Request cooldown is active.");
    Request existing = requests.get(to.getUUID());
    if (existing != null && existing.expiry > now())
      throw new IllegalArgumentException("That player already has a pending request.");
    requests.entrySet().removeIf(e -> e.getValue().from.equals(from.getUUID()));
    Request request =
        new Request(from.getUUID(), to.getUUID(), here, now() + app.settings.requestExpiry * 1000L);
    requests.put(to.getUUID(), request);
    requestCooldowns.put(from.getUUID(), now() + app.settings.requestCooldown * 1000L);
    tell(
        to,
        from.getGameProfile().getName()
            + (here ? " requests that you teleport to them." : " wants to teleport to you."));
    to.sendSystemMessage(
        Component.literal("[ACCEPT] ")
            .withStyle(
                s ->
                    s.withColor(net.minecraft.ChatFormatting.GREEN)
                        .withClickEvent(
                            new ClickEvent(
                                ClickEvent.Action.RUN_COMMAND, "/ffa tpaccept " + request.token)))
            .append(
                Component.literal("[DECLINE]")
                    .withStyle(
                        s ->
                            s.withColor(net.minecraft.ChatFormatting.RED)
                                .withClickEvent(
                                    new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        "/ffa tpdeny " + request.token)))));
    tell(from, "Request sent.");
  }

  public void answer(ServerPlayer p, boolean accept) throws Exception {
    answer(p, accept, null);
  }

  public void answer(ServerPlayer p, boolean accept, UUID token) throws Exception {
    Request r = requests.get(p.getUUID());
    if (r == null || r.expiry < now()) {
      requests.remove(p.getUUID());
      throw new IllegalArgumentException("No unexpired request.");
    }
    if (token != null && !token.equals(r.token))
      throw new IllegalArgumentException(
          "That request button is stale. Use the current request message.");
    requests.remove(p.getUUID());
    ServerPlayer from = app.server.getPlayerList().getPlayer(r.from);
    if (from == null || !Vanish.canSee(p, from))
      throw new IllegalArgumentException("Requesting player is offline.");
    if (!accept) {
      tell(from, "Request declined.");
      return;
    }
    PlayerData prefs = app.storage.player(p.getUUID());
    if (!prefs.requestsEnabled || prefs.ignored.contains(from.getUUID().toString()))
      throw new IllegalArgumentException("Request blocked by current privacy preferences.");
    teleport(r.here ? p : from, point(r.here ? from : p), false);
  }

  public void cancel(ServerPlayer p) {
    boolean removed = waiting.remove(p.getUUID()) != null;
    removed |= loading.remove(p.getUUID()) != null;
    removed |= requests.entrySet().removeIf(e -> e.getValue().from.equals(p.getUUID()));
    tell(p, removed ? "Cancelled." : "Nothing pending.");
  }

  public void home(ServerPlayer p, String name, boolean set, boolean delete) throws Exception {
    if (!set && !delete) {
      Point pt = app.storage.player(p.getUUID()).homes.get(name);
      if (pt == null) throw new IllegalArgumentException("Unknown home.");
      teleport(p, pt, false);
      return;
    }
    Regions.checkId(name);
    if (set) {
      if (!app.compat.admin(p) && !app.settings.homeDimensions.contains(point(p).dimension))
        throw new IllegalArgumentException("Homes are disabled in this dimension.");
      if (!app.compat.admin(p)
          && !app.regions.allowed(
              p.serverLevel(), p.blockPosition(), Flag.HOME_CREATION, app.settings))
        throw new IllegalArgumentException("Homes cannot be created here.");
      if (!app.compat.admin(p)
          && !app.storage.player(p.getUUID()).homes.containsKey(name)
          && app.storage.player(p.getUUID()).homes.size()
              >= app.compat.homeLimit(p, app.settings.homeMax)
          && !app.compat.permission(p, "ffa.homes.unlimited"))
        throw new IllegalArgumentException("Home limit reached.");
      String error = validate(p, point(p));
      if (error != null) throw new IllegalArgumentException(error);
    } else if (!app.storage.player(p.getUUID()).homes.containsKey(name))
      throw new IllegalArgumentException("Unknown home.");
    app.storage.update(
        s -> {
          var homes = s.players.get(p.getUUID().toString()).homes;
          if (delete) homes.remove(name);
          else homes.put(name, point(p));
        });
    tell(p, "Home " + name + (delete ? " deleted." : " saved."));
  }

  public void random(ServerPlayer p) throws Exception {
    if (!app.settings.rtpDimensions.contains(point(p).dimension))
      throw new IllegalArgumentException("Random teleport disabled in this dimension.");
    for (int i = 0; i < app.settings.rtpAttempts; i++) {
      int
          x =
              p.blockPosition().getX()
                  + p.getRandom().nextInt(app.settings.rtpRadius * 2 + 1)
                  - app.settings.rtpRadius,
          z =
              p.blockPosition().getZ()
                  + p.getRandom().nextInt(app.settings.rtpRadius * 2 + 1)
                  - app.settings.rtpRadius;
      var pos = new BlockPos(x, p.blockPosition().getY(), z);
      if (!p.serverLevel().hasChunkAt(pos)) continue;
      int y = p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
      Point d = new Point(point(p).dimension, x + .5, y, z + .5, p.getYRot(), p.getXRot());
      if (app.regions.protectedAt(p.serverLevel(), BlockPos.containing(d.x, d.y, d.z), app.settings)
          || app.compat.inSettlement(p.serverLevel(), BlockPos.containing(d.x, d.y, d.z))
          || validate(p, d) != null) continue;
      teleport(p, d, false);
      return;
    }
    throw new IllegalArgumentException(
        "No safe loaded wilderness destination found within the search limit.");
  }

  @SubscribeEvent
  public void tick(ServerTickEvent.Post e) {
    long time = now();
    for (var entry : new ArrayList<>(loading.entrySet())) {
      ServerPlayer player = app.server.getPlayerList().getPlayer(entry.getKey());
      Loading task = entry.getValue();
      if (player == null) {
        loading.remove(entry.getKey());
        continue;
      }
      if (time > task.expiry) {
        loading.remove(entry.getKey());
        tell(player, "Destination loading timed out.");
        continue;
      }
      if (!task.future.isDone()) continue;
      loading.remove(entry.getKey());
      try {
        if (app.settings.cancelMove
            && (!point(player).dimension.equals(task.origin.dimension)
                || player
                        .position()
                        .distanceToSqr(new Vec3(task.origin.x, task.origin.y, task.origin.z))
                    > .01))
          throw new IllegalArgumentException("Teleport cancelled because you moved.");
        var saved = task.future.join();
        if (saved.isEmpty()
            || !Set.of("minecraft:full", "full").contains(saved.get().getString("Status")))
          throw new IllegalArgumentException(
              "Destination has no fully generated saved chunk; refusing terrain generation.");
        ServerLevel target = level(task.point);
        if (target == null)
          throw new IllegalArgumentException("Destination dimension no longer exists.");
        target.getChunk(BlockPos.containing(task.point.x, task.point.y, task.point.z));
        teleport(player, task.point, task.instant);
      } catch (Exception ex) {
        tell(player, ex.getMessage());
      }
      break;
    }
    var due = new ArrayList<>(waiting.entrySet());
    for (var entry : due) {
      ServerPlayer p = app.server.getPlayerList().getPlayer(entry.getKey());
      Waiting w = entry.getValue();
      if (p == null) {
        waiting.remove(entry.getKey());
        continue;
      }
      if (app.settings.cancelMove
          && (!point(p).dimension.equals(w.origin.dimension)
              || p.position().distanceToSqr(new Vec3(w.origin.x, w.origin.y, w.origin.z)) > .01)) {
        waiting.remove(entry.getKey());
        tell(p, "Teleport cancelled because you moved.");
        continue;
      }
      if (time >= w.due) {
        waiting.remove(entry.getKey());
        try {
          execute(p, w.point);
        } catch (Exception ex) {
          tell(p, ex.getMessage());
        }
      }
    }
    if (app.server.getTickCount() % 200 == 0)
      requests.entrySet().removeIf(x -> x.getValue().expiry < time);
  }

  @SubscribeEvent
  public void logout(PlayerEvent.PlayerLoggedOutEvent e) {
    UUID id = e.getEntity().getUUID();
    waiting.remove(id);
    loading.remove(id);
    requests.entrySet().removeIf(x -> x.getValue().from.equals(id) || x.getValue().to.equals(id));
    cooldowns.remove(id);
    requestCooldowns.remove(id);
    portalCooldowns.remove(id);
  }

  @SubscribeEvent
  public void respawn(PlayerRespawnPositionEvent e) {
    if (e.isFromEndFight()
        || app.settings.respawn.equals("vanilla")
        || app.storage.state().spawn == null) return;
    if (app.settings.respawn.equals("bed_first")
        && !e.getOriginalDimensionTransition().missingRespawnBlock()
        && ((ServerPlayer) e.getEntity()).getRespawnPosition() != null) return;
    ServerPlayer p = (ServerPlayer) e.getEntity();
    Point d = app.storage.state().spawn;
    if (validate(p, d) != null) {
      Administration.LOG.warn(
          "FFA respawn fallback unavailable; retaining vanilla destination for {}", p.getUUID());
      return;
    }
    e.setDimensionTransition(
        new DimensionTransition(
            level(d),
            new Vec3(d.x, d.y, d.z),
            Vec3.ZERO,
            d.yaw,
            d.pitch,
            DimensionTransition.DO_NOTHING));
    e.setCopyOriginalSpawnPosition(true);
  }

  public static void tell(ServerPlayer p, String message) {
    p.sendSystemMessage(Component.literal(message == null ? "Operation failed." : message));
  }
}
