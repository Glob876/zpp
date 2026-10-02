# Zombie Apocalipse++

**ZPP** is a configurable, server-side zombie-apocalypse mod for **Minecraft Java 1.20.1 / Fabric / Java 17**. Players can join using a regular 1.20.1 client; they do not need ZPP or Fabric API installed on their client. The mod also works in a local single-player world's integrated server when installed locally with Fabric API.

The Russian documentation is available in [README.ru.md](README.ru.md).

## Installation

1. Set up a Minecraft **1.20.1** server with Fabric Loader **0.15.11+** and Java **17+**.
2. Put `zombie-apocalipse-plus-plus-1.0.0+1.20.1.jar` and Fabric API for 1.20.1 into the server's `mods/` directory.
3. Start the server. It creates `world/zpp.json` on a dedicated server or `saves/<world name>/zpp.json` in single player.
4. Run `/zpp` or `/zpp help`. ZPP starts disabled; enable it with `/zpp toggle` or `/zpp enabled true`. Changing settings requires OP level 2.

The release JAR is `build/libs/zombie-apocalipse-plus-plus-1.0.0+1.20.1.jar`; the `-sources` JAR is not for the `mods/` directory.

## Highlights

- Uses four vanilla zombie types by default and accepts additional mobs by registry ID.
- Adds configurable day and night waves around survival/adventure players in the Overworld.
- Provides configurable spawn distance, caps, light checks, open-sky requirement, grace period, and a death cooldown.
- Supports progressive health, damage, and speed scaling by world day.
- Includes scheduled hordes and random blood moons.
- Announces each new day and all events with a centered full-screen vanilla title. A new-day title shows only the day number; it never reveals the zombie-strength percentage.
- Keeps persistent ZPP spawn and player-kill statistics.

## Quick start

```mcfunction
/zpp dayburn false
/zpp dayspawn amount 3
/zpp babies false
/zpp variants zombie 65
/zpp variants drowned 10
/zpp variants husk 15
/zpp variants zombie_villager 10
/zpp enabled true
/zpp status
```

ZPP is disabled by default. Once enabled, daytime waves remain disabled (`dayspawn amount = 0`) and night waves spawn two zombies per player every 15 seconds. `dayburn false` prevents the supported zombies from burning in sunlight; husks retain their vanilla immunity. Type weights apply only to ZPP waves, not vanilla biome spawning or mob conversions.

## Commands

Settings without a value display their current value. Configuration edits, manual events, manual spawning, `config`, and `reload` require OP level 2. Omit the leading `/` in the server console.

| Command | Purpose |
|---|---|
| `/zpp`, `/zpp status`, `/zpp help` | Status and help |
| `/zpp enabled <true\|false>` | Main ZPP switch |
| `/zpp toggle` | Toggle ZPP on or off |
| `/zpp dayburn <true\|false>` | Vanilla sunlight burning |
| `/zpp dayspawn [amount] <0..64>` | Day-wave spawn attempts |
| `/zpp nightspawn [amount] <0..64>` | Night-wave spawn attempts |
| `/zpp babies <true\|false>` | Allow baby variants |
| `/zpp babies chance <0..100>` | Baby chance in ZPP waves |
| `/zpp variants [type] [weight]` | View or set zombie, drowned, husk, and zombie_villager weights |
| `/zpp variants list` | List every mob in the wave and its weight |
| `/zpp variants add <entity_id> <weight>` | Add any registered mob to waves, with weight 0–1000 |
| `/zpp variants remove <entity_id>` | Remove a mob from waves |
| `/zpp spawn <type> <1..64> [player]` | Manually spawn a wave around a player |
| `/zpp spawn interval\|cap\|globalcap\|distance\|attempts\|maxlight\|opensky\|grace\|cooldown ...` | Spawn controls |
| `/zpp scaling <true\|false>` | Day-based attribute scaling |
| `/zpp scaling days\|health\|damage\|speed\|natural ...` | Scaling controls |
| `/zpp horde enabled\|start\|stop\|every\|duration\|multiplier ...` | Horde controls |
| `/zpp bloodmoon enabled\|start\|stop\|chance\|multiplier ...` | Blood-moon controls |
| `/zpp announcements <true\|false>` | Enable or disable full-screen announcements |
| `/zpp day`, `/zpp stats [player]` | World day and statistics |
| `/zpp preset <casual\|standard\|hardcore>` | Apply a balance preset |
| `/zpp preset list\|save <name>\|load <name>\|delete <name>` | Manage named presets shared across worlds |
| `/zpp config`, `/zpp reload` | Print the config path or reload it |

Manual spawning bypasses the grace period, death cooldown, and configured day/night amount, but still respects the master switch, difficulty, `doMobSpawning`, placement checks, and caps.
Changing settings or applying a built-in preset while ZPP is off keeps it off and prints a reminder. Each world's `zpp.json` is independent. The old shared `.minecraft/config/zpp.json` is no longer read; copy its values into a world's file if needed.

Named presets are stored in Fabric's global `config/zpp-presets.json`. `save` captures the complete current configuration, including `enabled` and the mob list; `load` applies it to the current world. Names use 1–32 lowercase letters, digits, `_`, or `-`. A mod providing a custom mob must be installed when its preset is applied. Existing built-in presets retain their partial-update behavior. Added mobs use the same wave caps and attribute scaling where supported; the baby setting applies only to zombie types.

## Defaults

| Setting | Default |
|---|---|
| Main switch | Off |
| Sunlight burning | Disabled |
| Day waves | Disabled (0) |
| Night waves | 2 zombies per player every 15 seconds |
| Spawn distance | 24–48 blocks |
| Nearby / global ZPP cap | 32 / 256 loaded mobs |
| Block-light limit | 7 |
| Hordes | Every 7th night, 120 seconds, ×3 waves |
| Blood moon | 15% per night, ×2 waves until dawn |
| Full scaling | Day 31: +150% health, +75% damage, +20% speed |

## Building and testing

```sh
./gradlew build
./gradlew runIntegrationTest
```

Use JDK 17. `build` runs unit tests; `runIntegrationTest` starts an isolated Fabric server and verifies commands, mixins, events, and spawning. See [docs/TESTING.md](docs/TESTING.md) for test notes.

## License and credits

Licensed under [GPL-3.0-only](LICENSE). Created by **Glob876**. ZPP is an independent implementation inspired by [Zombie Apocalypse Addon](https://www.curseforge.com/minecraft/mc-mods/zombieapocalypseaddon); no code or assets from that addon are included.
