# KaramSMP

Paper 1.21.11 plugin for KaramSMP.

## Features

- `/gmsp` / `/gsmp` spectator toggle
- `/nightvision` / `/nv` night vision toggle
- Configurable TAB header/footer with hex colors and styled Unicode text support
- Rank prefixes/suffixes, rank permissions, and database-backed assigned ranks
- SQLite/MySQL/YAML rank storage
- Configurable chat format
- Join/quit messages
- `/discord`, `/guide`, and `/store` info commands
- Region protection system with wand selections and flags
- Region `double-jump` flag for spawn-style double jump areas
- Spawn system with `/setspawn` and first-join teleport
- Animated multi-file scoreboards in `plugins/KaramSMP/scoreboards/`
- DonutSMP-style homes system with GUI, gray beds, confirm-delete menu, teleport countdown, movement cancel, and database-backed home saving

## Build

```bash
mvn clean package
```

The built jar will be:

```text
target/KaramSMP-0.9.jar
```

## Home commands

```text
/home
/home <name>
/sethome <name>
/delhome <name>
```

`/home` opens the GUI. Gray beds teleport to saved homes or create free slots. Delete dye opens a confirm-delete menu. Confirming plays the configured XP level-up sound; canceling plays the configured pressure-plate sound and returns to the homes GUI.

Homes use `homes.storage.type: same-as-database` by default, so they save into SQLite or MySQL based on `database.type`. Set it to `yaml` if you want `homes.yml` instead.

## Spawn commands

```text
/spawn
/setspawn
```

`/setspawn` requires `karamsmp.spawn.set`. New players teleport to the saved spawn on first join when `spawn.teleport-first-join` is enabled.

## Double jump

Set a region to allow double jump:

```text
/region flag spawn double-jump true
```

You can also allow double jump around the saved spawn location with:

```yaml
double-jump:
  spawn:
    enabled: true
    radius: 50.0
```

## Region commands

```text
/region wand
/region pos1
/region pos2
/region create <name>
/region delete <name>
/region list
/region info <name>
/region here
/region flag <name> <flag> <true|false|reset>
/region owner add|remove <region> <player>
/region member add|remove <region> <player>
/region priority <region> <priority>
/region redefine <region>
/region expand <region> <direction> <amount>
/region contract <region> <direction> <amount>
/region message <region> greeting|farewell <message|clear>
/region tp <region>
/region save
/region reload
```
