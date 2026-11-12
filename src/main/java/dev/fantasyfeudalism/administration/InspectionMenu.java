package dev.fantasyfeudalism.administration;

import dev.fantasyfeudalism.administration.mixin.PlayerListAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Native chest screen backed by the target's real inventory, not a duplicated snapshot. */
public final class InspectionMenu extends ChestMenu {
  public final ServerPlayer target;
  private final ServerPlayer viewer;
  private final Administration app;
  private boolean saveFailed;

  private InspectionMenu(int id, Inventory inventory, ServerPlayer target, Administration app) {
    super(MenuType.GENERIC_9x6, id, inventory, new View(target), 6);
    this.target = target;
    this.viewer = (ServerPlayer) inventory.player;
    this.app = app;
    for (int i = 0; i < 54; i++) {
      final int index = i;
      Slot previous = slots.get(i);
      Slot guarded =
          new Slot(getContainer(), i, previous.x, previous.y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
              return index < 41;
            }

            @Override
            public boolean mayPickup(Player player) {
              return index < 41;
            }
          };
      guarded.index = previous.index;
      slots.set(i, guarded);
    }
  }

  public static void open(Administration app, ServerPlayer viewer, ServerPlayer target) {
    if (!app.compat.admin(viewer)) throw new IllegalArgumentException("Administrator only");
    if (viewer == target)
      throw new IllegalArgumentException("Use your normal inventory for yourself");
    if (busy(app, target) || busy(app, viewer))
      throw new IllegalArgumentException("Finish the existing inventory inspection first");
    if (app.storage.state().trades.containsKey(target.getUUID().toString())
        || app.storage.state().trades.containsKey(viewer.getUUID().toString()))
      throw new IllegalArgumentException(
          "Recover pending item transactions before inventory editing");
    viewer.openMenu(
        new SimpleMenuProvider(
            (id, inv, player) -> new InspectionMenu(id, inv, target, app),
            Component.literal("Inventory: " + target.getGameProfile().getName())));
    Teleports.tell(viewer, "Inventory opened.");
  }

  public static boolean busy(Administration app, ServerPlayer player) {
    for (var online : app.server.getPlayerList().getPlayers())
      if (online.containerMenu instanceof InspectionMenu menu
          && (online == player || menu.target == player)) return true;
    return false;
  }

  public static void closeFor(Administration app, ServerPlayer player) {
    for (var online : app.server.getPlayerList().getPlayers())
      if (online.containerMenu instanceof InspectionMenu menu
          && (online == player || menu.target == player)) {
        online.closeContainer();
        if (menu.saveFailed)
          throw new IllegalStateException(
              "Inventory save failed; resolve the player-data error before trading");
        Teleports.tell(online, "Inventory inspection closed for a player transaction.");
      }
  }

  @Override
  public boolean stillValid(Player player) {
    return !saveFailed
        && player == viewer
        && app.compat.admin(viewer)
        && app.server.getPlayerList().getPlayer(target.getUUID()) == target
        && !app.storage.state().trades.containsKey(target.getUUID().toString());
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player player) {
    if (!stillValid(player)) {
      viewer.closeContainer();
      return;
    }
    super.clicked(slot, button, type, player);
    target.inventoryMenu.broadcastChanges();
    if (!persist()) {
      viewer.closeContainer();
      return;
    }
    app.storage.audit(
        app.settings,
        viewer.getUUID().toString(),
        "inventory edit " + target.getUUID(),
        Teleports.point(viewer).describe(),
        "SUCCESS vanilla inventories saved");
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (!stillValid(player) || index < 0 || index >= slots.size() || index >= 41 && index < 54)
      return ItemStack.EMPTY;
    return super.quickMoveStack(player, index);
  }

  @Override
  public void removed(Player player) {
    super.removed(player);
    target.inventoryMenu.broadcastChanges();
    if (app.server != null) persist();
  }

  private boolean persist() {
    try {
      save(target);
      save(viewer);
      return true;
    } catch (Exception error) {
      saveFailed = true;
      Administration.LOG.error(
          "Inventory editing save failed for viewer {} and target {}",
          viewer.getUUID(),
          target.getUUID(),
          error);
      Teleports.tell(
          viewer,
          "Inventory editing stopped: player-data save did not verify. Preserve current server data"
              + " and resolve the save error before further edits.");
      app.storage.audit(
          app.settings,
          viewer.getUUID().toString(),
          "inventory edit " + target.getUUID(),
          Teleports.point(viewer).describe(),
          "FAILED player-data save verification");
      return false;
    }
  }

  private void save(ServerPlayer player) throws Exception {
    if (app.server.getPlayerList().getPlayer(player.getUUID()) != player) return;
    ((PlayerListAccess) app.server.getPlayerList()).ffa$save(player);
    var file =
        app.server
            .getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR)
            .resolve(player.getUUID() + ".dat");
    var saved =
        net.minecraft.nbt.NbtIo.readCompressed(
            file, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
    if (!saved
        .getList("Inventory", 10)
        .equals(player.getInventory().save(new net.minecraft.nbt.ListTag())))
      throw new IllegalStateException("Inventory save did not match current items");
  }

  private static final class View implements Container {
    private final ServerPlayer target;

    View(ServerPlayer target) {
      this.target = target;
    }

    public int getContainerSize() {
      return 54;
    }

    public boolean isEmpty() {
      return target.getInventory().isEmpty();
    }

    public ItemStack getItem(int slot) {
      return slot < 41 ? target.getInventory().getItem(slot) : ItemStack.EMPTY;
    }

    public ItemStack removeItem(int slot, int amount) {
      return slot < 41 ? target.getInventory().removeItem(slot, amount) : ItemStack.EMPTY;
    }

    public ItemStack removeItemNoUpdate(int slot) {
      return slot < 41 ? target.getInventory().removeItemNoUpdate(slot) : ItemStack.EMPTY;
    }

    public void setItem(int slot, ItemStack item) {
      if (slot < 41) target.getInventory().setItem(slot, item);
    }

    public void setChanged() {
      target.getInventory().setChanged();
    }

    public boolean stillValid(Player player) {
      return !target.hasDisconnected();
    }

    public boolean canPlaceItem(int slot, ItemStack stack) {
      return slot < 41;
    }

    public void clearContent() {
      throw new UnsupportedOperationException("Use explicit inventory edits");
    }
  }
}
