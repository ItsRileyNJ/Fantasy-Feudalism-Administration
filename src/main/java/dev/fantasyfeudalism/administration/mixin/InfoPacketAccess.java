package dev.fantasyfeudalism.administration.mixin;

import java.util.List;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public interface InfoPacketAccess {
  @Mutable
  @Accessor("entries")
  void ffa$entries(List<ClientboundPlayerInfoUpdatePacket.Entry> entries);
}
