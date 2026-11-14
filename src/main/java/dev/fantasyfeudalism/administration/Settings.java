package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.nio.file.*;
import java.util.*;

/** Parse a complete candidate before publishing; invalid reloads leave the old settings intact. */
public final class Settings {
  public static final String TEMPLATE =
      """
# Fantasy Feudalism Administration. All times are seconds; all money is whole Denar.
# Edit while running, then /ffa reload. Dimension settings require a full restart.
# Invalid settings reject the complete reload and retain the last valid settings.
configVersion = 1
[administration]
# Operator permission level 2..4. Operators bypass gameplay protection, never transaction checks.
operatorLevel = 2
# Legacy setting retained for compatibility. Chat feedback stays concise; audit logs retain details.
verboseFeedback = false
[permissions]
# FTB Ranks, LuckPerms, then built-in rank nodes ffa.command.<name> override this fallback.
# Administrator management always requires operator level or the documented management node.
defaultPlayerCommands = true
[moderation]
defaultJailSeconds = 300
defaultChatBanSeconds = 300
defaultWarpBanSeconds = 300
maximumDurationSeconds = 31536000
[worldBorder]
# The wall appears only within this many blocks; enforcement is always active.
visibilityDistance = 8
[portals]
adminCreationOnly = true
[portalPack]
# Bundled animated portal surfaces; clients accept a standard resource-pack download.
enabled = true
# Empty serves the bundled ZIP over the existing Minecraft port. Set an HTTP(S) URL behind proxies/CDNs.
url = ""
[dimension]
# Restart required. No terrain or automatic platform is generated. Build before setting spawn.
enabled = false
[modules]
standaloneRegions = false
economy = true
homes = true
tpa = true
portals = true
# Legacy compatibility flag; /ffa back is always available and controlled by permissions.
back = true
rtp = false
pay = false
leaderboard = false
privacy = false
[spawn]
# Seconds of invincibility after login and death respawn; 0 disables it.
invulnerabilitySeconds = 8
firstJoin = false
# vanilla | bed_first | spawn_first
respawnPolicy = "vanilla"
[combat]
# Block travel after receiving player damage. Administrators remain exempt.
teleportBlockEnabled = true
# Applies to the victim's travel lock and both participants' PvP-toggle combat lock.
tagSeconds = 30
[pvp]
# Applies until a player explicitly chooses a PvP preference.
enabledByDefault = true
[teleport]
cooldownSeconds = 3
warmupSeconds = 0
cancelOnMovement = true
requestExpirySeconds = 60
requestCooldownSeconds = 10
portalCooldownSeconds = 5
# Load only previously saved full chunks; at most one completed load is applied per tick.
maximumQueuedLoads = 2
loadTimeoutSeconds = 15
[homes]
maximum = 3
allowedDimensions = ["minecraft:overworld"]
[rtp]
# Searches only existing loaded terrain; bounded attempts, no chunk generation.
radius = 1000
attempts = 16
allowedDimensions = ["minecraft:overworld"]
[economy]
currency = "Denar"
startingBalance = 0
maximumBalance = 9000000000000000
maximumTransfer = 1000000
[compatibility]
mercenaries = true
easyNpc = true
luckPerms = true
ftbRanks = true
# Region overlap with settlements requires an explicit override on that region.
[logging]
audit = true
security = false
retentionFiles = 8
maximumFileBytes = 1048576
[messages]
denied = "You cannot do that here."
portalDenied = "You cannot use this portal."
[defaults]
# true means allowed; false means blocked/frozen. Applied throughout the spawn dimension.
# Child regions inherit; an explicit region flag overrides these defaults.
build = false
break = false
interact = false
containers = false
entity_interact = false
npc_trading = true
settlement_management = true
hunger = false
damage = false
pvp = false
tnt = false
pearls = false
totems = false
fire = false
mob_spawning = false
hostile_spawning = false
mob_griefing = false
explosions = false
pistons = false
fluids = false
automation = false
dispensers = false
hanging_damage = false
animal_damage = false
flight = false
teleport_in = true
teleport_out = true
home_creation = false
farming = false
tilling = false
strip_wood = false
dirt_path = false
item_drop = false
item_pickup = false
catch_fire = false
vehicle_enter = false
flint_and_steel = false
shears = false
""";
  public int queuedLoads, loadTimeout;
  public int combatSeconds;
  public boolean combatTravel, pvpDefault;
  public int defaultJailSeconds,
      defaultChatBanSeconds,
      defaultWarpBanSeconds,
      maximumDurationSeconds;
  public boolean adminPortalCreationOnly;
  public boolean portalPack;
  public String portalPackUrl;
  public int operatorLevel,
      spawnInvulnerabilitySeconds,
      borderVisibilityDistance,
      homeMax,
      cooldown,
      warmup,
      requestExpiry,
      requestCooldown,
      portalCooldown,
      rtpRadius,
      rtpAttempts,
      retention,
      maxLog;
  public boolean publicCommands;
  public boolean dimension,
      standalone,
      economy,
      homes,
      tpa,
      portals,
      back,
      rtp,
      pay,
      leaderboard,
      privacy,
      firstJoin,
      cancelMove,
      verbose,
      audit,
      security,
      mercenaries,
      easyNpc,
      luckPerms,
      ftbRanks;
  public long startingBalance, maxBalance, maxTransfer;
  public String currency, respawn, denied, portalDenied;
  public List<String> homeDimensions, rtpDimensions;
  public final Map<Flag, Boolean> defaults = new EnumMap<>(Flag.class);

