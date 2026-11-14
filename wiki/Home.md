# Fantasy Feudalism: Administration Wiki

The operator and player manual for **development version 0.7.0**, Minecraft **1.21.1**, NeoForge **21.1.262**, and **Java 21**.

FFA manages protected spawn regions, configurable interactions, safe travel, dyeable portals, and virtual **Denar** shops. Install FFA on the server; players do not need the FFA JAR. Other mods can have their own client requirements.

**Start here:** [Install and create spawn](Getting-Started.md). Already using IntelliJ IDEA? See [IntelliJ and building](IntelliJ-and-Building.md).

## Find a task

| I want to… | Guide |
| --- | --- |
| Install, update, or set up the first protected spawn | [Getting started](Getting-Started.md) |
| Create a marketplace, arena, or independent protected area | [Regions](Regions.md) |
| Allow doors, containers, NPCs, combat, flight, or other interactions | [Flags and interactions](Flags-and-Interactions.md) |
| Edit every server setting or change region boundaries in a file | [Configuration](Configuration.md) |
| Set up warps and colored physical portals | [Warps and portals](Warps-and-Portals.md) |
| Use homes, teleport requests, privacy, back, or random teleport | [Player guide](Player-Guide.md) |
| Create Denar shops, fund players, or enable transfers | [Economy and shops](Economy-and-Shops.md) |
| Create and assign ranks, inherit permissions, or control other mods' commands | [Built-in ranks](Ranks.md) |
| Vanish or edit an online player's inventory | [Staff tools](Staff-Tools.md) |
| Send consent-based whispers, monitor them, or view player stats | [Whispers and stats](Whispers-and-Stats.md) |
| Assign chat colors, nicknames, titles, bold, and italic | [Chat and nicknames](Chat-and-Nicknames.md) |
| Give a rank management rights or restrict a gate to a group/team | [Permissions and access](Permissions-and-Access.md) |
| Use Mercenaries, Easy NPC, FTB Essentials, FTB Ranks, or LuckPerms | [Mod compatibility](Mod-Compatibility.md) |
| Build a spawn in an empty dimension | [Void spawn dimension](Void-Spawn-Dimension.md) |
| Configure new-player arrivals and death respawns | [First join and respawns](First-Join-and-Respawns.md) |
| Back up, inspect logs, or recover from a save failure | [Backups and recovery](Backups-and-Recovery.md) |
| Solve missing suggestions, failed builds, or refused teleports | [Troubleshooting](Troubleshooting.md) |
| Look up exact syntax | [Complete command reference](Command-Reference.md) |

## Three rules to remember

1. All commands start with **`/ffa`**. `/spawn`, `/warp`, and `/home` from other mods are separate commands. Type `/ffa`, add a space, and press **Tab**.
2. **Operators are exempt from gameplay restrictions.** Test protection with a nonoperator account; switching an operator to Survival does not remove the exemption.
3. The **spawn teleport point** and **spawn protection region** are separate. Set both. Moving the teleport point does not move the region.

## Current release boundaries

This manual describes implemented behavior, not every proposed feature in the original design. The [validation record](../docs/VALIDATION.md) distinguishes tested behavior from staging work still required, including real-client workflows, the exact FTB Ranks binary, dedicated void-dimension travel, automation compatibility, and crash/load testing.

FFA does not bundle an anti-X-ray engine, a schematic importer, or a Denar merchant GUI. Other mods' direct world writes may require additional adapters. Backups made by FFA do not include world blocks or vanilla player inventories.

The pages are published in the repository so they are available alongside the source and can be reviewed and updated with it.

- [Punishments and jail](Moderation.md)

- [World borders](World-Borders.md): select two corners for a rectangular dimension boundary with a wall visible only nearby.

- [Combat and personal PvP](Combat-and-PvP.md): configurable travel lock after player hits, PvP consent and the five-minute toggle cooldown.
