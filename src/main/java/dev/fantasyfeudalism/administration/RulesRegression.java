package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;
import static dev.fantasyfeudalism.administration.RegressionTests.*;

import java.util.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;

final class RulesRegression {
  static void verify(GameTestHelper h, Administration app) throws Exception {
    State state = app.storage.copy();
    var regions = app.regions.copy();
    var admin = player(h, "rulesAdmin");
    var existing = player(h, "rulesExisting");
    var ordinary = player(h, "rulesOther");
    net.minecraft.server.level.ServerPlayer newcomer = null;
    try {
      app.server
          .getPlayerList()
          .getOps()
          .add(
              new net.minecraft.server.players.ServerOpListEntry(admin.getGameProfile(), 4, false));
      Region parent = region(h, "rulesSpawn", Kind.SPAWN, 0, 15);
      Region room = region(h, "RulesRoom", Kind.CHILD, 1, 6);
      room.parent = parent.id;
      app.regions.edit(List.of(parent, room), app.server, app.settings, app.compat);
      admin.setPos(h.absoluteVec(new Vec3(3.5, 1, 3.5)));
      app.server
          .getCommands()
          .performPrefixedCommand(admin.createCommandSourceStack(), "ffa setfirstspawn");
      app.server
          .getCommands()
          .performPrefixedCommand(admin.createCommandSourceStack(), "ffa rules region RulesRoom");
      Point spawn = app.storage.state().firstSpawn;
      existing.setPos(h.absoluteVec(new Vec3(12.5, 1, 12.5)));
      h.assertTrue(app.storage.player(existing.getUUID()).joined, "Fixture is an existing player");
      app.rules.enable(true);
      h.assertTrue(
          app.rules.pending(existing) && !BackHistory.different(spawn, Teleports.point(existing)),
          "Enabling enrolls and transfers existing players");
      h.assertFalse(app.rules.pending(admin), "Administrators are fully exempt");
      h.assertTrue(
          outgoing(existing).stream()
              .anyMatch(
                  p ->
                      p instanceof ClientboundSystemChatPacket c
                          && c.content().getString().contains("[ ACCEPT ]")),
          "Prominent accept/deny chat buttons are delivered despite chat filtering");
      for (String command :
          List.of(
              "ffa sethome Escape",
              "ffa pvp off",
              "msg rulesOther bypass",
              "help",
              "execute run say bypass")) {
        app.server
            .getCommands()
            .performPrefixedCommand(existing.createCommandSourceStack().withPermission(4), command);
      }
      h.assertTrue(
          app.storage.player(existing.getUUID()).homes.isEmpty() && app.combat.enabled(existing),
          "All commands, including privileged or unrelated commands, are blocked before"
              + " acceptance");
      h.assertTrue(
          !app.rules.commandAllowed(existing, "ffa rules accept extra extra")
              && app.rules.commandAllowed(existing, "ffa rules accept"),
          "Only exact consent command paths are allowed");
      outgoing(existing);
      existing.sendSystemMessage(Component.literal("server chatter"));
      var type = ChatType.bind(ChatType.CHAT, ordinary);
      existing.connection.sendPlayerChatMessage(
          PlayerChatMessage.unsigned(ordinary.getUUID(), "normal chatter"), type);
      existing.connection.sendDisguisedChatMessage(Component.literal("disguised chatter"), type);
      h.assertTrue(
          outgoing(existing).isEmpty(),
          "System, signed-player and disguised chat from ordinary sources are hidden");
      existing.connection.sendPlayerChatMessage(
          PlayerChatMessage.unsigned(admin.getUUID(), "admin chatter"),
          ChatType.bind(ChatType.CHAT, admin));
      h.assertTrue(
          outgoing(existing).stream().anyMatch(p -> p instanceof ClientboundPlayerChatPacket),
          "Administrator chat is visible");
      try (var ignored = Rules.from(admin)) {
        existing.sendSystemMessage(Component.literal("staff notice"));
      }
      h.assertTrue(
          outgoing(existing).stream().anyMatch(p -> p instanceof ClientboundSystemChatPacket),
          "Administrator command/whisper system messages are visible");
      existing.sendSystemMessage(Component.literal("untrusted after staff"));
      h.assertTrue(
          outgoing(existing).isEmpty(), "Staff delivery scope cannot leak into later broadcasts");
      var event = new ServerChatEvent(existing, "blocked", Component.literal("blocked"));
      NeoForge.EVENT_BUS.post(event);
      h.assertTrue(event.isCanceled(), "Outgoing player chat is blocked before acceptance");
      existing.setPos(h.absoluteVec(new Vec3(12.5, 1, 12.5)));
      app.rules.enforce(existing);
      h.assertTrue(
          !BackHistory.different(spawn, Teleports.point(existing)),
          "Walking out returns to exact first-join spawn");
      existing.setPos(h.absoluteVec(new Vec3(5.5, 1, 3.5)));
      Point outside = new Point(spawn.dimension, spawn.x + 9, spawn.y, spawn.z, 0, 0);
      existing.teleportTo(h.getLevel(), outside.x, outside.y, outside.z, 0, 0);
      h.assertTrue(
          !BackHistory.different(spawn, Teleports.point(existing)),
          "Other mods' native teleport paths cannot escape and return the player to the initial"
              + " spawn");
      app.rules.answer(existing, false, null);
      h.assertTrue(app.rules.pending(existing), "Deny keeps player in the room");
      app.rules.answer(existing, true, "stale");
      h.assertTrue(
          app.rules.pending(existing), "An obsolete rules button cannot accept a replacement room");
      var profile = existing.getGameProfile();
      app.server.getPlayerList().remove(existing);
      existing = player(h, profile);
      h.assertTrue(
          app.rules.pending(existing) && !BackHistory.different(spawn, Teleports.point(existing)),
          "Reconnect cannot bypass pending acceptance");
      newcomer = player(h, "rulesNewcomer");
      h.assertTrue(
          app.rules.pending(newcomer) && !BackHistory.different(spawn, Teleports.point(newcomer)),
          "A genuinely new player arrives at the rules spawn");
      app.server
          .getCommands()
          .performPrefixedCommand(existing.createCommandSourceStack(), "ffa rules accept");
      h.assertFalse(
          app.rules.pending(existing), "Consent command works while all other commands are locked");
      h.assertTrue(
          new Storage(app.storage.directory).player(existing.getUUID()).rulesAccepted,
          "Acceptance is durable on disk");
      outgoing(existing);
      existing.sendSystemMessage(Component.literal("normal chat restored"));
      h.assertTrue(
          outgoing(existing).stream().anyMatch(p -> p instanceof ClientboundSystemChatPacket),
          "Normal chat is restored immediately after acceptance");
      existing.setPos(h.absoluteVec(new Vec3(12.5, 1, 12.5)));
      app.rules.enforce(existing);
      h.assertTrue(
          !app.rules.inside(Teleports.point(existing)), "Accepted players can leave normally");
      existing.setPos(h.absoluteVec(new Vec3(3.5, 1, 3.5)));
      app.rules.enforce(existing);
      h.assertFalse(
          app.rules.pending(existing), "Returning to the room never requires consent again");
      app.server.getPlayerList().remove(existing);
      existing = player(h, profile);
      h.assertFalse(app.rules.pending(existing), "Accepted status survives real reconnect");
      try {
        app.regions.edit(List.of(parent), app.server, app.settings, app.compat);
        h.fail("Active rules region must not be deleted");
      } catch (IllegalArgumentException expected) {
        h.assertTrue(
            expected.getMessage().contains("Disable"),
            "Region edits fail with an actionable error");
      }
      app.rules.enable(false);
      h.assertFalse(
          app.rules.pending(newcomer),
          "Disabling releases pending players without falsifying saved acceptance");
    } finally {
      app.storage.commit(state);
      app.regions.edit(regions, app.server, app.settings, app.compat);
      app.server.getPlayerList().deop(admin.getGameProfile());
      for (var p : List.of(admin, existing, ordinary)) app.server.getPlayerList().remove(p);
      if (newcomer != null) app.server.getPlayerList().remove(newcomer);
    }
  }

  private static Region region(GameTestHelper h, String id, Kind kind, int low, int high) {
    Region r = new Region();
    r.id = r.name = id;
    r.kind = kind;
    r.dimension = h.getLevel().dimension().location().toString();
    r.minX = h.absolutePos(new net.minecraft.core.BlockPos(low, 1, low)).getX();
    r.minZ = h.absolutePos(new net.minecraft.core.BlockPos(low, 1, low)).getZ();
    r.maxX = h.absolutePos(new net.minecraft.core.BlockPos(high, 1, high)).getX();
    r.maxZ = h.absolutePos(new net.minecraft.core.BlockPos(high, 1, high)).getZ();
    r.settlementOverride = true;
    return r;
  }
}
