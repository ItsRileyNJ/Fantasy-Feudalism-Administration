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
    atomicWrites(root);
    usability(root);
    ranks(root);
    chatStyles();
    whispers();
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

  private static void usability(Path root) throws Exception {
    Path config = root.resolve("upgrade.toml");
    String legacy =
        Settings.TEMPLATE
            .replaceAll("(?m)^adminCreationOnly = .*\\R", "")
            .replaceAll("(?m)^default(Jail|ChatBan|WarpBan)Seconds = .*\\R", "")
            .replaceAll("(?m)^maximumDurationSeconds = .*\\R", "");
    Files.writeString(config, legacy);
    Settings settings = Settings.load(config);
    check(
        settings.adminPortalCreationOnly
            && settings.defaultJailSeconds == 300
            && settings.defaultChatBanSeconds == 300
            && settings.defaultWarpBanSeconds == 300,
        "Older settings gain safe portal and moderation defaults");
    Files.writeString(
        config, Settings.TEMPLATE.replace("defaultJailSeconds = 300", "defaultJailSeconds = 0"));
    rejects(() -> Settings.load(config), "Zero punishment default rejected");
    check(Moderation.seconds("2h", 10000) == 7200, "Duration suffix uses real seconds");
    rejects(
        () -> Moderation.seconds("99999999999999999999999w", 10000), "Duration overflow rejected");
    rejects(() -> Moderation.seconds("0s", 10000), "Zero duration rejected");
    rejects(() -> Moderation.seconds("2d", 10000), "Configured duration maximum enforced");
    Regions.checkName("MiningYard");
    check(true, "Uppercase region and home names accepted");
    rejects(() -> Regions.checkName("Bad Name"), "Names with spaces rejected");
    Region parent = region("Spawn", Kind.SPAWN, 0, 100);
    Region child = region("Market", Kind.CHILD, 10, 20);
    child.parent = "sPAWN";
    child.items.add("minecraft:bread");
    child.entry = "Entering {name}";
    child.entryColor = "#FF8800";
    child.entryBold = true;
    Regions.validate(List.of(parent, child), null, settings, null);
    check(true, "Child parent lookup ignores case");
    Region duplicate = region("market", Kind.CHILD, 30, 40);
    duplicate.parent = "Spawn";
    rejects(
        () -> Regions.validate(List.of(parent, child, duplicate), null, settings, null),
        "Case-only duplicate region names rejected");
    Path file = root.resolve("styled-regions.toml");
    Storage.atomic(file, Regions.write(List.of(parent, child)));
    Region reread = Regions.read(file).get(1);
    check(
        reread.items.equals(child.items)
            && reread.entryBold
            && reread.entryColor.equals("#FF8800")
            && reread.entry.equals("Entering {name}"),
        "Item allowlists and styled title templates round trip");
    String oldRegions =
        Files.readString(file)
            .replaceAll(
                "(?m)^(items|entryColor|exitColor|entryBold|exitBold|entryItalic|exitItalic) ="
                    + " .*\\R",
                "");
    Files.writeString(file, oldRegions);
    reread = Regions.read(file).get(1);
    check(
        reread.items.isEmpty() && !reread.entryBold && reread.entryColor.equals("green"),
        "Old region files receive optional item/style defaults");
  }

  private static void whispers() throws Exception {
    check(Whispers.validate("Hello there").equals("Hello there"), "Whispers preserve plain text");
    rejects(() -> Whispers.validate(" "), "Blank whisper rejected");
    rejects(() -> Whispers.validate("x".repeat(513)), "Oversized whisper rejected");
    rejects(() -> Whispers.validate("hello\nworld"), "Whisper control characters rejected");
    rejects(() -> Whispers.validate("\u00a7cRed"), "Whisper formatting injection rejected");
    check(
        new PlayerData().whisperMonitor && new PlayerData().whisperAllowed.isEmpty(),
        "Monitoring defaults on while recipient consent starts empty");
  }

  private static void chatStyles() throws Exception {
    check(
        ChatStyles.validate("nickname", "Sir Riley").equals("Sir Riley"),
        "Nickname permits plain text spaces");
    check(ChatStyles.validate("title", "").isEmpty(), "Empty title can suppress inherited title");
    check(
        ChatStyles.validate("name_color", "gold").equals("gold"),
        "Native named chat colors accepted");
    check(ChatStyles.validate("title_italic", "true").equals("true"), "Italic style uses boolean");
    rejects(() -> ChatStyles.validate("nickname", " "), "Empty nickname rejected");
    rejects(() -> ChatStyles.validate("nickname", "A\nB"), "Newline nickname rejected");
    rejects(
        () -> ChatStyles.validate("title", "\u00a7cAdmin"), "Legacy injected formatting rejected");
    rejects(
        () -> ChatStyles.validate("nickname", "A\u202eB"), "Invisible direction controls rejected");
    rejects(
        () -> ChatStyles.validate("name_color", "obfuscated"),
        "Non-color formatting is not accepted as a color");
    rejects(() -> ChatStyles.validate("name_bold", "yes"), "Non-boolean formatting rejected");
    rejects(() -> ChatStyles.validate("title", "x".repeat(49)), "Title length bounded");
    rejects(
        () -> ChatStyles.validateRank("ffa.chat.style.title", Ranks.parseValue("true")),
        "Rank display title must be a string");
    check(
        ChatStyles.rankValue("ffa.chat.style.nickname", "123").isString(),
        "Numeric-looking rank nickname remains plain text");
    check(
        ChatStyles.permission("nickname").equals("ffa.chat.nickname"),
        "Self-service nickname node is separate from stored style");
  }

  private static void ranks(Path root) throws Exception {
    Path file = root.resolve("ranks/ranks.json");
    Ranks ranks = new Ranks(file);
    check(
        Ranks.parse(Files.readString(Path.of("wiki/examples/ranks.json")))
            .ranks
            .containsKey("nobles"),
        "Published ranks example passes production validation");
    UUID player = UUID.randomUUID();
    check(ranks.active(player).equals(List.of("member")), "Default rank is automatic");
    check(
        ranks.decision(player, "ffa.admin").isEmpty(), "New ranks grant no administration rights");
    ranks.update(
        d -> {
          d.ranks.get("member").permissions.put("ffa.command.spawn", Ranks.parseValue("true"));
          var vip = new Ranks.Rank();
          vip.power = 10;
          vip.parents.add("member");
          vip.permissions.put("ffa.*", Ranks.parseValue("true"));
          vip.permissions.put("ffa.admin", Ranks.parseValue("false"));
          vip.permissions.put("ffa.homes.maximum", Ranks.parseValue("5"));
          vip.permissions.put("command.time", Ranks.parseValue("true"));
          vip.permissions.put("command.time.set", Ranks.parseValue("false"));
          vip.permissions.put("display.label", Ranks.parseValue("Royal Guard"));
          d.ranks.put("vip", vip);
          d.players.put(player.toString(), new TreeSet<>(Set.of("vip")));
        });
    check(
        ranks.active(player).equals(List.of("vip", "member")),
        "Assigned and inherited ranks use descending power");
    check(!ranks.decision(player, "ffa.admin").orElse(true), "Exact deny beats same-rank wildcard");
    check(
        ranks.value(player, "ffa.chat.style.name_bold").isEmpty(),
        "Broad permission grant never creates display metadata");
    check(
        ranks.decision(player, "ffa.command.home").orElse(false),
        "Trailing wildcard grants matching nodes");
    check(
        ranks.integer(player, "ffa.homes.maximum").orElse(0) == 5,
        "Numeric permission stays an integer");
    check(
        ranks.decision(player, "ffa.homes.maximum").isEmpty(),
        "Number is never coerced to boolean");
    check(
        ranks.value(player, "display.label").orElseThrow().getAsString().equals("Royal Guard"),
        "String metadata round trips");
    check(
        ranks.decision(player, "command.time.query.daytime").orElse(false),
        "Command permission covers descendants");
    check(
        !ranks.decision(player, "command.time.set.day").orElse(true),
        "Specific command deny beats parent allow");
    check(
        new Ranks(file).integer(player, "ffa.homes.maximum").orElse(0) == 5,
        "Ranks persist across a new manager");
    String original = Files.readString(file);
    rejects(
        () -> ranks.update(d -> d.ranks.get("member").parents.add("vip")),
        "Inheritance cycle rejected");
    rejects(
        () -> ranks.update(d -> d.ranks.get("vip").parents.add("missing")),
        "Missing parent rejected");
    rejects(
        () ->
            ranks.update(
                d ->
                    d.ranks
                        .get("vip")
                        .permissions
                        .put("ffa.homes.maximum", Ranks.parseValue("-1"))),
        "Invalid numeric home limit rejected");
    rejects(
        () ->
            ranks.update(
                d -> {
                  for (int i = 0; i <= 33; i++) {
                    var rank = new Ranks.Rank();
                    if (i > 0) rank.parents.add("depth_" + (i - 1));
                    d.ranks.put("depth_" + i, rank);
                  }
                }),
        "Inheritance beyond 32 edges is rejected");
    check(Files.readString(file).equals(original), "Invalid rank changes preserve disk");
    ranks.update(
        d -> {
          var restricted = new Ranks.Rank();
          restricted.power = 20;
          restricted.permissions.put("ffa.command.spawn", Ranks.parseValue("false"));
          d.ranks.put("restricted", restricted);
          d.players.get(player.toString()).add("restricted");
        });
    check(
        !ranks.decision(player, "ffa.command.spawn").orElse(true),
        "Higher-power deny overrides lower allow");
    ranks.update(
        d -> {
          var first = new Ranks.Rank();
          first.power = 20;
          first.permissions.put("ffa.command.spawn", Ranks.parseValue("true"));
          d.ranks.put("aaa", first);
          d.players.get(player.toString()).add("aaa");
        });
    check(
        ranks.decision(player, "ffa.command.spawn").orElse(false),
        "Equal powers resolve by rank ID deterministically");
    ranks.update(d -> d.ranks.get("member").defaultRank = false);
    check(ranks.active(player).contains("member"), "Inheritance activates a non-default parent");
    check(
        ranks.active(UUID.randomUUID()).isEmpty(),
        "Non-default rank does not activate for strangers");
    String valid = Files.readString(file);
    Files.writeString(file, "{}");
    rejects(ranks::reload, "Malformed ranks reload rejected");
    check(
        ranks.integer(player, "ffa.homes.maximum").orElse(0) == 5,
        "Bad reload retains active permissions");
    rejects(() -> ranks.update(d -> d.ranks.clear()), "Commands cannot overwrite external edits");
    Files.writeString(file, valid);
    ranks.reload();
    rejects(
        () -> Ranks.parse(valid.replace("\"power\": 10", "\"power\": 1.5")),
        "Fractional priorities rejected");
    rejects(
        () -> Ranks.parse(valid.replace("\"power\": 10", "\"power\": true")),
        "Boolean priorities rejected");
    rejects(() -> Ranks.checkNode("ffa.*.admin", true), "Mid-node wildcards rejected");
    check(ranks.ids().contains("vip"), "Successful reload preserves ranks");
    Files.delete(file);
    Files.createDirectory(file);
    Files.writeString(file.resolve("keep"), "sentinel");
    boolean ioFailure = false;
    try {
      ranks.update(d -> d.ranks.clear());
    } catch (java.io.IOException expected) {
      ioFailure = true;
    }
    check(ioFailure && ranks.ids().contains("vip"), "Rank read failure retains active permissions");
  }

  private static void atomicWrites(Path root) throws Exception {
    Path directory = root.resolve("atomic-writes");
    Path target = directory.resolve("state.json");
    Storage.atomic(target, "first state");
    check(Files.readString(target).equals("first state"), "First atomic save succeeds");
    String replacement = "Denar: 19\nMessage: \u00a7aWelcome \u2014 \uD83C\uDFF0\n";
    Storage.atomic(target, replacement);
    check(Files.readString(target).equals(replacement), "Atomic replacement preserves UTF-8");
    Storage.atomic(target, "short");
    check(Files.readString(target).equals("short"), "Replacement leaves no old trailing data");
    try (var paths = Files.list(directory)) {
      check(
          paths.noneMatch(p -> p.getFileName().toString().startsWith(".ffa-")),
          "Successful saves remove temporary files");
    }

    Path blocked = directory.resolve("blocked.json");
    Files.createDirectory(blocked);
    Path sentinel = blocked.resolve("keep.txt");
    Files.writeString(sentinel, "existing data");
    boolean failed = false;
    try {
      Storage.atomic(blocked, "must not replace the directory");
    } catch (java.io.IOException expected) {
      failed = true;
    }
    check(
        failed && Files.readString(sentinel).equals("existing data"),
        "Real replacement errors propagate and preserve existing data");
    try (var paths = Files.list(directory)) {
      check(
          paths.noneMatch(p -> p.getFileName().toString().startsWith(".ffa-")),
          "Failed saves remove temporary files");
    }
  }
}
