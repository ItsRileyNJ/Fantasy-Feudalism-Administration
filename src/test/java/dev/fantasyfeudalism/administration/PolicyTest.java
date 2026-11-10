package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;

import java.nio.file.*;
import java.util.*;

public final class PolicyTest {
  private static int count;

  private static void check(boolean value, String message) {
    count++;
    if (!value) throw new AssertionError(message);
  }

  private static void rejects(Throwing action, String message) throws Exception {
    boolean rejected = false;
    try {
      action.run();
    } catch (IllegalArgumentException e) {
      rejected = true;
    }
    check(rejected, message);
  }

  interface Throwing {
    void run() throws Exception;
  }

  static Region region(String id, Kind kind, int low, int high) {
    Region r = new Region();
    r.id = id;
    r.name = id;
    r.kind = kind;
    r.dimension = "minecraft:overworld";
    r.minX = r.minZ = low;
    r.maxX = r.maxZ = high;
    return r;
  }

  public static void main(String[] args) throws Exception {
    Path root = Files.createTempDirectory("ffa-tests-");
    Path config = root.resolve("server.toml");
    Settings settings = Settings.load(config);
    check(!settings.dimension, "void dimension opt-in");
    check(!settings.defaults.get(Flag.HUNGER), "hunger frozen by default");
    for (var flag :
        List.of(
            Flag.DAMAGE,
            Flag.PVP,
            Flag.INTERACT,
            Flag.MOB_SPAWNING,
            Flag.FIRE,
            Flag.EXPLOSIONS,
            Flag.FLIGHT)) check(!settings.defaults.get(flag), "safe spawn default " + flag);
    check(
        settings.defaults.get(Flag.NPC_TRADING)
            && settings.defaults.get(Flag.SETTLEMENT_MANAGEMENT),
        "town/NPC exceptions");
    check(
        settings.currency.equals("Denar") && settings.startingBalance == 0, "whole Denar default");
    String valid = Files.readString(config);
    Files.writeString(config, valid.replace("maximum = 3", "maximum = -1"));
    rejects(() -> Settings.load(config), "invalid config rejected");
    check(settings.homeMax == 3, "previous candidate unchanged");
    Files.writeString(config, valid);
    Region parent = region("spawn", Kind.SPAWN, 0, 100),
        child = region("market", Kind.CHILD, 10, 20);
    child.parent = "spawn";
    Regions.validate(List.of(parent, child), null, settings, null);
    check(true, "legal child");
    check(
        parent.contains(0, 0) && parent.contains(100.999, 100.999) && !parent.contains(101, 20),
        "block edges use half-open player coordinates");
    var other = region("other", Kind.CHILD, 20, 25);
    other.parent = "spawn";
    rejects(
        () -> Regions.validate(List.of(parent, child, other), null, settings, null),
        "sibling overlap rejected");
    other.minX = other.minZ = 101;
    other.maxX = other.maxZ = 110;
    rejects(
        () -> Regions.validate(List.of(parent, other), null, settings, null),
        "outside parent rejected");
    rejects(
        () -> Regions.validate(List.of(parent, parent), null, settings, null),
        "duplicate IDs rejected");
    Path regionFile = root.resolve("regions.toml");
    Storage.atomic(regionFile, Regions.write(List.of(parent, child)));
    var roundTrip = Regions.read(regionFile);
    check(
        roundTrip.size() == 2 && roundTrip.get(1).parent.equals("spawn"),
        "human-readable region round trip");
    Regions manager = new Regions(regionFile);
    manager.reload(null, settings, null);
    check(!manager.resolve(manager.get("market"), Flag.PVP, settings), "inherit parent defaults");
    child.flags.put(Flag.PVP, true);
    manager.edit(List.of(parent, child), null, settings, null);
    check(manager.resolve(manager.get("market"), Flag.PVP, settings), "child explicit override");
    String previous = Files.readString(regionFile);
    Files.writeString(regionFile, previous + "\n# external edit\n");
    rejects(
        () -> manager.edit(List.of(parent), null, settings, null),
        "external edits cannot be overwritten");
    check(manager.all().size() == 2, "failed edit keeps active definitions");
    Storage store = new Storage(root.resolve("state"));
    UUID id = UUID.randomUUID();
    store.ensure(id, settings);
    store.update(s -> s.players.get(id.toString()).balance = 19);
    check(new Storage(root.resolve("state")).player(id).balance == 19, "durable balances");
    boolean failed = false;
    try {
      store.update(
          s -> {
            s.players.get(id.toString()).balance = 99;
            throw new IllegalArgumentException("failure");
          });
    } catch (IllegalArgumentException e) {
      failed = true;
    }
    check(
        failed && store.player(id).balance == 19,
        "failed mutation leaves memory and disk unchanged");
    Access access = new Access();
    access.mode = "unverified";
    rejects(() -> Compat.validateAccess(access), "unknown access fails closed");
    Storage broken = new Storage(root.resolve("broken"));
    Files.createDirectory(root.resolve("broken/state.json"));
    boolean ioFailed = false;
    try {
      broken.commit(new State());
    } catch (java.io.IOException expected) {
      ioFailed = true;
    }
    check(ioFailed, "I/O failure is reported");
    Files.delete(root.resolve("broken/state.json"));
    boolean locked = false;
    try {
      broken.commit(new State());
    } catch (java.io.IOException expected) {
      locked = true;
    }
    check(locked, "Further state writes fail closed after an I/O error");
    check(
        Commands.tokenize("shop set stone minecraft:stone 5 2")
            .equals(List.of("shop", "set", "stone", "minecraft:stone", "5", "2")),
        "Namespaced IDs parse completely");
    check(
        Commands.tokenize("region message spawn entry \"Welcome to Spawn!\"")
            .get(4)
            .equals("Welcome to Spawn!"),
        "Quoted messages preserve spaces");
    check(
        Commands.tokenize("bad :::: = ???").size() == 4,
        "Unexpected punctuation always makes parser progress");
    boolean quoteRejected = false;
    try {
      Commands.tokenize("\"unterminated");
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
      quoteRejected = true;
    }
    check(quoteRejected, "Malformed quote rejects without looping");
    Model.Portal portal = new Model.Portal();
    portal.maxX = portal.maxY = portal.maxZ = 1;
    check(Portals.volume(portal) == 8, "Portal volume uses inclusive block bounds");
    portal.minX = portal.minY = portal.minZ = Integer.MIN_VALUE;
    portal.maxX = portal.maxY = portal.maxZ = Integer.MAX_VALUE;
    rejects(() -> Portals.volume(portal), "Oversized portal cannot overflow volume validation");
    System.out.println("PASS " + count + " policy, persistence and config checks");
  }
}
