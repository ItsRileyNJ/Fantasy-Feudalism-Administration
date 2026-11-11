# Warps and portals

[Wiki home](Home.md) · [Access rules](Permissions-and-Access.md)

A **warp** is a saved arrival point. A **portal** is a physical trigger volume linked to a warp by name. Updating a warp updates where linked portals lead.

## Create and use a warp

Stand on a safe full-block floor at the destination, facing the intended direction:

```text
/ffa setwarp marketplace
/ffa warp marketplace
/ffa warps
```

`setwarp` also updates an existing warp of that name, retaining its access rule. Travel can cross dimensions. Normal teleport timing, permissions and destination safety still apply.

To remove a warp:

```text
/ffa delwarp marketplace
```

Delete or relink portals using it first; FFA refuses to leave those references dangling. Removing a warp does not remove a region or building.

## Build a portal

Create the destination warp first. Choose a walkable entrance volume away from its arrival point. Operators select a **3D cuboid** with the same two position commands used for regions; unlike regions, portal Y coordinates matter.

Example entrance inside the sample spawn: first corner (-30, 65, 0), second corner (-28, 67, 0). Stand or hover at the first corner and `/ffa pos1`; move to the second and run:

```text
/ffa pos2
/ffa portal create market_gate marketplace
/ffa portal color market_gate light_blue
```

The volume includes both selected corner blocks. This example is 3 × 3 × 1 = 9 blocks. Portal volumes must contain 1..4096 blocks, stay within build height, and cannot overlap another portal.

FFA shows non-solid, vanilla stained-glass displays at the corners with interaction targets. It does not generate a building, fill the whole entrance with portal blocks, or require an FFA client renderer. You can build your own frame. Players activate the portal by moving into its configured volume. Select the height where their feet will travel, not only the arch above their heads.

## Recolor or relink

An authorized administrator can right-click a colored corner with any vanilla dye, or use:

```text
/ffa portal color market_gate red
/ffa portal link market_gate town_hall
```

Create the `town_hall` warp before linking it. Recoloring keeps the destination, and linking keeps the color. Both are saved through restarts.

All color names:

```text
white orange magenta light_blue yellow lime pink gray
light_gray cyan purple blue brown green red black
```

## Restrict a portal or warp

```text
/ffa portal access market_gate public
/ffa portal access market_gate admin
/ffa portal access market_gate permission ffa.portal.market
/ffa portal access market_gate group traders
/ffa warp marketplace permission ffa.warp.market
```

Grant the named permission/rank through [the installed permission provider](Permissions-and-Access.md). Travel must pass **both portal and warp access**, plus applicable region and safety rules. A denied player stays in place and receives a cooldown-limited notice.

`team` access uses Mercenaries membership at the portal's location for a portal check, and at the destination for a warp check. It needs the supported integration and a relevant settlement there. See [team examples](Permissions-and-Access.md).

## Timing and maintenance

Default command teleport cooldown: **3 seconds**. Default portal cooldown: **5 seconds**. Administrators bypass ordinary command timing, but portal loop protection remains. Keep arrival points outside other portal trigger volumes to avoid unwanted return travel.

```text
/ffa portal delete market_gate
```

Deleting a portal removes its definition and loaded visual markers, not its destination warp or world frame. FFA has no portal-resize command in 0.1.2: delete/recreate the trigger to change its geometry.

`modules.portals = false` disables travel and portal management. Saved definitions remain; existing visual markers can remain visible. Re-enable the module to resume using them, or delete unwanted portals while it is enabled.
