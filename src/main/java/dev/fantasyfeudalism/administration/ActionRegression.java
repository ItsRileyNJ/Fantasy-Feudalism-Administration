package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;
import static dev.fantasyfeudalism.administration.RegressionTests.*;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

final class ActionRegression {
  private static void flag(Administration app, String key, Flag flag, boolean value)
      throws Exception {
    var regions = app.regions.copy();
    regions.stream().filter(r -> r.id.equals(key)).findFirst().orElseThrow().flags.put(flag, value);
    app.regions.edit(regions, app.server, app.settings, app.compat);
  }

  private static InteractionResult use(ServerPlayer player, BlockPos at, ItemStack stack) {
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    return player.gameMode.useItemOn(
        player,
        player.serverLevel(),
        stack,
        InteractionHand.MAIN_HAND,
        new BlockHitResult(Vec3.atCenterOf(at).add(0, .5, 0), Direction.UP, at, false));
  }

  static void verify(GameTestHelper h, ServerPlayer player, Administration app) throws Exception {
    String ranksFile = java.nio.file.Files.readString(app.ranks.file);
    var saved = app.storage.copy();
    var regions = app.regions.copy();
    var inventory = player.getInventory().save(new ListTag());
    var oldMode = player.gameMode.getGameModeForPlayer();
    var position = player.position();
    var boat = EntityType.BOAT.create(h.getLevel());
    var cart = EntityType.MINECART.create(h.getLevel());
    var pickup = new ItemEntity(h.getLevel(), 0, 0, 0, new ItemStack(Items.EMERALD, 2));
    try {
      var parent = regions.stream().filter(r -> r.kind == Kind.SPAWN).findFirst().orElseThrow();
      var child = new Region();
      child.id = child.name = "ActionYard";
      child.kind = Kind.CHILD;
      child.parent = parent.id;
      child.dimension = parent.dimension;
      child.settlementOverride = true;
      var lo = h.absolutePos(new BlockPos(3, 1, 3));
      var hi = h.absolutePos(new BlockPos(6, 1, 6));
      child.minX = lo.getX();
      child.minZ = lo.getZ();
      child.maxX = hi.getX();
      child.maxZ = hi.getZ();
      child.flags.put(Flag.BUILD, false);
      child.flags.put(Flag.BREAK, false);
      child.flags.put(Flag.INTERACT, false);
      for (var f : Flag.values()) if (f.upgradeDefault != null) child.flags.put(f, false);
      app.regions.edit(List.of(parent, child), app.server, app.settings, app.compat);
      player.setPos(h.absoluteVec(new Vec3(5.5, 1, 5.5)));
      player.setGameMode(GameType.SURVIVAL);
      app.protection.update(player);
      BlockPos soil = h.absolutePos(new BlockPos(4, 0, 4));
      h.getLevel().setBlock(soil, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
      h.getLevel().setBlock(soil.above(), Blocks.AIR.defaultBlockState(), 3);
      use(player, soil, new ItemStack(Items.IRON_HOE));
      h.assertTrue(
          h.getLevel().getBlockState(soil).is(Blocks.GRASS_BLOCK), "Tilling defaults to blocked");
      flag(app, child.id, Flag.TILLING, true);
      app.protection.update(player);
      h.assertTrue(
          use(player, soil, new ItemStack(Items.IRON_HOE)).consumesAction()
              && h.getLevel().getBlockState(soil).is(Blocks.FARMLAND),
          "Tilling works with build and interact disabled");
      flag(app, child.id, Flag.FARMING, true);
      app.protection.update(player);
      h.assertTrue(
          use(player, soil, new ItemStack(Items.WHEAT_SEEDS, 3)).consumesAction()
              && h.getLevel().getBlockState(soil.above()).is(Blocks.WHEAT),
          "Crop planting works independently of building");
      use(player, soil.above(), new ItemStack(Items.BONE_MEAL, 3));
      h.assertTrue(
          h.getLevel().getBlockState(soil.above()).getValue(CropBlock.AGE) > 0,
          "Bone meal grows crops while general building remains blocked");
      h.assertTrue(
          player.gameMode.destroyBlock(soil.above()) && h.getLevel().isEmptyBlock(soil.above()),
          "Farming independently permits crop harvesting");
      BlockPos stone = h.absolutePos(new BlockPos(4, 1, 3));
      h.getLevel().setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
      h.assertFalse(
          player.gameMode.destroyBlock(stone), "Farming does not permit ordinary block breaking");
      BlockPos log = h.absolutePos(new BlockPos(3, 1, 4));
      h.getLevel().setBlock(log, Blocks.OAK_LOG.defaultBlockState(), 3);
      use(player, log, new ItemStack(Items.IRON_AXE));
      h.assertTrue(
          h.getLevel().getBlockState(log).is(Blocks.OAK_LOG), "Log stripping has its own denial");
      flag(app, child.id, Flag.STRIP_WOOD, true);
      use(player, log, new ItemStack(Items.IRON_AXE));
      h.assertTrue(
          h.getLevel().getBlockState(log).is(Blocks.STRIPPED_OAK_LOG),
          "Log stripping works with building disabled");
      BlockPos path = h.absolutePos(new BlockPos(4, 0, 6));
      h.getLevel().setBlock(path, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
      use(player, path, new ItemStack(Items.IRON_SHOVEL));
      h.assertTrue(
          h.getLevel().getBlockState(path).is(Blocks.GRASS_BLOCK),
          "Path creation has its own denial");
      flag(app, child.id, Flag.DIRT_PATH, true);
      use(player, path, new ItemStack(Items.IRON_SHOVEL));
      h.assertTrue(
          h.getLevel().getBlockState(path).is(Blocks.DIRT_PATH),
          "Path creation works with building disabled");
      player.getInventory().selected = 0;
      player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 3));
      h.assertFalse(
          player.drop(true), "Blocked selected-stack drop returns before inventory mutation");
      player.inventoryMenu.clicked(36, 1, ClickType.THROW, player);
      h.assertTrue(
          player.getInventory().getItem(0).getCount() == 3,
          "Blocked inventory throw preserves the stack");
      player.inventoryMenu.setCarried(new ItemStack(Items.DIAMOND, 2));
      player.inventoryMenu.clicked(-999, 0, ClickType.PICKUP, player);
      h.assertTrue(
          player.inventoryMenu.getCarried().getCount() == 2,
          "Blocked cursor drop preserves carried items");
      player.inventoryMenu.setCarried(ItemStack.EMPTY);
      pickup.setPos(player.position());
      pickup.setNoPickUpDelay();
      h.getLevel().addFreshEntity(pickup);
      int emeralds = player.getInventory().countItem(Items.EMERALD);
      pickup.playerTouch(player);
      h.assertTrue(
          player.getInventory().countItem(Items.EMERALD) == emeralds
              && pickup.getItem().getCount() == 2,
          "Blocked pickup leaves the world item intact");
      flag(app, child.id, Flag.ITEM_PICKUP, true);
      pickup.playerTouch(player);
      h.assertTrue(
          player.getInventory().countItem(Items.EMERALD) == emeralds + 2,
          "Pickup can be enabled independently of dropping");
      flag(app, child.id, Flag.ITEM_DROP, true);
      h.assertTrue(
          player.drop(true) && player.getInventory().getItem(0).isEmpty(),
          "Dropping can be enabled independently");
      boat.setPos(player.position());
      cart.setPos(player.position());
      h.getLevel().addFreshEntity(boat);
      h.getLevel().addFreshEntity(cart);
      h.assertFalse(player.startRiding(boat, true), "Boat boarding denied by its own flag");
      h.assertFalse(player.startRiding(cart, true), "Minecart boarding denied by its own flag");
      flag(app, child.id, Flag.VEHICLE_ENTER, true);
      h.assertTrue(
          player.startRiding(boat, true),
          "Boat boarding enabled without general entity interaction");
      player.stopRiding();
      h.assertTrue(
          player.startRiding(cart, true),
          "Minecart boarding enabled without general entity interaction");
      flag(app, child.id, Flag.VEHICLE_ENTER, false);
      player.stopRiding();
      h.assertFalse(player.isPassenger(), "Disembarking is always possible");
      player.clearFire();
      player.igniteForSeconds(5);
      h.assertTrue(
          player.getRemainingFireTicks() <= 0,
          "Catch-fire flag prevents ignition before fire ticks are assigned");
      flag(app, child.id, Flag.CATCH_FIRE, true);
      player.igniteForSeconds(5);
      h.assertTrue(
          player.getRemainingFireTicks() > 0,
          "Catching fire can be enabled separately from damage");
      player.setPos(h.absoluteVec(new Vec3(2.5, 1, 4.5)));
      player.baseTick();
      h.assertTrue(
          player.getRemainingFireTicks() <= 0,
          "Entering a no-ignition parent extinguishes existing fire");
      player.setPos(h.absoluteVec(new Vec3(5.5, 1, 5.5)));
      BlockPos fire = h.absolutePos(new BlockPos(5, 0, 3));
      h.getLevel().setBlock(fire.above(), Blocks.AIR.defaultBlockState(), 3);
      flag(app, child.id, Flag.FIRE, true);
      use(player, fire, new ItemStack(Items.FLINT_AND_STEEL));
      h.assertTrue(
          h.getLevel().isEmptyBlock(fire.above()),
          "Flint and steel is blocked even when world fire is allowed");
      flag(app, child.id, Flag.FLINT_AND_STEEL, true);
      use(player, fire, new ItemStack(Items.FLINT_AND_STEEL));
      h.assertTrue(
          h.getLevel().getBlockState(fire.above()).getBlock() instanceof BaseFireBlock,
          "Flint and steel works when both tool and world-fire flags allow it");
      h.getLevel().setBlock(fire.above(), Blocks.AIR.defaultBlockState(), 3);
      BlockPos pumpkin = h.absolutePos(new BlockPos(6, 1, 4));
      h.getLevel().setBlock(pumpkin, Blocks.PUMPKIN.defaultBlockState(), 3);
      use(player, pumpkin, new ItemStack(Items.SHEARS));
      h.assertTrue(
          h.getLevel().getBlockState(pumpkin).is(Blocks.PUMPKIN),
          "Shears blocked independently of farming");
      flag(app, child.id, Flag.SHEARS, true);
      use(player, pumpkin, new ItemStack(Items.SHEARS));
      h.assertTrue(
          h.getLevel().getBlockState(pumpkin).is(Blocks.CARVED_PUMPKIN),
          "Shears can carve without opening general block interaction");
      player.startFallFlying();
      app.protection.update(player);
      h.assertFalse(player.isFallFlying(), "Existing flight flag also stops elytra gliding");
      flag(app, child.id, Flag.FLIGHT, true);
      app.protection.update(player);
      player.setItemSlot(
          net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
      player.setOnGround(false);
      h.assertTrue(
          player.tryToStartFallFlying(),
          "Existing flight flag permits a valid elytra launch when enabled");
      player.stopFallFlying();

      var console = app.server.createCommandSourceStack();
      app.server
          .getCommands()
          .getDispatcher()
          .execute("ffa chat set ffaPlayer name_underlined true", console);
      var rendered =
          app.chatStyles.format(player, net.minecraft.network.chat.Component.literal("Original"));
      h.assertTrue(
          rendered.getSiblings().getLast().getStyle().isUnderlined(),
          "Administrator-assigned name underline renders");
      app.server
          .getCommands()
          .getDispatcher()
          .execute("ffa ranks node add member ffa.chat.style.title_underlined true", console);
      app.server
          .getCommands()
          .getDispatcher()
          .execute("ffa ranks node add member ffa.chat.style.title Banner", console);
      rendered =
          app.chatStyles.format(player, net.minecraft.network.chat.Component.literal("Original"));
      h.assertTrue(
          rendered.getSiblings().getFirst().getStyle().isUnderlined(),
          "Rank-assigned title underline renders");
      app.server
          .getCommands()
          .getDispatcher()
          .execute("ffa ranks node add member ffa.chat.format true", console);
      h.assertTrue(
          app.server
                  .getCommands()
                  .getDispatcher()
                  .execute("ffa chat titleunderline false", player.createCommandSourceStack())
              == 1,
          "Rank-authorized players can change their own underline");
      rendered =
          app.chatStyles.format(player, net.minecraft.network.chat.Component.literal("Original"));
      h.assertFalse(
          rendered.getSiblings().getFirst().getStyle().isUnderlined(),
          "Personal underline false overrides rank true");
      app.server
          .getCommands()
          .getDispatcher()
          .execute("ffa chat inherit title_underlined", player.createCommandSourceStack());
      h.assertTrue(
          app.chatStyles
              .format(player, net.minecraft.network.chat.Component.literal("Original"))
              .getSiblings()
              .getFirst()
              .getStyle()
              .isUnderlined(),
          "Clearing personal underline restores the rank style");

      flag(app, child.id, Flag.ITEM_DROP, false);
      flag(app, child.id, Flag.CATCH_FIRE, false);
      app.server
          .getPlayerList()
          .getOps()
          .add(
              new net.minecraft.server.players.ServerOpListEntry(
                  player.getGameProfile(), 4, false));
      player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 1));
      h.assertTrue(player.drop(true), "Administrators bypass the new item-drop restriction");
      player.igniteForSeconds(5);
      h.assertTrue(
          player.getRemainingFireTicks() > 0,
          "Administrators bypass the new catch-fire restriction");
    } finally {
      app.server.getPlayerList().deop(player.getGameProfile());
      Storage.atomic(app.ranks.file, ranksFile);
      app.ranks.reload();
      player.stopRiding();
      player.stopFallFlying();
      player.clearFire();
      boat.discard();
      cart.discard();
      pickup.discard();
      player.inventoryMenu.setCarried(ItemStack.EMPTY);
      player.getInventory().load(inventory);
      app.regions.edit(regions, app.server, app.settings, app.compat);
      app.storage.commit(saved);
      player.setGameMode(oldMode);
      player.setPos(position);
      app.protection.update(player);
    }
  }
}
