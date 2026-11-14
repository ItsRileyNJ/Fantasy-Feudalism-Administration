package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.RegressionTests.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;

final class CombatRegression {
  static void verify(GameTestHelper h, Administration app) throws Exception {
    var state = app.storage.copy();
    var regions = app.regions.copy();
    var previous = app.combat;
    var clock = new AtomicLong(1000);
    var wall = new AtomicLong(1700000000000L);
    var combat = new Combat(app, clock::get, wall::get);
    boolean travel = app.settings.combatTravel,
        defaultPvp = app.settings.pvpDefault,
        pvp = app.server.isPvpAllowed(),
        commands = app.settings.publicCommands;
    int duration = app.settings.combatSeconds, warmup = app.settings.warmup;
    var attacker = player(h, "combatAttacker");
    var victim = player(h, "combatVictim");
    NeoForge.EVENT_BUS.unregister(previous);
    NeoForge.EVENT_BUS.register(combat);
    app.combat = combat;
    try {
      app.regions.edit(List.of(), app.server, app.settings, app.compat);
      app.server.setPvpAllowed(true);
      app.settings.combatTravel = app.settings.pvpDefault = app.settings.publicCommands = true;
      app.settings.combatSeconds = 30;
      attacker.setGameMode(GameType.SURVIVAL);
      victim.setGameMode(GameType.SURVIVAL);
      attacker.setPos(h.absoluteVec(new Vec3(10.5, 1, 12.5)));
      victim.setPos(h.absoluteVec(new Vec3(12.5, 1, 12.5)));
      var destination = Teleports.point(victim);
      destination.x += 2;
      UUID id = victim.getUUID();
      app.storage.update(
          s -> {
            s.spawn = destination;
            s.players.get(id.toString()).homes.put("CombatHome", destination);
            s.players.get(id.toString()).back = destination;
            var w = new Model.Warp();
            w.point = destination;
            s.warps.put("CombatWarp", w);
          });
      h.assertTrue(combat.enabled(victim), "PvP starts enabled for an unset preference");
      var safeRegion = new Model.Region();
      safeRegion.id = safeRegion.name = "combat_safe";
      safeRegion.kind = Model.Kind.SPAWN;
      safeRegion.dimension = h.getLevel().dimension().location().toString();
      safeRegion.minX = victim.blockPosition().getX() - 4;
      safeRegion.maxX = victim.blockPosition().getX() + 4;
      safeRegion.minZ = victim.blockPosition().getZ() - 4;
      safeRegion.maxZ = victim.blockPosition().getZ() + 4;
      safeRegion.settlementOverride = true;
      safeRegion.flags.put(Model.Flag.PVP, false);
      safeRegion.flags.put(Model.Flag.DAMAGE, false);
      app.regions.edit(List.of(safeRegion), app.server, app.settings, app.compat);
      h.assertFalse(
          victim.hurt(victim.damageSources().playerAttack(attacker), 2),
          "Safe-region PvP remains blocked");
      h.assertTrue(
          combat.travelError(victim) == null, "A region-cancelled hit creates no combat timer");
      app.regions.edit(List.of(), app.server, app.settings, app.compat);
      hit(h, attacker, victim);
      h.assertTrue(
          combat.travelError(victim) != null && combat.travelError(attacker) == null,
          "A real successful hit blocks victim travel rather than tagging outgoing attacks as"
              + " travel hits");
      for (String command :
          List.of(
              "ffa home CombatHome",
              "ffa warp CombatWarp",
              "ffa back",
              "ffa spawn",
              "ffa tpa combatAttacker",
              "ffa tpahere combatAttacker",
              "ffa tpaccept")) {
        h.assertTrue(
            command(app, victim, command) == 0, "Combat blocks travel command: " + command);
      }
      try {
        app.server
            .getCommands()
            .getDispatcher()
            .execute("tp @s ~ ~ ~", victim.createCommandSourceStack().withPermission(4));
        h.fail("Native travel command must also respect the victim combat timer");
      } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
        h.assertTrue(
            expected.getRawMessage().getString().contains("after being hit"),
            "Native combat error is specific");
      }
      rejects(h, () -> combat.set(attacker, false), "while in combat");
      var initialVictim = victim;
      rejects(h, () -> combat.set(initialVictim, false), "while in combat");
      clock.addAndGet(5000);
      hit(h, attacker, victim);
      clock.addAndGet(29999);
      h.assertTrue(
          combat.travelError(victim) != null, "A subsequent hit refreshes the complete timer");
      clock.incrementAndGet();
      h.assertTrue(
          combat.travelError(victim) == null, "Combat expires at its exact elapsed-time deadline");

      var damagingArrow = new Arrow(EntityType.ARROW, h.getLevel());
      damagingArrow.setOwner(attacker);
      victim.invulnerableTime = 0;
      h.assertTrue(
          victim.hurt(victim.damageSources().arrow(damagingArrow, attacker), 2)
              && combat.travelError(victim) != null,
          "A real attributed projectile hit also starts combat");
      combat.clear(victim);
      combat.clear(attacker);
      app.settings.warmup = 10;
      app.teleports.teleport(victim, destination, false);
      h.assertTrue(app.teleports.waiting.containsKey(id), "A real FFA warmup is queued");
      hit(h, attacker, victim);
      h.assertFalse(app.teleports.waiting.containsKey(id), "Being hit cancels the queued warmup");
      app.teleports.loading.put(
          id,
          new Teleports.Loading(
              destination,
              Teleports.point(victim),
              false,
              Teleports.now() + 15000,
              new CompletableFuture<>()));
      hit(h, attacker, victim);
      h.assertFalse(
          app.teleports.loading.containsKey(id),
          "Being hit cancels pending saved-chunk travel too");
      var position = victim.position();
      app.teleports.waiting.put(
          id, new Teleports.Waiting(destination, Teleports.point(victim), Teleports.now() - 1));
      app.teleports.tick(null);
      h.assertTrue(
          victim.position().equals(position),
          "Execution rechecks combat even for a previously queued job");
      app.settings.combatTravel = false;
      h.assertTrue(
          combat.travelError(victim) == null,
          "Travel restriction can be disabled in configuration");
      rejects(h, () -> combat.set(attacker, false), "while in combat");
      app.settings.combatTravel = true;
      combat.clear(victim);
      combat.clear(attacker);

      combat.set(victim, false);
      long after = app.storage.player(id).pvpToggleAfter;
      h.assertTrue(
          after == wall.get() + 300000
              && new Storage(app.storage.directory).player(id).pvpToggleAfter == after,
          "The five-minute toggle cooldown is durably saved");
      victim.invulnerableTime = attacker.invulnerableTime = 0;
      h.assertFalse(
          victim.hurt(victim.damageSources().playerAttack(attacker), 2),
          "PvP-off protects incoming damage");
      h.assertFalse(
          attacker.hurt(attacker.damageSources().playerAttack(victim), 2),
          "PvP-off also prevents outgoing damage");
      var arrow = new Arrow(EntityType.ARROW, h.getLevel());
      arrow.setOwner(attacker);
      h.assertTrue(
          EventHooks.onProjectileImpact(arrow, new EntityHitResult(victim)),
          "Owned projectile impact is cancelled before effects");
      h.assertFalse(
          victim.addEffect(new MobEffectInstance(MobEffects.POISON, 100), attacker),
          "Player poison cannot bypass PvP-off");
      var cloud = EntityType.AREA_EFFECT_CLOUD.create(h.getLevel());
      cloud.setOwner(attacker);
      h.assertFalse(
          victim.addEffect(new MobEffectInstance(MobEffects.POISON, 100), cloud),
          "Player-owned lingering effects cannot bypass PvP-off");
      h.assertTrue(
          victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100), attacker),
          "Beneficial player effects remain allowed");
      victim.invulnerableTime = 0;
      h.assertTrue(
          victim.hurt(victim.damageSources().fall(), 1) && combat.travelError(victim) == null,
          "Environmental damage neither uses PvP consent nor creates a player combat timer");
      var skeleton = EntityType.SKELETON.create(h.getLevel());
      victim.invulnerableTime = 0;
      h.assertTrue(
          victim.hurt(victim.damageSources().mobAttack(skeleton), 1)
              && combat.travelError(victim) == null,
          "Mob/NPC-style damage remains independent of personal PvP and combat tagging");
      var profile = victim.getGameProfile();
      app.server.getPlayerList().remove(victim);
      victim = player(h, profile);
      h.assertTrue(
          !combat.enabled(victim) && app.storage.player(id).pvpToggleAfter == after,
          "Preference and cooldown survive a real logout and login");
      var current = victim;
      wall.addAndGet(299999);
      rejects(h, () -> combat.set(current, true), "again in");
      wall.incrementAndGet();
      combat.set(victim, true);
      h.assertTrue(combat.enabled(victim), "Toggle becomes available exactly five minutes later");
      hit(h, attacker, victim);
      profile = victim.getGameProfile();
      app.server.getPlayerList().remove(victim);
      victim = player(h, profile);
      h.assertTrue(
          combat.travelError(victim) != null, "Relogging does not clear the active combat timer");
      combat.clear(victim);
      combat.clear(attacker);
      h.assertTrue(
          victim.addEffect(new MobEffectInstance(MobEffects.POISON, 100), attacker)
              && combat.travelError(victim) != null,
          "Successful harmful player effects count as combat");
      victim.removeEffect(MobEffects.POISON);
      app.server
          .getPlayerList()
          .getOps()
          .add(
              new net.minecraft.server.players.ServerOpListEntry(
                  victim.getGameProfile(), 4, false));
      h.assertTrue(
          combat.travelError(victim) == null, "Administrators retain the travel exemption");
      combat.set(victim, false);
      combat.set(victim, true);
      h.assertTrue(
          app.storage.player(id).pvpToggleAfter == 0,
          "Administrator toggle cooldown exemption remains explicit");
      app.server.getPlayerList().deop(victim.getGameProfile());
      combat.clear(victim);
      combat.clear(attacker);
      hit(h, attacker, victim);
      victim.kill();
      h.assertTrue(combat.travelError(victim) == null, "Death clears the old life combat timer");
      var tree = clientCommands(attacker, true).getChild("ffa").getChild("pvp");
      h.assertTrue(
          tree != null && tree.getChild("status") != null && tree.getChild("on") != null,
          "Actual normal-player command tree exposes PvP controls");
      app.settings.publicCommands = false;
      h.assertTrue(
          clientCommands(attacker, true).getChild("ffa").getChild("pvp") == null,
          "The PvP command respects the player-command permission fallback");
    } finally {
      app.server.getPlayerList().deop(victim.getGameProfile());
      app.server.getPlayerList().remove(victim);
      app.server.getPlayerList().remove(attacker);
      app.combat = previous;
      NeoForge.EVENT_BUS.unregister(combat);
      NeoForge.EVENT_BUS.register(previous);
      app.settings.combatTravel = travel;
      app.settings.combatSeconds = duration;
      app.settings.pvpDefault = defaultPvp;
      app.settings.publicCommands = commands;
      app.settings.warmup = warmup;
      app.server.setPvpAllowed(pvp);
      app.storage.commit(state);
      app.regions.edit(regions, app.server, app.settings, app.compat);
    }
  }

  private static void hit(GameTestHelper h, ServerPlayer attacker, ServerPlayer victim) {
    victim.invulnerableTime = 0;
    victim.setHealth(victim.getMaxHealth());
    h.assertTrue(
        victim.hurt(victim.damageSources().playerAttack(attacker), 2),
        "Real PvP hit reaches damage processing");
  }

  private static int command(Administration app, ServerPlayer player, String command)
      throws Exception {
    try {
      return app.server
          .getCommands()
          .getDispatcher()
          .execute(command, player.createCommandSourceStack());
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException denied) {
      return 0;
    }
  }

  interface Attempt {
    void run() throws Exception;
  }

  private static void rejects(GameTestHelper h, Attempt attempt, String message) throws Exception {
    try {
      attempt.run();
      h.fail("Expected rejection: " + message);
    } catch (IllegalArgumentException expected) {
      h.assertTrue(
          expected.getMessage().contains(message), "Expected human-readable combat rejection");
    }
  }
}
