package dev.fantasyfeudalism.administration;

import static net.minecraft.commands.Commands.*;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;

final class ChatCommands {
  private ChatCommands() {}

  static LiteralArgumentBuilder<CommandSourceStack> tree() {
    var root =
        literal("chat")
            .requires(ChatCommands::available)
            .executes(
                c ->
                    run(
                        c,
                        () ->
                            tell(
                                c,
                                "Available /ffa chat actions: "
                                    + app()
                                        .server
                                        .getCommands()
                                        .getDispatcher()
                                        .getRoot()
                                        .getChild("ffa")
                                        .getChild("chat")
                                        .getChildren()
                                        .stream()
                                        .filter(n -> n.canUse(c.getSource()))
                                        .map(n -> n.getName())
                                        .sorted()
                                        .collect(java.util.stream.Collectors.joining(", ")))));
    Map<String, String> actions =
        Map.of(
            "nickname",
            "nickname",
            "title",
            "title",
            "color",
            "name_color",
            "titlecolor",
            "title_color",
            "bold",
            "name_bold",
            "italic",
            "name_italic",
            "titlebold",
            "title_bold",
            "titleitalic",
            "title_italic",
            "underline",
            "name_underlined",
            "titleunderline",
            "title_underlined");
    actions.forEach(
        (action, key) ->
            root.then(
                literal(action)
                    .requires(s -> self(s, key))
                    .then(
                        value(key)
                            .executes(
                                c ->
                                    run(
                                        c,
                                        () -> {
                                          if (!self(c.getSource(), key)
                                              || c.getSource().getPlayer() == null)
                                            throw new IllegalArgumentException(
                                                "Permission denied or no player source");
                                          save(
                                              c,
                                              c.getSource().getPlayer().getUUID(),
                                              key,
                                              text(c, "value"),
                                              false);
                                        })))));
    root.then(
        literal("inherit")
            .requires(s -> s.getPlayer() != null)
            .then(
                property(false)
                    .executes(
                        c ->
                            run(
                                c,
                                () -> {
                                  String key = text(c, "property");
                                  if (!self(c.getSource(), key))
                                    throw new IllegalArgumentException("Permission denied");
                                  save(c, c.getSource().getPlayer().getUUID(), key, null, false);
                                }))));
    root.then(
        literal("view")
            .executes(
                c ->
                    run(
                        c,
                        () -> {
                          var player = c.getSource().getPlayer();
                          if (player == null)
                            throw new IllegalArgumentException(
                                "Specify a target player from console");
                          tell(c, ChatStyles.describe(app().chatStyles.effective(player)));
                        }))
            .then(
                RankCommands.target()
                    .requires(ChatCommands::manager)
                    .executes(
                        c ->
                            run(
                                c,
                                () -> {
                                  requireManager(c);
                                  UUID id = RankCommands.targetId(c);
                                  var data = app().storage.player(id);
                                  var player = app().server.getPlayerList().getPlayer(id);
                                  if (player != null && !Vanish.canSee(c.getSource(), player))
                                    player = null;
                                  tell(
                                      c,
                                      id
                                          + " personal="
                                          + (data == null ? Map.of() : data.chat)
                                          + (player == null
                                              ? " (offline)"
                                              : " effective="
                                                  + app().chatStyles.effective(player)));
                                }))));
    root.then(
        literal("set")
            .requires(ChatCommands::manager)
            .then(
                RankCommands.target()
                    .then(
                        property(true)
                            .then(
                                value(null)
                                    .executes(
                                        c ->
                                            run(
                                                c,
                                                () -> {
                                                  requireManager(c);
                                                  save(
                                                      c,
                                                      RankCommands.targetId(c),
                                                      text(c, "property"),
                                                      text(c, "value"),
                                                      false);
                                                }))))));
    root.then(
        literal("clear")
            .requires(ChatCommands::manager)
            .then(
                RankCommands.target()
                    .then(
                        property(true)
                            .executes(
                                c ->
                                    run(
                                        c,
                                        () -> {
                                          requireManager(c);
                                          save(
                                              c,
                                              RankCommands.targetId(c),
                                              text(c, "property"),
                                              null,
                                              false);
                                        })))));
    root.then(
        literal("reset")
            .requires(ChatCommands::manager)
            .then(
                RankCommands.target()
                    .executes(
                        c ->
                            run(
                                c,
                                () -> {
                                  requireManager(c);
                                  save(c, RankCommands.targetId(c), null, null, true);
                                }))));
    return root;
  }

