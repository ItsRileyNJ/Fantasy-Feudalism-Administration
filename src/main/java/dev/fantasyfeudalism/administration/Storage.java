package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;
import static java.nio.file.StandardOpenOption.*;

import com.google.gson.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.function.Consumer;

public final class Storage {
  public static final Gson JSON =
      new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
  public final Path directory;
  private State state;
  private boolean healthy = true;
  private final java.util.Map<String, Long> securityTimes = new java.util.HashMap<>();

  public Storage(Path directory) throws Exception {
    this.directory = directory;
    Files.createDirectories(directory);
    Path p = directory.resolve("state.json");
    state = Files.exists(p) ? JSON.fromJson(Files.readString(p), State.class) : new State();
    if (state == null
        || state.version != 1
        || state.worldBorders == null
        || state.rulesRoom == null
        || state.npcShops == null
        || state.players == null
        || state.warps == null
        || state.portals == null
        || state.trades == null
        || state.shopSigns == null
        || state.jailCells == null)
      throw new IOException(
          "Invalid state.json; restore an offline backup. File was not modified.");
    for (var entry : state.worldBorders.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null)
        throw new IOException("Invalid world border in state.json.");
      entry.getValue().validate(29999984);
    }
  }

  public State state() {
    return state;
  }

  public State copy() {
    return JSON.fromJson(JSON.toJson(state), State.class);
  }

  public void update(Consumer<State> edit) throws IOException {
    State candidate = copy();
    edit.accept(candidate);
    commit(candidate);
  }

  public void commit(State candidate) throws IOException {
    if (!healthy)
      throw new IOException(
          "State storage previously failed; restart and recover before further changes.");
    try {
      atomic(directory.resolve("state.json"), JSON.toJson(candidate));
      state = candidate;
    } catch (IOException e) {
      healthy = false;
      throw e;
    }
  }

  public PlayerData player(java.util.UUID id) {
    return state.players.get(id.toString());
  }

  public void ensure(java.util.UUID id, Settings settings) throws IOException {
    if (player(id) == null)
      update(
          s -> {
            var p = new PlayerData();
            p.balance = settings.startingBalance;
            s.players.put(id.toString(), p);
          });
  }

  public static void atomic(Path destination, String content) throws IOException {
    Files.createDirectories(destination.toAbsolutePath().getParent());
    Path temp = Files.createTempFile(destination.toAbsolutePath().getParent(), ".ffa-", ".tmp");
    try {
      try (var channel = FileChannel.open(temp, WRITE, TRUNCATE_EXISTING)) {
        ByteBuffer b = StandardCharsets.UTF_8.encode(content);
        while (b.hasRemaining()) channel.write(b);
        channel.force(true);
      }
      Files.move(
          temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      // Windows' NIO provider rejects directory FileChannels with AccessDeniedException.
      // The temporary file is still forced above and replacement must remain atomic.
      // Do not catch IOException here: real write, move and supported directory-sync failures
      // must still propagate so that commit() locks the store after an I/O failure.
      if (File.separatorChar != '\\') {
        try (var channel = FileChannel.open(destination.toAbsolutePath().getParent(), READ)) {
          channel.force(true);
        }
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  public void audit(Settings c, String actor, String action, String where, String result) {
    if (!c.audit) return;
    writeLog(c, "audit", actor, action, where, result);
  }

  public void security(Settings c, String actor, String action, String where, String result) {
    if (!c.security) return;
    long time = System.nanoTime() / 1_000_000;
    Long previous = securityTimes.get(actor);
    if (previous != null && time - previous < 2000) return;
    if (securityTimes.size() > 4096) securityTimes.clear();
    securityTimes.put(actor, time);
    writeLog(c, "security", actor, action, where, result);
  }

  private void writeLog(
      Settings c, String kind, String actor, String action, String where, String result) {
    try {
      Path log = directory.resolve(kind + ".log");
      if (Files.exists(log) && Files.size(log) > c.maxLog) {
        for (int i = c.retention - 1; i >= 1; i--) {
          Path from = directory.resolve(kind + "." + i + ".log"),
              to = directory.resolve(kind + "." + (i + 1) + ".log");
          if (Files.exists(from)) Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(log, directory.resolve(kind + ".1.log"), StandardCopyOption.REPLACE_EXISTING);
      }
      String line =
          Instant.now()
              + " actor="
              + clean(actor)
              + " action="
              + clean(action)
              + " where="
              + clean(where)
              + " result="
              + clean(result)
              + "\n";
      Files.writeString(log, line, CREATE, APPEND);
    } catch (IOException e) {
      Administration.LOG.error("Cannot write administration audit", e);
    }
  }

  private static String clean(String s) {
    return s.replace('\n', ' ').replace('\r', ' ');
  }

  public String snapshot(Path settings, Path regions) throws IOException {
    if (!state.trades.isEmpty())
      throw new IOException("Finish/recover pending trades before backup.");
    String id = Instant.now().toString().replace(':', '-');
    Path target = directory.resolve("backups").resolve(id);
    Files.createDirectories(target);
    atomic(target.resolve("state.json"), JSON.toJson(state));
    Files.copy(settings, target.resolve("server.toml"));
    Files.copy(regions, target.resolve("regions.toml"));
    Path ranks = directory.resolve("ranks.json");
    if (Files.exists(ranks)) Files.copy(ranks, target.resolve("ranks.json"));
    atomic(
        target.resolve("README.txt"),
        "Offline recovery only. Stop the server. Restore configuration/regions separately. Economy"
            + " state must be restored together with matching vanilla playerdata from the SAME"
            + " stopped-server backup. This snapshot alone does not include inventories or world"
            + " blocks. Never roll back balances independently of traded items.\n");
    return id;
  }
}
