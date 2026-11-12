package dev.fantasyfeudalism.administration;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** One return location: the latest real death or completed teleport. Respawn is not travel. */
public final class BackHistory {
  private final Administration app;

  private record Trace(Model.Point origin, int depth) {}

  private final Map<UUID, Trace> traces = new HashMap<>();
  private int suppressed;

  public BackHistory(Administration app) {
    this.app = app;
  }

  public void suppress() {
    suppressed++;
  }

  public void resume() {
    suppressed--;
  }

  public void begin(ServerPlayer p) {
    Trace trace = traces.get(p.getUUID());
    traces.put(
        p.getUUID(),
        trace == null
            ? new Trace(Teleports.point(p), 1)
            : new Trace(trace.origin, trace.depth + 1));
  }

  public void end(ServerPlayer p) {
    Trace trace = traces.get(p.getUUID());
    if (trace == null) return;
    if (trace.depth > 1) {
      traces.put(p.getUUID(), new Trace(trace.origin, trace.depth - 1));
      return;
    }
    traces.remove(p.getUUID());
    if (suppressed == 0 && p.isAlive() && different(trace.origin, Teleports.point(p)))
      record(p, trace.origin);
  }

  public static boolean different(Model.Point a, Model.Point b) {
    return !a.dimension.equals(b.dimension)
        || Math.abs(a.x - b.x) > 0.000001
        || Math.abs(a.y - b.y) > 0.000001
        || Math.abs(a.z - b.z) > 0.000001;
  }

  public void record(ServerPlayer p, Model.Point origin) {
    if (app.storage.player(p.getUUID()) == null
        || app.moderation != null && app.moderation.forcing(p)) return;
    try {
      app.storage.update(s -> s.players.get(p.getUUID().toString()).back = origin);
    } catch (Exception error) {
      Administration.LOG.error("Cannot save return location for {}", p.getUUID(), error);
      Teleports.tell(p, "Could not save your return location.");
    }
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public void death(LivingDeathEvent event) {
    if (event.getEntity() instanceof ServerPlayer p && app.storage.player(p.getUUID()) != null) {
      record(p, Teleports.point(p));
      app.teleports.cancel(p, false);
    }
  }
}
