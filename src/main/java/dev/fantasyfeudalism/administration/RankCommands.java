package dev.fantasyfeudalism.administration;

import static net.minecraft.commands.Commands.*;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;

/** FTB-style operations with native suggestions, private feedback, and console support. */
final class RankCommands {
  private RankCommands() {}

  static com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> tree() {
    var root =
        literal("ranks")
            .requires(RankCommands::allowed)
            .executes(
                c ->
                    run(
                        c,
                        () ->
                            tell(
                                c,
                                "/ffa ranks"
                                    + " create|delete|list|info|add|remove|player|power|default|parent|node|check|reload."
                                    + " See the Ranks wiki.")));
    root.then(
        literal("nodes")
            .executes(
                c ->
                    run(
                        c,
                        () ->
                            tell(
                                c,
                                "Node groups: player, staff, chat, style, limits, commands. Use"
                                    + " /ffa ranks nodes GROUP [PAGE].")))
            .then(
                argument("category", StringArgumentType.word())
                    .suggests(
                        (c, b) ->
                            SharedSuggestionProvider.suggest(PermissionCatalog.categories(), b))
                    .executes(
                        c ->
                            run(
                                c,
                                () ->
                                    PermissionCatalog.explain(text(c, "category"), 1)
                                        .forEach(line -> tell(c, line))))
                    .then(
                        argument("page", IntegerArgumentType.integer(1))
                            .executes(
                                c ->
                                    run(
                                        c,
                                        () ->
                                            PermissionCatalog.explain(
                                                    text(c, "category"),
                                                    IntegerArgumentType.getInteger(c, "page"))
                                                .forEach(line -> tell(c, line)))))));
    root.then(
        literal("list")
            .executes(
                c -> run(c, () -> tell(c, "Ranks: " + String.join(", ", app().ranks.ids())))));
    root.then(
        literal("create")
            .then(
                word("rank")
                    .executes(c -> create(c, 1))
                    .then(
                        argument("power", IntegerArgumentType.integer(-1000000, 1000000))
                            .executes(
                                c -> create(c, IntegerArgumentType.getInteger(c, "power"))))));
    root.then(
        literal("delete")
            .then(
                rank()
                    .executes(
                        c ->
                            edit(
                                c,
                                d -> {
                                  String id = text(c, "rank");
                                  require(d, id);
                                  if (d.ranks.values().stream()
                                      .anyMatch(r -> r.parents.contains(id)))
                                    throw new IllegalArgumentException(
                                        "Remove this rank from child parents first");
                                  d.ranks.remove(id);
                                  d.players.values().forEach(r -> r.remove(id));
                                  d.players.entrySet().removeIf(e -> e.getValue().isEmpty());
                                }))));
    root.then(
        literal("info")
            .then(
                rank()
                    .executes(
                        c ->
                            run(
                                c,
                                () ->
                                    tell(
                                        c,
                                        describeRank(
                                            text(c, "rank"),
                                            require(app().ranks.copy(), text(c, "rank"))))))));
    root.then(
        literal("power")
            .then(
                rank()
                    .then(
                        argument("power", IntegerArgumentType.integer(-1000000, 1000000))
                            .executes(
                                c ->
                                    edit(
                                        c,
                                        d ->
                                            require(d, text(c, "rank")).power =
                                                IntegerArgumentType.getInteger(c, "power"))))));
    root.then(
        literal("default")
            .then(
                rank()
                    .then(
                        argument("enabled", BoolArgumentType.bool())
                            .executes(
                                c ->
                                    edit(
                                        c,
                                        d ->
                                            require(d, text(c, "rank")).defaultRank =
                                                BoolArgumentType.getBool(c, "enabled"))))));
    for (String action : List.of("add", "remove"))
      root.then(
          literal(action)
              .then(
                  target()
                      .then(
                          rank()
                              .executes(
                                  c ->
                                      edit(
                                          c,
                                          d -> {
                                            String id = text(c, "rank"),
                                                player = targetId(c).toString();
                                            require(d, id);
                                            if (action.equals("add"))
                                              d.players
                                                  .computeIfAbsent(player, k -> new TreeSet<>())
                                                  .add(id);
                                            else {
                                              Set<String> assigned = d.players.get(player);
                                              if (assigned != null) {
                                                assigned.remove(id);
                                                if (assigned.isEmpty()) d.players.remove(player);
                                              }
                                            }
                                          })))));
    root.then(
        literal("player")
            .then(
                target()
                    .executes(
                        c ->
                            run(
                                c,
                                () -> {
                                  UUID id = targetId(c);
                                  tell(
                                      c,
                                      id
                                          + " assigned="
                                          + app()
                                              .ranks
                                              .copy()
                                              .players
                                              .getOrDefault(id.toString(), Set.of())
                                          + " active="
                                          + app().ranks.active(id));
                                }))));
    var parent = literal("parent");
    for (String action : List.of("add", "remove"))
      parent.then(
          literal(action)
              .then(
                  rank()
                      .then(
                          word("parent")
                              .suggests(
                                  (c, b) -> SharedSuggestionProvider.suggest(app().ranks.ids(), b))
                              .executes(
                                  c ->
                                      edit(
                                          c,
                                          d -> {
                                            Ranks.Rank rank = require(d, text(c, "rank"));
                                            String id = text(c, "parent");
                                            require(d, id);
                                            if (action.equals("add")) rank.parents.add(id);
                                            else rank.parents.remove(id);
                                          })))));
    root.then(parent);
    var node = literal("node");
    node.then(
        literal("add")
            .then(
                rank()
                    .then(
                        permission()
                            .then(
                                argument("value", StringArgumentType.string())
                                    .suggests(
                                        (c, b) ->
                                            SharedSuggestionProvider.suggest(
                                                List.of("true", "false"), b))
                                    .executes(
                                        c ->
                                            edit(
                                                c,
                                                d -> {
                                                  String key = text(c, "permission");
                                                  Ranks.checkNode(key, true);
                                                  require(d, text(c, "rank"))
                                                      .permissions
                                                      .put(
                                                          key,
                                                          ChatStyles.rankValue(
                                                              key, text(c, "value")));
                                                }))))));
    node.then(
        literal("remove")
            .then(
                rank()
                    .then(
                        permission()
                            .executes(
                                c ->
                                    edit(
                                        c,
                                        d ->
                                            require(d, text(c, "rank"))
                                                .permissions
                                                .remove(text(c, "permission")))))));
    node.then(
        literal("get")
            .then(
                rank()
                    .then(
                        permission()
                            .executes(
                                c ->
                                    run(
                                        c,
                                        () ->
                                            tell(
                                                c,
                                                String.valueOf(
                                                    require(app().ranks.copy(), text(c, "rank"))
                                                        .permissions
                                                        .get(text(c, "permission")))))))));
    node.then(
        literal("list")
            .then(
                rank()
                    .executes(
                        c ->
                            run(
                                c,
                                () ->
                                    tell(
                                        c,
                                        require(app().ranks.copy(), text(c, "rank"))
                                            .permissions
                                            .toString())))));
    root.then(node);
    root.then(
        literal("check")
            .then(
                target()
                    .then(
                        permission()
                            .executes(
                                c ->
                                    run(
                                        c,
                                        () -> {
                                          UUID id = targetId(c);
                                          String key = text(c, "permission");
                                          Ranks.checkNode(key, false);
                                          var player = app().server.getPlayerList().getPlayer(id);
                                          if (player != null
                                              && !Vanish.canSee(c.getSource(), player))
                                            player = null;
                                          String result =
                                              "local="
                                                  + app()
                                                      .ranks
                                                      .value(id, key)
                                                      .map(Object::toString)
                                                      .orElse("unset");
                                          if (player != null) {
                                            result +=
                                                " effectiveBoolean="
                                                    + app()
                                                        .compat
                                                        .decision(player, key)
                                                        .map(Object::toString)
                                                        .orElse("unset");
                                            if (key.equals("ffa.homes.maximum"))
                                              result +=
                                                  " effectiveHomeLimit="
                                                      + app()
                                                          .compat
                                                          .homeLimit(
                                                              player, app().settings.homeMax);
                                          } else
                                            result += " (offline: external providers not queried)";
                                          tell(c, id + " " + key + " " + result);
                                        })))));
    root.then(
        literal("reload")
            .executes(
                c ->
                    run(
                        c,
                        () -> {
                          app().ranks.reload();
                          app().refreshPermissions();
                          Commands.feedback(
                              app(),
                              c.getSource(),
                              c.getInput(),
                              app().ranks.file.toString(),
                              "SUCCESS ranks validated and reloaded");
                        })));
    root.then(
        literal("list_players_with")
            .then(
                rank()
                    .executes(
                        c ->
                            run(
                                c,
                                () -> {
                                  String rank = text(c, "rank");
                                  Ranks.Data data = app().ranks.copy();
                                  require(data, rank);
                                  Set<String> assigned = new TreeSet<>(), online = new TreeSet<>();
                                  data.players.forEach(
                                      (id, ids) -> {
                                        if (ids.contains(rank)) assigned.add(id);
                                      });
                                  for (var player : app().server.getPlayerList().getPlayers())
                                    if (Vanish.canSee(c.getSource(), player)
                                        && app().ranks.active(player.getUUID()).contains(rank))
                                      online.add(player.getGameProfile().getName());
                                  tell(
                                      c,
                                      rank
                                          + " assignedUUIDs="
                                          + assigned
                                          + " activeOnline="
                                          + online
                                          + " default="
                                          + data.ranks.get(rank).defaultRank);
                                }))));
    root.then(
        literal("refresh_readme")
            .executes(
                c ->
                    run(
                        c,
                        () -> {
                          var file = app().storage.directory.resolve("ranks-readme.txt");
                          String text =
                              "FFA built-in ranks. Manage with /ffa ranks.\n"
                                  + "Wiki:"
                                  + " https://github.com/ItsRileyNJ/Fantasy-Feudalism-Administration/blob/main/wiki/Ranks.md\n"
                                  + "Registered command permission paths (FFA uses its ffa.* nodes"
                                  + " instead):\n"
                                  + String.join("\n", app().commandPermissions.nodes())
                                  + "\n";
                          Storage.atomic(file, text);
                          Commands.feedback(
                              app(),
                              c.getSource(),
                              c.getInput(),
                              file.toString(),
                              "SUCCESS command permission reference saved");
                        })));
    var built = root.build();
    for (var alias :
        Map.of("list_all_ranks", "list", "list_ranks_of", "player", "show_rank", "info")
            .entrySet()) {
      var target = built.getChild(alias.getValue());
      var builder = literal(alias.getKey()).redirect(target);
      if (target.getCommand() != null) builder.executes(target.getCommand());
      built.addChild(builder.build());
    }
    return built;
  }

