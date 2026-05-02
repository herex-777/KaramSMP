# KaramSMP

KaramSMP is a Paper 1.21.11 plugin made for Herex._.7.

Version: `0.6`  
Group ID: `me.herex`  
Artifact ID: `karmsmp`

## Main features

- `/gmsp` or `/gsmp` toggles spectator/survival with `karamsmp.helper.gmsp`.
- `/nightvision` or `/nv` toggles Night Vision for every player with no permission needed.
- Configurable TAB header/footer with rank priority sorting.
- Rank prefix/suffix system with saved player ranks.
- PlaceholderAPI hook and internal placeholders.
- SQLite, MySQL, or YAML rank storage.
- WorldGuard-style cuboid regions with flags.
- Scoreboards from `plugins/KaramSMP/scoreboards/*.yml`.
- Animated scoreboard titles and animated lines.
- Hex color support everywhere KaramSMP formats text.
- Custom join/quit messages.
- Configurable `/discord` command.

## Build

```bash
mvn clean package
```

The built plugin will be:

```text
target/KaramSMP-0.6.1.jar
```

Put the JAR into your server `plugins` folder and restart the server.

## Hex colors

KaramSMP supports these RGB formats in `config.yml`, TAB, chat, ranks, join messages, and scoreboards:

```yaml
"&#00D5FFBlue text"
"#00D5FFBlue text"
"<#00D5FF>Blue text"
```

Legacy colors still work too:

```yaml
"&b&lKaram SMP"
```

## Scoreboards

Scoreboard files are stored in:

```text
plugins/KaramSMP/scoreboards/
```

Default files included:

```text
default.yml
spawn.yml
staff.yml
nether.yml
```

The default scoreboard is DonutSMP-inspired and uses these symbols:

```text
$ Money
✦ Shards
⚔ Kills
☠ Deaths
⏳ Keyall
◷ Playtime
```

Useful placeholders:

```text
%kills%
%deaths%
%playtime%
%playtime_ticks%
%ping%
%karamsmp_kills%
%karamsmp_deaths%
%karamsmp_playtime%
%karamsmp_ping%
%karamsmp_ranks_prefix%
%karamsmp_ranks_suffix%
%karamsmp_region%
```

Line animations use `||` inside one line:

```yaml
lines:
  - "&#00FF66Online||&#FFFF55Online"
```

## Scoreboard commands

Base command: `/kscoreboard`  
Aliases: `/scoreboards`, `/sb`, `/karamsmpscoreboard`  
Permission: `karamsmp.scoreboards.admin`

- `/kscoreboard reload`
- `/kscoreboard list`
- `/kscoreboard info <id>`
- `/kscoreboard create <id>`
- `/kscoreboard delete <id>`
- `/kscoreboard enable <id>`
- `/kscoreboard disable <id>`
- `/kscoreboard setpermission <id> <permission|none>`
- `/kscoreboard setpriority <id> <number>`
- `/kscoreboard addworld <id> <world>`
- `/kscoreboard removeworld <id> <world>`
- `/kscoreboard addregion <id> <region>`
- `/kscoreboard removeregion <id> <region>`
- `/kscoreboard settitle <id> <text>`
- `/kscoreboard addline <id> <text>`
- `/kscoreboard setline <id> <line> <text>`
- `/kscoreboard removeline <id> <line>`

## Rank commands

Base command: `/rank`  
Aliases: `/ranks`, `/ksmprank`  
Permission: `karamsmp.admin.ranks`

- `/rank help`
- `/rank list`
- `/rank info <rank>`
- `/rank set <player> <rank>`
- `/rank clear <player>`
- `/rank create <rank> <permission> <priority>`
- `/rank remove <rank>`
- `/rank setprefix <rank> <prefix>`
- `/rank setsuffix <rank> <suffix>`
- `/rank addperm <rank> <permission>`
- `/rank delperm <rank> <permission>`

## Region wand

Use:

```text
/region wand
```

Then:

- Left click a block to set position 1.
- Right click a block to set position 2.
- Run `/region create <name>`.

The wand is configurable in `config.yml` under `regions.wand`.

## Region flags

`true` means allowed. `false` means blocked.

- `block-break`
- `block-place`
- `pvp`
- `fall-damage`
- `interact`
- `chest-access`
- `item-drop`
- `item-pickup`
- `mob-spawning`
- `explosions`
- `fire-spread`
- `entry`
- `exit`

Example protected spawn:

```text
/region flag spawn block-break false
/region flag spawn block-place false
/region flag spawn pvp false
/region flag spawn fall-damage false
```

## Database config

```yaml
database:
  type: "sqlite" # sqlite, mysql, or yaml
```

SQLite is the default and creates:

```text
plugins/KaramSMP/player-ranks.db
```


## 0.6.1 compatibility fix

Removed Adventure/Paper-only chat classes from the chat listener and switched to Bukkit AsyncPlayerChatEvent so the plugin can load on servers that do not provide net.kyori.adventure.text.Component at runtime.
