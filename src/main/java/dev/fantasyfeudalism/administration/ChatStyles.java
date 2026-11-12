package dev.fantasyfeudalism.administration;

import com.google.gson.JsonPrimitive;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Literal display components: never rename accounts, rewrite signed message bodies, or parse
 * markup.
 */
public final class ChatStyles {
  public static final String PREFIX = "ffa.chat.style.";
  public static final Set<String> COLORS =
      Collections.unmodifiableSet(
          new TreeSet<>(
              List.of(
                  "black",
                  "dark_blue",
                  "dark_green",
                  "dark_aqua",
                  "dark_red",
                  "dark_purple",
                  "gold",
                  "gray",
                  "dark_gray",
                  "blue",
                  "green",
                  "aqua",
                  "red",
                  "light_purple",
                  "yellow",
                  "white")));
  public static final Set<String> PROPERTIES =
      Collections.unmodifiableSet(
          new TreeSet<>(
              List.of(
                  "nickname",
                  "title",
                  "name_color",
                  "title_color",
                  "name_bold",
                  "name_italic",
                  "title_bold",
                  "title_italic",
                  "name_underlined",
                  "title_underlined")));
  private final Administration app;

  public ChatStyles(Administration app) {
    this.app = app;
  }

  public static String permission(String key) {
    if (!PROPERTIES.contains(key))
      throw new IllegalArgumentException("Unknown chat property: " + key);
    return "ffa.chat."
        + switch (key) {
          case "nickname" -> "nickname";
          case "title" -> "title";
          case "name_color", "title_color" -> "color";
          default -> "format";
        };
  }

  public static String validate(String key, String value) {
    permission(key);
    if (value == null) throw new IllegalArgumentException("Chat value cannot be null");
    if (key.endsWith("_color")) {
      if (!COLORS.contains(value))
        throw new IllegalArgumentException("Use a named Minecraft chat color");
    } else if (key.endsWith("_bold") || key.endsWith("_italic") || key.endsWith("_underlined")) {
      if (!value.equals("true") && !value.equals("false"))
        throw new IllegalArgumentException("Formatting must be true or false");
    } else {
      int max = key.equals("nickname") ? 32 : 48;
      if (value.codePointCount(0, value.length()) > max
          || !value.equals(value.strip())
          || key.equals("nickname") && value.isBlank()
          || value
              .codePoints()
              .anyMatch(
                  c ->
                      Character.isISOControl(c)
                          || Character.getType(c) == Character.FORMAT
                          || c == 0x00a7
                          || Character.isWhitespace(c) && c != ' '))
        throw new IllegalArgumentException(
            key
                + " must be plain text, at most "
                + max
                + " characters, without control/format codes or outer spaces");
    }
    return value;
  }

  public static JsonPrimitive rankValue(String node, String text) {
    if (node.startsWith(PREFIX)) {
      String key = node.substring(PREFIX.length());
      if (Set.of("nickname", "title", "name_color", "title_color").contains(key))
        return new JsonPrimitive(text);
    }
    return Ranks.parseValue(text);
  }

  public static void validateRank(String node, JsonPrimitive value) {
    if (!node.startsWith(PREFIX)) return;
    String property = node.substring(PREFIX.length());
    permission(property);
    boolean flag =
        property.endsWith("_bold")
            || property.endsWith("_italic")
            || property.endsWith("_underlined");
    if (flag ? !value.isBoolean() : !value.isString())
      throw new IllegalArgumentException(node + " must be " + (flag ? "Boolean" : "a string"));
    validate(property, value.getAsString());
  }

  public Map<String, String> effective(ServerPlayer player) {
    Map<String, String> result = new TreeMap<>();
    for (String key : PROPERTIES) {
      var value = app.ranks.value(player.getUUID(), PREFIX + key);
      if (value.isPresent()) {
        try {
          validateRank(PREFIX + key, value.get());
          result.put(key, value.get().getAsString());
        } catch (IllegalArgumentException ignored) {
          // A broad permission wildcard is not a valid display-style value.
        }
      }
    }
    var data = app.storage.player(player.getUUID());
    if (data != null && data.chat != null)
      data.chat.forEach(
          (key, value) -> {
            try {
              result.put(key, validate(key, value));
            } catch (IllegalArgumentException ignored) {
            }
          });
    return result;
  }

  public static String describe(Map<String, String> values) {
    if (values == null || values.isEmpty()) return "Default chat appearance.";
    return String.join(
            ". ",
            values.entrySet().stream()
                .map(
                    e -> {
                      String key = e.getKey().replace('_', ' ');
                      return Character.toUpperCase(key.charAt(0))
                          + key.substring(1)
                          + ": "
                          + e.getValue();
                    })
                .toList())
        + ".";
  }

  public Component format(ServerPlayer player, Component original) {
    Map<String, String> values = effective(player);
    if (values.isEmpty()) return original;
    MutableComponent result = Component.empty();
    String title = values.getOrDefault("title", "");
    if (!title.isEmpty())
      result
          .append(Component.literal("[" + title + "]").setStyle(style(values, "title")))
          .append(" ");
    result.append(
        Component.literal(values.getOrDefault("nickname", player.getGameProfile().getName()))
            .setStyle(style(values, "name")));
    return result;
  }

  private static Style style(Map<String, String> values, String part) {
    return Style.EMPTY
        .withColor(ChatFormatting.getByName(values.getOrDefault(part + "_color", "white")))
        .withBold(Boolean.parseBoolean(values.getOrDefault(part + "_bold", "false")))
        .withItalic(Boolean.parseBoolean(values.getOrDefault(part + "_italic", "false")))
        .withUnderlined(Boolean.parseBoolean(values.getOrDefault(part + "_underlined", "false")));
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public void name(PlayerEvent.NameFormat event) {
    if (event.getEntity() instanceof ServerPlayer player
        && app.server != null
        && app.ranks != null
        && app.storage != null) event.setDisplayname(format(player, event.getDisplayname()));
  }
}
