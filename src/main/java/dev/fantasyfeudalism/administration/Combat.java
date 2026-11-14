package dev.fantasyfeudalism.administration;

import java.util.*;
import java.util.function.LongSupplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Actual PvP hits lock victim travel; personal PvP consent and toggle cooldown are separate. */
public final class Combat {
  static final long TOGGLE_COOLDOWN = 300_000;
  private final Administration app;
  private final LongSupplier clock, wallClock;
  private final Map<UUID, Long> travel = new HashMap<>(), activity = new HashMap<>();

  public Combat(Administration app) {
    this(app, Teleports::now, System::currentTimeMillis);
  }

  Combat(Administration app, LongSupplier clock, LongSupplier wallClock) {
    this.app = app;
    this.clock = clock;
    this.wallClock = wallClock;
  }

  public boolean enabled(ServerPlayer player) {
    var data = app.storage.player(player.getUUID());
    return data == null || data.pvpEnabled == null ? app.settings.pvpDefault : data.pvpEnabled;
  }

  public static ServerPlayer attacker(Entity source) {
    for (int i = 0; i < 4 && source != null; i++) {
      if (source instanceof ServerPlayer player) return player;
      if (source instanceof Projectile projectile) source = projectile.getOwner();
      else if (source instanceof AreaEffectCloud cloud) source = cloud.getOwner();
      else return null;
    }
    return null;
  }

  public boolean denied(Entity target, Entity source) {
    if (!(target instanceof ServerPlayer victim)) return false;
    var attacker = attacker(source);
    return attacker != null
        && !attacker.getUUID().equals(victim.getUUID())
        && (!enabled(attacker) || !enabled(victim));
  }

  public String travelError(ServerPlayer player) {
    if (!app.settings.combatTravel
        || app.compat.admin(player)
        || app.moderation != null && app.moderation.forcing(player)) return null;
    long left = remaining(travel, player.getUUID());
    return left == 0
        ? null
        : "You cannot teleport for " + time(left) + " after being hit by a player.";
  }

  public void requireTravel(ServerPlayer player) {
    String error = travelError(player);
    if (error != null) throw new IllegalArgumentException(error);
  }

  private long remaining(Map<UUID, Long> timers, UUID id) {
    long until = timers.getOrDefault(id, Long.MIN_VALUE);
    long now = clock.getAsLong();
    if (until <= now) {
      timers.remove(id);
      return 0;
    }
    return until - now;
  }

  private static String time(long millis) {
    long seconds = (millis + 999) / 1000;
    return seconds < 60 ? seconds + "s" : seconds / 60 + "m " + seconds % 60 + "s";
  }

  public void set(ServerPlayer player, boolean enabled) throws Exception {
    if (enabled(player) == enabled) {
      Teleports.tell(player, enabled ? "PvP is already enabled." : "PvP is already disabled.");
      return;
    }
    boolean admin = app.compat.admin(player);
    if (!admin) {
      long combat = remaining(activity, player.getUUID());
      if (combat > 0)
        throw new IllegalArgumentException(
            "You cannot change PvP for " + time(combat) + " while in combat.");
      long cooldown = app.storage.player(player.getUUID()).pvpToggleAfter - wallClock.getAsLong();
      if (cooldown > 0)
        throw new IllegalArgumentException("You can change PvP again in " + time(cooldown) + ".");
    }
    long after = admin ? 0 : wallClock.getAsLong() + TOGGLE_COOLDOWN;
    app.storage.update(
        s -> {
          var data = s.players.get(player.getUUID().toString());
          data.pvpEnabled = enabled;
          data.pvpToggleAfter = after;
        });
    app.storage.audit(
        app.settings,
        player.getUUID().toString(),
        "pvp " + (enabled ? "on" : "off"),
        Teleports.point(player).describe(),
        "SUCCESS persisted");
    Teleports.tell(player, enabled ? "PvP enabled." : "PvP disabled.");
  }

  public String status(ServerPlayer player) {
    long wait =
        app.compat.admin(player)
            ? 0
            : Math.max(
                remaining(activity, player.getUUID()),
                Math.max(
                    0,
                    app.storage.player(player.getUUID()).pvpToggleAfter - wallClock.getAsLong()));
    return "PvP: "
        + (enabled(player) ? "enabled." : "disabled.")
        + (wait > 0 ? " Change available in " + time(wait) + "." : "");
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void incoming(LivingIncomingDamageEvent event) {
    if (denied(event.getEntity(), event.getSource().getEntity())) event.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void impact(ProjectileImpactEvent event) {
    if (event.getRayTraceResult() instanceof EntityHitResult hit
        && denied(hit.getEntity(), event.getProjectile())) event.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void attack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
    if (denied(event.getTarget(), event.getEntity())) event.setCanceled(true);
  }

  @SubscribeEvent
  public void hurt(LivingDamageEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer victim) || victim instanceof FakePlayer) return;
    var attacker = attacker(event.getSource().getEntity());
    if (attacker == null
        || attacker instanceof FakePlayer
        || attacker == victim
        || event.getNewDamage() <= 0
            && event.getReduction(DamageContainer.Reduction.ABSORPTION) <= 0) return;
    hit(victim, attacker);
  }

  public void effectHit(LivingEntity target, Entity source) {
    if (target instanceof ServerPlayer victim) {
      var attacker = attacker(source);
      if (attacker != null) hit(victim, attacker);
    }
  }

  private void hit(ServerPlayer victim, ServerPlayer attacker) {
    if (victim instanceof FakePlayer
        || attacker instanceof FakePlayer
        || victim.getUUID().equals(attacker.getUUID())) return;
    long until = clock.getAsLong() + app.settings.combatSeconds * 1000L;
    travel.put(victim.getUUID(), until);
    activity.put(victim.getUUID(), until);
    activity.put(attacker.getUUID(), until);
    if (app.settings.combatTravel
        && !app.compat.admin(victim)
        && !(app.moderation != null && app.moderation.forcing(victim)))
      app.teleports.cancelForCombat(victim);
  }

  void clear(ServerPlayer player) {
    travel.remove(player.getUUID());
    activity.remove(player.getUUID());
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public void death(LivingDeathEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) clear(player);
  }

  @SubscribeEvent
  public void tick(ServerTickEvent.Post event) {
    if (app.server.getTickCount() % 200 != 0) return;
    long now = clock.getAsLong();
    travel.values().removeIf(until -> until <= now);
    activity.values().removeIf(until -> until <= now);
  }
}
