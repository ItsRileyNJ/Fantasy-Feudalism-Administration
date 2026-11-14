package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;

import java.util.*;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.event.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Durable real-time sanctions; cell reservation and release do not depend on player sessions. */
public final class Moderation {
  public static final List<String> TYPES = List.of("jail", "chatban", "warpban");
  private final Administration app;
  private final LongSupplier clock;

  private record Move(Point destination, boolean release) {}

  private final Map<UUID, Move> forced = new HashMap<>();
  private final Map<UUID, Long> notices = new HashMap<>();

  public Moderation(Administration app) {
    this(app, System::currentTimeMillis);
  }

  Moderation(Administration app, LongSupplier clock) {
    this.app = app;
    this.clock = clock;
  }

  private Punishment get(UUID id, String type) {
    var p = app.storage.player(id);
    return p == null || p.punishments == null ? null : p.punishments.get(type);
  }

  public boolean banned(ServerPlayer p, String type) {
    var rule = get(p.getUUID(), type);
    return !app.compat.admin(p) && rule != null && rule.until > clock.getAsLong();
  }

  public boolean confined(ServerPlayer p) {
    return !app.compat.admin(p) && get(p.getUUID(), "jail") != null;
  }

  public boolean forcing(ServerPlayer p) {
    return forced.containsKey(p.getUUID());
  }

  public boolean permittedMove(ServerPlayer p, Point dest) {
    var move = forced.get(p.getUUID());
    return move != null && !BackHistory.different(move.destination, dest);
  }

  public boolean cellAt(ServerLevel level, BlockPos at) {
    Point point =
        new Point(
            level.dimension().location().toString(),
            at.getX() + .5,
            at.getY() + .5,
            at.getZ() + .5,
            0,
            0);
    return app.storage.state().jailCells.values().stream().anyMatch(c -> c.contains(point));
  }

  public static long seconds(String text, int maximum) {
    if (text == null || !text.matches("[1-9][0-9]*[smhdw]?"))
      throw new IllegalArgumentException("Use a duration such as 30s, 5m, 2h or 1d.");
    char last = text.charAt(text.length() - 1);
    long multiplier =
        switch (last) {
          case 'm' -> 60;
          case 'h' -> 3600;
          case 'd' -> 86400;
          case 'w' -> 604800;
          default -> 1;
        };
    String number = Character.isDigit(last) ? text : text.substring(0, text.length() - 1);
    try {
      long seconds = Math.multiplyExact(Long.parseLong(number), multiplier);
      if (seconds > maximum) throw new ArithmeticException();
      return seconds;
    } catch (ArithmeticException | NumberFormatException e) {
      throw new IllegalArgumentException("Duration exceeds the configured maximum.");
    }
  }

  public int defaultSeconds(String type) {
    return switch (type) {
      case "jail" -> app.settings.defaultJailSeconds;
      case "chatban" -> app.settings.defaultChatBanSeconds;
      case "warpban" -> app.settings.defaultWarpBanSeconds;
      default -> throw new IllegalArgumentException("Unknown punishment.");
    };
  }

  private String cellId(String name) {
    return app.storage.state().jailCells.keySet().stream()
        .filter(k -> k.equalsIgnoreCase(name))
        .findFirst()
        .orElse(name);
  }

