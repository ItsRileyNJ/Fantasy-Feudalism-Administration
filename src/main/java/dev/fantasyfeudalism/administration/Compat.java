package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;

/** Optional integrations never change another mod's records or ownership decisions. */
public final class Compat {
  private final Settings settings;
  private final Ranks ranks;
  private Class<?> villageClass;
  private Method get, all, at;
  private Field bells, owner, members, center, settlementName;
  private boolean townInstalled, townReady;
  private int settlementRadius;
  private final boolean essentials = ModList.get().isLoaded("ftbessentials");

  public Compat(Settings settings, Ranks ranks) {
    this.settings = settings;
    this.ranks = ranks;
    townInstalled = ModList.get().isLoaded("villagerscontract");
    if (townInstalled && settings.mercenaries)
      try {
        String version =
            ModList.get()
                .getModContainerById("villagerscontract")
                .orElseThrow()
                .getModInfo()
                .getVersion()
                .toString();
        if (!version.equals("1.20.4"))
          throw new ReflectiveOperationException("Unverified Mercenaries version: " + version);
        villageClass = Class.forName("dev.villagerscontract.VillageBells");
        Class<?> v = Class.forName("dev.villagerscontract.VillageBells$Village");
        get = villageClass.getMethod("get", ServerLevel.class);
        all = method(villageClass, "all");
        at = method(villageClass, "at", ServerLevel.class, BlockPos.class);
        bells = field(v, "bells");
        owner = field(v, "owner");
        members = field(v, "members");
        center = field(v, "center");
        settlementName = field(v, "name");
        settlementRadius = villageClass.getField("RADIUS").getInt(null);
        if (settlementRadius < 1 || settlementRadius > 4096)
          throw new ReflectiveOperationException("Unsupported settlement radius");
        townReady = true;
      } catch (ReflectiveOperationException e) {
        Administration.LOG.error(
            "Mercenaries adapter unavailable: settlement-overlap checks fail closed", e);
      }
  }

  private static Method method(Class<?> c, String n, Class<?>... args)
      throws ReflectiveOperationException {
    Method m = c.getDeclaredMethod(n, args);
    m.setAccessible(true);
    return m;
  }

  private static Field field(Class<?> c, String n) throws ReflectiveOperationException {
    Field f = c.getDeclaredField(n);
    f.setAccessible(true);
    return f;
  }

  public boolean ftbEssentials() {
    return essentials;
  }

  public boolean overlaps(MinecraftServer server, Region region) {
    if (!townInstalled) return false;
    if (!settings.mercenaries || !townReady)
      throw new IllegalArgumentException(
          "Mercenaries is installed but settlement adapter is unavailable; explicit region override"
              + " required");
    try {
      for (ServerLevel level : server.getAllLevels())
        if (level.dimension().location().toString().equals(region.dimension)) {
          Object data = get.invoke(null, level);
          for (Object village : (Collection<?>) all.invoke(data))
            if (owner.get(village) != null)
              for (Object b : (Collection<?>) bells.get(village)) {
                BlockPos p = (BlockPos) b;
                double x = Math.max(region.minX, Math.min(region.maxX, p.getX())),
                    z = Math.max(region.minZ, Math.min(region.maxZ, p.getZ()));
                if ((x - p.getX()) * (x - p.getX()) + (z - p.getZ()) * (z - p.getZ())
                    <= (long) settlementRadius * settlementRadius) return true;
              }
        }
      return false;
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException(
          "Cannot validate settlement overlap; override or repair compatibility", e);
    }
  }

  public boolean townMember(ServerPlayer p, BlockPos target, String requiredCenter) {
    return townMember(p, p.serverLevel(), target, requiredCenter);
  }

  public boolean inSettlement(ServerLevel level, BlockPos target) {
    if (!townInstalled) return false;
    if (!townReady) return true;
    try {
      return at.invoke(get.invoke(null, level), level, target) != null;
    } catch (ReflectiveOperationException e) {
      return true;
    }
  }

