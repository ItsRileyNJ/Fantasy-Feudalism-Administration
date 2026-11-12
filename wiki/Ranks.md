# Built-in ranks

[Wiki home](Home.md) · [Permission nodes](Permissions-and-Access.md) · [Command reference](Command-Reference.md)

Available in **0.3.0**. FFA includes its own server-side ranks manager; players need no additional client mod. Use `/ffa ranks` and Tab. FTB Ranks keeps `/ftbranks` and its own data when installed.

## Create and assign your first rank

Run these as an operator or from the server console. Replace `Alice` with an online player name:

```text
/ffa ranks create traders 10
/ffa ranks node add traders ffa.portal.market true
/ffa ranks node add traders ffa.homes.maximum 5
/ffa ranks add Alice traders
/ffa ranks player Alice
/ffa ranks check Alice ffa.portal.market
```

To connect that permission to an existing portal, run as an in-game portal administrator:

```text
/ffa portal access market_gate permission ffa.portal.market
```

Alternatively, use membership directly:

```text
/ffa portal access market_gate group traders
```

Rank changes persist immediately to `<world>/ffa/ranks.json`. They refresh online players' command packets and FFA protection state, so permission changes do not require reconnecting. Changes to a player's own permissions can hide commands immediately.

## Delegate region management

```text
/ffa ranks create builders 20
/ffa ranks node add builders ffa.region.edit true
/ffa ranks add Alice builders
```

This permits region editing without making Alice a full FFA administrator. Protection still applies to Alice. For management operations behind public command families, also grant that family's permission if the server disables default player commands. For example, shop managers need both `ffa.shop.manage` and `ffa.command.shop` in that configuration.

`ffa.ranks.manage` delegates the entire ranks manager, including creating administrator ranks and assigning them. Treat it as full permission-administration authority. `ffa.admin` also includes rank management. Operators retain their normal authority and FFA gameplay exemption regardless of a local deny.

## Default ranks and inheritance

FFA creates one empty **member** rank with power **0**, automatically active for every player. It grants no new permission nodes. Normal FFA public commands still follow `permissions.defaultPlayerCommands`. There is no automatic privileged rank or automatic import from FTB Ranks.

```text
/ffa ranks create resident 5
/ffa ranks default resident true
/ffa ranks parent add traders resident
/ffa ranks parent remove traders resident
/ffa ranks default resident false
/ffa ranks power traders 30
```

A default rank applies to all players. Setting it to false stops automatic membership but keeps explicit assignments and inheritance. Removing a player's explicit assignment also leaves default/inherited membership intact. Multiple default ranks and multiple assigned ranks are supported.

An active rank activates its parents recursively. Parent membership counts for group access too. Cycles, missing parents, and inheritance paths longer than 32 edges are rejected without saving. Before deleting a parent, remove it from child ranks. Deleting an otherwise valid rank removes its direct player assignments.

## Which permission wins

1. For Boolean checks, an explicit **FTB Ranks** decision wins, then **LuckPerms**, then the built-in ranks. Boolean provider failures are treated as denies rather than bypassed with local permission grants. FTB numeric home limits take precedence over local numeric limits; LuckPerms numeric metadata is not read.
2. Within built-in ranks, active ranks are checked by **higher power first**. Equal power resolves by rank ID in alphabetical order. This ordering includes inherited ranks; give children higher power when their overrides should win.
3. Within one rank, an exact node wins. Otherwise the closest matching trailing wildcard wins. For `command.…` nodes, an ancestor command node also covers its descendants.
4. If no Boolean decision exists, the caller's normal default applies. FFA player commands use their configured fallback; other commands use their original permission predicate. Numeric/string values are not coerced into Boolean grants.

The first matching rank value wins, even if another rank has a more specific node. A higher-power broad grant can therefore override a lower-power specific deny. Put exceptions in that same rank or a higher-power rank.

Example: grant ordinary command use but refuse economy transfers in the same rank:

