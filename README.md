# KaramSMP

KaramSMP is a Paper 1.21.11 plugin made by Herex._.7.

It includes:

- `/gmsp` spectator/survival toggle
- Configurable TAB header/footer
- Rank prefixes and suffixes
- PlaceholderAPI support
- SQLite, MySQL, and YAML rank storage
- WorldGuard-style regions
- Animated configurable scoreboards from a `scoreboards/` folder

## Build

```bash
mvn clean package
```

The plugin jar will be created here:

```text
target/KaramSMP-0.2.jar
```

Put the jar in your server `plugins` folder and restart the server.

## Game mode command

```text
/gmsp
/gsmp
```

This command toggles the player:

- not spectator -> spectator
- spectator -> survival

Permission:

```text
karamsmp.helper.gmsp
```

## Reload command

```text
/reload
/ksmpreload
/karamsmpreload
```

Permission:

```text
karamsmp.reload
```

## Rank commands

```text
/rank list
/rank info <rank>
/rank set <player> <rank>
/rank clear <player>
/rank create <rank> <permission> <priority>
/rank remove <rank>
/rank setprefix <rank> <prefix>
/rank setsuffix <rank> <suffix>
/rank addperm <rank> <permission>
/rank delperm <rank> <permission>
```

Permission:

```text
karamsmp.admin.ranks
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
/region flags
/region flags <name>
/region flag <name> <flag> <true|false|reset>
/region owner add <region> <player>
/region owner remove <region> <player>
/region member add <region> <player>
/region member remove <region> <player>
/region priority <region> <priority>
/region redefine <region>
/region expand <region> <up|down|north|south|east|west|vertical> <amount>
/region contract <region> <up|down|north|south|east|west> <amount>
/region message <region> greeting <message>
/region message <region> farewell <message>
/region rename <old> <new>
/region tp <region>
/region save
/region reload
```

Main permission:

```text
karamsmp.regions.admin
```

Bypass permission:

```text
karamsmp.regions.bypass
```

### Region flags

```text
block-break
block-place
pvp
fall-damage
interact
chest-access
item-drop
item-pickup
mob-spawning
explosions
fire-spread
entry
exit
```

`true` means allowed and `false` means blocked.

Example spawn protection:

```text
/region wand
```

Select two corners, then run:

```text
/region create spawn
/region flag spawn block-break false
/region flag spawn block-place false
/region flag spawn pvp false
/region flag spawn fall-damage false
/region flag spawn explosions false
/region flag spawn fire-spread false
```

## Scoreboard system

Scoreboards are saved in:

```text
plugins/KaramSMP/scoreboards/
```

The project includes these default scoreboard files:

```text
scoreboards/default.yml
scoreboards/spawn.yml
scoreboards/staff.yml
scoreboards/nether.yml
```

Each file can target:

- all players
- one or more worlds
- one or more KaramSMP regions
- only players with a permission

The plugin chooses the matching scoreboard with the highest `priority`.

### Example scoreboard file

```yaml
id: "staff"
enabled: true
priority: 100
permission: "karamsmp.scoreboard.staff"
worlds: []
regions: []
title:
  frames:
    - "&c&lSTAFF"
    - "&4&lSTAFF MODE"
animation:
  title-speed-ticks: 8
  line-speed-ticks: 20
lines:
  - "&7&m----------------"
  - "&fName: &e%player%"
  - "&fRank: %karamsmp_ranks_prefix%"
  - "&fWorld: &e%world%"
  - "&fRegion: &e%karamsmp_region%"
  - "&7&m----------------"
animated-lines:
  5:
    - "&fRegion: &e%karamsmp_region%"
    - "&fScoreboard: &e%karamsmp_scoreboard%"
```

### Scoreboard commands

```text
/kscoreboard help
/kscoreboard reload
/kscoreboard list
/kscoreboard info <id>
/kscoreboard create <id>
/kscoreboard delete <id>
/kscoreboard enable <id>
/kscoreboard disable <id>
/kscoreboard setpermission <id> <permission|none>
/kscoreboard setpriority <id> <number>
/kscoreboard addworld <id> <world>
/kscoreboard removeworld <id> <world>
/kscoreboard addregion <id> <region>
/kscoreboard removeregion <id> <region>
/kscoreboard settitle <id> <text>
/kscoreboard addline <id> <text>
/kscoreboard setline <id> <line> <text>
/kscoreboard removeline <id> <line>
```

Permission:

```text
karamsmp.scoreboards.admin
```

### Permission-only scoreboards

Set this inside a scoreboard `.yml` file:

```yaml
permission: "karamsmp.scoreboard.vip"
```

Only players with that permission can see that scoreboard.

## Placeholders

Internal placeholders:

```text
%player%
%displayname%
%rank%
%prefix%
%suffix%
%online%
%max_players%
%world%
%gamemode%
%karamsmp_rank%
%karamsmp_ranks_prefix%
%karamsmp_ranks_suffix%
%karamsmp_saved_rank%
%karamsmp_rank_priority%
%karamsmp_rank_permission%
%karamsmp_rank_permissions%
%karamsmp_region%
%karamsmp_regions%
%karamsmp_region_count%
%karamsmp_scoreboard%
%karamsmp_scoreboard_id%
```

PlaceholderAPI placeholders also work if PlaceholderAPI is installed.

## Night Vision Toggle

Any player can use these commands without any permission:

- `/nightvision`
- `/nv`

Running the command once gives the player night vision. Running it again removes night vision.

Messages and visual options are editable in `config.yml`:

```yaml
night-vision:
  enabled: true
  enabled-message: "&aNight vision enabled."
  disabled-message: "&cNight vision disabled."
  command-disabled-message: "&cThe night vision command is disabled."
  only-players-message: "&cOnly players can use this command."
  amplifier: 0
  show-particles: false
  show-icon: true
  ambient: false
```
