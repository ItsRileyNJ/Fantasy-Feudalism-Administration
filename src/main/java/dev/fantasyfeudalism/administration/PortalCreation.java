package dev.fantasyfeudalism.administration;

import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;

/** Creation authority follows synchronous player item use, commands and portal travel. */
public final class PortalCreation {
  private static final ThreadLocal<Deque<Boolean>> ACTORS =
      ThreadLocal.withInitial(ArrayDeque::new);

  public static void enter(boolean admin) {
    ACTORS.get().push(admin);
  }

  public static void leave() {
    var actors = ACTORS.get();
    actors.pop();
    if (actors.isEmpty()) ACTORS.remove();
  }

  public static boolean adminActor() {
    return !ACTORS.get().isEmpty() && ACTORS.get().peek();
  }

  public static boolean permitted() {
    var a = Administration.INSTANCE;
    return a == null
        || a.server == null
        || !a.settings.adminPortalCreationOnly
        || !ACTORS.get().isEmpty() && ACTORS.get().peek();
  }

  public static boolean admin(CommandSourceStack source) {
    return source.getPlayer() == null
        ? source.hasPermission(Administration.INSTANCE.settings.operatorLevel)
        : Administration.INSTANCE.compat.admin(source.getPlayer());
  }

  public static boolean admin(ServerPlayer player) {
    var a = Administration.INSTANCE;
    return a != null && a.compat != null && a.compat.admin(player);
  }

  public static boolean denyEye(UseOnContext context) {
    var a = Administration.INSTANCE;
    return a != null
        && a.server != null
        && a.settings.adminPortalCreationOnly
        && context.getPlayer() instanceof ServerPlayer player
        && !admin(player)
        && context.getLevel().getBlockState(context.getClickedPos()).is(Blocks.END_PORTAL_FRAME)
        && context.getItemInHand().is(net.minecraft.world.item.Items.ENDER_EYE);
  }
}
