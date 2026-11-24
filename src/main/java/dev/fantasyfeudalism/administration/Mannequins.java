package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import dev.fantasyfeudalism.administration.mixin.InfoPacketAccess;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Static vanilla player/text packets. Never add a player or entity to the server world. */
public final class Mannequins implements AutoCloseable {
  static final List<EquipmentSlot> SLOTS =
      List.of(
          EquipmentSlot.MAINHAND,
          EquipmentSlot.OFFHAND,
          EquipmentSlot.HEAD,
          EquipmentSlot.CHEST,
          EquipmentSlot.LEGS,
          EquipmentSlot.FEET);
  private final Administration app;
  private final Map<String, Visual> visuals = new HashMap<>();
  private final Map<UUID, Map<String, Visual>> viewers = new HashMap<>();
  private final Map<String, UUID> pending = new HashMap<>();
  private volatile boolean closed;
  private final java.util.function.Function<String, CompletableFuture<GameProfile>> skinLookup;

  public Mannequins(Administration app) {
    this(app, null);
  }

  Mannequins(
      Administration app,
      java.util.function.Function<String, CompletableFuture<GameProfile>> skinLookup) {
    this.app = app;
    this.skinLookup = skinLookup == null ? this::lookupSkin : skinLookup;
  }

  private CompletableFuture<GameProfile> lookupSkin(String account) {
    var server = app.server;
    return server
        .getProfileCache()
        .getAsync(account)
        .thenApplyAsync(
            found -> {
              var profile =
                  found.orElseThrow(
                      () -> new IllegalArgumentException("Unknown Minecraft account."));
              var result = server.getSessionService().fetchProfile(profile.getId(), true);
              if (result == null || result.profile().getProperties().get("textures").isEmpty())
                throw new IllegalArgumentException(
                    "Skin unavailable. Have that player join and try again.");
              return result.profile();
            },
            Util.backgroundExecutor());
  }

