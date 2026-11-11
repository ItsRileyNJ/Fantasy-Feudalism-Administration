# Mod compatibility and NPC shops

[Wiki home](Home.md) · [Permissions](Permissions-and-Access.md) · [Economy](Economy-and-Shops.md)

## Fantasy Feudalism: Mercenaries

The supplied `fantasyfeudalism-mercenaries-1.20.4.jar` declares **Minecraft 1.21.1 / NeoForge 21.1.262**; `1.20.4` is its mod version. FFA's current adapter is pinned to that version.

Mercenaries retains its town system, commands, bell groups, owners, members, enemies and item-based payments. FFA reads its settlement data for overlap and membership checks; it does not replace the system or automatically convert its payment items to Denar.

A claimed settlement overlapping a new/edited protected region is rejected unless the administrator makes an explicit [overlap override](Regions.md). With an override, FFA's flags still apply. Membership does not automatically give a player FFA administrator/build bypass.

Supported NPC trading and charter/empty-hand bell-management interactions are enabled by default. Mercenaries still decides who may perform the town operation. Its entity types and Easy NPC entity types are exempt from the ordinary spawning block; loaded residents are not purged.

**Vanilla residents need attention:** an operation that spawns ordinary vanilla villagers can still encounter `mob_spawning = false`. For a deliberately overlapped town, test recruitment/breeding/settlement workflows. A child region with `mob_spawning = true` and `hostile_spawning = false` can allow ordinary residents while keeping hostile spawning blocked. This is a configurable regional exception, not a universal resident-detection adapter.

No Mercenaries patch was needed for the tested adapter. A future public settlement/membership API would make updates less fragile. If an unsupported version is installed, validation can refuse startup or region editing until compatibility is restored or the administrator explicitly reviews the affected region overrides. Simply disabling the integration is not a promise that settlement-overlap validation is bypassed safely.

## Easy NPC 7.18.0: a Denar shop button

1. Install the required Easy NPC core/UI components and their dependencies. The supplied small bundle file has no embedded core/UI JARs; do not rely on that file alone.
2. Create an FFA offering:

   ```text
   /ffa shop set stone minecraft:stone 5 2
   ```

3. In Easy NPC's own configuration interface, add a dialogue/button/interaction action that executes a command **as the interacting player**, using ordinary player permission:

   ```text
   ffa shop buy stone 1
   ```

4. Add a sale action if wanted:

   ```text
   ffa shop sell stone 1
   ```

5. Fund a nonoperator test player or let them sell matching items, then test the button.

UI labels can vary with the Easy NPC screen/version; the required behavior is its **player-command action**, not a server/operator command impersonation. If its security config needs an allowlist, allow the exact actions required. Do not give the NPC general elevated `/ffa` management authority to run a player shop.

Both action and typed player command reach FFA's same validated transaction path. Easy NPC's native villager-style merchant screens retain their item trades; they are not automatically a Denar GUI. Use Easy NPC's own documentation for creating NPC entities and editing their dialogue/model.

## FTB Essentials

Detection disables FFA's overlapping native home, TPA and request-privacy operations. Their `/ffa` suggestions are hidden. FTB Essentials keeps its own `/home`, `/tpa`, `/spawn`, `/warp`, and other roots; `/ffa spawn` and FFA warps/portals remain distinct.

Configure FTB Essentials' own home limits, creation rules and request settings when it owns those features. FFA does not automatically rewrite them. Supported arrival/teleport protection hooks still apply, but direct position writes by another mod may require a verified adapter.

If another mod also handles first join or death respawns, choose a consistent owner for those redirects and test the complete stack. FFA defaults those policies to off/vanilla.

## Permission providers and other integrations

FFA now also includes [built-in ranks](Ranks.md) and [chat tools](Chat-and-Nicknames.md), managed under `/ffa`. Explicit external Boolean decisions take precedence over local ranks; local rank/chat data is separate and is not automatically imported from another mod. FFA command help and client suggestions hide unauthorized management tools.

FTB Ranks **2101.1.5** uses the public API checked in its source. LuckPerms is another optional provider. See [permission nodes](Permissions-and-Access.md); the exact FTB Ranks JAR, LuckPerms and FTB Essentials were not in the uploaded runtime test set.

Protection covers supported NeoForge events and included vanilla hooks. A machine mod that writes blocks/inventories outside those hooks needs its own verified adapter. There is no bundled anti-X-ray obfuscator: region rules cannot hide block data already sent to a client.

## Staff visibility and inventory tools

[FFA vanish](Staff-Tools.md) filters supported vanilla/FFA player-facing presence paths, including status/query responses. Other mods' web maps, custom online lists and direct APIs require verified adapters; server operators/logs retain real presence. Staff inventory editing closes before FFA item trades and does not replace another mod's inventory rules. [Public Stats](Whispers-and-Stats.md) reads Mercenaries owner/member settlement names and active local/FTB ranks without changing their data.