  public boolean townMember(
      ServerPlayer p, ServerLevel level, BlockPos target, String requiredCenter) {
    if (!townReady) return false;
    try {
      Object village = at.invoke(get.invoke(null, level), level, target);
      if (village == null) return false;
      if (!requiredCenter.isEmpty()
          && !((BlockPos) center.get(village)).toShortString().equals(requiredCenter)) return false;
      return p.getUUID().equals(owner.get(village))
          || ((Set<?>) members.get(village)).contains(p.getUUID());
    } catch (ReflectiveOperationException e) {
      return false;
    }
  }

  public boolean settlementBell(ServerPlayer p, BlockPos target) {
    return townReady && p.serverLevel().getBlockState(target).is(Blocks.BELL);
  }

  public boolean npc(Entity entity) {
    String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace();
    return settings.easyNpc && id.equals("easy_npc")
        || settings.mercenaries && id.equals("villagerscontract");
  }

  public boolean admin(ServerPlayer p) {
    if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) return false;
    return p.hasPermissions(settings.operatorLevel) || permission(p, "ffa.admin");
  }

  public boolean permission(ServerPlayer p, String node) {
    return decision(p, node).orElse(false);
  }

  public Optional<Boolean> decision(ServerPlayer p, String node) {
    Optional<Boolean> external = externalDecision(p, node);
    return external.isPresent()
        ? external
        : ranks.decision(p.getUUID(), node.toLowerCase(Locale.ROOT));
  }

  private Optional<Boolean> externalDecision(ServerPlayer p, String node) {
    if (settings.ftbRanks && ModList.get().isLoaded("ftbranks"))
      try {
        Object value =
            Class.forName("dev.ftb.mods.ftbranks.api.FTBRanksAPI")
                .getMethod("getPermissionValue", ServerPlayer.class, String.class)
                .invoke(null, p, node);
        Optional<?> result =
            (Optional<?>)
                Class.forName("dev.ftb.mods.ftbranks.api.PermissionValue")
                    .getMethod("asBoolean")
                    .invoke(value);
        if (result.isPresent()) return Optional.of(Boolean.TRUE.equals(result.get()));
      } catch (ReflectiveOperationException | LinkageError e) {
        return Optional.of(false);
      }
    if (!settings.luckPerms || !ModList.get().isLoaded("luckperms")) return Optional.empty();
    try {
      Class<?> api = Class.forName("net.luckperms.api.LuckPerms"),
          provider = Class.forName("net.luckperms.api.LuckPermsProvider");
      Object lp = provider.getMethod("get").invoke(null),
          manager = api.getMethod("getUserManager").invoke(lp);
      Object user =
          Class.forName("net.luckperms.api.model.user.UserManager")
              .getMethod("getUser", UUID.class)
              .invoke(manager, p.getUUID());
      if (user == null) return Optional.of(false);
      Object cached =
          Class.forName("net.luckperms.api.model.PermissionHolder")
              .getMethod("getCachedData")
              .invoke(user);
      Object permissions =
          Class.forName("net.luckperms.api.cacheddata.CachedDataManager")
              .getMethod("getPermissionData")
              .invoke(cached);
      Object result =
          Class.forName("net.luckperms.api.cacheddata.CachedPermissionData")
              .getMethod("checkPermission", String.class)
              .invoke(permissions, node);
      String state = ((Enum<?>) result).name();
      return state.equals("UNDEFINED") ? Optional.empty() : Optional.of(state.equals("TRUE"));
    } catch (ReflectiveOperationException | LinkageError e) {
      return Optional.of(false);
    }
  }

  public boolean access(ServerPlayer p, Access access, BlockPos target) {
    return access(p, access, p.serverLevel(), target);
  }

  public boolean access(ServerPlayer p, Access access, ServerLevel level, BlockPos target) {
    if (admin(p)) return true;
    return switch (access.mode) {
      case "public" -> true;
      case "admin" -> false;
      case "permission" -> permission(p, access.value);
      case "group" -> group(p, access.value);
      case "team" -> townMember(p, level, target, access.value);
      default -> false;
    };
  }

  public int homeLimit(ServerPlayer p, int fallback) {
    if (settings.ftbRanks && ModList.get().isLoaded("ftbranks"))
      try {
        Object value =
            Class.forName("dev.ftb.mods.ftbranks.api.FTBRanksAPI")
                .getMethod("getPermissionValue", ServerPlayer.class, String.class)
                .invoke(null, p, "ffa.homes.maximum");
        java.util.OptionalInt n =
            (java.util.OptionalInt)
                Class.forName("dev.ftb.mods.ftbranks.api.PermissionValue")
                    .getMethod("asInteger")
                    .invoke(value);
        if (n.isPresent()) return Math.max(0, Math.min(1000, n.getAsInt()));
      } catch (ReflectiveOperationException | LinkageError ignored) {
      }
    return ranks.integer(p.getUUID(), "ffa.homes.maximum").orElse(fallback);
  }

  public boolean group(ServerPlayer p, String id) {
    if (settings.ftbRanks && ModList.get().isLoaded("ftbranks"))
      try {
        Object manager =
            Class.forName("dev.ftb.mods.ftbranks.api.FTBRanksAPI")
                .getMethod("manager")
                .invoke(null);
        Collection<?> ranks =
            (Collection<?>)
                Class.forName("dev.ftb.mods.ftbranks.api.RankManager")
                    .getMethod("getRanks", ServerPlayer.class)
                    .invoke(manager, p);
        Method name = Class.forName("dev.ftb.mods.ftbranks.api.Rank").getMethod("getId");
        for (Object rank : ranks) if (id.equals(name.invoke(rank))) return true;
      } catch (ReflectiveOperationException | LinkageError ignored) {
        return false;
      }
    return ranks.active(p.getUUID()).contains(id) || permission(p, "group." + id);
  }

  public String settlementNames(ServerPlayer player) {
    if (!townInstalled) return "not installed";
    if (!townReady) return "adapter unavailable";
    Set<String> names = new TreeSet<>();
    try {
      for (var level : player.server.getAllLevels()) {
        Object data = get.invoke(null, level);
        for (Object village : (Collection<?>) all.invoke(data))
          if (player.getUUID().equals(owner.get(village))
              || ((Set<?>) members.get(village)).contains(player.getUUID())) {
            String name = String.valueOf(settlementName.get(village));
            names.add(
                (name.isBlank() ? "Unnamed settlement" : name)
                    + " ("
                    + level.dimension().location()
                    + ")");
          }
      }
    } catch (ReflectiveOperationException | LinkageError ex) {
      return "adapter unavailable";
    }
    return names.isEmpty() ? "none" : String.join(", ", names);
  }

  public String rankNames(ServerPlayer player) {
    Set<String> names = new TreeSet<>();
    for (String id : ranks.active(player.getUUID())) names.add("FFA:" + id);
    if (settings.ftbRanks && ModList.get().isLoaded("ftbranks")) {
      try {
        Object manager =
            Class.forName("dev.ftb.mods.ftbranks.api.FTBRanksAPI")
                .getMethod("manager")
                .invoke(null);
        Collection<?> active =
            (Collection<?>)
                Class.forName("dev.ftb.mods.ftbranks.api.RankManager")
                    .getMethod("getRanks", ServerPlayer.class)
                    .invoke(manager, player);
        Method id = Class.forName("dev.ftb.mods.ftbranks.api.Rank").getMethod("getId");
        for (Object rank : active) names.add("FTB:" + id.invoke(rank));
      } catch (ReflectiveOperationException | LinkageError ex) {
        names.add("FTB:adapter unavailable");
      }
    }
    return names.isEmpty() ? "none" : String.join(", ", names);
  }

  public static void validateAccess(Access a) {
    if (a == null
        || a.mode == null
        || a.value == null
        || !Set.of("public", "admin", "permission", "group", "team").contains(a.mode))
      throw new IllegalArgumentException(
          "Access mode must be public, admin, permission, group or team");
    if (Set.of("permission", "group").contains(a.mode) && a.value.isBlank())
      throw new IllegalArgumentException("Access value is required for permission/group");
  }
}
