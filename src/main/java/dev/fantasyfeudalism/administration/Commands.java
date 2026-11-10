package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;
import static net.minecraft.commands.Commands.*;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.time.Instant;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class Commands {
  record Selection(String dimension, BlockPos first, BlockPos second) {}

  private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();

  private Commands() {}

  public static void register(RegisterCommandsEvent e) {
    SELECTIONS.clear();
    e.getDispatcher()
        .register(
            literal("ffa")
                .executes(
                    c -> {
                      c.getSource()
                          .sendSuccess(
                              () ->
                                  Component.literal(
                                      "Fantasy Feudalism Administration: /ffa spawn, warp, region,"
                                          + " portal, shop, balance, help"),
                              false);
                      return 1;
                    })
                .then(
                    argument("operation", StringArgumentType.greedyString())
                        .executes(
                            c ->
                                execute(
                                    c.getSource(), StringArgumentType.getString(c, "operation")))));
  }

  static List<String> tokenize(String text)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    StringReader reader = new StringReader(text);
    List<String> result = new ArrayList<>();
    while (reader.canRead()) {
      reader.skipWhitespace();
      if (!reader.canRead()) break;
      if (StringReader.isQuotedStringStart(reader.peek())) result.add(reader.readString());
      else {
        int start = reader.getCursor();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) reader.skip();
        result.add(text.substring(start, reader.getCursor()));
      }
    }
    return result;
  }

  private static int execute(CommandSourceStack source, String text) {
    var app = Administration.INSTANCE;
    try {
      if (app.server == null)
        throw new IllegalArgumentException("Administration is still starting.");
      List<String> args = tokenize(text);
      String cmd = arg(args, 0);
      ServerPlayer p = source.getPlayer();
      if (cmd.equals("help")) {
        source.sendSuccess(
            () ->
                Component.literal(
                    "Use /ffa pos1, pos2; spawn create|setpoint; region"
                        + " create|standalone|delete|flag|message|expand|contract|visualize|list;"
                        + " warp NAME; setwarp NAME; delwarp NAME; warps; portal"
                        + " create|delete|link|color|access; shop buy|sell|list|set|delete;"
                        + " balance; pay PLAYER AMOUNT; home|sethome|delhome NAME; homes;"
                        + " tpa|tpahere PLAYER; tpaccept|tpdeny|tpcancel; reload; backup. See docs"
                        + " for syntax."),
            false);
        return 1;
      }
      if (p != null
          && !app.compat.admin(p)
          && Set.of(
                  "spawn",
                  "warp",
                  "warps",
                  "home",
                  "sethome",
                  "delhome",
                  "homes",
                  "tpa",
                  "tpahere",
                  "tpaccept",
                  "tpdeny",
                  "tpcancel",
                  "tptoggle",
                  "tpignore",
                  "back",
                  "rtp",
                  "balance",
                  "pay",
                  "baltop",
                  "shop")
              .contains(cmd)
          && !app.compat.decision(p, "ffa.command." + cmd).orElse(app.settings.publicCommands))
        throw new IllegalArgumentException("Permission denied.");
      switch (cmd) {
        case "spawn" -> {
          if (args.size() == 1) {
            requirePlayer(p);
            app.teleports.teleport(p, app.storage.state().spawn, false);
          } else {
            authorize(app, source, "ffa.spawn.manage");
            requirePlayer(p);
            String action = arg(args, 1);
            if (action.equals("setpoint")) {
              String error = app.teleports.validate(p, Teleports.point(p));
              if (error != null) throw new IllegalArgumentException(error);
              app.storage.update(s -> s.spawn = Teleports.point(p));
            } else if (action.equals("create")) {
              var list = new ArrayList<>(app.regions.copy());
              Region r = region(p, "spawn", Kind.SPAWN, args.contains("override"));
              r.entry = "Welcome to Spawn!";
              r.exit = "You are now leaving the protected spawn area.";
              list.add(r);
              app.regions.edit(list, app.server, app.settings, app.compat);
            } else
              throw new IllegalArgumentException("Use spawn create [override] or spawn setpoint");
            feedback(app, source, text, Teleports.point(p).describe(), "SUCCESS persisted");
          }
        }
        case "pos1", "pos2" -> {
          authorize(app, source, "ffa.region.edit");
          requirePlayer(p);
          Selection old = SELECTIONS.get(p.getUUID());
          String dim = p.level().dimension().location().toString();
          if (old == null || !old.dimension.equals(dim)) old = new Selection(dim, null, null);
          var pos = p.blockPosition();
          SELECTIONS.put(
              p.getUUID(),
              new Selection(
                  dim,
                  cmd.equals("pos1") ? pos : old.first,
                  cmd.equals("pos2") ? pos : old.second));
          feedback(
              app,
              source,
              text,
              Teleports.point(p).describe(),
              "SUCCESS selection saved for this session");
        }
        case "region" -> regionCommand(app, source, p, args, text);
        case "setwarp" -> {
          authorize(app, source, "ffa.warp.manage");
          requirePlayer(p);
          String id = arg(args, 1);
          Regions.checkId(id);
          String error = app.teleports.validate(p, Teleports.point(p));
          if (error != null) throw new IllegalArgumentException(error);
          app.storage.update(
              s -> {
                Warp w = s.warps.getOrDefault(id, new Warp());
                w.point = Teleports.point(p);
                s.warps.put(id, w);
              });
          feedback(app, source, text, Teleports.point(p).describe(), "SUCCESS persisted");
        }
        case "delwarp" -> {
          authorize(app, source, "ffa.warp.manage");
          String id = arg(args, 1);
          if (!app.storage.state().warps.containsKey(id))
            throw new IllegalArgumentException("Unknown warp");
          if (app.storage.state().portals.values().stream().anyMatch(x -> x.warp.equals(id)))
            throw new IllegalArgumentException("Unlink/delete portals using this warp first");
          app.storage.update(s -> s.warps.remove(id));
          feedback(app, source, text, "warp " + id, "SUCCESS persisted");
        }
        case "warp" -> {
          requirePlayer(p);
          if (args.size() > 2) {
            authorize(app, source, "ffa.warp.manage");
            String id = arg(args, 1);
            Access access = access(args, 2);
            if (!app.storage.state().warps.containsKey(id))
              throw new IllegalArgumentException("Unknown warp");
            app.storage.update(s -> s.warps.get(id).access = access);
            feedback(app, source, text, "warp " + id, "SUCCESS persisted");
          } else app.teleports.warp(p, arg(args, 1));
        }
        case "warps" -> {
          requirePlayer(p);
          Teleports.tell(
              p,
              "Warps: "
                  + String.join(
                      ", ",
                      app.storage.state().warps.entrySet().stream()
                          .filter(
                              x ->
                                  app.compat.access(
                                      p,
                                      x.getValue().access,
                                      BlockPos.containing(
                                          x.getValue().point.x,
                                          x.getValue().point.y,
                                          x.getValue().point.z)))
                          .map(Map.Entry::getKey)
                          .toList()));
        }
        case "portal" -> portal(app, source, p, args, text);
        case "home", "sethome", "delhome", "homes" -> {
          requirePlayer(p);
          if (!app.settings.homes || app.compat.ftbEssentials())
            throw new IllegalArgumentException(
                "Native homes disabled; use the installed provider.");
          if (cmd.equals("homes"))
            Teleports.tell(p, "Homes: " + app.storage.player(p.getUUID()).homes.keySet());
          else app.teleports.home(p, arg(args, 1), cmd.equals("sethome"), cmd.equals("delhome"));
        }
        case "tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel" -> {
          requirePlayer(p);
          if (!app.settings.tpa || app.compat.ftbEssentials())
            throw new IllegalArgumentException(
                "Native teleport requests disabled; use the installed provider.");
          if (cmd.equals("tpaccept") || cmd.equals("tpdeny"))
            app.teleports.answer(
                p, cmd.equals("tpaccept"), args.size() > 1 ? UUID.fromString(arg(args, 1)) : null);
          else if (cmd.equals("tpcancel")) app.teleports.cancel(p);
          else app.teleports.request(p, player(app, arg(args, 1)), cmd.equals("tpahere"));
        }
        case "tptoggle", "tpignore" -> {
          requirePlayer(p);
          if (!app.settings.privacy || app.compat.ftbEssentials())
            throw new IllegalArgumentException("Native request privacy is disabled");
          if (cmd.equals("tptoggle"))
            app.storage.update(
                s -> {
                  PlayerData d = s.players.get(p.getUUID().toString());
                  d.requestsEnabled = !d.requestsEnabled;
                });
          else {
            String target = player(app, arg(args, 1)).getUUID().toString();
            app.storage.update(
                s -> {
                  Set<String> ignore = s.players.get(p.getUUID().toString()).ignored;
                  if (!ignore.remove(target)) ignore.add(target);
                });
          }
          Teleports.tell(p, "Request preferences saved.");
        }
        case "back" -> {
          requirePlayer(p);
          if (!app.settings.back) throw new IllegalArgumentException("Back is disabled");
          app.teleports.teleport(p, app.storage.player(p.getUUID()).back, false);
        }
        case "rtp" -> {
          requirePlayer(p);
          if (!app.settings.rtp) throw new IllegalArgumentException("Random teleport is disabled");
          app.teleports.random(p);
        }
        case "balance" -> {
          requirePlayer(p);
          if (!app.settings.economy) throw new IllegalArgumentException("Economy disabled");
          Teleports.tell(p, app.storage.player(p.getUUID()).balance + " " + app.settings.currency);
        }
        case "pay" -> {
          requirePlayer(p);
          if (!app.settings.pay) throw new IllegalArgumentException("Transfers disabled");
          app.economy.pay(p, player(app, arg(args, 1)), Long.parseLong(arg(args, 2)));
          Teleports.tell(p, "Transfer saved.");
        }
        case "baltop" -> {
          requirePlayer(p);
          if (!app.settings.leaderboard) throw new IllegalArgumentException("Leaderboard disabled");
          app.storage.state().players.entrySet().stream()
              .sorted(
                  Comparator.comparingLong(
                          (Map.Entry<String, PlayerData> x) -> x.getValue().balance)
                      .reversed())
              .limit(10)
              .forEach(
                  x ->
                      Teleports.tell(
                          p,
                          x.getKey() + ": " + x.getValue().balance + " " + app.settings.currency));
        }
        case "economy" -> {
          authorize(app, source, "ffa.economy.manage");
          requirePlayer(p);
          if (!arg(args, 1).equals("set"))
            throw new IllegalArgumentException("Use economy set PLAYER AMOUNT");
          ServerPlayer target = player(app, arg(args, 2));
          app.economy.set(p, target.getUUID(), Long.parseLong(arg(args, 3)));
          feedback(app, source, text, Teleports.point(target).describe(), "SUCCESS persisted");
        }
        case "shop" -> shop(app, source, p, args, text);
        case "reload" -> {
          authorize(app, source, "ffa.reload");
          app.reload();
          feedback(
              app,
              source,
              text,
              "server configuration and all regions",
              "SUCCESS validated and applied");
        }
        case "backup" -> {
          authorize(app, source, "ffa.backup");
          String id = app.storage.snapshot(app.settingsFile, app.regions.file);
          feedback(
              app,
              source,
              text,
              app.storage.directory.resolve("backups").resolve(id).toString(),
              "SUCCESS snapshot saved; economy recovery requires matching playerdata");
        }
        default -> throw new IllegalArgumentException("Unknown operation. Use /ffa help.");
      }
      return 1;
    } catch (Exception ex) {
      boolean admin =
          source.hasPermission(app.settings.operatorLevel)
              || source.getPlayer() != null
                  && app.compat != null
                  && app.compat.admin(source.getPlayer());
      String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
      source.sendFailure(
          Component.literal(
              (admin
                      ? Instant.now()
                          + " FAILED "
                          + text
                          + " at "
                          + source.getLevel().dimension().location()
                          + " "
                          + source.getPosition()
                          + ": "
                      : "")
                  + message));
      if (app.storage != null)
        app.storage.security(
            app.settings,
            source.getTextName(),
            "command " + text.split(" ", 2)[0],
            source.getLevel().dimension().location() + " " + source.getPosition(),
            "REJECTED " + message);
      if (admin && app.storage != null)
        app.storage.audit(
            app.settings,
            source.getTextName(),
            text,
            source.getLevel().dimension().location() + " " + source.getPosition(),
            "FAILED " + message);
      return 0;
    }
  }

  private static void regionCommand(
      Administration app, CommandSourceStack s, ServerPlayer p, List<String> a, String text)
      throws Exception {
    authorize(app, s, "ffa.region.edit");
    String action = arg(a, 1);
    if (action.equals("list")) {
      s.sendSuccess(
          () ->
              Component.literal(
                  app.regions.all().stream()
                      .map(
                          r ->
                              r.id
                                  + " "
                                  + r.kind
                                  + " "
                                  + r.dimension
                                  + " ["
                                  + r.minX
                                  + ","
                                  + r.minZ
                                  + "]..["
                                  + r.maxX
                                  + ","
                                  + r.maxZ
                                  + "]")
                      .toList()
                      .toString()),
          false);
      return;
    }
    requirePlayer(p);
    String id = arg(a, 2);
    var list = new ArrayList<>(app.regions.copy());
    if (action.equals("create") || action.equals("standalone")) {
      Region r =
          region(
              p,
              id,
              action.equals("create") ? Kind.CHILD : Kind.STANDALONE,
              a.contains("override"));
      if (r.kind == Kind.CHILD)
        r.parent =
            list.stream()
                .filter(x -> x.kind == Kind.SPAWN)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Create main spawn first"))
                .id;
      list.add(r);
    } else {
      Region r =
          list.stream()
              .filter(x -> x.id.equals(id))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("Unknown region"));
      switch (action) {
        case "delete" -> {
          if (list.stream().anyMatch(x -> x.parent.equals(id)))
            throw new IllegalArgumentException("Delete child regions first");
          list.remove(r);
        }
        case "flag" -> {
          Flag f = Flag.valueOf(arg(a, 3).toUpperCase(Locale.ROOT));
          String v = arg(a, 4);
          if (v.equals("inherit")) r.flags.remove(f);
          else {
            if (!v.equals("true") && !v.equals("false"))
              throw new IllegalArgumentException("Expected true, false or inherit");
            r.flags.put(f, Boolean.parseBoolean(v));
          }
        }
        case "message" -> {
          String side = arg(a, 3), msg = arg(a, 4);
          if (side.equals("entry")) r.entry = msg;
          else if (side.equals("exit")) r.exit = msg;
          else throw new IllegalArgumentException("Use message ID entry|exit \"text\"");
        }
        case "access" -> r.access = access(a, 3);
        case "interaction" -> {
          String block = arg(a, 3);
          if (arg(a, 4).equals("allow")) r.interactions.add(block);
          else if (arg(a, 4).equals("remove")) r.interactions.remove(block);
          else
            throw new IllegalArgumentException("Use interaction ID minecraft:block allow|remove");
        }
        case "expand", "contract" -> {
          int amount = Integer.parseInt(arg(a, 3));
          if (amount < 1 || amount > 100000)
            throw new IllegalArgumentException("Amount must be 1..100000");
          if (action.equals("contract")) amount = -amount;
          r.minX -= amount;
          r.minZ -= amount;
          r.maxX += amount;
          r.maxZ += amount;
        }
        case "visualize" -> {
          if (!p.level().dimension().location().toString().equals(r.dimension))
            throw new IllegalArgumentException("Travel to the region dimension first");
          double y = p.getY() + .3;
          for (int i = 0; i <= 32; i++) {
            double t = i / 32.0;
            for (double[] v :
                new double[][] {
                  {r.minX + (r.maxX + 1.0 - r.minX) * t, r.minZ},
                  {r.minX + (r.maxX + 1.0 - r.minX) * t, r.maxZ + 1.0},
                  {r.minX, r.minZ + (r.maxZ + 1.0 - r.minZ) * t},
                  {r.maxX + 1.0, r.minZ + (r.maxZ + 1.0 - r.minZ) * t}
                })
              p.serverLevel()
                  .sendParticles(p, ParticleTypes.END_ROD, true, v[0], y, v[1], 1, 0, 0, 0, 0);
          }
          feedback(
              app,
              s,
              text,
              r.dimension + " region=" + id,
              "SUCCESS boundary particles sent privately");
          return;
        }
        default -> throw new IllegalArgumentException("Unknown region operation");
      }
    }
    Region affected =
        list.stream()
            .filter(region -> region.id.equals(id))
            .findFirst()
            .orElseGet(() -> app.regions.get(id));
    app.regions.edit(list, app.server, app.settings, app.compat);
    for (var player : app.server.getPlayerList().getPlayers()) app.protection.update(player);
    feedback(
        app,
        s,
        text,
        "region="
            + id
            + " name="
            + affected.name
            + " "
            + affected.dimension
            + " ["
            + affected.minX
            + ","
            + affected.minZ
            + "]..["
            + affected.maxX
            + ","
            + affected.maxZ
            + "] full build height",
        "SUCCESS persisted to regions.toml");
  }

  private static Region region(ServerPlayer p, String id, Kind kind, boolean override) {
    Regions.checkId(id);
    Selection sel = selection(p);
    Region r = new Region();
    r.id = id;
    r.name = id;
    r.dimension = sel.dimension;
    r.kind = kind;
    r.minX = Math.min(sel.first.getX(), sel.second.getX());
    r.maxX = Math.max(sel.first.getX(), sel.second.getX());
    r.minZ = Math.min(sel.first.getZ(), sel.second.getZ());
    r.maxZ = Math.max(sel.first.getZ(), sel.second.getZ());
    r.settlementOverride = override;
    return r;
  }

  private static Selection selection(ServerPlayer p) {
    Selection s = SELECTIONS.get(p.getUUID());
    if (s == null
        || s.first == null
        || s.second == null
        || !s.dimension.equals(p.level().dimension().location().toString()))
      throw new IllegalArgumentException("Set /ffa pos1 and /ffa pos2 in this dimension first");
    return s;
  }

  private static void portal(
      Administration app, CommandSourceStack s, ServerPlayer p, List<String> a, String text)
      throws Exception {
    authorize(app, s, "ffa.portal.manage");
    requirePlayer(p);
    if (!app.settings.portals) throw new IllegalArgumentException("Portals disabled");
    String action = arg(a, 1), id = arg(a, 2);
    Regions.checkId(id);
    if (action.equals("color")) {
      Portals.color(app, p, id, arg(a, 3));
      return;
    }
    Portal old = app.storage.state().portals.get(id);
    if (action.equals("create")) {
      if (old != null) throw new IllegalArgumentException("Portal ID already exists");
      Selection sel = selection(p);
      String warp = arg(a, 3);
      if (!app.storage.state().warps.containsKey(warp))
        throw new IllegalArgumentException("Unknown warp");
      Portal portal = new Portal();
      portal.id = id;
      portal.dimension = sel.dimension;
      portal.warp = warp;
      portal.minX = Math.min(sel.first.getX(), sel.second.getX());
      portal.maxX = Math.max(sel.first.getX(), sel.second.getX());
      portal.minY = Math.min(sel.first.getY(), sel.second.getY());
      portal.maxY = Math.max(sel.first.getY(), sel.second.getY());
      portal.minZ = Math.min(sel.first.getZ(), sel.second.getZ());
      portal.maxZ = Math.max(sel.first.getZ(), sel.second.getZ());
      Portals.volume(portal);
      if (portal.minY < p.serverLevel().getMinBuildHeight()
          || portal.maxY >= p.serverLevel().getMaxBuildHeight())
        throw new IllegalArgumentException(
            "Portal bounds must be within the dimension build height");
      for (var other : app.storage.state().portals.values())
        if (other.dimension.equals(portal.dimension)
            && portal.minX <= other.maxX
            && portal.maxX >= other.minX
            && portal.minY <= other.maxY
            && portal.maxY >= other.minY
            && portal.minZ <= other.maxZ
            && portal.maxZ >= other.minZ)
          throw new IllegalArgumentException("Portal overlaps " + other.id);
      app.storage.update(st -> st.portals.put(id, portal));
      Portals.visualize(app, portal);
    } else {
      if (old == null) throw new IllegalArgumentException("Unknown portal");
      switch (action) {
        case "delete" -> {
          app.storage.update(st -> st.portals.remove(id));
          Portals.removeVisuals(app, old);
        }
        case "link" -> {
          String warp = arg(a, 3);
          if (!app.storage.state().warps.containsKey(warp))
            throw new IllegalArgumentException("Unknown warp");
          app.storage.update(st -> st.portals.get(id).warp = warp);
        }
        case "access" -> {
          Access access = access(a, 3);
          app.storage.update(st -> st.portals.get(id).access = access);
        }
        default -> throw new IllegalArgumentException("Unknown portal operation");
      }
    }
    Portals.reindex(app);
    Portal result = app.storage.state().portals.getOrDefault(id, old);
    feedback(
        app,
        s,
        text,
        result.dimension
            + " ["
            + result.minX
            + ","
            + result.minY
            + ","
            + result.minZ
            + "]..["
            + result.maxX
            + ","
            + result.maxY
            + ","
            + result.maxZ
            + "] warp="
            + result.warp
            + " color="
            + result.color
            + " access="
            + result.access.mode,
        "SUCCESS persisted");
  }

  private static void shop(
      Administration app, CommandSourceStack s, ServerPlayer p, List<String> a, String text)
      throws Exception {
    requirePlayer(p);
    String action = arg(a, 1);
    if (action.equals("list")) {
      Teleports.tell(p, app.storage.state().shops.keySet().toString());
      return;
    }
    String id = arg(a, 2);
    if (action.equals("buy") || action.equals("sell")) {
      app.economy.trade(p, id, Integer.parseInt(arg(a, 3)), action.equals("buy"));
      Teleports.tell(p, "Trade saved.");
      return;
    }
    authorize(app, s, "ffa.shop.manage");
    Regions.checkId(id);
    if (action.equals("set")) {
      Shop shop = new Shop();
      shop.item = arg(a, 3);
      var key = net.minecraft.resources.ResourceLocation.tryParse(shop.item);
      if (key == null
          || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key)
          || shop.item.equals("minecraft:air")) throw new IllegalArgumentException("Unknown item");
      shop.buy = Long.parseLong(arg(a, 4));
      shop.sell = Long.parseLong(arg(a, 5));
      if (shop.buy < -1
          || shop.sell < -1
          || shop.buy > app.settings.maxBalance
          || shop.sell > app.settings.maxBalance
          || shop.buy >= 0 && shop.sell > shop.buy)
        throw new IllegalArgumentException(
            "Prices must be -1 (disabled) or 0..maximum balance; sell cannot exceed buy");
      app.storage.update(st -> st.shops.put(id, shop));
    } else if (action.equals("delete")) {
      if (!app.storage.state().shops.containsKey(id))
        throw new IllegalArgumentException("Unknown shop");
      app.storage.update(st -> st.shops.remove(id));
    } else throw new IllegalArgumentException("Unknown shop operation");
    feedback(app, s, text, "shop=" + id + " " + Teleports.point(p).describe(), "SUCCESS persisted");
  }

  private static Access access(List<String> a, int index) {
    Access access = new Access();
    access.mode = arg(a, index);
    access.value = a.size() > index + 1 ? a.get(index + 1) : "";
    Compat.validateAccess(access);
    return access;
  }

  private static String arg(List<String> a, int i) {
    if (i >= a.size()) throw new IllegalArgumentException("Missing argument; use /ffa help");
    return a.get(i);
  }

  private static void requirePlayer(ServerPlayer p) {
    if (p == null) throw new IllegalArgumentException("This operation requires a player");
  }

  private static ServerPlayer player(Administration app, String name) {
    ServerPlayer p = app.server.getPlayerList().getPlayerByName(name);
    if (p == null) throw new IllegalArgumentException("Player is offline");
    return p;
  }

  private static void authorize(Administration app, CommandSourceStack s, String permission) {
    if (!s.hasPermission(app.settings.operatorLevel)
        && (s.getPlayer() == null
            || !app.compat.admin(s.getPlayer())
                && !app.compat.permission(s.getPlayer(), permission)))
      throw new IllegalArgumentException("Permission denied.");
  }

  public static void feedback(
      Administration app, CommandSourceStack s, String action, String where, String result) {
    app.storage.audit(app.settings, s.getTextName(), action, where, result);
    String message =
        app.settings.verbose
            ? Instant.now() + " " + result + " | " + action + " | " + where
            : result + " | " + action;
    s.sendSuccess(() -> Component.literal(message), false);
  }
}
