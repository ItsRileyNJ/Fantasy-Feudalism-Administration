package dev.fantasyfeudalism.administration;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

/**
 * Immutable pack snapshots are shared by the HTTP thread; administrators provide only Vorbis data.
 */
public final class SoundPack {
  public static volatile SoundPack active;
  static final int MAX_FILE = 16 * 1024 * 1024, MAX_TOTAL = 64 * 1024 * 1024, MAX_TRACKS = 64;

  public record Track(String id, String resource, OggInfo info) {}

  public record Snapshot(
      byte[] bytes, String sha1, UUID id, String path, Map<String, Track> tracks) {}

  private record Offer(UUID id, boolean ready) {}

  final Administration app;
  final Path directory;
  volatile Snapshot snapshot;
  private final Map<UUID, Offer> offered = new HashMap<>();

  public SoundPack(Administration app) throws Exception {
    this(app, app.settingsFile.getParent().resolve("sounds"));
  }

  SoundPack(Administration app, Path directory) throws Exception {
    this.app = app;
    this.directory = directory;
    Files.createDirectories(directory);
    if (!Files.exists(directory.resolve("README.txt")))
      Storage.atomic(
          directory.resolve("README.txt"),
          "Put mono Ogg Vorbis .ogg files here, named with lowercase letters, digits, _ or -.\n"
              + "Use /ffa sound reload, then /ffa soundregion create NAME TRACK after /ffa pos1 and"
              + " pos2.\n"
              + "1 second to 1 hour per track; 16 MiB per file, 64 MiB total, 64 tracks.\n"
              + "Players only need to accept the audio resource pack. FFA stays server-only.\n");
    snapshot = build(Map.of());
    active = this;
    try {
      install(read(directory));
    } catch (Exception ex) {
      Administration.LOG.error(
          "Region audio was not loaded; fix config/ffa/sounds and run /ffa sound reload", ex);
    }
  }

  static Snapshot read(Path directory) throws Exception {
    var files = new TreeMap<String, byte[]>();
    long total = 0;
    try (var stream = Files.list(directory)) {
      for (Path file : stream.sorted().toList()) {
        String name = file.getFileName().toString();
        if (!name.endsWith(".ogg")) continue;
        String id = name.substring(0, name.length() - 4);
        Regions.checkId(id);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
          throw new IllegalArgumentException("Audio files must be regular files: " + name);
        long size = Files.size(file);
        total += size;
        if (size > MAX_FILE || total > MAX_TOTAL || files.size() >= MAX_TRACKS)
          throw new IllegalArgumentException(
              "Audio limit: 16 MiB per file, 64 MiB total, 64 tracks.");
        byte[] bytes;
        try (var input = Files.newInputStream(file)) {
          bytes = input.readNBytes((int) size + 1);
        }
        if (bytes.length != size)
          throw new IllegalArgumentException("Audio changed during reload: " + name);
        files.put(id, bytes);
      }
    }
    return build(files);
  }

