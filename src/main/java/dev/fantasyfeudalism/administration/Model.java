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
    HOME_CREATION
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
    public String entry = "", exit = "";
    public Map<Flag, Boolean> flags = new EnumMap<>(Flag.class);
    public Set<String> interactions = new LinkedHashSet<>();
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
    public long balance;
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
  }

  public static final class Shop {
    public String item = "minecraft:stone";
    public long buy = 1, sell = 0;
  }

  public static final class PendingTrade {
    public String id, player, before, after;
    public long oldBalance, newBalance;
  }

  public static final class State {
    public int version = 1;
    public Point spawn;
    public Map<String, Warp> warps = new TreeMap<>();
    public Map<String, Portal> portals = new TreeMap<>();
    public Map<String, PlayerData> players = new TreeMap<>();
    public Map<String, Shop> shops = new TreeMap<>();
    public Map<String, PendingTrade> trades = new TreeMap<>();
  }
}
