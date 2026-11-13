# Player guide

[Wiki home](Home.md) · [Economy](Economy-and-Shops.md) · [Command reference](Command-Reference.md)

All examples use **FFA's** `/ffa` commands. Features can be hidden by server settings or rank permissions. Type `/ffa`, add a space, and press Tab.

## Spawn and warps

```text
/ffa spawn
/ffa warps
/ffa warp marketplace
```

Your inventory and XP are retained during mod teleports, including dimension changes. Protection is evaluated at arrival. You cannot force an obstructed, hazardous, forbidden or unavailable destination.

The default command cooldown is 3 seconds. The default warmup is zero; if the administrator enables a warmup, movement normally cancels it. Preparing a previously saved unloaded chunk can also require waiting in place. `/ffa tpcancel` cancels your pending mod travel or outgoing request when the native request system is available.

## Homes

```text
/ffa sethome base
/ffa homes
/ffa home base
/ffa delhome base
```

A name is required. Default limit: **3 homes**. Home names accept uppercase/lowercase letters, digits, underscores and hyphens. Lookup ignores case; setting an existing name updates it. Confirmation simply says “Home set.”

By default, normal players can create homes only in the Overworld and cannot create them inside protected regions. The allowed-dimensions list governs **creation**; changing it does not automatically delete existing homes. Existing destinations still pass teleport safety and region checks. A reduced home limit does not delete homes already saved.

FTB Ranks can override the limit, and administrators bypass home creation restrictions/count limits. A player with `ffa.homes.unlimited` bypasses the normal count limit.

## Ask to teleport

Suppose Alice wants to visit Bob:

```text
/ffa tpa Bob
```

Bob clicks the green **ACCEPT** or red **DECLINE** in the incoming message, or types:

```text
/ffa tpaccept
/ffa tpdeny
```

To ask Bob to come to Alice instead, Alice uses `/ffa tpahere Bob`. Bob still decides whether to accept. Requests expire after **60 seconds** by default, and the sender's default request cooldown is **10 seconds**. Both players must be online. One pending incoming request is handled per recipient, and sending a new outgoing request replaces the sender's old one.

A request can be accepted only once. Old clickable messages are tied to their original request and cannot accept a different, newer one. Use the newest message when one has expired. `/ffa tpcancel` cancels your outgoing request.

## Optional request privacy

If the administrator enables `modules.privacy`, restart to enable:

```text
/ffa tptoggle
/ffa tpignore Bob
```

`tptoggle` switches acceptance of incoming requests on/off. `tpignore` toggles an online player in your stored ignore list; running it again removes that block. These preferences apply to the native FFA request system and do not stop authorized vanilla/admin `/tp`.

## Back and optional random teleport

```text
/ffa back
/ffa rtp
```

`back` is available by default. It returns to your latest death location or the origin of your latest successful teleport, whichever event happened last. Respawning does not replace a death location. Native vanilla/FFA teleport entry points are tracked; a custom mod that moves players directly without those APIs needs an adapter. Failed/no-op teleports leave the location unchanged. Normal destination-safety checks, permissions, warmup and cooldown still apply. `rtp` remains disabled by default.

`rtp` searches around your current location in a permitted dimension. It considers only already loaded terrain, rejects protected/settlement/hazardous destinations, and has a bounded number of attempts. Finding no destination is a safe refusal, not permission to generate unlimited wilderness chunks.

## When FTB Essentials is installed

FFA disables its native home, request and request-privacy operations. Use FTB Essentials' own commands for those features. FFA's settings for native homes/requests do not automatically rewrite the other mod's limits or privacy settings. `/ffa spawn`, custom warps, portals and regions remain separate.

## Private messages and public statistics

Use `/ffa whisper PLAYER MESSAGE` to request consent before first contact. Accepting permanently approves that sender for you. Authorized administrators can monitor these moderated whispers. `/ffa stats PLAYER` privately shows playtime days, deaths, player kills, Denar, settlements and ranks for visible online players. See [whispers and stats](Whispers-and-Stats.md) for consent, monitoring and full syntax.

Use `/ffa balance send PLAYER AMOUNT` to send Denar or `/ffa balance request PLAYER AMOUNT` to ask them to pay you. Requests require the payer’s explicit acceptance. See [payments and limits](Economy-and-Shops.md). Accept the standard resource-pack prompt for colored animated portals.
