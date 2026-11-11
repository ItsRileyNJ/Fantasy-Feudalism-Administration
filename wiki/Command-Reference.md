# Complete command reference

[Wiki home](Home.md) · [First setup](Getting-Started.md)

This reference covers **0.1.2**. Commands are lowercase, under `/ffa`, and accept Tab completion. `<value>` means replace the placeholder; `[value]` means optional. Do not type the brackets. Names used for players are online player names, not arbitrary selectors.

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
/ffa region create <name> [override]
/ffa region standalone <name> [override]
/ffa region delete <name>
/ffa region visualize <name>
/ffa region expand <name> <blocks>
/ffa region contract <name> <blocks>
/ffa region flag <name> <flag> <true|false|inherit>
/ffa region message <name> <entry|exit> "<message>"
/ffa region interaction <name> <block_id> <allow|remove>
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

Disabled by default through the corresponding modules. Privacy commands require the native request-privacy mode, not an unverified FTB Essentials bridge.

## Economy and shops

```text
/ffa balance
/ffa shop list
/ffa shop buy <name> <quantity>
/ffa shop sell <name> <quantity>
/ffa shop set <name> <item_id> <buy_price> <sell_price>
/ffa shop delete <name>
/ffa economy set <online_player> <whole_balance>
/ffa pay <online_player> <whole_amount>
/ffa baltop
```

Shop quantities: 1..2304, subject to actual funds/items/space. Prices: -1 disables a direction; 0 is free/no payout; otherwise nonnegative whole Denar within limits. When buying is enabled, sell cannot exceed buy. `economy set` replaces a total. `pay` and `baltop` default to disabled. [Economy walkthrough](Economy-and-Shops.md).

## Reload, snapshots, and console use

```text
/ffa reload
/ffa backup
```

These require their [management nodes](Permissions-and-Access.md). Reload cannot mutate dimension registration or the documented restart-only modules, and waits for pending jobs/trades to finish. Backups have [inventory/rollback restrictions](Backups-and-Recovery.md).

Most operations require an in-game player, including edits that use player location or private management context. Console-supported operations include bare help, `/ffa help`, `/ffa region list`, `/ffa delwarp NAME`, `/ffa reload`, and `/ffa backup`. Other mods' and vanilla commands have their own console rules.

There is no `/ffa economy give`, `/ffa portal resize`, `/ffa region select` wand, built-in schematic-import command, or admin command to inspect/delete another player's homes in this release. Use the documented supported actions instead.
