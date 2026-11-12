package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;

import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class Protection {
  private final Administration app;
  private final Map<UUID, String> previous = new HashMap<>();
  private final Map<UUID, Region> previousRegions = new HashMap<>();
  private final Map<UUID, BlockPos> positions = new HashMap<>();
  private final Map<UUID, String> dimensions = new HashMap<>();
  private final Map<UUID, Long> notices = new HashMap<>();

  public Protection(Administration app) {
    this.app = app;
  }

  public boolean allowed(LevelAccessor level, BlockPos pos, Flag flag) {
    return !(level instanceof ServerLevel l)
        || (app.moderation == null || !app.moderation.cellAt(l, pos))
            && app.regions.allowed(l, pos, flag, app.settings);
  }

  public boolean denied(Player player, ServerLevel level, BlockPos target, Flag flag) {
    if (player instanceof ServerPlayer p && !(p instanceof FakePlayer) && app.compat.admin(p))
      return false;
    if (player instanceof ServerPlayer p && app.moderation != null && app.moderation.confined(p))
      return true;
    if (player instanceof FakePlayer && !allowed(level, target, Flag.AUTOMATION)) return true;
    return !allowed(level, target, flag);
  }

  public static boolean block(LevelAccessor level, BlockPos pos, Flag flag) {
    var a = Administration.INSTANCE;
    return a != null
        && a.server != null
        && a.protection != null
        && !a.protection.allowed(level, pos, flag);
  }

  public void update(ServerPlayer p) {
    if (Vanish.hidden(p.getUUID()) || app.moderation != null && app.moderation.confined(p)) return;
    PlayerData data = app.storage.player(p.getUUID());
    if (data == null) return;
    boolean
        protectedArea = app.regions.protectedAt(p.serverLevel(), p.blockPosition(), app.settings),
        admin = app.compat.admin(p);
    Region r = app.regions.at(p.serverLevel(), p.blockPosition());
    boolean childBuilding =
        r != null
            && r.kind == Kind.CHILD
            && (app.regions.resolve(r, Flag.BUILD, app.settings)
                || app.regions.resolve(r, Flag.BREAK, app.settings));
    boolean force =
        protectedArea
            && (r == null || r.kind != Kind.STANDALONE)
            && !childBuilding
            && !RegionActions.editsAllowed(this, p)
            && !admin;
    try {
      if (protectedArea && !admin && !data.flightTracked) {
        boolean may = p.getAbilities().mayfly, flying = p.getAbilities().flying;
        app.storage.update(
            s -> {
              PlayerData d = s.players.get(p.getUUID().toString());
              d.previousMayFly = may;
              d.previousFlying = flying;
              d.flightTracked = true;
            });
        data = app.storage.player(p.getUUID());
      }
      if (force && data.previousMode == null) {
        String mode = p.gameMode.getGameModeForPlayer().getName();
        boolean fly = p.getAbilities().mayfly, flying = p.getAbilities().flying;
        app.storage.update(
            s -> {
              PlayerData d = s.players.get(p.getUUID().toString());
              d.previousMode = mode;
            });
        data = app.storage.player(p.getUUID());
      }
      if (!force && data.previousMode != null) {
        var d = data;
        p.setGameMode(GameType.byName(d.previousMode));
        p.getAbilities().mayfly = d.previousMayFly;
        p.getAbilities().flying = d.previousFlying && d.previousMayFly;
        p.onUpdateAbilities();
        app.storage.update(s -> s.players.get(p.getUUID().toString()).previousMode = null);
      }
      if ((!protectedArea || admin) && data.flightTracked) {
        p.getAbilities().mayfly = data.previousMayFly;
        p.getAbilities().flying = data.previousFlying && data.previousMayFly;
        p.onUpdateAbilities();
        app.storage.update(s -> s.players.get(p.getUUID().toString()).flightTracked = false);
      }
      if (force && p.gameMode.getGameModeForPlayer() != GameType.ADVENTURE)
        p.setGameMode(GameType.ADVENTURE);
      if (protectedArea && !admin) {
        boolean flight = allowed(p.serverLevel(), p.blockPosition(), Flag.FLIGHT);
        if (!flight && p.isFallFlying()) p.stopFallFlying();
        if (p.getAbilities().mayfly != flight || !flight && p.getAbilities().flying) {
          p.getAbilities().mayfly = flight;
          if (!flight) p.getAbilities().flying = false;
          p.onUpdateAbilities();
        }
      }
    } catch (Exception e) {
      p.connection.disconnect(
          Component.literal("Protection state could not be saved; contact an administrator."));
      Administration.LOG.error("Protection state failure", e);
      return;
    }
    String current =
        r == null
            ? (Regions.isSpawnDimension(p.serverLevel(), app.settings) ? "@dimension" : "")
            : r.id;
    String old = previous.put(p.getUUID(), current);
    Region prior = previousRegions.put(p.getUUID(), r);
    if (old == null || !old.equals(current)) RegionTitles.show(p, prior, r);
  }

  @SubscribeEvent
  public void login(PlayerEvent.PlayerLoggedInEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer p)) return;
    try {
      boolean returning =
          Files.exists(
              app.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID() + ".dat"));
      app.storage.ensure(p.getUUID(), app.settings);
      app.economy.recover(p);
      boolean first = !app.storage.player(p.getUUID()).joined;
      if (first) {
        if (!returning && app.settings.firstJoin && app.storage.state().spawn != null)
          app.teleports.teleport(p, app.storage.state().spawn, true);
        app.storage.update(s -> s.players.get(p.getUUID().toString()).joined = true);
      }
      update(p);
    } catch (Exception e) {
      Administration.LOG.error("FFA login recovery failed for " + p.getUUID(), e);
      p.connection.disconnect(
          Component.literal("FFA login recovery failed. Please contact a server administrator."));
    }
  }

  @SubscribeEvent
  public void clone(PlayerEvent.Clone e) {
    if (e.getEntity() instanceof ServerPlayer p) update(p);
  }

  @SubscribeEvent
  public void logout(PlayerEvent.PlayerLoggedOutEvent e) {
    UUID id = e.getEntity().getUUID();
    previous.remove(id);
    previousRegions.remove(id);
    positions.remove(id);
    dimensions.remove(id);
    notices.remove(id);
  }

  @SubscribeEvent
  public void tick(PlayerTickEvent.Post e) {
    if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
    BlockPos old = positions.put(p.getUUID(), p.blockPosition());
    String dim = dimensions.put(p.getUUID(), p.level().dimension().location().toString());
    if (!p.blockPosition().equals(old)
        || !p.level().dimension().location().toString().equals(dim)
        || p.tickCount % 20 == 0) {
      update(p);
      if (!p.blockPosition().equals(old)
          || !p.level().dimension().location().toString().equals(dim)) Portals.check(app, p);
    }
  }

  private void notice(ServerPlayer p) {
    long now = Teleports.now();
    if (notices.getOrDefault(p.getUUID(), 0L) <= now) {
      Teleports.tell(p, app.settings.denied);
      notices.put(p.getUUID(), now + 2000);
      app.storage.security(
          app.settings,
          p.getUUID().toString(),
          "protected interaction",
          Teleports.point(p).describe(),
          "DENIED");
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void portalCreation(BlockEvent.PortalSpawnEvent event) {
    if (!PortalCreation.permitted()) event.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void breaking(BlockEvent.BreakEvent e) {
    if (e.getLevel() instanceof ServerLevel l
        && (app.signShops != null && app.signShops.linked(l, e.getPos())
            || denied(
                e.getPlayer(),
                l,
                e.getPos(),
                RegionActions.farm(e.getState()) ? Flag.FARMING : Flag.BREAK))) e.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void placing(BlockEvent.EntityPlaceEvent e) {
    if (!(e.getLevel() instanceof ServerLevel l)) return;
    Flag f =
        e.getPlacedBlock().is(Blocks.TNT)
            ? Flag.TNT
            : RegionActions.changeFlag(e.getBlockSnapshot().getState(), e.getPlacedBlock());
    boolean deny =
        e.getEntity() instanceof Player p
            ? denied(p, l, e.getPos(), f) || f == Flag.TNT && denied(p, l, e.getPos(), Flag.BUILD)
            : !allowed(l, e.getPos(), Flag.AUTOMATION);
    if (e instanceof BlockEvent.EntityMultiPlaceEvent multi)
      for (var snap : multi.getReplacedBlockSnapshots())
        deny |=
            e.getEntity() instanceof Player p
                ? denied(
                    p,
                    l,
                    snap.getPos(),
                    RegionActions.changeFlag(snap.getState(), l.getBlockState(snap.getPos())))
                : !allowed(l, snap.getPos(), Flag.AUTOMATION);
    if (deny) e.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void interact(PlayerInteractEvent.RightClickBlock e) {
    if (!(e.getEntity() instanceof ServerPlayer p)) return;
    if (app.signShops != null && app.signShops.click(p, e.getPos(), e.getHand(), true)) {
      e.setCanceled(true);
      e.setCancellationResult(InteractionResult.SUCCESS);
      return;
    }
    if (app.compat.admin(p) && Portals.recolor(app, p, e.getPos(), p.getItemInHand(e.getHand()))) {
      e.setCanceled(true);
      e.setCancellationResult(InteractionResult.SUCCESS);
      return;
    }
    if (app.compat.admin(p)) return;
    if (app.compat.settlementBell(p, e.getPos())
        && (p.getItemInHand(e.getHand()).isEmpty()
            || BuiltInRegistries.ITEM
                .getKey(p.getItemInHand(e.getHand()).getItem())
                .toString()
                .equals("villagerscontract:settlement_charter"))
        && allowed(p.serverLevel(), e.getPos(), Flag.SETTLEMENT_MANAGEMENT)) return;
    var region = app.regions.at(p.serverLevel(), e.getPos());
    String block =
        BuiltInRegistries.BLOCK
            .getKey(e.getLevel().getBlockState(e.getPos()).getBlock())
            .toString();
    boolean listed = region != null && region.interactions.contains(block);
    boolean usable =
        itemAllowed(p, p.blockPosition(), p.getItemInHand(e.getHand()))
            && itemAllowed(p, e.getPos(), p.getItemInHand(e.getHand()));
    if (!usable
        || RegionActions.itemFlag(p.getItemInHand(e.getHand())) == null
            && denied(p, p.serverLevel(), e.getPos(), Flag.BUILD)
            && !app.regions.itemAllowed(
                p.serverLevel(),
                e.getPos(),
                BuiltInRegistries.ITEM.getKey(p.getItemInHand(e.getHand()).getItem()).toString()))
      e.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
    if (p.getItemInHand(e.getHand()).getItem() instanceof SpawnEggItem
        && (denied(p, p.serverLevel(), e.getPos(), Flag.MOB_SPAWNING)
            || denied(p, p.serverLevel(), e.getPos().relative(e.getFace()), Flag.MOB_SPAWNING))) {
      e.setCanceled(true);
      e.setCancellationResult(InteractionResult.FAIL);
      return;
    }

    boolean container =
        e.getLevel().getBlockEntity(e.getPos()) instanceof net.minecraft.world.Container
            || e.getLevel().getBlockState(e.getPos()).getMenuProvider(e.getLevel(), e.getPos())
                != null
            || p.serverLevel()
                    .getCapability(
                        net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                        e.getPos(),
                        null)
                != null;
    var blockAction =
        RegionActions.blockFlag(
            e.getLevel().getBlockState(e.getPos()), p.getItemInHand(e.getHand()));
    if (denied(p, p.serverLevel(), e.getPos(), blockAction)
            && (!listed || blockAction != Flag.INTERACT)
        || container && denied(p, p.serverLevel(), e.getPos(), Flag.CONTAINERS)
        || p.getItemInHand(e.getHand()).is(Items.ENDER_PEARL)
            && denied(p, p.serverLevel(), e.getPos(), Flag.PEARLS)) {
      e.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
      if (!usable
          || p.getItemInHand(e.getHand()).is(Items.ENDER_PEARL)
              && denied(p, p.serverLevel(), e.getPos(), Flag.PEARLS)) {
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
        notice(p);
      }
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void left(PlayerInteractEvent.LeftClickBlock e) {
    if (e.getEntity() instanceof ServerPlayer p
        && app.signShops != null
        && app.signShops.linked(p.serverLevel(), e.getPos())) {
      e.setCanceled(true);
      if (e.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START)
        app.signShops.click(p, e.getPos(), net.minecraft.world.InteractionHand.MAIN_HAND, false);
      return;
    }
    if (e.getEntity() instanceof ServerPlayer p) {
      if (denied(p, p.serverLevel(), e.getPos(), Flag.INTERACT))
        e.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
      if (denied(
          p,
          p.serverLevel(),
          e.getPos(),
          RegionActions.farm(e.getLevel().getBlockState(e.getPos())) ? Flag.FARMING : Flag.BREAK))
        e.setCanceled(true);
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void projectile(ProjectileImpactEvent e) {
    if (e.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit
        && hit.getEntity() instanceof Player target
        && playerDamageDenied(target, e.getProjectile().getOwner())) {
      // Cancel the hit before fire, potion effects, knockback or enchantment effects run.
      // Keep the projectile: discarding it here would destroy thrown tridents.
      e.setCanceled(true);
      return;
    }
    if (e.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit
        && (hit.getEntity() instanceof HangingEntity || hit.getEntity() instanceof ArmorStand)
        && !allowed(
            hit.getEntity().level(), hit.getEntity().blockPosition(), Flag.HANGING_DAMAGE)) {
      if (e.getProjectile().getOwner() instanceof ServerPlayer p && app.compat.admin(p)) return;
      e.setCanceled(true);
      e.getProjectile().discard();
    }
  }

  public boolean itemAllowed(ServerPlayer p, BlockPos at, ItemStack stack) {
    if (app.compat.admin(p)) return true;
    if (app.moderation != null && app.moderation.confined(p)) return false;
    var action = RegionActions.itemFlag(stack);
    if (action != null)
      return !denied(p, p.serverLevel(), at, action)
          && (action != Flag.FLINT_AND_STEEL || !denied(p, p.serverLevel(), at, Flag.FIRE));
    if (stack.getItem() instanceof BlockItem && allowed(p.serverLevel(), at, Flag.BUILD))
      return true;
    return !app.regions.protectedAt(p.serverLevel(), at, app.settings)
        || !stack.isEmpty()
            && app.regions.itemAllowed(
                p.serverLevel(), at, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void item(PlayerInteractEvent.RightClickItem e) {
    if (e.getEntity() instanceof ServerPlayer p
        && (!itemAllowed(p, p.blockPosition(), p.getItemInHand(e.getHand()))
            || p.getItemInHand(e.getHand()).is(Items.ENDER_PEARL)
                && denied(p, p.serverLevel(), p.blockPosition(), Flag.PEARLS))) {
      e.setCanceled(true);
      e.setCancellationResult(InteractionResult.FAIL);
      notice(p);
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void using(LivingEntityUseItemEvent.Tick e) {
    if (e.getEntity() instanceof ServerPlayer p && !itemAllowed(p, p.blockPosition(), e.getItem()))
      p.stopUsingItem();
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void releaseItem(LivingEntityUseItemEvent.Stop e) {
    if (e.getEntity() instanceof ServerPlayer p && !itemAllowed(p, p.blockPosition(), e.getItem()))
      e.setCanceled(true);
  }

  private Flag entityFlag(Entity entity, ItemStack held) {
    if (app.compat.npc(entity) || entity instanceof net.minecraft.world.entity.npc.Villager)
      return Flag.NPC_TRADING;
    if (RegionActions.vehicle(entity)) return Flag.VEHICLE_ENTER;
    var action = RegionActions.itemFlag(held);
    if (action == Flag.SHEARS
        && (entity instanceof net.neoforged.neoforge.common.IShearable
            || entity instanceof net.minecraft.world.entity.animal.Wolf)) return Flag.SHEARS;
    if (action == Flag.FLINT_AND_STEEL
        && entity instanceof net.minecraft.world.entity.monster.Creeper)
      return Flag.FLINT_AND_STEEL;
    return Flag.ENTITY_INTERACT;
  }

  private boolean entityDenied(ServerPlayer player, Entity target, ItemStack held) {
    Flag flag = entityFlag(target, held);
    return denied(player, player.serverLevel(), player.blockPosition(), flag)
        || denied(player, player.serverLevel(), target.blockPosition(), flag)
        || flag == Flag.FLINT_AND_STEEL
            && (denied(player, player.serverLevel(), player.blockPosition(), Flag.FIRE)
                || denied(player, player.serverLevel(), target.blockPosition(), Flag.FIRE))
        || target instanceof net.minecraft.world.Container
            && denied(player, player.serverLevel(), target.blockPosition(), Flag.CONTAINERS);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void entity(PlayerInteractEvent.EntityInteract e) {
    if (e.getEntity() instanceof ServerPlayer p) {
      if (app.compat.admin(p)
          && e.getTarget().getTags().stream().anyMatch(t -> t.startsWith("ffa.portal."))
          && Portals.recolor(app, p, e.getTarget().blockPosition(), p.getItemInHand(e.getHand()))) {
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        return;
      }
      if (entityDenied(p, e.getTarget(), p.getItemInHand(e.getHand()))) {
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
      }
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void entitySpecific(PlayerInteractEvent.EntityInteractSpecific e) {
    if (e.getEntity() instanceof ServerPlayer p
        && app.compat.admin(p)
        && e.getTarget().getTags().stream().anyMatch(t -> t.startsWith("ffa.portal."))
        && Portals.recolor(app, p, e.getTarget().blockPosition(), p.getItemInHand(e.getHand()))) {
      e.setCanceled(true);
      e.setCancellationResult(InteractionResult.SUCCESS);
      return;
    }
    if (e.getEntity() instanceof ServerPlayer p
        && entityDenied(p, e.getTarget(), p.getItemInHand(e.getHand()))) {
      e.setCanceled(true);
      e.setCancellationResult(InteractionResult.FAIL);
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void attack(AttackEntityEvent e) {
    if (e.getEntity() instanceof ServerPlayer p) {
      Flag f =
          e.getTarget() instanceof Player
              ? Flag.PVP
              : e.getTarget() instanceof HangingEntity || e.getTarget() instanceof ArmorStand
                  ? Flag.HANGING_DAMAGE
                  : Flag.ANIMAL_DAMAGE;
      if (denied(p, p.serverLevel(), e.getTarget().blockPosition(), f)) e.setCanceled(true);
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void damage(LivingIncomingDamageEvent e) {
    if (!(e.getEntity().level() instanceof ServerLevel l)) return;
    var target = e.getEntity();
    if (app.adminPowers != null && app.adminPowers.smiting(target.getUUID())) return;
    if (target instanceof Player player) {
      if (playerDamageDenied(player, e.getSource().getEntity())) e.setCanceled(true);
      return;
    }
    if (e.getSource().getEntity() instanceof ServerPlayer source && app.compat.admin(source))
      return;
    if ((target instanceof net.minecraft.world.entity.animal.Animal
            || target instanceof net.minecraft.world.entity.TamableAnimal)
        && !allowed(l, target.blockPosition(), Flag.ANIMAL_DAMAGE)) e.setCanceled(true);
  }

  /** Shared by projectile impacts and final damage; the victim's effective region wins. */
  private boolean playerDamageDenied(Player target, Entity attacker) {
    if (!(target.level() instanceof ServerLevel level)) return false;
    if (attacker instanceof ServerPlayer source && app.compat.admin(source)) return false;
    if (target instanceof ServerPlayer player) {
      if (app.compat.admin(player)) return false;
      if (app.moderation != null && app.moderation.confined(player)) return true;
    }
    return !allowed(level, target.blockPosition(), Flag.DAMAGE)
        || attacker instanceof Player
            && (!allowed(level, target.blockPosition(), Flag.PVP)
                || !allowed(attacker.level(), attacker.blockPosition(), Flag.PVP));
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void externalTeleport(EntityTeleportEvent e) {
    if (!(e.getEntity() instanceof ServerPlayer p)) return;
    boolean pearl = e instanceof EntityTeleportEvent.EnderPearl;
    if (pearl
        && (denied(p, p.serverLevel(), p.blockPosition(), Flag.PEARLS)
            || denied(
                p,
                p.serverLevel(),
                BlockPos.containing(e.getTargetX(), e.getTargetY(), e.getTargetZ()),
                Flag.PEARLS))) {
      e.setCanceled(true);
      notice(p);
      return;
    }
    String error =
        app.teleports.validate(
            p,
            new Point(
                p.level().dimension().location().toString(),
                e.getTargetX(),
                e.getTargetY(),
                e.getTargetZ(),
                p.getYRot(),
                p.getXRot()));
    if (error != null) {
      e.setCanceled(true);
      notice(p);
    }
  }

  @SubscribeEvent
  public void totem(LivingUseTotemEvent e) {
    if (e.getEntity() instanceof ServerPlayer p
        && denied(p, p.serverLevel(), p.blockPosition(), Flag.TOTEMS)) e.setCanceled(true);
  }

  @SubscribeEvent
  public void spawning(MobSpawnEvent.PositionCheck e) {
    if (!(e.getLevel() instanceof ServerLevel l) || app.compat.npc(e.getEntity())) return;
    var reason = e.getSpawnType();
    if (reason == MobSpawnType.COMMAND
        || reason == MobSpawnType.SPAWN_EGG
        || reason == MobSpawnType.DISPENSER) return;
    Flag f = e.getEntity() instanceof Monster ? Flag.HOSTILE_SPAWNING : Flag.MOB_SPAWNING;
    if (!allowed(l, e.getEntity().blockPosition(), Flag.MOB_SPAWNING)
        || !allowed(l, e.getEntity().blockPosition(), f))
      e.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
  }

  @SubscribeEvent
  public void joiningMob(EntityJoinLevelEvent e) {
    if (e.loadedFromDisk()
        || !(e.getLevel() instanceof ServerLevel l)
        || !(e.getEntity() instanceof Mob mob)
        || app.compat.npc(mob)) return;
    MobSpawnType reason = mob.getSpawnType();
    if (reason == MobSpawnType.COMMAND
        || reason == MobSpawnType.SPAWN_EGG
        || reason == MobSpawnType.DISPENSER) return;
    if (!allowed(l, mob.blockPosition(), Flag.MOB_SPAWNING)
        || mob instanceof Monster && !allowed(l, mob.blockPosition(), Flag.HOSTILE_SPAWNING))
      e.setCanceled(true);
  }

  @SubscribeEvent
  public void markerLoad(EntityJoinLevelEvent e) {
    if (!(e.getLevel() instanceof ServerLevel)) return;
    for (String tag : e.getEntity().getTags())
      if (tag.startsWith("ffa.portal.")) {
        String id = tag.substring("ffa.portal.".length());
        Portal portal = app.storage.state().portals.get(id);
        if (portal == null) {
          e.setCanceled(true);
          return;
        }
        if (e.getEntity() instanceof net.minecraft.world.entity.Display.BlockDisplay marker) {
          var block =
              BuiltInRegistries.BLOCK.get(
                  net.minecraft.resources.ResourceLocation.withDefaultNamespace(
                      portal.color + "_stained_glass"));
          ((dev.fantasyfeudalism.administration.mixin.BlockDisplayAccess) marker)
              .ffa$block(block.defaultBlockState());
        }
        return;
      }
  }

  @SubscribeEvent
  public void grief(EntityMobGriefingEvent e) {
    if (e.getEntity() != null
        && !app.compat.npc(e.getEntity())
        && !allowed(e.getEntity().level(), e.getEntity().blockPosition(), Flag.MOB_GRIEFING))
      e.setCanGrief(false);
  }

  @SubscribeEvent
  public void explosions(ExplosionEvent.Detonate e) {
    if (e.getExplosion().getIndirectSourceEntity() instanceof ServerPlayer p && app.compat.admin(p))
      return;
    e.getAffectedBlocks().removeIf(p -> !allowed(e.getLevel(), p, Flag.EXPLOSIONS));
    e.getAffectedEntities()
        .removeIf(x -> !allowed(e.getLevel(), x.blockPosition(), Flag.EXPLOSIONS));
  }

  @SubscribeEvent
  public void piston(PistonEvent.Pre e) {
    if (!allowed(e.getLevel(), e.getPos(), Flag.PISTONS)
        || !allowed(e.getLevel(), e.getFaceOffsetPos(), Flag.PISTONS)) {
      e.setCanceled(true);
      return;
    }
    var h = e.getStructureHelper();
    if (h == null || !h.resolve()) return;
    var positions = new HashSet<BlockPos>(h.getToPush());
    positions.addAll(h.getToDestroy());
    for (var p : positions)
      if (!allowed(e.getLevel(), p, Flag.PISTONS)
          || !allowed(
              e.getLevel(),
              p.relative(
                  e.getPistonMoveType().isExtend
                      ? e.getDirection()
                      : e.getDirection().getOpposite()),
              Flag.PISTONS)) {
        e.setCanceled(true);
        return;
      }
  }

  @SubscribeEvent
  public void fluid(BlockEvent.FluidPlaceBlockEvent e) {
    if (!allowed(e.getLevel(), e.getPos(), Flag.FLUIDS)
        || !allowed(e.getLevel(), e.getLiquidPos(), Flag.FLUIDS)) e.setCanceled(true);
  }
}
