package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.Flag.*;

import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.common.*;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Narrow region permissions for world editing and player actions, with the normal admin bypass. */
public final class RegionActions {
  private final Administration app;

  public RegionActions(Administration app) {
    this.app = app;
  }

  public static boolean farm(BlockState state) {
    var block = state.getBlock();
    return block instanceof CropBlock
        || block instanceof StemBlock
        || block instanceof AttachedStemBlock
        || block instanceof NetherWartBlock
        || block instanceof SugarCaneBlock
        || block instanceof CocoaBlock
        || block instanceof SweetBerryBushBlock
        || state.is(Blocks.MELON)
        || block instanceof PumpkinBlock;
  }

  public static Model.Flag itemFlag(ItemStack stack) {
    if (stack.isEmpty()) return null;
    if (stack.canPerformAction(ItemAbilities.FIRESTARTER_LIGHT)) return FLINT_AND_STEEL;
    if (stack.canPerformAction(ItemAbilities.SHEARS_HARVEST)) return SHEARS;
    if (stack.canPerformAction(ItemAbilities.HOE_TILL)) return TILLING;
    if (stack.canPerformAction(ItemAbilities.AXE_STRIP)) return STRIP_WOOD;
    if (stack.canPerformAction(ItemAbilities.SHOVEL_FLATTEN)) return DIRT_PATH;
    if (stack.is(Items.BONE_MEAL)
        || stack.getItem() instanceof BlockItem item && farm(item.getBlock().defaultBlockState()))
      return FARMING;
    return null;
  }

  public static Model.Flag blockFlag(BlockState state, ItemStack held) {
    if (itemFlag(held) == SHEARS
        && (state.getBlock() instanceof BeehiveBlock
            || state.is(Blocks.PUMPKIN)
            || state.is(Blocks.TRIPWIRE))) return SHEARS;
    if (itemFlag(held) == FLINT_AND_STEEL
        && (state.getBlock() instanceof CampfireBlock
            || state.getBlock() instanceof CandleBlock
            || state.is(Blocks.TNT))) return FLINT_AND_STEEL;
    return farm(state) ? FARMING : INTERACT;
  }

  public static Model.Flag changeFlag(BlockState old, BlockState current) {
    if (farm(current)) return FARMING;
    if (current.getBlock() instanceof FarmBlock
        || current.is(Blocks.DIRT) && (old.is(Blocks.COARSE_DIRT) || old.is(Blocks.ROOTED_DIRT)))
      return TILLING;
    if (current.getBlock() instanceof DirtPathBlock) return DIRT_PATH;
    var stripped = AxeItem.getAxeStrippingState(old);
    if (stripped != null && stripped.equals(current)) return STRIP_WOOD;
    if (old.getBlock() instanceof GrowingPlantHeadBlock && old.getBlock() == current.getBlock())
      return SHEARS;
    if (current.getBlock() instanceof BaseFireBlock
        || old.getBlock() == current.getBlock()
            && current.hasProperty(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
            && current.getValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
            && !old.getValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT))
      return FLINT_AND_STEEL;
    return BUILD;
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void itemUse(net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent event) {
    var action = itemFlag(event.getItemStack());
    if (action == null) return;
    if (event.getUsePhase()
            == net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent.UsePhase.BLOCK
        && blockFlag(event.getLevel().getBlockState(event.getPos()), event.getItemStack())
            != action) return;
    if (event.getPlayer() instanceof ServerPlayer player
        && (!app.protection.itemAllowed(player, player.blockPosition(), event.getItemStack())
            || !app.protection.itemAllowed(player, event.getPos(), event.getItemStack())))
      event.cancelWithResult(net.minecraft.world.ItemInteractionResult.FAIL);
  }

  public static boolean canDrop(ServerPlayer player) {
    var app = Administration.INSTANCE;
    return app == null
        || app.server == null
        || app.protection == null
        || !app.protection.denied(player, player.serverLevel(), player.blockPosition(), ITEM_DROP);
  }

  public static boolean canCatchFire(ServerPlayer player) {
    var app = Administration.INSTANCE;
    return app == null
        || app.server == null
        || app.protection == null
        || !app.protection.denied(player, player.serverLevel(), player.blockPosition(), CATCH_FIRE);
  }

  public static boolean editsAllowed(Protection protection, ServerPlayer player) {
    for (var flag : List.of(FARMING, TILLING, STRIP_WOOD, DIRT_PATH, FLINT_AND_STEEL, SHEARS))
      if (protection.allowed(player.serverLevel(), player.blockPosition(), flag)) return true;
    return false;
  }

  public static boolean vehicle(Entity target) {
    return target instanceof Boat || target instanceof AbstractMinecart;
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void tool(BlockEvent.BlockToolModificationEvent event) {
    if (!(event.getLevel() instanceof ServerLevel level)) return;
    if (event.getPlayer() instanceof ServerPlayer player && app.compat.admin(player)) return;
    var ability = event.getItemAbility();
    Model.Flag flag =
        ability == ItemAbilities.HOE_TILL
            ? TILLING
            : ability == ItemAbilities.AXE_STRIP
                ? STRIP_WOOD
                : ability == ItemAbilities.SHOVEL_FLATTEN
                    ? DIRT_PATH
                    : ability == ItemAbilities.FIRESTARTER_LIGHT
                        ? FLINT_AND_STEEL
                        : ItemAbilities.DEFAULT_SHEARS_ACTIONS.contains(ability) ? SHEARS : BUILD;
    boolean denied =
        event.getPlayer() instanceof Player player
            ? app.protection.denied(player, level, event.getPos(), flag)
            : !app.protection.allowed(level, event.getPos(), AUTOMATION);
    if (denied || flag == FLINT_AND_STEEL && !app.protection.allowed(level, event.getPos(), FIRE))
      event.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void trample(BlockEvent.FarmlandTrampleEvent event) {
    if (!(event.getLevel() instanceof ServerLevel level)) return;
    boolean denied =
        event.getEntity() instanceof Player player
            ? app.protection.denied(player, level, event.getPos(), FARMING)
            : !app.protection.allowed(level, event.getPos(), MOB_GRIEFING);
    if (denied) event.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void pickup(ItemEntityPickupEvent.Pre event) {
    if (event.getPlayer() instanceof ServerPlayer player
        && (app.protection.denied(player, player.serverLevel(), player.blockPosition(), ITEM_PICKUP)
            || app.protection.denied(
                player, player.serverLevel(), event.getItemEntity().blockPosition(), ITEM_PICKUP)))
      event.setCanPickup(TriState.FALSE);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void mount(EntityMountEvent event) {
    if (!event.isMounting()
        || !(event.getEntityMounting() instanceof ServerPlayer player)
        || !vehicle(event.getEntityBeingMounted())) return;
    if (app.protection.denied(player, player.serverLevel(), player.blockPosition(), VEHICLE_ENTER)
        || app.protection.denied(
            player,
            player.serverLevel(),
            event.getEntityBeingMounted().blockPosition(),
            VEHICLE_ENTER)) event.setCanceled(true);
  }
}
