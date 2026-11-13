# Warps and portals

[Wiki home](Home.md) · [Access rules](Permissions-and-Access.md) · [Configuration](Configuration.md)

A **warp** is a named arrival point. A **portal** is a passable trigger volume that travels to a named warp. Updating a shared warp updates every portal linked to it.

## Simplest portal setup

Select two opposite corners of the portal opening with `/ffa pos1` and `/ffa pos2`. Use a thin, upright rectangle: for example, three blocks wide, four tall and one block deep. Then:

```text
/ffa portal create town_gate
```

The portal becomes visible immediately. Stand on a safe full-block floor where players should arrive, face their intended direction, then:

```text
/ffa portal destination town_gate
```

FFA creates and links the `town_gate` warp for you. No separate warp-assignment step is required. An unconfigured portal shows its surface but does not teleport players. You must stand outside its own trigger volume when setting the destination.

Setting a destination requires both portal and warp management authority. If the portal already links to an existing warp, this command updates that warp's destination, including other portals that share it.

## Create a warp first, or reuse one

Stand at the arrival point:

```text
/ffa warp create marketplace
```

Select the portal opening, then:

```text
/ffa portal create market_gate to marketplace
```

The earlier `/ffa portal create market_gate marketplace` syntax also works. Creating a portal without a destination argument automatically links an already existing warp with the same name; otherwise, use the guided destination command above.

```text
/ffa warp marketplace
/ffa warps
/ffa setwarp marketplace
/ffa portal link market_gate marketplace
/ffa delwarp marketplace
```

`warp create` refuses duplicate names. `setwarp` deliberately updates an existing warp, retaining its access rule. Delete or relink portals before deleting a warp they use. Warp IDs remain lowercase letters, digits, underscores or hyphens, up to 48 characters.

## Animated, recolored portal surfaces

Portals now fill their opening with an animated translucent Nether-style surface, rather than corner glass markers. Sixteen dye colors are available:

```text
/ffa portal color town_gate red
/ffa portal color town_gate cyan
/ffa portal color town_gate purple
```

Administrators can also use a dye on a portal's interaction surface. Colors persist through restarts. Chunk-load reconciliation restores surfaces when their area loads; they do not generate or force-load terrain. Large selections use coarser tiles to keep the surface renderer bounded to 256 tiles per portal. Use compact openings for the clearest effect.

These are display surfaces, not real vanilla Nether portal blocks: FFA remains in control of the destination and does not accidentally send players to the Nether.

## The bundled resource pack

The mod JAR includes a small resource pack containing original animated textures and vanilla-compatible models. With portals enabled, Minecraft automatically offers it to joining players. **Players must accept the pack to see the recolored surfaces; no FFA client mod is needed.** If previously declined, edit the server entry in Multiplayer, enable Server Resource Packs, and reconnect.

Normally the pack downloads from a read-only route on the **existing Minecraft TCP port**. No additional HTTP port or separate web server is needed for direct/LAN connections. The route serves only the matching bundled ZIP and never exposes server files. Minecraft verifies the advertised SHA-1 and caches the download.

The matching ZIP is also extracted to `<world>/ffa/portal-pack/ffa-portals.zip`. If the server uses a protocol-specific proxy or hosting layer that will not forward HTTP on the game port, upload this exact ZIP to an HTTP(S) host and set its public URL:

```toml
[portalPack]
enabled = true
url = "https://example.org/ffa-portals.zip"
```

Edit the existing section rather than adding a duplicate, then `/ffa reload`. Leave `url = ""` for automatic same-port delivery. Do not edit the hosted ZIP independently: its bytes must match the bundled hash. Pack assets reserve CustomModelData **600000..600015** on the purple stained-glass item; its ordinary item/block appearance is retained. A custom pack overriding that same item model may need its model overrides merged.

Setting `portalPack.enabled = false` uses native animated purple portal surfaces with colored particles instead of requiring the custom pack. Vanilla cannot recolor that native purple texture. Disabling the portals module also stops pack offers and removes loaded FFA surfaces.

## Access and timing

```text
/ffa portal access town_gate public
/ffa portal access town_gate admin
/ffa portal access town_gate permission ffa.portal.town
/ffa portal access town_gate group residents
/ffa warp marketplace permission ffa.warp.market
```

Both portal and warp access must pass, along with region and destination-safety rules. For a warp automatically created by the guided workflow, FFA copies portal access to that warp and keeps them aligned when portal access changes. Existing manually linked warps retain their own independent access settings. Configure both when restricting an existing link.

`team` access uses the supported Mercenaries settlement context at the portal and destination respectively. Named-warp bans also block portal routes using those warps. Normal home/TPA/back routes remain separate.

Normal teleport cooldown defaults to three seconds; portal loop cooldown defaults to five seconds, including for administrators. Put destinations outside portal trigger volumes. Travel uses saved safe terrain; unknown terrain is not generated.

```text
/ffa portal delete town_gate
```

Deleting a portal removes its definition and loaded visuals, not world blocks or its warp. Delete/recreate a portal to change its trigger bounds. Selections are limited to 4096 blocks and the dimension's build height; portal volumes cannot overlap.

## Administrator-only portal creation

`portals.adminCreationOnly = true` defaults on. Full administrators can create supported vanilla Nether/End portals and new FFA portals. Delegating `ffa.portal.manage` alone does not override this creation policy. Set it false and reload to restore normal creation permissions, while retaining region restrictions.

For ordinary Nether travel, establish both ends as an administrator: a player's first trip cannot generate a missing destination portal while administrator-only creation is enabled. Custom mods bypassing vanilla/event/tag hooks need a verified adapter.
