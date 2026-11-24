package dev.fantasyfeudalism.administration;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

/** Decoration stands share inventory and hanging-damage rules without linking optional mods. */
public final class StandProtection {
  private StandProtection() {}

  public static boolean displayStand(Entity entity) {
    return entity instanceof ArmorStand
        || BuiltInRegistries.ENTITY_TYPE
            .getKey(entity.getType())
            .toString()
            .equals("supplementaries:hat_stand");
  }
}
