# Fantasy Feudalism: Administration

A server-side NeoForge 21.1.262 mod for Minecraft 1.21.1 and Java 21. Players do not need this administration mod installed. Animated recolored FFA portals use a bundled standard Minecraft resource pack, offered automatically for players to accept. Other installed mods may have their own client requirements.

**[Open the complete mod wiki](wiki/Home.md)** — installation, every command, configuration, regions, portals, shops, built-in ranks, chat styling, compatibility, recovery and IntelliJ guides.

**Development release 0.11.1.** Read [validation and limits](docs/VALIDATION.md) before deployment. Use a copy of your server world for the first installation.

## Installation

1. Put the built `fantasy-feudalism-administration-0.11.1.jar` in the **server's** `mods` directory.
2. Start the server. General settings are created in `config/ffa/server.toml`. World data is stored in `<world>/ffa/`.
3. As an operator, stand at one corner of spawn and run `/ffa pos1`. Stand at the opposite corner and run `/ffa pos2`. Run `/ffa spawn create`.
4. Stand at a safe arrival location with solid ground and run `/ffa spawn setpoint`.
5. Players use `/ffa spawn`. All commands are under `/ffa`; vanilla, FTB Essentials and Mercenaries keep their own command roots.

Protected regions cover the world's full build height. Spawn children must lie entirely within the main spawn. Sibling and standalone overlaps are rejected. A region touching a claimed Mercenaries settlement is rejected unless its creator explicitly adds `override`; configuration uses `settlementOverride = true`.

Administrators bypass gameplay restrictions, Adventure Mode, home limits and command teleport warmups/cooldowns. Portal loop protection still applies. Inventory, balance and destination-safety validation always apply. Automatic world effects without an administrator actor remain controlled by region flags.

Spawn defaults freeze hunger/saturation and block building, breaking, block/container interactions, PvP, player damage, ordinary entity interactions, natural mob spawning, fire, explosions, fluid/piston changes, automation, flight and elytra flight for nonadministrators. NPC trading and settlement management remain enabled. Explicit child flags override inherited settings. A child allowing building or breaking restores the player’s prior mode so those permissions work; the surrounding spawn retains Adventure Mode.

Built-in [ranks](wiki/Ranks.md) support permissions, inheritance, priority, default membership and explicit permissions for other mods' commands. [Chat tools](wiki/Chat-and-Nicknames.md) support nicknames, titles, colors, bold and italic, with rank-based self-service permissions. [Staff tools](wiki/Staff-Tools.md) add vanish and live inventory editing. [Whispers and public stats](wiki/Whispers-and-Stats.md) include persistent recipient consent, administrator monitoring controls and player statistics. Unauthorized management commands are hidden from the client command tree and `/ffa help`.

See [commands](docs/COMMANDS.md), [configuration and recovery](docs/CONFIGURATION.md), and [compatibility](docs/COMPATIBILITY.md).

## Build and verify

```sh
./gradlew check build
./gradlew runGameTestServer
# Optional local integration JARs, never packaged in the result:
./gradlew runGameTestServer -PcompatModsDir=/absolute/path/to/test-mods
```

`check` runs the standalone policy/persistence assertions. The GameTest server runs opt-in tests with real players, saves, event handlers and mixins. Tests are registered only when the development run sets `ffa.tests=true`.

No uploaded third-party JARs are included in this repository or release.

Version 0.3.0 also adds persistent region visualization, explicit child/standalone creation, per-region usable items and styled entry/exit titles, sign shops, `/ffa back`, `/ffa balance set`, [timed punishments/jail](wiki/Moderation.md), configurable administrator-only portal creation, smite and god mode. Home and region names preserve uppercase letters; command confirmations are concise.

Version 0.3.1 cancels projectile impacts on protected players even when shots originate outside spawn. It preserves child combat overrides, administrator exemptions and thrown tridents.

Version 0.3.2 uses the smaller centered subtitle size for region entry/exit notices, retaining configured messages, colors, bold and italic. Vanilla subtitle text is half the previous title size.

Version 0.4.0 corrects rank-node value suggestions: `ffa.homes.maximum` offers whole-number examples instead of Boolean values.

Version 0.4.0 adds independent farming/tool permissions, separate item drop/pickup flags, player ignition and vehicle boarding controls, and underlining for chat/rank names, titles and region notices. New action flags default to blocked in protected areas; existing files load with those defaults. [Configure the action flags](wiki/Flags-and-Interactions.md).

