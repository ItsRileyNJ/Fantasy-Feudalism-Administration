# Flags and interactions

[Wiki home](Home.md) · [Regions](Regions.md) · [Permissions](Permissions-and-Access.md)

**`true` allows an action; `false` blocks it or freezes its decrease.** `inherit` removes an explicit region override. Spawn and standalone regions use `[defaults]`; children first inherit from their spawn parent.

```text
/ffa region flag marketplace pvp false
/ffa region flag marketplace hunger false
/ffa region flag marketplace pvp inherit
```

## Flag reference

| Flag | Default | Controls |
| --- | --- | --- |
| `build` | `false` | Player block placement; children that enable this restore the prior game mode. |
| `break` | `false` | Player block destruction; children that enable this restore the prior game mode. |
| `interact` | `false` | Ordinary block interaction, subject to explicit allowlists and other checks. |
| `containers` | `false` | Inventory/menu and supported item-handler block access, including armor-stand and supported hat-stand equipment. |
| `entity_interact` | `false` | Ordinary non-trading entity interaction. Display stand equipment also requires `containers`. |
| `npc_trading` | `true` | Supported NPC and villager trading interactions. |
| `settlement_management` | `true` | Supported Mercenaries charter/empty-hand bell management, with its own ownership checks. |
| `hunger` | `false` | Allow hunger/saturation decrease; false freezes their decrease. |
| `damage` | `false` | Damage taken by ordinary players. |
| `pvp` | `false` | Players damaging players; both applicable sides must allow PvP. |
| `tnt` | `false` | TNT placement; building permission is also required. |
| `pearls` | `false` | Ender-pearl use and supported pearl travel at protected endpoints. |
| `totems` | `false` | Totem of Undying activation. |
| `fire` | `false` | Supported fire placement, ticking, burning and spreading. |
| `lantern_burnout` | `false` | With Items 0.1.7, allow placed lanterns to consume fuel and go out from weather. False pauses these effects. Manual switches retain ordinary interaction permissions. |
| `mob_spawning` | `false` | Ordinary new mob spawning, with supported NPC/admin-origin exceptions. |
| `hostile_spawning` | `false` | Hostile spawning; mob_spawning must also allow the spawn. |
| `mob_griefing` | `false` | Supported mob-griefing behavior; not a universal mod-machine hook. |
| `explosions` | `false` | Supported explosion effects at affected blocks/entities. |
| `pistons` | `false` | Supported piston movement, including affected source/target positions. |
| `fluids` | `false` | Supported fluid flow/placement effects across boundaries. |
| `automation` | `false` | Supported fake-player/nonplayer automation checks, alongside build/break rules. |
| `dispensers` | `false` | Supported dispenser actions at source/adjacent target. |
| `hanging_damage` | `false` | Protected hanging/entity decorations, armor stands and supported Supplementaries hat stands: attacks/projectiles, separate from equipment access. |
| `animal_damage` | `false` | Supported damage to animals; applies beyond only owned animals. |
| `flight` | `false` | Player flight and elytra flight; false disables them for nonadministrators. |
| `teleport_in` | `true` | Supported arrivals into the region. |
| `teleport_out` | `true` | Supported departures from the region; does not block walking. |
| `farming` | `false` | Planting, harvesting and bone-mealing supported crops, including stems, nether wart, cocoa, sugar cane, berries, melons and pumpkins; independent of build/break. Also controls player farmland trampling. |
| `tilling` | `false` | Hoe soil transformations, independently of building. |
| `strip_wood` | `false` | Axe stripping of logs/wood, independently of building. |
| `dirt_path` | `false` | Shovel conversion of soil into paths, independently of building. |
| `item_drop` | `false` | Voluntary Q/stack drops, inventory throws/outside-click drops and creative discard; blocked actions preserve inventory/cursor contents. |
| `item_pickup` | `false` | Picking up ground item entities; both the player and item positions must allow it. |
| `catch_fire` | `false` | Catching fire; false also extinguishes existing burning when entering. Does not grant immunity to direct lava/fire contact damage; use `damage` for that. |
| `vehicle_enter` | `false` | Boarding boats and minecarts; disembarking remains possible. Both rider and vehicle positions must allow boarding. |
| `flint_and_steel` | `false` | Using flint and steel/fire charges; the `fire` flag must also permit ignition. Administrator-only portal creation remains a separate setting. |
| `shears` | `false` | Shearing entities, pumpkin carving, beehive harvesting and supported shear tool transformations. Ordinary mining still follows break/farming flags. |
| `home_creation` | `false` | Creating/updating homes with the native FFA home system. |

