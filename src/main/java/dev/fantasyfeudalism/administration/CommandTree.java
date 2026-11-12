package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;
import static net.minecraft.commands.Commands.*;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;

/** Native command grammar. Execution continues through the existing server-side validators. */
final class CommandTree {
  private CommandTree() {}

  private enum Saved {
    REGION,
    PORTAL,
    WARP,
    MANAGED_WARP,
    HOME,
    SHOP
  }

  static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
    root.then(node("help"));
    root.then(RankCommands.tree());
    root.then(ChatCommands.tree());
    StaffCommands.addTo(root);
    ModerationCommands.addTo(root);
    root.then(
        node("spawn")
            .requires(s -> usable(s, "spawn"))
            .then(
                node("create").requires(s -> manages(s, "ffa.spawn.manage")).then(node("override")))
            .then(node("setpoint").requires(s -> manages(s, "ffa.spawn.manage"))));
    for (String position : List.of("pos1", "pos2"))
      root.then(node(position).requires(s -> manages(s, "ffa.region.edit")));

    var region = node("region").requires(s -> manages(s, "ffa.region.edit"));
    region.then(node("list"));
    region.then(
        node("create")
            .then(word("name").then(node("override")))
            .then(node("child").then(word("name").then(node("override"))))
            .then(node("standalone").then(word("name").then(node("override")))));
    region.then(node("child").then(word("name").then(node("override"))));
    region.then(node("standalone").then(word("name").then(node("override"))));
    region.then(node("delete").then(saved("region", Saved.REGION)));
    region.then(node("visualize").then(node("cancel")).then(saved("region", Saved.REGION)));
    for (String action : List.of("expand", "contract"))
      region.then(
          node(action)
              .then(
                  saved("region", Saved.REGION)
                      .then(arg("blocks", IntegerArgumentType.integer(1, 100000)))));
    region.then(
        node("flag")
            .then(
                saved("region", Saved.REGION)
                    .then(
                        word("flag")
                            .suggests(
                                (c, b) ->
                                    SharedSuggestionProvider.suggest(
                                        Arrays.stream(Flag.values())
                                            .map(f -> f.name().toLowerCase(Locale.ROOT)),
                                        b))
                            .then(choices("value", "true", "false", "inherit")))));
    region.then(
        node("message")
            .then(
                saved("region", Saved.REGION)
                    .then(node("entry").then(arg("message", StringArgumentType.string())))
                    .then(node("exit").then(arg("message", StringArgumentType.string())))));
    region.then(
        node("interaction")
            .then(
                saved("region", Saved.REGION)
                    .then(
                        arg("block", ResourceLocationArgument.id())
                            .suggests(
                                (c, b) ->
                                    SharedSuggestionProvider.suggestResource(
                                        BuiltInRegistries.BLOCK.keySet(), b))
                            .then(choices("action", "allow", "remove")))));
    region.then(
        node("item")
            .then(
                saved("region", Saved.REGION)
                    .then(
                        arg("item", ResourceLocationArgument.id())
                            .suggests(
                                (c, b) ->
                                    SharedSuggestionProvider.suggestResource(
                                        BuiltInRegistries.ITEM.keySet(), b))
                            .then(choices("action", "allow", "remove")))));
    var title = saved("region", Saved.REGION);
    for (String side : List.of("entry", "exit"))
      title.then(
          node(side)
              .then(
                  node("color")
                      .then(
                          arg("color", StringArgumentType.string())
                              .suggests(
                                  (c, b) ->
                                      SharedSuggestionProvider.suggest(ChatStyles.COLORS, b))))
              .then(node("bold").then(choices("value", "true", "false")))
              .then(node("italic").then(choices("value", "true", "false")))
              .then(node("underline").then(choices("value", "true", "false"))));
    region.then(node("title").then(title));
    region.then(node("access").then(withAccess(saved("region", Saved.REGION))));
    root.then(region);

    root.then(
        node("setwarp")
            .requires(s -> manages(s, "ffa.warp.manage"))
            .then(saved("name", Saved.MANAGED_WARP)));
    root.then(
        node("delwarp")
            .requires(s -> manages(s, "ffa.warp.manage"))
            .then(saved("warp", Saved.MANAGED_WARP)));
    var warp = saved("warp", Saved.WARP);
    withAccess(warp, "ffa.warp.manage");
    root.then(node("warp").requires(s -> usable(s, "warp")).then(warp));
    root.then(node("warps").requires(s -> usable(s, "warps")));

