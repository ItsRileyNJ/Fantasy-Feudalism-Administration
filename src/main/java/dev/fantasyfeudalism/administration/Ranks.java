package dev.fantasyfeudalism.administration;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** World-local permissions. Publish only a completely validated, atomically persisted candidate. */
public final class Ranks {
  public static final class Rank {
    public int power;
    public boolean defaultRank;
    public Set<String> parents = new TreeSet<>();
    public Map<String, JsonPrimitive> permissions = new TreeMap<>();
  }

  public static final class Data {
    public int version = 1;
    public Map<String, Rank> ranks = new TreeMap<>();
    public Map<String, Set<String>> players = new TreeMap<>();
  }

  public final Path file;
  private Data data;
  private String loaded;
  private boolean healthy = true;
  private final Map<UUID, List<String>> memberships = new HashMap<>();

  public Ranks(Path file) throws IOException {
    this.file = file;
    if (!Files.exists(file)) {
      Data initial = new Data();
      Rank member = new Rank();
      member.defaultRank = true;
      initial.ranks.put("member", member);
      Storage.atomic(file, Storage.JSON.toJson(initial));
    }
    reload();
  }

  public void reload() throws IOException {
    String text = Files.readString(file);
    Data candidate = parse(text);
    data = candidate;
    memberships.clear();
    loaded = text;
    healthy = true;
  }

  public Data copy() {
    return Storage.JSON.fromJson(Storage.JSON.toJson(data), Data.class);
  }

  public Set<String> ids() {
    return Collections.unmodifiableSet(data.ranks.keySet());
  }

  public void update(Consumer<Data> edit) throws IOException {
    if (!healthy)
      throw new IOException("Ranks save previously failed; reload/recover ranks first.");
    if (!Files.readString(file).equals(loaded))
      throw new IllegalArgumentException(
          "ranks.json changed on disk; use /ffa ranks reload first.");
    Data candidate = copy();
    edit.accept(candidate);
    String text = Storage.JSON.toJson(candidate);
    candidate = parse(text);
    try {
      Storage.atomic(file, text);
    } catch (IOException e) {
      healthy = false;
      throw e;
    }
    data = candidate;
    memberships.clear();
    loaded = text;
  }

  /** Includes automatic default ranks and all inherited memberships. No implicit operator rank. */
  public List<String> active(UUID player) {
    List<String> cached = memberships.get(player);
    if (cached != null) return cached;
    if (memberships.size() >= 4096) memberships.clear();
    Set<String> result = new HashSet<>();
    data.ranks.forEach(
        (id, rank) -> {
          if (rank.defaultRank) collect(id, result);
        });
    for (String id : data.players.getOrDefault(player.toString(), Set.of())) collect(id, result);
    List<String> sorted =
        result.stream()
            .sorted(
                Comparator.<String>comparingInt(id -> data.ranks.get(id).power)
                    .reversed()
                    .thenComparing(Comparator.naturalOrder()))
            .toList();
    memberships.put(player, sorted);
    return sorted;
  }

  private void collect(String id, Set<String> result) {
    if (result.add(id)) for (String parent : data.ranks.get(id).parents) collect(parent, result);
  }

  public Optional<JsonPrimitive> value(UUID player, String node) {
    checkNode(node, false);
    for (String id : active(player)) {
      var nodes = data.ranks.get(id).permissions;
      JsonPrimitive exact = nodes.get(node);
      if (exact != null) return Optional.of(exact.deepCopy());
      if (node.startsWith(ChatStyles.PREFIX)) continue;
      for (int dot = node.lastIndexOf('.'); dot > 0; dot = node.lastIndexOf('.', dot - 1)) {
        String prefix = node.substring(0, dot);
        JsonPrimitive match = nodes.get(prefix + ".*");
        if (match == null && node.startsWith("command.")) match = nodes.get(prefix);
        if (match != null) return Optional.of(match.deepCopy());
      }
      if (nodes.containsKey("*")) return Optional.of(nodes.get("*").deepCopy());
    }
    return Optional.empty();
  }

  public Optional<Boolean> decision(UUID player, String node) {
    return value(player, node).filter(JsonPrimitive::isBoolean).map(JsonPrimitive::getAsBoolean);
  }

  public OptionalInt integer(UUID player, String node) {
    var value = value(player, node);
    return value.isPresent() && value.get().isNumber()
        ? OptionalInt.of(value.get().getAsInt())
        : OptionalInt.empty();
  }

  public static JsonPrimitive parseValue(String text) {
    if (text.equals("true") || text.equals("false"))
      return new JsonPrimitive(Boolean.parseBoolean(text));
    if (text.matches("-?[0-9]+")) {
      try {
        return new JsonPrimitive(Integer.parseInt(text));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Integer value is outside the 32-bit range");
      }
    }
    if (text.length() > 512)
      throw new IllegalArgumentException("String values are limited to 512 characters");
    return new JsonPrimitive(text);
  }