The table describes FFA's supported hooks. Mods that bypass those hooks need verified compatibility adapters; do not assume universal automation protection.

## Allow only chosen doors, buttons, and levers

Leave `interact = false` and allow specific block IDs:

```text
/ffa region interaction spawn minecraft:oak_door allow
/ffa region interaction spawn minecraft:oak_trapdoor allow
/ffa region interaction spawn minecraft:lever allow
/ffa region interaction spawn minecraft:stone_button allow
```

Remove an exception with:

```text
/ffa region interaction spawn minecraft:lever remove
```

Use the **block ID**, not its display name. Variants are separate IDs. An oak-door exception does not automatically include an iron door or every modded door.

The current block allowlist is per region. If you need the same exception in a child, add it to the child too; the interaction allowlist is not inherited like flags. With building blocked, operating an allowed block does not also authorize using a held building item on it.

## Allow a particular chest

The allowlist selects block **types**, not individual coordinates. To allow a specific chest, make a suitably small child region around it, then:

```text
/ffa region interaction storage_counter minecraft:chest allow
/ffa region flag storage_counter containers true
```

Create/select `storage_counter` first. This allows matching chest blocks inside that child. Container access has its own check; allowing an interaction alone does not override `containers = false`. Alternatively, `interact = true` permits ordinary block interactions throughout the selected region, which is broader.

## Combat and game modes

For a PvP arena, allow both `pvp` and `damage`. An attack can still be refused when the attacker is in a region that forbids PvP. Animal/hanging-entity protections are separate flags.

Children explicitly allowing `build` or `break`, and regions enabling specific farming/tool editing permissions, restore the prior game mode. Other child flags still apply individually. Operators and `ffa.admin` users bypass gameplay restrictions; use a nonadministrator to test.

Freezing hunger does not automatically refill an empty food bar. A permitted-flight region does not guarantee a safe landing after you leave it.

## NPCs and environmental effects

`npc_trading` and `settlement_management` default to true despite ordinary interactions being blocked. They preserve the supported NPC/trading and bell-management paths; the other mod still checks its own ownership and permissions.

Environmental flags govern supported world effects. An unattributed fluid flow or fire tick does not become allowed merely because an administrator is nearby. Existing residents loaded from disk are not deleted by the natural-spawn restriction. See [compatibility](Mod-Compatibility.md) for settlement workflows that spawn vanilla villagers and for automation limits.

## Allow usable items in a region

```text
/ffa region item Marketplace minecraft:bread allow
/ffa region item Marketplace minecraft:ender_pearl allow
/ffa region item Marketplace minecraft:bread remove
```

Each region has an `items` list in `regions.toml`, empty by default. Add food, tools or other usable items by registry ID. Children use their own list rather than inheriting the parent's list. Full administrators bypass it.

This permits item use without opening every door, chest or button. Block interactions have their separate `interactions` list and flags. Allowed block placement follows `build`; a child with building enabled can use block items without listing every building material. Specific restrictions such as `pearls` or spawn-egg rules still apply, and town/mod rules can impose additional checks. Jail confinement still blocks items.

## Projectiles crossing a boundary

A shot fired outside spawn still checks the **player's effective region at impact**. With `damage = false`, player, mob and ownerless/dispenser projectiles cannot damage players inside. When damage is enabled but `pvp = false`, player-fired projectiles remain blocked. Both the shooter's and victim's PvP rules must permit player combat.

Blocked direct hits are cancelled before the projectile's fire, tipped-arrow effects, knockback or other hit processing runs. Projectiles are retained rather than deleted, so a blocked thrown trident is not destroyed. The ordinary damage handler also checks the victim's region for damage paths that do not use the projectile-impact hook, including splash/explosion health damage.

A child arena explicitly allowing both `damage` and `pvp` still allows combat; inheritance otherwise retains the parent's rules. Full administrators retain their existing gameplay exemption. No additional configuration is needed for default protected spawn.

## A working farm inside protected spawn

Select a child region as described in [Regions](Regions.md), then:

```text
/ffa region flag FarmYard farming true
/ffa region flag FarmYard tilling true
```

Players can plant supported crop blocks, harvest those crops and apply bone meal while `build`, `break` and `interact` remain false. Tilling is separate so you can permit tending an established farm without allowing soil changes. Ordinary stone/wood construction or destruction remains blocked. World editing permissions restore the player's previous game mode so vanilla Adventure restrictions do not swallow the explicitly allowed action. This also applies when enabling these specific action flags on the main spawn.

For other independent permissions:

