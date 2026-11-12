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
                .withItalic(entry ? region.entryItalic : region.exitItalic)
                .withUnderlined(entry ? region.entryUnderlined : region.exitUnderlined));
  }

  public static void show(ServerPlayer player, Model.Region previous, Model.Region current) {
    Component enter = current != null && !current.entry.isEmpty() ? text(current, true) : null;
    Component exit = previous != null && !previous.exit.isEmpty() ? text(previous, false) : null;
    if (enter == null && exit == null) return;
    MutableComponent notice = (enter == null ? exit : enter).copy();
    if (enter != null && exit != null)
      notice
          .append(
              Component.literal(" · ")
                  .withStyle(
                      Style.EMPTY
                          .withColor(0xAAAAAA)
                          .withBold(false)
                          .withItalic(false)
                          .withUnderlined(false)))
          .append(exit);
    player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
    // Vanilla renders the title at 4x and the subtitle at 2x; no arbitrary scale is available.
    // A blank title starts the display timer and clears any previous large title.
    player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
    player.connection.send(new ClientboundSetSubtitleTextPacket(notice));
  }
}
