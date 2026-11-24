package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;
import static dev.fantasyfeudalism.administration.RegressionTests.*;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

final class StandRegression {
  private static Region region(
      String id, Kind kind, String dimension, BlockPos low, BlockPos high) {
    var r = new Region();
    r.id = r.name = id;
    r.kind = kind;
    r.dimension = dimension;
    r.minX = low.getX();
    r.minZ = low.getZ();
    r.maxX = high.getX();
    r.maxZ = high.getZ();
    r.settlementOverride = true;
    return r;
  }

  private static ItemStack gear(Item item) {
    var stack = new ItemStack(item);
    stack.set(DataComponents.CUSTOM_NAME, Component.literal("Protected display"));
    return stack;
  }

  private static void flag(Administration app, String id, Flag flag, boolean value)
      throws Exception {
    var regions = app.regions.copy();
    regions.stream().filter(r -> r.id.equals(id)).findFirst().orElseThrow().flags.put(flag, value);
    app.regions.edit(regions, app.server, app.settings, app.compat);
  }

  private static void reset(
      ServerPlayer player, LivingEntity stand, EquipmentSlot slot, ItemStack original) {
    player.getInventory().clearContent();
    for (var s : EquipmentSlot.values()) stand.setItemSlot(s, ItemStack.EMPTY);
    stand.setItemSlot(slot, original.copy());
  }

  private static void packet(
      ServerPlayer player, LivingEntity stand, InteractionHand hand, Vec3 hit) {
    player.connection.handleInteract(
        ServerboundInteractPacket.createInteractionPacket(stand, false, hand, hit));
  }

  private static void unchanged(
      GameTestHelper h,
      ServerPlayer player,
      LivingEntity stand,
      EquipmentSlot slot,
      ItemStack original,
      String inventory,
      String reason) {
    h.assertTrue(
        ItemStack.matches(stand.getItemBySlot(slot), original)
            && player.getInventory().save(new ListTag()).toString().equals(inventory),
        reason);
  }

  static void verify(GameTestHelper h, Administration app) throws Exception {
    check(h, app, EntityType.ARMOR_STAND, false);
    if (net.neoforged.fml.ModList.get().isLoaded("supplementaries")) {
      var type =
          BuiltInRegistries.ENTITY_TYPE
              .getOptional(ResourceLocation.fromNamespaceAndPath("supplementaries", "hat_stand"))
              .orElseThrow();
      check(h, app, type, true);
    }
  }

