# Validation and release limits

[Complete mod wiki](../wiki/Home.md)

This is a development build, not a certification against every mod combination or exploit in the specification.

## Completed checks

- CI runs the build, policy checks and headless regression suite on Ubuntu and Windows. Atomic-save coverage includes creation, UTF-8 replacement, truncation, replacement failures and temporary-file cleanup.
- Java 21 compilation against NeoForge **21.1.262**, Minecraft **1.21.1**.
- **102** standalone assertions for safe defaults, invalid configuration rejection, unchanged last-valid settings, parent/child geometry, sibling-overlap and duplicate-ID rejection, region TOML round trips, inheritance/overrides, external-edit protection, durable balances and failed-mutation rollback, I/O failure lockout and command parsing (including namespaced IDs and malformed input).
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

## Remaining deployment acceptance checks

- Join a dedicated server using an unmodified-by-FFA client; confirm visible portals, dye interaction and clickable request messages from a real client.
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
