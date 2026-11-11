package dev.fantasyfeudalism.administration;

import static net.minecraft.commands.Commands.*;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

final class StaffCommands {
  private StaffCommands() {}

  static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
    root.then(
        literal("vanish")
            .requires(StaffCommands::admin)
            .executes(
                c ->
                    run(
                        c,
                        () -> {
                          requireAdmin(c);
                          app().vanish.toggle(playerSource(c));
                        })));
    root.then(
        literal("invisee")
            .requires(StaffCommands::admin)
            .then(
                target()
                    .executes(
                        c ->
                            run(
                                c,
                                () -> {
                                  requireAdmin(c);
                                  InspectionMenu.open(app(), playerSource(c), targetPlayer(c));
                                }))));
    root.then(
        literal("stats")
            .requires(s -> allowed(s, "stats"))
            .then(
                target()
                    .executes(
                        c ->
                            run(
                                c,
                                () -> {
                                  if (!allowed(c.getSource(), "stats"))
                                    throw new IllegalArgumentException("Permission denied");
                                  ServerPlayer p = targetPlayer(c);
                                  double days =
                                      p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME))
                                          / 1728000.0;
                                  var money = app().storage.player(p.getUUID());
                                  tell(
                                      c,
                                      p.getGameProfile().getName()
                                          + " | Days played: "
                                          + String.format(Locale.ROOT, "%.2f", days)
                                          + " | Deaths: "
                                          + p.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS))
                                          + " | Player kills: "
                                          + p.getStats()
                                              .getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS))
                                          + " | Money: "
                                          + (money == null ? 0 : money.balance)
                                          + " "
                                          + app().settings.currency
                                          + " | Settlements: "
                                          + app().compat.settlementNames(p)
                                          + " | Ranks: "
                                          + app().compat.rankNames(p));
                                }))));
    root.then(
        literal("whisper")
            .requires(s -> allowed(s, "whisper"))
            .then(
                target()
                    .then(
                        argument("message", StringArgumentType.greedyString())
                            .executes(
                                c ->
                                    run(
                                        c,
                                        () -> {
                                          if (!allowed(c.getSource(), "whisper"))
                                            throw new IllegalArgumentException("Permission denied");
                                          app()
                                              .whispers
                                              .send(
                                                  playerSource(c),
                                                  targetPlayer(c),
                                                  StringArgumentType.getString(c, "message"));
                                        })))));
    for (boolean accept : List.of(true, false)) {
      var action =
          literal(accept ? "whisperaccept" : "whisperdeny")
              .requires(
                  s ->
                      s.getPlayer() != null
                          && app().whispers != null
                          && app().whispers.waiting(s.getPlayer()))
              .executes(c -> run(c, () -> app().whispers.answer(playerSource(c), accept, null)))
              .then(
                  argument("request", StringArgumentType.word())
                      .executes(
                          c ->
                              run(
                                  c,
                                  () ->
                                      app()
                                          .whispers
                                          .answer(
                                              playerSource(c),
                                              accept,
                                              UUID.fromString(
                                                  StringArgumentType.getString(c, "request"))))));
      root.then(action);
    }
    root.then(
        literal("whisperforget")
            .requires(s -> allowed(s, "whisper"))
            .then(
                RankCommands.target()
                    .executes(
                        c ->
                            run(
                                c,
                                () -> {
                                  if (!allowed(c.getSource(), "whisper"))
                                    throw new IllegalArgumentException("Permission denied");
                                  app().whispers.forget(playerSource(c), RankCommands.targetId(c));
                                }))));
    var monitor = literal("whispers").requires(StaffCommands::admin);
    for (boolean enabled : List.of(true, false))
      monitor.then(
          literal(enabled ? "on" : "off")
              .executes(
                  c ->
                      run(
                          c,
                          () -> {
                            requireAdmin(c);
                            app().whispers.monitorPreference(playerSource(c), enabled);
                          })));
    root.then(monitor);
  }

  static boolean admin(CommandSourceStack s) {
    var app = app();
    return app != null
        && app.settings != null
        && (s.hasPermission(app.settings.operatorLevel)
            || s.getPlayer() != null && app.compat != null && app.compat.admin(s.getPlayer()));
  }

  private static boolean allowed(CommandSourceStack s, String family) {
    return admin(s)
        || s.getPlayer() != null
            && app().compat != null
            && app()
                .compat
                .decision(s.getPlayer(), "ffa.command." + family)
                .orElse(app().settings.publicCommands);
  }

  private static Administration app() {
    return Administration.INSTANCE;
  }

  private static void requireAdmin(CommandContext<CommandSourceStack> c) {
    if (!admin(c.getSource())) throw new IllegalArgumentException("Administrator only");
  }

  private static ServerPlayer playerSource(CommandContext<CommandSourceStack> c) {
    if (c.getSource().getPlayer() == null)
      throw new IllegalArgumentException("This operation requires a player");
    return c.getSource().getPlayer();
  }

  private static RequiredArgumentBuilder<CommandSourceStack, String> target() {
    return argument("player", StringArgumentType.word())
        .suggests(
            (c, b) ->
                SharedSuggestionProvider.suggest(
                    c.getSource().getServer().getPlayerList().getPlayers().stream()
                        .filter(p -> Vanish.canSee(c.getSource(), p))
                        .map(p -> p.getGameProfile().getName()),
                    b));
  }

  private static ServerPlayer targetPlayer(CommandContext<CommandSourceStack> c) {
    ServerPlayer p =
        c.getSource()
            .getServer()
            .getPlayerList()
            .getPlayerByName(StringArgumentType.getString(c, "player"));
    if (p == null || !Vanish.canSee(c.getSource(), p))
      throw new IllegalArgumentException("Player is offline");
    return p;
  }

  private static void tell(CommandContext<CommandSourceStack> c, String text) {
    c.getSource().sendSuccess(() -> Component.literal(text), false);
  }

  private interface Action {
    void run() throws Exception;
  }

  private static int run(CommandContext<CommandSourceStack> c, Action action) {
    try {
      if (app().server == null)
        throw new IllegalArgumentException("Administration is still starting");
      action.run();
      return 1;
    } catch (Exception e) {
      c.getSource()
          .sendFailure(
              Component.literal(
                  e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
      return 0;
    }
  }
}