  private static void check(GameTestHelper h, Administration app, EntityType<?> type, boolean hat)
      throws Exception {
    var saved = app.storage.copy();
    var regions = app.regions.copy();
    var player = player(h, hat ? "hatStandViewer" : "armorViewer");
    var stand = (LivingEntity) type.create(h.getLevel());
    try {
      h.assertFalse(app.compat.admin(player), "Stand test uses a genuine non-admin player");
      stand.setNoGravity(true);
      if (stand instanceof ArmorStand armor) armor.setShowArms(true);
      var parent =
          region(
              "StandSpawn",
              Kind.SPAWN,
              h.getLevel().dimension().location().toString(),
              h.absolutePos(new BlockPos(1, 1, 1)),
              h.absolutePos(new BlockPos(8, 1, 8)));
      var child =
          region(
              "Wardrobe",
              Kind.CHILD,
              parent.dimension,
              h.absolutePos(new BlockPos(4, 1, 4)),
              h.absolutePos(new BlockPos(6, 1, 6)));
      child.parent = parent.id;
      app.regions.edit(List.of(parent, child), app.server, app.settings, app.compat);
      stand.setPos(h.absoluteVec(new Vec3(3.5, 1, 3.5)));
      h.getLevel().addFreshEntity(stand);
      player.setPos(h.absoluteVec(new Vec3(3.5, 1, 5.5)));
      player.setGameMode(GameType.SURVIVAL);
      h.assertTrue(
          StandProtection.displayStand(stand), "Armor and hat stands share the display category");
      EquipmentSlot slot = hat ? EquipmentSlot.HEAD : EquipmentSlot.CHEST;
      ItemStack original = gear(hat ? Items.DIAMOND_HELMET : Items.DIAMOND_CHESTPLATE);
      Vec3 hit = new Vec3(0, hat ? 1.7 : 1.1, 0);
      reset(player, stand, slot, original);
      String inventory = player.getInventory().save(new ListTag()).toString();
      packet(player, stand, InteractionHand.MAIN_HAND, hit);
      unchanged(
          h,
          player,
          stand,
          slot,
          original,
          inventory,
          "Default spawn flags block native client equipment removal");
      stand.interactAt(player, hit, InteractionHand.MAIN_HAND);
      unchanged(
          h,
          player,
          stand,
          slot,
          original,
          inventory,
          "Direct mod/API interaction cannot bypass default spawn equipment protection");

      // Exercise the mutation point independently of interactAt and NeoForge packet events.
      var swap =
          stand
              .getClass()
              .getDeclaredMethod(
                  "swapItem",
                  Player.class,
                  EquipmentSlot.class,
                  ItemStack.class,
                  InteractionHand.class);
      swap.setAccessible(true);
      h.assertFalse(
          (boolean) swap.invoke(stand, player, slot, ItemStack.EMPTY, InteractionHand.MAIN_HAND),
          "Direct equipment swaps cannot bypass display protection");
      unchanged(h, player, stand, slot, original, inventory, "Blocked direct swap preserves gear");

      flag(app, parent.id, Flag.ENTITY_INTERACT, true);
      packet(player, stand, InteractionHand.MAIN_HAND, hit);
      unchanged(
          h,
          player,
          stand,
          slot,
          original,
          inventory,
          "Generic entity interaction cannot bypass containers=false on a display stand");
      stand.interactAt(player, hit, InteractionHand.OFF_HAND);
      unchanged(
          h,
          player,
          stand,
          slot,
          original,
          inventory,
          "Offhand and direct interactions enforce locked display inventory");
      var replacement = gear(hat ? Items.GOLDEN_HELMET : Items.GOLDEN_CHESTPLATE);
      player.setItemInHand(InteractionHand.MAIN_HAND, replacement.copy());
      inventory = player.getInventory().save(new ListTag()).toString();
      stand.interactAt(player, hit, InteractionHand.MAIN_HAND);
      unchanged(
          h,
          player,
          stand,
          slot,
          original,
          inventory,
          "Blocked equipment replacement preserves both the display and held item components");
      stand.setItemSlot(slot, ItemStack.EMPTY);
      player.setGameMode(GameType.CREATIVE);
      inventory = player.getInventory().save(new ListTag()).toString();
      stand.interactAt(player, hit, InteractionHand.MAIN_HAND);
      unchanged(
          h,
          player,
          stand,
          slot,
          ItemStack.EMPTY,
          inventory,
          "Non-admin Creative Mode cannot equip a locked stand");
      player.setGameMode(GameType.SURVIVAL);
      reset(player, stand, slot, original);

      flag(app, parent.id, Flag.CONTAINERS, true);
      flag(app, parent.id, Flag.ENTITY_INTERACT, false);
      inventory = player.getInventory().save(new ListTag()).toString();
      stand.interactAt(player, hit, InteractionHand.MAIN_HAND);
      unchanged(
          h,
          player,
          stand,
          slot,
          original,
          inventory,
          "containers=true does not override entity_interact=false");

      flag(app, child.id, Flag.CONTAINERS, true);
      flag(app, child.id, Flag.ENTITY_INTERACT, true);
      stand.setPos(h.absoluteVec(new Vec3(5.5, 1, 5.5)));
      player.setPos(h.absoluteVec(new Vec3(5.5, 1, 7.5)));
      stand.interactAt(player, hit, InteractionHand.MAIN_HAND);
      unchanged(
          h,
          player,
          stand,
          slot,
          original,
          inventory,
          "Protected actor position blocks reaching into an allowed child wardrobe");
      player.setPos(h.absoluteVec(new Vec3(5.5, 1, 4.5)));
      packet(player, stand, InteractionHand.MAIN_HAND, hit);
      h.assertTrue(
          stand.getItemBySlot(slot).isEmpty()
              && ItemStack.matches(player.getMainHandItem(), original),
          "Explicitly allowing both child flags permits normal component-preserving equipment"
              + " access");
      reset(player, stand, slot, original);
      stand.setPos(h.absoluteVec(new Vec3(3.5, 1, 5.5)));
      inventory = player.getInventory().save(new ListTag()).toString();
      stand.interactAt(player, hit, InteractionHand.MAIN_HAND);
      unchanged(
          h,
          player,
          stand,
          slot,
          original,
          inventory,
          "Allowed actor cannot access a stand in a protected parent");
      stand.setPos(h.absoluteVec(new Vec3(8.5, 1, 5.5)));
      player.setPos(h.absoluteVec(new Vec3(9.5, 1, 5.5)));
      stand.interactAt(player, hit, InteractionHand.MAIN_HAND);
      unchanged(
          h,
          player,
          stand,
          slot,
          original,
          inventory,
          "Standing outside spawn cannot steal equipment from a protected stand");
      stand.setPos(h.absoluteVec(new Vec3(9.5, 1, 5.5)));
      player.setPos(h.absoluteVec(new Vec3(8.5, 1, 5.5)));
      stand.interactAt(player, hit, InteractionHand.MAIN_HAND);
      unchanged(
          h,
          player,
          stand,
          slot,
          original,
          inventory,
          "Protected actor cannot bypass restrictions by targeting a wilderness stand");
      player.setPos(h.absoluteVec(new Vec3(10.5, 1, 5.5)));
      packet(player, stand, InteractionHand.MAIN_HAND, hit);
      h.assertTrue(
          stand.getItemBySlot(slot).isEmpty()
              && ItemStack.matches(player.getMainHandItem(), original),
          "Unprotected wilderness stand behavior is unchanged");

      reset(player, stand, slot, original);
      stand.setPos(h.absoluteVec(new Vec3(3.5, 1, 3.5)));
      player.setPos(h.absoluteVec(new Vec3(3.5, 1, 5.5)));
      flag(app, parent.id, Flag.ANIMAL_DAMAGE, true);
      var attack = new AttackEntityEvent(player, stand);
      NeoForge.EVENT_BUS.post(attack);
      h.assertTrue(
          attack.isCanceled(),
          "Display stands use hanging_damage, even when ordinary animal damage is allowed");
      var arrow = EntityType.ARROW.create(h.getLevel());
      arrow.setOwner(player);
      var impact = new ProjectileImpactEvent(arrow, new EntityHitResult(stand));
      NeoForge.EVENT_BUS.post(impact);
      h.assertTrue(
          impact.isCanceled(),
          "The same hanging protection covers projectiles against armor and hat stands");
      app.server
          .getPlayerList()
          .getOps()
          .add(
              new net.minecraft.server.players.ServerOpListEntry(
                  player.getGameProfile(), 4, false));
      player.setGameMode(GameType.CREATIVE);
      stand.interactAt(player, hit, InteractionHand.MAIN_HAND);
      h.assertTrue(
          stand.getItemBySlot(slot).isEmpty()
              && ItemStack.matches(player.getMainHandItem(), original),
          "Administrators retain full display equipment access");

      if (!hat) {
        app.server.getPlayerList().deop(player.getGameProfile());
        player.setGameMode(GameType.SURVIVAL);
        for (var s :
            List.of(
                EquipmentSlot.FEET,
                EquipmentSlot.LEGS,
                EquipmentSlot.CHEST,
                EquipmentSlot.HEAD,
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND)) {
          Item item =
              switch (s) {
                case FEET -> Items.DIAMOND_BOOTS;
                case LEGS -> Items.DIAMOND_LEGGINGS;
                case CHEST -> Items.DIAMOND_CHESTPLATE;
                case HEAD -> Items.DIAMOND_HELMET;
                case MAINHAND -> Items.DIAMOND_SWORD;
                default -> Items.SHIELD;
              };
          double y =
              switch (s) {
                case FEET -> .2;
                case LEGS -> .6;
                case CHEST -> 1.1;
                case HEAD -> 1.7;
                default -> 0;
              };
          var stack = gear(item);
          reset(player, stand, s, stack);
          inventory = player.getInventory().save(new ListTag()).toString();
          stand.interactAt(player, new Vec3(0, y, 0), InteractionHand.MAIN_HAND);
          unchanged(
              h,
              player,
              stand,
              s,
              stack,
              inventory,
              "Spawn protection covers armor and hand slot " + s);
        }
      }
    } finally {
      stand.discard();
      app.server.getPlayerList().deop(player.getGameProfile());
      app.server.getPlayerList().remove(player);
      app.regions.edit(regions, app.server, app.settings, app.compat);
      app.storage.commit(saved);
    }
  }
}
