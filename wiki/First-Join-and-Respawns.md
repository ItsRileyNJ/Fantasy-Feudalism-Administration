# First join and death respawns

[Wiki home](Home.md) · [Player travel](Player-Guide.md)

Create and verify the [spawn teleport point](Getting-Started.md) before enabling redirects. Edit the existing `[spawn]` section and run `/ffa reload` for supported changes.

## New-player arrival

```toml
[spawn]
firstJoin = true
respawnPolicy = "vanilla"
```

FFA uses its own joined record and the existence of the player's vanilla player-data file to distinguish new arrivals from returning players. Installing FFA on an established world is not intended to repeatedly redirect existing players.

A new player's first arrival can be sent to the configured FFA spawn, including the optional void dimension. Inventory/XP are not rewritten. Safety and destination availability still apply. If the initial move is refused or cancelled, do not depend on repeated login redirects as a repair mechanism; fix the destination and use `/ffa spawn`.

## Death policies

| `respawnPolicy` | Behavior |
| --- | --- |
| `"vanilla"` | Leave Minecraft's chosen death respawn unchanged; default |
| `"bed_first"` | Retain a valid personal bed/anchor result; otherwise try configured FFA spawn |
| `"spawn_first"` | Prefer a valid configured FFA spawn |

If the configured spawn is absent or fails validation, FFA retains the original vanilla destination instead of repeatedly redirecting. Saved personal respawn references are retained for future use. The death-respawn override does not redirect the End-fight return through this policy.

Vanilla inventory loss, XP loss, bed/anchor validity and resource rules still apply. This is not a `keepInventory` feature. Changing where a death respawn occurs does not turn death into an inventory-preserving teleport.

These policies need real-client tests with the server's exact bed/anchor and dimension setup before production. If another mod redirects first join or respawns, configure the overlap deliberately rather than assuming both redirect systems will arbitrate automatically.