```text
/ffa region flag Workshop strip_wood true
/ffa region flag Workshop dirt_path true
/ffa region flag Workshop shears true
/ffa region flag Spawn item_drop false
/ffa region flag Spawn item_pickup true
/ffa region flag Spawn catch_fire false
/ffa region flag Station vehicle_enter true
/ffa region flag Workshop flint_and_steel true
/ffa region flag Workshop fire true
```

Dedicated action flags permit the relevant tool/seed use without needing an ordinary item allowlist entry. Allowlisting an item or block cannot bypass a denied dedicated action. Other transformations, such as axe scraping/wax removal or shovel campfire extinguishing, still need general building permission. Container vehicles also retain container protection; boarding permission does not grant storage access.

All ten new flags default to false in protected areas, including when loading older configuration files. Outside protected areas normal behavior continues. Children inherit each flag unless explicitly overridden, and administrators retain their exemption. Add optional keys under your existing `[defaults]` section to change the server-wide baseline, or use region commands for local exceptions.

`flight` already controls elytra launch and ongoing gliding, so no duplicate elytra flag is needed. Disabling it stops gliding when entering protection. `fire` controls world fire; `catch_fire` controls burning players; `flint_and_steel` controls ignition tools.

`item_drop` targets voluntary player drops. Death drops and Minecraft-forced inventory cleanup are retained rather than deleting items. `item_pickup` governs ground item pickups, not shop transactions or direct administrator inventory edits. Mod features that bypass vanilla/NeoForge hooks need their own verified adapters.

## Keep spawn lanterns lit

With **FFA 0.10.1** and **Fantasy Feudalism Items 0.1.7**, placed vanilla lanterns in protected areas keep their current fuel and cannot be switched off by the Items weather handler. This applies automatically to existing spawn regions and the enabled spawn dimension, with `lantern_burnout = false`. Standalone protected regions also use that default. Children inherit their parent's rule.

The fuel timer **pauses at the remaining amount**. It resumes normally when protection is removed or burnout is allowed. Nothing is refilled automatically. Administrators can still use the Items mod's normal lantern switches, and ordinary player interactions retain their existing region permissions. Already unlit or empty lanterns need refueling/relighting once through the Items controls.

To allow the Items mod's ordinary lantern fuel/weather behavior in a child such as a survival area:

```text
/ffa region flag survival_area lantern_burnout true
```

To pause it there again, or restore parent inheritance:

```text
/ffa region flag survival_area lantern_burnout false
/ffa region flag survival_area lantern_burnout inherit
```

The server-wide protected-area default is in the existing `[defaults]` section of `config/ffa/server.toml`:

```toml
[defaults]
lantern_burnout = false
```

Existing files missing this new key use `false` automatically. This controls **placed lanterns**, including soul lanterns; torches and carried/dropped lights keep their normal Items behavior. FFA's `fire` flag remains separate. The adapter is enabled only for the inspected Items **0.1.7** layout; other Items versions need verification. The Items JAR and clients need no FFA-specific changes. The protection reuses existing checks on loaded lights and does not scan or force-load chunks.

## Protect armor stands and hat stands

From **FFA 0.10.2**, vanilla armor stands and **Supplementaries 1.21.1-4.1.4** hat stands share the same protection:

- `entity_interact = false` blocks using the stand.
- `containers = false` independently blocks removing, equipping or swapping its gear, even if ordinary entity interaction is allowed.
- `hanging_damage = false` blocks player attacks and projectile impacts. It does not authorize or replace equipment-access checks.

Both equipment flags must allow access at **the player's position and the stand's position**. Reaching across a region boundary cannot bypass a locked display. The defaults already protect spawn and standalone regions, and children inherit them. Nonadministrator Creative players are also restricted; administrators retain their exemption. This does not change Easy NPC dialogue/trading permissions.

To explicitly lock existing displays in a region named `Spawn`:

```text
/ffa region flag Spawn entity_interact false
/ffa region flag Spawn containers false
/ffa region flag Spawn hanging_damage false
```

To let players use a wardrobe inside spawn, create a child named `Wardrobe` around the stand and enough space for the player, then:

```text
/ffa region flag Wardrobe entity_interact true
/ffa region flag Wardrobe containers true
```

Leave `hanging_damage = false` to keep the stand safe from attacks. Allowing only `containers` or only `entity_interact` is insufficient. Block/item allowlists do not unlock a display stand. Outside protected areas, normal equipment interactions continue.

The verified hat-stand adapter uses **Supplementaries 1.21.1-4.1.4** with **Moonlight 1.21.1-3.8.5**; Supplementaries requires Moonlight 3.8.3 or later. No FFA client installation is required. The direct private equipment hook is enabled only for that inspected Supplementaries version; other releases require verification.
