package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.RegressionTests.*;

import java.util.*;
import net.minecraft.gametest.framework.GameTestHelper;

/** Wallet commit checks are independent of the optional FTB runtime. */
final class QuestRegression {
  @FunctionalInterface
  private interface Action {
    void run() throws Exception;
  }

  private static void rejected(GameTestHelper h, Action action, String message) throws Exception {
    boolean refused = false;
    try {
      action.run();
    } catch (IllegalArgumentException expected) {
      refused = true;
    }
    h.assertTrue(refused, message);
  }

  static void verify(GameTestHelper h, Administration app) throws Exception {
    var state = app.storage.copy();
    var a = player(h, "questPayer");
    var b = player(h, "questMember");
    boolean economy = app.settings.economy;
    long limit = app.settings.maxBalance;
    String aid = a.getUUID().toString(), bid = b.getUUID().toString();
    try {
      app.settings.economy = true;
      app.storage.update(
          s -> {
            s.players.get(aid).balance = 2000;
            s.players.get(bid).balance = 500;
          });
      var pay = new QuestDenar(false, 1000);
      var reward = new QuestDenar(true, 250);
      h.assertTrue(
          app.economy.quest(a, "task:team:fixture", 0, pay)
              && app.storage.player(a.getUUID()).balance == 1000
              && app.storage.player(b.getUUID()).balance == 500,
          "Quest payment debits only the submitting player and never a teammate");
      h.assertFalse(
          app.economy.quest(b, "task:team:fixture", 0, pay),
          "A retry by another team member restores progress without charging another wallet");
      h.assertTrue(
          app.storage.player(b.getUUID()).balance == 500, "Team replay preserves teammate balance");
      app.economy.quest(a, "reward:team:fixture", 1, reward);
      h.assertFalse(
          app.economy.quest(b, "reward:team:fixture", 1, reward),
          "A team reward pays once per completion, even with concurrent claim attempts");
      h.assertTrue(
          app.storage.player(a.getUUID()).balance == 1250
              && app.storage.player(b.getUUID()).balance == 500,
          "Team reward goes to its claimant without multiplying");
      app.economy.quest(a, "reward:player:" + aid, 1, reward);
      app.economy.quest(b, "reward:player:" + bid, 1, reward);
      h.assertTrue(
          app.storage.player(a.getUUID()).balance == 1500
              && app.storage.player(b.getUUID()).balance == 750,
          "Explicit individual rewards have separate per-player receipts");
      app.economy.quest(a, "task:team:fixture", 1, pay);
      h.assertTrue(
          app.storage.player(a.getUUID()).balance == 500,
          "A later legitimate repeat cycle can be paid once again");
      rejected(
          h,
          () -> app.economy.quest(a, "task:team:fixture", 0, pay),
          "Rewound progress cannot replay a historical payment");
      rejected(
          h,
          () -> app.economy.quest(a, "task:team:fixture", 1, new QuestDenar(false, 999)),
          "Editing an already processed amount cannot double-charge");
      int receipts = app.storage.state().questReceipts.size();
      rejected(
          h,
          () -> app.economy.quest(a, "task:insufficient", 0, pay),
          "Insufficient funds refuse quest progress");
      h.assertTrue(
          app.storage.state().questReceipts.size() == receipts,
          "Failed payment creates no completion receipt");
      app.settings.maxBalance = 600;
      rejected(
          h,
          () -> app.economy.quest(a, "reward:full", 1, reward),
          "Wallet limit refuses a reward instead of discarding money");
      app.settings.maxBalance = limit;
      app.settings.economy = false;
      rejected(
          h,
          () -> app.economy.quest(a, "reward:disabled", 1, reward),
          "Disabled economy cannot issue quest currency");
      app.settings.economy = true;
      var pending = new Model.PendingTrade();
      pending.player = aid;
      pending.id = UUID.randomUUID().toString();
      app.storage.update(s -> s.trades.put(aid, pending));
      rejected(
          h,
          () -> app.economy.quest(a, "reward:locked", 1, reward),
          "An unresolved inventory trade locks new quest wallet changes");
      app.storage.update(s -> s.trades.remove(aid));
      var restored = new Storage(app.storage.directory);
      h.assertTrue(
          restored.state().questReceipts.get("task:team:fixture").cycle == 1
              && restored.player(a.getUUID()).balance == 500,
          "Wallet and retry receipt survive restart together");
      app.storage = restored;
      h.assertFalse(
          app.economy.quest(a, "task:team:fixture", 1, pay),
          "Restart does not erase replay protection");
      h.assertTrue(
          app.quests.available() == net.neoforged.fml.ModList.get().isLoaded("ftbquests"),
          "Adapter is optional; uploaded FTB version is recognized when installed");
    } finally {
      app.settings.economy = economy;
      app.settings.maxBalance = limit;
      app.server.getPlayerList().remove(a);
      app.server.getPlayerList().remove(b);
      app.storage.commit(state);
    }
  }
}
