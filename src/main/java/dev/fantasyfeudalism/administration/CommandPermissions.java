package dev.fantasyfeudalism.administration;

import com.mojang.brigadier.tree.CommandNode;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Keeps each command's original predicate as fallback, including any FTB Ranks wrapper. */
public final class CommandPermissions {
  private final Administration app;
  private final Map<CommandNode<CommandSourceStack>, String> paths = new IdentityHashMap<>();
  private final Map<CommandNode<CommandSourceStack>, Predicate<CommandSourceStack>> originals =
      new IdentityHashMap<>();
  private final Field requirement;
  private final com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher;

  public CommandPermissions(Administration app) throws ReflectiveOperationException {
    this.app = app;
    requirement = CommandNode.class.getDeclaredField("requirement");
    requirement.setAccessible(true);
    dispatcher = app.server.getCommands().getDispatcher();
    var root = dispatcher.getRoot();
    index(root, "command", Collections.newSetFromMap(new IdentityHashMap<>()));
    for (var entry : paths.entrySet()) {
      var node = entry.getKey();
      if (entry.getValue().equals("command.ffa") || entry.getValue().startsWith("command.ffa.")) {
        Predicate<CommandSourceStack> nativeRequirement = Commands.nativeRequirement(node);
        if (nativeRequirement != null) {
          Predicate<CommandSourceStack> original = node.getRequirement();
          originals.put(node, original);
          requirement.set(
              node,
              (Predicate<CommandSourceStack>)
                  source -> nativeRequirement.test(source) && original.test(source));
        }
        continue;
      }
      String permission = canonical(node);
      try {
        Ranks.checkNode(permission.toLowerCase(Locale.ROOT), false);
      } catch (IllegalArgumentException unsupported) {
        continue;
      }
      Predicate<CommandSourceStack> original = node.getRequirement();
      originals.put(node, original);
      requirement.set(
          node,
          (Predicate<CommandSourceStack>)
              source -> {
                var player = source.getPlayer();
                if (player == null
                    || player instanceof FakePlayer
                    || app.compat == null
                    || source.hasPermission(app.settings.operatorLevel)
                    || app.compat.admin(player)) return original.test(source);
                return app.compat
                    .decision(player, permission)
                    .orElseGet(() -> original.test(source));
              });
    }
  }

  private void index(
      CommandNode<CommandSourceStack> node,
      String path,
      Set<CommandNode<CommandSourceStack>> seen) {
    if (!seen.add(node)) return;
    for (var child : node.getChildren()) {
      String name = path + "." + child.getName().replace("*", "all");
      paths.put(child, name);
      index(child, name, seen);
    }
  }

  private String canonical(CommandNode<CommandSourceStack> node) {
    Set<CommandNode<CommandSourceStack>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    while (node.getRedirect() != null && paths.containsKey(node.getRedirect()) && seen.add(node))
      node = node.getRedirect();
    return paths.get(node);
  }

  public boolean current() {
    return dispatcher == app.server.getCommands().getDispatcher();
  }

  public Set<String> nodes() {
    Set<String> nodes = new TreeSet<>();
    for (var node : originals.keySet())
      if (!paths.get(node).equals("command.ffa") && !paths.get(node).startsWith("command.ffa."))
        nodes.add(canonical(node).toLowerCase(Locale.ROOT));
    return nodes;
  }

  public void close() {
    originals.forEach(
        (node, original) -> {
          try {
            requirement.set(node, original);
          } catch (IllegalAccessException e) {
            Administration.LOG.error("Cannot restore command permission predicate", e);
          }
        });
    originals.clear();
    paths.clear();
  }
}
