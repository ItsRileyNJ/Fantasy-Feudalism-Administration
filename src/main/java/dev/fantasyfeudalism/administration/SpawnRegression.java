package dev.fantasyfeudalism.administration;

import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;

final class SpawnRegression {
  static void verify(GameTestHelper h, Administration app) throws Exception {
    var previous = app.spawnProtection;
    int duration = app.settings.spawnInvulnerabilitySeconds;
    var now = new AtomicLong(1000);
    var shield = new SpawnProtection(app, now::get);
    NeoForge.EVENT_BUS.unregister(previous);
    NeoForge.EVENT_BUS.register(shield);
    app.spawnProtection = shield;
    ServerPlayer player = null;
    var regions = app.regions.copy();
    try {
      app.regions.edit(List.of(), app.server, app.settings, app.compat);
      app.settings.spawnInvulnerabilitySeconds = 8;
      player = RegressionTests.player(h, new GameProfile(UUID.randomUUID(), "ffaShield"), true);
      player.setGameMode(GameType.SURVIVAL);
      player.setPos(h.absoluteVec(new Vec3(12.5, 1, 12.5)));
      app.protection.update(player);
      h.assertTrue(shield.active(player), "Real login starts spawn protection");
      float health = player.getHealth();
      for (var damage :
          List.of(
              player.damageSources().fall(),
              player.damageSources().lava(),
              player.damageSources().fellOutOfWorld(),
              player.damageSources().genericKill())) {
        player.invulnerableTime = 0;
        h.assertFalse(player.hurt(damage, 1000), "Shield stops environmental and bypass damage");
        h.assertTrue(
            player.getHealth() == health && player.isAlive(), "Protected health is unchanged");
      }
      var arrow = new Arrow(EntityType.ARROW, h.getLevel());
      h.assertTrue(
          EventHooks.onProjectileImpact(arrow, new EntityHitResult(player)),
          "Spawn shield stops projectile impact before side effects");
      app.server
          .getPlayerList()
          .getOps()
          .add(
              new net.minecraft.server.players.ServerOpListEntry(
                  player.getGameProfile(), 4, false));
      h.assertTrue(shield.active(player), "Administrators also receive the spawn shield");
      player.kill();
      h.assertTrue(player.isAlive(), "Direct death fallback respects spawn shield");
      app.server.getPlayerList().deop(player.getGameProfile());
      now.addAndGet(7999);
      h.assertTrue(shield.active(player), "Shield remains active just before eight seconds");
      now.incrementAndGet();
      h.assertFalse(shield.active(player), "Shield expires exactly at eight seconds");
      player.invulnerableTime = 0;
      h.assertTrue(
          player.hurt(player.damageSources().fall(), 2), "Normal damage resumes after expiry");
      player.kill();
      h.assertFalse(player.isAlive(), "Expired protection permits death");
      player = app.server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
      h.assertTrue(shield.active(player), "Actual death respawn renews the shield");
      app.server
          .getPlayerList()
          .getOps()
          .add(
              new net.minecraft.server.players.ServerOpListEntry(
                  player.getGameProfile(), 4, false));
      app.adminPowers.smite(player, player);
      h.assertFalse(player.isAlive(), "Intentional administrator smite overrides the spawn shield");
      player = app.server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
      app.server.getPlayerList().deop(player.getGameProfile());
      app.settings.spawnInvulnerabilitySeconds = 1;
      shield.start(player);
      now.addAndGet(1000);
      player.invulnerableTime = 0;
      h.assertTrue(
          player.hurt(player.damageSources().fall(), 2),
          "One-second setting replaces vanilla's longer grace period");
      shield.respawn(
          new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(
              player, true));
      h.assertFalse(shield.active(player), "End return does not renew the shield");
      app.settings.spawnInvulnerabilitySeconds = 0;
      shield.start(player);
      h.assertFalse(shield.active(player), "Zero disables the shield");
      player.invulnerableTime = 0;
      h.assertTrue(player.hurt(player.damageSources().fall(), 2), "Zero allows immediate damage");
      app.settings.spawnInvulnerabilitySeconds = 8;
      shield.start(player);
      app.server.getPlayerList().remove(player);
      h.assertFalse(shield.active(player), "Logout clears temporary protection");
      player = null;
    } finally {
      if (player != null) {
        app.server.getPlayerList().deop(player.getGameProfile());
        app.server.getPlayerList().remove(player);
      }
      app.settings.spawnInvulnerabilitySeconds = duration;
      app.regions.edit(regions, app.server, app.settings, app.compat);
      NeoForge.EVENT_BUS.unregister(shield);
      NeoForge.EVENT_BUS.register(previous);
      app.spawnProtection = previous;
    }
  }
}
