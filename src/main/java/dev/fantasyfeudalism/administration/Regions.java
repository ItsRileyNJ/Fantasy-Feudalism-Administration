package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class Regions {
  public final Path file;
  private String loadedText;
  private List<Region> regions = List.of();
  private Map<String, List<Region>> dimensions = Map.of();

  public Regions(Path file) {
    this.file = file;
  }

  public List<Region> all() {
    return regions;
  }

  public Region get(String id) {
    return regions.stream()
        .filter(r -> r.id.equalsIgnoreCase(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown region: " + id));
  }

  public List<Region> copy() {
    return Arrays.asList(Storage.JSON.fromJson(Storage.JSON.toJson(regions), Region[].class));
  }

  public void reload(MinecraftServer server, Settings settings, Compat compat) throws Exception {
    if (!Files.exists(file)) Storage.atomic(file, header());
    var candidate = read(file);
    validate(candidate, server, settings, compat);
    install(candidate);
    loadedText = Files.readString(file);
  }

  public void edit(List<Region> candidate, MinecraftServer server, Settings settings, Compat compat)
      throws Exception {
    if (!Objects.equals(loadedText, Files.readString(file)))
      throw new IllegalArgumentException(
          "regions.toml has external edits. Run /ffa reload before in-game changes.");
    validate(candidate, server, settings, compat);
    String text = write(candidate);
    Storage.atomic(file, text);
    install(candidate);
    loadedText = text;
  }

  private void install(List<Region> candidate) {
    regions = List.copyOf(candidate);
    var index = new HashMap<String, List<Region>>();
    for (var r : regions) index.computeIfAbsent(r.dimension, k -> new ArrayList<>()).add(r);
    for (var list : index.values())
      list.sort(
          Comparator.comparingInt((Region r) -> r.kind == Kind.CHILD ? 1 : 0)
              .thenComparingInt(r -> r.priority)
              .reversed());
    dimensions = Map.copyOf(index);
  }

  public Region at(ServerLevel level, BlockPos pos) {
    return at(level.dimension().location().toString(), pos.getX(), pos.getZ());
  }

  public Region at(String dim, double x, double z) {
    for (var r : dimensions.getOrDefault(dim, List.of())) if (r.contains(x, z)) return r;
    return null;
  }

  public boolean protectedAt(ServerLevel level, BlockPos pos, Settings s) {
    return at(level, pos) != null || isSpawnDimension(level, s);
  }

  public static boolean isSpawnDimension(ServerLevel level, Settings s) {
    return s.dimension && level.dimension().location().toString().equals("ffa:spawn");
  }

  public boolean allowed(ServerLevel level, BlockPos pos, Flag flag, Settings settings) {
    Region r = at(level, pos);
    return r == null
        ? (isSpawnDimension(level, settings) ? settings.defaults.get(flag) : true)
        : resolve(r, flag, settings);
  }

  public boolean resolve(Region r, Flag flag, Settings settings) {
    Boolean v = r.flags.get(flag);
    if (v != null) return v;
    if (r.kind == Kind.CHILD) return resolve(get(r.parent), flag, settings);
    return settings.defaults.get(flag);
  }

  public static void validate(
      List<Region> candidate, MinecraftServer server, Settings settings, Compat compat) {
    Set<String> ids = new HashSet<>();
    Region spawn = null;
    for (var r : candidate) {
      checkName(r.id);
      if (!ids.add(r.id.toLowerCase(Locale.ROOT)))
        throw new IllegalArgumentException("Duplicate region ID: " + r.id);
      if (r.kind == null || r.name == null || r.name.isBlank())
        throw new IllegalArgumentException(r.id + ": name/type required");
      if (r.minX > r.maxX
          || r.minZ > r.maxZ
          || Math.abs((long) r.minX) > 29999984
          || Math.abs((long) r.maxX) > 29999984
          || Math.abs((long) r.minZ) > 29999984
          || Math.abs((long) r.maxZ) > 29999984)
        throw new IllegalArgumentException(r.id + ": invalid bounds");
      var key = ResourceLocation.tryParse(r.dimension);
      if (key == null
          || server != null
              && server.getLevel(ResourceKey.create(Registries.DIMENSION, key)) == null)
        throw new IllegalArgumentException(r.id + ": dimension does not exist: " + r.dimension);
      if (r.kind == Kind.SPAWN) {
        if (spawn != null)
          throw new IllegalArgumentException("Only one main spawn region is allowed");
        spawn = r;
      }
      if (r.kind == Kind.STANDALONE && !settings.standalone)
        throw new IllegalArgumentException(
            "Standalone regions are disabled. Enable modules.standaloneRegions and reload.");
      if (r.kind != Kind.CHILD && !r.parent.isEmpty())
        throw new IllegalArgumentException(r.id + ": only child regions have a parent");
      if (r.items == null)
        throw new IllegalArgumentException(r.id + ": items must be a list of item IDs");
      for (String item : r.items) {
        var itemKey = ResourceLocation.tryParse(item);
        if (itemKey == null
            || item.equals("minecraft:air")
            || server != null
                && !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(itemKey))
          throw new IllegalArgumentException(r.id + ": unknown usable item: " + item);
      }
      RegionTitles.color(r.entryColor);
      RegionTitles.color(r.exitColor);
      Compat.validateAccess(r.access);
      if (compat != null && !r.settlementOverride && compat.overlaps(server, r))
        throw new IllegalArgumentException(
            r.id + ": overlaps a Mercenaries settlement; explicit settlementOverride required");
    }
    for (var r : candidate)
      if (r.kind == Kind.CHILD
          && (spawn == null || !r.parent.equalsIgnoreCase(spawn.id) || !spawn.encloses(r)))
        throw new IllegalArgumentException(
            r.id + ": child must be fully inside the main spawn in the same dimension");
    for (int i = 0; i < candidate.size(); i++)
      for (int j = i + 1; j < candidate.size(); j++) {
        Region a = candidate.get(i), b = candidate.get(j);
        if (!a.overlaps(b)) continue;
        if (a.kind == Kind.SPAWN && b.kind == Kind.CHILD
            || b.kind == Kind.SPAWN && a.kind == Kind.CHILD) continue;
        throw new IllegalArgumentException(
            a.id + " overlaps " + b.id + "; overlapping sibling/standalone regions are rejected");
      }
  }

  public static void checkName(String name) {
    if (name == null || !name.matches("[A-Za-z0-9_-]{1,48}"))
      throw new IllegalArgumentException("Use 1–48 letters, numbers, underscores or hyphens.");
  }

  public boolean itemAllowed(ServerLevel level, BlockPos pos, String item) {
    Region region = at(level, pos);
    return region != null && region.items.contains(item);
  }

  public static void checkId(String id) {
    if (id == null || !id.matches("[a-z0-9_-]{1,48}"))
      throw new IllegalArgumentException(
          "ID must contain 1..48 lowercase letters, digits, underscores or hyphens");
  }

  private static String header() {
    return "# Authoritative region configuration. /ffa reload validates the entire file.\n"
        + "# Boundaries are inclusive X/Z blocks and full build height. CHILD parent must be"
        + " SPAWN.\n"
        + "# Every field below is editable; name changes preserve ID. ID renames require"
        + " parent references updated.\n"
        + "# Flag true = allow; omitted = inherit. Adventure mode is mandatory inside"
        + " spawn except children that allow building/breaking.\n"
        + "configVersion = 1\n\n";
  }

  static String quote(String s) {
    return Storage.JSON.toJson(s);
  }

  public static String write(List<Region> list) {
    StringBuilder b = new StringBuilder(header());
    for (var r : list) {
      b.append("[[regions]]\n");
      b.append("id = ")
          .append(quote(r.id))
          .append("\nname = ")
          .append(quote(r.name))
          .append("\ntype = ")
          .append(quote(r.kind.name()))
          .append("\ndimension = ")
          .append(quote(r.dimension))
          .append("\nparent = ")
          .append(quote(r.parent))
          .append("\n");
      b.append("minX = ")
          .append(r.minX)
          .append("\nmaxX = ")
          .append(r.maxX)
          .append("\nminZ = ")
          .append(r.minZ)
          .append("\nmaxZ = ")
          .append(r.maxZ)
          .append("\npriority = ")
          .append(r.priority)
          .append("\nsettlementOverride = ")
          .append(r.settlementOverride)
          .append("\nentry = ")
          .append(quote(r.entry))
          .append("\nexit = ")
          .append(quote(r.exit))
          .append("\nentryColor = ")
          .append(quote(r.entryColor))
          .append("\nexitColor = ")
          .append(quote(r.exitColor))
          .append("\nentryBold = ")
          .append(r.entryBold)
          .append("\nentryItalic = ")
          .append(r.entryItalic)
          .append("\nexitBold = ")
          .append(r.exitBold)
          .append("\nexitItalic = ")
          .append(r.exitItalic)
          .append("\ninteractions = [")
          .append(String.join(", ", r.interactions.stream().map(Regions::quote).toList()))
          .append("]\nitems = [")
          .append(String.join(", ", r.items.stream().map(Regions::quote).toList()))
          .append("]\n");
      b.append("[regions.access]\nmode = ")
          .append(quote(r.access.mode))
          .append("\nvalue = ")
          .append(quote(r.access.value))
          .append("\n[regions.flags]\n");
      r.flags.forEach(
          (f, v) ->
              b.append(f.name().toLowerCase(Locale.ROOT)).append(" = ").append(v).append('\n'));
      b.append('\n');
    }
    return b.toString();
  }

  public static List<Region> read(Path file) {
    try (var c = CommentedFileConfig.of(file)) {
      c.load();
      if (Settings.integer(c, "configVersion", 1, 1) != 1)
        throw new IllegalArgumentException("Unsupported region version");
      Object obj = c.get("regions");
      if (obj == null) return List.of();
      if (!(obj instanceof List<?> list))
        throw new IllegalArgumentException("regions must be [[regions]] entries");
      var out = new ArrayList<Region>();
      for (Object o : list) {
        if (!(o instanceof UnmodifiableConfig v))
          throw new IllegalArgumentException("Invalid region table");
        var r = new Region();
        r.id = Settings.scalar(v, "id", String.class);
        r.name = Settings.scalar(v, "name", String.class);
        r.dimension = Settings.scalar(v, "dimension", String.class);
        r.kind = Kind.valueOf(Settings.scalar(v, "type", String.class));
        r.parent = Settings.scalar(v, "parent", String.class);
        r.minX = Settings.integer(v, "minX", -29999984, 29999984);
        r.maxX = Settings.integer(v, "maxX", -29999984, 29999984);
        r.minZ = Settings.integer(v, "minZ", -29999984, 29999984);
        r.maxZ = Settings.integer(v, "maxZ", -29999984, 29999984);
        r.priority = Settings.integer(v, "priority", -10000, 10000);
        r.settlementOverride = Settings.bool(v, "settlementOverride");
        r.entry = Settings.scalar(v, "entry", String.class);
        r.exit = Settings.scalar(v, "exit", String.class);
        if (v.contains("entryColor")) r.entryColor = Settings.scalar(v, "entryColor", String.class);
        if (v.contains("exitColor")) r.exitColor = Settings.scalar(v, "exitColor", String.class);
        if (v.contains("entryBold")) r.entryBold = Settings.bool(v, "entryBold");
        if (v.contains("entryItalic")) r.entryItalic = Settings.bool(v, "entryItalic");
        if (v.contains("exitBold")) r.exitBold = Settings.bool(v, "exitBold");
        if (v.contains("exitItalic")) r.exitItalic = Settings.bool(v, "exitItalic");
        r.interactions.addAll(Settings.strings(v, "interactions"));
        if (v.contains("items")) r.items.addAll(Settings.strings(v, "items"));
        r.access.mode = Settings.scalar(v, "access.mode", String.class);
        r.access.value = Settings.scalar(v, "access.value", String.class);
        UnmodifiableConfig flags = v.get("flags");
        if (flags != null)
          for (var e : flags.entrySet()) {
            Flag f = Flag.valueOf(e.getKey().toUpperCase(Locale.ROOT));
            if (!(e.getValue() instanceof Boolean))
              throw new IllegalArgumentException(
                  r.id + ": flag " + e.getKey() + " must be true/false");
            r.flags.put(f, (Boolean) e.getValue());
          }
        out.add(r);
      }
      return out;
    }
  }
}
