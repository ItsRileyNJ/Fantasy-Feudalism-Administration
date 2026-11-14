package dev.fantasyfeudalism.administration;

import static net.minecraft.commands.Commands.*;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

final class NpcTradeCommands {
  static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
    var node = literal("npctrade").requires(StaffCommands::admin);
    var price = argument("offer", IntegerArgumentType.integer(1));
    for (boolean buy : new boolean[] {true, false})
      price.then(
          literal(buy ? "buy" : "sell")
              .then(
                  argument("price", LongArgumentType.longArg(0))
                      .executes(
                          c ->
                              run(
                                  c,
                                  () -> {
                                    var app = Administration.INSTANCE;
                                    var npc =
                                        app.npcTrades.looking(c.getSource().getPlayerOrException());
                                    app.npcTrades.price(
                                        npc,
                                        IntegerArgumentType.getInteger(c, "offer"),
                                        buy,
                                        LongArgumentType.getLong(c, "price"));
                                    tell(c, "NPC offer price set.");
                                  }))));
    node.then(literal("price").then(price));
    node.then(
        literal("clear")
            .then(
                argument("offer", IntegerArgumentType.integer(1))
                    .executes(
                        c ->
                            run(
                                c,
                                () -> {
                                  var app = Administration.INSTANCE;
                                  app.npcTrades.clear(
                                      app.npcTrades.looking(c.getSource().getPlayerOrException()),
                                      IntegerArgumentType.getInteger(c, "offer"));
                                  tell(c, "NPC offer price removed.");
                                })))
            .then(
                literal("all")
                    .executes(
                        c ->
                            run(
                                c,
                                () -> {
                                  var app = Administration.INSTANCE;
                                  app.npcTrades.clear(
                                      app.npcTrades.looking(c.getSource().getPlayerOrException()),
                                      null);
                                  tell(c, "Native item trading restored.");
                                }))));
    node.then(
        literal("list")
            .executes(
                c ->
                    run(
                        c,
                        () -> {
                          var app = Administration.INSTANCE;
                          var npc = app.npcTrades.looking(c.getSource().getPlayerOrException());
                          var shop =
                              app.storage.state().npcShops.get(npc.entity().getUUID().toString());
                          for (int i = 0; i < npc.merchant().getOffers().size(); i++) {
                            var offer = npc.merchant().getOffers().get(i);
                            var bound = shop == null ? null : shop.offers.get(i + 1);
                            tell(
                                c,
                                "Offer "
                                    + (i + 1)
                                    + ": "
                                    + offer.getResult().getCount()
                                    + " × "
                                    + offer.getResult().getHoverName().getString()
                                    + "; "
                                    + (bound == null
                                        ? "native item trade"
                                        : (bound.buy ? "buy for " : "sell inputs for ")
                                            + bound.price
                                            + " Denar")
                                    + ".");
                          }
                          if (npc.merchant().getOffers().isEmpty())
                            tell(c, "This NPC has no offers. Create them in Easy NPC first.");
                        })));
    root.then(node);
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
                  ex.getMessage() == null ? "NPC trading command failed." : ex.getMessage()));
      return 0;
    }
  }

  private static void tell(CommandContext<CommandSourceStack> c, String text) {
    c.getSource().sendSuccess(() -> Component.literal(text), false);
  }
}
