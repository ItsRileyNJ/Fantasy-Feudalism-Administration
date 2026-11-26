# Vanish and inventory editing

[Wiki home](Home.md) · [Whispers and stats](Whispers-and-Stats.md) · [Permissions](Permissions-and-Access.md)

These **0.3.0** tools require an in-game operator at the configured level or full `ffa.admin` authority. They are hidden from ordinary players' command trees and `/ffa help`. Giving a rank nickname permission does not unlock them.

## Vanish

```text
/ffa vanish
```

The command toggles staff concealment. While active:

- Ordinary players do not receive your tab-list profile or tracked player entity. This includes spectator observers and players who join later.
- Vanilla `/list` and `/list uuids`, player selectors, online-name suggestions, FFA player lookups, public stats and player messaging treat you as offline for ordinary viewers.
- Modern server-list status, both legacy ping formats, and GS4 query counts/samples exclude you. Internal server accounting still counts your connection.
- Your join/leave, advancement and supported public player announcements are withheld from ordinary viewers. Turning vanish on sends an apparent departure; turning it off sends an apparent arrival.
- You use Spectator Mode to prevent ordinary interaction, collisions, pickups, targeting and footsteps. FFA retains the prior game mode and restores it when you leave vanish. Change game mode after leaving vanish.
- Public chat and new FFA whisper/TPA requests from you are blocked to avoid announcing your presence. Pending native whisper/TPA requests are cancelled.

Your vanish flag and prior mode survive reconnects/restarts. Revoking administrator rights exits vanish into Survival, after which region rules can apply Adventure Mode. A vanished administrator cannot retain concealment through a lower rank.

Other authorized administrators can see your staff presence in tab lists and command lookups. Console, server logs, audit records, connection limits and internal APIs retain the real session. This is player-facing concealment, not removal of the connected session from the server.

**Compatibility boundary:** other mods can publish their own online-player lists, web maps, presence notifications or direct API lookups. Those need verified adapters. FFA cannot promise concealment from arbitrary mods or server operators. Test the complete server stack before relying on vanish for moderation.

## Inspect and edit a live inventory

```text
/ffa invisee Alice
```

Use the online player's real account name. `invisee` is the command spelling. The native six-row chest screen contains:

| Upper slots | Contents |
| --- | --- |
| 1–36 | Target's main inventory and hotbar, in vanilla inventory index order |
| 37–40 | Target's armor: feet, legs, chest, head |
| 41 | Target's offhand |
| 42–54 | Locked padding |
| Lower inventory | Your own inventory/hotbar |

This is a live editable view. Moving items changes the target's real inventory; the screen is not a copied chest of duplicated items. Native clicks, shift-clicks and swaps use the real slots. Armor slots are administrative inventory slots; the editor can place items directly, so take care when modifying equipped items. The target's ender chest, crafting grid, current cursor and another open container are not included.

You can open/edit this screen while vanished. The narrowly scoped spectator exception applies only to this authorized inspection menu, not normal world containers or other spectator interactions.

You cannot inspect yourself, start overlapping inspection sessions for either participant, or edit a player with an unresolved FFA item transaction. Use your normal inventory for yourself. If the target leaves or your administrator permission is revoked, the screen closes instead of permitting further edits.

If either participant starts an FFA shop item trade, the inspection closes before the transaction journal is prepared. The target receives no inspection-specific notification/error. Reopen the screen afterward if needed. Other mods retain responsibility for their own inventory operations.

Player inventories are saved and read back for verification after edits and on close. A save-verification failure stops the editor and reports the player-data error to the administrator/server log; resolve the storage problem before further edits. Administrative edits follow vanilla cursor/drop behavior and are not the Denar transaction journal or a crash-proof inventory rollback system. Keep normal stopped-server world/player-data backups.

Commands and edit results use private feedback. The FFA audit identifies the editor, target and save result. It does not create a staff-wide chat announcement for each edit.

## God mode and smite

```text
/ffa god
/ffa smite Alice
```

`god` toggles immediate invulnerability for the administrator using it. It survives reconnects and respawns; toggling off restores normal mode-dependent vulnerability. Losing full administrator rights removes FFA god mode. Ordinary administrator protection bypass alone does not grant god mode.

`smite` produces a visual lightning strike on the online target and immediately kills them. The strike avoids collateral lightning fire. It intentionally overrides FFA region damage protection and FFA god mode; another mod can still veto death. Use the real online account name. Both commands are hidden from nonadministrators.

## Immediate administrator teleport

```text
/ffa tp Alice
```

This teleports the administrator using it to the online player's current location, without a TPA request, warmup or ordinary cooldown. It cancels the administrator's pending command teleport. It does not bring the target to the administrator. Existing world/destination-safety checks and other mods' protection decisions still apply. The command is hidden from nonadministrators.

## World borders

Use `/ffa worldborder pos1`, `/ffa worldborder pos2`, then `/ffa worldborder set` for an exact rectangle or square in the current dimension. `/ffa worldborder info` shows it; `/ffa worldborder clear` removes it. These commands require full administrator status and are hidden from normal players. See [world border setup and close-only visibility](World-Borders.md).

## Bring a player to you

```text
/ffa tphere Alice
```

An administrator can immediately bring the named online player to their current location, including across dimensions. This command is hidden from normal players. It bypasses the target's combat travel lock, ordinary teleport cooldown/warmup and region arrival/access restrictions. It cancels the target's pending FFA travel, preserves their combat timer and existing ordinary cooldown, and records the move for `/ffa back`.

Destination bounds, obstruction/hazard checks and pending transaction protection still apply. Release a jailed player before bringing them out of jail. A failed move clears the temporary staff authority and reports an error; it does not grant a lasting bypass.

## Player mannequins and holograms

Use `/ffa mannequin create DISPLAY [PLAYER]` at the intended position to recognize a notable player. `/ffa hologram` is an alias. These persistent visual displays support saved skins, copied equipment and styled names/titles without becoming online players. See the [complete setup and examples](Mannequins-and-Holograms.md).

## Teleporting to unloaded chunks

From **FFA 0.11.1**, administrators can teleport into unloaded chunks without visiting or force-loading them first. This covers FFA homes, warps, spawn, `/back`, portals and direct/vanilla teleports, including cross-dimension commands:

```text
/gamemode creative
/execute in ffa:spawn run tp @s 8.5 80 8.5
```

Enable the spawn dimension and restart before using its dimension ID. FFA loads existing saved chunks normally, preserving their blocks. If a chunk has never existed, Minecraft generates it with that dimension's generator. Missing `ffa:spawn` chunks are empty; FFA does not create a floor or replace an imported building. Creative/flying administrators can enter clear air; otherwise a safe supporting block is still required.

The destination collision footprint, including neighboring chunks at an edge, is loaded before checking the landing. Obstructions, hazardous blocks, invalid coordinates and world borders still reject a teleport. This uses normal chunk loading and does not add persistent `/forceload` entries. Generating new terrain can take a moment, as with vanilla travel.

Administrator status belongs to the **player being teleported**, checked through the configured operator level or `ffa.admin`. An administrator typing a vanilla command targeting an ordinary player does not grant that target administrator privileges. Load the destination by visiting it yourself first if needed. Ordinary FFA player teleports retain the existing bounded saved-full-chunk queue and cannot generate missing terrain. RTP continues searching only already loaded terrain for everyone. Automation fake players do not receive administrator chunk-generation privileges.

If you are still told to have an administrator load the chunk first, confirm the **server** runs FFA 0.11.1 or later and that FFA recognizes you as an administrator. No new setting or separate chunk-loading command is needed.
