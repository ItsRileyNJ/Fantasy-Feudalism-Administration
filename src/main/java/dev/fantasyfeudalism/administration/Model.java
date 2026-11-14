package dev.fantasyfeudalism.administration;

import java.util.*;

/** Data-only types; no client registry entries or network payloads. */
public final class Model {
  private Model() {}

  public enum Flag {
    BUILD,
    BREAK,
    INTERACT,
    CONTAINERS,
    ENTITY_INTERACT,
    NPC_TRADING,
    SETTLEMENT_MANAGEMENT,
    HUNGER,
    DAMAGE,
    PVP,
    TNT,
    PEARLS,
    TOTEMS,
    FIRE,
    MOB_SPAWNING,
    HOSTILE_SPAWNING,
    MOB_GRIEFING,
    EXPLOSIONS,
    PISTONS,
    FLUIDS,
    AUTOMATION,
    DISPENSERS,
    HANGING_DAMAGE,
    ANIMAL_DAMAGE,
    FLIGHT,
    TELEPORT_IN,
    TELEPORT_OUT,
    HOME_CREATION,
    FARMING(false),
    TILLING(false),
    STRIP_WOOD(false),
    DIRT_PATH(false),
    ITEM_DROP(false),
    ITEM_PICKUP(false),
    CATCH_FIRE(false),
    VEHICLE_ENTER(false),
    FLINT_AND_STEEL(false),
    SHEARS(false);

    public final Boolean upgradeDefault;

    Flag() {
      upgradeDefault = null;
    }

    Flag(boolean value) {
      upgradeDefault = value;
    }
  }

  public enum Kind {
    SPAWN,
    CHILD,
    STANDALONE
  }

  public static final class Region {
    public String id, name, dimension, parent = "";
    public Kind kind;
    public int minX, minZ, maxX, maxZ, priority;
    public boolean settlementOverride;
    public String entry = "Entering {name}", exit = "Leaving {name}";
    public String entryColor = "green", exitColor = "yellow";
    public boolean entryBold, entryItalic, exitBold, exitItalic, entryUnderlined, exitUnderlined;
    public Map<Flag, Boolean> flags = new EnumMap<>(Flag.class);
    public Set<String> interactions = new LinkedHashSet<>();
    public Set<String> items = new LinkedHashSet<>();
    public Access access = new Access();

    public boolean contains(double x, double z) {
      return x >= minX && x < (double) maxX + 1 && z >= minZ && z < (double) maxZ + 1;
    }

    public boolean overlaps(Region b) {
      return dimension.equals(b.dimension)
          && minX <= b.maxX
          && maxX >= b.minX
          && minZ <= b.maxZ
          && maxZ >= b.minZ;
    }

    public boolean encloses(Region b) {
      return dimension.equals(b.dimension)
          && minX <= b.minX
          && maxX >= b.maxX
          && minZ <= b.minZ
          && maxZ >= b.maxZ;
    }
  }

  public static final class Access {
    public String mode = "public", value = "";
  }

  public static final class Point {
    public String dimension;
    public double x, y, z;
    public float yaw, pitch;

    public Point() {}

    public Point(String d, double x, double y, double z, float yaw, float pitch) {
      dimension = d;
      this.x = x;
      this.y = y;
      this.z = z;
      this.yaw = yaw;
      this.pitch = pitch;
    }

    public String describe() {
      return dimension + " [" + x + ", " + y + ", " + z + "]";
    }
  }

  public static final class Warp {
    public Point point;
    public Access access = new Access();
  }

  public static final class Portal {
    public String id, dimension, warp, color = "purple";
    public boolean managedWarp;
    public int minX, minY, minZ, maxX, maxY, maxZ;
    public Access access = new Access();

    public boolean contains(double x, double y, double z) {
      return x >= minX
          && x < maxX + 1.0
          && y >= minY
          && y < maxY + 1.0
          && z >= minZ
          && z < maxZ + 1.0;
    }
  }

  public static final class PlayerData {
    public boolean rulesAccepted;
    public long balance;
    public Boolean pvpEnabled;
    public long pvpToggleAfter;
    public Map<String, String> chat = new TreeMap<>();
    public boolean vanished, whisperMonitor = true;
    public String vanishMode;
    public Set<String> whisperAllowed = new HashSet<>();
    public boolean joined, requestsEnabled = true;
    public String previousMode;
    public boolean previousMayFly, previousFlying, flightTracked;
    public Map<String, Point> homes = new TreeMap<>();
    public Set<String> ignored = new HashSet<>();
    public Point back;
    public boolean god, godPreviousInvulnerable;
    public Map<String, Punishment> punishments = new TreeMap<>();
  }

  public static final class Shop {
    public String item = "minecraft:stone";
    public long buy = 1, sell = 0;
    public long buyLimit = -1, sellLimit = -1;
    public String limitScope = "shared", limitReset = "manual";
    public Map<String, ShopUsage> usage = new TreeMap<>();
  }

  public static final class ShopUsage {
    public long bought, sold;
  }

  public static final class JailCell {
    public String id, dimension;
    public int minX, minY, minZ, maxX, maxY, maxZ;
    public Point arrival;
    public boolean settlementOverride;

    public boolean contains(Point point) {
      return dimension.equals(point.dimension)
          && point.x >= minX
          && point.x < maxX + 1.0
          && point.y >= minY
          && point.y < maxY + 1.0
          && point.z >= minZ
          && point.z < maxZ + 1.0;
    }
  }

  public static final class Punishment {
    public long until;
    public String reason = "", cell = "", previousMode = "survival";
  }

  public static final class ShopSign {
    public String dimension, shop, token;
    public int x, y, z, quantity = 1;
    public boolean wasWaxed;
  }

  public static final class PendingTrade {
    public String npc = "";
    public int npcOffer;
    public int npcBaseUses;
    public String id, player, before, after;
    public String shop = "", quotaKey = "";
    public int quantity;
    public boolean buy;
    public long oldBalance, newBalance;
  }

  public static final class NpcPrice {
    public String fingerprint, revision;
    public boolean buy;
    public long price, reset;
    public int used;
  }

  public static final class NpcShop {
    public Map<Integer, NpcPrice> offers = new TreeMap<>();
  }

  public static final class Border {
    public int minX, minZ, maxX, maxZ;

    public void validate(int limit) {
      if (minX > maxX
          || minZ > maxZ
          || minX < -limit
          || minZ < -limit
          || (long) maxX + 1 > limit
          || (long) maxZ + 1 > limit)
        throw new IllegalArgumentException(
            "World border corners are outside the world's coordinate limits.");
    }
  }

  public static final class State {
    public int version = 1;
    public Point spawn;
    public Point firstSpawn;
    public RulesRoom rulesRoom = new RulesRoom();
    public Map<String, NpcShop> npcShops = new TreeMap<>();
    public Map<String, Border> worldBorders = new TreeMap<>();
    public Map<String, Warp> warps = new TreeMap<>();
    public Map<String, Portal> portals = new TreeMap<>();
    public Map<String, PlayerData> players = new TreeMap<>();
    public Map<String, Shop> shops = new TreeMap<>();
    public Map<String, PendingTrade> trades = new TreeMap<>();
    public Map<String, JailCell> jailCells = new TreeMap<>();
    public Map<String, ShopSign> shopSigns = new TreeMap<>();
  }

  public static final class RulesRoom {
    public boolean enabled;
    public String color = "white";
    public boolean bold, italic, underlined;
    public String region = "", revision = "";
    public String message = "Please read the server rules, then accept to continue.";
  }
}
