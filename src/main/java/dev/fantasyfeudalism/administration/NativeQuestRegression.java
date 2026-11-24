package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.RegressionTests.*;

import io.netty.buffer.Unpooled;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Exercises the actual supplied FTB classes and FFA mixins, never replacement/mock FTB classes. */
final class NativeQuestRegression {
  private static final String BASE = "dev.ftb.mods.ftbquests.quest.";

  private static boolean matches(Class<?>[] types, Object[] values) {
    if (types.length != values.length) return false;
    for (int i = 0; i < types.length; i++) {
      Class<?> type = types[i];
      if (type == long.class) type = Long.class;
      else if (type == int.class) type = Integer.class;
      else if (type == boolean.class) type = Boolean.class;
      if (values[i] != null && !type.isInstance(values[i])) return false;
    }
    return true;
  }

  private static Object call(Object receiver, String name, Object... args) throws Exception {
    for (Class<?> type = receiver.getClass(); type != null; type = type.getSuperclass())
      for (Method method : type.getDeclaredMethods())
        if (method.getName().equals(name) && matches(method.getParameterTypes(), args)) {
          method.setAccessible(true);
          try {
            return method.invoke(receiver, args);
          } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception cause) throw cause;
            throw ex;
          }
        }
    throw new NoSuchMethodException(receiver.getClass().getName() + "." + name);
  }

  private static Object make(String name, Object... args) throws Exception {
    for (Constructor<?> constructor : Class.forName(BASE + name).getConstructors())
      if (matches(constructor.getParameterTypes(), args)) return constructor.newInstance(args);
    throw new NoSuchMethodException("Constructor " + name);
  }

  private static long id(Object object) throws Exception {
    return object.getClass().getField("id").getLong(object);
  }

  private static CompoundTag data(String tag) {
    var data = new CompoundTag();
    var tags = new ListTag();
    tags.add(StringTag.valueOf(tag));
    data.put("tags", tags);
    return data;
  }

  private static void load(Administration app, Object object, CompoundTag data) throws Exception {
    call(object, "readData", data, app.server.registryAccess());
    call(object, "clearCachedData");
  }

  private static Object quest(Object file, Object chapter, boolean repeatable, Administration app)
      throws Exception {
    Object quest = make("Quest", call(file, "newID"), chapter);
    var settings = new CompoundTag();
    settings.putBoolean("can_repeat", repeatable);
    load(app, quest, settings);
    call(quest, "onCreated");
    return quest;
  }

  private static Object task(Object file, Object quest, String tag, Administration app)
      throws Exception {
    Object task = make("task.CustomTask", call(file, "newID"), quest);
    load(app, task, data(tag));
    call(task, "onCreated");
    call(file, "refreshIDMap");
    return task;
  }

  private static Object reward(
      Object file, Object quest, String tag, boolean shared, Administration app) throws Exception {
    Object reward = make("reward.CustomReward", call(file, "newID"), quest);
    var settings = data(tag);
    settings.putString("auto", "disabled");
    Object tristate =
        Class.forName("dev.ftb.mods.ftblibrary.config.Tristate")
            .getField(shared ? "TRUE" : "FALSE")
            .get(null);
    call(tristate, "write", settings, "team_reward");
    load(app, reward, settings);
    call(reward, "onCreated");
    call(file, "refreshIDMap");
    return reward;
  }

  private static void submit(Object task, Object team, ServerPlayer player) throws Exception {
    call(task, "submitTask", team, player, ItemStack.EMPTY);
  }

  private static void submitContext(Object file, Object task, Object team, ServerPlayer player)
      throws Exception {
    call(
        file,
        "withPlayerContext",
        player,
        (Runnable)
            () -> {
              try {
                submit(task, team, player);
              } catch (Exception error) {
                throw new IllegalStateException(error);
              }
            });
  }

  private static void claim(Object reward, Object team, ServerPlayer player) throws Exception {
    call(team, "claimReward", player, reward, false);
  }

  private static long balance(Administration app, ServerPlayer player) {
    return app.storage.player(player.getUUID()).balance;
  }

  private static void balances(Administration app, ServerPlayer a, long av, ServerPlayer b, long bv)
      throws Exception {
    app.storage.update(
        s -> {
          s.players.get(a.getUUID().toString()).balance = av;
          s.players.get(b.getUUID().toString()).balance = bv;
        });
  }

  static void verify(GameTestHelper h, Administration app) throws Exception {
    if (!net.neoforged.fml.ModList.get().isLoaded("ftbquests")) return;
    h.assertTrue(
        app.quests.available(), "Uploaded FTB Quests version initializes the real adapter");
    var old = app.storage.copy();
    var a = player(h, "ftbDenarPayer");
    var b = player(h, "ftbDenarMember");
    var outsider = player(h, "ftbDenarOut");
    Object file = Class.forName(BASE + "ServerQuestFile").getField("INSTANCE").get(null);
    Object chapter =
        make("Chapter", call(file, "newID"), file, call(file, "getDefaultChapterGroup"));
    Object party = null;
    long limit = app.settings.maxBalance;
    boolean economy = app.settings.economy;
    try {
      call(chapter, "onCreated");
      Object api =
          Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI").getMethod("api").invoke(null);
      Object manager = call(api, "getManager");
      Object white =
          Class.forName("dev.ftb.mods.ftblibrary.icon.Color4I").getField("WHITE").get(null);
      party =
          call(
              manager,
              "createPartyTeam",
              a,
              "ffa_" + UUID.randomUUID().toString().substring(0, 8),
              "Denar regression",
              white);
      call(party, "invite", a, List.of(b.getGameProfile()));
      call(party, "join", b);
      Object team = ((Optional<?>) call(file, "getTeamData", a)).orElseThrow();
      h.assertTrue(
          team == ((Optional<?>) call(file, "getTeamData", b)).orElseThrow(),
          "Real FTB party members share quest progress");
      balances(app, a, 1500, b, 200);
      Object q = quest(file, chapter, false, app);
      Object pay = task(file, q, "ffa_denar_pay_1000", app);
      Object personal = reward(file, q, "ffa_denar_reward_250", false, app);
      Object shared = reward(file, q, "ffa_denar_reward_100", true, app);
      h.assertTrue(
          (long) call(pay, "getMaxProgress") == 1 && (int) call(pay, "autoSubmitOnPlayerTick") == 0,
          "Real Custom Task has one manual completion step and no automatic payment ticks");
      var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), app.server.registryAccess());
      try {
        call(pay, "writeNetData", wire);
        Object copy = make("task.CustomTask", call(file, "newID"), q);
        call(copy, "readNetData", wire);
        Field button = copy.getClass().getDeclaredField("enableButton");
        button.setAccessible(true);
        h.assertTrue(
            button.getBoolean(copy) && (long) call(copy, "getMaxProgress") == 1,
            "Existing FTB task network format delivers an enabled Submit button without FFA client"
                + " code");
      } finally {
        wire.release();
      }
      claim(personal, team, a);
      h.assertTrue(balance(app, a) == 1500, "Incomplete native quest cannot grant Denar");
      submit(pay, team, b);
      h.assertTrue(
          balance(app, b) == 200 && !(boolean) call(team, "isCompleted", pay),
          "Insufficient native task payment leaves wallet and progress unchanged");
      submit(pay, team, outsider);
      h.assertTrue(
          !(boolean) call(team, "isCompleted", pay),
          "Outsider cannot submit another party's payment task");
      submit(pay, team, a);
      h.assertTrue(
          balance(app, a) == 500
              && balance(app, b) == 200
              && (boolean) call(team, "isCompleted", pay),
          "Real Custom Task/mixin debits only the payer and completes shared progress");
      submit(pay, team, b);
      h.assertTrue(
          balance(app, b) == 200, "Completed shared task cannot debit a teammate on replay");
      claim(personal, team, a);
      claim(personal, team, a);
      claim(personal, team, b);
      h.assertTrue(
          balance(app, a) == 750 && balance(app, b) == 450,
          "Real individual reward credits each claimant once using FTB claim records");
      claim(shared, team, a);
      claim(shared, team, b);
      h.assertTrue(
          balance(app, a) == 850 && balance(app, b) == 450,
          "Real team reward pays the first claimant once without multiplying by member count");

      Object recovery = quest(file, chapter, false, app);
      Object recoveryTask = task(file, recovery, "ffa_denar_pay_100", app);
      String teamId = call(team, "getTeamId").toString();
      String scope = "task:" + Long.toUnsignedString(id(recoveryTask), 16) + ":team:" + teamId;
      app.economy.quest(a, scope, 0, new QuestDenar(false, 100));
      submit(recoveryTask, team, b);
      h.assertTrue(
          balance(app, a) == 750
              && balance(app, b) == 450
              && (boolean) call(team, "isCompleted", recoveryTask),
          "Interrupted native payment restores shared task progress without charging a retrying"
              + " teammate");
      Object recoveryReward = reward(file, recovery, "ffa_denar_reward_50", true, app);
      scope = "reward:" + Long.toUnsignedString(id(recoveryReward), 16) + ":team:" + teamId;
      long cycle = ((Number) call(team, "getCompletionCount", recovery)).longValue();
      app.economy.quest(a, scope, cycle, new QuestDenar(true, 50));
      claim(recoveryReward, team, b);
      h.assertTrue(
          balance(app, a) == 800
              && balance(app, b) == 450
              && (boolean) call(team, "isRewardClaimed", b.getUUID(), recoveryReward),
          "Interrupted native reward restores the claim marker without issuing money twice");

      Object full = reward(file, q, "ffa_denar_reward_250", false, app);
      app.settings.maxBalance = 900;
      claim(full, team, a);
      h.assertTrue(
          balance(app, a) == 800 && !(boolean) call(team, "isRewardClaimed", a.getUUID(), full),
          "Wallet cap leaves the actual FTB reward unclaimed");
      app.settings.maxBalance = limit;
      app.settings.economy = false;
      claim(full, team, a);
      h.assertTrue(
          !(boolean) call(team, "isRewardClaimed", a.getUUID(), full),
          "Disabled economy preserves native reward eligibility");
      app.settings.economy = true;
      claim(full, team, a);
      h.assertTrue(
          balance(app, a) == 1050,
          "Refused native reward can be claimed after the limit is resolved");
      Object malformed = reward(file, q, "ffa_denar_reward_bad", false, app);
      claim(malformed, team, a);
      h.assertTrue(
          !(boolean) call(team, "isRewardClaimed", a.getUUID(), malformed),
          "Invalid Denar tag cannot consume a real FTB reward");
      Object unrelated = reward(file, q, "unrelated_mod", false, app);
      claim(unrelated, team, a);
      h.assertTrue(
          (boolean) call(team, "isRewardClaimed", a.getUUID(), unrelated)
              && balance(app, a) == 1050,
          "Unrelated native Custom Rewards retain their original handlers and change no wallet");

      Object edit = quest(file, chapter, false, app);
      Object editedTask = task(file, edit, "ffa_denar_pay_20", app);
      call(editedTask, "getTags");
      load(app, editedTask, data("ffa_denar_pay_50"));
      call(team, "setLocked", true);
      submit(editedTask, team, a);
      call(team, "setLocked", false);
      h.assertTrue(
          !(boolean) call(team, "isCompleted", editedTask),
          "Locked native progress cannot be paid");
      submit(editedTask, team, a);
      h.assertTrue(
          balance(app, a) == 1000,
          "Editing a cached task tag uses the new server-authoritative amount");

      Object repeated = quest(file, chapter, true, app);
      Object repeatTask = task(file, repeated, "ffa_denar_pay_10", app);
      Object repeatReward = reward(file, repeated, "ffa_denar_reward_5", true, app);
      submit(repeatTask, team, a);
      claim(repeatReward, team, a);
      long firstCycle = ((Number) call(team, "getCompletionCount", repeated)).longValue();
      h.assertTrue(
          !(boolean) call(team, "isCompleted", repeatTask),
          "Native reward claim resets a repeatable quest through FTB's own API");
      submit(repeatTask, team, a);
      claim(repeatReward, team, a);
      h.assertTrue(
          balance(app, a) == 990
              && ((Number) call(team, "getCompletionCount", repeated)).longValue()
                  == firstCycle + 1,
          "Two actual FTB completion cycles each charge and reward exactly once");

      Object autoIndividualQuest = quest(file, chapter, false, app);
      Object autoIndividualTask = task(file, autoIndividualQuest, "ffa_denar_pay_10", app);
      Object autoIndividualReward =
          reward(file, autoIndividualQuest, "ffa_denar_reward_7", false, app);
      var autoData = new CompoundTag();
      call(autoIndividualReward, "writeData", autoData, app.server.registryAccess());
      autoData.putString("auto", "enabled");
      load(app, autoIndividualReward, autoData);
      submitContext(file, autoIndividualTask, team, a);
      h.assertTrue(
          balance(app, a) == 987 && balance(app, b) == 457,
          "Native individual auto-claim pays each eligible member exactly once when explicitly"
              + " configured");
      Object autoTeamQuest = quest(file, chapter, false, app);
      Object autoTeamTask = task(file, autoTeamQuest, "ffa_denar_pay_10", app);
      Object autoTeamReward = reward(file, autoTeamQuest, "ffa_denar_reward_7", true, app);
      autoData = new CompoundTag();
      call(autoTeamReward, "writeData", autoData, app.server.registryAccess());
      autoData.putString("auto", "enabled");
      load(app, autoTeamReward, autoData);
      submitContext(file, autoTeamTask, team, a);
      h.assertTrue(
          balance(app, a) == 984 && balance(app, b) == 457,
          "Native team auto-claim pays only the submitting player, once for the party");
      Object ordered = quest(file, chapter, false, app);
      var orderedData = new CompoundTag();
      call(ordered, "writeData", orderedData, app.server.registryAccess());
      orderedData.putBoolean("require_sequential_tasks", true);
      load(app, ordered, orderedData);
      Object first = task(file, ordered, "ffa_denar_pay_10", app);
      Object second = task(file, ordered, "ffa_denar_pay_20", app);
      submit(second, team, a);
      h.assertTrue(
          balance(app, a) == 984 && !(boolean) call(team, "isCompleted", second),
          "Native task-order restrictions prevent paying a later task early");
      submit(first, team, a);
      submit(second, team, a);
      h.assertTrue(
          balance(app, a) == 954 && (boolean) call(team, "isCompleted", ordered),
          "Sequential native Denar tasks complete and charge in the configured order");
    } finally {
      app.settings.maxBalance = limit;
      app.settings.economy = economy;
      call(chapter, "deleteSelf");
      if (party != null)
        call(party, "forceDisband", a.createCommandSourceStack().withPermission(4));
      app.server.getPlayerList().remove(a);
      app.server.getPlayerList().remove(b);
      app.server.getPlayerList().remove(outsider);
      app.storage.commit(old);
    }
  }
}
