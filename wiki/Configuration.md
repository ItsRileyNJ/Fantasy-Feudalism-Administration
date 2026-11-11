# Configuration reference

[Wiki home](Home.md) · [Region editing](Regions.md) · [Flags](Flags-and-Interactions.md)

## Where the files live

| File | Purpose |
| --- | --- |
| `config/ffa/server.toml` | General server rules, modules, limits, integration and logging settings |
| `<world>/ffa/regions.toml` | The one authoritative, human-editable region definition file |
| `<world>/ffa/ranks.json` | Built-in ranks, typed permission nodes, default/inherited membership and UUID assignments; use `/ffa ranks reload` after edits |
| `<world>/ffa/state.json` | Saved FFA points, objects, accounts, personal chat styles, vanish state, whisper approvals/monitor preferences and transaction state, managed through commands |
| `<world>/ffa/backups/` | Administrator-created snapshots |

`<world>` means the active world directory, usually the server's `level-name`, not a literal folder with angle brackets. For IntelliJ's default development run these paths are usually under `<project>/run/`; inspect its working directory if customized. These general settings are not stored in `<world>/serverconfig/ffa/`.

## Edit safely

1. Keep a copy of the file you will change.
2. Edit the **existing** TOML section/key with a plain-text editor. Examples showing one section are excerpts, not instructions to append duplicate section headers.
3. For reloadable settings, run `/ffa reload`. For restart-only settings, stop/restart the server.
4. Read the private result and server log. An invalid live reload keeps the last valid active configuration. Invalid startup settings can refuse startup until corrected.

Reload is refused while teleport jobs or prepared trades are pending. Do not change dimension registration live. In-game region edits refuse to overwrite unreloaded external edits.

The [full default server.toml](examples/server.toml) is extracted from 0.2.0's actual template. Use it for comparison; replacing your existing settings with the whole template resets your custom general rules. Config/state schema versions remain 1. Unknown versions are refused; do not manually increment version fields.

## Every general setting

| Setting | Fresh-install default | Valid value / unit | Apply | Purpose |
| --- | --- | --- | --- | --- |
| `configVersion` | `1` | 1 only | Restart | Initial schema identifier; do not increment it manually. |
| `administration.operatorLevel` | `2` | 2..4 | Reload | Minimum built-in operator level for FFA administration. |
| `administration.verboseFeedback` | `true` | Boolean | Reload | Detailed private management confirmations. |
| `permissions.defaultPlayerCommands` | `true` | Boolean | Reload | Fallback for player command families when no explicit provider decision exists. |
| `dimension.enabled` | `false` | Boolean | Restart | Register the optional ffa:spawn void-dimension datapack. |
| `modules.standaloneRegions` | `false` | Boolean | Reload* | Allow independent regions; existing ones must remain valid when disabling. |
| `modules.economy` | `true` | Boolean | Reload | Enable the native Denar transaction system; retain saved accounts when disabled. |
| `modules.homes` | `true` | Boolean | Restart | Enable native home commands when FTB Essentials is absent. |
| `modules.tpa` | `true` | Boolean | Restart | Enable native teleport requests when FTB Essentials is absent. |
| `modules.portals` | `true` | Boolean | Reload | Enable portal travel and management; stored definitions remain. |
| `modules.back` | `false` | Boolean | Restart | Enable previous-FFA-origin return. |
| `modules.rtp` | `false` | Boolean | Restart | Enable bounded random wilderness teleport. |
| `modules.pay` | `false` | Boolean | Restart | Enable player transfers; economy must also be enabled. |
| `modules.leaderboard` | `false` | Boolean | Restart | Enable stored UUID/balance leaderboard visibility. |
| `modules.privacy` | `false` | Boolean | Restart | Enable native request toggle/ignore controls; overlapping FTB mode disables them. |
| `spawn.firstJoin` | `false` | Boolean | Reload | Redirect genuinely new players to a valid configured FFA spawn. |
| `spawn.respawnPolicy` | `"vanilla"` | vanilla / bed_first / spawn_first | Reload | Death arrival policy; see the respawn guide. |
| `teleport.cooldownSeconds` | `3` | 0..86400 seconds | Reload | Ordinary command teleport cooldown. |
| `teleport.warmupSeconds` | `0` | 0..300 seconds | Reload | Delay before ordinary player travel; zero is immediate. |
| `teleport.cancelOnMovement` | `true` | Boolean | Reload | Cancel warmups/prepared chunk loads when the player moves. |
| `teleport.requestExpirySeconds` | `60` | 1..3600 seconds | Reload | Time before an incoming TPA/first-contact whisper request expires. |
| `teleport.requestCooldownSeconds` | `10` | 0..3600 seconds | Reload | Minimum interval between normal-player TPA or first-contact whisper requests. |
| `teleport.portalCooldownSeconds` | `5` | 1..3600 seconds | Reload | Portal loop/repeat protection, including administrators. |
| `teleport.maximumQueuedLoads` | `2` | 1..16 jobs | Reload | Maximum queued saved-destination loads; at most one completed load is applied per tick. |
| `teleport.loadTimeoutSeconds` | `15` | 1..60 seconds | Reload | Timeout for a queued destination load. |
| `homes.maximum` | `3` | 0..1000 | Reload | Default home count for normal players; existing homes are not deleted. |
| `homes.allowedDimensions` | `["minecraft:overworld"]` | List of dimension IDs | Reload | Dimensions in which native homes can be created; administrators bypass this restriction. |
| `rtp.radius` | `1000` | 1..1000000 blocks | Reload | Maximum X/Z offsets around the current location; search uses a square. |
| `rtp.attempts` | `16` | 1..128 | Reload | Maximum candidate attempts; only loaded terrain is considered. |
| `rtp.allowedDimensions` | `["minecraft:overworld"]` | List of dimension IDs | Reload | Current dimensions in which random teleport is allowed. |
| `economy.currency` | `"Denar"` | Nonblank string, 1..32 characters | Reload | Displayed virtual-currency name; amounts remain whole numbers. |
| `economy.startingBalance` | `0` | 0..maximumBalance | Reload | Initial funds for newly created FFA accounts, not a retroactive grant. |
| `economy.maximumBalance` | `9000000000000000` | 1..9000000000000000 | Reload | Balance/credit limit; existing balances are not automatically rewritten. |
| `economy.maximumTransfer` | `1000000` | 1..maximumBalance | Reload | Maximum amount in one player transfer. |
| `compatibility.mercenaries` | `true` | Boolean | Reload* | Enable the supported settlement adapter; disabling it is not an overlap-validation bypass. |
| `compatibility.easyNpc` | `true` | Boolean | Reload | Enable the Easy NPC entity exception/detection path. |
| `compatibility.luckPerms` | `true` | Boolean | Reload | Allow optional LuckPerms permission decisions. |
| `compatibility.ftbRanks` | `true` | Boolean | Reload | Allow optional FTB Ranks permission/limit decisions. |
| `logging.audit` | `true` | Boolean | Reload | Record administrative edits and financial actions. |
| `logging.security` | `false` | Boolean | Reload | Record supported suspicious/rejected activity with rate limiting. |
| `logging.retentionFiles` | `8` | 1..100 archives | Reload | Bounded numbered log archives, in addition to the current file. |
| `logging.maximumFileBytes` | `1048576` | 4096..16777216 bytes | Reload | Log rotation threshold; 1048576 is 1 MiB. |
| `messages.denied` | `"You cannot do that here."` | String | Reload | Generic region-protection refusal. |
| `messages.portalDenied` | `"You cannot use this portal."` | String | Reload | Portal-access refusal message. |

