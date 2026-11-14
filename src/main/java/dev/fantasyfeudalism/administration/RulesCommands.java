package dev.fantasyfeudalism.administration;

import static net.minecraft.commands.Commands.*;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;

final class RulesCommands {
  private RulesCommands() {}

  static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
    root.then(
        literal("setfirstspawn")
            .requires(StaffCommands::admin)
            .executes(
                c ->
                    run(
                        c,
                        () -> {
                          app().rules.setSpawn(c.getSource().getPlayerOrException());
                          tell(c, "First-join spawn set.");
                        })));
    var rules =
        literal("rules")
            .executes(
                c ->
                    run(
                        c,
                        () -> {
                          var p = c.getSource().getPlayerOrException();
                          if (app().rules.pending(p)) app().rules.prompt(p, true);
                          else tell(c, "Rules accepted.");
                        }));
    for (boolean accept : new boolean[] {true, false}) {
      var answer =
          literal(accept ? "accept" : "deny")
              .executes(
                  c ->
                      run(
                          c,
                          () ->
                              app()
                                  .rules
                                  .answer(c.getSource().getPlayerOrException(), accept, null)))
              .then(
                  argument("request", StringArgumentType.word())
                      .executes(
                          c ->
                              run(
                                  c,
                                  () ->
                                      app()
                                          .rules
                                          .answer(
                                              c.getSource().getPlayerOrException(),
                                              accept,
                                              StringArgumentType.getString(c, "request")))));
      rules.then(answer);
    }
    rules.then(
        literal("region")
            .requires(StaffCommands::admin)
            .then(
                argument("region", StringArgumentType.word())
                    .suggests(
                        (c, b) ->
                            SharedSuggestionProvider.suggest(
                                app().regions.all().stream()
                                    .filter(r -> r.kind == Model.Kind.CHILD)
                                    .map(r -> r.id),
                                b))
                    .executes(
                        c ->
                            run(
                                c,
                                () -> {
                                  app().rules.setRegion(StringArgumentType.getString(c, "region"));
                                  tell(c, "Rules room set.");
                                }))));
    for (boolean enabled : new boolean[] {true, false})
      rules.then(
          literal(enabled ? "enable" : "disable")
              .requires(StaffCommands::admin)
              .executes(
                  c ->
                      run(
                          c,
                          () -> {
                            app().rules.enable(enabled);
                            tell(c, enabled ? "Rules room enabled." : "Rules room disabled.");
                          })));
    rules.then(
        literal("status")
            .requires(StaffCommands::admin)
            .executes(
                c ->
                    run(
                        c,
                        () -> {
                          var state = app().storage.state();
                          tell(
                              c,
                              "Rules room: "
                                  + (state.rulesRoom.enabled ? "enabled" : "disabled")
                                  + "; region: "
                                  + (state.rulesRoom.region.isEmpty()
                                      ? "not set"
                                      : state.rulesRoom.region)
                                  + "; first-join spawn: "
                                  + (state.firstSpawn == null
                                      ? "not set"
                                      : state.firstSpawn.describe())
                                  + ".");
                        })));
    rules.then(
        literal("message")
            .requires(StaffCommands::admin)
            .then(
                argument("message", StringArgumentType.greedyString())
                    .executes(
                        c ->
                            run(
                                c,
                                () -> {
                                  String text = StringArgumentType.getString(c, "message");
                                  if (text.isBlank()
                                      || text.length() > 2000
                                      || text.codePoints().anyMatch(Character::isISOControl))
                                    throw new IllegalArgumentException(
                                        "Use 1–2000 characters of plain text.");
                                  app().storage.update(s -> s.rulesRoom.message = text);
                                  tell(c, "Rules reminder updated.");
                                }))));
    var style = literal("style").requires(StaffCommands::admin);
    style.then(
        literal("color")
            .then(
                argument("color", StringArgumentType.string())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(ChatStyles.COLORS, b))
                    .executes(
                        c ->
                            run(
                                c,
                                () -> {
                                  String value = StringArgumentType.getString(c, "color");
                                  RegionTitles.color(value);
                                  app().storage.update(s -> s.rulesRoom.color = value);
                                  tell(c, "Rules text style updated.");
                                }))));
    for (String property : java.util.List.of("bold", "italic", "underline"))
      style.then(
          literal(property)
              .then(
                  argument("value", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                      .executes(
                          c ->
                              run(
                                  c,
                                  () -> {
                                    boolean value =
                                        com.mojang.brigadier.arguments.BoolArgumentType.getBool(
                                            c, "value");
                                    app()
                                        .storage
                                        .update(
                                            s -> {
                                              switch (property) {
                                                case "bold" -> s.rulesRoom.bold = value;
                                                case "italic" -> s.rulesRoom.italic = value;
                                                default -> s.rulesRoom.underlined = value;
                                              }
                                            });
                                    tell(c, "Rules text style updated.");
                                  }))));
    rules.then(style);
    root.then(rules);
  }

  private static Administration app() {
    return Administration.INSTANCE;
  }

  private static void tell(CommandContext<CommandSourceStack> c, String text) {
    c.getSource().sendSuccess(() -> Component.literal(text), false);
  }

  private interface Action {
    void run() throws Exception;
  }

  private static int run(CommandContext<CommandSourceStack> c, Action action) {
    try {
      action.run();
      return 1;
    } catch (Exception ex) {
      c.getSource()
          .sendFailure(
              Component.literal(
                  ex.getMessage() == null ? "Unable to complete rules command." : ex.getMessage()));
      return 0;
    }
  }
}
