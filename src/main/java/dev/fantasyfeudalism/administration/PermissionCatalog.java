package dev.fantasyfeudalism.administration;

import java.util.*;

/** A small documented starting point; other mods' command paths remain an opt-in discovery list. */
public final class PermissionCatalog {
  public record Node(String category, String description, String value) {}

  private static final Map<String, Node> NODES = new LinkedHashMap<>();

  static {
    add(
        "staff",
        "ffa.admin",
        "Full FFA administrator: bypasses gameplay restrictions; grants staff tools",
        "true or false");
    add(
        "staff",
        "ffa.ranks.manage",
        "Create ranks and grant permissions; treat as full permission-administration authority",
        "true or false");
    add("staff", "ffa.region.edit", "Create and edit protected regions", "true or false");
    add("staff", "ffa.spawn.manage", "Create spawn and set its arrival point", "true or false");
    add("staff", "ffa.warp.manage", "Create/delete warps and edit access", "true or false");
    add(
        "staff",
        "ffa.portal.manage",
        "Manage FFA portals; admin-only creation setting still applies",
        "true or false");
    add("staff", "ffa.shop.manage", "Configure shop offerings and prices", "true or false");
    add("staff", "ffa.economy.manage", "Set balances with /ffa balance set", "true or false");
    add("staff", "ffa.reload", "Reload configuration and regions", "true or false");
    add("staff", "ffa.backup", "Create an FFA snapshot", "true or false");
    for (String family :
        List.of("spawn", "warp", "home", "back", "balance", "shop", "whisper", "stats", "pvp"))
      add(
          "player",
          "ffa.command." + family,
          "Allow or deny the /ffa " + family + " command family",
          "true or false");
    add("limits", "ffa.homes.maximum", "Maximum saved homes for this rank", "whole number 0–1000");
    add("limits", "ffa.homes.unlimited", "Ignore the home count limit", "true or false");
    add("chat", "ffa.chat.nickname", "Let players change their own nickname", "true or false");
    add("chat", "ffa.chat.title", "Let players change their own title", "true or false");
    add(
        "chat",
        "ffa.chat.color",
        "Let players choose their own name/title colors",
        "true or false");
    add(
        "chat",
        "ffa.chat.format",
        "Let players toggle their own bold/italic/underline styles",
        "true or false");
    add("chat", "ffa.chat.manage", "Edit any player's appearance", "true or false");
    for (String property : ChatStyles.PROPERTIES)
      add(
          "style",
          ChatStyles.PREFIX + property,
          "Default rank " + property.replace('_', ' '),
          property.endsWith("bold")
                  || property.endsWith("italic")
                  || property.endsWith("underlined")
              ? "true or false"
              : "text or color name");
  }

  private static void add(String group, String node, String description, String value) {
    NODES.put(node, new Node(group, description, value));
  }

  public static Set<String> common() {
    return Collections.unmodifiableSet(NODES.keySet());
  }

  public static String description(String node) {
    var value = NODES.get(node);
    return value == null
        ? node.startsWith("command.")
            ? "Permission for this registered command branch; true or false"
            : "Custom permission node"
        : value.description + ". Value: " + value.value;
  }

  public static List<String> categories() {
    return List.of("player", "staff", "chat", "style", "limits", "commands");
  }

  public static List<String> explain(String category, int page) {
    if (category.equals("commands"))
      return List.of(
          "command.… nodes control vanilla/other mods' commands. Start typing command. in a node"
              + " argument, or run /ffa ranks refresh_readme for the complete reference.");
    if (!categories().contains(category))
      throw new IllegalArgumentException("Choose player, staff, chat, style, limits or commands.");
    var values =
        NODES.entrySet().stream().filter(e -> e.getValue().category.equals(category)).toList();
    int pages = Math.max(1, (values.size() + 5) / 6);
    if (page < 1 || page > pages)
      throw new IllegalArgumentException("Choose a page from 1 to " + pages + ".");
    List<String> result = new ArrayList<>();
    result.add(category + " nodes — page " + page + " of " + pages + ":");
    values.stream()
        .skip((page - 1) * 6L)
        .limit(6)
        .forEach(e -> result.add(e.getKey() + ": " + description(e.getKey())));
    return result;
  }
}
