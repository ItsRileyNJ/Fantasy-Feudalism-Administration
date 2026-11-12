package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.Model.*;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.*;

/** Sign clicks use the same inventory/currency journal as normal shop commands. */
public final class SignShops {
  private static final String TOKEN = "ffa_shop_token";
  private final Administration app;
  private final java.util.function.LongSupplier clock;
  private final Map<UUID, Long> clicks = new HashMap<>();

  public SignShops(Administration app) {
    this(app, Teleports::now);
  }

  SignShops(Administration app, java.util.function.LongSupplier clock) {
    this.app = app;
    this.clock = clock;
  }

  static String key(ServerLevel level, BlockPos pos) {
    return level.dimension().location() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
  }

  private SignBlockEntity looking(ServerPlayer p) {
    var hit = p.pick(6, 1, false);
    if (!(hit instanceof BlockHitResult block)
        || hit.getType() != HitResult.Type.BLOCK
        || !(p.serverLevel().getBlockEntity(block.getBlockPos()) instanceof SignBlockEntity sign))
      throw new IllegalArgumentException("Look at a sign within six blocks.");
    return sign;
  }

  public void bind(ServerPlayer p, String offering, int quantity) throws Exception {
    if (!app.compat.admin(p)) throw new IllegalArgumentException("Administrator only.");
    if (!app.storage.state().shops.containsKey(offering))
      throw new IllegalArgumentException("Unknown shop.");
    if (quantity < 1 || quantity > 2304)
      throw new IllegalArgumentException("Quantity must be 1–2304.");
    var sign = looking(p);
    String key = key(p.serverLevel(), sign.getBlockPos());
    ShopSign old = app.storage.state().shopSigns.get(key), link = new ShopSign();
    link.dimension = p.serverLevel().dimension().location().toString();
    link.x = sign.getBlockPos().getX();
    link.y = sign.getBlockPos().getY();
    link.z = sign.getBlockPos().getZ();
    link.shop = offering;
    link.quantity = quantity;
    link.token = UUID.randomUUID().toString();
    link.wasWaxed = old != null && valid(sign, old) ? old.wasWaxed : sign.isWaxed();
    app.storage.update(s -> s.shopSigns.put(key, link));
    sign.getPersistentData().putString(TOKEN, link.token);
    sign.setWaxed(true);
    label(sign, link);
    app.storage.audit(
        app.settings,
        p.getUUID().toString(),
        "link shop sign " + offering,
        Teleports.point(p).describe(),
        "SUCCESS");
    Teleports.tell(p, "Shop sign created.");
  }

  public void remove(ServerPlayer p) throws Exception {
    if (!app.compat.admin(p)) throw new IllegalArgumentException("Administrator only.");
    var sign = looking(p);
    String key = key(p.serverLevel(), sign.getBlockPos());
    var link = app.storage.state().shopSigns.get(key);
    if (link == null) throw new IllegalArgumentException("That sign is not a shop.");
    app.storage.update(s -> s.shopSigns.remove(key));
    if (valid(sign, link)) {
      sign.getPersistentData().remove(TOKEN);
      sign.setWaxed(link.wasWaxed);
      sign.setChanged();
      p.serverLevel()
          .sendBlockUpdated(sign.getBlockPos(), sign.getBlockState(), sign.getBlockState(), 3);
    }
    Teleports.tell(p, "Shop sign removed.");
  }

  private boolean valid(SignBlockEntity sign, ShopSign link) {
    return link.token != null && link.token.equals(sign.getPersistentData().getString(TOKEN));
  }

  public boolean linked(ServerLevel level, BlockPos at) {
    var link = app.storage.state().shopSigns.get(key(level, at));
    return link != null
        && level.getBlockEntity(at) instanceof SignBlockEntity sign
        && valid(sign, link);
  }

  public boolean click(ServerPlayer p, BlockPos at, InteractionHand hand, boolean buy) {
    if (!linked(p.serverLevel(), at)) return false;
    if (hand != InteractionHand.MAIN_HAND) return true;
    long now = clock.getAsLong();
    if (clicks.getOrDefault(p.getUUID(), 0L) > now) return true;
    clicks.put(p.getUUID(), now + 300);
    var link = app.storage.state().shopSigns.get(key(p.serverLevel(), at));
    try {
      if (app.moderation.confined(p))
        throw new IllegalArgumentException("You cannot trade while jailed.");
      if (!app.compat.admin(p)
          && !app.compat.decision(p, "ffa.command.shop").orElse(app.settings.publicCommands))
        throw new IllegalArgumentException("Permission denied.");
      app.economy.trade(p, link.shop, link.quantity, buy);
      Teleports.tell(p, buy ? "Purchase complete." : "Sale complete.");
    } catch (Exception error) {
      Teleports.tell(
          p, error.getMessage() == null ? "The trade could not be completed." : error.getMessage());
    }
    return true;
  }

  public void refresh(String offering) {
    for (var link : app.storage.state().shopSigns.values())
      if (link.shop.equals(offering)) {
        var dimension = net.minecraft.resources.ResourceLocation.tryParse(link.dimension);
        if (dimension == null) continue;
        var level =
            app.server.getLevel(
                net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, dimension));
        var at = new BlockPos(link.x, link.y, link.z);
        if (level != null
            && level.hasChunkAt(at)
            && level.getBlockEntity(at) instanceof SignBlockEntity sign
            && valid(sign, link)) label(sign, link);
      }
  }

  private void label(SignBlockEntity sign, ShopSign link) {
    Shop shop = app.storage.state().shops.get(link.shop);
    if (shop == null) return;
    String[] lines = {
      link.shop + " x" + link.quantity,
      "Right: buy " + price(shop.buy, link.quantity),
      "Left: sell " + price(shop.sell, link.quantity),
      app.settings.currency
    };
    for (boolean front : List.of(true, false)) {
      var text = sign.getText(front);
      for (int i = 0; i < 4; i++) text = text.setMessage(i, Component.literal(lines[i]));
      sign.setText(text, front);
    }
    sign.setChanged();
    var level = sign.getLevel();
    if (level != null)
      level.sendBlockUpdated(sign.getBlockPos(), sign.getBlockState(), sign.getBlockState(), 3);
  }

  private static String price(long unit, int count) {
    if (unit < 0) return "off";
    try {
      return Long.toString(Math.multiplyExact(unit, count));
    } catch (ArithmeticException overflow) {
      return "too large";
    }
  }
}
