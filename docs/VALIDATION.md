# Validation and release limits

[Complete mod wiki](../wiki/Home.md)

This is a development build, not a certification against every mod combination or exploit in the specification.

## Completed checks

- CI runs the build, policy checks and headless regression suite on Ubuntu and Windows. Atomic-save coverage includes creation, UTF-8 replacement, truncation, replacement failures and temporary-file cleanup.
- Java 21 compilation against NeoForge **21.1.262**, Minecraft **1.21.1**.
- **203** standalone assertions for safe defaults, invalid configuration rejection, unchanged last-valid settings, parent/child geometry, sibling-overlap and duplicate-ID rejection, region TOML round trips, inheritance/overrides, external-edit protection, durable balances and failed-mutation rollback, I/O failure lockout and command parsing (including namespaced IDs and malformed input).
- Headless NeoForge runtime regression suite with actual ServerPlayers, mock network connections, real vanilla player saves, loaded mixins and registered production event handlers.
- The runtime suite was also run with the exact uploaded **Mercenaries 1.20.4**, **Easy NPC 7.18.0**, and **Easy NPC Config UI 7.18.0** JARs installed together.
- Runtime assertions include Adventure Mode entry and original-mode restoration, administrator exemption, inventory/XP preservation, hunger/saturation freezing, blocked fire placement, solid-destination rejection through vanilla teleport entry points, mob-spawn restrictions, purchases/sales and interrupted-transaction recovery settling once.
- Expanded runtime checks cover single-use, expired and stale-button teleport consent, transfers, all 16 persistent portal dye colors, denied portal access, actual command dispatch/permissions, and NPC creation exceptions.
- Native command-tree regression checks cover the actual client command packet, root/action/argument suggestions, partial and namespaced inputs, accessible warp filtering, private home names, disabled modules, immediate permission refresh on reload, quoted messages and vanilla `/execute` redirects.
- Optional void-datapack selection and its dimension definition are checked using Minecraft's real `LevelStem` codec and server registries. Minecraft's GameTestServer deliberately discards custom level stems when creating its test worlds, so this does not replace a dedicated-server travel test.

- Built-in ranks assertions cover default/assigned/inherited membership, priority and deterministic ties, typed nodes, wildcard/specific deny handling, cycles, failed reload preservation, external edit protection and persistence.
- Actual runtime checks cover console rank commands, aliases, generated command-node reference, vanilla grants/denials, redirected aliases, numeric homes, local group gates, assignment/revocation packets, a simulated external broad-grant predicate that cannot expose native FFA management branches, snapshot inclusion, datapack synchronization hooks and a real datapack reload that replaces the command dispatcher while retaining rank grants.
- Chat checks cover permitted self-service nickname changes by a nonadministrator rank, hidden administrator branches, filtered `/ffa help`, independent name/title colors and bold/italic styles through the real display-name event, persistent personal overrides, rank fallback/reset, revocation and unchanged account identity.

- Consent tests cover withheld first-message bodies, clickable/native token acceptance, permanent UUID approval and revocation, stale/replayed/expired requests, denied requests, monitoring copies and persisted per-admin monitoring controls.
- Staff-tool runtime checks cover live editable inventory packets while vanished, actual vanilla player-data persistence, armor updates, locked padding and closing inspections before item trades; public Stats fields and real Mercenaries settlement names are checked.
- Vanish checks cover ordinary/spectator tracking, tab update/removal packets and new observers, native/FFA names, `/list`, direct selectors/tell, hidden public Stats/whispers, suppressed joins, reconnect persistence, mode restoration and privilege revocation. Actual TCP status handling (including stale JSON bypass), both legacy ping response formats and GS4 full-query response builders are exercised without opening external sockets.

- Version 0.3.0 checks cover mixed-case homes/regions, concise private confirmations, explicit child/standalone creation and inherited rule overrides, real block placement/mining in building children, per-region item lists, title packets/styles without chat, continuing/cancelled boundary particles, sign binding and click trades through the real journal, label updates and persistence.
- Travel/moderation checks cover successful vanilla teleport origins, actual death and respawn history, rejected destination preservation, two occupied jail cells, escape correction and blocked teleport/drop/command routes, real-time deadlines including offline expiry, spawn release, chat/warp-ban scope, administrator exemption, configurable portal creation, god damage immunity and explicit smite overriding FFA protection/god mode.
- Additional standalone assertions verify legacy config/region defaults, case-insensitive duplicate rejection, duration limits/overflow and item/title TOML round trips.

- Version 0.3.1 projectile checks run a real flaming tipped arrow from outside spawn through vanilla collision and the registered impact event, verifying cancellation and unchanged health, fire, poison and motion. Additional cases cover player/mob/ownerless arrow damage, tridents without item destruction, fireballs and direct potion hits, PvP-only protection, child combat overrides, wilderness combat and administrator exemptions.

- Version 0.3.2 packet checks verify entry-only and exit-only notices at subtitle size, clearing the previous large title, preserved custom text/color/bold/italic, combined parent/child transitions with independent styles, disabled-message silence and no chat announcements. The size ratio was checked against Minecraft 1.21.1’s renderer (main title 4x, subtitle 2x).

- Version 0.4.0 checks the real rank-command completion results: home-limit values suggest whole numbers without Boolean choices, while Boolean command permissions retain true/false suggestions.

- Version 0.4.0 checks safe new-flag defaults in legacy files, underline validation/round trips, actual hoe/seed/bone-meal/harvest/axe/shovel/shears/flint actions with build/interact disabled, blocked Q/menu/cursor drops without inventory loss, independent ground pickup, boat/minecart boarding and disembarking, player ignition/extinguishing, the existing elytra flag, administrator-assigned and rank-inherited underlines, and self-service permission/override/reset behavior.

