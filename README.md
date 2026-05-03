# KaramSMP

Paper 1.21.x SMP plugin by Herex._.7.

## Build

```bash
mvn clean package
```

The built jar will be:

```text
target/KaramSMP-1.2.jar
```

## Main player commands

- `/gmsp` or `/gsmp` - toggle spectator/survival, requires `karamsmp.helper.gmsp`.
- `/nightvision` or `/nv` - toggle night vision.
- `/home`, `/home <name>`, `/sethome <name>`, `/delhome <name>` - homes GUI and teleport system.
- `/spawn` - teleport to server spawn.
- `/stats` or `/stats <player>` - open the stats GUI.
- `/rtp` - opens random teleport GUI for overworld/nether/end.
- `/blance`, `/balance`, `/bal`, `/money` - check your balance.
- `/pay <player> <amount>` - pay another player.
- `/discord`, `/guide`, `/store` - configurable info commands.

## Staff/admin commands

- `/setspawn` - set first-join spawn, permission `karamsmp.spawn.set`.
- `/blance <player>` - check another player's balance, permission `karamsmp.balance.others`.
- `/blance add <player> <amount>`, `/blance remove <player> <amount>`, `/blance set <player> <amount>` - staff balance editing, permission `karamsmp.balance.admin`.
- `/rtp <overworld|nether|end>` - direct RTP section teleport, permission `karamsmp.rtp.admin`.
- `/rank help` - manage ranks.
- `/region help` - manage protection regions.
- `/kscoreboard help` - manage scoreboards.
- `/reload` - reload KaramSMP, permission `karamsmp.reload`.

## Economy placeholders

Use these in scoreboards, TAB, chat, and messages:

- `%balance%`
- `%money%`
- `%balance_plain%`
- `%money_plain%`
- `%karamsmp_balance%`
- `%karamsmp_balance_formatted%`
- `%karamsmp_balance_plain%`
- `%karamsmp_money%`

Balances are formatted like `$125K`, `$125.1K`, `$1M`, `$2.61B`.

## Economy storage

By default the economy uses `economy.storage.type: same-as-database`, so it follows `database.type` from `config.yml`.

Supported storage:

- SQLite
- MySQL
- YAML

## Random teleport

Edit the `rtp:` section in `config.yml` to change:

- GUI title/rows
- world slots/items/lore
- world names (`world`, `world_nether`, `world_the_end` by default)
- min/max radius
- teleport delay
- cancel/teleport sounds
- messages

RTP has a 5-second delay by default and cancels if the player moves.


## Join message hover/click

`join-messages.hover.text` supports all KaramSMP placeholders. For example, `%balance%` shows the balance of the player who joined.

`join-messages.click.command` can run or suggest a custom command such as `/stats %player%` when the join message is clicked.

## RTP lag prevention

RTP safe-location searching now runs across multiple ticks using `rtp.attempts-per-tick` so clicking an RTP GUI item should not freeze the server while it looks for a safe location.


## Stats GUI

`/stats` opens your own stats menu. `/stats <player>` opens an online player's stats and uses `%player%'s Stats` as the title.

Default GUI items include balance, kills, mobs killed, playtime, deaths, ping, and a red banner named `Coming Soon...`. Edit the `stats:` section in `config.yml` to change slots, materials, names, and lore.

Stats placeholders include `%mob_kills%`, `%mobs_killed%`, and `%karamsmp_mob_kills%`.

## ClearLag

KaramSMP now includes an automatic ClearLag cleaner. By default it runs every 15 minutes and announces at 60, 30, 15, 5, 4, 3, 2, and 1 seconds before cleanup.

Commands:

- `/clearlag` or `/clearlag time` - shows the next cleanup time.
- `/clearlag now` - manually clears configured lag entities. Requires `karamsmp.clearlag.admin`.
- `/clearlag reload` - restarts the ClearLag timer from config. Requires `karamsmp.clearlag.admin`.

By default it removes dropped items and XP orbs only. More entity types can be enabled in `config.yml`.
