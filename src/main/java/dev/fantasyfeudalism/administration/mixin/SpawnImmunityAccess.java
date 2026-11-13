package dev.fantasyfeudalism.administration.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPlayer.class)
public interface SpawnImmunityAccess {
  @Accessor("spawnInvulnerableTime")
  void ffa$spawnImmunity(int ticks);
}
