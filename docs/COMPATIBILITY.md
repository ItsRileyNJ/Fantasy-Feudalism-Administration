# Compatibility

[Complete mod wiki](../wiki/Home.md)

## Mercenaries

The supplied `fantasyfeudalism-mercenaries-1.20.4.jar` declares Minecraft **1.21.1**, NeoForge **[21.1.262,21.2)** and mod ID `villagerscontract`. The filename is its mod version, not its target Minecraft version.

The adapter reads Mercenaries' own bell groups, claimed settlement owner and member set. It does not create teams, change town claims, rewrite membership, replace bells, modify town inventories, or register town-management commands. Settlement boundary checks use the mod's 64-block horizontal bell radius, including groups with multiple bells; unclaimed natural villages are not treated as player settlements.

New or edited protection regions overlapping a claimed settlement are rejected unless that region explicitly has `settlementOverride = true` (or the administrator used `override` during creation). With an override, regional protection applies normally. Settlement membership permits the requested team checks; it does not bypass region building or damage rules. The enabled settlement-management flag allows charter/empty-hand bell actions, while Mercenaries itself continues to decide who may manage a town. NPC/settlement residents remain eligible for creation despite the default natural-spawn block.

The adapter is optional and checks class/method availability during startup/reload. If Mercenaries is installed but the adapter is unavailable, settlement overlap checks fail closed; the administrator can repair the integration or explicitly override a particular region. Team access fails closed.

**No Mercenaries patch was required for the tested build.** A small public, versioned API would improve future compatibility:

- Read-only settlement lookup by dimension/position and UUID; bounds covering all bells.
- Owner/member/enemy queries and a stable settlement identifier.
- Notifications when a town is created, expanded, split, removed or changes membership.
- A permission query for specific town-management actions.

Currently these details are package-private. The adapter uses reflection against the supplied version; a future public API would remove that dependency without changing town functionality.

## Easy NPC 7.18.0

The supplied core and Config UI JARs load alongside this mod in headless runtime tests. The supplied 25 KB bundle JAR describes the core/UI dependencies but contains no embedded core/UI JARs, so do not rely on that particular file alone to install both components.

Easy NPC already supports player command actions. To link a dialogue/button to an administrator shop, configure its action to execute **as the interacting player at normal player permission**, for example:

```
ffa shop buy stone 1
ffa shop sell stone 1
```

First create the shop using `/ffa shop set stone minecraft:stone 5 2`. Both the player command and the NPC action reach the same journaled inventory/Denar transaction. Do not elevate an NPC command to operator level or grant general administration permissions for this purpose. If Easy NPC command security requires an allowlist entry, allow only the exact shop actions needed by that NPC.

Native Easy NPC merchant screens continue to use their own item trades. The administration mod does not pretend those screens automatically use Denar. The supported Denar bridge is its player-command action, which requires no custom client UI or changes to Easy NPC.

## FTB Ranks 2101.1.5 and LuckPerms

FTB Ranks support uses its public `FTBRanksAPI.getPermissionValue`, `PermissionValue.asBoolean/asInteger`, and `RankManager.getRanks` APIs. These were checked against official source for **2101.1.5**. An explicit FTB decision takes precedence over LuckPerms; absent decisions consult the built-in ranks before the configured fallback. Missing/failed integrations cannot grant privileged access.

Relevant nodes:

- `ffa.admin`: complete administrator gameplay exemption and management access.
- `ffa.region.edit`, `ffa.spawn.manage`, `ffa.warp.manage`, `ffa.portal.manage`, `ffa.shop.manage`, `ffa.economy.manage`, `ffa.ranks.manage`, `ffa.chat.manage`, `ffa.reload`, `ffa.backup`: individual management access.
- `ffa.command.spawn`, `ffa.command.warp`, etc.: optional per-command player access; `permissions.defaultPlayerCommands` is the fallback.
- `ffa.homes.maximum`: FTB/local integer home limit, clamped to 0..1000.
- `ffa.homes.unlimited`: bypass the configured home count.

Use `group` access with a rank ID for rank restrictions. FTB Ranks remains responsible for assigning its own ranks. FFA's separate [built-in ranks](../wiki/Ranks.md) support local membership and group checks; no automatic import is performed. [Chat styles](../wiki/Chat-and-Nicknames.md) can be supplied by local rank metadata or personal settings, and self-edit permissions can come from the existing Boolean providers. Operators at the configured level always retain built-in access if neither permission provider is installed.

The exact FTB Ranks binary was not uploaded; runtime integration testing with that JAR remains to be performed. LuckPerms is also a soft dependency and was not present in the supplied test set.

## FTB Essentials and other command owners

Only the `/ffa` root is registered. Explicit `command.…` rank nodes can allow/deny other registered commands while preserving their original predicates when no decision is set. This preserves vanilla `/tp`, FTB `/spawn`, `/warp`, `/home`, `/tpa`, and other mods' roots without load-order competition. FTB Essentials detection disables this mod's home, request and request-privacy operations. Its custom `/ffa spawn`, `/ffa warp`, regions and portals remain available.

ServerPlayer teleport entry points and NeoForge teleport events validate arrivals against applicable region rules and destination safety. Mods that directly rewrite positions or bypass loader protection events need their own verified adapters; no universal interception guarantee is made.

FFA public Stats reads the supplied Mercenaries settlement names and membership without modifying towns. Vanish protects supported vanilla/FFA presence paths, not arbitrary mods' web maps or direct online-player APIs; test those separately. Whisper monitoring and inventory tools use native server/client interfaces and require no FFA client mod.
