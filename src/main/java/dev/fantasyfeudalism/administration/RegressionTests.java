package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Opt-in tests using real players, packets, saves, mixins, event handlers and registries. */
@PrefixGameTestTemplate(false)
public final class RegressionTests {
  private static ServerPlayer player(GameTestHelper h, String name) {
    var cookie =
        CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
    var p =
        new ServerPlayer(
            h.getLevel().getServer(),
            h.getLevel(),
            cookie.gameProfile(),
            cookie.clientInformation());
    var c = new Connection(PacketFlow.SERVERBOUND);
    new EmbeddedChannel(c);
    h.getLevel().getServer().getPlayerList().placeNewPlayer(c, p, cookie);
    net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(c);
    return p;
  }

  @GameTest(template = "arena", templateNamespace = "ffa", timeoutTicks = 100, batch = "ffa_core")
  public static void coreSecurityAndPersistence(GameTestHelper h) throws Exception {
    Administration app = Administration.INSTANCE;
    h.assertTrue(app.storage != null, "Administration initialized before test");
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        h.setBlock(x, 0, z, Blocks.STONE);
        for (int y = 1; y < 7; y++) h.setBlock(x, y, z, Blocks.AIR);
      }
    var before = app.regions.copy();
    State old = app.storage.copy();
    ServerPlayer p = player(h, "ffaPlayer");
    try {
      p.setGameMode(GameType.SURVIVAL);
      p.setPos(h.absoluteVec(new Vec3(12.5, 1, 12.5)));
      app.protection.update(p);
      Region r = new Region();
      r.id = r.name = "test_spawn";
      r.kind = Kind.SPAWN;
      r.dimension = h.getLevel().dimension().location().toString();
      BlockPos lo = h.absolutePos(new BlockPos(1, 1, 1)), hi = h.absolutePos(new BlockPos(8, 1, 8));
      r.minX = lo.getX();
      r.minZ = lo.getZ();
      r.maxX = hi.getX();
      r.maxZ = hi.getZ();
      r.settlementOverride = true;
      app.regions.edit(List.of(r), app.server, app.settings, app.compat);
      p.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
      p.giveExperiencePoints(51);
      int xp = p.totalExperience;
      String inventory = p.getInventory().save(new ListTag()).toString();
      p.setPos(h.absoluteVec(new Vec3(4.5, 1, 4.5)));
      app.protection.update(p);
      h.assertTrue(p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE, "Adventure on entry");
      h.assertTrue(
          p.totalExperience == xp
              && p.getInventory().save(new ListTag()).toString().equals(inventory),
          "Region entry preserves inventory and XP");
      p.getFoodData().setFoodLevel(15);
      p.getFoodData().setSaturation(3);
      p.getFoodData().addExhaustion(8);
      p.getFoodData().tick(p);
      h.assertTrue(
          p.getFoodData().getFoodLevel() == 15 && p.getFoodData().getSaturationLevel() == 3,
          "Hunger and saturation frozen");
      BlockPos inside = h.absolutePos(new BlockPos(4, 1, 4));
      var fake = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
      h.assertFalse(app.compat.admin(fake), "Fake players never inherit administrator exemption");
      h.assertTrue(
          app.protection.denied(fake, h.getLevel(), inside, Flag.BREAK),
          "Fake-player automation is blocked by default");
      h.assertFalse(
          h.getLevel().setBlock(inside, Blocks.FIRE.defaultBlockState(), 3),
          "Fire entry blocked at target");
      BlockPos wall = h.absolutePos(new BlockPos(5, 1, 5));
      h.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
      Point blocked = new Point(r.dimension, wall.getX() + .5, wall.getY(), wall.getZ() + .5, 0, 0);
      h.assertTrue(app.teleports.validate(p, blocked) != null, "Solid destination rejected");
      Vec3 position = p.position();
      p.teleportTo(h.getLevel(), blocked.x, blocked.y, blocked.z, 0, 0);
      h.assertTrue(
          p.position().equals(position), "Vanilla teleport path also rejects wall clipping");
      var zombie = EntityType.ZOMBIE.create(h.getLevel());
      zombie.setPos(p.position());
      var spawn = new MobSpawnEvent.PositionCheck(zombie, h.getLevel(), MobSpawnType.NATURAL, null);
      NeoForge.EVENT_BUS.post(spawn);
      h.assertTrue(
          spawn.getResult() == MobSpawnEvent.PositionCheck.Result.FAIL,
          "Natural mob spawning blocked");
      p.setPos(h.absoluteVec(new Vec3(12.5, 1, 12.5)));
      app.protection.update(p);
      h.assertTrue(
          p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "Prior mode restored on exit");
      app.server
          .getPlayerList()
          .getOps()
          .add(new net.minecraft.server.players.ServerOpListEntry(p.getGameProfile(), 4, false));
      p.setPos(h.absoluteVec(new Vec3(4.5, 1, 4.5)));
      app.protection.update(p);
      h.assertTrue(
          p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL,
          "Administrator exempt from mode rules");
      h.assertFalse(
          app.protection.denied(p, h.getLevel(), inside, Flag.BREAK),
          "Administrator exempt from protection");
      app.server.getPlayerList().deop(p.getGameProfile());
      p.setPos(h.absoluteVec(new Vec3(12.5, 1, 12.5)));
      app.protection.update(p);
      Shop shop = new Shop();
      shop.item = "minecraft:stone";
      shop.buy = 5;
      shop.sell = 2;
      app.storage.update(
          s -> {
            s.shops.put("test", shop);
            s.players.get(p.getUUID().toString()).balance = 100;
          });
      app.economy.trade(p, "test", 3, true);
      h.assertTrue(
          app.storage.player(p.getUUID()).balance == 85
              && p.getInventory().countItem(Items.STONE) == 3,
          "Purchase commits item and balance once");
      app.economy.trade(p, "test", 2, false);
      h.assertTrue(
          app.storage.player(p.getUUID()).balance == 89
              && p.getInventory().countItem(Items.STONE) == 1,
          "Sale removes items before credit");
      boolean invalid = false;
      try {
        app.economy.trade(p, "test", -1, true);
      } catch (IllegalArgumentException e) {
        invalid = true;
      }
      h.assertTrue(
          invalid && app.storage.player(p.getUUID()).balance == 89,
          "Invalid quantity leaves state unchanged");
      PendingTrade prepared = new PendingTrade();
      prepared.id = UUID.randomUUID().toString();
      prepared.player = p.getUUID().toString();
      prepared.before = "different";
      prepared.after = p.getInventory().save(new ListTag()).toString();
      prepared.oldBalance = 89;
      prepared.newBalance = 84;
      app.storage.update(s -> s.trades.put(prepared.player, prepared));
      app.economy.recover(p);
      app.economy.recover(p);
      h.assertTrue(
          app.storage.player(p.getUUID()).balance == 84
              && !app.storage.state().trades.containsKey(prepared.player),
          "Interrupted trade recovery settles exactly once");
      additional(h, p, app);
      int shopCommand =
          app.server
              .getCommands()
              .getDispatcher()
              .execute(
                  "ffa shop set command_test minecraft:stone 5 2",
                  p.createCommandSourceStack().withPermission(4));
      h.assertTrue(
          shopCommand == 1
              && app.storage.state().shops.get("command_test").item.equals("minecraft:stone"),
          "Actual namespaced shop command validates and persists");
      int unauthorized = 0;
      try {
        unauthorized =
            app.server
                .getCommands()
                .getDispatcher()
                .execute(
                    "ffa region delete test_spawn", p.createCommandSourceStack().withPermission(0));
      } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
        // Native registration hides unauthorized administrative branches from the client.
      }
      h.assertTrue(
          unauthorized == 0 && app.regions.all().size() == 1,
          "Unauthorized command cannot mutate regions");
      int flagCommand =
          app.server
              .getCommands()
              .getDispatcher()
              .execute(
                  "ffa region flag test_spawn pvp true",
                  p.createCommandSourceStack().withPermission(4));
      h.assertTrue(
          flagCommand == 1 && Regions.read(app.regions.file).getFirst().flags.get(Flag.PVP),
          "Administrative flag command updates the authoritative TOML");
      commandSuggestions(h, p, app);
      if (net.neoforged.fml.ModList.get().isLoaded("villagerscontract")) mercenaries(h, p, app);
      app.teleports.request(p, p, false);
      h.fail("Self teleport request must fail");
    } catch (IllegalArgumentException expected) {
      h.assertTrue(
          expected.getMessage().contains("another player"),
          "Only final self-request rejection expected: " + expected.getMessage());
      h.succeed();
    } finally {
      app.server.getPlayerList().deop(p.getGameProfile());
      app.regions.edit(before, app.server, app.settings, app.compat);
      app.server.getPlayerList().remove(p);
      app.storage.commit(old);
    }
  }

  private static java.util.List<String> complete(
      Administration app, net.minecraft.commands.CommandSourceStack source, String input) {
    var dispatcher = app.server.getCommands().getDispatcher();
    return dispatcher
        .getCompletionSuggestions(dispatcher.parse(input, source))
        .join()
        .getList()
        .stream()
        .map(com.mojang.brigadier.suggestion.Suggestion::getText)
        .toList();
  }

  private static java.util.List<Object> outgoing(ServerPlayer p) {
    var channel = (EmbeddedChannel) p.connection.getConnection().channel();
    channel.runPendingTasks();
    channel.flushOutbound();
    var result = new ArrayList<Object>();
    Object packet;
    while ((packet = channel.readOutbound()) != null) result.add(packet);
    return result;
  }

  private static com.mojang.brigadier.tree.RootCommandNode<
          net.minecraft.commands.SharedSuggestionProvider>
      clientCommands(ServerPlayer p, boolean request) {
    if (request) {
      outgoing(p);
      p.server.getCommands().sendCommands(p);
    }
    net.minecraft.network.protocol.game.ClientboundCommandsPacket found = null;
    for (Object packet : outgoing(p))
      if (packet instanceof net.minecraft.network.protocol.game.ClientboundCommandsPacket commands)
        found = commands;
    if (found == null) throw new AssertionError("Server did not send a client command tree");
    return found.getRoot(
        net.minecraft.commands.CommandBuildContext.simple(
            p.server.registryAccess(), p.serverLevel().enabledFeatures()));
  }

  private static void commandSuggestions(GameTestHelper h, ServerPlayer p, Administration app)
      throws Exception {
    var admin = p.createCommandSourceStack().withPermission(4);
    var ordinary = p.createCommandSourceStack().withPermission(0);
    h.assertTrue(
        complete(app, admin, "ffa ").containsAll(List.of("region", "spawn", "portal", "help")),
        "Native subcommands complete after ffa and a space");
    h.assertTrue(
        complete(app, admin, "ffa reg").contains("region"), "Partial root command completes");
    h.assertTrue(
        complete(app, admin, "ffa region ")
            .containsAll(List.of("create", "flag", "visualize", "list")),
        "Region actions complete");
    h.assertTrue(
        complete(app, admin, "ffa region flag test_").contains("test_spawn"),
        "Current region IDs complete");
    h.assertTrue(
        complete(app, admin, "ffa region flag test_spawn p").contains("pvp"),
        "Region flag names complete");
    h.assertTrue(
        complete(app, admin, "ffa region flag test_spawn pvp ")
            .containsAll(List.of("true", "false", "inherit")),
        "Flag values complete");
    h.assertTrue(
        complete(app, admin, "ffa shop set example minecraft:sto").contains("minecraft:stone"),
        "Namespaced item IDs complete");
    int redirect =
        app.server
            .getCommands()
            .getDispatcher()
            .execute("execute as @s run ffa region flag test_spawn hunger false", admin);
    h.assertTrue(redirect == 1, "Command forwarding works inside vanilla execute redirects");
    int quoted =
        app.server
            .getCommands()
            .getDispatcher()
            .execute("ffa region message test_spawn entry \"Welcome to Spawn!\"", admin);
    h.assertTrue(
        quoted == 1 && app.regions.get("test_spawn").entry.equals("Welcome to Spawn!"),
        "Quoted messages retain their content with native arguments");

    Warp visible = new Warp();
    visible.point = Teleports.point(p);
    Warp hidden = new Warp();
    hidden.point = Teleports.point(p);
    hidden.access.mode = "admin";
    UUID other = UUID.randomUUID();
    app.storage.update(
        state -> {
          state.warps.put("visible_completion", visible);
          state.warps.put("hidden_completion", hidden);
          state
              .players
              .get(p.getUUID().toString())
              .homes
              .put("my_completion_home", Teleports.point(p));
          PlayerData data = new PlayerData();
          data.homes.put("other_completion_home", Teleports.point(p));
          state.players.put(other.toString(), data);
          Model.Portal portal = new Model.Portal();
          portal.id = "completion_portal";
          portal.dimension = p.level().dimension().location().toString();
          portal.warp = "visible_completion";
          state.portals.put(portal.id, portal);
        });
    try {
      h.assertTrue(
          complete(app, ordinary, "ffa warp ").contains("visible_completion")
              && !complete(app, ordinary, "ffa warp ").contains("hidden_completion"),
          "Players only receive accessible warp names");
      h.assertTrue(
          complete(app, admin, "ffa portal link completion_portal ").contains("hidden_completion"),
          "Authorized portal managers can complete managed destinations");
      h.assertTrue(
          complete(app, admin, "ffa portal color completion_portal light_")
              .containsAll(List.of("light_blue", "light_gray")),
          "Portal dye colors complete");
      if (app.settings.homes && !app.compat.ftbEssentials())
        h.assertTrue(
            complete(app, ordinary, "ffa home ").contains("my_completion_home")
                && !complete(app, ordinary, "ffa home ").contains("other_completion_home"),
            "Home suggestions never include another player's homes");
      h.assertFalse(
          complete(app, ordinary, "ffa region visualize ").contains("test_spawn"),
          "Unauthorized sources do not receive private region names");
      app.server
          .getPlayerList()
          .getOps()
          .add(new net.minecraft.server.players.ServerOpListEntry(p.getGameProfile(), 4, false));
      var operatorTree = clientCommands(p, true).getChild("ffa");
      h.assertTrue(
          operatorTree.getChild("region") != null
              && operatorTree.getChild("spawn").getChild("create") != null,
          "The actual client packet contains native administrator subcommands");
      h.assertTrue(
          operatorTree.getChild("operation") == null,
          "Client command tree has no opaque greedy operation argument");
      if (!app.settings.back)
        h.assertTrue(
            operatorTree.getChild("back") == null,
            "Disabled modules are omitted from the client command tree");
      app.server.getPlayerList().deop(p.getGameProfile());
      var playerTree = clientCommands(p, true).getChild("ffa");
      h.assertTrue(
          playerTree.getChild("spawn") != null
              && playerTree.getChild("region") == null
              && playerTree.getChild("portal") == null
              && playerTree.getChild("reload") == null,
          "Client tree filters administrator commands for ordinary players");

      String config = java.nio.file.Files.readString(app.settingsFile);
      try {
        h.assertTrue(
            config.contains("defaultPlayerCommands = true"),
            "Permission reload fixture starts from default public commands");
        outgoing(p);
        Storage.atomic(
            app.settingsFile,
            config.replace("defaultPlayerCommands = true", "defaultPlayerCommands = false"));
        app.reload();
        h.assertTrue(
            clientCommands(p, false).getChild("ffa").getChild("spawn") == null,
            "Config reload immediately sends the updated permission-filtered client tree");
      } finally {
        Storage.atomic(app.settingsFile, config);
        app.reload();
      }

      var position = p.position();
      try {
        p.setPos(h.absoluteVec(new Vec3(2.5, 1, 2.5)));
        for (String command : List.of("ffa pos1", "ffa pos2", "ffa region create override"))
          h.assertTrue(
              app.server
                      .getCommands()
                      .getDispatcher()
                      .execute(command, p.createCommandSourceStack().withPermission(4))
                  == 1,
              "Native creation command succeeds: " + command);
        h.assertFalse(
            app.regions.get("override").settlementOverride,
            "Naming a region override does not enable the explicit overlap override");
        h.assertTrue(
            complete(app, admin, "ffa region visualize ov").contains("override"),
            "Newly created regions appear in suggestions immediately");
        app.server.getCommands().getDispatcher().execute("ffa region delete override", admin);
      } finally {
        p.setPos(position);
        app.protection.update(p);
      }
    } finally {
      app.server.getPlayerList().deop(p.getGameProfile());
      app.storage.update(
          state -> {
            state.warps.remove("visible_completion");
            state.warps.remove("hidden_completion");
            state.portals.remove("completion_portal");
            state.players.remove(other.toString());
            state.players.get(p.getUUID().toString()).homes.remove("my_completion_home");
          });
      Portals.reindex(app);
    }
  }

  private static void additional(GameTestHelper h, ServerPlayer p, Administration app)
      throws Exception {
    var dimension =
        app.server.getLevel(
            net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse("ffa:spawn")));
    if (app.settings.dimension
        && dimension == null
        && app.server instanceof net.minecraft.gametest.framework.GameTestServer) {
      h.assertTrue(
          app.server.getPackRepository().getSelectedIds().contains("mod/ffa:spawn_pack"),
          "Optional void datapack is selected");
      var path =
          net.neoforged.fml.ModList.get()
              .getModFileById("ffa")
              .getFile()
              .findResource("spawn_pack/data/ffa/dimension/spawn.json");
      var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path));
      var stem =
          net.minecraft.world.level.dimension.LevelStem.CODEC
              .parse(
                  net.minecraft.resources.RegistryOps.create(
                      com.mojang.serialization.JsonOps.INSTANCE, h.getLevel().registryAccess()),
                  json)
              .getOrThrow();
      h.assertTrue(
          stem.generator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource,
          "Void definition decodes with real server registries");
      h.assertTrue(
          ((net.minecraft.world.level.levelgen.FlatLevelSource) stem.generator())
              .settings()
              .getLayersInfo()
              .isEmpty(),
          "Void generator contains no terrain layers");
    } else
      h.assertTrue(
          (dimension != null) == app.settings.dimension,
          "Void dimension registration follows restart configuration");
    if (dimension != null) {
      dimension.getChunk(0, 0);
      h.assertTrue(
          dimension.getBlockState(new BlockPos(0, 64, 0)).isAir(),
          "Void dimension generates no platform");
      for (var f :
          List.of(
              Flag.HUNGER,
              Flag.DAMAGE,
              Flag.PVP,
              Flag.INTERACT,
              Flag.MOB_GRIEFING,
              Flag.MOB_SPAWNING,
              Flag.FIRE,
              Flag.EXPLOSIONS,
              Flag.FLIGHT))
        h.assertFalse(
            app.regions.allowed(dimension, new BlockPos(10000, 64, 10000), f, app.settings),
            "Dimension-wide default " + f);
    }
    ServerPlayer other = player(h, "ffaOther");
    try {
      other.setPos(h.absoluteVec(new Vec3(14.5, 1, 14.5)));
      app.protection.update(other);
      String inventory = p.getInventory().save(new ListTag()).toString();
      int xp = p.totalExperience;
      String safe = app.teleports.validate(p, Teleports.point(other));
      h.assertTrue(
          safe == null,
          "TPA arrival safety: "
              + safe
              + " pos="
              + other.position()
              + " bbox="
              + p.getBoundingBox()
              + " foot="
              + h.getLevel().getBlockState(other.blockPosition())
              + " head="
              + h.getLevel().getBlockState(other.blockPosition().above())
              + " floor="
              + h.getLevel().getBlockState(other.blockPosition().below()));
      app.teleports.request(p, other, false);
      boolean stale = false;
      try {
        app.teleports.answer(other, true, UUID.randomUUID());
      } catch (IllegalArgumentException expected) {
        stale = true;
      }
      h.assertTrue(
          stale && app.teleports.requests.containsKey(other.getUUID()),
          "A stale clickable message cannot consume or accept the current request");
      UUID token = app.teleports.requests.get(other.getUUID()).token();
      app.teleports.answer(other, true, token);
      h.assertTrue(
          p.position().distanceToSqr(other.position()) < .01,
          "Accepted request moves the requesting player");
      h.assertTrue(
          inventory.equals(p.getInventory().save(new ListTag()).toString())
              && xp == p.totalExperience,
          "TPA preserves inventory and XP");
      boolean duplicate = false;
      try {
        app.teleports.answer(other, true);
      } catch (IllegalArgumentException expected) {
        duplicate = true;
      }
      h.assertTrue(duplicate, "A teleport request cannot be accepted twice");
      app.teleports.requests.put(
          other.getUUID(),
          new Teleports.Request(p.getUUID(), other.getUUID(), false, Teleports.now() - 1));
      boolean expired = false;
      try {
        app.teleports.answer(other, true);
      } catch (IllegalArgumentException expected) {
        expired = true;
      }
      h.assertTrue(expired, "Expired request cannot be accepted");
      app.storage.update(st -> st.players.get(other.getUUID().toString()).balance = 10);
      long a = app.storage.player(p.getUUID()).balance,
          b = app.storage.player(other.getUUID()).balance;
      app.economy.pay(p, other, 2);
      h.assertTrue(
          app.storage.player(p.getUUID()).balance == a - 2
              && app.storage.player(other.getUUID()).balance == b + 2,
          "Transfer conserves whole currency in one persisted state");
      var denied = new Access();
      denied.mode = "group";
      denied.value = "missing_rank";
      h.assertFalse(
          app.compat.access(p, denied, p.blockPosition()), "Unavailable rank access fails closed");
      var portal = new Model.Portal();
      portal.id = "test_portal";
      portal.dimension = p.level().dimension().location().toString();
      portal.minX = portal.maxX = p.blockPosition().getX();
      portal.minY = p.blockPosition().getY();
      portal.maxY = portal.minY + 1;
      portal.minZ = portal.maxZ = p.blockPosition().getZ();
      portal.warp = "test_warp";
      Warp warp = new Warp();
      warp.point = Teleports.point(other);
      app.storage.update(
          st -> {
            st.portals.put(portal.id, portal);
            st.warps.put(portal.warp, warp);
          });
      Portals.reindex(app);
      for (DyeColor color : DyeColor.values()) {
        app.storage.update(st -> st.portals.get(portal.id).color = color.getName());
        Portals.visualize(app, app.storage.state().portals.get(portal.id));
        h.assertTrue(
            new Storage(app.storage.directory)
                .state()
                .portals
                .get(portal.id)
                .color
                .equals(color.getName()),
            "Portal dye persists " + color);
      }
      h.assertTrue(
          app.storage.state().portals.get(portal.id).warp.equals("test_warp"),
          "Recoloring preserves warp link");
      app.storage.update(st -> st.portals.get(portal.id).access.mode = "admin");
      app.teleports.portalCooldowns.remove(p.getUUID());
      Vec3 previous = p.position();
      Portals.check(app, p);
      h.assertTrue(p.position().equals(previous), "Denied portal access leaves player in place");
      Portals.removeVisuals(app, portal);
      app.storage.update(
          st -> {
            st.portals.remove(portal.id);
            st.warps.remove(portal.warp);
          });
      Portals.reindex(app);
      if (net.neoforged.fml.ModList.get().isLoaded("easy_npc")) {
        var type =
            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.stream()
                .filter(
                    t ->
                        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                            .getKey(t)
                            .getNamespace()
                            .equals("easy_npc"))
                .findFirst()
                .orElseThrow();
        var entity = type.create(h.getLevel());
        h.assertTrue(entity != null && app.compat.npc(entity), "Exact Easy NPC entity recognized");
        if (entity instanceof Mob mob) {
          entity.setPos(h.absoluteVec(new Vec3(3.5, 1, 3.5)));
          var event =
              new MobSpawnEvent.PositionCheck(mob, h.getLevel(), MobSpawnType.COMMAND, null);
          NeoForge.EVENT_BUS.post(event);
          h.assertTrue(
              event.getResult() != MobSpawnEvent.PositionCheck.Result.FAIL,
              "NPC creation remains allowed inside spawn");
        }
      }
    } finally {
      app.server.getPlayerList().remove(other);
    }
  }

  private static void mercenaries(GameTestHelper h, ServerPlayer player, Administration app)
      throws Exception {
    Class<?> cls = Class.forName("dev.villagerscontract.VillageBells"),
        village = Class.forName("dev.villagerscontract.VillageBells$Village");
    var get = cls.getMethod("get", ServerLevel.class);
    Object original = get.invoke(null, h.getLevel());
    Object test =
        cls.getMethod(
                "load",
                net.minecraft.nbt.CompoundTag.class,
                net.minecraft.core.HolderLookup.Provider.class)
            .invoke(null, new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess());
    h.getLevel()
        .getDataStorage()
        .set("ffm_village_bells", (net.minecraft.world.level.saveddata.SavedData) test);
    try {
      BlockPos bell = h.absolutePos(new BlockPos(3, 1, 3));
      h.getLevel().setBlock(bell, Blocks.BELL.defaultBlockState(), 3);
      var placed = cls.getDeclaredMethod("placed", BlockPos.class);
      placed.setAccessible(true);
      Object town = placed.invoke(test, bell);
      var claim = cls.getDeclaredMethod("claim", village, BlockPos.class, UUID.class, String.class);
      claim.setAccessible(true);
      claim.invoke(test, town, bell, player.getUUID(), "Integration Town");
      h.assertTrue(
          app.compat.townMember(player, bell, ""), "Mercenaries owner membership recognized");
      Region candidate = app.regions.copy().getFirst();
      candidate.settlementOverride = false;
      boolean rejected = false;
      try {
        Regions.validate(List.of(candidate), app.server, app.settings, app.compat);
      } catch (IllegalArgumentException expected) {
        rejected = expected.getMessage().contains("settlement");
      }
      h.assertTrue(rejected, "Settlement overlap rejected without override");
      candidate.settlementOverride = true;
      Regions.validate(List.of(candidate), app.server, app.settings, app.compat);
      h.assertTrue(
          app.compat.inSettlement(h.getLevel(), bell),
          "RTP recognizes settlement regardless of membership");
    } finally {
      h.getLevel()
          .getDataStorage()
          .set("ffm_village_bells", (net.minecraft.world.level.saveddata.SavedData) original);
    }
  }
}
