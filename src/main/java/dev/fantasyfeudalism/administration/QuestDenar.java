package dev.fantasyfeudalism.administration;

import java.util.Collection;

/** Tags use only characters accepted by FTB Quests' built-in Tags editor. */
public record QuestDenar(boolean reward, long amount) {
  public static final String PREFIX = "ffa_denar_";

  public static boolean tagged(Collection<String> tags) {
    return tags.stream().anyMatch(t -> t.startsWith(PREFIX));
  }

  public static QuestDenar parse(Collection<String> tags, boolean reward) {
    var selected = tags.stream().filter(t -> t.startsWith(PREFIX)).toList();
    String prefix = PREFIX + (reward ? "reward_" : "pay_");
    if (selected.size() != 1 || !selected.getFirst().matches(prefix + "[1-9][0-9]{0,15}"))
      throw new IllegalArgumentException(
          "Use exactly one tag: " + prefix + "AMOUNT (a positive whole number).");
    long amount = Long.parseLong(selected.getFirst().substring(prefix.length()));
    if (amount > 9_000_000_000_000_000L)
      throw new IllegalArgumentException("Denar amount exceeds the wallet limit.");
    return new QuestDenar(reward, amount);
  }
}
