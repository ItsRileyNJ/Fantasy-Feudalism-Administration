# Whispers, staff monitoring, and player stats

[Wiki home](Home.md) · [Staff tools](Staff-Tools.md) · [Player guide](Player-Guide.md)

## Send a private message

```text
/ffa whisper Alice Hello, would you like to trade?
```

Everything after the real online player name is the message. Do not add quotes unless you want quote characters in the message. Bodies contain 1..512 plain-text characters, with no embedded control/format codes.

For a sender Alice has not approved, Alice first receives the server message:

```text
YourName wants to send you a message
[Accept] [Deny]
```

The message body is withheld from Alice until acceptance. Click the buttons or use the currently available `/ffa whisperaccept` / `/ffa whisperdeny` command. Buttons contain unique request tokens: old buttons cannot accept a different request, and a consumed/expired request cannot be replayed.

Accepting delivers the waiting message and permanently remembers that **sender UUID for that recipient**. Later messages from the same sender arrive without another prompt, including after reconnects/restarts. Approval is directional: a reply in the reverse direction uses the other recipient's own consent. Names may change without losing the UUID-based approval.

Denying discards the waiting message and grants no permission. Future first-contact requests can be sent after the request cooldown; Deny is not a permanent block. Only one incoming message request per recipient and one outgoing request per sender are retained. A replacement outgoing request invalidates the older button. Requests expire and disconnect/vanish cancels them.

Default first-contact cooldown/expiry use the existing `teleport.requestCooldownSeconds` (**10**) and `teleport.requestExpirySeconds` (**60**) settings. Approved direct messages have a **1-second** cooldown. Messages to yourself or an unavailable target are refused. Ignored senders in the existing FFA privacy list are refused as well.

To withdraw permanent approval:

```text
/ffa whisperforget Alice
```

Use an online real name or a full lowercase UUID for an offline sender. Future messages from that sender require approval again. This does not erase message history already shown to clients.

## Administrator monitoring

Authorized administrators receive a private copy in chat:

```text
(Alice --> Bob) Hello there
```

First messages awaiting approval are explicitly labeled:

```text
(Alice --> Bob) [pending consent] Hello there
```

Monitoring defaults **on** for each administrator, including while vanished. It includes pending messages even if the recipient later denies them. Accepting a previously monitored pending message does not send a duplicate monitor copy. Participants already receiving a delivered message are not sent an extra monitoring duplicate.

Each administrator controls their own monitoring:

```text
/ffa whispers off
/ffa whispers on
```

This preference persists across reconnects/restarts. It does not turn off other administrators' monitoring or disable player messaging. Ordinary players cannot use or see these monitoring commands. Whisper bodies are not added to FFA's audit file; another logging/chat mod may have its own logging behavior. These are moderated private messages, not a guarantee of privacy from server administrators.

## View player statistics

Everyone can use:

```text
/ffa stats Alice
```

The target must be online and visible to you. The private result includes:

- **Days played:** vanilla `PLAY_TIME`, converted from 20 ticks/second into 24-hour days, displayed with two decimal places. This is total recorded playtime, not Minecraft day cycles or elapsed calendar time since joining.
- **Deaths** and **player kills:** the target's vanilla saved statistics.
- **Money:** whole Denar from the FFA ledger. Other mods' item currencies remain separate.
- **Settlements:** owned/member Mercenaries settlement names across active dimensions when its adapter is available. Missing mods or an unavailable adapter are identified rather than guessed.
- **Ranks:** active FFA rank IDs and supported FTB rank IDs, labeled by provider. This includes applicable default/inherited local ranks.

Ordinary players cannot use Stats to discover a vanished administrator. Authorized staff/console retain their trusted lookup access. Nicknames do not replace the real name in command arguments.

## Permissions and suggestions

`ffa.command.whisper` controls sending and consent-revocation commands. `ffa.command.stats` controls public stats. Both use the normal `permissions.defaultPlayerCommands` fallback. Operators/full FFA administrators retain their ordinary authority.

The Accept/Deny branches appear only while that player has a live incoming request. Administrator monitoring, vanish and inventory tools are omitted from unauthorized client trees and help. All commands remain under the lowercase `/ffa` root.
