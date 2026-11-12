package dev.fantasyfeudalism.administration;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class AdminPowers {
  private final Administration app;
  private final Set<UUID> smiting = new HashSet<>();

  public AdminPowers(Administration app) {
    this.app = app;
  }

  public boolean smiting(UUID id) {
    return smiting.contains(id);
  }

  public boolean god(ServerPlayer p) {
    var d = app.storage.player(p.getUUID());
    return d != null && d.god && app.compat.admin(p);
  }

  public void toggle(ServerPlayer p) throws Exception {
    if (!app.compat.admin(p)) throw new IllegalArgumentException("Administrator only.");
    boolean enabled = !app.storage.player(p.getUUID()).god;
    app.storage.update(
        s -> {
          var d = s.players.get(p.getUUID().toString());
          if (enabled) d.godPreviousInvulnerable = p.getAbilities().invulnerable;
          d.god = enabled;
        });
    update(p);
    Teleports.tell(p, enabled ? "God mode enabled." : "God mode disabled.");
  }

  public void smite(ServerPlayer actor, ServerPlayer target) {
    if (!app.compat.admin(actor)) throw new IllegalArgumentException("Administrator only.");
    var lightning = EntityType.LIGHTNING_BOLT.create(target.serverLevel());
    if (lightning != null) {
      lightning.moveTo(target.position());
      lightning.setVisualOnly(true);
      lightning.setCause(actor);
      target.serverLevel().addFreshEntity(lightning);
    }
    smiting.add(target.getUUID());
    try {
      target.kill();
    } finally {
      smiting.remove(target.getUUID());
    }
    app.storage.audit(
        app.settings,
        actor.getUUID().toString(),
        "smite " + target.getUUID(),
        Teleports.point(target).describe(),
        target.isAlive() ? "BLOCKED" : "SUCCESS");
    if (target.isAlive())
      throw new IllegalArgumentException("Another server rule prevented the kill.");
    Teleports.tell(actor, "Player smitten.");
  }

  private void update(ServerPlayer p) {
    var d = app.storage.player(p.getUUID());
    if (d == null) return;
    if (d.god && !app.compat.admin(p))
      try {
        active.add(p.getUUID());
        app.storage.update(s -> s.players.get(p.getUUID().toString()).god = false);
        d = app.storage.player(p.getUUID());
      } catch (Exception error) {
        return;
      }
    if (d.god) {
      if (!p.getAbilities().invulnerable) {
        p.getAbilities().invulnerable = true;
        p.onUpdateAbilities();
      }
    } else if (active.remove(p.getUUID())) {
      p.getAbilities().invulnerable =
          p.isCreative() || p.isSpectator() || d.godPreviousInvulnerable;
      p.onUpdateAbilities();
    }
    if (d.god) active.add(p.getUUID());
  }

  private final Set<UUID> active = new HashSet<>();

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void damage(LivingIncomingDamageEvent e) {
    if (e.getEntity() instanceof ServerPlayer p && god(p) && !smiting(p.getUUID()))
      e.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void death(LivingDeathEvent e) {
    if (e.getEntity() instanceof ServerPlayer p && god(p) && !smiting(p.getUUID())) {
      e.setCanceled(true);
      p.setHealth(p.getMaxHealth());
    }
  }

  @SubscribeEvent
  public void login(PlayerEvent.PlayerLoggedInEvent e) {
    if (e.getEntity() instanceof ServerPlayer p) update(p);
  }

  @SubscribeEvent
  public void respawn(PlayerEvent.PlayerRespawnEvent e) {
    if (e.getEntity() instanceof ServerPlayer p) update(p);
  }

  @SubscribeEvent
  public void tick(ServerTickEvent.Post e) {
    for (var p : app.server.getPlayerList().getPlayers()) update(p);
  }
}