  private static int create(CommandContext<CommandSourceStack> c, int power) {
    return edit(
        c,
        d -> {
          String id = text(c, "rank");
          Regions.checkId(id);
          if (d.ranks.containsKey(id)) throw new IllegalArgumentException("Rank already exists");
          Ranks.Rank rank = new Ranks.Rank();
          rank.power = power;
          d.ranks.put(id, rank);
        });
  }

  private static String describeRank(String name, Ranks.Rank rank) {
    return "Rank: "
        + name
        + ". Priority: "
        + rank.power
        + ". Default: "
        + (rank.defaultRank ? "yes" : "no")
        + ". Inherits: "
        + (rank.parents.isEmpty() ? "none" : String.join(", ", rank.parents))
        + ". Permission rules: "
        + rank.permissions.size()
        + ".";
  }

  private static Administration app() {
    return Administration.INSTANCE;
  }

  private static boolean allowed(CommandSourceStack s) {
    var a = app();
    return a != null
        && a.settings != null
        && (s.hasPermission(a.settings.operatorLevel)
            || s.getPlayer() != null
                && a.compat != null
                && (a.compat.admin(s.getPlayer())
                    || a.compat.permission(s.getPlayer(), "ffa.ranks.manage")));
  }

  private static RequiredArgumentBuilder<CommandSourceStack, String> word(String name) {
    return argument(name, StringArgumentType.word());
  }

