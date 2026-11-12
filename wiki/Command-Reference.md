# Complete command reference

[Wiki home](Home.md) · [First setup](Getting-Started.md)

This reference covers **0.4.0**. Commands are lowercase, under `/ffa`, and accept Tab completion. `<value>` means replace the placeholder; `[value]` means optional. Do not type the brackets. Most player arguments use online player names. Rank/chat management also accepts full lowercase offline UUIDs. Arbitrary selectors are not supported.

Examples containing a message or a team center must quote strings with spaces. Administrative changes must validate and persist before they report success; responses are private to the initiating source.

## Discovery and spawn

```text
/ffa
/ffa help
/ffa spawn
/ffa pos1
/ffa pos2
/ffa spawn create [override]
/ffa spawn setpoint
```

The first two commands show help. `spawn` teleports players. The remaining selection/creation/setpoint operations require management authority and an in-game player.

## Regions

```text
/ffa region list
/ffa region create child <name> [override]
/ffa region create standalone <name> [override]
/ffa region create <name> [override]
/ffa region child <name> [override]
/ffa region standalone <name> [override]
/ffa region delete <name>
/ffa region visualize <name>
/ffa region visualize cancel
/ffa region expand <name> <blocks>
/ffa region contract <name> <blocks>
/ffa region flag <name> <flag> <true|false|inherit>
/ffa region message <name> <entry|exit> "<message>"
/ffa region interaction <name> <block_id> <allow|remove>
/ffa region item <name> <item_id> <allow|remove>
/ffa region title <name> <entry|exit> color <color>
/ffa region title <name> <entry|exit> <bold|italic|underline> <true|false>
/ffa region access <name> <mode> [value]
```

All region operations require `ffa.region.edit` or the administrator fallback. Standalone creation additionally needs its module. Expansion/contraction accepts 1..100000 blocks and changes all four edges. See [regions](Regions.md), [all flags](Flags-and-Interactions.md), and [access modes](Permissions-and-Access.md).

## Warps and portals

```text
/ffa setwarp <name>
/ffa delwarp <name>
/ffa warp <name>
/ffa warps
/ffa warp <name> <mode> [value]
/ffa portal create <name> <warp>
/ffa portal delete <name>
/ffa portal link <name> <warp>
/ffa portal color <name> <color>
/ffa portal access <name> <mode> [value]
```

`warp NAME` travels; supplying an access mode edits that warp instead and requires management authority. Modes are `public`, `admin`, `permission NODE`, `group ID`, and `team ["X, Y, Z"]`. Use a supported integration for the restricted modes. Portal management needs its module and the appropriate [permission](Permissions-and-Access.md). Details: [warps and portals](Warps-and-Portals.md).

## Homes and teleport requests

```text
/ffa sethome <name>
/ffa home <name>
/ffa delhome <name>
/ffa homes
/ffa tpa <player>
/ffa tpahere <player>
/ffa tpaccept
/ffa tpdeny
/ffa tpcancel
```

Native home/request modules must be enabled and FTB Essentials absent. Clickable request messages include their own security token automatically; players do not need to type that internal argument. [Player guide](Player-Guide.md).

## Optional utilities and privacy

```text
/ffa back
/ffa rtp
/ffa tptoggle
/ffa tpignore <player>
```

`back` is available by default and returns to the latest death or successful teleport origin. The other utilities are disabled by default through their corresponding modules. Privacy commands require the native request-privacy mode, not an unverified FTB Essentials bridge.

## Economy and shops

```text
/ffa balance
/ffa shop list
/ffa shop buy <name> <quantity>
/ffa shop sell <name> <quantity>
/ffa shop set <name> <item_id> <buy_price> <sell_price>
/ffa shop delete <name>
/ffa shop sign bind <name> [quantity]
/ffa shop sign remove
/ffa balance set <online_player> <whole_balance>
/ffa pay <online_player> <whole_amount>
/ffa baltop
```

Shop quantities: 1..2304, subject to actual funds/items/space. Prices: -1 disables a direction; 0 is free/no payout; otherwise nonnegative whole Denar within limits. When buying is enabled, sell cannot exceed buy. `balance set` replaces a total. `pay` and `baltop` default to disabled. [Economy walkthrough](Economy-and-Shops.md).