    var portal = node("portal").requires(s -> enabled("portal") && manages(s, "ffa.portal.manage"));
    portal.then(
        node("create")
            .requires(
                s ->
                    !Administration.INSTANCE.settings.adminPortalCreationOnly
                        || StaffCommands.admin(s))
            .then(word("name").then(saved("warp", Saved.MANAGED_WARP))));
    portal.then(node("delete").then(saved("portal", Saved.PORTAL)));
    portal.then(
        node("link").then(saved("portal", Saved.PORTAL).then(saved("warp", Saved.MANAGED_WARP))));
    portal.then(
        node("color")
            .then(
                saved("portal", Saved.PORTAL)
                    .then(
                        word("color")
                            .suggests(
                                (c, b) ->
                                    SharedSuggestionProvider.suggest(
                                        Arrays.stream(DyeColor.values()).map(DyeColor::getName),
                                        b)))));
    portal.then(node("access").then(withAccess(saved("portal", Saved.PORTAL))));
    root.then(portal);

    for (String command : List.of("home", "sethome", "delhome"))
      root.then(node(command).requires(s -> usable(s, command)).then(saved("home", Saved.HOME)));
    root.then(node("homes").requires(s -> usable(s, "homes")));
    for (String command : List.of("tpa", "tpahere", "pay", "tpignore")) {
      var target = onlinePlayer(true);
      if (command.equals("pay")) target.then(arg("amount", LongArgumentType.longArg(1)));
      root.then(node(command).requires(s -> usable(s, command)).then(target));
    }
    for (String command : List.of("tpaccept", "tpdeny"))
      root.then(node(command).requires(s -> usable(s, command)).then(word("request")));
    for (String command : List.of("tpcancel", "tptoggle", "back", "rtp", "baltop"))
      root.then(node(command).requires(s -> usable(s, command)));

    root.then(
        node("balance")
            .requires(
                s ->
                    enabled("balance")
                        && (permitted(s, "balance") || manages(s, "ffa.economy.manage")))
            .then(
                node("set")
                    .requires(s -> manages(s, "ffa.economy.manage"))
                    .then(onlinePlayer(false).then(arg("amount", LongArgumentType.longArg(0))))));
    var shop = node("shop").requires(s -> permitted(s, "shop"));
    shop.then(node("list"));
    for (String direction : List.of("buy", "sell"))
      shop.then(
          node(direction)
              .requires(s -> enabled("balance"))
              .then(
                  saved("shop", Saved.SHOP)
                      .then(arg("quantity", IntegerArgumentType.integer(1, 2304)))));
    shop.then(
        node("set")
            .requires(s -> manages(s, "ffa.shop.manage"))
            .then(
                word("name")
                    .then(
                        arg("item", ResourceLocationArgument.id())
                            .suggests(
                                (c, b) ->
                                    SharedSuggestionProvider.suggestResource(
                                        BuiltInRegistries.ITEM.keySet(), b))
                            .then(
                                arg("buyPrice", LongArgumentType.longArg(-1))
                                    .then(arg("sellPrice", LongArgumentType.longArg(-1)))))));
    shop.then(
        node("delete")
            .requires(s -> manages(s, "ffa.shop.manage"))
            .then(saved("shop", Saved.SHOP)));
    shop.then(
        node("sign")
            .requires(StaffCommands::admin)
            .then(
                node("bind")
                    .then(
                        saved("shop", Saved.SHOP)
                            .then(arg("quantity", IntegerArgumentType.integer(1, 2304)))))
            .then(node("remove")));
    root.then(shop);
    root.then(node("reload").requires(s -> manages(s, "ffa.reload")));
    root.then(node("backup").requires(s -> manages(s, "ffa.backup")));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> node(String name) {
    return literal(name).executes(CommandTree::run);
  }

  private static <T> RequiredArgumentBuilder<CommandSourceStack, T> arg(
      String name, ArgumentType<T> type) {
    return argument(name, type).executes(CommandTree::run);
  }

  private static RequiredArgumentBuilder<CommandSourceStack, String> word(String name) {
    return arg(name, StringArgumentType.word());
  }

  private static RequiredArgumentBuilder<CommandSourceStack, String> choices(
      String name, String... values) {
    return word(name).suggests((c, b) -> SharedSuggestionProvider.suggest(values, b));
  }

  private static int run(CommandContext<CommandSourceStack> context) {
    for (var parsed : context.getNodes())
      if (parsed.getNode().getName().equals("ffa"))
        return Commands.execute(
            context.getSource(),
            context.getInput().substring(parsed.getRange().getEnd()).stripLeading());
    throw new IllegalStateException("Missing ffa command context");
  }

  private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withAccess(T parent) {
    return withAccess(parent, null);
  }