Version 0.5.0 adds `/ffa balance send/request` with payment consent, immediate administrator `/ffa tp`, mixed-case shop names, configurable shared/per-player shop quotas, guided warp/portal setup, and filled animated portals in sixteen colors. The resource pack is bundled in the JAR and normally served over the existing Minecraft TCP port.

Version 0.5.1 smooths the colored portal animation across adjacent tiles and adds eight seconds of invincibility on login and death respawn. Configure `spawn.invulnerabilitySeconds` in `config/ffa/server.toml` (0 disables). See [spawn protection](wiki/First-Join-and-Respawns.md#spawn-invincibility).

Version 0.6.0 adds [rectangular world borders](wiki/World-Borders.md): select `/ffa pos1` and `/ffa pos2`, then `/ffa worldborder set`. Each dimension has its own boundary; the standard wall is visible only within eight blocks by default.

Version 0.6.1 fixes vanilla `/execute in DIMENSION run tp` checks using the wrong world, relative-coordinate validation, and false teleport-success feedback after FFA rejects a move. Administrator bypass and destination safety remain enforced.

Version 0.7.0 adds [combat travel restrictions and personal PvP](wiki/Combat-and-PvP.md): configurable travel blocking after player hits and `/ffa pvp` with a saved five-minute cooldown.

Version 0.7.0 also adds administrator `/ffa tphere PLAYER` to bring another player immediately, with destination safety and jail protections retained.

Version 0.8.0 adds a [rules acceptance room](wiki/Rules-Room.md), `/ffa setfirstspawn`, and [Denar prices in Easy NPC trading menus](wiki/Easy-NPC-Trading.md). Pending players cannot use any command except Accept/Deny, send chat, or see nonadministrator chat. Existing players also accept once; administrators are exempt.

Version 0.8.1 fixes portal entry: body contact is checked every tick, including across chunk edges. Portals skip command warmups and continue saved-destination loading while players walk. Arrival contact prevents cooldown-expiry bounce loops; ordinary command movement cancellation is retained.

Version 0.8.2 adds Easy NPC 7.20.0 compatibility for native Denar trading while retaining support for 7.18.0. Existing FFA prices and stock records use the same format. See [Easy NPC trading](wiki/Easy-NPC-Trading.md) for supported installation files.

Version 0.8.3 supports Mercenaries 1.20.5 and retains 1.20.4 compatibility, fixing startup refusal after that mod update. Mercenaries’ optional housing checks for villager restocking remain separate from FFA settlement-overlap protection; see [compatibility](wiki/Mod-Compatibility.md).

Version 0.9.0 adds [server-only region ambience](wiki/Sound-Regions.md): upload mono Ogg Vorbis sounds, select two corners, and fade tracks in/out with automatic repetition. Players download audio through a resource pack; no FFA client code is required. The [Easy NPC guide](wiki/Easy-NPC-Trading.md#worked-example-a-village-baker) now includes a full Denar baker example.

Version 0.10.0 adds [Denar rewards and payment tasks in FTB Quests](wiki/FTB-Quests.md). Use the built-in Custom Reward/Task editor and Denar tags, with native team/individual claims and repeat rules. Wallet receipts prevent duplicate payments after interruptions; FFA remains server-only, and Easy NPC trading is unchanged.

Version 0.10.1 keeps placed spawn lanterns lit with Fantasy Feudalism Items 0.1.7. The new `lantern_burnout = false` default pauses their fuel use and weather extinguishing in protected regions; children can override it. See [lantern protection](wiki/Flags-and-Interactions.md#keep-spawn-lanterns-lit).

Version 0.10.2 protects armor-stand and Supplementaries hat-stand equipment. Both `entity_interact` and `containers` must allow access at the player and stand; `hanging_damage` separately controls attacks/projectiles. Default spawn settings block all three, and administrators remain exempt. See [display stand protection](wiki/Flags-and-Interactions.md#protect-armor-stands-and-hat-stands).

Version 0.11.0 adds [persistent player mannequins/holograms](wiki/Mannequins-and-Holograms.md) with saved skins, copied display equipment and styled name/title labels. They are server-only visual displays and never count as online players. The new [`weather` region flag](wiki/Flags-and-Interactions.md#stop-snow-and-ice-in-spawn) defaults to false in protected areas, preventing natural snow/ice, precipitation block changes and natural lightning while retaining visible weather and child overrides.

Version 0.11.1 lets administrators load saved destination chunks and generate missing terrain during FFA and vanilla teleports, including cross-dimension `/execute ... run tp`. Collision, hazard and border checks remain active. Ordinary players keep the bounded saved-only queue; RTP searches remain limited to loaded terrain. See [administrator chunk loading](wiki/Staff-Tools.md#teleporting-to-unloaded-chunks).
