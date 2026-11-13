package dev.fantasyfeudalism.administration;

import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Read-only bundled pack on the existing Minecraft port; no extra listener or filesystem route. */
public final class PortalPack {
  public static volatile PortalPack active;
  private static final Map<Connection, String> ADDRESSES =
      Collections.synchronizedMap(new WeakHashMap<>());
  final byte[] bytes;
  final String sha1, path;
  final UUID id;
  private final Administration app;
  private final Map<Connection, UUID> offered = new WeakHashMap<>();

  public PortalPack(Administration app) throws Exception {
    this.app = app;
    try (var in = PortalPack.class.getResourceAsStream("/resourcepacks/ffa-portals.zip")) {
      if (in == null) throw new IllegalStateException("Bundled portal pack is missing.");
      bytes = in.readAllBytes();
    }
    sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
    path = "/ffa/resource-pack/" + sha1 + ".zip";
    id = UUID.nameUUIDFromBytes(bytes);
    active = this;
    Storage.atomic(
        app.storage.directory.resolve("portal-pack/README.txt"),
        "The matching bundled pack is extracted as ffa-portals.zip.\n"
            + "It is normally offered automatically over the Minecraft TCP port.\n"
            + "For proxy/CDN hosting set portalPack.url to an HTTP(S) URL serving this exact"
            + " ZIP.\n");
    java.nio.file.Path output = app.storage.directory.resolve("portal-pack/ffa-portals.zip");
    if (!java.nio.file.Files.exists(output)
        || !Arrays.equals(java.nio.file.Files.readAllBytes(output), bytes)) {
      var tmp = java.nio.file.Files.createTempFile(output.getParent(), ".ffa-pack-", ".zip");
      try {
        java.nio.file.Files.write(tmp, bytes);
        java.nio.file.Files.move(
            tmp,
            output,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
      } finally {
        java.nio.file.Files.deleteIfExists(tmp);
      }
    }
  }

  public static void address(Connection connection, String host, int port) {
    try {
      host = host.split("\u0000", 2)[0];
      if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
      if (host.isBlank() || port < 1 || port > 65535) return;
      URI base = new URI("http", null, host, port, null, null, null);
      if (base.getHost() != null) ADDRESSES.put(connection, base.toASCIIString());
    } catch (Exception ignored) {
    }
  }

  public void close() {
    if (active == this) active = null;
    ADDRESSES.clear();
    offered.clear();
  }

  public boolean enabled() {
    return app.settings.portalPack && app.settings.portals;
  }

  @SubscribeEvent
  public void login(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) offer(player);
  }

  public void offer(ServerPlayer player) {
    if (!enabled() || id.equals(offered.get(player.connection.getConnection()))) return;
    String url = app.settings.portalPackUrl;
    if (url.isEmpty()) {
      String base = ADDRESSES.get(player.connection.getConnection());
      if (base == null) return; // Local/fake/GameTest connections do not need a download.
      url = base + path;
    }
    offered.put(player.connection.getConnection(), id);
    player.connection.send(
        new ClientboundResourcePackPushPacket(
            id,
            url,
            sha1,
            true,
            Optional.of(
                Component.literal(
                    "Animated colored FFA portals require this server resource pack."))));
  }

  public void remove(ServerPlayer player) {
    var previous = offered.remove(player.connection.getConnection());
    if (previous != null)
      player.connection.send(new ClientboundResourcePackPopPacket(Optional.of(previous)));
  }

  @SubscribeEvent
  public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
    if (event.getEntity() instanceof ServerPlayer player)
      offered.remove(player.connection.getConnection());
  }

  public static void response(
      net.minecraft.server.network.ServerCommonPacketListenerImpl listener,
      ServerboundResourcePackPacket packet) {
    var connection = listener.getConnection();
    var pack = active;
    if (pack == null
        || !pack.enabled()
        || !pack.id.equals(packet.id())
        || !pack.id.equals(pack.offered.get(connection))) return;
    if (packet.action() != ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED
        && packet.action() != ServerboundResourcePackPacket.Action.DISCARDED
        && packet.action().isTerminal())
      listener.disconnect(
          Component.literal(
              "The FFA portal resource pack is required. Enable server resource packs and rejoin."
                  + " If downloading fails, contact an administrator."));
  }

  public static final class HttpHandler extends ByteToMessageDecoder {
    private boolean http, replied;

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf input, List<Object> output) {
      int n = input.readableBytes(), start = input.readerIndex();
      if (n == 0) return;
      if (replied) {
        input.skipBytes(n);
        return;
      }
      if (!http) {
        String get = "GET ", head = "HEAD ";
        boolean g = true, h = true;
        for (int i = 0; i < Math.min(n, 5); i++) {
          char c = (char) input.getUnsignedByte(start + i);
          if (i < get.length() && c != get.charAt(i)) g = false;
          if (i < head.length() && c != head.charAt(i)) h = false;
        }
        if (!g && !h) {
          output.add(input.readRetainedSlice(n));
          context.pipeline().remove(this);
          return;
        }
        if (n < 4 || h && n < 5) return;
        http = true;
      }
      int end = -1;
      for (int i = start; i + 3 < start + Math.min(n, 8193); i++)
        if (input.getInt(i) == 0x0D0A0D0A) {
          end = i + 4;
          break;
        }
      if (end < 0) {
        if (n > 8192) reply(context, 431, false, null);
        return;
      }
      if (end - start > 8192) {
        reply(context, 431, false, null);
        return;
      }
      String first =
          input.toString(start, end - start, StandardCharsets.US_ASCII).split("\r\n", 2)[0];
      input.skipBytes(n);
      String[] words = first.split(" ", -1);
      boolean head = words.length == 3 && words[0].equals("HEAD");
      var pack = active;
      if (words.length != 3
          || !Set.of("GET", "HEAD").contains(words[0])
          || !Set.of("HTTP/1.0", "HTTP/1.1").contains(words[2])) {
        reply(context, 400, head, null);
        return;
      }
      if (pack == null || !pack.enabled() || !words[1].equals(pack.path)) {
        reply(context, 404, head, null);
        return;
      }
      reply(context, 200, head, pack.bytes);
    }

    private void reply(ChannelHandlerContext context, int status, boolean head, byte[] body) {
      replied = true;
      if (body == null) body = new byte[0];
      String reason =
          status == 200
              ? "OK"
              : status == 404
                  ? "Not Found"
                  : status == 431 ? "Request Header Fields Too Large" : "Bad Request";
      String headers =
          "HTTP/1.1 "
              + status
              + " "
              + reason
              + "\r\nContent-Type: application/zip\r\nContent-Length: "
              + body.length
              + "\r\n"
              + "Connection: close\r\n"
              + "Cache-Control: public, max-age=31536000, immutable\r\n\r\n";
      context.write(Unpooled.copiedBuffer(headers, StandardCharsets.US_ASCII));
      context
          .writeAndFlush(head ? Unpooled.EMPTY_BUFFER : Unpooled.wrappedBuffer(body))
          .addListener(ChannelFutureListener.CLOSE);
    }
  }
}
