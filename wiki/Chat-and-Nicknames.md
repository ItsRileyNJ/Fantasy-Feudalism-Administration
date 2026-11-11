# Chat colors, nicknames, and titles

[Wiki home](Home.md) · [Built-in ranks](Ranks.md) · [Permissions](Permissions-and-Access.md)

Available in **0.2.0**, server-side with no FFA client mod. Names and titles support independent colors, bold, and italic. A displayed nickname does not change a player's UUID, login name, permission identity, or the player name used by commands.

## Assign a player's appearance

Operators, full FFA administrators, or holders of `ffa.chat.manage` can run these from console or in game. Replace `Alice` with an online player's real account name:

```text
/ffa chat set Alice nickname "Lady Alice"
/ffa chat set Alice title Duchess
/ffa chat set Alice name_color gold
/ffa chat set Alice title_color aqua
/ffa chat set Alice name_bold true
/ffa chat set Alice name_italic false
/ffa chat set Alice title_bold false
/ffa chat set Alice title_italic true
/ffa chat view Alice
```

Chat displays **[Duchess] Lady Alice**, with a gold bold name and an aqua italic title. Use a full lowercase UUID instead of a name for an offline player. Personal overrides persist in `<world>/ffa/state.json` and apply when they next join.

Changes apply immediately to online display names. Commands still target `Alice`, not `Lady Alice`. Other features that use Minecraft's display-name component, such as some join/death messages, can also show the decorated name. This release does not replace the tab-list or overhead entity-name renderer.

## Give a rank a title, color, and formatting

Set style metadata on an FFA rank:

```text
/ffa ranks create nobles 50
/ffa ranks node add nobles ffa.chat.style.title Duke
/ffa ranks node add nobles ffa.chat.style.name_color gold
/ffa ranks node add nobles ffa.chat.style.title_color aqua
/ffa ranks node add nobles ffa.chat.style.name_bold true
/ffa ranks node add nobles ffa.chat.style.title_italic true
/ffa ranks add Alice nobles
```

Each property takes the first exact style value from the player's active local ranks, ordered by power and then rank ID as described in [ranks](Ranks.md). Inherited ranks participate too. Style metadata uses exact nodes; broad permission wildcards never accidentally make names bold or create titles.

The eight style nodes are:

| Node | Type |
| --- | --- |
| `ffa.chat.style.nickname` | Plain-text string, 1..32 characters |
| `ffa.chat.style.title` | Plain-text string, 0..48 characters |
| `ffa.chat.style.name_color` | Color name string |
| `ffa.chat.style.title_color` | Color name string |
| `ffa.chat.style.name_bold` | Boolean |
| `ffa.chat.style.name_italic` | Boolean |
| `ffa.chat.style.title_bold` | Boolean |
| `ffa.chat.style.title_italic` | Boolean |

Per-player overrides win over rank style values independently for each property. Unconfigured names use the real account name; unconfigured titles are absent. Once FFA formats a display name, unset colors are white and unset bold/italic settings are false.

## Unlock nickname changes for a high rank

Give the desired rank the **editing permission**, separate from its visual style:

```text
/ffa ranks node add nobles ffa.chat.nickname true
```

An assigned nonadministrator can now run:

```text
/ffa chat nickname "Sir Alice"
/ffa chat inherit nickname
/ffa chat view
```

The first command chooses a personal nickname. `inherit nickname` clears that override, restoring the rank nickname or real account name. This grants no ability to edit another player, manage ranks, change colors, or assign a title.

Optional self-service permissions:

| Node | Player commands |
| --- | --- |
| `ffa.chat.nickname` | `/ffa chat nickname TEXT` |
| `ffa.chat.title` | `/ffa chat title TEXT` |
| `ffa.chat.color` | `/ffa chat color COLOR`, `/ffa chat titlecolor COLOR` |
| `ffa.chat.format` | `/ffa chat bold BOOL`, `italic BOOL`, `titlebold BOOL`, `titleitalic BOOL` |
| `ffa.chat.manage` | Edit any player, reset overrides, and use all chat styling actions |

`inherit PROPERTY` requires the corresponding editing permission. For example, `inherit name_color` needs `ffa.chat.color`. Permission grants can also come from the supported FTB Ranks/LuckPerms Boolean providers. Visual style metadata comes from FFA's built-in ranks; it does not reinterpret another mod's name-format syntax.

Revoking an editing permission removes the ability to make further changes; an existing personal nickname remains until cleared/reset. A player who has an editing permission can change that property even if an administrator previously assigned its current personal value.

## Clear an override or hide a title

```text
/ffa chat clear Alice nickname
/ffa chat clear Alice title
/ffa chat reset Alice
```

`clear` restores inheritance for one property. `reset` removes all personal overrides, preserving rank defaults. To deliberately suppress an inherited title, assign an empty personal title:

```text
/ffa chat set Alice title ""
```

The self-service equivalent `/ffa chat title ""` requires `ffa.chat.title`.

## Colors and text rules

Use these native Minecraft color names; Tab suggests them:

```text
black dark_blue dark_green dark_aqua dark_red dark_purple gold gray
dark_gray blue green aqua red light_purple yellow white
```

Use `true` or `false` for bold/italic. Quote names/titles containing spaces. Names/titles are literal text: no `&`/section-sign formatting parser, JSON click actions, embedded newlines, invisible control characters, or leading/trailing spaces. Put the title text alone in the value; FFA supplies square brackets and the separating space.

Personal styles are included in `state.json` snapshots. Do not roll back the entire financial state merely to undo a nickname; use `clear`/`reset` instead.

## Hidden management commands

Unauthorized administrative branches are absent from the actual command tree sent to normal clients. `/ffa help` also lists only currently permitted command families. A nickname-enabled noble sees the nickname action, their view/inheritance tools, and any other explicitly unlocked actions; they do not receive the `set`, `clear`, or `reset` administration branches.

FFA retains its native management checks even if another ranks mod replaces command-node predicates with a broad allow. Typing a hidden command manually does not grant access. Permission changes resend the filtered command tree immediately. A delegated manager can see the management commands they are authorized to use; ordinary players do not receive them.

## Other chat mods

FFA changes the server's display-name component and leaves signed chat-message bodies unchanged. If no FFA personal/rank style is configured, it preserves the existing display-name component, including another mod's decoration. If FFA style is configured, FFA supplies that player's decorated name; choose one owner for name formatting and test the complete chat stack. A custom chat mod that ignores Minecraft's display-name component needs an adapter. FTB Ranks' separate prefix/name-format system and LuckPerms metadata are not automatically imported.
