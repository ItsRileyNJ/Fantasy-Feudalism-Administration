# Troubleshooting

[Wiki home](Home.md) · [IntelliJ builds](IntelliJ-and-Building.md)

## `/ffa` is unknown

Check the **server's** log/mod list for Fantasy Feudalism: Administration, Minecraft 1.21.1 and NeoForge 21.1.262. Installing the JAR only on the client does not add server commands. Keep one FFA version in the server's mods directory and restart after replacing it.

If building in IntelliJ, building a JAR does not automatically copy it to a separate external server's mods folder. A generated development-server run can load project classes directly; these are different launch paths.

## No suggestions after `/ffa`

Use **0.1.2+**, type `/ffa`, add a **space**, then press **Tab**. A running old server still has its old command tree until restarted. Check the loaded version rather than only the file downloaded.

Administration branches require OP or the relevant nodes. Optional disabled modules are hidden. FTB Essentials intentionally removes native FFA home/request suggestions. An operator account can use `/ffa reload` to refresh the command list after supported config edits; rejoining also obtains the current tree. If suggestions are missing for vanilla commands too, check the client's command-suggestion setting.

## Build fails at `policyTest`

The final "Java exited with code 1" line is a summary. Capture the first exception **above** it.

For `Storage.atomic` directory `AccessDeniedException` on Windows in 0.1.0, update to 0.1.1 or later; 0.1.2 includes that fix. A current-version access error can still be a genuine unwritable file/directory. Do not solve it by disabling tests or ignoring all save errors. Java/Gradle should use Java 21; see [the IntelliJ checklist](IntelliJ-and-Building.md).

## Protection appears ineffective

Test with a nonoperator, non-`ffa.admin` account. Administrators are exempt even in Survival. Check `/ffa region list`, dimensions and the current child region's overrides. Creating a spawn point does not create its protection region. Another mod bypassing protection events needs a verified adapter.

## A command is red, hidden, or incomplete

Use Tab and the [exact reference](Command-Reference.md). Namespaced block/item IDs need their namespace, e.g. `minecraft:stone`. Quote messages or team-center strings containing spaces. Region/warp/home IDs are lowercase letters, digits, underscores or hyphens; there is no unnamed `/ffa home` in this release.

A hidden command can be a permission/module restriction, not a missing installation. With `defaultPlayerCommands = false`, delegated staff may need the relevant command-family permission as well as a management node.

## A teleport is refused

| Message/symptom | Check |
| --- | --- |
| Destination is not configured | Set `/ffa spawn setpoint`, create the warp, or choose a saved home |
| Destination is obstructed | Clear room for the player's body; do not force arrivals inside walls |
| No safe support / hazard | Use a dry full-block floor, clear fire/lava/cactus/magma/powder snow and other checked hazards; partial floors can fail conservative checks |
| Dimension unavailable | Enable/register the intended dimension and repair outdated points |
| Loading saved destination | Stand still; movement can cancel the queued load |
| No fully generated saved chunk | Visit/load and save the destination legitimately; FFA will not generate missing chunks just to honor the point |
| Loading busy/timed out | Respect the bounded queue, investigate storage/server load and try later |
| Permission denied | Check source/destination region travel flags and portal, warp and region access rules |
| Teleport declined by another rule | Inspect the other protection/teleport mods and server logs |

Admins in Creative can deliberately set aerial points that are unsafe for normal players. Set public arrival points while standing on suitable full-block floors, then test with a normal player. Vanilla/admin teleport entry points are also safety checked; for initial void construction, follow [the bounded chunk-loading example](Void-Spawn-Dimension.md).

## A portal does not work

Confirm `modules.portals`, a valid linked warp, safe destination, source/destination region rules and **both** portal/warp access. Move into/cross a block boundary in the trigger volume and wait for the default 5-second cooldown. Keep arrival points outside other trigger volumes. Colored markers alone do not prove the module is enabled. Only authorized administrators recolor them; aim at a corner or use `/ffa portal color`. If a corner is missing, load its chunk and reissue the color command to refresh loaded markers.

## Home/TPA conflicts

When FTB Essentials is installed, use its native commands and configuration. FFA's overlapping operations are disabled by design. `/ffa spawn` and `/ffa warp` remain independent from `/spawn` and `/warp` owned by another mod.

## Region edit/reload fails

Fix the specific reported issue: duplicate IDs, reversed bounds, unsupported dimension, overlapping siblings, child outside parent, or settlement overlap without an explicit override. External TOML edits must be reloaded before in-game edits can overwrite the file. Change all dependent parent/child definitions together. A failed live reload retains the last valid active definitions; restart-only settings need a full restart.

## Shop, request, or recovery failures

- No money: new accounts start at 0; sell eligible items or have an administrator set funds.
- No inventory room: main inventory capacity matters; items are not dropped as a fallback.
- No matching sale items: modified components/enchants/names/damage may not match the default offer.
- Request stale/expired: use the current message or send a new request after cooldown; consent cannot be replayed.
- No safe RTP result: reduce the radius or use normally loaded wilderness; the search does not generate new terrain.
- Recovery/account lock: follow [backups and recovery](Backups-and-Recovery.md), preserve files and inspect the server log. Do not delete a journal to manufacture a successful result.

## Ranks or chat changes are not applying

Use `/ffa ranks check PLAYER NODE` and `/ffa ranks player PLAYER` as a manager. Check explicit FTB Ranks/LuckPerms decisions before local ranks, rank power, default/inherited memberships and personal chat overrides. Use `/ffa ranks reload` for external edits to `ranks.json`; general `/ffa reload` handles settings/regions. The [ranks](Ranks.md) and [chat](Chat-and-Nicknames.md) guides include examples. Hidden commands are expected when a player lacks their permission.