  public static Settings load(Path file) throws Exception {
    if (!Files.exists(file)) Storage.atomic(file, TEMPLATE);
    try (var c = CommentedFileConfig.of(file)) {
      c.load();
      Settings s = new Settings();
      s.portalPack = c.contains("portalPack.enabled") ? bool(c, "portalPack.enabled") : true;
      s.portalPackUrl =
          c.contains("portalPack.url") ? scalar(c, "portalPack.url", String.class) : "";
      if (!s.portalPackUrl.isEmpty()) {
        var url = java.net.URI.create(s.portalPackUrl);
        if (s.portalPackUrl.length() > 2048
            || !("http".equalsIgnoreCase(url.getScheme())
                || "https".equalsIgnoreCase(url.getScheme()))
            || url.getHost() == null
            || url.getUserInfo() != null)
          throw new IllegalArgumentException(
              "portalPack.url must be an HTTP(S) URL without credentials.");
      }
      s.adminPortalCreationOnly =
          c.contains("portals.adminCreationOnly") ? bool(c, "portals.adminCreationOnly") : true;
      s.defaultJailSeconds =
          c.contains("moderation.defaultJailSeconds")
              ? integer(c, "moderation.defaultJailSeconds", 1, 31536000)
              : 300;
      s.defaultChatBanSeconds =
          c.contains("moderation.defaultChatBanSeconds")
              ? integer(c, "moderation.defaultChatBanSeconds", 1, 31536000)
              : 300;
      s.defaultWarpBanSeconds =
          c.contains("moderation.defaultWarpBanSeconds")
              ? integer(c, "moderation.defaultWarpBanSeconds", 1, 31536000)
              : 300;
      s.maximumDurationSeconds =
          c.contains("moderation.maximumDurationSeconds")
              ? integer(c, "moderation.maximumDurationSeconds", 1, 31536000)
              : 31536000;
      if (Math.max(s.defaultJailSeconds, Math.max(s.defaultChatBanSeconds, s.defaultWarpBanSeconds))
          > s.maximumDurationSeconds)
        throw new IllegalArgumentException(
            "Default punishments cannot exceed the maximum duration.");
      if (scalar(c, "configVersion", Number.class).intValue() != 1)
        throw new IllegalArgumentException("Unsupported configVersion; back up before migration.");
      s.operatorLevel = integer(c, "administration.operatorLevel", 2, 4);
      s.verbose = bool(c, "administration.verboseFeedback");
      s.publicCommands = bool(c, "permissions.defaultPlayerCommands");
      s.dimension = bool(c, "dimension.enabled");
      s.standalone = bool(c, "modules.standaloneRegions");
      s.economy = bool(c, "modules.economy");
      s.homes = bool(c, "modules.homes");
      s.tpa = bool(c, "modules.tpa");
      s.portals = bool(c, "modules.portals");
      s.back = bool(c, "modules.back");
      s.rtp = bool(c, "modules.rtp");
      s.pay = bool(c, "modules.pay");
      s.leaderboard = bool(c, "modules.leaderboard");
      s.privacy = bool(c, "modules.privacy");
      s.spawnInvulnerabilitySeconds =
          c.contains("spawn.invulnerabilitySeconds")
              ? integer(c, "spawn.invulnerabilitySeconds", 0, 3600)
              : 8;
      s.borderVisibilityDistance =
          c.contains("worldBorder.visibilityDistance")
              ? integer(c, "worldBorder.visibilityDistance", 1, 64)
              : 8;
      s.combatTravel =
          c.contains("combat.teleportBlockEnabled") ? bool(c, "combat.teleportBlockEnabled") : true;
      s.combatSeconds =
          c.contains("combat.tagSeconds") ? integer(c, "combat.tagSeconds", 1, 3600) : 30;
      s.pvpDefault = c.contains("pvp.enabledByDefault") ? bool(c, "pvp.enabledByDefault") : true;
      s.firstJoin = bool(c, "spawn.firstJoin");
      s.respawn = scalar(c, "spawn.respawnPolicy", String.class);
      if (!Set.of("vanilla", "bed_first", "spawn_first").contains(s.respawn))
        throw new IllegalArgumentException(
            "spawn.respawnPolicy: expected vanilla, bed_first or spawn_first");
      s.queuedLoads = integer(c, "teleport.maximumQueuedLoads", 1, 16);
      s.loadTimeout = integer(c, "teleport.loadTimeoutSeconds", 1, 60);
      s.cooldown = integer(c, "teleport.cooldownSeconds", 0, 86400);
      s.warmup = integer(c, "teleport.warmupSeconds", 0, 300);
      s.cancelMove = bool(c, "teleport.cancelOnMovement");
      s.requestExpiry = integer(c, "teleport.requestExpirySeconds", 1, 3600);
      s.requestCooldown = integer(c, "teleport.requestCooldownSeconds", 0, 3600);
      s.portalCooldown = integer(c, "teleport.portalCooldownSeconds", 1, 3600);
      s.homeMax = integer(c, "homes.maximum", 0, 1000);
      s.homeDimensions = strings(c, "homes.allowedDimensions");
      s.rtpDimensions = strings(c, "rtp.allowedDimensions");
      s.rtpRadius = integer(c, "rtp.radius", 1, 1000000);
      s.rtpAttempts = integer(c, "rtp.attempts", 1, 128);
      s.currency = scalar(c, "economy.currency", String.class);
      if (s.currency.isBlank() || s.currency.length() > 32)
        throw new IllegalArgumentException("economy.currency must contain 1..32 characters");
      s.maxBalance = number(c, "economy.maximumBalance", 1, 9_000_000_000_000_000L);
      s.startingBalance = number(c, "economy.startingBalance", 0, s.maxBalance);
      s.maxTransfer = number(c, "economy.maximumTransfer", 1, s.maxBalance);
      s.mercenaries = bool(c, "compatibility.mercenaries");
      s.easyNpc = bool(c, "compatibility.easyNpc");
      s.luckPerms = bool(c, "compatibility.luckPerms");
      s.ftbRanks = bool(c, "compatibility.ftbRanks");
      s.audit = bool(c, "logging.audit");
      s.security = bool(c, "logging.security");
      s.retention = integer(c, "logging.retentionFiles", 1, 100);
      s.maxLog = integer(c, "logging.maximumFileBytes", 4096, 16777216);
      s.denied = scalar(c, "messages.denied", String.class);
      s.portalDenied = scalar(c, "messages.portalDenied", String.class);
      for (var f : Flag.values()) {
        String key = "defaults." + f.name().toLowerCase(Locale.ROOT);
        s.defaults.put(
            f, !c.contains(key) && f.upgradeDefault != null ? f.upgradeDefault : bool(c, key));
      }
      return s;
    }
  }

