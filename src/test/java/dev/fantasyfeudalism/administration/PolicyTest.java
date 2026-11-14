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
    actionDefaults(root);
    portalPack(root);
    spawnSettings(root);
    borders(root);
    combatSettings(root);
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

  private static void combatSettings(Path root) throws Exception {
    Path file = root.resolve("combat-settings.toml");
    Files.writeString(file, Settings.TEMPLATE);
    var defaults = Settings.load(file);
    check(
        defaults.combatTravel && defaults.combatSeconds == 30 && defaults.pvpDefault,
        "Combat travel defaults to thirty seconds with PvP enabled");
    Files.writeString(
        file, Settings.TEMPLATE.replaceAll("(?s)\\[combat\\].*?(?=\\[teleport\\])", ""));
    var legacy = Settings.load(file);
    check(
        legacy.combatTravel && legacy.combatSeconds == 30 && legacy.pvpDefault,
        "Legacy config receives compatible combat/PvP defaults");
    Files.writeString(
        file,
        Settings.TEMPLATE.replace("teleportBlockEnabled = true", "teleportBlockEnabled = false"));
    check(!Settings.load(file).combatTravel, "Combat travel lock can be disabled");
    Files.writeString(file, Settings.TEMPLATE.replace("tagSeconds = 30", "tagSeconds = 0"));
    rejects(() -> Settings.load(file), "Zero combat duration rejected");
    Files.writeString(file, Settings.TEMPLATE.replace("tagSeconds = 30", "tagSeconds = 3601"));
    rejects(() -> Settings.load(file), "Excessive combat duration rejected");
    Files.writeString(file, Settings.TEMPLATE.replace("tagSeconds = 30", "tagSeconds = 1.5"));
    rejects(() -> Settings.load(file), "Combat duration uses whole seconds");
    Files.writeString(
        file, Settings.TEMPLATE.replace("enabledByDefault = true", "enabledByDefault = false"));
    check(!Settings.load(file).pvpDefault, "PvP default can be configured off");
    var old = Storage.JSON.fromJson("{\"balance\":5}", PlayerData.class);
    check(
        old.pvpEnabled == null && old.pvpToggleAfter == 0,
        "Legacy players use configured PvP default without a cooldown");
    var storage = new Storage(root.resolve("pvp-state"));
    UUID id = UUID.randomUUID();
    storage.ensure(id, defaults);
    storage.update(
        s -> {
          var player = s.players.get(id.toString());
          player.pvpEnabled = false;
          player.pvpToggleAfter = 1700000300000L;
        });
    var saved = new Storage(storage.directory).player(id);
    check(
        Boolean.FALSE.equals(saved.pvpEnabled) && saved.pvpToggleAfter == 1700000300000L,
        "PvP preference and real-time cooldown survive storage reload");
    check(
        PermissionCatalog.common().contains("ffa.command.pvp"),
        "PvP command permission is documented in the node catalog");
  }

  private static void borders(Path root) throws Exception {
    Path config = root.resolve("border-settings.toml");
    Files.writeString(config, Settings.TEMPLATE);
    check(
        Settings.load(config).borderVisibilityDistance == 8,
        "Border wall visible within eight blocks by default");
    Files.writeString(
        config, Settings.TEMPLATE.replaceAll("(?s)\\[worldBorder\\].*?(?=\\[portals\\])", ""));
    check(
        Settings.load(config).borderVisibilityDistance == 8,
        "Legacy config gets close-only border visibility");
    Files.writeString(
        config, Settings.TEMPLATE.replace("visibilityDistance = 8", "visibilityDistance = 0"));
    rejects(() -> Settings.load(config), "Zero visibility distance rejected");
    Files.writeString(
        config, Settings.TEMPLATE.replace("visibilityDistance = 8", "visibilityDistance = 65"));
    rejects(() -> Settings.load(config), "Excessive border visibility rejected");
    Border rectangle = new Border();
    rectangle.minX = -40;
    rectangle.maxX = 59;
    rectangle.minZ = -100;
    rectangle.maxZ = 99;
    rectangle.validate(29999984);
    check(
        WorldBorders.view(rectangle, 0, 0, 8).equals(new WorldBorders.View(0, 0)),
        "Rectangle walls hidden at a distance");
    check(
        WorldBorders.view(rectangle, -32, 0, 8).centerX() - WorldBorders.VIEW_SIZE / 2 == -40,
        "West wall visible at threshold");
    check(
        WorldBorders.view(rectangle, -31.99, 0, 8).centerX() == 0,
        "West wall hidden beyond threshold");
    check(
        WorldBorders.view(rectangle, 59, 0, 8).centerX() + WorldBorders.VIEW_SIZE / 2 == 60,
        "East wall aligned to outer corner edge");
    check(
        WorldBorders.view(rectangle, 0, -98, 8).centerZ() - WorldBorders.VIEW_SIZE / 2 == -100,
        "North wall aligned");
    check(
        WorldBorders.view(rectangle, 0, 99, 8).centerZ() + WorldBorders.VIEW_SIZE / 2 == 100,
        "South wall aligned");
    var corner = WorldBorders.view(rectangle, -39, -99, 8);
    check(corner.centerX() != 0 && corner.centerZ() != 0, "Both nearby corner edges are visible");
    rectangle.maxX = Integer.MAX_VALUE;
    rejects(() -> rectangle.validate(29999984), "Border upper-edge overflow rejected");
    rectangle.maxX = -41;
    rejects(() -> rectangle.validate(29999984), "Reversed persisted bounds rejected");
    Path legacyDir = root.resolve("legacy-border-state");
    Files.createDirectories(legacyDir);
    Files.writeString(legacyDir.resolve("state.json"), "{\"version\":1}");
    check(
        new Storage(legacyDir).state().worldBorders.isEmpty(),
        "Legacy state starts without custom borders");
    var store = new Storage(root.resolve("border-state"));
    rectangle.maxX = 59;
    store.update(s -> s.worldBorders.put("minecraft:overworld", rectangle));
    check(
        new Storage(store.directory).state().worldBorders.get("minecraft:overworld").maxZ == 99,
        "Rectangle border round trips through storage");
  }

  private static void spawnSettings(Path root) throws Exception {
    Path file = root.resolve("spawn-settings.toml");
    Files.writeString(file, Settings.TEMPLATE);
    check(
        Settings.load(file).spawnInvulnerabilitySeconds == 8, "Eight-second default spawn shield");
    Files.writeString(file, Settings.TEMPLATE.replace("invulnerabilitySeconds = 8\n", ""));
    check(Settings.load(file).spawnInvulnerabilitySeconds == 8, "Legacy settings get spawn shield");
    Files.writeString(
        file,
        Settings.TEMPLATE.replace("invulnerabilitySeconds = 8", "invulnerabilitySeconds = 0"));
    check(Settings.load(file).spawnInvulnerabilitySeconds == 0, "Spawn shield may be disabled");
    Files.writeString(
        file,
        Settings.TEMPLATE.replace("invulnerabilitySeconds = 8", "invulnerabilitySeconds = -1"));
    rejects(() -> Settings.load(file), "Negative spawn protection rejected");
    Files.writeString(
        file,
        Settings.TEMPLATE.replace("invulnerabilitySeconds = 8", "invulnerabilitySeconds = 3601"));
    rejects(() -> Settings.load(file), "Unbounded spawn protection rejected");
  }

  private static void portalPack(Path root) throws Exception {
    Path config = root.resolve("portal-pack-settings.toml");
    Files.writeString(config, Settings.TEMPLATE);
    var current = Settings.load(config);
    check(
        current.portalPack && current.portalPackUrl.isEmpty(),
        "Bundled portal pack defaults to native automatic delivery");
    String legacy = Settings.TEMPLATE.replaceAll("(?s)\\[portalPack\\].*?(?=\\[dimension\\])", "");
    Files.writeString(config, legacy);
    check(
        Settings.load(config).portalPack,
        "Legacy settings enable bundled portal rendering without rewriting data");
    Files.writeString(
        config,
        Settings.TEMPLATE.replace("url = \"\"", "url = \"file:///private/server.properties\""));
    rejects(() -> Settings.load(config), "Pack URLs cannot expose local files");
    Files.writeString(config, Settings.TEMPLATE.replace("url = \"\"", "url = \"relative-path\""));
    rejects(() -> Settings.load(config), "Pack URL must be absolute HTTP(S)");
    try (var zip = new java.util.zip.ZipFile("src/main/resources/resourcepacks/ffa-portals.zip")) {
      var json =
          com.google.gson.JsonParser.parseString(
                  new String(
                      zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes(),
                      java.nio.charset.StandardCharsets.UTF_8))
              .getAsJsonObject();
      check(
          json.getAsJsonObject("pack").get("pack_format").getAsInt() == 34,
          "Bundled pack targets Minecraft 1.21.1");
      var hashes = new HashSet<String>();
      for (String dye :
          List.of(
              "white",
              "orange",
              "magenta",
              "light_blue",
              "yellow",
              "lime",
              "pink",
              "gray",
              "light_gray",
              "cyan",
              "purple",
              "blue",
              "brown",
              "green",
              "red",
              "black")) {
        byte[] png =
            zip.getInputStream(zip.getEntry("assets/ffa/textures/block/portal_" + dye + ".png"))
                .readAllBytes();
        hashes.add(
            java.util.HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(png)));
        check(
            java.nio.ByteBuffer.wrap(png, 16, 8).getInt() == 32
                && java.nio.ByteBuffer.wrap(png, 20, 4).getInt() == 1024,
            "Portal PNG contains 32 animation frames: " + dye);
        var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
        boolean seamless = true;
        for (int frame = 0; frame < 32; frame++)
          for (int pixel = 0; pixel < 32; pixel++) {
            seamless &= image.getRGB(0, frame * 32 + pixel) == image.getRGB(31, frame * 32 + pixel);
            seamless &= image.getRGB(pixel, frame * 32) == image.getRGB(pixel, frame * 32 + 31);
          }
        check(seamless, "Opposite portal edges match through every animation frame: " + dye);
        check(
            zip.getEntry("assets/ffa/textures/block/portal_" + dye + ".png.mcmeta") != null,
            "Portal animation metadata is bundled: " + dye);
      }
      check(hashes.size() == 16, "All sixteen portal surface colors are distinct");
    }
    Shop old =
        Storage.JSON.fromJson("{\"item\":\"minecraft:apple\",\"buy\":2,\"sell\":1}", Shop.class);
    check(
        old.buyLimit == -1 && old.sellLimit == -1 && old.usage.isEmpty(),
        "Legacy shops retain unlimited trade directions with empty counters");
  }

  private static void actionDefaults(Path root) throws Exception {
    String legacy = Settings.TEMPLATE;
    for (var flag : Flag.values())
      if (flag.upgradeDefault != null)
        legacy =
            legacy.replaceAll(
                "(?m)^" + flag.name().toLowerCase(java.util.Locale.ROOT) + " = .*\\R", "");
    Path file = root.resolve("legacy-action-settings.toml");
    Files.writeString(file, legacy);
    var settings = Settings.load(file);
    for (var flag : Flag.values())
      if (flag.upgradeDefault != null)
        check(!settings.defaults.get(flag), "Old config safely defaults new flag " + flag);
    check(
        ChatStyles.validate("name_underlined", "true").equals("true"),
        "Name underline accepts Boolean text");
    rejects(
        () -> ChatStyles.validate("title_underlined", "yes"),
        "Title underline rejects non-Boolean text");
    ChatStyles.validateRank("ffa.chat.style.title_underlined", Ranks.parseValue("true"));
    check(true, "Rank underline accepts a typed Boolean");
    rejects(
        () ->
            ChatStyles.validateRank(
                "ffa.chat.style.title_underlined", new com.google.gson.JsonPrimitive("true")),
        "Rank underline rejects string values masquerading as Boolean");
    Region region = region("Underline", Kind.SPAWN, 0, 10);
    region.entryUnderlined = true;
    Path regions = root.resolve("underlined-regions.toml");
    Storage.atomic(regions, Regions.write(List.of(region)));
    check(Regions.read(regions).getFirst().entryUnderlined, "Region underline round trips");
    Files.writeString(
        regions, Files.readString(regions).replaceAll("(?m)^(entry|exit)Underlined = .*\\R", ""));
    check(
        !Regions.read(regions).getFirst().entryUnderlined,
        "Legacy region files default underline off");
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
