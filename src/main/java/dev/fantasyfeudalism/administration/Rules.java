package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;

import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Durable acceptance, independent of rank permissions and ordinary teleport cooldowns. */
public final class Rules {
  private final Administration app;
  private final Set<UUID> moving = new HashSet<>();
  private final Map<UUID, Long> prompts = new HashMap<>();
  private static final ThreadLocal<Integer> TRUSTED = ThreadLocal.withInitial(() -> 0);

  public Rules(Administration app) {
    this.app = app;
  }

  public static final class Delivery implements AutoCloseable {
    private final boolean trusted;

    private Delivery(boolean trusted) {
      this.trusted = trusted;
      if (trusted) TRUSTED.set(TRUSTED.get() + 1);
    }

    @Override
    public void close() {
      if (trusted) {
        int next = TRUSTED.get() - 1;
        if (next == 0) TRUSTED.remove();
        else TRUSTED.set(next);
      }
    }
  }

  public static Delivery from(ServerPlayer sender) {
    var a = Administration.INSTANCE;
    return new Delivery(sender != null && a != null && a.compat != null && a.compat.admin(sender));
  }

  public boolean pending(ServerPlayer player) {
    if (app.server == null || app.storage == null || app.compat.admin(player)) return false;
    var data = app.storage.player(player.getUUID());
    return app.storage.state().rulesRoom.enabled && (data == null || !data.rulesAccepted);
  }

  public boolean forcing(ServerPlayer player) {
    return moving.contains(player.getUUID());
  }

  public boolean inside(Point point) {
    Region r = room();
    return r != null && r.dimension.equals(point.dimension) && r.contains(point.x, point.z);
  }

  private Region room() {
    String id = app.storage.state().rulesRoom.region;
    return app.regions.all().stream()
        .filter(r -> r.id.equalsIgnoreCase(id))
        .findFirst()
        .orElse(null);
  }

  public static void validate(State state, List<Region> regions) {
    if (state.rulesRoom == null)
      throw new IllegalArgumentException("Missing rules room configuration.");
    if (!state.rulesRoom.enabled) return;
    Region room =
        regions.stream()
            .filter(r -> r.id.equalsIgnoreCase(state.rulesRoom.region))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Disable the rules room before deleting or renaming its region."));
    Point spawn = state.firstSpawn;
    if (room.kind != Kind.CHILD
        || spawn == null
        || !room.dimension.equals(spawn.dimension)
        || !room.contains(spawn.x, spawn.z))
      throw new IllegalArgumentException(
          "The rules room must be a child region containing the first-join spawn.");
  }

  public void enable(boolean value) throws Exception {
    State candidate = app.storage.copy();
    candidate.rulesRoom.enabled = value;
    candidate.rulesRoom.revision = UUID.randomUUID().toString();
    validate(candidate, app.regions.all());
    if (value) checkSpawn(candidate.firstSpawn);
    app.storage.commit(candidate);
    for (var player : app.server.getPlayerList().getPlayers()) {
      if (pending(player)) {
        app.teleports.cancel(player, false);
        app.whispers.cancelFor(player.getUUID());
        enforce(player);
        prompt(player, true);
      }
      app.server.getCommands().sendCommands(player);
    }
  }

  public void setRegion(String name) throws Exception {
    if (app.storage.state().rulesRoom.enabled)
      throw new IllegalArgumentException("Disable the rules room before changing its region.");
    Region r = app.regions.get(name);
    if (r.kind != Kind.CHILD)
      throw new IllegalArgumentException("Choose a child region inside spawn.");
    app.storage.update(s -> s.rulesRoom.region = r.id);
  }

  public void setSpawn(ServerPlayer admin) throws Exception {
    Point point = Teleports.point(admin);
    checkSpawn(point);
    State candidate = app.storage.copy();
    candidate.firstSpawn = point;
    validate(candidate, app.regions.all());
    app.storage.commit(candidate);
  }