  static Snapshot build(Map<String, byte[]> files) throws Exception {
    var tracks = new TreeMap<String, Track>();
    var sounds = new JsonObject();
    var bytes = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      entry(
          zip,
          "pack.mcmeta",
          "{\"pack\":{\"pack_format\":34,\"description\":\"FFA region ambience\"}}"
              .getBytes(StandardCharsets.UTF_8));
      for (var file : new TreeMap<>(files).entrySet()) {
        Regions.checkId(file.getKey());
        var info = OggInfo.read(file.getValue());
        if (info.channels() != 1)
          throw new IllegalArgumentException(
              file.getKey() + ": convert to mono Ogg/Vorbis for fades.");
        if (info.seconds() < 1 || info.seconds() > 3600)
          throw new IllegalArgumentException(
              file.getKey() + ": tracks must last 1 second to 1 hour.");
        String resource = file.getKey() + "_" + digest("SHA-256", file.getValue()).substring(0, 16);
        tracks.put(file.getKey(), new Track(file.getKey(), resource, info));
        entry(zip, "assets/ffa/sounds/ambience/" + resource + ".ogg", file.getValue());
        var event = new JsonObject();
        var choices = new JsonArray();
        var sound = new JsonObject();
        sound.addProperty("name", "ffa:ambience/" + resource);
        sound.addProperty("stream", true);
        sound.addProperty("attenuation_distance", 16);
        choices.add(sound);
        event.add("sounds", choices);
        sounds.add("ambience." + resource, event);
      }
      entry(zip, "assets/ffa/sounds.json", sounds.toString().getBytes(StandardCharsets.UTF_8));
    }
    byte[] data = bytes.toByteArray();
    String sha = digest("SHA-1", data);
    return new Snapshot(
        data,
        sha,
        UUID.nameUUIDFromBytes(data),
        "/ffa/region-audio/" + sha + ".zip",
        Map.copyOf(tracks));
  }

  private static String digest(String algorithm, byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
  }

  private static void entry(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
    var entry = new ZipEntry(name);
    entry.setTime(0);
    zip.putNextEntry(entry);
    zip.write(bytes);
    zip.closeEntry();
  }

  private void install(Snapshot next) throws Exception {
    Path output = app.storage.directory.resolve("sound-pack/ffa-sounds.zip");
    Files.createDirectories(output.getParent());
    Path temporary = Files.createTempFile(output.getParent(), ".ffa-sounds-", ".zip");
    try {
      Files.write(temporary, next.bytes);
      Files.move(
          temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
    snapshot = next;
  }

  public void reload() throws Exception {
    Snapshot next = read(directory);
    if (next.id.equals(snapshot.id)) return;
    install(next);
    for (var player : app.server.getPlayerList().getPlayers()) {
      app.soundRegions.stop(player);
      remove(player);
      offer(player);
    }
  }

  public boolean enabled() {
    return app.settings.audioEnabled && !snapshot.tracks.isEmpty();
  }

  public boolean ready(ServerPlayer player) {
    var offer = offered.get(player.getUUID());
    return enabled() && offer != null && offer.id.equals(snapshot.id) && offer.ready;
  }

  public void offer(ServerPlayer player) {
    if (!enabled()) return;
    var old = offered.get(player.getUUID());
    if (old != null && old.id.equals(snapshot.id)) return;
    String url = app.settings.audioPackUrl;
    if (url.isEmpty()) {
      String base = PortalPack.base(player.connection.getConnection());
      if (base == null) return;
      url = base + snapshot.path;
    }
    offered.put(player.getUUID(), new Offer(snapshot.id, false));
    player.connection.send(
        new ClientboundResourcePackPushPacket(
            snapshot.id,
            url,
            snapshot.sha1,
            false,
            Optional.of(Component.literal("Download this server's village and region ambience."))));
  }

  public void remove(ServerPlayer player) {
    var offer = offered.remove(player.getUUID());
    if (offer != null)
      player.connection.send(new ClientboundResourcePackPopPacket(Optional.of(offer.id)));
  }

  public void logout(ServerPlayer player) {
    offered.remove(player.getUUID());
  }

  public void close() {
    if (active == this) active = null;
    offered.clear();
  }

  public static void response(
      ServerCommonPacketListenerImpl listener, ServerboundResourcePackPacket packet) {
    var pack = active;
    if (pack == null
        || !(listener instanceof net.minecraft.server.network.ServerGamePacketListenerImpl game))
      return;
    var offer = pack.offered.get(game.player.getUUID());
    if (offer == null || !offer.id.equals(packet.id()) || !packet.id().equals(pack.snapshot.id))
      return;
    if (packet.action() == ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED)
      pack.offered.put(game.player.getUUID(), new Offer(offer.id, true));
    else if (packet.action().isTerminal()) {
      pack.offered.remove(game.player.getUUID());
      pack.app.soundRegions.stop(game.player);
    }
  }
}