  public void setCell(ServerPlayer admin, String name, boolean override) throws Exception {
    Regions.checkName(name);
    var selected = Commands.selection(admin);
    JailCell cell = new JailCell();
    cell.id = cellId(name);
    cell.dimension = selected.dimension();
    cell.arrival = Teleports.point(admin);
    cell.settlementOverride = override;
    cell.minX = Math.min(selected.first().getX(), selected.second().getX());
    cell.maxX = Math.max(selected.first().getX(), selected.second().getX());
    cell.minY = Math.min(selected.first().getY(), selected.second().getY());
    cell.maxY = Math.max(selected.first().getY(), selected.second().getY());
    cell.minZ = Math.min(selected.first().getZ(), selected.second().getZ());
    cell.maxZ = Math.max(selected.first().getZ(), selected.second().getZ());
    long volume =
        ((long) cell.maxX - cell.minX + 1)
            * ((long) cell.maxY - cell.minY + 1)
            * ((long) cell.maxZ - cell.minZ + 1);
    if (volume < 1
        || volume > 4096
        || cell.minY < admin.serverLevel().getMinBuildHeight()
        || cell.maxY >= admin.serverLevel().getMaxBuildHeight())
      throw new IllegalArgumentException(
          "Select a jail cell of at most 4096 blocks within world height.");
    if (!cell.contains(cell.arrival))
      throw new IllegalArgumentException(
          "Stand inside the selected cell to set its arrival point.");
    if (occupied(cell.id, null)) throw new IllegalArgumentException("That cell is occupied.");
    for (var other : app.storage.state().jailCells.values())
      if (!other.id.equals(cell.id)
          && other.dimension.equals(cell.dimension)
          && cell.minX <= other.maxX
          && cell.maxX >= other.minX
          && cell.minY <= other.maxY
          && cell.maxY >= other.minY
          && cell.minZ <= other.maxZ
          && cell.maxZ >= other.minZ)
        throw new IllegalArgumentException("Jail cells cannot overlap.");
    Region bounds = new Region();
    bounds.dimension = cell.dimension;
    bounds.minX = cell.minX;
    bounds.maxX = cell.maxX;
    bounds.minZ = cell.minZ;
    bounds.maxZ = cell.maxZ;
    if (!override && app.compat.overlaps(app.server, bounds))
      throw new IllegalArgumentException(
          "Cell overlaps a settlement; use override only if intended.");
    app.storage.update(s -> s.jailCells.put(cell.id, cell));
    Teleports.tell(admin, "Jail cell set.");
  }

  public void deleteCell(String name) throws Exception {
    String id = cellId(name);
    if (!app.storage.state().jailCells.containsKey(id))
      throw new IllegalArgumentException("Unknown jail cell.");
    if (occupied(id, null))
      throw new IllegalArgumentException("Release the cell's prisoner first.");
    app.storage.update(s -> s.jailCells.remove(id));
  }

  private boolean occupied(String cell, UUID except) {
    for (var entry : app.storage.state().players.entrySet()) {
      UUID id = UUID.fromString(entry.getKey());
      if (id.equals(except)) continue;
      var jail =
          entry.getValue().punishments == null ? null : entry.getValue().punishments.get("jail");
      if (jail != null
          && jail.cell.equals(cell)
          && (jail.until > clock.getAsLong() || app.server.getPlayerList().getPlayer(id) != null))
        return true;
    }
    return false;
  }

