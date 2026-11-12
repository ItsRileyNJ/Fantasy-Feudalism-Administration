package dev.fantasyfeudalism.administration;

import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Per-viewer boundaries are live and remain enabled until cancelled or disconnected. */
public final class RegionVisuals {
  private final Administration app;
  private final Map<UUID, String> viewers = new HashMap<>();

  public RegionVisuals(Administration app) {
    this.app = app;
  }

  public void start(ServerPlayer player, String name) {
    viewers.put(player.getUUID(), app.regions.get(name).id);
    draw(player);
  }

  public void stop(ServerPlayer player) {
    viewers.remove(player.getUUID());
  }

  public boolean active(ServerPlayer player) {
    return viewers.containsKey(player.getUUID());
  }

  private void draw(ServerPlayer player) {
    String name = viewers.get(player.getUUID());
    if (name == null) return;
    Model.Region region;
    try {
      region = app.regions.get(name);
    } catch (IllegalArgumentException gone) {
      viewers.remove(player.getUUID());
      Teleports.tell(player, "Region deleted; boundary display stopped.");
      return;
    }
    if (!player.level().dimension().location().toString().equals(region.dimension)) return;
    if (!app.compat.admin(player) && !app.compat.permission(player, "ffa.region.edit")) {
      viewers.remove(player.getUUID());
      return;
    }
    for (int i = 0; i <= 24; i++) {
      double t = i / 24.0;
      double[][] points = {
        {region.minX + (region.maxX + 1.0 - region.minX) * t, region.minZ},
        {region.minX + (region.maxX + 1.0 - region.minX) * t, region.maxZ + 1.0},
        {region.minX, region.minZ + (region.maxZ + 1.0 - region.minZ) * t},
        {region.maxX + 1.0, region.minZ + (region.maxZ + 1.0 - region.minZ) * t}
      };
      for (double[] point : points)
        player
            .serverLevel()
            .sendParticles(
                player,
                ParticleTypes.END_ROD,
                true,
                point[0],
                player.getY() + .3,
                point[1],
                1,
                0,
                0,
                0,
                0);
    }
  }

  public void refresh() {
    for (var player : app.server.getPlayerList().getPlayers())
      if (viewers.containsKey(player.getUUID())) draw(player);
  }

  @SubscribeEvent
  public void tick(ServerTickEvent.Post e) {
    if (app.server.getTickCount() % 10 == 0) refresh();
  }

  @SubscribeEvent
  public void logout(PlayerEvent.PlayerLoggedOutEvent e) {
    viewers.remove(e.getEntity().getUUID());
  }
}
