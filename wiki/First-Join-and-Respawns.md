# First join and death respawns

[Wiki home](Home.md) · [Player travel](Player-Guide.md)

Create and verify the [spawn teleport point](Getting-Started.md) before enabling redirects. Edit the existing `[spawn]` section and run `/ffa reload` for supported changes.

## New-player arrival

```toml
[spawn]
invulnerabilitySeconds = 8
firstJoin = true
respawnPolicy = "vanilla"
```

FFA uses its own joined record and the existence of the player's vanilla player-data file to distinguish new arrivals from returning players. Installing FFA on an established world is not intended to repeatedly redirect existing players.

A new player's first arrival can be sent to the configured FFA spawn, including the optional void dimension. Inventory/XP are not rewritten. Safety and destination availability still apply. If the initial move is refused or cancelled, do not depend on repeated login redirects as a repair mechanism; fix the destination and use `/ffa spawn`.

## Separate initial spawn and rules room

Stand at a safe first-arrival position and run `/ffa setfirstspawn`. This separately saved point takes priority over `spawn.firstJoin` for genuinely new nonadministrator players, even when `spawn.firstJoin` is false. Returning accepted players keep their saved location. This point does not replace `/ffa spawn`, beds or the ordinary death policy.

To require a one-time rules agreement for **existing and new** players, configure the [rules room](Rules-Room.md). While enabled, unaccepted players return there at login and cannot leave or use other commands. Acceptance is saved permanently per UUID. Administrators are exempt.

## Spawn invincibility

Every player, including administrators, receives eight seconds of invincibility on joining the server and after respawning from death. This works at any arrival location, including beds and respawn anchors, regardless of the death policy or region flags.

Set `spawn.invulnerabilitySeconds` in `config/ffa/server.toml` to a whole number from **0 to 3600**. `0` disables this grace period, including Minecraft's separate default spawn immunity. Missing this setting in an older config uses **8**. Run `/ffa reload` after editing; the new duration applies at the next login or death respawn. Existing timers finish using their original duration.

The timer uses elapsed time starting at server login or respawn, including loading time. It blocks incoming damage, fatal damage, and direct projectile impacts. It does not change Creative mode or `/ffa god`, and the administrator's intentional `/ffa smite` still works. Ordinary teleports, `/ffa spawn`, portals and returning from the End do not renew it. There is no extra chat message.

## Death policies

| `respawnPolicy` | Behavior |
| --- | --- |
| `"vanilla"` | Leave Minecraft's chosen death respawn unchanged; default |
| `"bed_first"` | Retain a valid personal bed/anchor result; otherwise try configured FFA spawn |
| `"spawn_first"` | Prefer a valid configured FFA spawn |

If the configured spawn is absent or fails validation, FFA retains the original vanilla destination instead of repeatedly redirecting. Saved personal respawn references are retained for future use. The death-respawn override does not redirect the End-fight return through this policy.

Vanilla inventory loss, XP loss, bed/anchor validity and resource rules still apply. This is not a `keepInventory` feature. Changing where a death respawn occurs does not turn death into an inventory-preserving teleport.

These policies need real-client tests with the server's exact bed/anchor and dimension setup before production. If another mod redirects first join or respawns, configure the overlap deliberately rather than assuming both redirect systems will arbitrate automatically.
