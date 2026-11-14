package dev.fantasyfeudalism.administration;

import java.util.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server collision uses rectangles; vanilla clients receive only the nearby real edges. */
public final class WorldBorders implements AutoCloseable {
  private final Administration app;
  private final Map<UUID, View> views = new HashMap<>();
  private final Set<WorldBorder> attached = Collections.newSetFromMap(new IdentityHashMap<>());
  static final double VIEW_SIZE = 1_000_000_000;

  record View(double centerX, double centerZ) {}

  private static final class VisualPacket extends ClientboundInitializeBorderPacket {
    VisualPacket(WorldBorder border) {
      super(border);
    }
  }

  public WorldBorders(Administration app) {
    this.app = app;
  }

  Model.Border bounds(ServerLevel level) {
    return app.storage.state().worldBorders.get(level.dimension().location().toString());
  }

  public void set(ServerPlayer player, Commands.Selection selection) throws Exception {
    if (!selection.dimension().equals(player.serverLevel().dimension().location().toString()))
      throw new IllegalArgumentException("Select both corners in this dimension.");
    var border = new Model.Border();
    border.minX = Math.min(selection.first().getX(), selection.second().getX());
    border.maxX = Math.max(selection.first().getX(), selection.second().getX());
    border.minZ = Math.min(selection.first().getZ(), selection.second().getZ());
    border.maxZ = Math.max(selection.first().getZ(), selection.second().getZ());
    border.validate(app.server.getAbsoluteMaxWorldSize());
    app.storage.update(s -> s.worldBorders.put(selection.dimension(), border));
    refresh();
  }

  public void clear(ServerLevel level) throws Exception {
    String id = level.dimension().location().toString();
    if (bounds(level) == null)
      throw new IllegalArgumentException("No FFA world border in this dimension.");
    app.storage.update(s -> s.worldBorders.remove(id));
    refresh();
  }

  public String describe(ServerLevel level) {
    var b = bounds(level);
    if (b == null) return "No FFA world border in this dimension.";
    return "World border: X "
        + b.minX
        + " to "
        + b.maxX
        + ", Z "
        + b.minZ
        + " to "
        + b.maxZ
        + " ("
        + ((long) b.maxX - b.minX + 1)
        + " × "
        + ((long) b.maxZ - b.minZ + 1)
        + " blocks).";
  }

  public void refresh() {
    for (var level : app.server.getAllLevels()) {
      var border = level.getWorldBorder();
      var bounds = bounds(level);
      if (bounds != null) bounds.validate(app.server.getAbsoluteMaxWorldSize());
      ((RectangularBorderAccess) border).ffa$bounds(bounds);
      attached.add(border);
    }
    for (var player : app.server.getPlayerList().getPlayers()) {
      views.remove(player.getUUID());
      if (bounds(player.serverLevel()) == null)
        player.connection.send(nativePacket(player.serverLevel()));
      else update(player);
    }
  }

  static View view(Model.Border border, double x, double z, int distance) {
    double minX = border.minX,
        maxX = (double) border.maxX + 1,
        minZ = border.minZ,
        maxZ = (double) border.maxZ + 1;
    boolean left = Math.abs(x - minX) <= Math.abs(maxX - x);
    boolean north = Math.abs(z - minZ) <= Math.abs(maxZ - z);
    double edgeX = left ? minX : maxX, edgeZ = north ? minZ : maxZ;
    return new View(
        Math.abs(x - edgeX) <= distance ? edgeX + (left ? VIEW_SIZE / 2 : -VIEW_SIZE / 2) : 0,
        Math.abs(z - edgeZ) <= distance ? edgeZ + (north ? VIEW_SIZE / 2 : -VIEW_SIZE / 2) : 0);
  }

  static ClientboundInitializeBorderPacket packet(View view) {
    var border = new WorldBorder();
    border.setAbsoluteMaxSize(Integer.MAX_VALUE);
    border.setSize(VIEW_SIZE);
    border.setCenter(view.centerX(), view.centerZ());
    border.setWarningBlocks(0);
    border.setWarningTime(0);
    return new VisualPacket(border);
  }

  private static VisualPacket nativePacket(ServerLevel level) {
    var original = new WorldBorder();
    original.setAbsoluteMaxSize(level.getWorldBorder().getAbsoluteMaxSize());
    original.applySettings(level.getWorldBorder().createSettings());
    return new VisualPacket(original);
  }

  public static Packet<?> filter(ServerPlayer player, Packet<?> packet) {
    if (packet instanceof VisualPacket || !isBorderPacket(packet)) return packet;
    var app = Administration.INSTANCE;
    if (app == null || app.server == null || app.worldBorders == null) return packet;
    var self = app.worldBorders;
    var b = self.bounds(player.serverLevel());
    if (b == null) {
      self.views.remove(player.getUUID());
      return app.storage.state().worldBorders.isEmpty()
          ? packet
          : nativePacket(player.serverLevel());
    }
    var view = view(b, player.getX(), player.getZ(), app.settings.borderVisibilityDistance);
    self.views.put(player.getUUID(), view);
    return packet(view);
  }

  private static boolean isBorderPacket(Packet<?> packet) {
    return packet instanceof ClientboundInitializeBorderPacket
        || packet instanceof ClientboundSetBorderCenterPacket
        || packet instanceof ClientboundSetBorderSizePacket
        || packet instanceof ClientboundSetBorderLerpSizePacket
        || packet instanceof ClientboundSetBorderWarningDelayPacket
        || packet instanceof ClientboundSetBorderWarningDistancePacket;
  }

  public void update(ServerPlayer player) {
    var b = bounds(player.serverLevel());
    if (b == null) {
      if (views.remove(player.getUUID()) != null)
        player.connection.send(nativePacket(player.serverLevel()));
      return;
    }
    var view = view(b, player.getX(), player.getZ(), app.settings.borderVisibilityDistance);
    if (!view.equals(views.put(player.getUUID(), view))) player.connection.send(packet(view));
  }

  @SubscribeEvent
  public void tick(ServerTickEvent.Post event) {
    for (var player : app.server.getPlayerList().getPlayers()) update(player);
  }

  @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
  public void login(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) update(player);
  }

  @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
  public void respawn(PlayerEvent.PlayerRespawnEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) update(player);
  }

  @SubscribeEvent
  public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) update(player);
  }

  @SubscribeEvent
  public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
    views.remove(event.getEntity().getUUID());
  }

  @Override
  public void close() {
    for (var border : attached) ((RectangularBorderAccess) border).ffa$bounds(null);
    attached.clear();
    views.clear();
  }
}