## Built-in ranks and chat

```text
/ffa ranks
/ffa ranks create <rank> [power]
/ffa ranks delete <rank>
/ffa ranks list
/ffa ranks nodes [category] [page]
/ffa ranks info <rank>
/ffa ranks add <player_or_uuid> <rank>
/ffa ranks remove <player_or_uuid> <rank>
/ffa ranks player <player_or_uuid>
/ffa ranks power <rank> <power>
/ffa ranks default <rank> <true|false>
/ffa ranks parent <add|remove> <rank> <parent>
/ffa ranks node add <rank> <permission> <value>
/ffa ranks node remove <rank> <permission>
/ffa ranks node get <rank> <permission>
/ffa ranks node list <rank>
/ffa ranks check <player_or_uuid> <permission>
/ffa ranks list_all_ranks
/ffa ranks list_ranks_of <player_or_uuid>
/ffa ranks show_rank <rank>
/ffa ranks list_players_with <rank>
/ffa ranks refresh_readme
/ffa ranks reload
/ffa chat
/ffa chat nickname "<nickname>"
/ffa chat title "<title>"
/ffa chat color <color>
/ffa chat titlecolor <color>
/ffa chat bold <true|false>
/ffa chat italic <true|false>
/ffa chat underline <true|false>
/ffa chat titlebold <true|false>
/ffa chat titleitalic <true|false>
/ffa chat titleunderline <true|false>
/ffa chat inherit <property>
/ffa chat view
/ffa chat view <player_or_uuid>
/ffa chat set <player_or_uuid> <property> <value>
/ffa chat clear <player_or_uuid> <property>
/ffa chat reset <player_or_uuid>
```

Rank management and administrator chat operations support console use. Self-service chat changes require a player and the corresponding editing permission. See [ranks](Ranks.md) for priorities, inheritance, wildcard quoting, values and aliases, and [chat styling](Chat-and-Nicknames.md) for properties, colors, permissions and full examples. Unauthorized management branches are omitted from the client tree and player help.

## Staff tools, whispers and stats

```text
/ffa vanish
/ffa smite <online_player>
/ffa god
/ffa invisee <online_player>
/ffa whisper <online_player> <message...>
/ffa whisperaccept [request_token]
/ffa whisperdeny [request_token]
/ffa whisperforget <player_or_uuid>
/ffa whispers <on|off>
/ffa stats <online_player>
```

Vanish, inventory editing and whisper monitoring require an in-game full administrator/operator. Stats is public by default and supports console use; whispers and consent actions require a player. See [staff tools](Staff-Tools.md) and [whispers/stats](Whispers-and-Stats.md) for the live inventory layout, vanish boundaries, permanent recipient approval, monitoring and time units.

## Reload, snapshots, and console use

```text
/ffa reload
/ffa backup
```

These require their [management nodes](Permissions-and-Access.md). Reload cannot mutate dimension registration or the documented restart-only modules, and waits for pending jobs/trades to finish. Backups have [inventory/rollback restrictions](Backups-and-Recovery.md).

Most operations require an in-game player, including edits that use player location or private management context. Console-supported operations include bare help, `/ffa help`, `/ffa region list`, `/ffa delwarp NAME`, `/ffa reload`, `/ffa backup`, rank management, administrator chat operations and `/ffa stats PLAYER`. Other mods' and vanilla commands have their own console rules.

There is no `/ffa economy give`, `/ffa portal resize`, `/ffa region select` wand, built-in schematic-import command, or admin command to inspect/delete another player's homes in this release. Use the documented supported actions instead.

## Punishments and jail cells

```text
/ffa punish jail <online_player> [duration] [reason...]
/ffa punish chatban <player_or_uuid> [duration] [reason...]
/ffa punish warpban <player_or_uuid> [duration] [reason...]
/ffa punish clear <jail|chatban|warpban> <player_or_uuid>
/ffa punish status <player_or_uuid>
/ffa jail cell set <name> [override]
/ffa jail cell list
/ffa jail cell delete <name>
```

Full administrator/operator only. Durations accept seconds or a suffix: `30s`, `5m`, `2h`, `1d`, `1w`. Omission uses the configured default. [Punishments and jail](Moderation.md) explains cell selection, offline timers, release and the scope of each ban.
