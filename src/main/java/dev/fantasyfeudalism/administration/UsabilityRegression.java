package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;
import static dev.fantasyfeudalism.administration.RegressionTests.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

final class UsabilityRegression {
  private static int cmd(Administration app, String command, CommandSourceStack source)
      throws Exception {
    try {
      return app.server.getCommands().getDispatcher().execute(command, source);
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
      return 0;
    }
  }

  private static String messages(ServerPlayer p) {
    return outgoing(p).stream()
        .filter(packet -> packet instanceof ClientboundSystemChatPacket)
        .map(packet -> ((ClientboundSystemChatPacket) packet).content().getString())
        .collect(java.util.stream.Collectors.joining("\n"));
  }

  static void verify(GameTestHelper h, ServerPlayer p, Administration app) throws Exception {
    var state = app.storage.copy();
    var regions = app.regions.copy();
    var inventory = p.getInventory().save(new ListTag());
    var originalModeration = app.moderation;
    var originalSigns = app.signShops;
    boolean standalone = app.settings.standalone, verbose = app.settings.verbose;
    int cooldown = app.settings.cooldown, warmup = app.settings.warmup;
    var admin = player(h, "ffaAdmin");
    var second = player(h, "ffaInmate");
    var returner = player(h, "ffaReturn");
    UUID returnId = returner.getUUID();
    var clock = new AtomicLong(1000000);
    var signsClock = new AtomicLong(1000);
    NeoForge.EVENT_BUS.unregister(originalModeration);
    app.moderation = new Moderation(app, clock::get);
    NeoForge.EVENT_BUS.register(app.moderation);
    app.signShops = new SignShops(app, signsClock::get);
    app.server
        .getPlayerList()
        .getOps()
        .add(new net.minecraft.server.players.ServerOpListEntry(admin.getGameProfile(), 4, false));
    try {
      app.settings.cooldown = 0;
      app.settings.warmup = 0;
      app.settings.verbose = true;
      app.teleports.cooldowns.clear();
      app.storage.update(
          s -> {
            s.players.get(p.getUUID().toString()).homes.clear();
            s.spawn =
                new Point(
                    h.getLevel().dimension().location().toString(),
                    h.absoluteVec(new Vec3(14.5, 1, 14.5)).x,
                    h.absoluteVec(new Vec3(14.5, 1, 14.5)).y,
                    h.absoluteVec(new Vec3(14.5, 1, 14.5)).z,
                    0,
                    0);
          });
      p.setPos(h.absoluteVec(new Vec3(13.5, 1, 13.5)));
      admin.setPos(h.absoluteVec(new Vec3(14.5, 1, 13.5)));
      app.protection.update(p);
      var ordinary = p.createCommandSourceStack();
      var staff = admin.createCommandSourceStack();
      outgoing(p);
      h.assertTrue(
          cmd(app, "ffa sethome Hill", ordinary) == 1 && messages(p).equals("Home set."),
          "Home feedback is one readable sentence even with legacy verbose setting enabled");
      h.assertTrue(
          cmd(app, "ffa sethome HILL", ordinary) == 1
              && app.storage.player(p.getUUID()).homes.size() == 1,
          "Case-only home updates do not create duplicates");
      h.assertTrue(cmd(app, "ffa home hill", ordinary) == 1, "Homes accept mixed-case lookup");
      outgoing(admin);
      h.assertTrue(
          cmd(app, "ffa balance set ffaInmate 250", staff) == 1
              && app.storage.player(second.getUUID()).balance == 250
              && messages(admin).equals("Balance set."),
          "New balance syntax is private, readable and persisted");
      h.assertTrue(
          cmd(app, "ffa balance set ffaInmate 999", ordinary) == 0
              && app.storage.player(second.getUUID()).balance == 250,
          "Ordinary users cannot set balances");
      regions(h, p, admin, app);
      signs(h, p, admin, app, signsClock);
      // The newest successful teleport replaces older history; rejected movement leaves it alone.
      returner.setPos(h.absoluteVec(new Vec3(12.5, 1, 12.5)));
      var start = Teleports.point(returner);
      var destination = h.absoluteVec(new Vec3(13.5, 1, 12.5));
      returner.teleportTo(h.getLevel(), destination.x, destination.y, destination.z, 0, 0);
      h.assertFalse(
          BackHistory.different(start, app.storage.player(returnId).back),
          "Vanilla teleport records exactly its original location");
      var death = h.absoluteVec(new Vec3(14.5, 1, 12.5));
      returner.setPos(death);
      returner.kill();
      h.assertTrue(
          returner.isDeadOrDying() && Math.abs(app.storage.player(returnId).back.x - death.x) < .01,
          "A real death replaces older teleport history");
      returner =
          app.server
              .getPlayerList()
              .respawn(returner, false, net.minecraft.world.entity.Entity.RemovalReason.KILLED);
      returner.connection.player = returner; // Vanilla respawn packet handler normally does this.
      h.assertTrue(
          Math.abs(app.storage.player(returnId).back.x - death.x) < .01,
          "Respawn does not overwrite the death return location");
      int backResult = cmd(app, "ffa back", returner.createCommandSourceStack());
      h.assertTrue(
          backResult == 1 && returner.position().distanceToSqr(death) < .01,
          "Back is enabled and returns to the most recent death: " + messages(returner));
      var last = Teleports.point(returner);
      returner.teleportTo(h.getLevel(), start.x, start.y, start.z, 0, 0);
      h.assertFalse(
          BackHistory.different(last, app.storage.player(returnId).back),
          "A newer teleport origin supersedes the death");
      var savedBack = app.storage.player(returnId).back;
      BlockPos obstruction = h.absolutePos(new BlockPos(11, 1, 11));
      h.getLevel().setBlock(obstruction, Blocks.STONE.defaultBlockState(), 3);
      returner.teleportTo(
          h.getLevel(), obstruction.getX() + .5, obstruction.getY(), obstruction.getZ() + .5, 0, 0);
      h.assertFalse(
          BackHistory.different(savedBack, app.storage.player(returnId).back),
          "Rejected teleports do not replace return history");
      h.getLevel().setBlock(obstruction, Blocks.AIR.defaultBlockState(), 3);
      jail(h, p, second, admin, app, clock);
      portals(h, p, admin, app);
      powers(h, second, admin, app);
      var defaults = complete(app, staff, "ffa ranks node add member ");
      h.assertTrue(
          defaults.contains("ffa.command.spawn") && !defaults.contains("command.time"),
          "Rank node suggestions start with curated FFA permissions");
      h.assertTrue(
          complete(app, staff, "ffa ranks node add member command.t").contains("command.time"),
          "Other command permissions are discoverable by explicit prefix");
      h.assertTrue(
          cmd(app, "ffa ranks nodes player", staff) == 1,
          "Rank node categories have readable help");
    } finally {
      app.regionVisuals.stop(admin);
      app.settings.standalone = standalone;
      app.settings.verbose = verbose;
      app.settings.cooldown = cooldown;
      app.settings.warmup = warmup;
      for (ServerPlayer actor : List.of(admin, second)) {
        app.server.getPlayerList().deop(actor.getGameProfile());
        app.server.getPlayerList().remove(actor);
      }
      var current = app.server.getPlayerList().getPlayer(returnId);
      if (current != null) app.server.getPlayerList().remove(current);
      NeoForge.EVENT_BUS.unregister(app.moderation);
      app.moderation = originalModeration;
      NeoForge.EVENT_BUS.register(originalModeration);
      app.signShops = originalSigns;
      app.regions.edit(regions, app.server, app.settings, app.compat);
      app.storage.commit(state);
      p.getInventory().load(inventory);
      p.setPos(h.absoluteVec(new Vec3(12.5, 1, 12.5)));
      app.protection.update(p);
    }
  }

