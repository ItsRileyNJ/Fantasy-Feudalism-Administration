# Optional void spawn dimension

[Wiki home](Home.md) · [Configuration](Configuration.md)

The optional dimension is **`ffa:spawn`**. It uses an empty generator: no terrain, automatic platform, buildings or structures are supplied by FFA. It defaults to disabled.

## Enable and construct

1. Make a consistent backup and stage this on a copy of the server first. Dedicated-server dimension travel remains on the [acceptance checklist](../docs/VALIDATION.md).
2. In the existing `[dimension]` section of `config/ffa/server.toml`, set:

   ```toml
   enabled = true
   ```

3. Stop/restart the server. `/ffa reload` cannot create or remove dimensions.
4. As an in-game operator, prepare one construction chunk. The following is an **optional manual platform example** that you choose to run, not an automatically generated structure:

   ```text
   /gamemode creative
   /execute in ffa:spawn run forceload add 0 0
   /execute in ffa:spawn run fill 2 79 2 12 79 12 minecraft:stone
   /execute in ffa:spawn run tp @s 8.5 80 8.5
   ```

5. Build/import your own spawn and stand on a safe arrival floor, facing the intended direction:

   ```text
   /ffa spawn setpoint
   ```

6. Define the main spawn/children as appropriate, then release the temporary forced load when ready:

   ```text
   /execute in ffa:spawn run forceload remove 0 0
   ```

FFA teleports can subsequently load previously saved full chunks through their bounded queue. They will not generate missing destination terrain to rescue an invalid point.

## How protection works here

Spawn default flags apply throughout the enabled dimension, including areas without a main spawn region. Main spawn/children provide the normal hierarchy and overrides. Standalone regions retain their distinct region-type behavior; use the spawn/child hierarchy for the dedicated spawn design.

Players retain their inventory/XP in ordinary dimension travel. The destination must still be safe and permitted. Administrators bypass gameplay restrictions, but do not have permission to teleport into solid blocks.

## Already have Overworld spawn?

There can be only **one main spawn region for the server**. Setting a new teleport point does not move the old region. Choose one migration approach:

- Edit the existing main region and all its children together in `regions.toml`, updating dimensions/bounds so the children remain enclosed, then perform a valid reload after the dimension exists.
- Delete the old children and parent using commands, then create the new hierarchy in the void dimension.

Keep a copy of the old definitions. Do not create a second main spawn or silently re-parent old children. Independent Overworld sites can be deliberately converted to standalone regions with that module enabled and legal, non-overlapping definitions.

Update existing warps from their new locations with `/ffa setwarp NAME`; linked portals keep following the named warp. Homes and other saved destinations are not automatically migrated.

## Imports and disabling

Use compatible structure/schematic/world-editing tools for imports, following their version and backup instructions. FFA provides the void dimension and protection; it does not include a destructive world importer or support every schematic format itself.

Before disabling, move players and important spawn/warp destinations elsewhere and plan for saved homes. Preserve the world/dimension backup. Change the config and restart only after testing the migration. FFA does not automatically delete dimension files, convert world chunks, or promise that removing a dimension is harmless to every saved reference.

## Memory use

Enabling the empty dimension does not reserve a fixed 8 GB or automatically force-load its chunks. Remove temporary construction force-loads when done. Hosting panels can include committed heap and native memory; see [RAM-limit diagnosis and JVM settings](Troubleshooting.md#hosting-panel-stays-near-the-ram-limit).
