# Mod compatibility and NPC shops

[Wiki home](Home.md) · [Permissions](Permissions-and-Access.md) · [Economy](Economy-and-Shops.md)

## Fantasy Feudalism: Mercenaries

The supplied Mercenaries **1.20.4 and 1.20.5** versions target **Minecraft 1.21.1 / NeoForge 21.1.262 or a supported later 21.1 patch**. FFA **0.8.3** supports both inspected settlement APIs. These are Mercenaries version numbers, not Minecraft versions.

Mercenaries retains its town system, commands, bell groups, owners, members, enemies and item-based payments. FFA reads its settlement data for overlap and membership checks; it does not replace the system or automatically convert its payment items to Denar.

A claimed settlement overlapping a new/edited protected region is rejected unless the administrator makes an explicit [overlap override](Regions.md). With an override, FFA's flags still apply. Membership does not automatically give a player FFA administrator/build bypass.

Supported NPC trading and charter/empty-hand bell-management interactions are enabled by default. Mercenaries still decides who may perform the town operation. Its entity types and Easy NPC entity types are exempt from the ordinary spawning block; loaded residents are not purged.

**Vanilla residents need attention:** an operation that spawns ordinary vanilla villagers can still encounter `mob_spawning = false`. For a deliberately overlapped town, test recruitment/breeding/settlement workflows. A child region with `mob_spawning = true` and `hostile_spawning = false` can allow ordinary residents while keeping hostile spawning blocked. This is a configurable regional exception, not a universal resident-detection adapter.

No Mercenaries patch was needed for the tested adapter. A future public settlement/membership API would make updates less fragile. If an unsupported version is installed, validation can refuse startup or region editing until compatibility is restored or the administrator explicitly reviews the affected region overrides. Simply disabling the integration is not a promise that settlement-overlap validation is bypassed safely.

### Optional housing checks for villager restocking

Mercenaries 1.20.5 lets you set `requireHousingForRestocking = false` under `[settlement]` in its `ffm-caravans-server.toml`. Edit the effective file in `config/` or the world `serverconfig/` override, then restart. Villagers use vanilla restocking requirements, including workstation access. This setting controls housing validation for restocking; town ownership, bell groups, management and FFA's region-overlap checks remain active.

If FFA 0.8.2 reports **“Unverified Mercenaries version: 1.20.5”**, update to FFA **0.8.3 or later**. That crash is a version check in the adapter and is independent of this Mercenaries config option. Existing FFA region definitions and Mercenaries towns need no conversion for this compatibility update. An actual claimed-town overlap still needs the normal explicit region override.

## Easy NPC 7.18.0 and 7.20.0: native Denar trading

Use [Easy NPC Denar Trading](Easy-NPC-Trading.md) to assign explicit Denar prices to selected native merchant offers. Prices are per NPC UUID and offer number; unconfigured NPCs retain their item trades. No Easy NPC or Mercenaries patch is required.

### Optional command-action shop button

1. Install the required Easy NPC core/UI components and their dependencies. The supplied 7.18.0 and 7.20.0 small bundle files have no embedded core/UI JARs; install the matching separate core and UI files.
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

Both action and typed player command reach FFA's same validated transaction path. Native merchant offers can also use Denar when explicitly priced with `/ffa npctrade`. Use Easy NPC's own documentation for creating NPC entities and editing their dialogue/model.

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

## FTB Quests Denar integration

FFA **0.10.0 or later** supports the uploaded FTB Quests **2101.1.39**, tested with FTB Library **2101.1.40**, FTB Teams **2101.1.12** and Architectury **13.0.12**. Teams is a required Quests dependency even when players only use solo quests; creating a party is optional.

Use the built-in Custom Reward and Custom Task editor with `ffa_denar_reward_AMOUNT` or `ffa_denar_pay_AMOUNT` tags. Native individual/team rewards and repeat cycles apply; a shared payment task charges only its submitter. Denar stays in FFA's individual wallets. See the [complete quest-editor examples and team budgeting](FTB-Quests.md).

FFA does not register a new client task type or replace FTB Library's global currency provider. FFA stays server-only. Install FTB Quests, FTB Teams, FTB Library and Architectury on the server and players’ clients. Easy NPC's existing Denar trade setup is unchanged.

## Fantasy Feudalism Items lanterns

FFA **0.10.1** supports the uploaded **Items 0.1.7** lantern fuel/weather handlers. Protected areas pause placed-lantern fuel and weather extinguishing by default. The `lantern_burnout` region flag supports child overrides; [configuration and examples](Flags-and-Interactions.md#keep-spawn-lanterns-lit) explain manual switches, previously burnt-out lanterns, and the placed-light scope.

No Items update or FFA client installation is needed for this adapter. It is optional: servers without Items retain vanilla lantern behavior. The private Items fuel layout is patched only for verified 0.1.7; this does not claim support for other Items versions.

## Supplementaries display stands

FFA **0.10.2** protects equipment on **Supplementaries 1.21.1-4.1.4** hat stands using the same `entity_interact` and `containers` checks as vanilla armor stands. Both flags must allow access at the player and stand. Attacks/projectiles use `hanging_damage`. See [setup and a child wardrobe example](Flags-and-Interactions.md#protect-armor-stands-and-hat-stands).

The runtime suite uses the supplied **Moonlight 1.21.1-3.8.5** with that Supplementaries build (which requires Moonlight 3.8.3 or later). FFA stays server-only; these other mods retain their own installation requirements. No third-party JAR is bundled. The optional private equipment hook is gated to the inspected Supplementaries version, and servers without Supplementaries load normally.
