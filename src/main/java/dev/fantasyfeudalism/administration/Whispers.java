package dev.fantasyfeudalism.administration;

import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Recipient-owned durable consent and single-use request buttons. No message body is audited. */
public final class Whispers {
  record Pending(UUID sender, UUID recipient, UUID token, String body, long expires) {}

  private final Administration app;
  private final java.util.function.LongSupplier clock;
  private final Map<UUID, Pending> pending = new HashMap<>();
  private final Map<UUID, Long> cooldown = new HashMap<>();

  public Whispers(Administration app) {
    this(app, () -> System.nanoTime() / 1000000);
  }

  Whispers(Administration app, java.util.function.LongSupplier clock) {
    this.app = app;
    this.clock = clock;
  }

  private long now() {
    return clock.getAsLong();
  }

  public boolean waiting(ServerPlayer p) {
    Pending r = pending.get(p.getUUID());
    return r != null && r.expires > now();
  }

  Pending request(UUID recipient) {
    return pending.get(recipient);
  }

  public static String validate(String body) {
    if (body == null
        || body.isBlank()
        || body.codePointCount(0, body.length()) > 512
        || body.codePoints()
            .anyMatch(
                c ->
                    Character.isISOControl(c)
                        || Character.getType(c) == Character.FORMAT
                        || c == 0x00a7))
      throw new IllegalArgumentException(
          "Whispers require 1..512 plain-text characters without control or formatting codes");
    return body;
  }

  public void send(ServerPlayer from, ServerPlayer to, String body) throws Exception {
    try (var ignored = Rules.from(from)) {
      sendInternal(from, to, body);
    }
  }