```text
/ffa ranks node add traders "ffa.command.*" true
/ffa ranks node add traders ffa.command.pay false
```

Quote wildcard nodes in commands. Only `*` and a trailing `.*` are supported; patterns in the middle of a node are rejected. Chat style metadata uses exact nodes rather than permission wildcards. Removing a node restores inheritance/fallback; setting it to false creates an explicit deny.

## Control vanilla and other mods' commands

Permissions use the registered **Brigadier node path**, prefixed with `command.`. Local paths use lowercase and include literal subcommands and argument **names**, not actual player names or coordinates. The bridge preserves original argument-name case when querying external FTB permissions. Use Tab to discover registered paths, or export them:

```text
/ffa ranks refresh_readme
```

Read `<world>/ffa/ranks-readme.txt`. It is a generated reference for the currently installed server command tree. Regenerate it after changing the mod set.

For example:

```text
/ffa ranks create timekeepers 10
/ffa ranks node add timekeepers command.time true
/ffa ranks node add timekeepers command.time.set false
/ffa ranks add Alice timekeepers
```

Alice can reach permitted `time` subcommands but not `time set`. A denied parent blocks its whole branch: allowing a child alone does not open a denied parent. A child can override an ancestor grant after the parent has admitted the player. Redirect aliases use their canonical target's node; for example, denying `command.msg` also applies to vanilla `/w`.

Explicit grants replace the command node's normal permission predicate. A command's own execution-time restrictions, argument rules and validation still apply. FFA's own `/ffa` tree uses the documented `ffa.…` permissions; `command.ffa.…` does not replace those checks. Console and fake-player sources retain their original predicates. Local rank decisions do not remove operator authority.

A server restart indexes the command tree after normal registration. A normal vanilla datapack `/reload` reindexes replacement command trees automatically. Mods that add entirely new nodes outside that lifecycle need a restart or a specific adapter. A direct mod API call is not a Brigadier command and is not covered by `command.…` permissions. This is not a replacement for another mod's permission API or a promise that every operator-only handler can be delegated.

## Inspect, revoke, and use familiar FTB-style names

```text
/ffa ranks list
/ffa ranks info traders
/ffa ranks node list traders
/ffa ranks node get traders ffa.homes.maximum
/ffa ranks check Alice ffa.homes.maximum
/ffa ranks node remove traders ffa.portal.market
/ffa ranks remove Alice traders
/ffa ranks list_players_with traders
```

`player` shows direct and active local memberships. `check` shows the local stored effective value and, for online players, the combined Boolean decision; the home-limit node also shows the combined numeric home limit. Unset is distinct from false. The Boolean report does not include the caller's final operator/default fallback. `node get` reads that rank's own value, without resolving inheritance.

Familiar command aliases are included:

| FFA command | Equivalent |
| --- | --- |
| `/ffa ranks list_all_ranks` | `/ffa ranks list` |
| `/ffa ranks list_ranks_of Alice` | `/ffa ranks player Alice` |
| `/ffa ranks show_rank traders` | `/ffa ranks info traders` |

`list_players_with` reports direct assigned UUIDs, active online player names, and whether the rank is automatic. It does not fabricate a list of every offline player who might qualify for an automatic rank. `create NAME` defaults to power 1; supply a power explicitly for predictable ordering.

Online names are resolved to UUIDs. For an offline player, supply their full lowercase UUID in any player argument. Names/selectors are not looked up over the network, and `@a` is not supported. Membership survives a player-name change.

## Edit the file, reload, and recover

See [the complete JSON example](examples/ranks.json). It contains member, trader and noble ranks plus a sample UUID assignment; replace/remove that sample UUID before using the file. Fields are strict: version, ranks, players; each rank has power, defaultRank, parents and permissions. Unknown/missing fields, nulls, invalid references and malformed values reject the entire file.

After an external edit:

```text
/ffa ranks reload
```

