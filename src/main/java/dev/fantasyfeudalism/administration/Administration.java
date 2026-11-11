package dev.fantasyfeudalism.administration;

import com.mojang.logging.LogUtils;
import java.nio.file.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.server.*;
import org.slf4j.Logger;

@Mod("ffa")
public final class Administration {
  public static final Logger LOG = LogUtils.getLogger();
  public static Administration INSTANCE;
  public Settings settings;
  public Storage storage;
  public Regions regions;
  public Compat compat;
  public Ranks ranks;
  public CommandPermissions commandPermissions;
  public ChatStyles chatStyles;
  public Vanish vanish;
  public Whispers whispers;
  public Teleports teleports;
  public Protection protection;
  public Economy economy;
  public MinecraftServer server;
  public final Path settingsFile = FMLPaths.CONFIGDIR.get().resolve("ffa/server.toml");

  public Administration(IEventBus bus) {
    INSTANCE = this;
    try {
      settings = Settings.load(settingsFile);
    } catch (Exception e) {
      throw new IllegalStateException("FFA configuration invalid: " + e.getMessage(), e);
    }
    bus.addListener(this::packs);
    bus.addListener(
        (net.neoforged.neoforge.event.RegisterGameTestsEvent e) -> {
          if (Boolean.getBoolean("ffa.tests")) e.register(RegressionTests.class);
        });
    NeoForge.EVENT_BUS.addListener(this::start);
    NeoForge.EVENT_BUS.addListener(this::stop);
    NeoForge.EVENT_BUS.addListener(Commands::register);
    NeoForge.EVENT_BUS.addListener(this::datapackSync);
  }

  private void packs(AddPackFindersEvent e) {
    if (settings.dimension)
      e.addPackFinders(
          ResourceLocation.fromNamespaceAndPath("ffa", "spawn_pack"),
          PackType.SERVER_DATA,
          Component.literal("FFA void spawn"),
          PackSource.BUILT_IN,
          true,
          Pack.Position.TOP);
  }

  private void start(ServerStartedEvent e) {
    server = e.getServer();
    try {
      storage = new Storage(server.getWorldPath(LevelResource.ROOT).resolve("ffa"));
      ranks = new Ranks(storage.directory.resolve("ranks.json"));
      compat = new Compat(settings, ranks);
      regions = new Regions(storage.directory.resolve("regions.toml"));
      regions.reload(server, settings, compat);
      teleports = new Teleports(this);
      economy = new Economy(this);
      protection = new Protection(this);
      whispers = new Whispers(this);
      vanish = new Vanish(this);
      NeoForge.EVENT_BUS.register(whispers);
      NeoForge.EVENT_BUS.register(vanish);
      chatStyles = new ChatStyles(this);
      NeoForge.EVENT_BUS.register(chatStyles);
      commandPermissions = new CommandPermissions(this);
      Portals.reindex(this);
      NeoForge.EVENT_BUS.register(teleports);
      NeoForge.EVENT_BUS.register(protection);
      for (var portal : storage.state().portals.values()) Portals.visualize(this, portal);
      LOG.info(
          "FFA enabled; server-only protection. FTB Essentials detected: {}",
          compat.ftbEssentials());
    } catch (Exception ex) {
      throw new IllegalStateException("FFA startup refused: " + ex.getMessage(), ex);
    }
  }

  private void datapackSync(net.neoforged.neoforge.event.OnDatapackSyncEvent e) {
    if (server == null || ranks == null || e.getPlayer() != null) return;
    if (commandPermissions == null || !commandPermissions.current()) {
      if (commandPermissions != null) commandPermissions.close();
      try {
        commandPermissions = new CommandPermissions(this);
      } catch (ReflectiveOperationException ex) {
        throw new IllegalStateException(
            "Cannot apply FFA command rank permissions after datapack reload", ex);
      }
      refreshPermissions();
    }
  }

  private void stop(ServerStoppedEvent e) {
    if (teleports != null) NeoForge.EVENT_BUS.unregister(teleports);
    if (protection != null) NeoForge.EVENT_BUS.unregister(protection);
    if (commandPermissions != null) commandPermissions.close();
    if (chatStyles != null) NeoForge.EVENT_BUS.unregister(chatStyles);
    if (vanish != null) NeoForge.EVENT_BUS.unregister(vanish);
    if (whispers != null) NeoForge.EVENT_BUS.unregister(whispers);
    server = null;
  }

  public void reload() throws Exception {
    Settings candidate = Settings.load(settingsFile);
    if (candidate.dimension != settings.dimension)
      throw new IllegalArgumentException("dimension.enabled requires a full server restart");
    if (!storage.state().trades.isEmpty() || teleports.busy())
      throw new IllegalArgumentException(
          "Wait until pending teleports/trades finish before reload");
    if (candidate.homes != settings.homes
        || candidate.tpa != settings.tpa
        || candidate.back != settings.back
        || candidate.rtp != settings.rtp
        || candidate.pay != settings.pay
        || candidate.leaderboard != settings.leaderboard
        || candidate.privacy != settings.privacy)
      throw new IllegalArgumentException("Command module enable/disable requires restart");
    Compat next = new Compat(candidate, ranks);
    regions.reload(server, candidate, next);
    settings = candidate;
    compat = next;
    refreshPermissions();
  }

  public void refreshPermissions() {
    if (vanish != null) vanish.enforce();
    for (var p : server.getPlayerList().getPlayers()) {
      protection.update(p);
      p.refreshDisplayName();
      server.getCommands().sendCommands(p);
    }
  }
}
