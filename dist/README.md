# Development build 0.6.0

For the server only: Minecraft 1.21.1, NeoForge 21.1.262, Java 21.

Download the JAR using GitHub's Download raw file button, and place it in the server mods directory. See the root README for setup and docs/VALIDATION.md for the staging acceptance checklist. This is a development build.

193 policy/persistence/rank/chat/parser checks and the headless regression suite passed with the supplied Mercenaries 1.20.4, Easy NPC 7.18.0 and Easy NPC Config UI 7.18.0 installed together. FTB Ranks 2101.1.5 was checked against its public source API; the exact binary was not supplied for runtime testing.

SHA-256 is listed in SHA256SUMS. Third-party mods are not bundled.

The JAR bundles the animated colored portal resource pack and offers a standard required download while portals are enabled. Direct servers serve it over the existing Minecraft TCP port. Proxy/CDN configurations may need `portalPack.url`; see the wiki. No FFA client mod is required.

0.5.1: continuous tiled portal animation and configurable eight-second invincibility after login/death respawn. Set `spawn.invulnerabilitySeconds` in `config/ffa/server.toml`; 0 disables it. Accept the updated bundled resource pack for the new portal animation.

0.6.0: rectangular, per-dimension world borders. Use `/ffa pos1`, `/ffa pos2`, `/ffa worldborder set`. The wall appears only within `worldBorder.visibilityDistance` blocks (default 8). See the world-border wiki page.