General `/ffa reload` continues to reload server settings/regions; it does not reload ranks. A failed ranks reload retains the last valid active ranks. Commands refuse to overwrite external file edits until a successful ranks reload. A rank-save I/O failure blocks further rank edits until successful recovery/reload; correct the disk/permission issue first. A bad file at startup refuses startup, preserving its contents for repair.

`/ffa backup` now includes `ranks.json`. To restore permissions, stop the server, retain the current file, restore the chosen ranks file and restart. Restoring ranks does not require restoring balances. Full economy rollbacks still need matching player inventories as described in [recovery](Backups-and-Recovery.md).

Limits: 256 ranks, 4096 nodes per rank, 32 direct parents per rank, powers -1000000..1000000, 100000 explicit UUID records. Permission node names use lowercase letters/numbers, underscores, hyphens and colons separated by dots, up to 256 characters. Rank IDs use 1..48 lowercase letters, digits, underscores or hyphens.

Values can be Boolean, signed 32-bit whole integers, or strings up to 512 characters. `ffa.homes.maximum` specifically requires 0..1000; FTB Essentials still owns its own homes when installed. Strings can supply the [built-in chat style metadata](Chat-and-Nicknames.md), including titles and colors. FFA does not implement timed ranks, FTB condition expressions or automatic migration of another mod's rank files. Built-in ranks use their own JSON format rather than FTB's SNBT.

## What a node means, and which ones you need

A **rank** is a named collection of settings. A **node** is one setting's exact name. You do not need to configure every node offered by Tab. Most servers can start with the empty default `member` rank and add a few privileges to higher ranks.

| Node/value | Effect | Example |
| --- | --- | --- |
| Permission + `true` | Allows an action | `ffa.chat.nickname true` lets a player set their own nickname |
| Permission + `false` | Explicitly refuses an action | `ffa.command.pay false` blocks transfers |
| Limit + whole number | Sets a numeric limit | `ffa.homes.maximum 5` allows five homes |
| Style metadata + text | Supplies the rank's default appearance | `ffa.chat.style.title "Noble"` supplies a title |
| `command.…` + Boolean | Allows/refuses another registered command branch | `command.time true` grants access to `/time` |

Permission to edit appearance and a default appearance are separate. Giving `ffa.chat.nickname true` does not choose a nickname; giving style metadata does not let members edit it. See the exact style nodes in [chat styling](Chat-and-Nicknames.md).

A practical nonadministrator noble rank:

```text
/ffa ranks create noble 20
/ffa ranks node add noble ffa.homes.maximum 5
/ffa ranks node add noble ffa.chat.nickname true
/ffa ranks node add noble ffa.chat.style.title "Noble"
/ffa ranks node add noble ffa.chat.style.title_color gold
/ffa ranks add Alice noble
```

Alice can then run `/ffa chat nickname "Lady Alice"`. Giving this rank `ffa.admin` would also exempt its members from region restrictions and unlock full staff tools, so only add that node to your actual administrator rank. Region editors use `ffa.region.edit`; that grants editing without the gameplay exemption.

To find a short explanation in game:

```text
/ffa ranks nodes player
/ffa ranks nodes staff 1
/ffa ranks nodes chat
/ffa ranks nodes style
/ffa ranks nodes limits
/ffa ranks nodes commands
```

These lists show six nodes per page and describe the accepted value. Tab begins with common FFA nodes and nodes you have already saved; hover suggestions for their meanings. To explore the many vanilla/mod command nodes, begin typing `command.` in the permission argument. `/ffa ranks refresh_readme` writes the complete installed command tree to a file instead of flooding chat.

Use `/ffa ranks node remove noble NODE` to undo a setting and return to inheritance/fallback. Use `false` only when you intend an explicit denial. Higher power wins between active ranks; inheritance makes another rank active rather than copying its settings. Existing explicit FTB Ranks decisions still take precedence, so inspect those too if a local change appears ineffective.