  private static RequiredArgumentBuilder<CommandSourceStack, String> rank() {
    return word("rank").suggests((c, b) -> SharedSuggestionProvider.suggest(app().ranks.ids(), b));
  }

  static RequiredArgumentBuilder<CommandSourceStack, String> target() {
    return word("player")
        .suggests(
            (c, b) ->
                SharedSuggestionProvider.suggest(
                    app().server.getPlayerList().getPlayers().stream()
                        .filter(p -> Vanish.canSee(c.getSource(), p))
                        .map(p -> p.getGameProfile().getName()),
                    b));
  }

  private static RequiredArgumentBuilder<CommandSourceStack, String> permission() {
    return argument("permission", StringArgumentType.string())
        .suggests(
            (c, b) -> {
              Set<String> keys = new TreeSet<>(PermissionCatalog.common());
              for (var rank : app().ranks.copy().ranks.values())
                keys.addAll(rank.permissions.keySet());
              String prefix = b.getRemaining().toLowerCase(Locale.ROOT).replace("\"", "");
              if (prefix.startsWith("command.")) keys.addAll(app().commandPermissions.nodes());
              for (String key : keys)
                if (key.toLowerCase(Locale.ROOT).startsWith(prefix))
                  b.suggest(
                      key.contains("*") || key.contains(":") ? "\"" + key + "\"" : key,
                      new com.mojang.brigadier.LiteralMessage(PermissionCatalog.description(key)));
              return b.buildFuture();
            });
  }

