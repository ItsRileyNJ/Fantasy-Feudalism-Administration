package dev.fantasyfeudalism.administration;

import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;

public final class RegionTitles {
  private RegionTitles() {}

  public static TextColor color(String value) {
    return TextColor.parseColor(value)
        .result()
        .orElseThrow(() -> new IllegalArgumentException("Use a Minecraft color name or #RRGGBB."));
  }

  private static Component text(Model.Region region, boolean entry) {
    String template = entry ? region.entry : region.exit;
    return Component.literal(template.replace("{name}", region.name).replace("{id}", region.id))
        .withStyle(
            Style.EMPTY
                .withColor(color(entry ? region.entryColor : region.exitColor))
                .withBold(entry ? region.entryBold : region.exitBold)
                .withItalic(entry ? region.entryItalic : region.exitItalic));
  }

  public static void show(ServerPlayer player, Model.Region previous, Model.Region current) {
    Component enter = current != null && !current.entry.isEmpty() ? text(current, true) : null;
    Component exit = previous != null && !previous.exit.isEmpty() ? text(previous, false) : null;
    if (enter == null && exit == null) return;
    player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
    player.connection.send(new ClientboundSetTitleTextPacket(enter == null ? exit : enter));
    player.connection.send(
        new ClientboundSetSubtitleTextPacket(
            enter != null && exit != null ? exit : Component.empty()));
  }
}
