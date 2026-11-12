package dev.fantasyfeudalism.administration;

import static net.minecraft.commands.Commands.*;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;

final class ModerationCommands {
  static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
    var punish = literal("punish").requires(StaffCommands::admin);
    for (String type : Moderation.TYPES) {
      var target = RankCommands.target().executes(c -> apply(c, type, null, ""));
      target.then(
          argument("duration", StringArgumentType.word())
              .executes(c -> apply(c, type, text(c, "duration"), ""))
              .then(
                  argument("reason", StringArgumentType.greedyString())
                      .executes(c -> apply(c, type, text(c, "duration"), text(c, "reason")))));
      punish.then(literal(type).then(target));
    }
    var clear = literal("clear");
    for (String type : Moderation.TYPES)
      clear.then(
          literal(type)
              .then(
                  RankCommands.target()
                      .executes(
                          c ->
                              run(
                                  c,
                                  () -> {
                                    app().moderation.clear(RankCommands.targetId(c), type);
                                    tell(c, "Punishment cleared.");
                                  }))));
    punish.then(clear);
    punish.then(
        literal("status")
            .then(
                RankCommands.target()
                    .executes(
                        c ->
                            run(
                                c,
                                () ->
                                    tell(c, app().moderation.status(RankCommands.targetId(c)))))));
    root.then(punish);
    var cell = literal("cell");
    cell.then(
        literal("set")
            .then(
                argument("name", StringArgumentType.word())
                    .executes(c -> cell(c, false))
                    .then(literal("override").executes(c -> cell(c, true)))));
    cell.then(
        literal("list")
            .executes(
                c ->
                    run(
                        c,
                        () ->
                            tell(
                                c,
                                "Jail cells: "
                                    + String.join(
                                        ", ", app().storage.state().jailCells.keySet())))));
    cell.then(
        literal("delete")
            .then(
                argument("name", StringArgumentType.word())
                    .suggests(
                        (c, b) ->
                            SharedSuggestionProvider.suggest(
                                app().storage.state().jailCells.keySet(), b))
                    .executes(
                        c ->
                            run(
                                c,
                                () -> {
                                  app().moderation.deleteCell(text(c, "name"));
                                  tell(c, "Jail cell deleted.");
                                }))));
    root.then(literal("jail").requires(StaffCommands::admin).then(cell));
  }

  private static Administration app() {
    return Administration.INSTANCE;
  }

  private static String text(CommandContext<CommandSourceStack> c, String key) {
    return StringArgumentType.getString(c, key);
  }

  private static int cell(CommandContext<CommandSourceStack> c, boolean override) {
    return run(
        c,
        () -> {
          if (c.getSource().getPlayer() == null)
            throw new IllegalArgumentException("Select the cell in game first.");
          app().moderation.setCell(c.getSource().getPlayer(), text(c, "name"), override);
        });
  }

  private static int apply(
      CommandContext<CommandSourceStack> c, String type, String duration, String reason) {
    return run(
        c,
        () -> {
          long seconds =
              duration == null
                  ? app().moderation.defaultSeconds(type)
                  : Moderation.seconds(duration, app().settings.maximumDurationSeconds);
          app().moderation.apply(RankCommands.targetId(c), type, seconds, reason);
          tell(
              c,
              type.equals("jail")
                  ? "Player jailed."
                  : type.equals("chatban") ? "Chat ban applied." : "Warp ban applied.");
        });
  }

  private static void tell(CommandContext<CommandSourceStack> c, String text) {
    c.getSource().sendSuccess(() -> Component.literal(text), false);
  }

  private interface Action {
    void run() throws Exception;
  }

  private static int run(CommandContext<CommandSourceStack> c, Action action) {
    try {
      if (!StaffCommands.admin(c.getSource()))
        throw new IllegalArgumentException("Administrator only.");
      action.run();
      app()
          .storage
          .audit(
              app().settings, c.getSource().getTextName(), c.getInput(), "moderation", "SUCCESS");
      return 1;
    } catch (Exception error) {
      c.getSource()
          .sendFailure(
              Component.literal(
                  error.getMessage() == null
                      ? "The operation could not be completed."
                      : error.getMessage()));
      return 0;
    }
  }
}