  private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withAccess(
      T parent, String permission) {
    for (String mode : List.of("public", "admin", "permission", "group", "team")) {
      var option = node(mode);
      if (permission != null) option.requires(s -> manages(s, permission));
      if (Set.of("permission", "group", "team").contains(mode))
        option.then(arg("value", StringArgumentType.string()));
      parent.then(option);
    }
    return parent;
  }

  private static RequiredArgumentBuilder<CommandSourceStack, String> onlinePlayer(
      boolean excludeSelf) {
    return word("player")
        .suggests(
            (c, b) ->
                SharedSuggestionProvider.suggest(
                    c.getSource().getServer().getPlayerList().getPlayers().stream()
                        .filter(
                            p ->
                                (!excludeSelf || p != c.getSource().getPlayer())
                                    && Vanish.canSee(c.getSource(), p))
                        .map(p -> p.getGameProfile().getName()),
                    b));
  }

  private static RequiredArgumentBuilder<CommandSourceStack, String> saved(
      String name, Saved kind) {
    return word(name).suggests((c, b) -> suggestSaved(c.getSource(), kind, b));
  }

  private static CompletableFuture<Suggestions> suggestSaved(
      CommandSourceStack source, Saved kind, SuggestionsBuilder builder) {
    var a = Administration.INSTANCE;
    if (a == null || a.storage == null || a.compat == null) return builder.buildFuture();
    var p = source.getPlayer();
    Collection<String> names = List.of();
    switch (kind) {
      case REGION -> {
        if (manages(source, "ffa.region.edit"))
          names = a.regions.all().stream().map(r -> r.id).toList();
      }
      case PORTAL -> {
        if (enabled("portal") && manages(source, "ffa.portal.manage"))
          names = a.storage.state().portals.keySet();
      }
      case MANAGED_WARP -> {
        if (manages(source, "ffa.warp.manage") || manages(source, "ffa.portal.manage"))
          names = a.storage.state().warps.keySet();
      }
      case WARP -> {
        if (permitted(source, "warp")) {
          if (manages(source, "ffa.warp.manage")) names = a.storage.state().warps.keySet();
          else if (p != null)
            names =
                a.storage.state().warps.entrySet().stream()
                    .filter(
                        e -> {
                          var w = e.getValue();
                          var level = a.teleports.level(w.point);
                          return level != null
                              && a.compat.access(
                                  p,
                                  w.access,
                                  level,
                                  BlockPos.containing(w.point.x, w.point.y, w.point.z));
                        })
                    .map(Map.Entry::getKey)
                    .toList();
        }
      }
      case HOME -> {
        if ((usable(source, "home") || usable(source, "sethome") || usable(source, "delhome"))
            && p != null) {
          var d = a.storage.player(p.getUUID());
          if (d != null) names = d.homes.keySet();
        }
      }
      case SHOP -> {
        if (permitted(source, "shop")) names = a.storage.state().shops.keySet();
      }
    }
    return SharedSuggestionProvider.suggest(names, builder);
  }

  private static boolean permitted(CommandSourceStack s, String command) {
    var a = Administration.INSTANCE;
    if (a == null || a.settings == null) return false;
    var p = s.getPlayer();
    return p == null
        || a.compat != null && a.compat.admin(p)
        || a.compat != null
            && a.compat.decision(p, "ffa.command." + command).orElse(a.settings.publicCommands);
  }

  private static boolean usable(CommandSourceStack source, String command) {
    return enabled(command) && permitted(source, command);
  }

  private static boolean manages(CommandSourceStack s, String node) {
    var a = Administration.INSTANCE;
    if (a == null || a.settings == null) return false;
    if (s.hasPermission(a.settings.operatorLevel)) return true;
    var p = s.getPlayer();
    return p != null && a.compat != null && (a.compat.admin(p) || a.compat.permission(p, node));
  }

  private static boolean enabled(String command) {
    var a = Administration.INSTANCE;
    if (a == null || a.settings == null) return false;
    var c = a.settings;
    boolean ftb = a.compat != null && a.compat.ftbEssentials();
    return switch (command) {
      case "home", "sethome", "delhome", "homes" -> c.homes && !ftb;
      case "tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel" -> c.tpa && !ftb;
      case "tptoggle", "tpignore" -> c.privacy && !ftb;
      case "portal" -> c.portals;
      case "back" -> true;
      case "rtp" -> c.rtp;
      case "balance" -> c.economy;
      case "pay" -> c.pay && c.economy;
      case "baltop" -> c.leaderboard;
      default -> true;
    };
  }
}
