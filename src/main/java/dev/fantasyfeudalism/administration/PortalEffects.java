package dev.fantasyfeudalism.administration;

import java.util.*;
import net.minecraft.core.particles.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Reconcile chunk-loaded portal surfaces and show bounded colored motes around the animated plane.
 */
public final class PortalEffects {
  private final Administration app;
  private final Set<String> dirty = new LinkedHashSet<>();

  public PortalEffects(Administration app) {
    this.app = app;
  }

  public void refreshAll() {
    dirty.addAll(app.storage.state().portals.keySet());
  }

  @SubscribeEvent
  public void chunk(ChunkEvent.Load event) {
    if (!(event.getLevel() instanceof ServerLevel level) || app.server == null) return;
    var pos = event.getChunk().getPos();
    app.server.execute(
        () -> {
          if (app.server == null) return;
          for (var portal : app.storage.state().portals.values())
            if (portal.dimension.equals(level.dimension().location().toString())
                && pos.x >= portal.minX >> 4
                && pos.x <= portal.maxX >> 4
                && pos.z >= portal.minZ >> 4
                && pos.z <= portal.maxZ >> 4) dirty.add(portal.id);
        });
  }

  @SubscribeEvent
  public void loadedSurface(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
    if (app.server == null
        || !event.loadedFromDisk()
        || !(event.getLevel() instanceof ServerLevel)
        || !(event.getEntity() instanceof net.minecraft.world.entity.Display
            || event.getEntity() instanceof net.minecraft.world.entity.Interaction)) return;
    app.server.execute(
        () -> {
          if (app.server == null) return;
          for (String tag : event.getEntity().getTags())
            if (tag.startsWith("ffa.portal.")) {
              String id = tag.substring("ffa.portal.".length());
              if (app.storage.state().portals.containsKey(id)) dirty.add(id);
              else event.getEntity().discard();
            }
        });
  }

  @SubscribeEvent
  public void tick(ServerTickEvent.Post event) {
    if (!dirty.isEmpty()) {
      var id = dirty.iterator().next();
      dirty.remove(id);
      var portal = app.storage.state().portals.get(id);
      if (portal != null) Portals.visualize(app, portal);
    }
    if (!app.settings.portals || app.server.getTickCount() % 10 != 0) return;
    for (var portal : app.storage.state().portals.values()) {
      var level =
          app.server.getLevel(
              ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(portal.dimension)));
      if (level == null) continue;
      var color =
          Arrays.stream(DyeColor.values())
              .filter(d -> d.getName().equals(portal.color))
              .findFirst()
              .orElse(DyeColor.PURPLE);
      int rgb = color.getTextureDiffuseColor();
      var dust =
          new DustParticleOptions(
              new org.joml.Vector3f(
                  ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f),
              .85f);
      double x = (portal.minX + portal.maxX + 1.0) / 2,
          y = (portal.minY + portal.maxY + 1.0) / 2,
          z = (portal.minZ + portal.maxZ + 1.0) / 2;
      for (var player : level.players())
        if (player.distanceToSqr(x, y, z) < 64 * 64)
          level.sendParticles(
              player,
              dust,
              false,
              x,
              y,
              z,
              Math.min(12, (portal.maxX - portal.minX + 1) * (portal.maxY - portal.minY + 1)),
              (portal.maxX - portal.minX + 1) / 2.0,
              (portal.maxY - portal.minY + 1) / 2.0,
              (portal.maxZ - portal.minZ + 1) / 2.0,
              .015);
    }
  }
}
