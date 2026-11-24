package dev.fantasyfeudalism.administration;

import static net.minecraft.commands.Commands.*;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;

final class MannequinCommands {
  private MannequinCommands() {}

  static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
    var tree = literal("mannequin").requires(StaffCommands::admin);
    tree.executes(c -> list(c));
    tree.then(literal("list").executes(c -> list(c)));
    tree.then(
        literal("create")
            .then(
                argument("name", StringArgumentType.word())
                    .executes(
                        c ->
                            run(
                                c,
                                () ->
                                    app()
                                        .mannequins
                                        .create(
                                            c.getSource(),
                                            text(c, "name"),
                                            c.getSource()
                                                .getPlayerOrException()
                                                .getGameProfile()
                                                .getName())))
                    .then(
                        account()
                            .executes(
                                c ->
                                    run(
                                        c,
                                        () ->
                                            app()
                                                .mannequins
                                                .create(
                                                    c.getSource(),
                                                    text(c, "name"),
                                                    text(c, "player")))))));
    tree.then(
        literal("delete")
            .then(
                display()
                    .executes(
                        c ->
                            run(
                                c,
                                () -> {
                                  app().mannequins.delete(text(c, "display"));
                                  say(c, "Mannequin removed.");
                                }))));
    tree.then(
        literal("move")
            .then(
                display()
                    .executes(
                        c ->
                            run(
                                c,
                                () -> {
                                  var point = Teleports.point(c.getSource().getPlayerOrException());
                                  app().mannequins.edit(text(c, "display"), m -> m.point = point);
                                  say(c, "Mannequin moved.");
                                }))));
    tree.then(
        literal("face")
            .then(
                display()
                    .executes(
                        c ->
                            run(
                                c,
                                () -> {
                                  var player = c.getSource().getPlayerOrException();
                                  app()
                                      .mannequins
                                      .edit(
                                          text(c, "display"),
                                          m -> {
                                            m.point.yaw = player.getYRot();
                                            m.point.pitch = player.getXRot();
                                          });
                                  say(c, "Mannequin rotation updated.");
                                }))));
    tree.then(
        literal("skin")
            .then(
                display()
                    .then(
                        account()
                            .executes(
                                c ->
                                    run(
                                        c,
                                        () ->
                                            app()
                                                .mannequins
                                                .skin(
                                                    c.getSource(),
                                                    text(c, "display"),
                                                    text(c, "player")))))));
    tree.then(
        literal("equipment")
            .then(
                display()
                    .then(
                        literal("clear")
                            .executes(
                                c ->
                                    run(
                                        c,
                                        () -> {
                                          app()
                                              .mannequins
                                              .edit(text(c, "display"), m -> m.equipment.clear());
                                          say(c, "Display equipment cleared.");
                                        })))
                    .then(
                        account()
                            .executes(
                                c ->
                                    run(
                                        c,
                                        () -> {
                                          var player =
                                              app()
                                                  .server
                                                  .getPlayerList()
                                                  .getPlayerByName(text(c, "player"));
                                          if (player == null)
                                            throw new IllegalArgumentException(
                                                "That player must be online to copy equipment.");
                                          app().mannequins.equipment(text(c, "display"), player);
                                          say(c, "Display equipment copied.");
                                        })))));
    tree.then(
        literal("label")
            .then(
                display()
                    .then(
                        argument("visible", BoolArgumentType.bool())
                            .executes(
                                c ->
                                    run(
                                        c,
                                        () -> {
                                          boolean visible = BoolArgumentType.getBool(c, "visible");
                                          app()
                                              .mannequins
                                              .edit(text(c, "display"), m -> m.label = visible);
                                          say(c, "Display label updated.");
                                        })))));
    tree.then(
        literal("text")
            .then(
                display()
                    .then(
                        literal("reset")
                            .executes(
                                c ->
                                    run(
                                        c,
                                        () -> {
                                          app()
                                              .mannequins
                                              .edit(text(c, "display"), m -> m.text.clear());
                                          say(c, "Display text reset.");
                                        })))
                    .then(
                        argument("property", StringArgumentType.word())
                            .suggests(
                                (c, b) ->
                                    SharedSuggestionProvider.suggest(ChatStyles.PROPERTIES, b))
                            .then(
                                argument("value", StringArgumentType.string())
                                    .suggests(
                                        (c, b) -> {
                                          String key = text(c, "property");
                                          return SharedSuggestionProvider.suggest(
                                              key.endsWith("_color")
                                                  ? ChatStyles.COLORS
                                                  : key.endsWith("_bold")
                                                          || key.endsWith("_italic")
                                                          || key.endsWith("_underlined")
                                                      ? List.of("true", "false")
                                                      : List.<String>of(),
                                              b);
                                        })
                                    .executes(
                                        c ->
                                            run(
                                                c,
                                                () -> {
                                                  String key = text(c, "property"),
                                                      value =
                                                          ChatStyles.validate(
                                                              key, text(c, "value"));
                                                  app()
                                                      .mannequins
                                                      .edit(
                                                          text(c, "display"),
                                                          m -> m.text.put(key, value));
                                                  say(c, "Display text updated.");
                                                }))))));
    tree.then(
        literal("info")
            .then(
                display()
                    .executes(
                        c ->
                            run(
                                c,
                                () -> {
                                  var m = app().mannequins.get(text(c, "display"));
                                  say(c, m.id + " | " + m.accountName + " | " + m.point.describe());
                                }))));
    var node = tree.build();
    root.then(node);
    root.then(
        literal("hologram")
            .requires(StaffCommands::admin)
            .executes(MannequinCommands::list)
            .redirect(node));
  }

  private static RequiredArgumentBuilder<CommandSourceStack, String> display() {
    return argument("display", StringArgumentType.word())
        .suggests(
            (c, b) ->
                SharedSuggestionProvider.suggest(app().storage.state().mannequins.keySet(), b));
  }

  private static RequiredArgumentBuilder<CommandSourceStack, String> account() {
    return argument("player", StringArgumentType.word())
        .suggests(
            (c, b) ->
                SharedSuggestionProvider.suggest(
                    app().server.getPlayerList().getPlayers().stream()
                        .filter(p -> Vanish.canSee(c.getSource(), p))
                        .map(p -> p.getGameProfile().getName()),
                    b));
  }

  private static int list(CommandContext<CommandSourceStack> c) {
    return run(
        c,
        () ->
            say(
                c,
                app().storage.state().mannequins.isEmpty()
                    ? "No mannequins yet."
                    : "Mannequins: "
                        + String.join(", ", app().storage.state().mannequins.keySet())));
  }

  private static Administration app() {
    return Administration.INSTANCE;
  }

  private static String text(CommandContext<CommandSourceStack> c, String key) {
    return StringArgumentType.getString(c, key);
  }

  private static void say(CommandContext<CommandSourceStack> c, String message) {
    c.getSource().sendSuccess(() -> Component.literal(message), false);
  }

  private interface Action {
    void run() throws Exception;
  }

  private static int run(CommandContext<CommandSourceStack> c, Action action) {
    try {
      if (app().server == null || app().mannequins == null)
        throw new IllegalArgumentException("Administration is still starting.");
      if (!StaffCommands.admin(c.getSource()))
        throw new IllegalArgumentException("Administrator only.");
      action.run();
      app()
          .storage
          .audit(app().settings, c.getSource().getTextName(), c.getInput(), "mannequin", "SUCCESS");
      return 1;
    } catch (Exception ex) {
      c.getSource()
          .sendFailure(
              Component.literal(
                  ex.getMessage() == null ? "Could not update mannequin." : ex.getMessage()));
      return 0;
    }
  }
}
