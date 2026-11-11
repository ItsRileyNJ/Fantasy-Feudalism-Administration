# Configuration and recovery

[Complete mod wiki](../wiki/Home.md)

`config/ffa/server.toml` is the general server configuration. It contains comments, default values and units. Values are validated as a complete candidate. Numeric bounds are enforced with errors naming the setting and allowed range.

| Category | Key settings / allowed range |
| --- | --- |
| administration | operatorLevel 2..4; verboseFeedback true/false |
| permissions | defaultPlayerCommands true/false, overridden by explicit rank decisions |
| dimension | enabled true/false, default false; restart only |
| modules | independently enable standaloneRegions, economy, homes, tpa, portals, back, rtp, pay, leaderboard, privacy |
| spawn | firstJoin false by default; respawnPolicy `vanilla`, `bed_first`, `spawn_first` |
| teleport | cooldown 0..86400 seconds; warmup 0..300; request expiry 1..3600; request cooldown 0..3600; portal cooldown 1..3600; cancelOnMovement; maximumQueuedLoads 1..16; loadTimeoutSeconds 1..60 |
| homes | maximum 0..1000; allowedDimensions list |
| rtp | radius 1..1000000 blocks; attempts 1..128; allowedDimensions list; existing loaded terrain only |
| economy | whole Denar; startingBalance 0..maximumBalance; maximumBalance 1..9000000000000000; maximumTransfer 1..maximumBalance |
| compatibility | Mercenaries, Easy NPCs, LuckPerms, FTB Ranks independently enabled |
| logging | audit/security switches; retentionFiles 1..100 archives; maximumFileBytes 4096..16777216 |
| messages | generic protection denial and portal denial |
| defaults | all documented protection flags; child regions inherit unless overridden |

Changing dimension enablement or command-module enablement (homes, tpa, back, rtp, pay, leaderboard, privacy) requires a full restart. Other general settings and region geometry can be reloaded using `/ffa reload`. Reloads are refused while teleport jobs or prepared trades exist. Reloading dimensions in place is never attempted. Configuration version 1 is the initial schema; unknown versions are refused without rewriting files. Back up before any future schema migration.

## Authoritative region definitions

`<world>/ffa/regions.toml` contains every region immediately after successful creation. It is the sole authoritative region definition: in-game commands write this file, and reload reads it. Every entry has readable ID, display name, kind (`SPAWN`, `CHILD`, `STANDALONE`), dimension, horizontal boundaries, parent, priority, settlement override, messages, interaction allowlist, access rule and flags.

Boundaries are inclusive block coordinates. A region covering X 0..10 includes a player at X 10.9 but excludes X 11.0. Y spans the dimension's full build height. IDs contain 1..48 lowercase letters, digits, underscores or hyphens. Rename display names freely; update all child `parent` references when renaming the main spawn ID. Parent/child dimensions and enclosure must remain valid. Child and standalone overlaps are rejected even when priorities differ.

Edit the file and run `/ffa reload`. An invalid reload retains the last active regions. If the file has external edits not yet reloaded, in-game edits refuse to overwrite them. Resolve the file and reload first. Reloading reevaluates online players' protection state.

World-specific spawn positions, homes, warps, portals, shops, balances and transaction records are stored in `<world>/ffa/state.json` and managed by the documented commands. Do not edit this state while the server runs. Use TOML for general rules/defaults and for all region geometry/attributes.

## Void dimension

Enable `dimension.enabled = true` and restart. The optional built-in datapack creates `ffa:spawn` with a void flat generator: no terrain, structures, automatic platform or buildings. All spawn defaults apply throughout this dimension, even outside a main spawn region; explicit child overrides still apply within their bounds.

Build/import your spawn using compatible world-editing tools or normal administrator building, then set a safe spawn arrival point. Structure/schematic formats and import tools remain the responsibility of those tools. No destructive world-directory importer is included. The mod never replaces a Mercenaries town or its world data.

Disable the dimension only after relocating its spawn/warp/home destinations and moving players out. Preserve a stopped-server world backup before removing any existing dimension/datapack. Already generated dimension data is not deleted by changing this setting.

## Transaction recovery

Balances use signed 64-bit whole numbers constrained to nonnegative configured limits. Purchases/sales prepare a durable journal containing both inventory snapshots and balances, modify the real inventory, save and verify the vanilla player inventory, then commit the balance and remove the journal atomically. No inventory scanning runs in the background.

On reconnect, an interrupted prepared trade is settled once if the saved inventory matches its resulting snapshot, or cancelled if it matches the original snapshot. A mismatch locks login for administrator investigation; it never guesses or issues replacement rewards. Save failure disconnects the affected player before further inventory actions. Do not manually delete pending journals to bypass a lock.

## Snapshots and offline recovery

`/ffa backup` writes a timestamped snapshot of this mod's state, region file and general configuration under `<world>/ffa/backups/`. It refuses to snapshot prepared transactions. These snapshots contain **no world blocks or vanilla player inventories**.

For a configuration/region rollback, stop the server, retain a copy of the current files, restore only `server.toml` and/or `regions.toml` from the selected snapshot, and start the server to validate them. Never overwrite unrelated world files. If validation fails, restore the retained current files.

Do **not** roll back `state.json` balances independently after trades. That could credit currency while retaining purchased items. Economy rollback requires a matching, consistent stopped-server backup of the entire server's player inventory state and this mod's state. Such a full-world backup/restore is an external administrator operation; this mod neither performs it nor claims its own snapshots can replace it. There is deliberately no live balance-rollback command. For individual account corrections, investigate the audit trail and use `/ffa economy set` with the server online.

Keep snapshots until you have verified recovery. Snapshot creation does not automatically prune administrator backups.

For the first build in a completely empty void dimension, an operator can use vanilla commands to load a construction chunk and enter in Creative Mode:

```
/gamemode creative
/execute in ffa:spawn run forceload add 0 0
/execute in ffa:spawn run tp @s 0 80 0
```

Build/import a platform, stand on it, and run `/ffa spawn setpoint`. Then remove the temporary forced load if desired with `/execute in ffa:spawn run forceload remove 0 0`. Future mod teleports can load that already saved full chunk through the bounded load queue.


### Windows saves

Windows' Java NIO provider does not support opening a parent directory as a file channel. Version 0.1.1 omits that unsupported directory-flush step on Windows. The complete temporary file is still flushed before atomic replacement; write and replacement errors still propagate and lock further state changes. Directory flushing remains required on other platforms. The mod does not fall back to a non-atomic move.

Windows rename durability during a system power loss depends on the operating system and filesystem because Java cannot request the equivalent parent-directory flush. Keep the consistent backups described above; the existing power-loss recovery acceptance checks still apply.
