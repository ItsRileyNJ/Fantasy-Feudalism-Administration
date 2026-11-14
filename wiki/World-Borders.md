# World borders

[Wiki home](Home.md) · [Regions](Regions.md) · [Configuration](Configuration.md)

FFA world borders are permanent rectangular or square map boundaries. They use the same two-corner selection as regions, but they do not apply region protection flags or change settlement ownership. They cover the full world height and apply **only to the selected dimension**. An Overworld border leaves the Nether, End and other dimensions unchanged.

## Create a border

As an administrator:

1. Stand at the first corner and run `/ffa worldborder pos1` (or `/ffa pos1`).
2. Stand at the opposite corner and run `/ffa worldborder pos2` (or `/ffa pos2`).
3. Run `/ffa worldborder set`.

Both corner blocks are included. Height is ignored. Corners can be selected in either order, and the two side lengths can differ. There is no expansion into a square. Selections reset when switching dimensions; select both corners in the dimension you want to limit.

`/ffa worldborder create` is an alias for `set`. Running either again replaces this dimension's FFA border. There is one FFA border per dimension, with no naming step. Feedback simply says **World border set.**

## Inspect or remove

| Command | Behavior |
| --- | --- |
| `/ffa worldborder` or `/ffa worldborder info` | Show this dimension's inclusive X/Z coordinates and width × depth |
| `/ffa worldborder clear` | Remove this dimension's FFA boundary and restore its underlying vanilla border |
| `/ffa worldborder remove` | Alias for clear |

These commands require administrator status and are hidden from normal players' command suggestions. They are not unlocked by ordinary rank command grants. Run them in game in the target dimension.

## A wall that appears only nearby

The standard blue Minecraft border wall appears only when a player is within **8 blocks** of an edge. Farther away, the wall is hidden. No distant red warning vignette is added. The physical boundary stays active at every distance.

Change the distance in `config/ffa/server.toml`:

```toml
[worldBorder]
visibilityDistance = 8
```

Edit the existing section if it is present; add this section once if upgrading from an older config. Valid values are whole numbers from **1 to 64**. Run `/ffa reload`; players' views update on the next server tick. Older configs default to 8 without rewriting the file.

Players need neither the FFA client mod nor an extra resource pack for this wall. FFA uses standard Minecraft border packets to display the nearest nearby X edge and Z edge. At a corner both appear. In very narrow rectangles, the closest of two opposite edges is displayed; collision still enforces all four sides. The wall appears at the configured distance using vanilla's animation and opacity.

## How it interacts with the world

The rectangle supplies the dimension's actual border bounds and collision shape. Ordinary movement and flight collide with it, and FFA travel validation rejects outside destinations. Vanilla border-aware portal placement and coordinate clamping read these same bounds. It applies to administrators in normal play too; Spectator mode retains Minecraft's ability to cross physical borders.

This creates a boundary, not terrain. It neither deletes nor regenerates chunks. Setting a smaller border can leave existing players, towns, homes, jail cells or spawn points outside; choose its corners to include the places that must remain accessible. Players outside can move back inside. Outside-border damage still follows vanilla behavior and any active region protection, god mode or spawn invincibility.

The border is stored in `<world>/ffa/state.json` and is reapplied on restart. Existing worlds start without an FFA border until you create one. FFA preserves the underlying vanilla border settings separately, including for world saves. Use `/ffa worldborder clear` before switching back to vanilla `/worldborder` management. Vanilla's single size value does not describe an FFA rectangle; use `/ffa worldborder info`.

The border does not replace Mercenaries settlements, region flags, economy or ranks. Those systems continue to work inside it.
