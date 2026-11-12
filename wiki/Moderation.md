# Punishments and jail

[Wiki home](Home.md) · [Commands](Command-Reference.md) · [Configuration](Configuration.md)

These commands require a full FFA administrator or operator. Administrators are exempt as targets. Times use real elapsed seconds and continue while players are offline; server restarts do not reset the stored deadline.

## Prepare jail cells

First configure a safe `/ffa spawn setpoint`; every release goes there. Select two opposite corners with `/ffa pos1` and `/ffa pos2`, then stand at the safe arrival spot **inside** that selection:

```text
/ffa jail cell set CellOne
/ffa jail cell list
```

Unlike normal regions, cells use all three dimensions of the selection, including Y. Select enough headroom, a solid floor, and room for the player's body. Cells may contain at most 4096 blocks and cannot overlap each other. A claimed Mercenaries area is rejected unless you deliberately use `/ffa jail cell set CellOne override`. This does not replace town ownership or permissions.

Create more cells the same way. One active prisoner reserves each cell, including while offline. Jailing refuses if no free loaded safe cell is available, if spawn is unset, or if an unresolved item trade prevents moving the player. You cannot delete a cell in use.

## Apply and remove punishments

```text
/ffa punish jail Alice 10m Repeated griefing
/ffa punish chatban Alice 30m Chat abuse
/ffa punish warpban Alice 1h Event restriction
/ffa punish status Alice
/ffa punish clear jail Alice
/ffa punish clear chatban Alice
/ffa punish clear warpban Alice
```

Durations accept a whole number of seconds, or `s`, `m`, `h`, `d`, `w`. The reason is optional. Omitting duration uses that punishment's configured default (five minutes initially). The maximum initially is one year. Zero/negative durations are rejected; use `clear` to pardon.

Jail moves an online player immediately to a free cell and forces Adventure Mode. It blocks gameplay interactions, item use, inventory manipulation and escape teleports/commands; leaving the cell by movement is corrected. Full administrators still manage their surroundings. Death/respawn and login reapply active confinement.

Expired or pardoned prisoners always go to the configured spawn and recover their earlier mode, after which normal spawn rules apply. If spawn is unavailable or unsafe, the player remains confined while release retries; repair the spawn rather than expecting an unsafe fallback. An offline prisoner's expired sentence is released on login. The sentence deadline keeps counting down offline.

Chat bans block ordinary public chat, FFA whispers and supported vanilla message commands. A custom mod sending messages directly needs its own adapter. Warp bans block **named warps**, including FFA portal routes linked to them; they do not block homes, TPA, `/ffa back` or ordinary portal travel. Jail's broader confinement still applies separately.

Jail requires an online target. Chat/warp bans, status and pardons can address a known offline player by full UUID. Names resolve online; selectors and network name lookups are not supported. Punishment commands and confirmation messages stay out of ordinary players' command trees.