  public Mannequin get(String id) {
    return app.storage.state().mannequins.values().stream()
        .filter(m -> m.id.equalsIgnoreCase(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown mannequin."));
  }

  private void freeName(String id) {
    if (id == null || !id.matches("[A-Za-z0-9_-]{1,32}"))
      throw new IllegalArgumentException("Use 1–32 letters, numbers, underscores or hyphens.");
    if (app.storage.state().mannequins.values().stream().anyMatch(m -> m.id.equalsIgnoreCase(id)))
      throw new IllegalArgumentException("A mannequin already has that name.");
    if (app.storage.state().mannequins.size() + pending.size() >= 128)
      throw new IllegalArgumentException(
          "The world already has 128 mannequins or pending creations.");
  }

  public void create(CommandSourceStack source, String id, String account) throws Exception {
    freeName(id);
    Point point = Teleports.point(source.getPlayerOrException());
    resolve(
        source,
        id,
        account,
        profile -> {
          freeName(id);
          create(id, point, profile);
          say(source, "Mannequin created.");
        });
  }

  /** Also used by native regression fixtures with their own saved texture properties. */
  void create(String id, Point point, GameProfile profile) throws Exception {
    freeName(id);
    var m = new Mannequin();
    m.id = id;
    m.point = point;
    appearance(m, profile);
    m.validate();
    app.storage.update(s -> s.mannequins.put(id, m));
    invalidate(id);
  }

  public void skin(CommandSourceStack source, String id, String account) throws Exception {
    String savedId = get(id).id;
    resolve(
        source,
        savedId,
        account,
        profile -> {
          edit(savedId, m -> appearance(m, profile));
          say(source, "Mannequin skin updated.");
        });
  }

  private static void appearance(Mannequin m, GameProfile profile) {
    m.accountId = profile.getId().toString();
    m.accountName = profile.getName();
    var textures = profile.getProperties().get("textures");
    Property texture = textures.isEmpty() ? null : textures.iterator().next();
    m.texture = texture == null ? "" : texture.value();
    m.signature = texture == null || texture.signature() == null ? "" : texture.signature();
  }

  @FunctionalInterface
  private interface ProfileAction {
    void accept(GameProfile profile) throws Exception;
  }

  private void resolve(CommandSourceStack source, String id, String account, ProfileAction action)
      throws Exception {
    if (account == null || !account.matches("[A-Za-z0-9_]{1,16}"))
      throw new IllegalArgumentException("Use a Minecraft account name.");
    String key = id.toLowerCase(Locale.ROOT);
    if (pending.containsKey(key)) throw new IllegalArgumentException("That skin is still loading.");
    var online = app.server.getPlayerList().getPlayerByName(account);
    if (online != null) {
      action.accept(online.getGameProfile());
      return;
    }
    if (pending.size() >= 8)
      throw new IllegalArgumentException("Wait for the other skins to finish loading.");
    var server = app.server;
    UUID token = UUID.randomUUID();
    pending.put(key, token);
    say(source, "Loading skin.");
    // Profile and skin HTTP requests stay off the tick thread. Saved skins need no lookup on
    // restart.
    CompletableFuture<GameProfile> loading;
    try {
      loading = skinLookup.apply(account);
    } catch (RuntimeException ex) {
      pending.remove(key);
      throw ex;
    }
    loading
        .orTimeout(20, TimeUnit.SECONDS)
        .whenComplete(
            (profile, error) -> {
              if (closed) return;
              server.execute(
                  () -> {
                    if (closed || !token.equals(pending.get(key))) return;
                    pending.remove(key);
                    var actor = source.getPlayer();
                    if (actor != null
                        && (server.getPlayerList().getPlayer(actor.getUUID()) != actor
                            || !StaffCommands.admin(actor.createCommandSourceStack()))) return;
                    if (actor == null && !StaffCommands.admin(source)) return;
                    if (error != null) {
                      source.sendFailure(
                          Component.literal(
                              "Could not load that skin. Have the player join and try again."));
                      return;
                    }
                    try {
                      action.accept(profile);
                    } catch (Exception ex) {
                      source.sendFailure(
                          Component.literal("Could not save mannequin: " + ex.getMessage()));
                    }
                  });
            });
  }

  public void edit(String id, Consumer<Mannequin> edit) throws Exception {
    String key = get(id).id;
    app.storage.update(
        s -> {
          var m = s.mannequins.get(key);
          edit.accept(m);
          m.validate();
        });
    invalidate(key);
  }

  public void delete(String id) throws Exception {
    var found =
        app.storage.state().mannequins.values().stream()
            .filter(m -> m.id.equalsIgnoreCase(id))
            .findFirst();
    boolean loading = pending.containsKey(id.toLowerCase(Locale.ROOT));
    if (found.isEmpty() && !loading) throw new IllegalArgumentException("Unknown mannequin.");
    // Commit first, so a failed save never removes an existing public display.
    if (found.isPresent()) {
      String key = found.get().id;
      app.storage.update(s -> s.mannequins.remove(key));
      invalidate(key);
    }
    pending.remove(id.toLowerCase(Locale.ROOT));
  }

  public void equipment(String id, ServerPlayer source) throws Exception {
    var equipment = new TreeMap<String, String>();
    var ops = app.server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
    for (var slot : SLOTS) {
      var stack = source.getItemBySlot(slot);
      if (!stack.isEmpty())
        equipment.put(
            slot.getName(), ItemStack.CODEC.encodeStart(ops, stack.copy()).getOrThrow().toString());
    }
    edit(id, m -> m.equipment = equipment);
  }

  private void invalidate(String id) {
    visuals.remove(id);
  }

  private static void say(CommandSourceStack source, String message) {
    source.sendSuccess(() -> Component.literal(message), false);
  }

  private static final class Body extends Player {
    Body(ServerLevel level, GameProfile profile, Point point) {
      super(level, BlockPos.containing(point.x, point.y, point.z), point.yaw, profile);
      moveTo(point.x, point.y, point.z, point.yaw, point.pitch);
      setYHeadRot(point.yaw);
      setNoGravity(true);
      getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 127);
    }

    @Override
    public boolean isSpectator() {
      return false;
    }

    @Override
    public boolean isCreative() {
      return false;
    }
  }

  private record Visual(
      Body body,
      Display.TextDisplay label,
      PlayerTeam team,
      Point point,
      List<Pair<EquipmentSlot, ItemStack>> equipment) {}

  static Component label(Mannequin m) {
    var text =
        Component.literal(m.text.getOrDefault("nickname", m.accountName))
            .setStyle(ChatStyles.style(m.text, "name"));
    String title = m.text.getOrDefault("title", "");
    if (!title.isEmpty())
      text.append("\n")
          .append(Component.literal(title).setStyle(ChatStyles.style(m.text, "title")));
    return text;
  }

  private Visual visual(Mannequin m, ServerLevel level) {
    UUID uuid = UUID.randomUUID();
    String internalName = "FFA_" + uuid.toString().replace("-", "").substring(0, 12);
    var profile = new GameProfile(uuid, internalName);
    if (!m.texture.isEmpty())
      profile
          .getProperties()
          .put(
              "textures",
              m.signature.isEmpty()
                  ? new Property("textures", m.texture)
                  : new Property("textures", m.texture, m.signature));
    var body = new Body(level, profile, m.point);
    var board = new Scoreboard();
    var team = board.addPlayerTeam("ffaM" + uuid.toString().replace("-", "").substring(0, 12));
    team.setNameTagVisibility(Team.Visibility.NEVER);
    team.setCollisionRule(Team.CollisionRule.NEVER);
    board.addPlayerToTeam(internalName, team);
    Display.TextDisplay label = null;
    if (m.label) {
      label = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
      var tag = new CompoundTag();
      tag.putString("billboard", "center");
      tag.putString("alignment", "center");
      tag.putString("text", Component.Serializer.toJson(label(m), app.server.registryAccess()));
      tag.putInt("background", 0);
      tag.putBoolean("shadow", true);
      tag.putInt("line_width", 250);
      label.load(tag);
      label.setPos(m.point.x, m.point.y + 2.15, m.point.z);
    }
    var ops = app.server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
    List<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
    for (var slot : SLOTS) {
      ItemStack stack = ItemStack.EMPTY;
      String data = m.equipment.get(slot.getName());
      if (data != null)
        try {
          stack = ItemStack.CODEC.parse(ops, JsonParser.parseString(data)).getOrThrow();
        } catch (RuntimeException ex) {
          Administration.LOG.warn(
              "Mannequin {} cannot display saved {} equipment: {}", m.id, slot, ex.getMessage());
        }
      equipment.add(Pair.of(slot, stack));
    }
    return new Visual(body, label, team, m.point, equipment);
  }

  private static ClientboundAddEntityPacket spawn(Entity entity) {
    return new ClientboundAddEntityPacket(
        entity.getId(),
        entity.getUUID(),
        entity.getX(),
        entity.getY(),
        entity.getZ(),
        entity.getXRot(),
        entity.getYRot(),
        entity.getType(),
        0,
        Vec3.ZERO,
        entity.getYHeadRot());
  }

  private static void add(ServerPlayer viewer, Visual visual) {
    var profile = visual.body.getGameProfile();
    var info =
        new ClientboundPlayerInfoUpdatePacket(
            EnumSet.of(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE),
            List.of());
    ((InfoPacketAccess) (Object) info)
        .ffa$entries(
            List.of(
                new ClientboundPlayerInfoUpdatePacket.Entry(
                    profile.getId(), profile, false, 0, GameType.ADVENTURE, null, null)));
    viewer.connection.send(info);
    viewer.connection.send(
        ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(visual.team, true));
    viewer.connection.send(spawn(visual.body));
    var data = visual.body.getEntityData().getNonDefaultValues();
    if (data != null)
      viewer.connection.send(new ClientboundSetEntityDataPacket(visual.body.getId(), data));
    viewer.connection.send(
        new ClientboundSetEquipmentPacket(visual.body.getId(), visual.equipment));
    if (visual.label != null) {
      viewer.connection.send(spawn(visual.label));
      var labelData = visual.label.getEntityData().getNonDefaultValues();
      if (labelData != null)
        viewer.connection.send(new ClientboundSetEntityDataPacket(visual.label.getId(), labelData));
    }
  }

  private static void remove(ServerPlayer viewer, Visual visual) {
    viewer.connection.send(
        new ClientboundRemoveEntitiesPacket(
            visual.label == null
                ? new int[] {visual.body.getId()}
                : new int[] {visual.body.getId(), visual.label.getId()}));
    viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(visual.body.getUUID())));
    viewer.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(visual.team));
  }

  /** Reconcile visible displays without loading chunks or changing actual online-player state. */
  public void refresh(ServerPlayer viewer) {
    var seen = viewers.computeIfAbsent(viewer.getUUID(), id -> new HashMap<>());
    Map<String, Visual> wanted = new HashMap<>();
    for (var m : app.storage.state().mannequins.values()) {
      var p = m.point;
      int chunkX = BlockPos.containing(p.x, p.y, p.z).getX() >> 4;
      int chunkZ = BlockPos.containing(p.x, p.y, p.z).getZ() >> 4;
      if (!p.dimension.equals(viewer.level().dimension().location().toString())
          || viewer.distanceToSqr(p.x, p.y, p.z) > 64 * 64
          || !viewer.getChunkTrackingView().contains(chunkX, chunkZ)
          || viewer.connection.chunkSender.isPending(ChunkPos.asLong(chunkX, chunkZ))) continue;
      wanted.put(m.id, visuals.computeIfAbsent(m.id, id -> visual(m, viewer.serverLevel())));
    }
    seen.entrySet()
        .removeIf(
            e -> {
              if (wanted.get(e.getKey()) == e.getValue()) return false;
              remove(viewer, e.getValue());
              return true;
            });
    wanted.forEach(
        (id, visual) -> {
          if (!seen.containsKey(id)) {
            add(viewer, visual);
            seen.put(id, visual);
          }
        });
    visuals.keySet().removeIf(id -> !app.storage.state().mannequins.containsKey(id));
  }

  @SubscribeEvent
  public void tick(ServerTickEvent.Post event) {
    if (!closed && app.server.getTickCount() % 10 == 0)
      for (var player : app.server.getPlayerList().getPlayers()) refresh(player);
  }

  public void reset(ServerPlayer player) {
    var seen = viewers.remove(player.getUUID());
    if (seen != null) seen.values().forEach(v -> remove(player, v));
  }

  @SubscribeEvent
  public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) reset(player);
  }

  @SubscribeEvent
  public void respawn(PlayerEvent.PlayerRespawnEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) reset(player);
  }

  @SubscribeEvent
  public void unwatch(ChunkWatchEvent.UnWatch event) {
    var seen = viewers.get(event.getPlayer().getUUID());
    if (seen != null)
      seen.entrySet()
          .removeIf(
              e -> {
                var v = e.getValue();
                if (v.body.level() == event.getLevel()
                    && new ChunkPos(BlockPos.containing(v.point.x, v.point.y, v.point.z))
                        .equals(event.getPos())) {
                  remove(event.getPlayer(), v);
                  return true;
                }
                return false;
              });
  }

  @SubscribeEvent
  public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
    viewers.remove(event.getEntity().getUUID());
  }

  @Override
  public void close() {
    closed = true;
    pending.clear();
    if (app.server != null)
      for (var player : app.server.getPlayerList().getPlayers()) reset(player);
    visuals.clear();
    viewers.clear();
  }
}