`Reload*` can be refused when the resulting existing region definitions would be invalid. Remove/repair affected definitions first rather than forcing an inconsistent state.

Every key under `[defaults]` is listed with its exact initial value in [Flags and interactions](Flags-and-Interactions.md). All are Boolean and reloadable through the same validation process. They are baseline values for protected regions and the enabled spawn dimension; they do not impose protection everywhere in an ordinary unprotected world.

## Region file fields

Create the initial region in-game, then edit its generated entry. The [complete example region file](examples/regions.toml) demonstrates a parent, market and arena. It is a reference, not an automatic world installer; use your own existing dimension, coordinates and settlement decisions. It does not set the separate spawn teleport point.

| Field | How to edit it |
| --- | --- |
| `id` | Unique 1..48 lowercase letters/digits/underscores/hyphens; update child parent references if a parent ID changes |
| `name` | Human-readable nonblank display name, separate from the ID |
| `type` | `SPAWN`, `CHILD`, or enabled `STANDALONE` |
| `dimension` | An already registered dimension ID; children must match the parent |
| `parent` | Main spawn ID for a child; empty string for spawn/standalone |
| `minX`, `maxX`, `minZ`, `maxZ` | Inclusive horizontal block bounds, each -29999984..29999984; minimum must not exceed maximum |
| `priority` | -10000..10000; does not legalize overlap. Children take precedence over their parent; leave 0 unless deliberately organizing metadata |
| `settlementOverride` | Explicitly accept overlap with a Mercenaries claimed settlement |
| `entry`, `exit` | Quoted messages; empty strings disable them |
| `interactions` | List of allowed block IDs for that region, e.g. `["minecraft:oak_door"]` |
| `[regions.access]` | `mode` and `value` for public/admin/permission/group/team arrival access |
| `[regions.flags]` | Explicit Boolean overrides; remove a key to inherit the applicable baseline/parent |

Boundaries, names, relationships and other fields can be applied by a valid reload after creation. A new dimension must already exist following any required restart. There is no need to manipulate NBT or Java code for ordinary region edits.

Do not live-edit `state.json` to change balances or portals. Use the [commands](Command-Reference.md) for these world-specific objects and follow [recovery instructions](Backups-and-Recovery.md) before any offline restoration.