  static <T> T scalar(
      com.electronwill.nightconfig.core.UnmodifiableConfig c, String key, Class<T> type) {
    Object v = c.get(key);
    if (!type.isInstance(v))
      throw new IllegalArgumentException(key + ": missing or wrong value type");
    return type.cast(v);
  }

  static boolean bool(com.electronwill.nightconfig.core.UnmodifiableConfig c, String key) {
    return scalar(c, key, Boolean.class);
  }

  static int integer(
      com.electronwill.nightconfig.core.UnmodifiableConfig c, String key, int min, int max) {
    return (int) number(c, key, min, max);
  }

  static long number(
      com.electronwill.nightconfig.core.UnmodifiableConfig c, String key, long min, long max) {
    Number n = scalar(c, key, Number.class);
    long v = n.longValue();
    if (n.doubleValue() != v || v < min || v > max)
      throw new IllegalArgumentException(key + ": expected whole number " + min + ".." + max);
    return v;
  }

  static List<String> strings(com.electronwill.nightconfig.core.UnmodifiableConfig c, String key) {
    Object v = c.get(key);
    if (!(v instanceof List<?> l) || l.stream().anyMatch(x -> !(x instanceof String)))
      throw new IllegalArgumentException(key + ": expected list of dimension IDs");
    return ((List<?>) v).stream().map(String.class::cast).toList();
  }
}
