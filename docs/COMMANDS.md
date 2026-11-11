# Commands

[Complete mod wiki](../wiki/Home.md)

Type `/ffa ` (including the space), then press **Tab** to browse commands. Continue using Tab for actions, region/warp/portal IDs, your home names, flags, dye colors, item/block IDs and online player names. Management commands are shown only when you have the required permissions, and disabled modules are omitted. `/ffa reload` refreshes the client command list after supported config edits.

All command responses are private. Administration confirmations include time, operation, object, dimension/location and persistence result when verbose feedback is enabled. Failures preserve the previous definition. None of these commands replaces another mod's command root.

Arguments containing spaces, including messages and team coordinates, must be quoted.

## Operators

| Command | Purpose |
| --- | --- |
| `/ffa pos1`, `/ffa pos2` | Select two block positions by standing at each corner; same dimension |
| `/ffa spawn create [override]` | Create the one full-height main spawn; optional explicit settlement overlap override |
| `/ffa spawn setpoint` | Save safe spawn arrival coordinates and orientation |
| `/ffa region create NAME [override]` | Create a child using the selection |
| `/ffa region standalone NAME [override]` | Create an independent region if the module is enabled |
| `/ffa region delete NAME` | Delete; children must be deleted before their parent |
| `/ffa region flag NAME FLAG true\|false\|inherit` | Allow, block or inherit a rule |
| `/ffa region message NAME entry\|exit "message"` | Set a boundary-crossing message; empty quoted text clears it |
| `/ffa region interaction NAME minecraft:oak_door allow\|remove` | Allow a particular block to be operated, without allowing item placement on it |
| `/ffa region access NAME public\|admin\|permission\|group\|team [VALUE]` | Restrict arrival access; membership does not confer town ownership |
| `/ffa region expand NAME BLOCKS` | Expand all horizontal edges |
| `/ffa region contract NAME BLOCKS` | Contract all horizontal edges; invalid results are rejected |
| `/ffa region visualize NAME` | Show the boundary with one private particle burst near your current height |
| `/ffa region list` | List IDs, types and boundaries |
| `/ffa setwarp NAME` | Create/update a warp at your location |
| `/ffa delwarp NAME` | Delete an unreferenced warp; remove portal references first |
| `/ffa warp NAME public\|admin\|permission\|group\|team [VALUE]` | Set warp access |
| `/ffa portal create NAME WARP` | Use the current two-corner selection as the portal volume |
| `/ffa portal delete NAME` | Remove portal definition and its loaded visual markers |
| `/ffa portal link NAME WARP` | Change a portal's linked warp |
| `/ffa portal color NAME COLOR` | One of the 16 vanilla dye color names, e.g. `light_blue` |
| `/ffa portal access NAME public\|admin\|permission\|group\|team [VALUE]` | Set portal access |
| `/ffa shop set NAME minecraft:item BUY_PRICE SELL_PRICE` | Create/update an item shop; `-1` disables a direction; sell must not exceed buy |
| `/ffa shop delete NAME` | Delete a shop |
| `/ffa economy set ONLINE_PLAYER WHOLE_AMOUNT` | Set an online player's Denar balance within configured limits |
| `/ffa reload` | Validate and apply reloadable settings and region edits together |
| `/ffa backup` | Snapshot this mod's state and configuration; see recovery restrictions |

Portals use vanilla stained-glass block displays at the selected volume's corners and vanilla interaction entities. They add no registered blocks or client protocol. Walk through the selected volume to travel. Operators can right-click a visible corner with a dye to recolor it without changing its destination. Corner markers have no block collision, so the portal interior can remain open. Colors persist and loaded markers synchronize from the saved definition.

A `team` restriction uses Mercenaries settlement ownership/membership at the target. An optional VALUE is the settlement center in `BlockPos.toShortString()` format, for example `"10, 64, 20"`; an empty value uses the settlement at the target. A `group` VALUE is an FTB rank ID or a LuckPerms `group.ID` permission. A `permission` VALUE is an explicit permission node.

## Players

- `/ffa spawn`, `/ffa warp NAME`, `/ffa warps`
- `/ffa sethome NAME`, `/ffa home NAME`, `/ffa delhome NAME`, `/ffa homes`
- `/ffa tpa PLAYER`, `/ffa tpahere PLAYER`, `/ffa tpaccept`, `/ffa tpdeny`, `/ffa tpcancel`
- `/ffa balance`, `/ffa shop list`, `/ffa shop buy NAME QUANTITY`, `/ffa shop sell NAME QUANTITY`
- When explicitly enabled: `/ffa back`, `/ffa rtp`, `/ffa pay PLAYER AMOUNT`, `/ffa baltop`, `/ffa tptoggle`, `/ffa tpignore ONLINE_PLAYER`

Native home/request/privacy operations decline to run when FTB Essentials is detected. Use FTB's own commands in that case. `/ffa spawn` and `/ffa warp` remain separate.

Region flags, with `true` meaning allowed and `false` meaning blocked/frozen:

```
build break interact containers entity_interact npc_trading settlement_management
hunger damage pvp tnt pearls totems fire mob_spawning hostile_spawning mob_griefing
explosions pistons fluids automation dispensers hanging_damage animal_damage flight
teleport_in teleport_out home_creation
```

Allowing `build` does not remove vanilla Adventure Mode's item placement requirements. Administrators are exempt from Adventure Mode.
