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
          if (portal.warp == null || portal.warp.isEmpty())
            throw new IllegalArgumentException("Portal destination is not set.");
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
    if (!app.settings.portals) return;
    boolean normalX = p.maxX - p.minX < p.maxZ - p.minZ;
    int width = normalX ? p.maxZ - p.minZ + 1 : p.maxX - p.minX + 1, height = p.maxY - p.minY + 1;
    int step = Math.max(1, (int) Math.ceil(Math.sqrt(width * (double) height / 256.0)));
    while (((width + step - 1) / step) * ((height + step - 1) / step) > 256) step++;
    double normal = normalX ? (p.minX + p.maxX + 1.0) / 2 : (p.minZ + p.maxZ + 1.0) / 2;
    DyeColor color =
        Arrays.stream(DyeColor.values())
            .filter(d -> d.getName().equals(p.color))
            .findFirst()
            .orElse(DyeColor.PURPLE);
    for (int u = 0; u < width; u += step)
      for (int v = 0; v < height; v += step) {
        int w = Math.min(step, width - u), h = Math.min(step, height - v);
        double x = normalX ? normal : p.minX + u + w / 2.0,
            z = normalX ? p.minZ + u + w / 2.0 : normal,
            y = p.minY + v + h / 2.0;
        if (!level.hasChunkAt(BlockPos.containing(x, y, z))) continue;
        Entity marker;
        var data = new net.minecraft.nbt.CompoundTag();
        if (app.settings.portalPack) {
          marker = EntityType.ITEM_DISPLAY.create(level);
          if (marker == null) continue;
          var item = new ItemStack(Items.PURPLE_STAINED_GLASS);
          item.set(
              net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
              new net.minecraft.world.item.component.CustomModelData(600000 + color.getId()));
          marker.setPos(x, y, z);
          marker.saveWithoutId(data);
          data.put("item", item.save(level.registryAccess()));
          data.putString("item_display", "fixed");
          data.put("transformation", transform(w, h, 1, normalX));
        } else {
          marker = EntityType.BLOCK_DISPLAY.create(level);
          if (marker == null) continue;
          marker.setPos(
              normalX ? normal - .5 : p.minX + u, p.minY + v, normalX ? p.minZ + u : normal - .5);
          marker.saveWithoutId(data);
          data.put("transformation", transform(normalX ? 1 : w, h, normalX ? w : 1, false));
        }
        var light = new net.minecraft.nbt.CompoundTag();
        light.putInt("block", 15);
        light.putInt("sky", 15);
        data.put("brightness", light);
        marker.load(data);
        marker.addTag("ffa.portal." + p.id);
        marker.setInvulnerable(true);
        if (marker instanceof Display.BlockDisplay display)
          ((BlockDisplayAccess) display)
              .ffa$block(
                  net.minecraft.world.level.block.Blocks.NETHER_PORTAL
                      .defaultBlockState()
                      .setValue(
                          net.minecraft.world.level.block.NetherPortalBlock.AXIS,
                          normalX
                              ? net.minecraft.core.Direction.Axis.Z
                              : net.minecraft.core.Direction.Axis.X));
        level.addFreshEntity(marker);
        var interaction = EntityType.INTERACTION.create(level);
        if (interaction != null) {
          var hit = new net.minecraft.nbt.CompoundTag();
          interaction.saveWithoutId(hit);
          hit.putFloat("width", w);
          hit.putFloat("height", h);
          hit.putBoolean("response", true);
          interaction.load(hit);
          interaction.setPos(x, p.minY + v, z);
          interaction.addTag("ffa.portal." + p.id);
          interaction.setInvulnerable(true);
          level.addFreshEntity(interaction);
        }
      }
  }

  private static net.minecraft.nbt.ListTag floats(float... values) {
    var list = new net.minecraft.nbt.ListTag();
    for (float value : values) list.add(net.minecraft.nbt.FloatTag.valueOf(value));
    return list;
  }

  private static net.minecraft.nbt.CompoundTag transform(
      float x, float y, float z, boolean rotate) {
    var data = new net.minecraft.nbt.CompoundTag();
    data.put("translation", floats(0, 0, 0));
    data.put("scale", floats(x, y, z));
    data.put("left_rotation", rotate ? floats(0, .70710677f, 0, .70710677f) : floats(0, 0, 0, 1));
    data.put("right_rotation", floats(0, 0, 0, 1));
    return data;
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
