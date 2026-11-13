package dev.fantasyfeudalism.administration;

import java.util.*;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Bounded, single-use payment requests. Only the payer's explicit acceptance can debit them. */
public final class Payments {
  record Request(UUID requester, UUID payer, UUID token, long amount, long expires) {}

  private final Administration app;
  private final java.util.function.LongSupplier clock;
  private final Map<UUID, Request> pending = new HashMap<>();
  private final Map<UUID, Long> cooldown = new HashMap<>();

  public Payments(Administration app) {
    this(app, Teleports::now);
  }

  Payments(Administration app, java.util.function.LongSupplier clock) {
    this.app = app;
    this.clock = clock;
  }

  public boolean allowed(ServerPlayer player) {
    return app.settings.economy
        && (app.compat.admin(player)
            || app.compat
                    .decision(player, "ffa.command.balance")
                    .orElse(app.settings.publicCommands)
                && app.compat
                    .decision(player, "ffa.command.pay")
                    .orElse(app.settings.publicCommands));
  }

  private void validate(ServerPlayer from, ServerPlayer to, long amount) {
    if (!allowed(from)) throw new IllegalArgumentException("Money transfers are not permitted.");
    if (from == to || !Vanish.canSee(from, to))
      throw new IllegalArgumentException("Choose another online player.");
    if (Vanish.hidden(from.getUUID()))
      throw new IllegalArgumentException("Leave vanish before sending money or payment requests.");
    if (amount < 1 || amount > app.settings.maxTransfer)
      throw new IllegalArgumentException("Amount must be 1–" + app.settings.maxTransfer + ".");
  }

  public void send(ServerPlayer from, ServerPlayer to, long amount) throws Exception {
    validate(from, to, amount);
    app.economy.pay(from, to, amount);
    Teleports.tell(
        from,
        "Sent "
            + amount
            + " "
            + app.settings.currency
            + " to "
            + to.getGameProfile().getName()
            + ".");
    Teleports.tell(
        to,
        "Received "
            + amount
            + " "
            + app.settings.currency
            + " from "
            + from.getGameProfile().getName()
            + ".");
  }

  public void request(ServerPlayer from, ServerPlayer payer, long amount) throws Exception {
    validate(from, payer, amount);
    if (!allowed(payer)) throw new IllegalArgumentException("That player cannot send money.");
    if (cooldown.getOrDefault(from.getUUID(), 0L) > clock.getAsLong())
      throw new IllegalArgumentException("Wait before requesting money again.");
    var old = pending.get(payer.getUUID());
    if (old != null && old.expires > clock.getAsLong())
      throw new IllegalArgumentException("That player already has a payment request.");
    if (pending.values().stream()
        .anyMatch(r -> r.requester.equals(from.getUUID()) && r.expires > clock.getAsLong()))
      throw new IllegalArgumentException("You already have a payment request waiting.");
    var r =
        new Request(
            from.getUUID(),
            payer.getUUID(),
            UUID.randomUUID(),
            amount,
            clock.getAsLong() + app.settings.requestExpiry * 1000L);
    pending.put(payer.getUUID(), r);
    cooldown.put(from.getUUID(), clock.getAsLong() + app.settings.requestCooldown * 1000L);
    Teleports.tell(
        payer,
        from.getGameProfile().getName()
            + " requests "
            + amount
            + " "
            + app.settings.currency
            + " from you.");
    payer.sendSystemMessage(
        Component.literal("[Accept] ")
            .withStyle(
                s ->
                    s.withColor(0x55FF55)
                        .withClickEvent(
                            new ClickEvent(
                                ClickEvent.Action.RUN_COMMAND, "/ffa balance accept " + r.token)))
            .append(
                Component.literal("[Deny]")
                    .withStyle(
                        s ->
                            s.withColor(0xFF5555)
                                .withClickEvent(
                                    new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        "/ffa balance deny " + r.token)))));
    Teleports.tell(from, "Payment request sent.");
    refresh(payer);
  }

  public boolean waiting(ServerPlayer p) {
    var r = pending.get(p.getUUID());
    return r != null && r.expires > clock.getAsLong();
  }

  Request request(UUID payer) {
    return pending.get(payer);
  }

  public void answer(ServerPlayer payer, boolean accept, UUID token) throws Exception {
    var r = pending.get(payer.getUUID());
    if (r == null || r.expires <= clock.getAsLong()) {
      pending.remove(payer.getUUID());
      refresh(payer);
      throw new IllegalArgumentException("No unexpired payment request.");
    }
    if (token != null && !r.token.equals(token))
      throw new IllegalArgumentException("That payment request button is stale.");
    var requester = app.server.getPlayerList().getPlayer(r.requester);
    if (requester == null || !Vanish.canSee(payer, requester))
      throw new IllegalArgumentException("Requesting player is offline.");
    pending.remove(payer.getUUID());
    refresh(payer);
    if (accept) {
      try {
        send(payer, requester, r.amount);
      } catch (Exception error) {
        Teleports.tell(requester, "Payment request could not be completed.");
        throw error;
      }
    }
    if (!accept) {
      Teleports.tell(payer, "Payment request denied.");
      Teleports.tell(requester, "Payment request denied.");
    }
  }

  private void refresh(ServerPlayer p) {
    app.server.getCommands().sendCommands(p);
  }

  @SubscribeEvent
  public void logout(PlayerEvent.PlayerLoggedOutEvent e) {
    var id = e.getEntity().getUUID();
    Set<UUID> changed = new HashSet<>();
    pending
        .entrySet()
        .removeIf(
            entry -> {
              boolean remove = entry.getKey().equals(id) || entry.getValue().requester.equals(id);
              if (remove) changed.add(entry.getKey());
              return remove;
            });
    cooldown.remove(id);
    for (var uuid : changed) {
      var p = app.server.getPlayerList().getPlayer(uuid);
      if (p != null) refresh(p);
    }
  }

  @SubscribeEvent
  public void tick(ServerTickEvent.Post e) {
    if (pending.isEmpty()) return;
    for (var p : app.server.getPlayerList().getPlayers()) {
      var r = pending.get(p.getUUID());
      if (r != null && r.expires <= clock.getAsLong()) {
        pending.remove(p.getUUID());
        Teleports.tell(p, "Payment request expired.");
        refresh(p);
      }
    }
  }
}
