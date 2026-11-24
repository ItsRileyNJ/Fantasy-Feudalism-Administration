package dev.fantasyfeudalism.administration;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

/** Optional server-only bridge using FTB's already registered Custom Task/Reward types. */
public final class FtbQuests {
  private static final String BASE = "dev.ftb.mods.ftbquests.quest.";
  private static volatile Field tagField;
  private final Administration app;
  private String unavailable = "FTB Quests is not installed.";
  private Class<?> questType, taskType, rewardType, teamType, objectType;
  private Method getTeam,
      getTaskQuest,
      getRewardQuest,
      cycle,
      completed,
      ready,
      sequence,
      setProgress;

  public FtbQuests(Administration app) {
    this.app = app;
    if (!ModList.get().isLoaded("ftbquests")) return;
    String version =
        ModList.get()
            .getModContainerById("ftbquests")
            .orElseThrow()
            .getModInfo()
            .getVersion()
            .toString();
    if (!version.equals("2101.1.39")) {
      unavailable =
          "Denar quests require the verified FTB Quests 2101.1.39 adapter; installed: "
              + version
              + ".";
      Administration.LOG.warn(unavailable);
      return;
    }
    try {
      objectType = Class.forName(BASE + "QuestObject");
      questType = Class.forName(BASE + "Quest");
      teamType = Class.forName(BASE + "TeamData");
      taskType = Class.forName(BASE + "task.Task");
      rewardType = Class.forName(BASE + "reward.Reward");
      getTeam = Class.forName(BASE + "BaseQuestFile").getMethod("getTeamData", Player.class);
      getTaskQuest = taskType.getMethod("getQuest");
      getRewardQuest = rewardType.getMethod("getQuest");
      cycle = teamType.getMethod("getCompletionCount", questType);
      completed = teamType.getMethod("isCompleted", objectType);
      ready = teamType.getMethod("isRewardReadyToClaim", UUID.class, rewardType);
      sequence = taskType.getDeclaredMethod("checkTaskSequence", teamType);
      sequence.setAccessible(true);
      setProgress = teamType.getMethod("setProgress", taskType, long.class);
      unavailable = null;
      Administration.LOG.info(
          "FTB Quests {}: Denar custom rewards and payment tasks enabled.", version);
    } catch (ReflectiveOperationException | LinkageError ex) {
      unavailable = "Denar quest adapter is unavailable; check the server log.";
      Administration.LOG.error("FTB Quests Denar API mismatch", ex);
    }
  }

  public boolean available() {
    return unavailable == null;
  }

  @SuppressWarnings("unchecked")
  private static Collection<String> tags(Object object) throws ReflectiveOperationException {
    Field field = tagField;
    if (field == null) {
      field = Class.forName(BASE + "QuestObjectBase").getDeclaredField("tags");
      field.setAccessible(true);
      tagField = field;
    }
    // FTB clears its cached getTags() result after readData; read the new list during that
    // callback.
    return (Collection<String>) field.get(object);
  }

  public static boolean tagged(Object object) {
    try {
      return QuestDenar.tagged(tags(object));
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("Cannot read quest tags.", ex);
    }
  }

  /** Reuses fields which FTB already synchronizes, so a normal FTB client gets a Submit button. */
  public static void configureTask(Object task) {
    if (!tagged(task)) return;
    try {
      task.getClass().getMethod("setEnableButton", boolean.class).invoke(task, true);
      task.getClass().getMethod("setMaxProgress", long.class).invoke(task, 1L);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("Cannot configure the Denar task button.", ex);
    }
  }

  private void check(ServerPlayer player) {
    if (!available()) throw new IllegalArgumentException(unavailable);
    if (!app.server.isSameThread())
      throw new IllegalStateException("Quest payments must run on the server thread.");
    if (app.rules.pending(player))
      throw new IllegalArgumentException("Accept the server rules before using Denar quests.");
  }

  private Object team(Object object, ServerPlayer player) throws ReflectiveOperationException {
    Object file = object.getClass().getMethod("getQuestFile").invoke(object);
    return ((Optional<?>) getTeam.invoke(file, player))
        .orElseThrow(
            () -> new IllegalArgumentException("Quest progress is not ready. Rejoin the server."));
  }

  private static String id(Object object) throws ReflectiveOperationException {
    return Long.toUnsignedString(object.getClass().getField("id").getLong(object), 16);
  }

  private static UUID teamId(Object team) throws ReflectiveOperationException {
    return (UUID) team.getClass().getMethod("getTeamId").invoke(team);
  }

  public boolean reward(Object reward, ServerPlayer player) {
    try {
      check(player);
      QuestDenar operation = QuestDenar.parse(tags(reward), true);
      Object team = team(reward, player);
      Object quest = getRewardQuest.invoke(reward);
      if (!(boolean) completed.invoke(team, quest)
          || (boolean) teamType.getMethod("isLocked").invoke(team)
          || !(boolean) ready.invoke(team, player.getUUID(), reward))
        throw new IllegalArgumentException("This quest reward is not ready to claim.");
      long count = ((Number) cycle.invoke(team, quest)).longValue();
      boolean shared = (boolean) rewardType.getMethod("isTeamReward").invoke(reward);
      String scope =
          "reward:"
              + id(reward)
              + (shared ? ":team:" + teamId(team) : ":player:" + player.getUUID());
      boolean applied = app.economy.quest(player, scope, count, operation);
      Teleports.tell(
          player,
          applied
              ? "Received " + operation.amount() + " Denar."
              : "Reward already paid. Quest claim restored.");
      return true;
    } catch (Exception ex) {
      failure(player, ex);
      return false; // FTB must leave failed rewards unclaimed.
    }
  }

  public void pay(Object task, Object suppliedTeam, ServerPlayer player) {
    try {
      check(player);
      QuestDenar operation = QuestDenar.parse(tags(task), false);
      Object team = team(task, player);
      if (team != suppliedTeam)
        throw new IllegalArgumentException("You cannot pay for another team's quest.");
      Object quest = getTaskQuest.invoke(task);
      if ((boolean) teamType.getMethod("isLocked").invoke(team)
          || !(boolean) teamType.getMethod("canStartTasks", questType).invoke(team, quest)
          || !(boolean) sequence.invoke(task, team))
        throw new IllegalArgumentException("This quest task is not available yet.");
      if ((boolean) completed.invoke(team, task) || (boolean) completed.invoke(team, quest)) return;
      if (app.moderation.confined(player))
        throw new IllegalArgumentException("You cannot pay quest costs while jailed.");
      long count = ((Number) cycle.invoke(team, quest)).longValue();
      String scope = "task:" + id(task) + ":team:" + teamId(team);
      boolean applied = app.economy.quest(player, scope, count, operation);
      // A retry after an interruption restores progress without charging any member twice.
      setProgress.invoke(team, task, 1L);
      Teleports.tell(
          player,
          applied
              ? "Paid " + operation.amount() + " Denar."
              : "Payment already received. Quest progress restored.");
    } catch (Exception ex) {
      failure(player, ex);
    }
  }

  private static void failure(ServerPlayer player, Exception error) {
    Throwable cause =
        error instanceof InvocationTargetException reflected ? reflected.getCause() : error;
    if (!(cause instanceof IllegalArgumentException))
      Administration.LOG.error("Denar quest transaction failed", cause);
    Teleports.tell(
        player,
        cause instanceof IllegalArgumentException
            ? cause.getMessage()
            : "Quest transaction could not finish. Contact an admin.");
  }
}
