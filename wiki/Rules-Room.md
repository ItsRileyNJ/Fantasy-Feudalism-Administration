# Rules room and first-join spawn

[Wiki home](Home.md) · [Regions](Regions.md) · [First join and respawns](First-Join-and-Respawns.md)

The rules room is an ordinary **child region inside main spawn**, with an additional one-time acceptance requirement. It starts disabled. Once enabled, **existing players and new players must accept once per UUID**. Administrators are completely exempt.

## Set up the room

1. [Create main spawn](Getting-Started.md) first. Build a safe room inside it and place the actual rules on signs, lecterns or other visible displays. Include the rules in the chat reminder instead if reading them would require an interaction blocked by your child flags.
2. Select opposite corners of the room. Stand at the first corner, run `/ffa pos1`, move to the second, then run:

   ```text
   /ffa pos2
   /ffa region create child RulesRoom
   /ffa rules region RulesRoom
   ```

3. Stand on a solid floor at the room's safe arrival point, with two clear blocks above, and run:

   ```text
   /ffa setfirstspawn
   ```

4. Set the chat reminder, then enable the requirement:

   ```text
   /ffa rules message Please read the rules on the walls, then choose ACCEPT to continue.
   /ffa rules enable
   /ffa rules status
   ```

Style the reminder with `/ffa rules style color gold`, `/ffa rules style bold true`, `/ffa rules style italic true`, and `/ffa rules style underline true`. Use `false` to remove a style. Colors accept Minecraft names or a quoted `"#RRGGBB"` value. Buttons retain their clear green/red styling.

The region covers full build height, as other FFA regions do. It must remain fully within main spawn. Use a separate room with no overlap with other children. Mixed-case region names work. `/ffa setfirstspawn` saves the administrator's current position and facing; it is separate from the ordinary `/ffa spawn` destination.

Enabling immediately moves connected, unaccepted nonadministrators to that arrival point. Offline players are handled when they next join. The first-join spawn must stay inside the selected child. Geometry edits/reloads that would delete the active child or exclude its arrival point are rejected.

## Before acceptance

- Bold green **[ ACCEPT ]** and red **[ DENY ]** buttons appear on their own spaced chat line. They use vanilla chat; no FFA client mod is needed.
- Walking outside the room or trying another mod's teleport out returns the player to the exact initial spawn point. Reconnecting or dying does not bypass the requirement.
- **Every other player command is blocked**, including vanilla commands, other mods' commands, aliases, homes, warps and messages. Rank grants do not bypass this. The only permitted actions are `/ffa rules accept` and `/ffa rules deny`; the buttons add their own request token automatically.
- The player cannot send normal chat. Ordinary player chat, whispers, disguised command chat and unsolicited server chat announcements are hidden. Administrator chat and administrator messages remain visible, as do the rules prompt and buttons. Gameplay overlays are unaffected.
- **DENY** leaves the player in the room and offers the buttons again. It does not disconnect them. Trying another command can show the prompt again, with a short spam-prevention delay.

The child keeps normal region inheritance and flag overrides. Leave its protection flags blocked for a protected room. Administratively jailed players remain confined in jail instead of bouncing between the two areas; they can still accept the rules there. On release, an unaccepted player is returned to the rules room.

## After acceptance

Acceptance is saved before the player is released. Normal chat and permitted commands become available immediately. Players leave the room themselves and may revisit it freely; acceptance is never reset by entering the room, disconnecting, restarting, or disabling/re-enabling the feature.

The initial spawn applies to genuinely new nonadministrators even if the rules room is disabled. It takes priority over `spawn.firstJoin`; it does not change accepted returning players' saved logout location, beds, ordinary death policies, or the normal `/ffa spawn` point.

## Change or disable

```text
/ffa rules disable
/ffa rules region NewRulesRoom
/ffa setfirstspawn
/ffa rules enable
```

Stand at the new safe arrival location before setting it. Disabling immediately releases command/chat restrictions; it does not mark anyone accepted. Accepted records remain valid on re-enable. Changing the room is not a reset of everyone's agreement.

Room selection, reminder, enable state, initial spawn and player acceptance are saved in `<world>/ffa/state.json`; geometry and inherited flags remain in `regions.toml`. Use the setup commands and normal backups rather than editing live state files. The ordinary first-join/respawn settings remain in `config/ffa/server.toml`. If the safe arrival is obstructed or the dimension becomes unavailable, FFA refuses the transfer and disconnects the affected player with an administrator-contact message instead of releasing them outside the room.
