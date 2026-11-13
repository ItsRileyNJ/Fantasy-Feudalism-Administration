package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;
import static net.minecraft.commands.Commands.*;

import com.mojang.brigadier.StringReader;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class Commands {
  record Selection(String dimension, BlockPos first, BlockPos second) {}

  private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
  private static final Map<
          com.mojang.brigadier.tree.CommandNode<CommandSourceStack>,
          java.util.function.Predicate<CommandSourceStack>>
      NATIVE_REQUIREMENTS = new IdentityHashMap<>();

  private Commands() {}

  public static void register(RegisterCommandsEvent e) {
    SELECTIONS.clear();
    var root = literal("ffa").executes(c -> help(c.getSource()));
    CommandTree.addTo(root);
    NATIVE_REQUIREMENTS.clear();
    capture(e.getDispatcher().register(root));
  }

  private static void capture(com.mojang.brigadier.tree.CommandNode<CommandSourceStack> node) {
    if (NATIVE_REQUIREMENTS.putIfAbsent(node, node.getRequirement()) != null) return;
    node.getChildren().forEach(Commands::capture);
  }

  static java.util.function.Predicate<CommandSourceStack> nativeRequirement(
      com.mojang.brigadier.tree.CommandNode<CommandSourceStack> node) {
    return NATIVE_REQUIREMENTS.get(node);
  }

  static int help(CommandSourceStack source) {
    var root = source.getServer().getCommands().getDispatcher().getRoot().getChild("ffa");
    String available =
        root.getChildren().stream()
            .filter(n -> n.canUse(source))
            .map(n -> "/ffa " + n.getName())
            .sorted()
            .collect(java.util.stream.Collectors.joining(", "));
    source.sendSuccess(
        () -> Component.literal("Available commands: " + available + ". Use Tab for options."),
        false);
    return 1;
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

  static int execute(CommandSourceStack source, String text) {
    var app = Administration.INSTANCE;
    try {
      if (app.server == null)
        throw new IllegalArgumentException("Administration is still starting.");
      List<String> args = tokenize(text);
      String cmd = arg(args, 0);
      ServerPlayer p = source.getPlayer();
      if (cmd.equals("help")) return help(source);
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
          && !(cmd.equals("balance") && args.size() > 1)
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
              Region r =
                  region(
                      p, "spawn", Kind.SPAWN, args.size() > 2 && arg(args, 2).equals("override"));
              r.entry = "Entering {name}";
              r.exit = "Leaving {name}";
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
          if (args.size() > 2 && arg(args, 1).equals("create")) {
            authorize(app, source, "ffa.warp.manage");
            String id = arg(args, 2);
            Regions.checkId(id);
            if (app.storage.state().warps.containsKey(id))
              throw new IllegalArgumentException(
                  "Warp already exists. Use /ffa setwarp " + id + " to update it.");
            String error = app.teleports.validate(p, Teleports.point(p));
            if (error != null) throw new IllegalArgumentException(error);
            app.storage.update(
                st -> {
                  var w = new Warp();
                  w.point = Teleports.point(p);
                  st.warps.put(id, w);
                });
            feedback(app, source, text, Teleports.point(p).describe(), "SUCCESS persisted");
          } else if (args.size() > 2) {
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
          else
            app.teleports.request(
                p, player(app, source.getPlayer(), arg(args, 1)), cmd.equals("tpahere"));
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
            String target = player(app, source.getPlayer(), arg(args, 1)).getUUID().toString();
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
          if (app.storage.player(p.getUUID()).back == null)
            throw new IllegalArgumentException("No previous death or teleport location.");
          app.teleports.teleport(p, app.storage.player(p.getUUID()).back, false);
        }
        case "rtp" -> {
          requirePlayer(p);
          if (!app.settings.rtp) throw new IllegalArgumentException("Random teleport is disabled");
          app.teleports.random(p);
        }
        case "balance" -> {
          requirePlayer(p);
          if (!app.settings.economy) throw new IllegalArgumentException("Economy is disabled.");
          if (args.size() > 1
              && Set.of("send", "request", "accept", "deny").contains(arg(args, 1))) {
            String action = arg(args, 1);
            if (action.equals("send") || action.equals("request")) {
              var target = player(app, p, arg(args, 2));
              long amount = Long.parseLong(arg(args, 3));
              if (action.equals("send")) app.payments.send(p, target, amount);
              else app.payments.request(p, target, amount);
            } else
              app.payments.answer(
                  p,
                  action.equals("accept"),
                  args.size() > 2 ? UUID.fromString(arg(args, 2)) : null);
          } else if (args.size() > 1) {
            authorize(app, source, "ffa.economy.manage");
            if (!arg(args, 1).equals("set"))
              throw new IllegalArgumentException("Use /ffa balance set PLAYER AMOUNT.");
            ServerPlayer target = player(app, p, arg(args, 2));
            app.economy.set(p, target.getUUID(), Long.parseLong(arg(args, 3)));
            feedback(app, source, text, Teleports.point(target).describe(), "SUCCESS persisted");
          } else
            Teleports.tell(
                p, app.storage.player(p.getUUID()).balance + " " + app.settings.currency);
        }
        case "pay" -> {
          requirePlayer(p);
          if (!app.settings.pay) throw new IllegalArgumentException("Transfers disabled");
          app.economy.pay(
              p, player(app, source.getPlayer(), arg(args, 1)), Long.parseLong(arg(args, 2)));
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
      source.sendFailure(Component.literal(message));
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
    a = new ArrayList<>(a);
    if (a.size() > 1 && a.get(1).equals("child")) a.set(1, "create");
    if (a.size() > 2
        && a.get(1).equals("create")
        && Set.of("child", "standalone").contains(a.get(2))) {
      boolean standalone = a.get(2).equals("standalone");
      a.remove(2);
      if (standalone) a.set(1, "standalone");
    }
    final List<String> args = a;
    String action = arg(args, 1);
    if (action.equals("visualize") && (a.size() == 2 || a.get(2).equals("cancel"))) {
      requirePlayer(p);
      app.regionVisuals.stop(p);
      s.sendSuccess(() -> Component.literal("Region boundary hidden."), false);
      return;
    }
    if (action.equals("list")) {
      s.sendSuccess(
          () ->
              Component.literal(
                  "Regions: "
                      + String.join(", ", app.regions.all().stream().map(r -> r.id).toList())),
          false);
      return;
    }
    requirePlayer(p);
    String requested = arg(args, 2);
    String id =
        action.equals("create") || action.equals("standalone")
            ? requested
            : app.regions.get(requested).id;
    var list = new ArrayList<>(app.regions.copy());
    if (action.equals("create") || action.equals("standalone")) {
      Region r =
          region(
              p,
              id,
              action.equals("create") ? Kind.CHILD : Kind.STANDALONE,
              a.size() > 3 && arg(args, 3).equals("override"));
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
          if (list.stream().anyMatch(x -> x.parent.equalsIgnoreCase(id)))
            throw new IllegalArgumentException("Delete child regions first");
          list.remove(r);
        }
        case "flag" -> {
          Flag f = Flag.valueOf(arg(args, 3).toUpperCase(Locale.ROOT));
          String v = arg(args, 4);
          if (v.equals("inherit")) r.flags.remove(f);
          else {
            if (!v.equals("true") && !v.equals("false"))
              throw new IllegalArgumentException("Expected true, false or inherit");
            r.flags.put(f, Boolean.parseBoolean(v));
          }
        }
        case "message" -> {
          String side = arg(args, 3), msg = arg(args, 4);
          if (side.equals("entry")) r.entry = msg;
          else if (side.equals("exit")) r.exit = msg;
          else throw new IllegalArgumentException("Use message ID entry|exit \"text\"");
        }
        case "access" -> r.access = access(args, 3);
        case "interaction" -> {
          String block = arg(args, 3);
          if (arg(args, 4).equals("allow")) r.interactions.add(block);
          else if (arg(args, 4).equals("remove")) r.interactions.remove(block);
          else
            throw new IllegalArgumentException("Use interaction ID minecraft:block allow|remove");
        }
        case "title" -> {
          String side = arg(args, 3), property = arg(args, 4), value = arg(args, 5);
          if (!Set.of("entry", "exit").contains(side))
            throw new IllegalArgumentException("Choose entry or exit.");
          boolean entry = side.equals("entry");
          if (property.equals("color")) {
            RegionTitles.color(value);
            if (entry) r.entryColor = value;
            else r.exitColor = value;
          } else {
            if (!Set.of("bold", "italic", "underline").contains(property)
                || !Set.of("true", "false").contains(value))
              throw new IllegalArgumentException(
                  "Use color, bold, italic or underline with a valid value.");
            boolean enabled = Boolean.parseBoolean(value);
            if (property.equals("underline")) {
              if (entry) r.entryUnderlined = enabled;
              else r.exitUnderlined = enabled;
            } else if (entry && property.equals("bold")) r.entryBold = enabled;
            else if (entry) r.entryItalic = enabled;
            else if (property.equals("bold")) r.exitBold = enabled;
            else r.exitItalic = enabled;
          }
        }
        case "item" -> {
          String item = arg(args, 3);
          var key = net.minecraft.resources.ResourceLocation.tryParse(item);
          if (key == null
              || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key)
              || item.equals("minecraft:air")) throw new IllegalArgumentException("Unknown item.");
          if (arg(args, 4).equals("allow")) r.items.add(item);
          else if (arg(args, 4).equals("remove")) r.items.remove(item);
          else throw new IllegalArgumentException("Use item REGION ITEM allow|remove.");
        }
        case "expand", "contract" -> {
          int amount = Integer.parseInt(arg(args, 3));
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
          app.regionVisuals.start(p, id);
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
    Regions.checkName(id);
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

  static Selection selection(ServerPlayer p) {
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
      if (app.settings.adminPortalCreationOnly && !app.compat.admin(p))
        throw new IllegalArgumentException("Only administrators can create portals.");
      if (old != null) throw new IllegalArgumentException("Portal ID already exists");
      Selection sel = selection(p);
      String warp =
          a.size() > 3
              ? arg(a, 3).equals("to") ? arg(a, 4) : arg(a, 3)
              : app.storage.state().warps.containsKey(id) ? id : "";
      if (!warp.isEmpty() && !app.storage.state().warps.containsKey(warp))
        throw new IllegalArgumentException(
            "Unknown warp. Create it at the destination with /ffa warp create " + warp + ".");
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
          app.storage.update(
              st -> {
                st.portals.get(id).warp = warp;
                st.portals.get(id).managedWarp = false;
              });
        }
        case "destination" -> {
          authorize(app, s, "ffa.warp.manage");
          if (old.dimension.equals(p.level().dimension().location().toString())
              && old.contains(p.getX(), p.getY(), p.getZ()))
            throw new IllegalArgumentException(
                "Stand outside the portal at its destination first.");
          String error = app.teleports.validate(p, Teleports.point(p));
          if (error != null) throw new IllegalArgumentException(error);
          String warp = old.warp == null || old.warp.isEmpty() ? id : old.warp;
          app.storage.update(
              st -> {
                var portal = st.portals.get(id);
                var w = st.warps.get(warp);
                if (w == null) {
                  w = new Warp();
                  w.access =
                      Storage.JSON.fromJson(Storage.JSON.toJson(portal.access), Access.class);
                  portal.managedWarp = true;
                }
                w.point = Teleports.point(p);
                st.warps.put(warp, w);
                portal.warp = warp;
              });
        }
        case "access" -> {
          Access access = access(a, 3);
          app.storage.update(
              st -> {
                var portal = st.portals.get(id);
                portal.access = access;
                if (portal.managedWarp && st.warps.containsKey(portal.warp))
                  st.warps.get(portal.warp).access =
                      Storage.JSON.fromJson(Storage.JSON.toJson(access), Access.class);
              });
        }
        default -> throw new IllegalArgumentException("Unknown portal operation");
      }
    }
    Portals.reindex(app);
    if (action.equals("create") && app.storage.state().portals.get(id).warp.isEmpty())
      Teleports.tell(p, "Stand at the destination, then run /ffa portal destination " + id + ".");
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
    if (action.equals("sign")) {
      if (!app.compat.admin(p)) throw new IllegalArgumentException("Administrator only.");
      if (arg(a, 2).equals("remove")) app.signShops.remove(p);
      else if (arg(a, 2).equals("bind"))
        app.signShops.bind(p, arg(a, 3), a.size() > 4 ? Integer.parseInt(arg(a, 4)) : 1);
      else
        throw new IllegalArgumentException(
            "Use /ffa shop sign bind SHOP [QUANTITY] or sign remove.");
      return;
    }
    if (action.equals("list")) {
      Teleports.tell(p, app.storage.state().shops.keySet().toString());
      return;
    }
    String id = app.economy.shopId(arg(a, 2));
    if (action.equals("buy") || action.equals("sell")) {
      app.economy.trade(p, id, Integer.parseInt(arg(a, 3)), action.equals("buy"));
      Teleports.tell(p, "Trade saved.");
      return;
    }
    authorize(app, s, "ffa.shop.manage");
    Regions.checkName(id);
    if (action.equals("limits")) {
      if (a.size() == 3) {
        var shop = app.storage.state().shops.get(id);
        if (shop == null) throw new IllegalArgumentException("Unknown shop.");
        Teleports.tell(
            p,
            (shop.limitScope.equals("shared") ? "Shared shop totals" : "Per-player limits")
                + "; "
                + (shop.limitReset.equals("daily") ? "resets daily at 00:00 UTC" : "manual reset")
                + ". Buy: "
                + (shop.buyLimit < 0 ? "unlimited" : shop.buyLimit + " items")
                + ". Sell: "
                + (shop.sellLimit < 0 ? "unlimited" : shop.sellLimit + " items")
                + ".");
        return;
      }
      app.economy.limits(
          id, arg(a, 3), arg(a, 4), Long.parseLong(arg(a, 5)), Long.parseLong(arg(a, 6)));
      app.storage.audit(
          app.settings,
          s.getTextName(),
          text,
          Teleports.point(p).describe(),
          "SUCCESS limits configured");
      Teleports.tell(p, "Shop limits updated.");
      return;
    }
    if (action.equals("reset")) {
      UUID target = null;
      if (a.size() > 3) {
        var online = app.server.getPlayerList().getPlayerByName(arg(a, 3));
        if (online != null && !Vanish.canSee(p, online)) online = null;
        target = online == null ? UUID.fromString(arg(a, 3)) : online.getUUID();
      }
      app.economy.resetLimits(id, target);
      app.storage.audit(
          app.settings,
          s.getTextName(),
          text,
          Teleports.point(p).describe(),
          "SUCCESS counters reset");
      Teleports.tell(p, "Shop counters reset.");
      return;
    }
    if (app.economy.pendingShop(id))
      throw new IllegalArgumentException("Recover this shop's pending transactions first.");
    if (action.equals("set")) {
      Shop shop =
          app.storage.state().shops.containsKey(id)
              ? Storage.JSON.fromJson(
                  Storage.JSON.toJson(app.storage.state().shops.get(id)), Shop.class)
              : new Shop();
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
      if (app.storage.state().shopSigns.values().stream().anyMatch(sign -> sign.shop.equals(id)))
        throw new IllegalArgumentException("Unlink this shop's signs first.");
      if (!app.storage.state().shops.containsKey(id))
        throw new IllegalArgumentException("Unknown shop");
      app.storage.update(st -> st.shops.remove(id));
    } else throw new IllegalArgumentException("Unknown shop operation");
    app.signShops.refresh(id);
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

  private static ServerPlayer player(Administration app, ServerPlayer viewer, String name) {
    ServerPlayer p = app.server.getPlayerList().getPlayerByName(name);
    if (p == null || viewer != null && !Vanish.canSee(viewer, p))
      throw new IllegalArgumentException("Player is offline");
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
    s.sendSuccess(() -> Component.literal(successMessage(action)), false);
  }

  static String successMessage(String action) {
    try {
      List<String> a = new ArrayList<>(tokenize(action));
      if (!a.isEmpty() && a.getFirst().equals("ffa")) a.removeFirst();
      String command = a.getFirst(), operation = a.size() > 1 ? a.get(1) : "";
      return switch (command) {
        case "pos1" -> "First corner set.";
        case "pos2" -> "Second corner set.";
        case "spawn" -> operation.equals("create") ? "Spawn region created." : "Spawn point set.";
        case "setwarp" -> "Warp set.";
        case "delwarp" -> "Warp deleted.";
        case "warp" -> operation.equals("create") ? "Warp created." : "Warp access updated.";
        case "region" ->
            switch (operation) {
              case "create", "standalone" -> "Region created.";
              case "delete" -> "Region deleted.";
              case "visualize" -> "Region boundary shown.";
              case "expand", "contract" -> "Region resized.";
              case "flag" -> "Region flag updated.";
              case "message" -> "Region message updated.";
              case "item" -> "Usable items updated.";
              case "title" -> "Region notification updated.";
              case "interaction" -> "Allowed blocks updated.";
              default -> "Region updated.";
            };
        case "portal" ->
            switch (operation) {
              case "create" -> "Portal created.";
              case "delete" -> "Portal deleted.";
              case "color" -> "Portal color updated.";
              default -> "Portal updated.";
            };
        case "shop" ->
            operation.equals("delete")
                ? "Shop deleted."
                : operation.equals("sign") ? "Shop sign updated." : "Shop saved.";
        case "balance" -> "Balance set.";
        case "reload" -> "Configuration reloaded.";
        case "backup" -> "Backup created.";
        case "chat" -> "Chat appearance updated.";
        case "ranks" ->
            operation.equals("reload")
                ? "Ranks reloaded."
                : operation.equals("create")
                    ? "Rank created."
                    : operation.equals("delete") ? "Rank deleted." : "Ranks updated.";
        default -> "Done.";
      };
    } catch (Exception ignored) {
      return "Done.";
    }
  }
}