  private static Administration app() {
    return Administration.INSTANCE;
  }

  private static boolean manager(CommandSourceStack s) {
    var app = app();
    return app != null
        && app.settings != null
        && (s.hasPermission(app.settings.operatorLevel)
            || s.getPlayer() != null
                && app.compat != null
                && (app.compat.admin(s.getPlayer())
                    || app.compat.permission(s.getPlayer(), "ffa.chat.manage")));
  }

  private static boolean self(CommandSourceStack s, String key) {
    return manager(s)
        || s.getPlayer() != null
            && app().compat != null
            && app().compat.permission(s.getPlayer(), ChatStyles.permission(key));
  }

  private static boolean available(CommandSourceStack s) {
    return manager(s) || ChatStyles.PROPERTIES.stream().anyMatch(k -> self(s, k));
  }

  private static RequiredArgumentBuilder<CommandSourceStack, String> property(boolean all) {
    return argument("property", StringArgumentType.word())
        .suggests(
            (c, b) ->
                SharedSuggestionProvider.suggest(
                    ChatStyles.PROPERTIES.stream().filter(k -> all || self(c.getSource(), k)), b));
  }

  private static RequiredArgumentBuilder<CommandSourceStack, String> value(String property) {
    return argument("value", StringArgumentType.string())
        .suggests(
            (c, b) -> {
              String key = property == null ? text(c, "property") : property;
              return SharedSuggestionProvider.suggest(
                  key.endsWith("_color")
                      ? ChatStyles.COLORS
                      : key.endsWith("_bold")
                              || key.endsWith("_italic")
                              || key.endsWith("_underlined")
                          ? List.of("true", "false")
                          : List.of(),
                  b);
            });
  }

  private static String text(CommandContext<CommandSourceStack> c, String key) {
    return StringArgumentType.getString(c, key);
  }

  private static void requireManager(CommandContext<CommandSourceStack> c) {
    if (!manager(c.getSource())) throw new IllegalArgumentException("Permission denied");
  }

  private static void save(
      CommandContext<CommandSourceStack> c, UUID id, String key, String value, boolean reset)
      throws Exception {
    if (!reset) {
      ChatStyles.permission(key);
      if (value != null) ChatStyles.validate(key, value);
    }
    app().storage.ensure(id, app().settings);
    app()
        .storage
        .update(
            s -> {
              var data = s.players.get(id.toString());
              if (data.chat == null) data.chat = new TreeMap<>();
              if (reset) data.chat.clear();
              else if (value == null) data.chat.remove(key);
              else data.chat.put(key, value);
            });
    var player = app().server.getPlayerList().getPlayer(id);
    if (player != null && !Vanish.canSee(c.getSource(), player)) player = null;
    if (player != null) player.refreshDisplayName();
    Commands.feedback(
        app(), c.getSource(), c.getInput(), "player=" + id, "SUCCESS chat style persisted");
  }

  private static void tell(CommandContext<CommandSourceStack> c, String message) {
    c.getSource().sendSuccess(() -> Component.literal(message), false);
  }

  private interface Action {
    void run() throws Exception;
  }

  private static int run(CommandContext<CommandSourceStack> c, Action action) {
    try {
      if (app().server == null || app().chatStyles == null)
        throw new IllegalArgumentException("Administration is still starting");
      if (!available(c.getSource())) throw new IllegalArgumentException("Permission denied");
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
                "chat",
                "FAILED " + e.getMessage());
      return 0;
    }
  }
}
