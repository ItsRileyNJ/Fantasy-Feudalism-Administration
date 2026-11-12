package dev.fantasyfeudalism.administration;

import com.mojang.authlib.GameProfile;
import dev.fantasyfeudalism.administration.mixin.InfoPacketAccess;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Player-facing concealment. Staff/console retain visibility and server accounting stays intact.
 */
public final class Vanish {
  private final Administration app;
  private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Set<UUID>> knownProfiles = new ConcurrentHashMap<>();
  private volatile Set<UUID> staff = Set.of();
  private volatile List<GameProfile> publicProfiles = List.of();

  public Vanish(Administration app) {
    this.app = app;
    app.storage
        .state()
        .players
        .forEach(
            (id, data) -> {
              if (data.vanished) hidden.add(UUID.fromString(id));
            });
    refresh(null);
  }

  private static Vanish instance() {
    var a = Administration.INSTANCE;
    return a == null ? null : a.vanish;
  }

  public static boolean hidden(UUID id) {
    var v = instance();
    return v != null && v.hidden.contains(id);
  }

  public static boolean canSee(ServerPlayer viewer, ServerPlayer subject) {
    var a = Administration.INSTANCE;
    return !hidden(subject.getUUID())
        || viewer == subject
        || a != null && a.compat != null && a.compat.admin(viewer);
  }

  public static boolean canSee(CommandSourceStack viewer, ServerPlayer subject) {
    return viewer.getPlayer() == null || canSee(viewer.getPlayer(), subject);
  }

  public static List<GameProfile> publicProfiles() {
    var v = instance();
    return v == null ? List.of() : v.publicProfiles;
  }

  public static boolean active() {
    var v = instance();
    return v != null && !v.hidden.isEmpty();
  }

  public void toggle(ServerPlayer player) throws Exception {
    if (!app.compat.admin(player)) throw new IllegalArgumentException("Administrator only");
    if (!hidden(player.getUUID())) {
      if (app.storage.state().trades.containsKey(player.getUUID().toString())
          || InspectionMenu.busy(app, player))
        throw new IllegalArgumentException(
            "Finish inventory transactions/inspections before vanish");
      String mode = player.gameMode.getGameModeForPlayer().getName();
      app.storage.update(
          s -> {
            var d = s.players.get(player.getUUID().toString());
            d.vanished = true;
            d.vanishMode = mode;
          });
      hidden.add(player.getUUID());
      app.whispers.cancelFor(player.getUUID());
      app.teleports.cancel(player, false);
      player.setGameMode(GameType.SPECTATOR);
      refresh(null);
      syncAll();
      for (var observer : app.server.getPlayerList().getPlayers())
        if (!app.compat.admin(observer))
          observer.sendSystemMessage(
              Component.literal(player.getDisplayName().getString() + " left the game")
                  .withStyle(net.minecraft.ChatFormatting.YELLOW));
      Teleports.tell(
          player,
          "Vanish on. Spectator mode; ordinary players see you as offline. Staff and console retain"
              + " visibility.");
    } else exit(player, true);
    app.storage.audit(
        app.settings,
        player.getUUID().toString(),
        "vanish",
        Teleports.point(player).describe(),
        hidden(player.getUUID()) ? "ON" : "OFF");
    app.server.invalidateStatus();
  }

