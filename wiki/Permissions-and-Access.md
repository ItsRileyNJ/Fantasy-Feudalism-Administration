# Permissions, ranks, and access rules

[Wiki home](Home.md) · [Compatibility](Mod-Compatibility.md)

## Administrator fallback

Operators at `administration.operatorLevel` have management rights. The default is **2**, and the setting accepts 2..4. Authorized console commands retain their normal server authority; operations based on your location still require an in-game player.

`ffa.admin` grants the mod's full administrator role through a supported provider. Administrators bypass gameplay restrictions, forced Adventure Mode and normal command travel timing/home limits. It is not an invulnerability grant: an exempt Survival administrator can still face ordinary Minecraft hazards. Inventory/transaction checks, destination safety and portal loop prevention remain.

## Nodes

| Node | Grants |
| --- | --- |
| `ffa.admin` | Full FFA administration and gameplay exemption |
| `ffa.ranks.manage` | Full built-in ranks administration, including privilege grants |
| `ffa.chat.manage` | Assign/edit/reset any player's chat appearance |
| `ffa.chat.nickname`, `ffa.chat.title`, `ffa.chat.color`, `ffa.chat.format` | Separate self-service chat editing rights; see [chat tools](Chat-and-Nicknames.md) |
| `ffa.region.edit` | Region selection, creation, deletion, flags, messages, interactions, access and visualization |
| `ffa.spawn.manage` | Spawn region creation and teleport-point management |
| `ffa.warp.manage` | Warp creation/deletion and access edits |
| `ffa.portal.manage` | Portal creation/deletion, colors, linking and access |
| `ffa.shop.manage` | Shop definitions and prices |
| `ffa.economy.manage` | Set an online player's balance |
| `ffa.reload` | Reload supported configuration and region edits |
| `ffa.backup` | Create an FFA state/configuration snapshot |
| `ffa.command.pvp` | Change/check personal PvP; normal-player five-minute cooldown and combat lock apply |
| `ffa.command.whisper`, `ffa.command.stats` | Public whisper sending/revocation and player stats; full staff tools require operator/`ffa.admin` |
| `ffa.command.<command>` | Allow/deny a player command family, e.g. `ffa.command.spawn` or `ffa.command.shop` |
| `ffa.homes.maximum` | FTB Ranks or built-in numeric home limit, 0..1000 |
| `ffa.homes.unlimited` | Bypass the normal home count limit |

`permissions.defaultPlayerCommands = true` is the player-command fallback. An explicit provider decision overrides that fallback; operators/full FFA administrators retain their exemption. If you set the fallback to false, grant the desired command-family nodes.

For delegated staff who are not full administrators, spawn management, warp-access edits and shop management also pass their public command-family checks. Grant `ffa.command.spawn`, `ffa.command.warp`, or `ffa.command.shop` as applicable alongside the management node. There are no separate per-flag editing permissions in 0.3.0.

FTB Ranks explicit Boolean decisions take precedence over LuckPerms, followed by FFA's built-in ranks. The [built-in ranks guide](Ranks.md) covers creation, assignment, default ranks, inheritance, priority, wildcards and explicit command permissions. A fresh install creates only an empty default member rank. Use `/ffa ranks` for local ranks, `/ftbranks` for external FTB ranks.

## FTB Ranks example

These command forms were checked against the official **2101.1.5** source. Replace names with your ranks/players and run with that mod's operator authority:

```text
/ftbranks create traders 10
/ftbranks node add traders ffa.portal.market true
/ftbranks node add traders ffa.homes.maximum 5
/ftbranks add Alice traders
/ffa portal access market_gate permission ffa.portal.market
```

The server rank file is normally `<world>/serverconfig/ftbranks/ranks.snbt`. If you edit it directly, use FTB Ranks' own `/ftbranks reload`; use FFA's `/ffa reload` to refresh its command list when needed. The exact FTB Ranks binary/server rank rules still need staging verification.

LuckPerms supports the Boolean permission nodes and group checks. This release does not read a LuckPerms numeric home-limit metadata field; use the global home limit or the supported unlimited permission instead.

## Access modes

Apply an access rule to a portal, warp, or region:

```text
/ffa portal access market_gate public
/ffa warp marketplace admin
/ffa region access marketplace permission ffa.market.enter
```

| Mode | Meaning |
| --- | --- |
| `public` | No extra object-specific access restriction |
| `admin` | FFA administrators only |
| `permission NODE` | Require a Boolean permission from the supported provider |
| `group ID` | Match an active FFA/FTB rank ID or the supported `group.ID` permission fallback |
| `team [CENTER]` | Require Mercenaries settlement owner/member status at the checked location |

These rules do not grant building rights, change town membership, or create a wall that prevents walking into a region. Region access restricts relevant teleport arrival; protection flags still decide actions inside it.

Portal team checks use the portal location. Warp/region arrival checks use the destination. To select a particular settlement center at that location:

```text
/ffa portal access town_gate team "10, 64, 20"
```

Use the real Mercenaries center coordinates, not arbitrary portal coordinates. `team` does not mean a vanilla scoreboard team or FTB Teams group. Missing/unavailable integrations fail closed for restricted access; administrators still have their explicit bypass.

Balance send/request require both `ffa.command.balance` and `ffa.command.pay` for nonadministrators. Guided portal destination setting requires both `ffa.portal.manage` and `ffa.warp.manage`; administrator-only creation remains authoritative. `/ffa tp` requires full administrator authority.

World-border management (`/ffa worldborder`) requires full administrator status and stays hidden from ordinary players. Region-editor grants do not unlock it. Physical world borders follow vanilla behavior: administrators in normal play still collide with them; Spectator mode can cross. See [world borders](World-Borders.md).
