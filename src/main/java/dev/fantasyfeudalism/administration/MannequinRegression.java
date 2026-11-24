package dev.fantasyfeudalism.administration;

import static dev.fantasyfeudalism.administration.RegressionTests.*;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;

/** Real command, vanilla packet and persistence checks. No network skin service is required. */
final class MannequinRegression {
  private static int cmd(Administration app, ServerPlayer p, String text) throws Exception {
    return app.server.getCommands().getDispatcher().execute(text, p.createCommandSourceStack());
  }

  private static <T> List<T> of(List<Object> packets, Class<T> type) {
    return packets.stream().filter(type::isInstance).map(type::cast).toList();
  }

  private static void op(Administration app, ServerPlayer p) {
    app.server
        .getPlayerList()
        .getOps()
        .add(new net.minecraft.server.players.ServerOpListEntry(p.getGameProfile(), 4, false));
  }

  static void verify(GameTestHelper h, Administration app) throws Exception {
    var saved = app.storage.copy();
    var original = app.mannequins;
    var future = new AtomicReference<>(new CompletableFuture<GameProfile>());
    var manager = new Mannequins(app, name -> future.get());
    app.mannequins = manager;
    var admin = player(h, "statueAdmin");
    var person = player(h, "statuePerson");
    var viewer = player(h, "statueViewer");
    try {
      app.storage.update(s -> s.mannequins.clear());
      op(app, admin);
      admin.setPos(h.absoluteVec(new Vec3(4.5, 1, 4.5)));
      admin.setYRot(90);
      admin.setXRot(12);
      viewer.setPos(h.absoluteVec(new Vec3(6.5, 1, 4.5)));
      var chunk = new ChunkPos(admin.blockPosition());
      viewer.setChunkTrackingView(ChunkTrackingView.of(chunk, 4));
      person
          .getGameProfile()
          .getProperties()
          .put("textures", new Property("textures", "c2F2ZWQtc2tpbg==", "saved-signature"));
      int playerCount = app.server.getPlayerList().getPlayerCount();
      var roots = clientCommands(viewer, true).getChild("ffa");
      h.assertTrue(
          roots.getChild("mannequin") == null && roots.getChild("hologram") == null,
          "Both display command roots are hidden from ordinary clients");
      h.assertTrue(
          complete(app, admin.createCommandSourceStack(), "ffa mannequin ")
              .containsAll(List.of("create", "skin", "equipment", "text", "move", "delete")),
          "Admin actions complete");
      h.assertTrue(
          cmd(app, admin, "ffa mannequin create Champion statuePerson") == 1,
          "Administrator creates a display of another online player");
      h.assertTrue(
          cmd(app, admin, "ffa mannequin create champion statuePerson") == 0,
          "Display names preserve uppercase and reject case-insensitive duplicates");
      h.assertTrue(
          manager.get("CHAMPION").signature.equals("saved-signature"),
          "Signed skin properties are saved");
      h.assertTrue(
          cmd(app, admin, "ffa mannequin create Self") == 1,
          "Omitting account creates the administrator's own display");
      cmd(app, admin, "ffa mannequin delete Self");
      h.assertTrue(
          cmd(app, admin, "ffa hologram text Champion title \"Server Founder\"") == 1,
          "Hologram alias executes display editing");
      cmd(app, admin, "ffa mannequin text Champion nickname \"Our Champion\"");
      cmd(app, admin, "ffa mannequin text Champion name_color gold");
      cmd(app, admin, "ffa mannequin text Champion name_underlined true");
      cmd(app, admin, "ffa mannequin text Champion title_bold true");
      cmd(app, admin, "ffa mannequin text Champion title_italic true");
      cmd(app, admin, "ffa mannequin text Champion title_underlined true");
      var label = Mannequins.label(manager.get("Champion"));
      h.assertTrue(
          label.getString().equals("Our Champion\nServer Founder")
              && label.getStyle().isUnderlined()
              && label.getSiblings().getLast().getStyle().isBold()
              && label.getSiblings().getLast().getStyle().isItalic()
              && label.getSiblings().getLast().getStyle().isUnderlined(),
          "Name and title retain independent styles");
      var sword = new ItemStack(Items.DIAMOND_SWORD);
      sword.set(
          net.minecraft.core.component.DataComponents.CUSTOM_NAME,
          net.minecraft.network.chat.Component.literal("Champion's sword"));
      person.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword.copy());
      String inventory = person.getInventory().save(new net.minecraft.nbt.ListTag()).toString();
      cmd(app, admin, "ffa mannequin equipment Champion statuePerson");
      h.assertTrue(
          person.getInventory().save(new net.minecraft.nbt.ListTag()).toString().equals(inventory),
          "Equipment copying never takes inventory items");
      outgoing(viewer);
      manager.refresh(viewer);
      var packets = outgoing(viewer);
      var info = of(packets, ClientboundPlayerInfoUpdatePacket.class).getFirst();
      var entry = info.entries().getFirst();
      h.assertTrue(
          !entry.listed()
              && !entry.profileId().equals(person.getUUID())
              && entry
                  .profile()
                  .getProperties()
                  .get("textures")
                  .iterator()
                  .next()
                  .signature()
                  .equals("saved-signature"),
          "Public display has a separate UUID, signed skin and no Tab listing");
      var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), app.server.registryAccess());
      try {
        ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.encode(buffer, info);
        var decoded = ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(buffer);
        h.assertTrue(
            !decoded.entries().getFirst().listed()
                && decoded.entries().getFirst().profileId().equals(entry.profileId()),
            "Native player-info codec retains unlisted profile fields");
      } finally {
        buffer.release();
      }
      var adds = of(packets, ClientboundAddEntityPacket.class);
      var body =
          adds.stream().filter(p -> p.getType() == EntityType.PLAYER).findFirst().orElseThrow();
      h.assertTrue(
          adds.size() == 2
              && adds.stream().anyMatch(p -> p.getType() == EntityType.TEXT_DISPLAY)
              && packets.indexOf(info) < packets.indexOf(body),
          "Profile arrives before a full player body and floating label");
      h.assertTrue(
          h.getLevel().getEntity(body.getId()) == null
              && h.getLevel().getEntity(entry.profileId()) == null
              && app.server.getPlayerList().getPlayerCount() == playerCount
              && app.server.getPlayerList().getPlayer(entry.profileId()) == null,
          "Display is absent from world entities, online players and server status counts");
      h.assertTrue(
          !of(packets, ClientboundSetPlayerTeamPacket.class).isEmpty()
              && of(packets, ClientboundSetEntityDataPacket.class).size() == 2,
          "Native teams and metadata hide internal name and deliver skin layers and styled text");
      var team =
          of(packets, ClientboundSetPlayerTeamPacket.class)
              .getFirst()
              .getParameters()
              .orElseThrow();
      h.assertTrue(
          team.getNametagVisibility().equals("never") && team.getCollisionRule().equals("never"),
          "Internal profile names and player collision are explicitly disabled");
      var labelSpawn =
          adds.stream()
              .filter(p -> p.getType() == EntityType.TEXT_DISPLAY)
              .findFirst()
              .orElseThrow();
      var labelPacket =
          of(packets, ClientboundSetEntityDataPacket.class).stream()
              .filter(p -> p.id() == labelSpawn.getId())
              .findFirst()
              .orElseThrow();
      var labelBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), app.server.registryAccess());
      try {
        ClientboundSetEntityDataPacket.STREAM_CODEC.encode(labelBuffer, labelPacket);
        var decoded = ClientboundSetEntityDataPacket.STREAM_CODEC.decode(labelBuffer);
        h.assertTrue(
            decoded.packedItems().stream()
                .anyMatch(
                    value ->
                        value.value() instanceof net.minecraft.network.chat.Component component
                            && component.getString().equals("Our Champion\nServer Founder")
                            && component.getStyle().isUnderlined()),
            "Actual label metadata survives the vanilla packet codec with text and styling");
      } finally {
        labelBuffer.release();
      }
      var bodyPacket =
          of(packets, ClientboundSetEntityDataPacket.class).stream()
              .filter(p -> p.id() == body.getId())
              .findFirst()
              .orElseThrow();
      h.assertTrue(
          bodyPacket.packedItems().stream()
              .anyMatch(value -> value.value() instanceof Byte b && b == 127),
          "All native skin layers are visible on the mannequin");
      var displayed = of(packets, ClientboundSetEquipmentPacket.class).getFirst();
      h.assertTrue(
          displayed.getSlots().stream()
              .anyMatch(pair -> ItemStack.matches(pair.getSecond(), sword)),
          "Native equipment packet retains copied item components");
      viewer.connection.handleInteract(
          ServerboundInteractPacket.createAttackPacket(
              // The packet carries the visual id; the server deliberately has no matching entity.
              new VisualId(h, body.getId()), false));
      h.assertTrue(
          person.getInventory().save(new net.minecraft.nbt.ListTag()).toString().equals(inventory),
          "Attacking visual equipment cannot reach a player's inventory");
      manager.refresh(viewer);
      h.assertTrue(
          of(outgoing(viewer), ClientboundAddEntityPacket.class).isEmpty(),
          "Idle displays do not resend spawn packets");
      var loaded = new Storage(app.storage.directory).state().mannequins.get("Champion");
      h.assertTrue(
          loaded.texture.equals("c2F2ZWQtc2tpbg==")
              && loaded.text.equals(manager.get("Champion").text)
              && loaded.equipment.equals(manager.get("Champion").equipment),
          "Skins, labels and equipment survive a real state-file reload");

      viewer.setPos(viewer.getX() + 100, viewer.getY(), viewer.getZ());
      manager.refresh(viewer);
      var removed = outgoing(viewer);
      h.assertTrue(
          of(removed, ClientboundRemoveEntitiesPacket.class).size() == 1
              && of(removed, ClientboundPlayerInfoRemovePacket.class)
                  .getFirst()
                  .profileIds()
                  .contains(entry.profileId()),
          "Leaving range removes body, label and skin profile");
      viewer.setPos(h.absoluteVec(new Vec3(6.5, 1, 4.5)));
      viewer.setChunkTrackingView(ChunkTrackingView.EMPTY);
      manager.refresh(viewer);
      h.assertTrue(
          of(outgoing(viewer), ClientboundAddEntityPacket.class).isEmpty(),
          "Unwatched chunks never receive display entities");
      viewer.setChunkTrackingView(ChunkTrackingView.of(chunk, 4));
      viewer.connection.chunkSender.markChunkPendingToSend(h.getLevel().getChunk(chunk.x, chunk.z));
      manager.refresh(viewer);
      h.assertTrue(
          of(outgoing(viewer), ClientboundAddEntityPacket.class).isEmpty(),
          "Queued, unsent chunks cannot receive premature entity packets");
      viewer.connection.chunkSender.dropChunk(viewer, chunk);
      manager.refresh(viewer);
      h.assertTrue(
          of(outgoing(viewer), ClientboundAddEntityPacket.class).size() == 2,
          "Displays reappear after chunk delivery becomes ready");
      NeoForge.EVENT_BUS.post(new ChunkWatchEvent.UnWatch(viewer, chunk, h.getLevel()));
      // This fixture manager is intentionally unregistered; exercise the production event method.
      manager.unwatch(new ChunkWatchEvent.UnWatch(viewer, chunk, h.getLevel()));
      h.assertTrue(
          !of(outgoing(viewer), ClientboundPlayerInfoRemovePacket.class).isEmpty(),
          "Chunk unload immediately removes display tracking");
      manager.refresh(viewer);
      outgoing(viewer);
      manager.dimension(
          new PlayerEvent.PlayerChangedDimensionEvent(viewer, Level.OVERWORLD, Level.NETHER));
      h.assertTrue(
          !of(outgoing(viewer), ClientboundRemoveEntitiesPacket.class).isEmpty(),
          "Dimension reset clears old client display state");
      manager.edit("Champion", m -> m.point.dimension = "minecraft:the_nether");
      manager.refresh(viewer);
      h.assertTrue(
          of(outgoing(viewer), ClientboundAddEntityPacket.class).isEmpty(),
          "A display in another dimension stays invisible");
      cmd(app, admin, "ffa mannequin move Champion");
      cmd(app, admin, "ffa mannequin label Champion false");
      manager.refresh(viewer);
      h.assertTrue(
          of(outgoing(viewer), ClientboundAddEntityPacket.class).size() == 1,
          "A hidden label leaves only the full player body");
      cmd(app, admin, "ffa mannequin equipment Champion clear");
      cmd(app, admin, "ffa mannequin text Champion reset");
      h.assertTrue(
          manager.get("Champion").equipment.isEmpty() && manager.get("Champion").text.isEmpty(),
          "Equipment and text reset independently");

      var offline = new GameProfile(UUID.randomUUID(), "pastHero");
      offline
          .getProperties()
          .put("textures", new Property("textures", "b2ZmbGluZQ==", "offline-signature"));
      cmd(app, admin, "ffa mannequin create Pending pastHero");
      h.assertTrue(
          !app.storage.state().mannequins.containsKey("Pending"),
          "Asynchronous lookup does not save incomplete displays");
      cmd(app, admin, "ffa mannequin delete Pending");
      future.get().complete(offline);
      h.assertTrue(
          !app.storage.state().mannequins.containsKey("Pending"),
          "Deleted pending displays cannot return after delayed skin lookup");
      future.set(new CompletableFuture<>());
      cmd(app, admin, "ffa mannequin create Revoked pastHero");
      app.server.getPlayerList().deop(admin.getGameProfile());
      future.get().complete(offline);
      h.assertTrue(
          !app.storage.state().mannequins.containsKey("Revoked"),
          "Delayed lookup rechecks current administrator authority");
      op(app, admin);
      future.set(new CompletableFuture<>());
      cmd(app, admin, "ffa mannequin create Offline pastHero");
      future.get().complete(offline);
      h.assertTrue(
          manager.get("Offline").signature.equals("offline-signature"),
          "Completed offline lookup persists the signed skin");
      future.set(new CompletableFuture<>());
      cmd(app, admin, "ffa mannequin skin Champion pastHero");
      future.get().completeExceptionally(new IllegalArgumentException("service unavailable"));
      h.assertTrue(
          manager.get("Champion").accountName.equals("statuePerson"),
          "Failed skin refresh preserves the previous display");
      future.set(new CompletableFuture<>());
      cmd(app, admin, "ffa mannequin skin Champion pastHero");
      cmd(app, admin, "ffa mannequin delete Champion");
      cmd(app, admin, "ffa mannequin create Champion statueAdmin");
      future.get().complete(offline);
      h.assertTrue(
          manager.get("Champion").accountName.equals("statueAdmin"),
          "Stale lookup cannot overwrite a replacement with the same id");
      manager.refresh(viewer);
      outgoing(viewer);
      cmd(app, admin, "ffa mannequin delete Champion");
      manager.refresh(viewer);
      h.assertTrue(
          !of(outgoing(viewer), ClientboundRemoveEntitiesPacket.class).isEmpty(),
          "Deletion cleans up visible display packets");
      future.set(new CompletableFuture<>());
      cmd(app, admin, "ffa mannequin create Closed pastHero");
      manager.close();
      future.get().complete(offline);
      h.assertTrue(
          !app.storage.state().mannequins.containsKey("Closed"),
          "Shutdown cancels outstanding display creation");
    } finally {
      manager.close();
      app.mannequins = original;
      app.server.getPlayerList().deop(admin.getGameProfile());
      for (var p : List.of(admin, person, viewer)) app.server.getPlayerList().remove(p);
      app.storage.commit(saved);
    }
  }

  private static final class VisualId extends net.minecraft.world.entity.decoration.ArmorStand {
    VisualId(GameTestHelper h, int id) {
      super(EntityType.ARMOR_STAND, h.getLevel());
      setId(id);
    }
  }
}