  private static String text(CommandContext<CommandSourceStack> c, String key) {
    return StringArgumentType.getString(c, key);
  }

  static UUID targetId(CommandContext<CommandSourceStack> c) {
    String name = text(c, "player");
    var player = app().server.getPlayerList().getPlayerByName(name);
    if (player != null && Vanish.canSee(c.getSource(), player)) return player.getUUID();
    try {
      UUID id = UUID.fromString(name);
      if (!id.toString().equals(name)) throw new IllegalArgumentException();
      return id;
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Use an online player name or a full lowercase UUID for an offline player");
    }
  }

  private static Ranks.Rank require(Ranks.Data data, String id) {
    var rank = data.ranks.get(id);
    if (rank == null) throw new IllegalArgumentException("Unknown rank: " + id);
    return rank;
  }

  private static int edit(
      CommandContext<CommandSourceStack> c, java.util.function.Consumer<Ranks.Data> edit) {
    return run(
        c,
        () -> {
          app().ranks.update(edit);
          app().refreshPermissions();
          Commands.feedback(
              app(),
              c.getSource(),
              c.getInput(),
              app().ranks.file.toString(),
              "SUCCESS ranks persisted");
        });
  }

  private static void tell(CommandContext<CommandSourceStack> c, String message) {
    c.getSource().sendSuccess(() -> Component.literal(message), false);
  }

  private interface Action {
    void run() throws Exception;
  }

  private static int run(CommandContext<CommandSourceStack> c, Action action) {
    try {
      if (app().server == null || app().ranks == null)
        throw new IllegalArgumentException("Administration is still starting");
      if (!allowed(c.getSource())) throw new IllegalArgumentException("Permission denied");
      action.run();
      return 1;
    } catch (Exception e) {
      c.getSource()
          .sendFailure(
              Component.literal(
                  e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
      if (app().storage != null)
        app()
            .storage
            .audit(
                app().settings,
                c.getSource().getTextName(),
                c.getInput(),
                "ranks",
                "FAILED " + e.getMessage());
      return 0;
    }
  }
}