  private void checkSpawn(Point point) {
    if (point == null || app.teleports.level(point) == null)
      throw new IllegalArgumentException("Set a first-join spawn first with /ffa setfirstspawn.");
    var level = app.teleports.level(point);
    var pos = net.minecraft.core.BlockPos.containing(point.x, point.y, point.z);
    if (!Double.isFinite(point.x)
        || !Double.isFinite(point.y)
        || !Double.isFinite(point.z)
        || level.isOutsideBuildHeight(pos)
        || level.isOutsideBuildHeight(pos.above())
        || !level.getWorldBorder().isWithinBounds(pos))
      throw new IllegalArgumentException("First-join spawn is outside world bounds.");
    level.getChunk(pos);
    var box =
        new net.minecraft.world.phys.AABB(
            point.x - .3, point.y, point.z - .3, point.x + .3, point.y + 1.8, point.z + .3);
    if (level.getBlockCollisions(null, box).iterator().hasNext()
        || level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty())
      throw new IllegalArgumentException("First-join spawn needs clear space above a solid floor.");
    for (var b : net.minecraft.core.BlockPos.betweenClosed(pos.below(), pos.above())) {
      var state = level.getBlockState(b);
      if (!state.getFluidState().isEmpty()
          || state.is(net.minecraft.world.level.block.Blocks.FIRE)
          || state.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
          || state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
          || state.is(net.minecraft.world.level.block.Blocks.CACTUS)
          || state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
          || state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW))
        throw new IllegalArgumentException("First-join spawn contains a hazard.");
    }
  }

  boolean login(ServerPlayer player, boolean newPlayer) throws Exception {
    if (pending(player)) {
      if (app.moderation != null && app.moderation.confined(player)) {
        prompt(player, true);
        return true;
      }
      returnToSpawn(player);
      prompt(player, true);
      return true;
    }
    if (newPlayer && app.storage.state().firstSpawn != null && !app.compat.admin(player)) {
      returnToSpawn(player);
      return true;
    }
    return false;
  }

  private void returnToSpawn(ServerPlayer player) throws Exception {
    Point spawn = app.storage.state().firstSpawn;
    checkSpawn(spawn);
    app.teleports.cancel(player, false);
    player.stopRiding();
    player.closeContainer();
    moving.add(player.getUUID());
    app.backHistory.suppress();
    try {
      player.teleportTo(
          app.teleports.level(spawn), spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch);
      if (BackHistory.different(spawn, Teleports.point(player)))
        throw new IllegalArgumentException("First-join transfer was refused.");
      player.fallDistance = 0;
    } finally {
      app.backHistory.resume();
      moving.remove(player.getUUID());
    }
  }

  public void enforce(ServerPlayer player) {
    if (!pending(player)
        || forcing(player)
        || app.moderation != null && app.moderation.confined(player)
        || inside(Teleports.point(player))) return;
    rejectExit(player);
  }

  public void rejectExit(ServerPlayer player) {
    if (!pending(player)
        || forcing(player)
        || app.moderation != null && app.moderation.confined(player)) return;
    try {
      returnToSpawn(player);
      prompt(player, false);
    } catch (Exception ex) {
      Administration.LOG.error("Rules room transfer failed for {}", player.getUUID(), ex);
      player.connection.disconnect(
          Component.literal("The rules room is unavailable. Please contact an administrator."));
    }
  }

  public void prompt(ServerPlayer player, boolean force) {
    if (!pending(player)) return;
    long now = Teleports.now();
    if (!force && prompts.getOrDefault(player.getUUID(), 0L) > now) return;
    prompts.put(player.getUUID(), now + 5000);
    String revision = app.storage.state().rulesRoom.revision;
    try (var ignored = new Delivery(true)) {
      var config = app.storage.state().rulesRoom;
      player.sendSystemMessage(
          Component.literal(config.message)
              .withStyle(
                  Style.EMPTY
                      .withColor(RegionTitles.color(config.color))
                      .withBold(config.bold)
                      .withItalic(config.italic)
                      .withUnderlined(config.underlined)));
      player.sendSystemMessage(
          Component.literal("\n     [ ACCEPT ]     ")
              .withStyle(
                  s ->
                      s.withBold(true)
                          .withColor(ChatFormatting.GREEN)
                          .withClickEvent(
                              new ClickEvent(
                                  ClickEvent.Action.RUN_COMMAND, "/ffa rules accept " + revision)))
              .append(
                  Component.literal("[ DENY ]\n")
                      .withStyle(
                          s ->
                              s.withBold(true)
                                  .withColor(ChatFormatting.RED)
                                  .withClickEvent(
                                      new ClickEvent(
                                          ClickEvent.Action.RUN_COMMAND,
                                          "/ffa rules deny " + revision)))));
    }
  }

  public void answer(ServerPlayer player, boolean accept, String revision) throws Exception {
    if (!pending(player)) {
      Teleports.tell(player, "You have no rules request waiting.");
      return;
    }
    if (revision != null && !revision.equals(app.storage.state().rulesRoom.revision)) {
      prompt(player, true);
      return;
    }
    if (!accept) {
      prompt(player, true);
      return;
    }
    app.storage.update(s -> s.players.get(player.getUUID().toString()).rulesAccepted = true);
    prompts.remove(player.getUUID());
    Teleports.tell(player, "Rules accepted. Welcome!");
    app.server.getCommands().sendCommands(player);
    app.storage.audit(
        app.settings,
        player.getUUID().toString(),
        "rules accepted",
        app.storage.state().rulesRoom.region,
        "SUCCESS");
  }

  public boolean commandAllowed(ServerPlayer player, String command) {
    if (!pending(player)) return true;
    String[] words = command.strip().split("\\s+");
    return (words.length == 3 || words.length == 4)
        && words[0].equals("ffa")
        && words[1].equals("rules")
        && Set.of("accept", "deny").contains(words[2]);
  }

  public static boolean systemAllowed(ServerPlayer recipient, Packet<?> packet) {
    var a = Administration.INSTANCE;
    return !(packet instanceof ClientboundSystemChatPacket chat)
        || chat.overlay()
        || a == null
        || a.rules == null
        || !a.rules.pending(recipient)
        || TRUSTED.get() > 0;
  }

  public static boolean chatAllowed(ServerPlayer recipient, UUID sender) {
    var a = Administration.INSTANCE;
    if (a == null || a.rules == null || !a.rules.pending(recipient) || TRUSTED.get() > 0)
      return true;
    var author = sender == null ? null : a.server.getPlayerList().getPlayer(sender);
    return author != null && a.compat.admin(author);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void chat(ServerChatEvent event) {
    if (pending(event.getPlayer())) {
      event.setCanceled(true);
      prompt(event.getPlayer(), false);
    }
  }

  @SubscribeEvent
  public void tick(PlayerTickEvent.Post event) {
    if (event.getEntity() instanceof ServerPlayer player) enforce(player);
  }

  @SubscribeEvent
  public void respawn(PlayerEvent.PlayerRespawnEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      enforce(player);
      prompt(player, true);
    }
  }

  @SubscribeEvent
  public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
    prompts.remove(event.getEntity().getUUID());
  }
}
