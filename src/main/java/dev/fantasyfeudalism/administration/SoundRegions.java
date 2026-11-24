package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Sound overlays do not participate in protection, settlement claims, or player permissions. */
public final class SoundRegions {
  private static final class Playback {
    final SoundPack.Track track;
    final net.minecraft.world.entity.Entity anchor;
    final AudioEnvelope gain = new AudioEnvelope();
    float target;
    int fade;
    long nextLoop;

    Playback(SoundPack.Track track, net.minecraft.world.entity.Entity anchor) {
      this.track = track;
      this.anchor = anchor;
    }
  }

  private static final class Session {
    final String dimension;
    final Map<String, Playback> tracks = new LinkedHashMap<>();

    Session(String dimension) {
      this.dimension = dimension;
    }
  }

  private final Administration app;
  private final java.util.function.LongSupplier clock;
  private final Map<UUID, Session> sessions = new HashMap<>();

  public SoundRegions(Administration app) {
    this(app, System::nanoTime);
  }

  SoundRegions(Administration app, java.util.function.LongSupplier clock) {
    this.app = app;
    this.clock = clock;
  }

  public static SoundRegion select(
      Collection<SoundRegion> regions, String dimension, double x, double z) {
    return regions.stream()
        .filter(r -> r.contains(dimension, x, z))
        .min(
            Comparator.<SoundRegion>comparingInt(r -> r.priority)
                .reversed()
                .thenComparingLong(r -> ((long) r.maxX - r.minX + 1) * ((long) r.maxZ - r.minZ + 1))
                .thenComparing(r -> r.id))
        .orElse(null);
  }

  public SoundRegion get(String name) {
    return app.storage.state().soundRegions.values().stream()
        .filter(r -> r.id.equalsIgnoreCase(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown sound region."));
  }

  public void create(ServerPlayer player, String name, String track) throws Exception {
    Regions.checkName(name);
    Regions.checkId(track);
    if (app.storage.state().soundRegions.size() >= 256)
      throw new IllegalArgumentException("At most 256 sound regions are supported.");
    if (app.storage.state().soundRegions.keySet().stream()
        .anyMatch(id -> id.equalsIgnoreCase(name)))
      throw new IllegalArgumentException("Sound region already exists.");
    if (!app.soundPack.snapshot.tracks().containsKey(track))
      throw new IllegalArgumentException(
          "Unknown track. Put TRACK.ogg in config/ffa/sounds and run /ffa sound reload.");
    var selection = Commands.selection(player);
    var region = new SoundRegion();
    region.id = name;
    region.track = track;
    region.dimension = selection.dimension();
    region.minX = Math.min(selection.first().getX(), selection.second().getX());
    region.maxX = Math.max(selection.first().getX(), selection.second().getX());
    region.minZ = Math.min(selection.first().getZ(), selection.second().getZ());
    region.maxZ = Math.max(selection.first().getZ(), selection.second().getZ());
    region.validate();
    app.storage.update(s -> s.soundRegions.put(name, region));
  }

  /** Stops immediately for pack removal, world unload, and disconnect; walking exits use a fade. */
  public void stop(ServerPlayer player) {
    Session old = sessions.remove(player.getUUID());
    if (old != null) old.tracks.values().forEach(play -> remove(player, play));
  }

  private static net.minecraft.resources.ResourceLocation sound(Playback play) {
    return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
        "ffa", "ambience." + play.track.resource());
  }

  private static void remove(ServerPlayer player, Playback play) {
    player.connection.send(
        new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
            sound(play), net.minecraft.sounds.SoundSource.AMBIENT));
    player.connection.send(
        new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(
            play.anchor.getId()));
  }

  /** Vanilla uses linear attenuation with reference distance 0 and maximum distance 16. */
  static double distance(float gain) {
    return 16 * (1 - Math.max(.001f, gain));
  }