  public static void checkNode(String node, boolean wildcard) {
    String pattern =
        wildcard
            ? "(?:\\*|[a-z0-9_:-]+(?:\\.[a-z0-9_:-]+)*(?:\\.\\*)?)"
            : "[a-z0-9_:-]+(?:\\.[a-z0-9_:-]+)*";
    if (node == null || node.length() > 256 || !node.matches(pattern))
      throw new IllegalArgumentException(
          "Use lowercase dotted permission nodes; only a trailing .* or * wildcard is supported");
  }

  public static Data parse(String text) {
    try {
      JsonObject root = JsonParser.parseString(text).getAsJsonObject();
      keys(root, Set.of("version", "ranks", "players"));
      integer(root.get("version"));
      if (root.get("version").getAsInt() != 1)
        throw new IllegalArgumentException("Unsupported ranks version");
      JsonObject ranks = root.getAsJsonObject("ranks"), players = root.getAsJsonObject("players");
      if (ranks.size() > 256 || players.size() > 100000)
        throw new IllegalArgumentException("Ranks file exceeds limits");
      for (var entry : ranks.entrySet()) {
        Regions.checkId(entry.getKey());
        JsonObject rank = entry.getValue().getAsJsonObject();
        keys(rank, Set.of("power", "defaultRank", "parents", "permissions"));
        int power = integer(rank.get("power"));
        if (power < -1000000 || power > 1000000)
          throw new IllegalArgumentException("Rank power must be -1000000..1000000");
        if (!rank.get("defaultRank").isJsonPrimitive()
            || !rank.getAsJsonPrimitive("defaultRank").isBoolean())
          throw new IllegalArgumentException("defaultRank must be a boolean");
        JsonArray parents = rank.getAsJsonArray("parents");
        if (parents.size() > 32) throw new IllegalArgumentException("At most 32 parents per rank");
        Set<String> unique = new HashSet<>();
        for (JsonElement parent : parents) {
          String id = string(parent);
          if (!ranks.has(id) || !unique.add(id))
            throw new IllegalArgumentException("Unknown or duplicate parent: " + id);
        }
        JsonObject permissions = rank.getAsJsonObject("permissions");
        if (permissions.size() > 4096)
          throw new IllegalArgumentException("At most 4096 permission nodes per rank");
        for (var permission : permissions.entrySet()) {
          checkNode(permission.getKey(), true);
          if (!permission.getValue().isJsonPrimitive())
            throw new IllegalArgumentException("Permission must be boolean, integer or string");
          JsonPrimitive value = permission.getValue().getAsJsonPrimitive();
          if (value.isNumber()) integer(value);
          ChatStyles.validateRank(permission.getKey(), value);
          if (value.isString() && value.getAsString().length() > 512)
            throw new IllegalArgumentException("Rank string is too long");
          if (permission.getKey().equals("ffa.homes.maximum")
              && (!value.isNumber() || integer(value) < 0 || integer(value) > 1000))
            throw new IllegalArgumentException(
                "ffa.homes.maximum must be an integer from 0 to 1000");
        }
      }
      for (var entry : players.entrySet()) {
        if (!UUID.fromString(entry.getKey()).toString().equals(entry.getKey()))
          throw new IllegalArgumentException("Use canonical player UUIDs");
        Set<String> unique = new HashSet<>();
        for (JsonElement rank : entry.getValue().getAsJsonArray()) {
          String id = string(rank);
          if (!ranks.has(id) || !unique.add(id))
            throw new IllegalArgumentException("Unknown or duplicate assigned rank: " + id);
        }
      }
      Data data = Storage.JSON.fromJson(root, Data.class);
      Map<String, Integer> depths = new HashMap<>();
      for (String id : data.ranks.keySet()) depth(data, id, new HashSet<>(), depths);
      return data;
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid ranks.json: " + e.getMessage(), e);
    }
  }

  private static int depth(Data data, String id, Set<String> path, Map<String, Integer> depths) {
    if (!path.add(id)) throw new IllegalArgumentException("Rank inheritance cycle: " + id);
    Integer known = depths.get(id);
    if (known != null) {
      path.remove(id);
      return known;
    }
    int depth = 0;
    for (String parent : data.ranks.get(id).parents)
      depth = Math.max(depth, 1 + depth(data, parent, path, depths));
    if (depth > 32) throw new IllegalArgumentException("Rank inheritance depth above 32: " + id);
    path.remove(id);
    depths.put(id, depth);
    return depth;
  }

  private static void keys(JsonObject object, Set<String> keys) {
    if (!object.keySet().equals(keys))
      throw new IllegalArgumentException("Expected fields " + keys);
  }

  private static int integer(JsonElement value) {
    if (value == null
        || !value.isJsonPrimitive()
        || !value.getAsJsonPrimitive().isNumber()
        || !value.getAsString().matches("-?[0-9]+"))
      throw new IllegalArgumentException("Expected a whole integer");
    return Integer.parseInt(value.getAsString());
  }

  private static String string(JsonElement value) {
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
      throw new IllegalArgumentException("Expected a string");
    return value.getAsString();
  }
}
