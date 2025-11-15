# PlaytimePlus

> Originally created for the [Oldstone](https://oldstone.net) beta Minecraft server. Publicly available for all Project Poseidon servers.

A Bukkit plugin for Project Poseidon (Minecraft Beta 1.7.3) that tracks player playtime and integrates it into chat messages. Includes item usage restrictions based on playtime thresholds, with AFK detection and admin bypass support.

## Features

PlaytimePlus provides a straightforward way to track and display player activity:

- **Automatic playtime tracking**: Records total playtime for each player across sessions
- **AFK detection**: Excludes idle time from playtime tracking using Essentials integration
- **Chat integration**: Shows playtime in chat messages (optional, configurable)
- **Playtime commands**: `/playtime` and `/pt` to check your own or other players' playtime
- **Item restrictions**: Prevent newer players from using specific items until they reach playtime thresholds
- **Admin bypass**: Players with admin permission can bypass block placement restrictions
- **Permission control**: Limit who can view other players' playtime
- **Fully configurable**: Customize all messages, restrictions, and behavior via config file
- **PermissionsEx integration**: Works with your existing permission system

## Installation

1. Download the latest `PlaytimePlus.jar`
2. Place it in your server's `plugins` folder
3. Start or restart your server
4. Edit `plugins/PlaytimePlus/config.yml` to configure restrictions and messages
5. Reload the plugin (or restart the server)

## Configuration

The plugin creates a default `config.yml` on first run. Here's what you can configure:

```yaml
# Display playtime in chat messages (true/false)
show-playtime-in-chat: true

# Restriction messages (use color codes: &c = red, &f = white, &7 = gray)
restriction-message-use: '&cYou need &f{TIME}&c more playtime to use &f{ITEM}&c!'
restriction-message-place: '&cYou need &f{TIME}&c more playtime to place &f{ITEM}&c!'

# Playtime command messages
playtime-command-message: '&f{PLAYER}&7 playtime: &f{TIME}'
playtime-command-not-found: '&cPlayer &f{PLAYER}&c not found!'

# Item restrictions (use pre-flattening item names, values in hours)
TNT: 24
FLINT_AND_STEEL: 720
```

### Message Variables

Messages support these variables:
- `{TIME}` - Formatted time (e.g., "5h 30m")
- `{ITEM}` - Item name formatted nicely (e.g., "flint and steel")
- `{PLAYER}` - Player name

## Commands

### /playtime [player]
Check your playtime or another player's playtime. If no player is specified, shows your own.

```
/playtime              # Check your playtime
/playtime SomePlayer   # Check another player's playtime
/pt                    # Shorthand, works the same way
/pt SomePlayer
```

### Item Restrictions

Once configured, players cannot use or place restricted items until they meet the playtime requirement. They'll see a friendly message telling them how much longer they need to wait.

## How It Works

**Playtime Tracking**: The plugin tracks when players join and logs their playtime every 60 seconds. When players quit, their session time is saved to a file. Playtime persists across server restarts.

**Chat Display**: If enabled, the plugin prepends playtime to chat messages in this format:
```
5h 30m | [Admin] PlayerName » Hello everyone!
```

**Item Restrictions**: When a player tries to use or place a restricted item, the plugin checks their total playtime. If they haven't reached the threshold, the action is blocked and they're notified.

## Storage

Player playtime data is stored in `plugins/PlaytimePlus/players/` with one file per player (UUID.txt). This is human-readable and editable if needed.

## Color Codes

Common color codes you can use in config messages:
- `&c` - Red
- `&f` - White
- `&7` - Gray
- `&e` - Yellow
- `&a` - Green
- `&b` - Cyan
- `&d` - Magenta
- `&8` - Dark gray
- `&0` - Black

See Bukkit documentation for a full list.

## Troubleshooting

**Players aren't seeing playtime in chat**: Check that `show-playtime-in-chat: true` in your config.

**Item restrictions aren't working**: Make sure you're using the correct pre-flattening item name (e.g., `TNT`, `FLINT_AND_STEEL`). Item names are case-sensitive.

**Playtime not persisting**: Check that `plugins/PlaytimePlus/players/` directory exists and is writable.

**Commands not working**: Verify the plugin loaded by checking the server console for PlaytimePlus messages on startup.

## Planned Features

We have some ideas for future versions:

- [ ] Leaderboard command to show top players by playtime
- [ ] Time tracking by session (show current session time vs total)
- [ ] AFK detection to exclude idle time from playtime tracking
- [ ] Playtime milestones with configurable rewards or broadcasts
- [ ] Date/time formatting options in the config
- [ ] Permission nodes for who can view other players' playtime

## Compatibility

- **Server**: Bukkit/Project Poseidon for Minecraft Beta 1.7.3
- **Dependencies**: PermissionsEx
- **Java**: 1.8+

## License

This plugin is provided as-is. Feel free to modify for your server's needs.

## Support

For issues or feature requests, check the config first. Most behavior can be customized without code changes. If you find a bug, check your console logs for error messages.
