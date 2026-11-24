package dev.fantasyfeudalism.administration;

import static net.minecraft.commands.Commands.*;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;

final class SoundCommands {
  static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
    var sounds = literal("sound").requires(StaffCommands::admin);
    sounds.then(
        literal("reload")
            .executes(
                c ->
                    run(
                        c,
                        () -> {
                          app().soundPack.reload();
                          say(c, "Sound files reloaded.");
                        })));
    sounds.then(
        literal("list")
            .executes(
                c ->
                    run(
                        c,
                        () ->
                            say(
                                c,
                                "Sound tracks: "
                                    + String.join(
                                        ", ",
                                        app().soundPack.snapshot.tracks().keySet().stream()
                                            .sorted()
                                            .toList())))));
    root.then(sounds);
    var regions = literal("soundregion").requires(StaffCommands::admin);
    regions.then(
        literal("create")
            .then(
                argument("name", StringArgumentType.word())
                    .then(
                        track()
                            .executes(
                                c ->
                                    run(
                                        c,
                                        () -> {
                                          app()
                                              .soundRegions
                                              .create(
                                                  c.getSource().getPlayerOrException(),
                                                  text(c, "name"),
                                                  text(c, "track"));
                                          say(c, "Sound region created.");
                                        })))));
    regions.then(
        literal("list")
            .executes(
                c ->
                    run(
                        c,
                        () ->
                            say(
                                c,
                                "Sound regions: "
                                    + String.join(
                                        ", ", app().storage.state().soundRegions.keySet())))));
    regions.then(
        literal("delete")
            .then(
                region()
                    .executes(
                        c ->
                            run(
                                c,
                                () -> {
                                  String id = app().soundRegions.get(text(c, "region")).id;
                                  app().storage.update(s -> s.soundRegions.remove(id));
                                  say(c, "Sound region deleted.");
                                }))));
    regions.then(
        literal("info")
            .then(
                region()
                    .executes(
                        c ->
                            run(
                                c,
                                () -> {
                                  var r = app().soundRegions.get(text(c, "region"));
                                  say(
                                      c,
                                      r.id
                                          + ": "
                                          + r.track
                                          + ", volume "
                                          + r.volume
                                          + ", fade "
                                          + r.fadeTicks / 20.0
                                          + "s, priority "
                                          + r.priority
                                          + ", "
                                          + r.dimension
                                          + " ["
                                          + r.minX
                                          + ", "
                                          + r.minZ
                                          + "] to ["
                                          + r.maxX
                                          + ", "
                                          + r.maxZ
                                          + "].");
                                }))));
    regions.then(
        literal("track")
            .then(
                region()
                    .then(
                        track()
                            .executes(
                                c ->
                                    run(
                                        c,
                                        () -> {
                                          String name = text(c, "track");
                                          if (!app().soundPack.snapshot.tracks().containsKey(name))
                                            throw new IllegalArgumentException(
                                                "Unknown sound track.");
                                          String id = app().soundRegions.get(text(c, "region")).id;
                                          app()
                                              .storage
                                              .update(s -> s.soundRegions.get(id).track = name);
                                          say(c, "Sound track updated.");
                                        })))));
    regions.then(
        literal("volume")
            .then(
                region()
                    .then(
                        argument("volume", FloatArgumentType.floatArg(0, 1))
                            .executes(
                                c ->
                                    run(
                                        c,
                                        () -> {
                                          String id = app().soundRegions.get(text(c, "region")).id;
                                          float value = FloatArgumentType.getFloat(c, "volume");
                                          app()
                                              .storage
                                              .update(s -> s.soundRegions.get(id).volume = value);
                                          say(c, "Sound volume updated.");
                                        })))));
    regions.then(
        literal("fade")
            .then(
                region()
                    .then(
                        argument("seconds", IntegerArgumentType.integer(0, 30))
                            .executes(
                                c ->
                                    run(
                                        c,
                                        () -> {
                                          String id = app().soundRegions.get(text(c, "region")).id;
                                          int ticks =
                                              IntegerArgumentType.getInteger(c, "seconds") * 20;
                                          app()
                                              .storage
                                              .update(
                                                  s -> s.soundRegions.get(id).fadeTicks = ticks);
                                          say(c, "Sound fade updated.");
                                        })))));
    regions.then(
        literal("priority")
            .then(
                region()
                    .then(
                        argument("priority", IntegerArgumentType.integer(-1000000, 1000000))
                            .executes(
                                c ->
                                    run(
                                        c,
                                        () -> {
                                          String id = app().soundRegions.get(text(c, "region")).id;
                                          int priority =
                                              IntegerArgumentType.getInteger(c, "priority");
                                          app()
                                              .storage
                                              .update(
                                                  s -> s.soundRegions.get(id).priority = priority);
                                          say(c, "Sound priority updated.");
                                        })))));
    root.then(regions);
  }

  private static RequiredArgumentBuilder<CommandSourceStack, String> region() {
    return argument("region", StringArgumentType.word())
        .suggests(
            (c, b) ->
                SharedSuggestionProvider.suggest(app().storage.state().soundRegions.keySet(), b));
  }

  private static RequiredArgumentBuilder<CommandSourceStack, String> track() {
    return argument("track", StringArgumentType.word())
        .suggests(
            (c, b) ->
                SharedSuggestionProvider.suggest(app().soundPack.snapshot.tracks().keySet(), b));
  }

  private static String text(CommandContext<CommandSourceStack> c, String key) {
    return StringArgumentType.getString(c, key);
  }

  private static Administration app() {
    return Administration.INSTANCE;
  }

  private static void say(CommandContext<CommandSourceStack> c, String text) {
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
                  ex.getMessage() == null ? "Sound command failed." : ex.getMessage()));
      return 0;
    }
  }
}
