# KaramSMP

KaramSMP is a Paper 1.21.11 plugin made for Herex._.7.

Version: `0.2`
Group ID: `me.herex`
Artifact ID: `karmsmp`

## Features

- `/gmsp` toggle command with `karamsmp.helper.gmsp` permission: switches to spectator, then back to survival when used again.
- Configurable TAB header/footer.
- Rank prefix/suffix system.
- PlaceholderAPI hook.
- SQLite, MySQL, or YAML rank storage.
- WorldGuard-style regions with selectable cuboids and protection flags.

## Build

```bash
mvn clean package
```

The built plugin will be:

```text
target/KaramSMP-0.2.jar
```

Put the JAR into your server `plugins` folder and restart the server.

## Database config

`config.yml` supports:

```yaml
database:
  type: "sqlite" # sqlite, mysql, or yaml
```

SQLite is the default and creates `plugins/KaramSMP/player-ranks.db`.

## Rank commands

Base command: `/rank`
Aliases: `/ranks`, `/ksmprank`
Permission: `karamsmp.admin.ranks`

- `/rank help` - Shows rank help.
- `/rank list` - Lists all ranks.
- `/rank info <rank>` - Shows rank prefix, suffix, priority, permission, and granted permissions.
- `/rank set <player> <rank>` - Saves a player's rank.
- `/rank clear <player>` - Removes a saved player rank.
- `/rank create <rank> <permission> <priority>` - Creates a rank in `config.yml`.
- `/rank remove <rank>` - Removes a rank from `config.yml`.
- `/rank setprefix <rank> <prefix>` - Changes the prefix.
- `/rank setsuffix <rank> <suffix>` - Changes the suffix.
- `/rank addperm <rank> <permission>` - Adds a permission to a rank.
- `/rank delperm <rank> <permission>` - Removes a permission from a rank.

## Placeholders

These work inside KaramSMP config text:

- `%player%`
- `%displayname%`
- `%rank%`
- `%prefix%`
- `%suffix%`
- `%online%`
- `%max_players%`
- `%world%`
- `%gamemode%`
- `%karamsmp_rank%`
- `%karamsmp_ranks_prefix%`
- `%karamsmp_ranks_suffix%`
- `%karamsmp_saved_rank%`
- `%karamsmp_rank_priority%`
- `%karamsmp_rank_permission%`
- `%karamsmp_rank_permissions%`
- `%karamsmp_region%`
- `%karamsmp_regions%`
- `%karamsmp_region_count%`

If PlaceholderAPI is installed, these can also be used through PlaceholderAPI as `%karamsmp_*%` placeholders.

## Region system

Regions are cuboids saved in:

```text
plugins/KaramSMP/regions.yml
```

The region system protects areas using flags. `true` means the action is allowed. `false` means the action is blocked.

Example:

```text
/region flag spawn block-break false
/region flag spawn pvp false
/region flag spawn fall-damage false
```

## Region commands

Base command: `/region`
Aliases: `/regions`, `/rg`
Admin permission: `karamsmp.regions.admin`
Bypass permission: `karamsmp.regions.bypass`

### Selection and creation

- `/region wand` - Gives the region wand.
- Left click a block with the wand - Sets position 1.
- Right click a block with the wand - Sets position 2.
- `/region pos1` - Sets position 1 to your current block.
- `/region pos2` - Sets position 2 to your current block.
- `/region create <name>` - Creates a region from your selection.
- `/region redefine <name>` - Replaces a region's area with your current selection.

### Region management

- `/region delete <name>` - Deletes a region.
- `/region rename <old> <new>` - Renames a region.
- `/region list [page]` - Lists regions.
- `/region info <name>` - Shows region info.
- `/region here` - Shows regions at your current location.
- `/region tp <name>` - Teleports to the center of a region.
- `/region save` - Saves `regions.yml`.
- `/region reload` - Reloads `regions.yml`.

### Flags

- `/region flags` - Lists all available flags.
- `/region flags <name>` - Shows flags for one region.
- `/region flag <name> <flag> <true|false>` - Sets a flag.
- `/region flag <name> <flag> reset` - Resets a flag to config defaults.

Available flags:

- `block-break` - Allow or block block breaking.
- `block-place` - Allow or block block placing.
- `pvp` - Allow or block PvP.
- `fall-damage` - Allow or block fall damage.
- `interact` - Allow or block block interaction.
- `chest-access` - Allow or block containers.
- `item-drop` - Allow or block dropping items.
- `item-pickup` - Allow or block picking up items.
- `mob-spawning` - Allow or block mob spawning.
- `explosions` - Allow or block explosions from damaging blocks.
- `fire-spread` - Allow or block fire spread/burn/ignite.
- `entry` - Allow or block entering a region.
- `exit` - Allow or block leaving a region.

### Owners and members

Owners and members can be allowed to bypass build/interact protection with these config settings:

```yaml
regions:
  members:
    bypass-build-flags: true
    bypass-interact-flags: true
```

Commands:

- `/region owner add <region> <player>`
- `/region owner remove <region> <player>`
- `/region member add <region> <player>`
- `/region member remove <region> <player>`

### Priority and resizing

Higher priority regions win when regions overlap.

- `/region priority <region> <priority>`
- `/region expand <region> <up|down|north|south|east|west|vertical> <amount>`
- `/region contract <region> <up|down|north|south|east|west> <amount>`

### Messages

- `/region message <region> greeting <message>`
- `/region message <region> farewell <message>`
- `/region message <region> greeting clear`
- `/region message <region> farewell clear`

Messages support placeholders like `%player%`, `%region%`, `%karamsmp_ranks_prefix%`, and `%karamsmp_rank%`.

## Reload command

- `/reload` - Reloads KaramSMP config, ranks, storage, TAB, and regions.
- Aliases: `/ksmpreload`, `/karamsmpreload`
- Permission: `karamsmp.reload`

Note: `/reload` can conflict with the server's built-in reload command. If that happens, use `/ksmpreload` or `/karamsmpreload`.
