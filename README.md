# Fantasy Feudalism: Administration

A server-side NeoForge 21.1.262 mod for Minecraft 1.21.1 and Java 21. Players do not need this administration mod installed. Other installed mods may have their own client requirements.

**Development release 0.1.2.** Read [validation and limits](docs/VALIDATION.md) before deployment. Use a copy of your server world for the first installation.

## Installation

1. Put the built `fantasy-feudalism-administration-0.1.2.jar` in the **server's** `mods` directory.
2. Start the server. General settings are created in `config/ffa/server.toml`. World data is stored in `<world>/ffa/`.
3. As an operator, stand at one corner of spawn and run `/ffa pos1`. Stand at the opposite corner and run `/ffa pos2`. Run `/ffa spawn create`.
4. Stand at a safe arrival location with solid ground and run `/ffa spawn setpoint`.
5. Players use `/ffa spawn`. All commands are under `/ffa`; vanilla, FTB Essentials and Mercenaries keep their own command roots.

Every region covers the world's full build height. Spawn children must lie entirely within the main spawn. Sibling and standalone overlaps are rejected. A region touching a claimed Mercenaries settlement is rejected unless its creator explicitly adds `override`; configuration uses `settlementOverride = true`.

Administrators bypass gameplay restrictions, Adventure Mode, home limits and command teleport warmups/cooldowns. Portal loop protection still applies. Inventory, balance and destination-safety validation always apply. Automatic world effects without an administrator actor remain controlled by region flags.

Spawn defaults freeze hunger/saturation and block building, breaking, block/container interactions, PvP, player damage, ordinary entity interactions, natural mob spawning, fire, explosions, fluid/piston changes, automation, flight and elytra flight for nonadministrators. NPC trading and settlement management remain enabled. Explicit child flags override inherited settings; Adventure Mode stays mandatory for nonadministrators throughout spawn and its children.

See [commands](docs/COMMANDS.md), [configuration and recovery](docs/CONFIGURATION.md), and [compatibility](docs/COMPATIBILITY.md).

## Build and verify

```sh
./gradlew check build
./gradlew runGameTestServer
# Optional local integration JARs, never packaged in the result:
./gradlew runGameTestServer -PcompatModsDir=/absolute/path/to/test-mods
```

`check` runs the standalone policy/persistence assertions. The GameTest server runs opt-in tests with real players, saves, event handlers and mixins. Tests are registered only when the development run sets `ffa.tests=true`.

No uploaded third-party JARs are included in this repository or release.
