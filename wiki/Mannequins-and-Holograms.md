# Mannequins and player holograms

[Wiki home](Home.md) · [Staff tools](Staff-Tools.md) · [Command reference](Command-Reference.md)

From **FFA 0.11.0**, administrators can place permanent, full-size player displays in spawn or another dimension to recognize founders, builders, winners, or other notable players. They use the selected account's skin, with optional copied equipment and a floating name/title. `/ffa hologram` is an alias for `/ffa mannequin`.

FFA remains **server-only**. These displays use Minecraft's existing player, equipment, and text packets; no FFA client JAR, resource pack, or Easy NPC installation is needed for this feature. They are static visual displays, not NPCs with dialogue or trades.

## Example: recognize a founder

Stand where the display's feet should be and face the direction it should face. Use the player's real Minecraft account name:

```text
/ffa mannequin create Founder Riley
/ffa mannequin text Founder nickname "Riley"
/ffa mannequin text Founder title "Server Founder"
/ffa mannequin text Founder name_color gold
/ffa mannequin text Founder name_bold true
/ffa mannequin text Founder title_color yellow
/ffa mannequin text Founder title_italic true
/ffa mannequin text Founder title_underlined true
```

Replace `Riley` with the actual account. To display yourself, omit the account:

```text
/ffa mannequin create Me
```

Names such as `Founder` preserve capitalization. They must be 1–32 letters, numbers, underscores or hyphens; names differing only by capitalization count as duplicates. Later commands find them regardless of case. These names identify the display and do not rename a player's account.

Creation uses your position, dimension, yaw and head pitch. Keep your view roughly level for an upright portrait. Floating displays are allowed; there is no invisible pedestal or automatically placed block.

## Copy equipment without taking items

While the source player is online:

```text
/ffa mannequin equipment Founder Riley
```

This copies their main hand, offhand, helmet, chestplate, leggings and boots, including supported item components. It takes no items, charges no Denar, and gives nobody an item. It is a snapshot: subsequent inventory changes do not update the display automatically. Copy again when you want a new outfit. The selected equipment player can differ from the account supplying the skin.

```text
/ffa mannequin equipment Founder clear
```

This removes the display's equipment only. Neither players nor administrators can collect visual equipment by attacking, right-clicking or opening the display. Use the editing commands to change it. Existing armor stands and Supplementaries hat stands remain separate objects with their [own interaction flags](Flags-and-Interactions.md#protect-armor-stands-and-hat-stands).

## Move, rotate, change the skin, or remove

```text
/ffa mannequin move Founder
/ffa mannequin face Founder
/ffa mannequin skin Founder OtherPlayer
/ffa mannequin info Founder
/ffa mannequin list
/ffa mannequin delete Founder
```

`move` puts the display at your current feet position and copies your rotation, including when moving between dimensions. `face` copies your current look direction without moving the display. `skin` changes its account/skin snapshot while preserving its position, copied equipment, and explicit text overrides.

Online players supply their current server-known skin immediately. For an offline account, FFA looks up the account/skin asynchronously; it may briefly say `Loading skin.` and needs access to Minecraft's profile services. If this fails, have the person join and try again. An online account without supplied texture data uses a vanilla default skin. Custom client-only skin changes are not necessarily known to the server.

Skins are saved rather than refreshed on every visit or restart. After the person changes their skin, have them reconnect and run `skin` again, or request the offline account once the profile service has the new skin. A failed lookup keeps an existing display unchanged. Deleting a display also cancels its pending skin lookup; a late response cannot recreate it. Permission is checked again before completing an offline request.

## Name and title settings

```text
/ffa mannequin label Founder false
/ffa mannequin label Founder true
/ffa mannequin text Founder reset
/ffa mannequin text Founder title ""
```

`label false` hides the whole floating label. `label true` shows it again. `reset` restores the account-name label, white text, an empty title and no extra formatting. Label visibility remains as set. An empty quoted title removes just the title.

Use `/ffa mannequin text DISPLAY PROPERTY VALUE` with:

| Property | Value |
| --- | --- |
| `nickname` | Plain display name, 1–32 characters; quote names containing spaces |
| `title` | Plain title, up to 48 characters; `""` clears it |
| `name_color`, `title_color` | A named Minecraft color, such as `gold`, `aqua`, `white` or `dark_red` |
| `name_bold`, `title_bold` | `true` or `false` |
| `name_italic`, `title_italic` | `true` or `false` |
| `name_underlined`, `title_underlined` | `true` or `false` |

The name and title have independent styles. No formatting codes, JSON chat commands or clickable actions are parsed from this text. Display styling does not change real chat/rank names, and rank changes do not automatically restyle existing displays.

## Permissions, saving, and limits

All mannequin/hologram management requires full administrator authority. Both command roots are hidden from nonadministrators, including players granted ordinary command permissions. Creation, movement and facing require an in-game administrator because they use that person's location/rotation. Console administrators can list, inspect, delete, change skins, copy an online player's equipment, and edit labels/text.

The world supports up to **128** displays. Clients see nearby displays within **64 blocks** in their own dimension once the containing chunk has been sent. FFA checks viewers twice per second and removes old display/profile/team packets on range exit, chunk unload, dimension changes and respawns. Displays do not force-load or generate chunks.

These are visual entities sent to nearby viewers, not server world entities or connected players. They cannot be harmed, looted, pushed around or used to trigger player pressure plates. They do not add to online counts, `/list`, Tab, selectors, player statistics, settlement membership, or quest-team membership. A public memorial of an administrator does not disclose that person's current online/vanish status.

Data lives in the existing `<world>/ffa/state.json` and is included in `/ffa backup`. Existing worlds load with an empty mannequin collection; no manual conversion is needed. Use commands rather than editing live state. On restart, FFA recreates displays for nearby viewers from saved data without repeating profile lookups. Unknown/removed mod items in a saved outfit are omitted from the visible outfit with a server warning; install the original mod again or recopy/clear that outfit.

The native tests verify command permissions, vanilla packets and saved state. Check actual skin loading, slim/wide models, equipment and label appearance with a real client on your server before building a large display area.
