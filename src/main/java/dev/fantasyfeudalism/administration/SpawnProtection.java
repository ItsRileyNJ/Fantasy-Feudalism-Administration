package dev.fantasyfeudalism.administration;

import dev.fantasyfeudalism.administration.mixin.SpawnImmunityAccess;
import java.util.*;
import java.util.function.LongSupplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Transient login/death protection, independent of ranks, regions and god mode. */
public final class SpawnProtection {
  private final Administration app;
  private final LongSupplier clock;
  private final Map<UUID, Long> deadlines = new HashMap<>();

  public SpawnProtection(Administration app) {
    this(app, Teleports::now);
  }

  SpawnProtection(Administration app, LongSupplier clock) {
    this.app = app;
    this.clock = clock;
  }

  void start(ServerPlayer player) {
    // Replace vanilla's separate three-second grace period so even 0 or 1 works.
    ((SpawnImmunityAccess) player).ffa$spawnImmunity(0);
    clear(player);
    if (app.settings.spawnInvulnerabilitySeconds > 0)
      deadlines.put(
          player.getUUID(), clock.getAsLong() + app.settings.spawnInvulnerabilitySeconds * 1000L);
  }

  void clear(ServerPlayer player) {
    deadlines.remove(player.getUUID());
  }

  boolean active(ServerPlayer player) {
    Long until = deadlines.get(player.getUUID());
    if (until == null) return false;
    if (clock.getAsLong() >= until) {
      clear(player);
      return false;
    }
    return !app.adminPowers.smiting(player.getUUID());
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public void login(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) start(player);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public void respawn(PlayerEvent.PlayerRespawnEvent event) {
    if (!event.isEndConquered() && event.getEntity() instanceof ServerPlayer player) start(player);
  }

  @SubscribeEvent
  public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) clear(player);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void damage(LivingIncomingDamageEvent event) {
    if (event.getEntity() instanceof ServerPlayer player && active(player)) event.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void death(LivingDeathEvent event) {
    if (event.getEntity() instanceof ServerPlayer player && active(player)) {
      event.setCanceled(true);
      player.setHealth(player.getMaxHealth());
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void projectile(ProjectileImpactEvent event) {
    if (event.getRayTraceResult() instanceof EntityHitResult hit
        && hit.getEntity() instanceof ServerPlayer player
        && active(player)) event.setCanceled(true);
  }
}
