package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;

import dev.fantasyfeudalism.administration.mixin.BlockDisplayAccess;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;

public final class Portals {
  private Portals() {}

  static long volume(Model.Portal p) {
    long x = (long) p.maxX - p.minX + 1,
        y = (long) p.maxY - p.minY + 1,
        z = (long) p.maxZ - p.minZ + 1;
    if (x < 1 || y < 1 || z < 1 || x > 4096 || y > 4096 || z > 4096 || x * y * z > 4096)
      throw new IllegalArgumentException("Portal volume limited to 1..4096 blocks");
    return x * y * z;
  }

  private static Map<String, Map<Long, List<String>>> index = Map.of();

  public static void reindex(Administration app) {
    var next = new HashMap<String, Map<Long, List<String>>>();
    for (var portal : app.storage.state().portals.values()) {
      var dimension = next.computeIfAbsent(portal.dimension, k -> new HashMap<>());
      for (int x = portal.minX >> 4; x <= portal.maxX >> 4; x++)
        for (int z = portal.minZ >> 4; z <= portal.maxZ >> 4; z++)
          dimension
              .computeIfAbsent(
                  net.minecraft.world.level.ChunkPos.asLong(x, z), k -> new ArrayList<>())
              .add(portal.id);
    }
    index = next;
  }

  private static List<Portal> nearby(Administration app, ServerPlayer p) {
    var chunk = p.chunkPosition();
    return index
        .getOrDefault(p.level().dimension().location().toString(), Map.of())
        .getOrDefault(chunk.toLong(), List.of())
        .stream()
        .map(id -> app.storage.state().portals.get(id))
        .filter(Objects::nonNull)
        .toList();
  }

  public static void check(Administration app, ServerPlayer p) {
    if (!app.settings.portals
        || app.teleports.portalCooldowns.getOrDefault(p.getUUID(), 0L) > Teleports.now()) return;
    for (Portal portal : nearby(app, p))
      if (portal.dimension.equals(p.level().dimension().location().toString())
          && portal.contains(p.getX(), p.getY(), p.getZ())) {
        app.teleports.portalCooldowns.put(
            p.getUUID(), Teleports.now() + app.settings.portalCooldown * 1000L);
        if (!app.compat.access(p, portal.access, p.blockPosition())) {
          Teleports.tell(p, app.settings.portalDenied);
          return;
        }
        try {
          app.teleports.warp(p, portal.warp);
        } catch (Exception e) {
          Teleports.tell(p, e.getMessage());
        }
        return;
      }
  }

  public static boolean recolor(Administration app, ServerPlayer p, BlockPos pos, ItemStack stack) {
    if (!app.settings.portals || !(stack.getItem() instanceof DyeItem dye)) return false;
    for (var portal : app.storage.state().portals.values())
      if (portal.dimension.equals(p.level().dimension().location().toString())
          && portal.contains(pos.getX(), pos.getY(), pos.getZ())) {
        try {
          color(app, p, portal.id, dye.getDyeColor().getName());
        } catch (Exception e) {
          Teleports.tell(p, "Portal " + portal.id + ": " + e.getMessage());
        }
        return true;
      }
    return false;
  }

  public static void color(Administration app, ServerPlayer p, String id, String color)
      throws Exception {
    if (!Arrays.stream(DyeColor.values()).anyMatch(d -> d.getName().equals(color)))
      throw new IllegalArgumentException("Choose one of the 16 Minecraft dye colors.");
    Portal portal = app.storage.state().portals.get(id);
    if (portal == null) throw new IllegalArgumentException("Unknown portal.");
    app.storage.update(s -> s.portals.get(id).color = color);
    visualize(app, app.storage.state().portals.get(id));
    Commands.feedback(
        app,
        p.createCommandSourceStack(),
        "portal color " + id + " " + color,
        portal.dimension + " [" + portal.minX + "," + portal.minY + "," + portal.minZ + "]",
        "SUCCESS persisted");
  }

  public static void visualize(Administration app, Portal p) {
    ServerLevel level =
        app.server.getLevel(
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(p.dimension)));
    if (level == null) return;
    var loaded = level.getEntities().getAll();
    var remove = new ArrayList<Entity>();
    for (var e : loaded) if (e.getTags().contains("ffa.portal." + p.id)) remove.add(e);
    remove.forEach(Entity::discard);
    Block block =
        BuiltInRegistries.BLOCK.get(
            ResourceLocation.withDefaultNamespace(p.color + "_stained_glass"));
    for (int x : java.util.stream.IntStream.of(p.minX, p.maxX).distinct().toArray())
      for (int y : java.util.stream.IntStream.of(p.minY, p.maxY).distinct().toArray())
        for (int z : java.util.stream.IntStream.of(p.minZ, p.maxZ).distinct().toArray()) {
          if (!level.hasChunkAt(new BlockPos(x, y, z))) continue;
          var marker = EntityType.BLOCK_DISPLAY.create(level);
          if (marker == null) continue;
          marker.setPos(x, y, z);
          marker.addTag("ffa.portal." + p.id);
          marker.setInvulnerable(true);
          ((BlockDisplayAccess) marker).ffa$block(block.defaultBlockState());
          level.addFreshEntity(marker);
          var interaction = EntityType.INTERACTION.create(level);
          if (interaction != null) {
            var data = new net.minecraft.nbt.CompoundTag();
            interaction.saveWithoutId(data);
            data.putFloat("width", 1);
            data.putFloat("height", 1);
            data.putBoolean("response", true);
            interaction.load(data);
            interaction.setPos(x + .5, y, z + .5);
            interaction.addTag("ffa.portal." + p.id);
            interaction.setInvulnerable(true);
            level.addFreshEntity(interaction);
          }
        }
  }

  public static void removeVisuals(Administration app, Portal p) {
    ServerLevel l =
        app.server.getLevel(
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(p.dimension)));
    if (l == null) return;
    var copy = new ArrayList<Entity>();
    for (var e : l.getEntities().getAll())
      if (e.getTags().contains("ffa.portal." + p.id)) copy.add(e);
    copy.forEach(Entity::discard);
  }
}
