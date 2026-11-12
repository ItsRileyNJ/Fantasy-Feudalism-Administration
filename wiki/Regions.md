# Regions

[Wiki home](Home.md) · [Flags](Flags-and-Interactions.md) · [Configuration](Configuration.md)

## Region types

| Type | Relationship | Mode behavior for nonadministrators |
| --- | --- | --- |
| `SPAWN` | One main spawn region for the server | Forces Adventure while inside |
| `CHILD` | Completely inside that spawn, in the same dimension | Overrides parent flags; restores prior mode when building or breaking is allowed |
| `STANDALONE` | Optional independent protected area | Applies flags without forcing the spawn Adventure mode |

Children inherit **flags** from the main spawn unless overridden. They cannot use another child as their parent. Child regions cannot overlap each other, and standalone areas cannot overlap existing regions. A shared boundary block counts as overlap: use adjacent, non-overlapping block ranges.

For example, a child covering X 10..20 includes a player at X 20.9 and excludes X 21.0. All regions are horizontal rectangles extending through full build height.

## Create a marketplace

First [create main spawn](Getting-Started.md). Select two corners **inside** it, for example (10, 65, 10) and (20, 65, 20):

```text
/ffa pos1
```

Move to the second corner:

```text
/ffa pos2
/ffa region create child Marketplace
/ffa region message marketplace entry "Welcome to the Marketplace."
/ffa region message marketplace exit "You have left the Marketplace."
/ffa region flag marketplace npc_trading true
```

Region IDs use 1..48 uppercase or lowercase letters, digits, underscores or hyphens. Lookup ignores case, so `Market` and `market` cannot be separate regions. The initial display name matches the ID; the [region configuration](Configuration.md) lets you change the display name separately.

## Create a combat arena

Select a different rectangle inside spawn, with no overlap with the marketplace:

```text
/ffa region create arena
/ffa region flag arena pvp true
/ffa region flag arena damage true
/ffa region flag arena hunger true
/ffa region flag arena flight false
/ffa region message arena entry "You have entered the Arena."
```

Both PvP and player damage must allow combat. Cross-boundary attacks are also constrained by the applicable rules. This is ordinary Minecraft combat: FFA does not create a duel session or restore death inventories. Adventure Mode remains in force unless the child allows building or breaking.

Players can leave using normal permitted travel. If the rules require blocking teleport exits, set `teleport_out` to `false`; this does not block walking across the boundary or create a combat-tag system.

## Change, inspect, or delete

```text
/ffa region visualize marketplace
/ffa region expand marketplace 2
/ffa region contract marketplace 2
/ffa region flag marketplace pvp inherit
/ffa region list
```

Expansion/contraction changes **all four horizontal edges** by the given number of blocks. It is refused if it creates invalid bounds, overlap, or a child outside its parent. There are no directional expand arguments in this release.

Visualization refreshes private boundary particles at your current height until you run `/ffa region visualize cancel` (or bare `/ffa region visualize`). Editing the bounds updates the display. Switching to another region replaces your selection. Logging out, losing permission or deleting the region stops it. Stand near the boundary in the correct dimension; distant edges can be hard to see.

To delete:

```text
/ffa region delete marketplace
```

Delete a parent's children before deleting the parent. Deleting a protection region does not remove world blocks, its associated warp, or the separately stored spawn teleport point.

## Mercenaries settlement overlap

Normal creation/editing rejects overlap with a claimed Mercenaries settlement. To make a deliberate exception, use the explicit option:

```text
/ffa region create town_hall override
```

Main spawn creation supports `/ffa spawn create override`. A region named `override` does not itself enable the option.

For a file edit, set that region's `settlementOverride = true`. This accepts overlap; it does not transfer town ownership, grant every member a protection bypass, or replace Mercenaries' rules. Review [compatibility](Mod-Compatibility.md) before overlaying a town.

## Standalone protected areas

Set `standaloneRegions = true` in the existing `[modules]` section, then `/ffa reload`. Select corners elsewhere and run:

```text
/ffa region create standalone EventGrounds
```

Standalone areas use the global default flag values plus their own overrides; they do not inherit edits made to the spawn parent's flags. Use them for separate event sites or public buildings. Remove existing standalone regions before disabling the module, or validation will retain the last valid configuration.

## Messages and entry/exit behavior

Messages belong to individual regions. An empty message disables it. Flags inherit; message strings are not automatically inherited.

The current implementation tracks the **effective current region**. Moving from the spawn's ordinary area into a child can send the spawn's exit message and the child's entry message, even though the child is physically inside spawn. Returning sends the child's exit and the parent's entry. Choose neutral parent wording, such as "Leaving the spawn plaza", rather than claiming every such transition leaves protection entirely.

For precise boundary changes, renaming, dimension changes, access or bulk edits, use the authoritative [region TOML file](Configuration.md). In-game edits refuse to overwrite external edits that have not yet been reloaded.

Entering/leaving uses a smaller centered on-screen notice, never a chat announcement. Vanilla clients render this at subtitle size, 50% smaller than the former main title; an exact 40% reduction would require client-side font changes. The defaults are `Entering {name}` and `Leaving {name}`. `{name}` means the display name; `{id}` means the region ID. When two transitions happen together, both appear on the smaller line as `Entering Child · Leaving Parent`, with each message keeping its own color and formatting. A player logging in inside a region receives its entry title. To suppress a parent exit title, use `/ffa region message spawn exit ""`.

## Choosing child or standalone

Use a **child** for a different activity inside the main spawn: a market, mining area or arena. Select both corners inside spawn, then `/ffa region create child MiningYard`. The older `/ffa region create MiningYard` and `/ffa region child MiningYard` also create children. A child always takes precedence at its coordinates; its explicit flags win over the parent. Setting a flag to `inherit` removes that child's override and uses the parent's current value.

For a building area:

```text
/ffa region flag MiningYard build true
/ffa region flag MiningYard break true
/ffa region flag MiningYard hunger true
```

Players can build and mine there while the rest of spawn stays protected. Doors/containers still need their own permissions. Item and block allowlists belong to each region separately and are not inherited. Test with a nonadministrator account: full administrators bypass protection everywhere.

Use **standalone** for a separate protected place outside spawn. It has no parent, uses global `[defaults]` plus its own overrides, and does not force Adventure Mode. Enable `modules.standaloneRegions = true`, reload, select nonoverlapping corners and run `/ffa region create standalone Outpost`. The creation option remains visible when disabled and tells you which setting to enable. Neither child nor standalone creation replaces the main `/ffa spawn create` operation.

For example, bounds X 0..20 and Z 0..20 become X 2..18 and Z 2..18 after `/ffa region contract NAME 2`. `contract` changes the protected rectangle, not world blocks. `expand` moves the edges outward instead. Both validate the whole result before saving.

## Title appearance

```text
/ffa region message Marketplace entry "Entering {name}"
/ffa region message Marketplace exit "Leaving {name}"
/ffa region title Marketplace entry color gold
/ffa region title Marketplace entry bold true
/ffa region title Marketplace entry italic false
/ffa region title Marketplace exit color "#FF8800"
```

Use `entry` or `exit` with each style command. Colors accept Minecraft color names or `#RRGGBB`. Text, color, bold and italic can also be edited in `regions.toml`; existing custom messages are preserved on upgrade.
