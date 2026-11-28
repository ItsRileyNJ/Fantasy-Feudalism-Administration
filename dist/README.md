# Development build 0.11.2

For the server only: Minecraft 1.21.1, NeoForge 21.1.262, Java 21.

Download the JAR using GitHub's Download raw file button, and place it in the server mods directory. See the root README for setup and docs/VALIDATION.md for the staging acceptance checklist. This is a development build.

255 policy/persistence/rank/chat/parser checks and the headless regression suite passed with the supplied Mercenaries 1.20.4 and 1.20.5 alongside Easy NPC core/UI/bundle 7.20.0. Easy NPC 7.18.0 compatibility was also verified in 0.8.2. FTB Ranks 2101.1.5 was checked against its public source API; the exact binary was not supplied for runtime testing.

SHA-256 is listed in SHA256SUMS. Third-party mods are not bundled.

The JAR bundles the animated colored portal resource pack and offers a standard required download while portals are enabled. Direct servers serve it over the existing Minecraft TCP port. Proxy/CDN configurations may need `portalPack.url`; see the wiki. No FFA client mod is required.

0.5.1: continuous tiled portal animation and configurable eight-second invincibility after login/death respawn. Set `spawn.invulnerabilitySeconds` in `config/ffa/server.toml`; 0 disables it. Accept the updated bundled resource pack for the new portal animation.

0.6.0: rectangular, per-dimension world borders. Use `/ffa pos1`, `/ffa pos2`, `/ffa worldborder set`. The wall appears only within `worldBorder.visibilityDistance` blocks (default 8). See the world-border wiki page.

0.6.1: fixes cross-dimension `/execute ... run tp` validation, relative-coordinate validation and misleading native success feedback after FFA blocks a teleport. Existing configuration and world data remain compatible.

0.7.0: configurable combat travel restriction and personal `/ffa pvp` on/off/status with a persistent five-minute change cooldown. See the Combat and PvP wiki page for configuration and administrator behavior.

Includes administrator `/ffa tphere PLAYER`; this can bring a combat-tagged player without clearing their combat timer.

0.8.0: rules acceptance child room and separate first-join spawn, plus explicit Denar prices in Easy NPC 7.18.0 native trading menus. See the new wiki guides for setup. Both features start inactive until configured.

0.8.1: walk naturally into FFA portals from either side or at an angle. Full-body contact is checked every tick and portal chunk loading tolerates movement. Landing inside another portal does not bounce you back when the cooldown expires. Command warmups, movement cancellation, access restrictions and destination safety remain enforced.

0.8.2: supports Easy NPC 7.20.0 core, Config UI and the supplied bundle, while retaining 7.18.0 support. Install the separate core and Config UI JARs: the supplied small bundle contains no embedded copies of either. Existing Denar prices and stock records require no FFA migration.

0.8.3: accepts the verified Mercenaries 1.20.5 settlement API and retains 1.20.4 support. Fixes the “Unverified Mercenaries version: 1.20.5” startup failure. Its housing-restock toggle stays under Mercenaries’ control; claimed-town region checks remain active. No region override or town migration is needed merely for the version upgrade.

0.9.0: server-only sound-region ambience with resource-pack audio, fades and automatic repetition. See the Sound Regions wiki guide.

0.10.0: native FTB Quests Denar Custom Tasks/Rewards, with individual or single-team claims and durable wallet receipts. See the FTB Quests wiki guide for the exact supported dependency versions.

0.10.1: Items 0.1.7 placed lantern fuel/weather protection, controlled by `lantern_burnout` (false by default).

0.10.2: locks armor-stand and Supplementaries hat-stand equipment behind both `entity_interact` and `containers` at the player and target. `hanging_damage` also covers hat-stand attacks/projectiles. Default spawn flags protect both stands; administrators remain exempt. Verified with Supplementaries 1.21.1-4.1.4 and Moonlight 1.21.1-3.8.5, separately and alongside the supplied Items/FTB/Easy NPC/Mercenaries mods. Replace the old FFA JAR with this version and restart; no new region flag or data migration is needed. See the Flags and Interactions wiki guide for a child wardrobe example.

0.11.0: persistent administrator player mannequins/holograms, with saved skins, copied visual equipment and independently styled names/titles. Use `/ffa mannequin create DISPLAY [PLAYER]`; `/ffa hologram` is an alias. No FFA client JAR or extra resource pack is required. The new `weather` flag defaults false in protected regions and stops natural snow/ice, precipitation block callbacks and natural lightning. Visible rain/snow remains unchanged, existing snow/ice is retained, and children can allow weather explicitly. See the Mannequins and Holograms and Flags and Interactions wiki guides. Replace the previous FFA server JAR and restart; no manual state conversion is needed.

0.11.1: administrator travelers automatically load existing chunks or generate missing destination chunks for FFA/vanilla TP, including cross-dimension commands. Saved blocks are preserved; bounds, hazards and collisions are still checked. Ordinary players and automation retain their restrictions, and RTP remains loaded-only. No config migration or manual force-load is needed. See the Staff Tools wiki guide.

0.11.2: protects lit vanilla/soul lanterns from automatic storm shutoff during scheduled lantern ticks, with normal rescheduling and manual administrator switches preserved. Adds verified Additions 0.1.25 fuel/weather support alongside Items 0.1.7. Keep `lantern_burnout = false` in the effective protected region; relight already extinguished lanterns once. Replace the old server FFA JAR and restart. No new setting, FFA client installation, or fuel refill is needed.
