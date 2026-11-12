# Getting started

[Wiki home](Home.md) · [IntelliJ instructions](IntelliJ-and-Building.md)

## Install or update

1. Use a Minecraft **1.21.1** server with NeoForge **21.1.262** and Java **21**.
2. Download the [0.4.0 JAR](../dist/fantasy-feudalism-administration-0.4.0.jar) using GitHub's **Download raw file** button, or [build it in IntelliJ](IntelliJ-and-Building.md).
3. Stop the server and put the JAR in its `mods` folder. Replace the previous FFA JAR; keep one FFA version installed.
4. Start the server and confirm its log lists **Fantasy Feudalism: Administration 0.4.0**.
5. Join as an operator. Type `/ffa`, add a space, and press Tab.

For an update, retain your configuration and world data. Existing 0.1.x config/state files remain compatible; the new ranks file is created automatically with an empty default member rank. Take a consistent server backup before updating a live world.

## Create the first spawn

The following example uses an existing, built Overworld area. Coordinates are examples: choose suitable positions in your world.

1. Build your spawn plaza and a safe arrival platform first. Operators can build inside protected regions.
2. Stand at the first horizontal corner, for example X **-50**, Z **-50**, and run:

   ```text
   /ffa pos1
   ```

3. Stand at the opposite corner, X **50**, Z **50**, and run:

   ```text
   /ffa pos2
   /ffa spawn create
   ```

4. Stand on a full solid arrival floor, for example with your feet at **0.5, 65, 0.5**, leaving room for a player to stand. Face the direction arriving players should look, then run:

   ```text
   /ffa spawn setpoint
   ```

5. Inspect the saved area:

   ```text
   /ffa region list
   /ffa region visualize spawn
   ```

Selections use the block containing **your feet**, not a block you look at or click. Both corners must be in the same dimension. Y does not limit a region: regions cover full build height. The example includes X/Z blocks -50 through 50.

A successful creation privately confirms the operation. The region appears immediately in `<world>/ffa/regions.toml`. If Mercenaries settlement overlap is rejected, read [the overlap procedure](Regions.md) before considering an override.

## Verify with a normal player

Use a nonoperator test player. Check that:

- Entering spawn changes their mode to Adventure; leaving the protected spawn restores their original mode.
- Building, breaking, normal block/container interactions, flight, damage and PvP are blocked by default.
- They can run `/ffa spawn` and arrive with their inventory and XP unchanged.
- Configured NPC trading and settlement-management exceptions remain available.

Operators bypass these gameplay restrictions. Do not use your operator account as proof that protection is broken.

## Customize next

- [Add child regions](Regions.md), such as a market or arena.
- [Allow specific doors and containers](Flags-and-Interactions.md).
- [Create warps and portals](Warps-and-Portals.md).
- [Add Denar shops](Economy-and-Shops.md).

Creating the spawn region does **not** enable first-join redirection, change vanilla death respawns, create a building, or enable the void dimension. Those are separate, configurable choices.
