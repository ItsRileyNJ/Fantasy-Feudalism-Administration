# Combat travel and personal PvP

[Wiki home](Home.md) · [Player guide](Player-Guide.md) · [Configuration](Configuration.md)

## Travel after being hit

By default, a player who takes damage from another player cannot use travel commands for **30 seconds**. Every successful new hit refreshes the timer. Player-owned projectile hits and successful harmful player potion effects also count. Absorption hearts count as a successful hit; cancelled attacks, region-protected hits, god/spawn-invulnerability blocks and ordinary environmental/mob damage do not create this timer.

The **victim** receives the travel restriction. Both attacker and victim are temporarily prevented from changing their PvP preference, so attacking then immediately switching PvP off is blocked too.

Edit the existing sections, or add them once when upgrading:

```toml
[combat]
teleportBlockEnabled = true
tagSeconds = 30

[pvp]
enabledByDefault = true
```

These settings live in `config/ffa/server.toml`. Run `/ffa reload` after editing. `combat.tagSeconds` accepts whole seconds from **1 to 3600** and applies to new hits. Set `combat.teleportBlockEnabled = false` to disable the travel restriction; the combat restriction on PvP changes still applies. Older configs use the defaults shown above.

The travel check covers FFA homes, warps, spawn, back, RTP, TPA/tpahere/accept and FFA portal travel, plus native `/tp` and `/teleport` commands (including `/execute ... run tp`). Home/warp listing and saving a home are not travel. A successful hit cancels the victim's pending FFA warmup, saved-chunk loading job and related TPA requests. Execution is checked again before a queued teleport can move the player.

The restriction is scoped to these travel routes. Natural Minecraft portals, ender pearls, chorus fruit and other mods' independent teleport implementations are not universally intercepted by this command restriction. Existing region/portal/item rules still apply to them. A delegated external home provider needs its own combat integration; do not assume every mod uses FFA's travel pipeline.

A brief private command error shows the remaining wait. There is no chat message for each hit. If a pending FFA teleport is actually cancelled, the victim receives one cancellation message.

Combat timers use elapsed time and remain active through logout/relogin on the same running server. They expire normally while offline, clear on death, and reset on a server restart. Administrator travel remains exempt, and moderation's forced jail/release moves continue to work.

## Toggle your PvP preference

```text
/ffa pvp
/ffa pvp on
/ffa pvp off
/ffa pvp status
```

`/ffa pvp` switches between enabled and disabled. `on` and `off` request a specific state. `status` reports your state and remaining wait without changing it. Asking for an already-active state does not consume another cooldown.

After changing PvP, normal players must wait **five real minutes** before changing it again. The preference and cooldown are saved and survive reconnects and server restarts. A failed attempt does not change the preference or consume another cooldown. The five-minute cooldown is fixed; `combat.tagSeconds` is a separate combat timer. Administrators retain FFA's cooldown/combat-toggle exemption.

New or previously unconfigured players use `pvp.enabledByDefault` (default **true**). Once someone explicitly changes their preference, their saved choice takes precedence over later default changes.

If either player's PvP preference is disabled, damage between those two players is blocked **in both directions**. Direct player-owned projectile impacts are cancelled before their hit effects. Harmful effects from player-owned splash/lingering potion sources are blocked too; beneficial effects, self-effects and ordinary environmental/mob combat remain possible. The consent check applies to ordinary administrator attacks as well. Intentional administrator `/ffa smite` retains its explicit kill behavior.

Turning personal PvP on does not grant combat rights inside a PvP-disabled region, turn on global server PvP, or change town/NPC permissions. Server PvP and applicable region damage/PvP flags must also allow the attack.

## Rank permissions

The command uses the Boolean node **`ffa.command.pvp`**, falling back to `permissions.defaultPlayerCommands`. It is available to normal players by default. For example:

```text
/ffa ranks node add member ffa.command.pvp true
```

Use the rank's actual name. This grants the PvP preference command; it does not grant administrator exemptions or override protected-region rules. See [rank configuration](Ranks.md).
