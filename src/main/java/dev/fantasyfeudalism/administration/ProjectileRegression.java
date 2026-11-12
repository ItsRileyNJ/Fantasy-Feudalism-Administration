package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;
import static dev.fantasyfeudalism.administration.RegressionTests.player;

import java.util.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.EventHooks;

/** Exercises incoming shots at their victim, independently of the projectile's launch position. */
public final class ProjectileRegression {
  public static final class ImpactProbe {
    final Projectile expected;
    boolean observed, cancelled;

    ImpactProbe(Projectile expected) {
      this.expected = expected;
    }

    @net.neoforged.bus.api.SubscribeEvent(
        priority = net.neoforged.bus.api.EventPriority.LOWEST,
        receiveCanceled = true)
    public void impact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent event) {
      if (event.getProjectile() == expected) {
        observed = true;
        cancelled = event.isCanceled();
      }
    }
  }

  static void verify(GameTestHelper h, ServerPlayer victim, Administration app) throws Exception {
    var regions = app.regions.copy();
    var inventory = victim.getInventory().save(new net.minecraft.nbt.ListTag());
    float health = victim.getHealth();
    int fire = victim.getRemainingFireTicks();
    boolean pvp = app.server.isPvpAllowed();
    // GameTest players were just logged in; clear vanilla's separate 60-tick respawn immunity.
    var spawnImmunity = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
    spawnImmunity.setAccessible(true);
    int immunityBefore = spawnImmunity.getInt(victim);
    Vec3 position = victim.position(), motion = victim.getDeltaMovement();
    var shooter = player(h, "ffaArcher");
    var spawned = new ArrayList<Projectile>();
    try {
      app.server.setPvpAllowed(true);
      spawnImmunity.setInt(victim, 0);
      victim.setGameMode(GameType.SURVIVAL);
      var spawn =
          app.regions.copy().stream().filter(r -> r.kind == Kind.SPAWN).findFirst().orElseThrow();
      spawn.flags.put(Flag.DAMAGE, false);
      spawn.flags.put(Flag.PVP, false);
      app.regions.edit(List.of(spawn), app.server, app.settings, app.compat);
      shooter.setGameMode(GameType.SURVIVAL);
      shooter.setPos(h.absoluteVec(new Vec3(12.5, 1, 4.5)));
      victim.setPos(h.absoluteVec(new Vec3(4.5, 1, 4.5)));
      app.protection.update(victim);
      victim.setHealth(victim.getMaxHealth());
      victim.invulnerableTime = 0;
      victim.clearFire();
      victim.setDeltaMovement(Vec3.ZERO);
      var arrow = new Arrow(h.getLevel(), shooter, new ItemStack(Items.TIPPED_ARROW), null);
      arrow.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
      arrow.igniteForSeconds(20);
      arrow.setPos(h.absoluteVec(new Vec3(9.5, 2, 4.5)));
      arrow.setDeltaMovement(-6, 0, 0);
      arrow.setNoGravity(true);
      h.getLevel().addFreshEntity(arrow);
      spawned.add(arrow);
      float before = victim.getHealth();
      var probe = new ImpactProbe(arrow);
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(probe);
      try {
        arrow.tick();
      } finally {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(probe);
      }
      h.assertTrue(
          probe.observed && probe.cancelled,
          "A real outside arrow reaches the victim and its impact event is cancelled");
      h.assertTrue(
          victim.getHealth() == before
              && !victim.isOnFire()
              && !victim.hasEffect(MobEffects.POISON)
              && victim.getDeltaMovement().equals(Vec3.ZERO),
          "Flaming tipped arrow fired from outside cannot damage, burn, poison or knock back a"
              + " spawn player");
      h.assertTrue(
          !arrow.isRemoved() && arrow.getDeltaMovement().x < 0,
          "Real arrow impact is cancelled before vanilla hit/deflection processing");
      var skeleton = EntityType.SKELETON.create(h.getLevel());
      skeleton.setPos(shooter.position());
      for (var owner : Arrays.asList(shooter, skeleton, null)) {
        var shot = new Arrow(EntityType.ARROW, h.getLevel());
        shot.setOwner(owner);
        shot.setPos(shooter.position());
        spawned.add(shot);
        h.assertTrue(
            EventHooks.onProjectileImpact(shot, new EntityHitResult(victim)),
            "Protected-player impacts are cancelled for player, mob and ownerless shots");
        victim.invulnerableTime = 0;
        h.assertFalse(
            victim.hurt(victim.damageSources().arrow(shot, owner), 6),
            "Incoming damage fallback blocks player, mob and ownerless arrows inside spawn");
        h.assertTrue(victim.getHealth() == before, "Blocked projectile damage preserves health");
      }
      var trident = new ThrownTrident(h.getLevel(), shooter, new ItemStack(Items.TRIDENT));
      spawned.add(trident);
      h.assertTrue(
          EventHooks.onProjectileImpact(trident, new EntityHitResult(victim))
              && !trident.isRemoved(),
          "Blocking a trident impact does not destroy the player's trident");
      var fireball = new SmallFireball(h.getLevel(), skeleton, new Vec3(-1, 0, 0));
      spawned.add(fireball);
      h.assertTrue(
          EventHooks.onProjectileImpact(fireball, new EntityHitResult(victim)),
          "Mob fireball impacts cannot run on protected spawn players");
      var potion = new ThrownPotion(h.getLevel(), shooter);
      spawned.add(potion);
      h.assertTrue(
          EventHooks.onProjectileImpact(potion, new EntityHitResult(victim)),
          "Direct thrown potion impacts respect protected spawn players");
      // Damage and PvP are independent switches.
      spawn.flags.put(Flag.DAMAGE, true);
      app.regions.edit(List.of(spawn), app.server, app.settings, app.compat);
      h.assertTrue(
          EventHooks.onProjectileImpact(trident, new EntityHitResult(victim)),
          "PvP-disabled spawn blocks player projectiles even when environmental damage is enabled");
      Region arena = new Region();
      arena.id = arena.name = "test_projectile_arena";
      arena.kind = Kind.CHILD;
      arena.parent = spawn.id;
      arena.dimension = spawn.dimension;
      var min = h.absolutePos(new net.minecraft.core.BlockPos(3, 1, 3));
      var max = h.absolutePos(new net.minecraft.core.BlockPos(6, 1, 6));
      arena.minX = min.getX();
      arena.minZ = min.getZ();
      arena.maxX = max.getX();
      arena.maxZ = max.getZ();
      arena.settlementOverride = true;
      arena.flags.put(Flag.DAMAGE, true);
      arena.flags.put(Flag.PVP, true);
      app.regions.edit(List.of(spawn, arena), app.server, app.settings, app.compat);
      h.assertFalse(
          EventHooks.onProjectileImpact(trident, new EntityHitResult(victim)),
          "Child arena damage/PvP overrides still allow incoming combat");
      victim.invulnerableTime = 0;
      h.assertTrue(
          victim.hurt(victim.damageSources().trident(trident, shooter), 4)
              && victim.getHealth() < before,
          "Permitted arena projectile damage really reaches health");
      shooter.setPos(h.absoluteVec(new Vec3(2.5, 1, 4.5)));
      h.assertTrue(
          EventHooks.onProjectileImpact(trident, new EntityHitResult(victim)),
          "A shooter in a PvP-disabled parent cannot fire into its combat child");
      shooter.setPos(h.absoluteVec(new Vec3(12.5, 1, 4.5)));
      victim.setPos(h.absoluteVec(new Vec3(12.5, 1, 12.5)));
      h.assertFalse(
          EventHooks.onProjectileImpact(trident, new EntityHitResult(victim)),
          "Ordinary wilderness projectile combat stays enabled");
      app.server
          .getPlayerList()
          .getOps()
          .add(
              new net.minecraft.server.players.ServerOpListEntry(
                  shooter.getGameProfile(), 4, false));
      victim.setPos(h.absoluteVec(new Vec3(2.5, 1, 4.5)));
      h.assertFalse(
          EventHooks.onProjectileImpact(trident, new EntityHitResult(victim)),
          "Administrator shooters retain the configured global administrator exemption");
    } finally {
      app.server.setPvpAllowed(pvp);
      spawnImmunity.setInt(victim, immunityBefore);
      for (var shot : spawned) shot.discard();
      app.server.getPlayerList().deop(shooter.getGameProfile());
      app.server.getPlayerList().remove(shooter);
      app.regions.edit(regions, app.server, app.settings, app.compat);
      victim.getInventory().load(inventory);
      victim.removeEffect(MobEffects.POISON);
      victim.setHealth(health);
      victim.setRemainingFireTicks(fire);
      victim.invulnerableTime = 0;
      victim.setPos(position);
      victim.setDeltaMovement(motion);
      app.protection.update(victim);
    }
  }
}