  private static void position(ServerPlayer player, Playback play, long now, boolean first) {
    double x = player.getX(),
        y = player.getEyeY() + distance(play.gain.value(now)),
        z = player.getZ();
    if (!first && play.anchor.getX() == x && play.anchor.getY() == y && play.anchor.getZ() == z)
      return;
    play.anchor.setPos(x, y, z);
    if (first) {
      player.connection.send(
          new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(
              play.anchor.getId(),
              play.anchor.getUUID(),
              x,
              y,
              z,
              0,
              0,
              net.minecraft.world.entity.EntityType.ITEM_DISPLAY,
              0,
              net.minecraft.world.phys.Vec3.ZERO,
              0));
    } else {
      player.connection.send(
          new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(play.anchor));
    }
  }

  private static void start(ServerPlayer player, Playback play, long now, boolean repeat) {
    if (repeat)
      player.connection.send(
          new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
              sound(play), net.minecraft.sounds.SoundSource.AMBIENT));
    player.connection.send(
        new net.minecraft.network.protocol.game.ClientboundSoundEntityPacket(
            net.minecraft.core.Holder.direct(
                net.minecraft.sounds.SoundEvent.createVariableRangeEvent(sound(play))),
            net.minecraft.sounds.SoundSource.AMBIENT,
            play.anchor,
            1,
            1,
            player.getRandom().nextLong()));
    play.nextLoop = now + (long) (play.track.info().seconds() * 1_000_000_000L);
  }

  @SubscribeEvent
  public void login(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) app.soundPack.offer(player);
  }

  @SubscribeEvent
  public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      sessions.remove(player.getUUID());
      app.soundPack.logout(player);
    }
  }

  @SubscribeEvent
  public void tick(ServerTickEvent.Post event) {
    for (var player : app.server.getPlayerList().getPlayers()) update(player);
  }

  void update(ServerPlayer player) {
    if (!app.settings.audioEnabled || !app.soundPack.ready(player)) {
      stop(player);
      return;
    }
    long now = clock.getAsLong();
    String dimension = player.level().dimension().location().toString();
    Session session = sessions.get(player.getUUID());
    // Minecraft drops entity-bound audio when unloading a dimension.
    if (session != null && !session.dimension.equals(dimension)) {
      stop(player);
      session = null;
    }
    SoundRegion region =
        select(app.storage.state().soundRegions.values(), dimension, player.getX(), player.getZ());
    SoundPack.Track track =
        region == null || region.volume == 0
            ? null
            : app.soundPack.snapshot.tracks().get(region.track);
    if (session == null) {
      if (track == null) return;
      session = new Session(dimension);
      sessions.put(player.getUUID(), session);
    }
    for (Playback play : session.tracks.values()) {
      if ((track == null || !track.resource().equals(play.track.resource())) && play.target != 0) {
        play.target = 0;
        play.gain.to(0, play.fade, now);
      }
    }
    if (track != null) {
      Playback play = session.tracks.get(track.resource());
      boolean first = play == null;
      if (first) {
        if (session.tracks.size() == 4) {
          var iterator = session.tracks.values().iterator();
          remove(player, iterator.next());
          iterator.remove();
        }
        // Never add this entity to a level: it is a private, empty, non-interactive audio anchor.
        var anchor =
            net.minecraft.world.entity.EntityType.ITEM_DISPLAY.create(player.serverLevel());
        if (anchor == null) return;
        play = new Playback(track, anchor);
        session.tracks.put(track.resource(), play);
      }
      if (first || play.target != region.volume || play.fade != region.fadeTicks) {
        play.target = region.volume;
        play.fade = region.fadeTicks;
        play.gain.to(region.volume, region.fadeTicks, now);
      }
      if (first) {
        position(player, play, now, true);
        start(player, play, now, false);
      }
    }
    var iterator = session.tracks.values().iterator();
    while (iterator.hasNext()) {
      Playback play = iterator.next();
      if (play.gain.silent(now)) {
        remove(player, play);
        iterator.remove();
        continue;
      }
      position(player, play, now, false);
      if (now >= play.nextLoop) start(player, play, now, true);
    }
    if (session.tracks.isEmpty()) sessions.remove(player.getUUID());
  }
}