  private static void select(
      GameTestHelper h, Administration app, ServerPlayer admin, BlockPos first, BlockPos second)
      throws Exception {
    admin.setPos(h.absoluteVec(new Vec3(first.getX() + .5, first.getY(), first.getZ() + .5)));
    cmd(app, "ffa pos1", admin.createCommandSourceStack());
    admin.setPos(h.absoluteVec(new Vec3(second.getX() + .5, second.getY(), second.getZ() + .5)));
    cmd(app, "ffa pos2", admin.createCommandSourceStack());
  }

  private static void regions(
      GameTestHelper h, ServerPlayer p, ServerPlayer admin, Administration app) throws Exception {
    var staff = admin.createCommandSourceStack();
    select(h, app, admin, new BlockPos(3, 1, 3), new BlockPos(6, 1, 6));
    h.assertTrue(
        cmd(app, "ffa region create child Market", staff) == 1
            && app.regions.get("mARKET").kind == Kind.CHILD,
        "Explicit child creation preserves uppercase name and supports case-insensitive lookup");
    for (String flag : List.of("build", "break", "hunger", "damage", "pvp", "flight"))
      h.assertTrue(
          cmd(app, "ffa region flag market " + flag + " true", staff) == 1,
          "Child flag persists: " + flag);
    p.setPos(h.absoluteVec(new Vec3(2.5, 1, 4.5)));
    app.protection.update(p);
    h.assertTrue(
        p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
        "Parent spawn retains protected Adventure mode");
    outgoing(p);
    p.setPos(h.absoluteVec(new Vec3(4.5, 1, 4.5)));
    app.protection.update(p);
    h.assertTrue(
        p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL && p.getAbilities().mayfly,
        "Child construction/flight overrides activate on entry without parent-mode interference");
    h.assertFalse(
        app.protection.denied(p, h.getLevel(), p.blockPosition(), Flag.BUILD),
        "Child building overrides parent denial");
    var left =
        new PlayerInteractEvent.LeftClickBlock(
            p, p.blockPosition(), Direction.UP, PlayerInteractEvent.LeftClickBlock.Action.START);
    NeoForge.EVENT_BUS.post(left);
    h.assertFalse(
        left.isCanceled(),
        "Parent interaction denial does not swallow an explicitly allowed child break");
    var titlePackets = outgoing(p);
    h.assertTrue(
        titlePackets.stream()
            .anyMatch(
                packet ->
                    packet instanceof ClientboundSetSubtitleTextPacket title
                        && title.text().getString().startsWith("Entering Market")),
        "Region entry uses the smaller centered subtitle");
    h.assertFalse(
        titlePackets.stream()
            .anyMatch(
                packet ->
                    packet instanceof ClientboundSystemChatPacket chat
                        && chat.content().getString().contains("Entering")),
        "Region entry is no longer printed in chat");
    cmd(app, "ffa region message Market entry \"Arriving {name}\"", staff);
    cmd(app, "ffa region title Market entry color gold", staff);
    cmd(app, "ffa region title Market entry bold true", staff);
    cmd(app, "ffa region title Market entry italic true", staff);
    p.setPos(h.absoluteVec(new Vec3(2.5, 1, 4.5)));
    app.protection.update(p);
    outgoing(p);
    p.setPos(h.absoluteVec(new Vec3(4.5, 1, 4.5)));
    app.protection.update(p);
    h.assertTrue(
        outgoing(p).stream()
            .anyMatch(
                packet ->
                    packet instanceof ClientboundSetSubtitleTextPacket t
                        && t.text().getString().startsWith("Arriving Market")
                        && t.text().getStyle().isBold()
                        && t.text().getStyle().isItalic()),
        "Custom region title text/color/bold/italic survive configuration and render");
    outgoing(p);
    var market = app.regions.get("Market");
    RegionTitles.show(p, null, market);
    var entryOnly = outgoing(p);
    h.assertTrue(
        entryOnly.stream()
            .anyMatch(
                packet ->
                    packet instanceof ClientboundSetTitleTextPacket title
                        && title.text().getString().isEmpty()),
        "Region notice clears the old large title while starting its timer");
    h.assertTrue(
        entryOnly.stream()
            .anyMatch(
                packet ->
                    packet instanceof ClientboundSetSubtitleTextPacket title
                        && title.text().getString().equals("Arriving Market")
                        && title.text().getStyle().isBold()
                        && title.text().getStyle().isItalic()
                        && title.text().getStyle().getColor().getValue() == 0xFFAA00),
        "Entry-only notice keeps configured text, color and formatting at subtitle size");
    RegionTitles.show(p, market, null);
    h.assertTrue(
        outgoing(p).stream()
            .anyMatch(
                packet ->
                    packet instanceof ClientboundSetSubtitleTextPacket title
                        && title.text().getString().equals("Leaving Market")),
        "Exit-only notice also uses subtitle size");
    RegionTitles.show(p, app.regions.get("test_spawn"), market);
    h.assertTrue(
        outgoing(p).stream()
            .anyMatch(
                packet ->
                    packet instanceof ClientboundSetSubtitleTextPacket title
                        && title.text().getString().equals("Arriving Market · Leaving test_spawn")
                        && title.text().getSiblings().getLast().getStyle().getColor().getValue()
                            == 0xFFFF55
                        && !title.text().getSiblings().getLast().getStyle().isBold()),
        "Direct parent/child transitions retain both messages and independent styles in the smaller"
            + " line");
    var silent = new Region();
    silent.entry = "";
    silent.exit = "";
    RegionTitles.show(p, silent, silent);
    h.assertTrue(outgoing(p).isEmpty(), "Disabled region messages send no title packets");

    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD, 2));
    var blocked = new PlayerInteractEvent.RightClickItem(p, InteractionHand.MAIN_HAND);
    NeoForge.EVENT_BUS.post(blocked);
    h.assertTrue(blocked.isCanceled(), "Usable items default to denied inside a protected child");
    cmd(app, "ffa region item Market minecraft:bread allow", staff);
    var allowed = new PlayerInteractEvent.RightClickItem(p, InteractionHand.MAIN_HAND);
    NeoForge.EVENT_BUS.post(allowed);
    h.assertFalse(allowed.isCanceled(), "An explicitly allowed item can be used in its region");
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 2));
    BlockPos floor = h.absolutePos(new BlockPos(3, 0, 3));
    var use =
        new UseOnContext(
            p,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false));
    h.assertTrue(
        p.getItemInHand(InteractionHand.MAIN_HAND).useOn(use).consumesAction()
            && h.getLevel().getBlockState(floor.above()).is(Blocks.STONE),
        "Allowed child building works through the real block-item placement path");
    h.getLevel().setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
    p.setPos(h.absoluteVec(new Vec3(2.5, 1, 4.5)));
    app.protection.update(p);
    h.assertTrue(
        p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE && !p.getAbilities().mayfly,
        "Leaving child restores parent mode and flight rules");
    h.assertTrue(
        cmd(app, "ffa region visualize Market", staff) == 1 && app.regionVisuals.active(admin),
        "Visualization remains active after command returns");
    outgoing(admin);
    app.regionVisuals.refresh();
    h.assertTrue(
        outgoing(admin).stream()
            .anyMatch(packet -> packet instanceof ClientboundLevelParticlesPacket),
        "Persistent visualization redraws through its production refresh loop");
    cmd(app, "ffa region visualize cancel", staff);
    h.assertFalse(app.regionVisuals.active(admin), "Explicit cancellation stops visualization");
    app.settings.standalone = true;
    select(h, app, admin, new BlockPos(10, 1, 1), new BlockPos(12, 1, 3));
    h.assertTrue(
        cmd(app, "ffa region create standalone Outpost", staff) == 1
            && app.regions.get("OUTPOST").kind == Kind.STANDALONE,
        "Explicit standalone creation is available and independent of parent");
    cmd(app, "ffa region delete Outpost", staff);
    cmd(app, "ffa region delete Market", staff);
  }

  private static void signs(
      GameTestHelper h, ServerPlayer p, ServerPlayer admin, Administration app, AtomicLong clock)
      throws Exception {
    cmd(app, "ffa shop set apples minecraft:apple 5 2", admin.createCommandSourceStack());
    BlockPos at = h.absolutePos(new BlockPos(2, 1, 5));
    h.getLevel().setBlock(at, Blocks.OAK_SIGN.defaultBlockState(), 3);
    admin.setPos(h.absoluteVec(new Vec3(2.5, 1, 3.5)));
    admin.lookAt(
        net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(at));
    outgoing(admin);
    int linked = cmd(app, "ffa shop sign bind apples 2", admin.createCommandSourceStack());
    h.assertTrue(
        linked == 1 && app.signShops.linked(h.getLevel(), at),
        "Administrator links an actual sign to a shop offering: " + messages(admin));
    p.setPos(h.absoluteVec(new Vec3(2.5, 1, 4.5)));
    app.protection.update(p);
    p.getInventory().setItem(0, new ItemStack(Items.APPLE, 8));
    p.getInventory().selected = 0;
    app.storage.update(s -> s.players.get(p.getUUID().toString()).balance = 100);
    var buy =
        new PlayerInteractEvent.RightClickBlock(
            p,
            InteractionHand.MAIN_HAND,
            at,
            new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, false));
    NeoForge.EVENT_BUS.post(buy);
    h.assertTrue(
        app.storage.player(p.getUUID()).balance == 90
            && p.getInventory().countItem(Items.APPLE) == 10,
        "Right click buys through the real journal despite default spawn interaction denial");
    clock.addAndGet(301);
    var sell =
        new PlayerInteractEvent.LeftClickBlock(
            p, at, Direction.NORTH, PlayerInteractEvent.LeftClickBlock.Action.START);
    NeoForge.EVENT_BUS.post(sell);
    h.assertTrue(
        sell.isCanceled()
            && app.storage.player(p.getUUID()).balance == 94
            && p.getInventory().countItem(Items.APPLE) == 8,
        "Left click sells while protecting the linked sign from breaking");
    clock.addAndGet(301);
    NeoForge.EVENT_BUS.post(
        new PlayerInteractEvent.LeftClickBlock(
            p, at, Direction.NORTH, PlayerInteractEvent.LeftClickBlock.Action.CLIENT_HOLD));
    h.assertTrue(
        app.storage.player(p.getUUID()).balance == 94, "Holding attack does not repeat sign sales");
    h.assertTrue(
        cmd(app, "ffa shop sign bind apples", p.createCommandSourceStack()) == 0,
        "Ordinary players cannot create shop signs");
    cmd(app, "ffa shop set apples minecraft:apple 7 3", admin.createCommandSourceStack());
    h.assertTrue(
        ((SignBlockEntity) h.getLevel().getBlockEntity(at))
            .getFrontText()
            .getMessage(1, false)
            .getString()
            .contains("14"),
        "Sign labels update with shop prices");
    h.assertTrue(
        new Storage(app.storage.directory).state().shopSigns.size() == 1,
        "Sign links persist with FFA state");
    cmd(app, "ffa shop sign remove", admin.createCommandSourceStack());
    h.assertFalse(app.signShops.linked(h.getLevel(), at), "Admin unlink removes the live link");
    h.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), 3);
  }

  private static void jail(
      GameTestHelper h,
      ServerPlayer p,
      ServerPlayer second,
      ServerPlayer admin,
      Administration app,
      AtomicLong clock)
      throws Exception {
    for (int i = 0; i < 2; i++) {
      int x = 1 + i * 4;
      select(h, app, admin, new BlockPos(x, 1, 1), new BlockPos(x + 2, 3, 3));
      admin.setPos(h.absoluteVec(new Vec3(x + 1.5, 1, 2.5)));
      h.assertTrue(
          cmd(app, "ffa jail cell set Cell" + i + " override", admin.createCommandSourceStack())
              == 1,
          "Jail cell geometry and arrival are saved");
    }
    p.setPos(h.absoluteVec(new Vec3(13.5, 1, 13.5)));
    second.setPos(h.absoluteVec(new Vec3(12.5, 1, 13.5)));
    app.protection.update(p);
    app.protection.update(second);
    h.assertTrue(
        cmd(app, "ffa punish jail ffaPlayer 5s Testing", admin.createCommandSourceStack()) == 1
            && app.moderation.confined(p),
        "Shared punishment command immediately jails an online player");
    String first = app.storage.player(p.getUUID()).punishments.get("jail").cell;
    h.assertTrue(
        cmd(app, "ffa punish jail ffaInmate 10s", admin.createCommandSourceStack()) == 1
            && !first.equals(app.storage.player(second.getUUID()).punishments.get("jail").cell),
        "A second prisoner receives another free cell");
    var position = p.position();
    p.teleportTo(h.getLevel(), position.x + 20, position.y, position.z, 0, 0);
    h.assertTrue(p.position().equals(position), "External teleport APIs cannot escape jail");
    h.assertTrue(
        p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE
            && app.protection.denied(p, h.getLevel(), p.blockPosition(), Flag.INTERACT),
        "Jail enforces Adventure and protected interactions");
    p.setPos(h.absoluteVec(new Vec3(14.5, 1, 10.5)));
    app.moderation.enforce(p);
    h.assertTrue(
        app.storage.state().jailCells.get(first).contains(Teleports.point(p)),
        "Movement escaping a cell is corrected");
    cmd(app, "ffa punish chatban ffaPlayer 5s", admin.createCommandSourceStack());
    h.assertTrue(
        cmd(app, "ffa whisper ffaInmate blocked", p.createCommandSourceStack()) == 0,
        "Chat bans cover private whispers");
    app.server
        .getCommands()
        .performPrefixedCommand(p.createCommandSourceStack(), "ffa sethome Escape");
    h.assertFalse(
        app.storage.player(p.getUUID()).homes.containsKey("Escape"),
        "The real command-event path prevents jailed command escapes");
    p.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
    p.getInventory().selected = 0;
    p.connection.handlePlayerAction(
        new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS, BlockPos.ZERO, Direction.DOWN));
    h.assertTrue(
        p.getInventory().getItem(0).getCount() == 3,
        "Jailed inventory drop packets preserve items");
    clock.addAndGet(6000);
    app.moderation.enforce(p);
    h.assertFalse(
        app.moderation.confined(p), "Real elapsed time expires jail and releases the player");
    h.assertFalse(
        BackHistory.different(Teleports.point(p), app.storage.state().spawn),
        "Release always goes to configured spawn");
    h.assertFalse(
        app.moderation.banned(p, "chatban"), "Chat ban time counts down with the shared clock");
    cmd(app, "ffa punish clear jail ffaInmate", admin.createCommandSourceStack());
    cmd(app, "ffa setwarp restricted", admin.createCommandSourceStack());
    cmd(app, "ffa punish warpban ffaPlayer 5s", admin.createCommandSourceStack());
    h.assertTrue(
        cmd(app, "ffa warp restricted", p.createCommandSourceStack()) == 0,
        "Warp bans block named warps");
    h.assertTrue(
        cmd(app, "ffa sethome StillAllowed", p.createCommandSourceStack()) == 1,
        "Named-warp bans do not disable unrelated home creation");
    h.assertTrue(
        cmd(app, "ffa punish jail ffaAdmin 5s", admin.createCommandSourceStack()) == 0,
        "Administrators retain their punishment exemption");
    cmd(app, "ffa punish clear warpban ffaPlayer", admin.createCommandSourceStack());
    UUID offlineId = UUID.randomUUID();
    app.storage.ensure(offlineId, app.settings);
    app.moderation.apply(offlineId, "chatban", 5, "Offline timing");
    h.assertTrue(
        new Storage(app.storage.directory).player(offlineId).punishments.get("chatban").until
            == clock.get() + 5000,
        "Offline ban deadline persists as real time");
    clock.addAndGet(6000);
    var rejoined = player(h, new com.mojang.authlib.GameProfile(offlineId, "ffaOffline"));
    try {
      h.assertFalse(
          app.moderation.banned(rejoined, "chatban"),
          "Time spent offline counts toward punishment expiry before login");
    } finally {
      app.server.getPlayerList().remove(rejoined);
    }
  }

  private static void portals(
      GameTestHelper h, ServerPlayer p, ServerPlayer admin, Administration app) throws Exception {
    BlockPos at = h.absolutePos(new BlockPos(10, 1, 10));
    PortalCreation.enter(false);
    try {
      h.assertFalse(
          h.getLevel().setBlock(at, Blocks.NETHER_PORTAL.defaultBlockState(), 3),
          "Nonadmin context cannot create a tagged portal block");
    } finally {
      PortalCreation.leave();
    }
    PortalCreation.enter(true);
    try {
      h.assertTrue(
          h.getLevel().setBlock(at, Blocks.END_PORTAL.defaultBlockState(), 3),
          "Admin context can create portals");
    } finally {
      PortalCreation.leave();
    }
    h.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), 3);
    boolean original = app.settings.adminPortalCreationOnly;
    app.settings.adminPortalCreationOnly = false;
    PortalCreation.enter(false);
    try {
      h.assertTrue(
          h.getLevel().setBlock(at, Blocks.END_PORTAL.defaultBlockState(), 3),
          "Portal creation restriction is configurable");
    } finally {
      PortalCreation.leave();
      app.settings.adminPortalCreationOnly = original;
      h.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), 3);
    }
  }

  private static void powers(
      GameTestHelper h, ServerPlayer victim, ServerPlayer admin, Administration app)
      throws Exception {
    admin.setGameMode(GameType.SURVIVAL);
    h.assertTrue(
        cmd(app, "ffa god", admin.createCommandSourceStack()) == 1 && app.adminPowers.god(admin),
        "God mode toggles on for administrators");
    float health = admin.getHealth();
    admin.hurt(admin.damageSources().genericKill(), 100);
    h.assertTrue(
        admin.isAlive() && admin.getHealth() == health,
        "God mode prevents even invulnerability-bypassing damage");
    h.assertTrue(
        cmd(app, "ffa god", victim.createCommandSourceStack()) == 0,
        "Ordinary players cannot enable god mode");
    victim.setPos(h.absoluteVec(new Vec3(2.5, 1, 5.5)));
    app.protection.update(victim);
    h.assertTrue(
        cmd(app, "ffa smite ffaInmate", admin.createCommandSourceStack()) == 1
            && victim.isDeadOrDying(),
        "Smite strikes and kills despite protected-spawn damage rules");
    h.assertTrue(
        cmd(app, "ffa smite ffaAdmin", admin.createCommandSourceStack()) == 1
            && admin.isDeadOrDying(),
        "Explicit smite overrides FFA god mode");
    cmd(app, "ffa god", admin.createCommandSourceStack());
    h.assertFalse(app.adminPowers.god(admin), "God mode can be turned off");
  }
}