  private void sendInternal(ServerPlayer from, ServerPlayer to, String body) throws Exception {
    validate(body);
    if (app.moderation != null && app.moderation.banned(from, "chatban"))
      throw new IllegalArgumentException("You cannot send messages right now.");
    if (from == to) throw new IllegalArgumentException("Choose another player");
    if (Vanish.hidden(from.getUUID()))
      throw new IllegalArgumentException("Leave vanish before sending player messages");
    if (!Vanish.canSee(from, to)) throw new IllegalArgumentException("Player is offline");
    var data = app.storage.player(to.getUUID());
    if (data.ignored.contains(from.getUUID().toString()))
      throw new IllegalArgumentException("That player is not accepting your messages");
    if (cooldown.getOrDefault(from.getUUID(), 0L) > now())
      throw new IllegalArgumentException("Whisper cooldown is active");
    if (data.whisperAllowed != null && data.whisperAllowed.contains(from.getUUID().toString())) {
      cooldown.put(from.getUUID(), now() + 1000);
      deliver(from, to, body);
      monitor(from, to, body, false);
      return;
    }
    Pending old = pending.get(to.getUUID());
    if (old != null && old.expires > now())
      throw new IllegalArgumentException("That player already has a pending message request");
    Set<UUID> replaced = new HashSet<>();
    pending
        .entrySet()
        .removeIf(
            e -> {
              if (e.getValue().sender.equals(from.getUUID())) {
                replaced.add(e.getKey());
                return true;
              }
              return false;
            });
    for (UUID id : replaced) {
      var player = app.server.getPlayerList().getPlayer(id);
      if (player != null) refresh(player);
    }
    Pending request =
        new Pending(
            from.getUUID(),
            to.getUUID(),
            UUID.randomUUID(),
            body,
            now() + app.settings.requestExpiry * 1000L);
    pending.put(to.getUUID(), request);
    cooldown.put(from.getUUID(), now() + app.settings.requestCooldown * 1000L);
    to.sendSystemMessage(
        Component.literal(from.getGameProfile().getName() + " wants to send you a message"));
    to.sendSystemMessage(
        Component.literal("[Accept] ")
            .withStyle(
                s ->
                    s.withColor(ChatFormatting.GREEN)
                        .withClickEvent(
                            new ClickEvent(
                                ClickEvent.Action.RUN_COMMAND,
                                "/ffa whisperaccept " + request.token)))
            .append(
                Component.literal("[Deny]")
                    .withStyle(
                        s ->
                            s.withColor(ChatFormatting.RED)
                                .withClickEvent(
                                    new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        "/ffa whisperdeny " + request.token)))));
    Teleports.tell(from, "Message request sent; delivery waits for acceptance.");
    monitor(from, to, body, true);
    refresh(to);
  }

  public void answer(ServerPlayer recipient, boolean accept, UUID token) throws Exception {
    Pending request = pending.get(recipient.getUUID());
    if (request == null || request.expires <= now()) {
      pending.remove(recipient.getUUID());
      refresh(recipient);
      throw new IllegalArgumentException("No unexpired message request");
    }
    if (token != null && !token.equals(request.token))
      throw new IllegalArgumentException("That message request button is stale");
    ServerPlayer sender = app.server.getPlayerList().getPlayer(request.sender);
    if (sender == null || !Vanish.canSee(recipient, sender))
      throw new IllegalArgumentException("Requesting player is offline");
    if (accept
        && app.storage.player(recipient.getUUID()).ignored.contains(sender.getUUID().toString()))
      throw new IllegalArgumentException("This sender is currently ignored");
    if (accept)
      app.storage.update(
          s -> {
            var data = s.players.get(recipient.getUUID().toString());
            if (data.whisperAllowed == null) data.whisperAllowed = new HashSet<>();
            data.whisperAllowed.add(sender.getUUID().toString());
          });
    pending.remove(recipient.getUUID());
    refresh(recipient);
    if (accept) {
      deliver(sender, recipient, request.body);
    } else {
      Teleports.tell(sender, "Message request denied.");
      Teleports.tell(recipient, "Message request denied.");
    }
  }

  private void deliver(ServerPlayer sender, ServerPlayer recipient, String body) {
    recipient.sendSystemMessage(
        Component.literal("(" + sender.getGameProfile().getName() + " --> you) " + body)
            .withStyle(ChatFormatting.LIGHT_PURPLE));
    sender.sendSystemMessage(
        Component.literal("(you --> " + recipient.getGameProfile().getName() + ") " + body)
            .withStyle(ChatFormatting.LIGHT_PURPLE));
  }

  private void monitor(ServerPlayer from, ServerPlayer to, String body, boolean awaiting) {
    for (ServerPlayer admin : app.server.getPlayerList().getPlayers()) {
      if (admin == from || admin == to && !awaiting || !app.compat.admin(admin)) continue;
      var prefs = app.storage.player(admin.getUUID());
      if (prefs != null && prefs.whisperMonitor)
        admin.sendSystemMessage(
            Component.literal(
                    "("
                        + from.getGameProfile().getName()
                        + " --> "
                        + to.getGameProfile().getName()
                        + ") "
                        + (awaiting ? "[pending consent] " : "")
                        + body)
                .withStyle(ChatFormatting.GRAY));
    }
  }

  public void monitorPreference(ServerPlayer admin, boolean enabled) throws Exception {
    if (!app.compat.admin(admin)) throw new IllegalArgumentException("Administrator only");
    app.storage.update(s -> s.players.get(admin.getUUID().toString()).whisperMonitor = enabled);
    Teleports.tell(admin, "Whisper monitoring " + (enabled ? "on" : "off") + ".");
  }

  public void forget(ServerPlayer recipient, UUID sender) throws Exception {
    app.storage.update(
        s -> {
          var data = s.players.get(recipient.getUUID().toString());
          if (data.whisperAllowed != null) data.whisperAllowed.remove(sender.toString());
        });
    Teleports.tell(recipient, "Future messages from that sender require consent again.");
  }

  public void cancelFor(UUID id) {
    Set<UUID> changed = new HashSet<>();
    pending
        .entrySet()
        .removeIf(
            e -> {
              boolean remove = e.getKey().equals(id) || e.getValue().sender.equals(id);
              if (remove) changed.add(e.getKey());
              return remove;
            });
    for (UUID recipient : changed) {
      var p = app.server.getPlayerList().getPlayer(recipient);
      if (p != null) refresh(p);
    }
  }

  private void refresh(ServerPlayer p) {
    app.server.getCommands().sendCommands(p);
  }

  @SubscribeEvent
  public void logout(PlayerEvent.PlayerLoggedOutEvent e) {
    cancelFor(e.getEntity().getUUID());
    cooldown.remove(e.getEntity().getUUID());
  }

  @SubscribeEvent
  public void tick(ServerTickEvent.Post e) {
    if (pending.isEmpty()) return;
    for (var p : app.server.getPlayerList().getPlayers()) {
      Pending r = pending.get(p.getUUID());
      if (r != null && r.expires <= now()) {
        pending.remove(p.getUUID());
        Teleports.tell(p, "Message request expired.");
        refresh(p);
      }
    }
  }
}
