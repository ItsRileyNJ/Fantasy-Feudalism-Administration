# Region ambience

[Wiki home](Home.md) · [Regions](Regions.md) · [Configuration](Configuration.md)

FFA **0.9.0** can play custom village ambience or music inside an area, fade it in on entry, repeat it automatically, and fade it out on exit. **Install FFA only on the server.** Players download an ordinary audio resource pack; no FFA client mod is needed.

## Example: village ambience

1. Prepare a **mono Ogg Vorbis** file named `village.ogg`. A `.mp3` renamed to `.ogg` will not work. In an audio editor such as Audacity, mix the track to mono and export as Ogg Vorbis. Include any intended pauses in the recording and trim accidental silence from its ends.
2. Upload the file to **`config/ffa/sounds/village.ogg` on the server**. This folder is created when FFA starts. It is not inside the imported dimension's `region`, `entities`, or `poi` folders.
3. As an administrator, run:

   ```text
   /ffa sound reload
   /ffa sound list
   ```

4. Stand at one corner of the village and run `/ffa pos1`. Stand at its opposite corner and run `/ffa pos2`. These are the same selection commands used for protected regions. Both positions must be in the same dimension.
5. Create and adjust the sound region:

   ```text
   /ffa soundregion create Village village
   /ffa soundregion volume Village 0.6
   /ffa soundregion fade Village 3
   /ffa soundregion info Village
   ```

6. Accept the server's ambience resource-pack download on the test client. Walk outside the village and back in. The track fades in over **three seconds** to **60% volume**, repeats when its recorded duration ends, and fades out over three seconds when you leave. Check Minecraft's **Ambient/Environment** and master volume sliders if you hear nothing.

`Village` is the region's name; `village` is the file name without `.ogg`. Region names can contain uppercase letters and are looked up without case sensitivity. Track filenames use **lowercase letters, digits, underscores or hyphens**, up to 48 characters. Each file must last **1 second to 1 hour**, with at most **16 MiB per file**, **64 MiB total**, and **64 tracks**.

## Regions, overlap and changing tracks

You can create up to **256 sound regions**. Sound regions cover the **full build height** of their selected X/Z rectangle in that dimension. They are separate from protection regions: they can overlap spawn, its children, standalone regions, and Mercenaries settlements. Creating one does not change any protection flag, town permission, or ownership rule. Administrators hear ambience too.

Only the highest-priority sound region containing the player supplies the active track. With equal priorities, the smaller area wins; equal-size ties use region name order. A region with volume `0` creates a quiet overlay. To give an inn priority over the surrounding village:

```text
/ffa soundregion priority Inn 10
/ffa soundregion priority Village 0
```

Create `Inn` first using its own two selected corners and a loaded track. Different tracks crossfade on transitions; the same track continues without restarting. Re-entering while its exit fade is still running reverses that fade. At most four simultaneous fading tracks are retained per player during rapid transitions.

To change the village track, upload `village_night.ogg`, reload sounds, then run:

```text
/ffa soundregion track Village village_night
```

Deleting a sound region fades out its sound for players inside it. Delete and recreate a region to change its selected rectangle. Its definition, track, volume, fade and priority are saved in `<world>/ffa/state.json`; manage them through commands.

## Commands

All commands below are administrator-only and hidden from ordinary players.

| Command | Purpose |
| --- | --- |
| `/ffa sound reload` | Validate all `.ogg` files and rebuild the audio pack if content changed |
| `/ffa sound list` | List loaded track IDs |
| `/ffa soundregion create NAME TRACK` | Create a sound area from `/ffa pos1` and `/ffa pos2` |
| `/ffa soundregion list` | List sound regions |
| `/ffa soundregion info REGION` | Show its track, settings, dimension and corners |
| `/ffa soundregion track REGION TRACK` | Change its track |
| `/ffa soundregion volume REGION VALUE` | Set volume from `0` to `1`; default `0.6` |
| `/ffa soundregion fade REGION SECONDS` | Set both entry and exit fade; whole seconds `0`–`30`, default `3` |
| `/ffa soundregion priority REGION NUMBER` | Set overlap priority; default `0`, higher wins |
| `/ffa soundregion delete REGION` | Remove the sound area |

## Server configuration and downloads

These settings belong in the existing `config/ffa/server.toml`; add the section once if upgrading from an older file:

```toml
[soundRegions]
enabled = true
packUrl = ""
```

Run `/ffa reload` after changing these general settings. Use `/ffa sound reload` after adding, replacing or deleting audio files. Invalid sound reloads keep the previous working pack. On startup, invalid audio disables that audio catalog and logs the problem; it does not prevent the server from starting.

With `packUrl = ""`, FFA serves the generated pack over the **existing Minecraft TCP port**, using the hostname/port in the player's connection. No extra port is needed. The ambience pack itself is optional: declining it leaves region audio silent. Any separate mandatory portal resource pack or `require-resource-pack` policy in `server.properties` still applies; declining a required pack can disconnect the player. Their resource pack contains only audio and Minecraft metadata; installing it grants no server permissions.

The generated ZIP is also written to **`<world>/ffa/sound-pack/ffa-sounds.zip`**. If a proxy or host prevents direct downloads from the Minecraft port, publish that exact ZIP on an HTTP(S) host and put its public download URL in `packUrl`. After changing tracks, update the hosted ZIP before players download it; the advertised SHA-1 must match. Keep backups of the original `config/ffa/sounds/` files alongside FFA state.

Minecraft must receive an audio file to play it. Players can extract, replace or mute downloaded sounds; this cannot bypass server permissions or region protection. Use recordings you are allowed to distribute.

## Playback details and troubleshooting

- **Stereo files are rejected.** The server controls fades through Minecraft's positional attenuation, which requires mono audio. No audio decoder or external conversion program runs on your server.
- Each player's playback is private. Walking away from the village fades its track instead of broadcasting a sound from one fixed point to the whole server.
- Repeat timing uses the Vorbis recording length. Automatic repeats use vanilla sound packets and can have a small gap from network latency, client loading or server lag; sample-perfect seamless loops are not promised.
- Walking or teleporting out within the same dimension fades out normally. A dimension unload, disconnect, pack replacement or disabling audio stops that playback immediately; entering a sound area in the new dimension starts its own fade. Minecraft cannot preserve the old world's audio anchor through an unload.
- If downloading fails, check the public URL, hosting/proxy access and resource-pack settings. FFA waits until the client reports the pack loaded before starting audio. Reconnect after fixing a declined or failed download.
- If a track disappears after a reload, check its filename, mono/Vorbis format, duration and size in the server log. A region referencing a removed track stays saved but is silent until that track is restored or changed.