  public void apply(UUID target, String type, long seconds, String reason) throws Exception {
    if (!TYPES.contains(type)) throw new IllegalArgumentException("Unknown punishment.");
    if (seconds < 1 || seconds > app.settings.maximumDurationSeconds)
      throw new IllegalArgumentException("Duration exceeds the configured maximum.");
    if (reason.length() > 256 || reason.codePoints().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Keep the reason to 256 plain-text characters.");
    ServerPlayer player = app.server.getPlayerList().getPlayer(target);
    if (player != null && app.compat.admin(player))
      throw new IllegalArgumentException("Administrators are exempt from punishments.");
    app.storage.ensure(target, app.settings);
    Punishment rule = new Punishment();
    rule.until = Math.addExact(clock.getAsLong(), Math.multiplyExact(seconds, 1000));
    rule.reason = reason;
    if (type.equals("jail")) {
      if (player == null)
        throw new IllegalArgumentException("The player must be online to be jailed.");
      if (app.storage.state().spawn == null)
        throw new IllegalArgumentException("Set a safe spawn point before using jail.");
      if (app.storage.state().trades.containsKey(target.toString()))
        throw new IllegalArgumentException("Recover the player's pending transaction first.");
      JailCell chosen = null;
      for (var cell : app.storage.state().jailCells.values())
        if (!occupied(cell.id, target)) {
          forced.put(target, new Move(cell.arrival, false));
          try {
            if (app.teleports.validate(player, cell.arrival) == null) {
              chosen = cell;
              break;
            }
          } finally {
            forced.remove(target);
          }
        }
      if (chosen == null)
        throw new IllegalArgumentException("No free, loaded and safe jail cell is available.");
      rule.cell = chosen.id;
      var old = get(target, "jail");
      rule.previousMode =
          old == null ? player.gameMode.getGameModeForPlayer().getName() : old.previousMode;
      player.closeContainer();
      player.stopUsingItem();
      player.stopRiding();
      app.teleports.cancel(player, false);
    }
    var previous = get(target, type);
    app.storage.update(s -> s.players.get(target.toString()).punishments.put(type, rule));
    if (type.equals("jail")) {
      try {
        player.setGameMode(GameType.ADVENTURE);
        move(player, app.storage.state().jailCells.get(rule.cell).arrival, false);
      } catch (Exception error) {
        forced.remove(target);
        app.storage.update(
            s -> {
              var rules = s.players.get(target.toString()).punishments;
              if (previous == null) rules.remove(type);
              else rules.put(type, previous);
            });
        if (previous == null) player.setGameMode(GameType.byName(rule.previousMode));
        throw error;
      }
    }
    if (player != null)
      Teleports.tell(
          player,
          (type.equals("jail")
                  ? "You have been jailed."
                  : type.equals("chatban")
                      ? "You have been banned from chat."
                      : "You have been banned from warps.")
              + (reason.isBlank() ? "" : " " + reason));
  }

  public void clear(UUID target, String type) throws Exception {
    if (!TYPES.contains(type)) throw new IllegalArgumentException("Unknown punishment.");
    if (get(target, type) == null)
      throw new IllegalArgumentException("That punishment is not active.");
    if (type.equals("jail")) {
      app.storage.update(s -> s.players.get(target.toString()).punishments.get(type).until = 0);
      var player = app.server.getPlayerList().getPlayer(target);
      if (player != null) release(player);
    } else app.storage.update(s -> s.players.get(target.toString()).punishments.remove(type));
  }

  private void move(ServerPlayer p, Point dest, boolean release) throws Exception {
    if (dest == null) throw new IllegalArgumentException("Spawn is not configured.");
    forced.put(p.getUUID(), new Move(dest, release));
    try {
      app.teleports.teleport(p, dest, true);
    } catch (Exception error) {
      forced.remove(p.getUUID());
      throw error;
    }
  }

  private void release(ServerPlayer p) throws Exception {
    if (forced.containsKey(p.getUUID())) return;
    app.teleports.cancel(p, false);
    move(p, app.storage.state().spawn, true);
  }

  public void arrived(ServerPlayer p) throws Exception {
    var move = forced.get(p.getUUID());
    if (move == null || BackHistory.different(move.destination, Teleports.point(p))) return;
    forced.remove(p.getUUID());
    if (move.release) {
      var old = get(p.getUUID(), "jail");
      app.storage.update(s -> s.players.get(p.getUUID().toString()).punishments.remove("jail"));
      if (old != null) p.setGameMode(GameType.byName(old.previousMode));
      app.protection.update(p);
      Teleports.tell(p, "You have been released to spawn.");
    }
  }

  public String status(UUID id) {
    List<String> lines = new ArrayList<>();
    for (String type : TYPES) {
      var rule = get(id, type);
      long remaining =
          rule == null ? 0 : Math.max(0, (rule.until - clock.getAsLong() + 999) / 1000);
      lines.add(
          (type.equals("jail") ? "Jail" : type.equals("chatban") ? "Chat ban" : "Warp ban")
              + ": "
              + (rule == null
                  ? "none"
                  : remaining == 0
                      ? (type.equals("jail") ? "expired; release pending" : "expired")
                      : remaining + " seconds remaining"));
    }
    return String.join(". ", lines) + ".";
  }

  void enforce(ServerPlayer p) {
    var jail = get(p.getUUID(), "jail");
    if (jail == null) return;
    try {
      if (jail.until <= clock.getAsLong() || app.compat.admin(p)) {
        release(p);
        return;
      }
      if (p.gameMode.getGameModeForPlayer() != GameType.ADVENTURE)
        p.setGameMode(GameType.ADVENTURE);
      var cell = app.storage.state().jailCells.get(jail.cell);
      if (cell == null) throw new IllegalStateException("Assigned jail cell is missing.");
      if (!cell.contains(Teleports.point(p)) && !forcing(p)) {
        app.teleports.cancel(p, false);
        move(p, cell.arrival, false);
      }
    } catch (Exception error) {
      forced.remove(p.getUUID());
      if (notices.getOrDefault(p.getUUID(), 0L) <= clock.getAsLong()) {
        notices.put(p.getUUID(), clock.getAsLong() + 10000);
        Teleports.tell(
            p, "Jail transfer is waiting for a safe destination. Contact an administrator.");
        Administration.LOG.warn("Jail transfer failed for {}: {}", p.getUUID(), error.getMessage());
      }
    }
  }

  @SubscribeEvent
  public void login(PlayerEvent.PlayerLoggedInEvent e) {
    if (e.getEntity() instanceof ServerPlayer p) enforce(p);
  }

  @SubscribeEvent
  public void respawn(PlayerEvent.PlayerRespawnEvent e) {
    if (e.getEntity() instanceof ServerPlayer p) enforce(p);
  }

  @SubscribeEvent
  public void tick(ServerTickEvent.Post e) {
    for (var p : app.server.getPlayerList().getPlayers()) {
      if (forcing(p) && !app.teleports.busyFor(p)) forced.remove(p.getUUID());
      enforce(p);
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void chat(ServerChatEvent e) {
    if (banned(e.getPlayer(), "chatban")) {
      e.setCanceled(true);
      Teleports.tell(e.getPlayer(), "You cannot chat right now.");
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void command(CommandEvent e) {
    var context = e.getParseResults().getContext();
    var player = context.getSource().getPlayer();
    if (player == null) return;
    Set<String> words = new HashSet<>();
    for (var current = context; current != null; current = current.getChild())
      for (var node : current.getNodes())
        if (node.getNode() instanceof com.mojang.brigadier.tree.LiteralCommandNode<?>)
          words.add(node.getNode().getName());
    boolean messaging =
        words.stream()
            .anyMatch(
                Set.of("msg", "tell", "w", "me", "say", "teammsg", "tm", "whisper")::contains);
    if (banned(player, "chatban") && messaging) {
      e.setCanceled(true);
      Teleports.tell(player, "You cannot send messages right now.");
      return;
    }
    var parsed = context.getNodes();
    String root = parsed.isEmpty() ? "" : parsed.getFirst().getNode().getName();
    var node = parsed.isEmpty() ? null : parsed.getFirst().getNode();
    Set<com.mojang.brigadier.tree.CommandNode<net.minecraft.commands.CommandSourceStack>> seen =
        new HashSet<>();
    while (node != null && node.getRedirect() != null && seen.add(node)) {
      node = node.getRedirect();
      root = node.getName();
    }
    boolean allowed =
        Set.of("msg", "tell", "w", "me", "say", "teammsg", "tm", "help").contains(root)
            || root.equals("ffa")
                && (parsed.size() == 1
                    || Set.of(
                            "help",
                            "stats",
                            "rules",
                            "whisper",
                            "whisperaccept",
                            "whisperdeny",
                            "whisperforget")
                        .contains(parsed.get(1).getNode().getName()));
    if (confined(player) && !allowed) {
      e.setCanceled(true);
      Teleports.tell(player, "You cannot use that command while jailed.");
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void rightBlock(
      net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
    interact(e);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void rightItem(
      net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem e) {
    interact(e);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void leftBlock(
      net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock e) {
    interact(e);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void entity(
      net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract e) {
    interact(e);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void specific(
      net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific e) {
    interact(e);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void attack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent e) {
    if (e.getEntity() instanceof ServerPlayer p && confined(p)) e.setCanceled(true);
  }

  private void interact(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent e) {
    if (e.getEntity() instanceof ServerPlayer p
        && confined(p)
        && e instanceof net.neoforged.bus.api.ICancellableEvent cancellable)
      cancellable.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void pickup(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre e) {
    if (e.getPlayer() instanceof ServerPlayer p && confined(p))
      e.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
  }
}