  private void exit(ServerPlayer player, boolean authorized) throws Exception {
    var data = app.storage.player(player.getUUID());
    String previous = data == null ? null : data.vanishMode;
    app.storage.update(
        s -> {
          var d = s.players.get(player.getUUID().toString());
          d.vanished = false;
          d.vanishMode = null;
        });
    hidden.remove(player.getUUID());
    player.setGameMode(
        authorized && previous != null ? GameType.byName(previous) : GameType.SURVIVAL);
    refresh(null);
    app.server
        .getPlayerList()
        .broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)));
    for (var observer : app.server.getPlayerList().getPlayers())
      if (!app.compat.admin(observer))
        observer.sendSystemMessage(
            Component.translatable("multiplayer.player.joined", player.getDisplayName()));
    app.protection.update(player);
    Teleports.tell(player, "Vanish disabled.");
    app.server.invalidateStatus();
  }

  public void refresh(UUID leaving) {
    Set<UUID> next = new HashSet<>();
    List<GameProfile> profiles = new ArrayList<>();
    for (var p : app.server.getPlayerList().getPlayers()) {
      if (p.getUUID().equals(leaving)) continue;
      if (app.compat.admin(p)) next.add(p.getUUID());
      if (!hidden.contains(p.getUUID())) profiles.add(p.getGameProfile());
    }
    Set<UUID> old = staff;
    staff = Set.copyOf(next);
    publicProfiles = List.copyOf(profiles);
    for (var p : app.server.getPlayerList().getPlayers())
      if (old.contains(p.getUUID()) != next.contains(p.getUUID())) sync(p);
  }

  public void syncAll() {
    for (var p : app.server.getPlayerList().getPlayers()) sync(p);
  }

  private void sync(ServerPlayer viewer) {
    for (var target : app.server.getPlayerList().getPlayers())
      if (hidden.contains(target.getUUID()) && target != viewer) {
        if (staff.contains(viewer.getUUID()))
          viewer.connection.send(
              ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(target)));
        else if (knownProfiles
            .getOrDefault(viewer.getUUID(), Set.of())
            .contains(target.getUUID())) {
          viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(target.getUUID())));
          viewer.connection.send(new ClientboundRemoveEntitiesPacket(target.getId()));
        }
      }
  }

  @SubscribeEvent
  public void login(PlayerEvent.PlayerLoggedInEvent e) {
    if (!(e.getEntity() instanceof ServerPlayer p)) return;
    try {
      if (hidden(p.getUUID())) {
        if (!app.compat.admin(p)) exit(p, false);
        else p.setGameMode(GameType.SPECTATOR);
      }
      refresh(null);
      sync(p);
    } catch (Exception ex) {
      p.connection.disconnect(
          Component.literal("Cannot restore staff visibility safely; contact an administrator"));
    }
  }

  @SubscribeEvent
  public void logout(PlayerEvent.PlayerLoggedOutEvent e) {
    refresh(e.getEntity().getUUID());
    knownProfiles.remove(e.getEntity().getUUID());
  }

  @SubscribeEvent
  public void tick(ServerTickEvent.Post e) {
    if (hidden.isEmpty()) return;
    enforce();
  }

  public void enforce() {
    for (var p : app.server.getPlayerList().getPlayers())
      if (hidden(p.getUUID())) {
        try {
          if (!app.compat.admin(p)) exit(p, false);
          else if (!p.isSpectator()) p.setGameMode(GameType.SPECTATOR);
        } catch (Exception ex) {
          p.connection.disconnect(Component.literal("Staff visibility recovery failed"));
        }
      }
    refresh(null);
  }

  @SubscribeEvent
  public void chat(ServerChatEvent e) {
    if (hidden(e.getPlayer().getUUID())) {
      e.setCanceled(true);
      Teleports.tell(e.getPlayer(), "Leave vanish before sending public chat.");
    }
  }

  public static Packet<?> filter(ServerPlayer viewer, Packet<?> packet) {
    Vanish v = instance();
    if (v == null) return packet;
    boolean staff = v.staff.contains(viewer.getUUID());
    if (packet instanceof ClientboundPlayerInfoUpdatePacket info) {
      Set<UUID> known =
          v.knownProfiles.computeIfAbsent(viewer.getUUID(), id -> ConcurrentHashMap.newKeySet());
      var entries =
          info.entries().stream()
              .filter(
                  e ->
                      staff
                          || !v.hidden.contains(e.profileId())
                          || e.profileId().equals(viewer.getUUID()))
              .toList();
      if (info.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER))
        for (var entry : entries) known.add(entry.profileId());
      if (entries.size() == info.entries().size()) return packet;
      if (entries.isEmpty()) return null;
      var copy = new ClientboundPlayerInfoUpdatePacket(info.actions(), List.of());
      ((InfoPacketAccess) (Object) copy).ffa$entries(entries);
      return copy;
    }
    if (packet instanceof ClientboundPlayerInfoRemovePacket info) {
      Set<UUID> known = v.knownProfiles.getOrDefault(viewer.getUUID(), Set.of());
      var entries =
          info.profileIds().stream()
              .filter(id -> staff || !v.hidden.contains(id) || known.contains(id))
              .toList();
      if (v.knownProfiles.containsKey(viewer.getUUID())) known.removeAll(info.profileIds());
      if (entries.size() == info.profileIds().size()) return packet;
      return entries.isEmpty() ? null : new ClientboundPlayerInfoRemovePacket(entries);
    }
    if (v.hidden.isEmpty() || staff) return packet;
    if (packet instanceof ClientboundPlayerChatPacket chat && hidden(chat.sender())) return null;
    if (packet instanceof ClientboundDisguisedChatPacket chat
        && containsHidden(chat.chatType().name())) return null;
    if (packet instanceof ClientboundSystemChatPacket chat
        && chat.content().getContents() instanceof TranslatableContents t
        && (t.getKey().startsWith("multiplayer.player.")
            || t.getKey().startsWith("chat.type.advancement.")
            || t.getKey().startsWith("death.")
            || t.getKey().equals("chat.type.announcement")
            || t.getKey().equals("chat.type.emote"))
        && containsHidden(chat.content())) return null;
    return packet;
  }

  private static boolean containsHidden(Component component) {
    return component
        .visit(
            (style, text) -> {
              var hover = style.getHoverEvent();
              var entity = hover == null ? null : hover.getValue(HoverEvent.Action.SHOW_ENTITY);
              return entity != null && hidden(entity.id) ? Optional.of(true) : Optional.empty();
            },
            Style.EMPTY)
        .orElse(false);
  }

  public static ServerStatus publicStatus(ServerStatus original) {
    if (!active() || original == null || original.players().isEmpty()) return original;
    var profiles = publicProfiles();
    return new ServerStatus(
        original.description(),
        Optional.of(
            new ServerStatus.Players(
                original.players().get().max(),
                profiles.size(),
                profiles.stream().limit(12).toList())),
        original.version(),
        original.favicon(),
        original.enforcesSecureChat(),
        original.isModded());
  }
}