- Version 0.5.0 checks native money send/request/accept/deny commands with no debit before consent, expiry/token mismatch/replay protection; immediate admin TP and hidden access; mixed-case shop operations, shared/per-player manual/daily caps, independent buy/sell counters, preserved usage on price/limit edits, journal capacity reservations and exactly-once recovery; guided portal destination creation/access and all sixteen surface model colors.
- Pack checks validate 32-frame original textures, sixteen distinct palettes and Minecraft 1.21.1 pack metadata; test exact ZIP/hash delivery on a real loopback Minecraft TCP listener, normal/legacy game-byte passthrough, fragmented HTTP, bounded headers, forbidden-path rejection, native resource-pack offer URL/hash and duplicate-offer prevention. No extra listener port is required. Real clients still need the acceptance check below.

## Remaining deployment acceptance checks

- Join a dedicated server using an unmodified-by-FFA client; accept the bundled resource pack and confirm animated translucent colored surfaces, dye changes and clickable payment/TPA/whisper prompts from a real client. Verify any proxy/CDN URL used by the live server.
- Test FTB Ranks **2101.1.5** with the actual binary and the server's rank rules. Its adapter was checked against the official source API; that JAR was not uploaded. LuckPerms and FTB Essentials were not installed in the supplied runtime test set.
- Enable the optional dimension on a dedicated staging server and verify construction, first arrival, return travel and restart behavior. Keep the shipped setting disabled until that check passes.
- Exercise first join versus existing player, bed/anchor fallback and cross-dimensional death respawns with real client sessions.
- Run the server's actual machine/automation mods against boundaries, including outside-to-inside effects; verify fluid/piston and dispenser cases with those mods.
- Perform process-kill/power-loss recovery tests and a multiplayer load/TPS benchmark on a copy of the server world. The journal tests simulate interrupted transaction phases; they are not a power-failure test.

## Deliberate safety boundaries

Ordinary mod teleports load only previously saved full chunks through a bounded queue. Unknown chunks are rejected rather than generating terrain. Random teleport searches only already loaded terrain, with a configured attempt limit.

NeoForge placement/break/spawn/interaction events and the included vanilla fire/fluid/dispenser/teleport hooks protect supported effects. A mod that writes world blocks directly without a reliable event hook needs an additional verified adapter. This build does not claim universal protection against every automation mod.

No anti-X-ray packet obfuscator is bundled. Region protection cannot hide block information already sent to clients; a compatible external anti-X-ray implementation must be evaluated separately if needed.

Backups do not include world blocks or player inventories. No live economy rollback is provided; see the recovery procedure before restoring snapshots. Unknown transaction/inventory mismatches lock recovery instead of guessing.

Mercenaries keeps its town system and its own item-based transactions. The virtual Denar ledger does not automatically replace Mercenaries' payment items. Easy NPC's native merchant screens similarly retain their own item trading; its supported Denar connection is the documented player-command action into this mod's shops.

- Version 0.5.1 checks matching opposite texture edges on every frame for all sixteen colors, spawn-protection defaults/legacy configs/disabled and invalid durations, and real login/death-respawn events. Runtime checks cover environmental and bypass damage, direct death, projectile impact, administrator coverage, precise expiry, shorter-than-vanilla duration, disabled protection and logout cleanup. Portal appearance still needs a real client review; automated checks establish edge continuity rather than artistic quality.

- Version 0.6.0 checks rectangular border selection, inclusive/reversed corners, persistence and legacy state, dimension isolation, real player collision on all four sides, outside teleport rejection, actual client command-tree hiding, and actual outgoing hidden/near border packets. Native settings remain intact for save/removal; close-only visibility uses vanilla packets rather than client code. A real client should verify wall appearance, corners, flight and portal approaches.

- A separate real dedicated NeoForge + FFA server (without the supplied third-party mods or players) booted with the void dimension enabled, a 256 MB initial / 2 GB maximum heap, and generated/edited a spawn chunk. Sample resident memory was ~0.8 GB, live heap ~0.4 GB; temporary force-loading was removed. This tests dimension creation beyond the GameTest codec path, but does not establish full-modpack memory requirements or replace real-player dimension-travel acceptance.

- Version 0.6.1 first reproduced the real native `/execute in ... run tp` failure with a level-four OP, an obstructed source coordinate and a safe destination. Regression coverage checks actual cross-dimension arrival, specific unsafe-destination failures without native success feedback, relative command/API coordinates, and preserved non-admin region restrictions plus OP bypass. Vanilla commands are checked after coordinate-changing events with the destination dimension available.

- The 0.6.1 suite also ran on a disposable normal dedicated server with `ffa:spawn` enabled (not GameTestServer). The same OP command assertions exercised the actual spawn dimension, plus unsafe arrival rejection, relative coordinates and permissions. The test server used IPv4/NIO for the sandbox network environment; no production host configuration was changed.

- Version 0.7.0 checks legacy/configurable combat/PvP defaults and persisted preferences/cooldowns. Runtime checks use actual player/attributed-projectile damage, cancelled safe-region hits, expiry/refresh, travel and native TP commands, cancelled warmup/loading and execution rechecks, both-direction PvP consent, harmful splash/lingering effects versus beneficial effects, environmental/mob independence, real logout/login, exact five-minute expiry, administrator exemptions, death cleanup, and actual client command-tree permissions.
