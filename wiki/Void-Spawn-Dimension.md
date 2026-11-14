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

## Importing an MCA Selector chunk export

MCA Selector exports selected chunks rather than a full standalone world. The export does not need `level.dat` for a dimension import and does not move the build to new coordinates.

Stop the server and back up the destination world. Put the exported `region`, `entities` and `poi` folders (where present) directly inside:

```text
<level-name>/dimensions/ffa/spawn/
```

For the usual `level-name=world`, the paths are `world/dimensions/ffa/spawn/region/`, `entities/`, and `poi/`. Do not nest the enclosing export folder under `spawn`, copy the source world's `level.dat`/`playerdata`, or bulk-replace the dimension's existing `data` folder. Keep the destination's existing `data` folder. Source maps and other saved-data objects can need separate migration; chunk import alone does not transfer every world-global feature.

Restart after copying. Use the imported build's **original coordinates**. For initial construction as an operator in Creative mode, `/execute in ffa:spawn run forceload add X Z` loads the chunk containing those block coordinates; then `/execute in ffa:spawn run tp @s X Y Z` takes you there. Once standing on a safe floor, `/ffa spawn setpoint` sets the arrival point. Release the temporary load with `/execute in ffa:spawn run forceload remove X Z`.

`forceload add` never replaces an existing chunk. It generates a chunk only if none exists; in this dimension, missing chunks use the empty generator. `forceload remove` releases the load without deleting anything. If version 0.5.0–0.6.0 reports a generic denial followed by success, see [the teleport fix](Troubleshooting.md#op-cannot-teleport-into-the-spawn-dimension).
