package main.java.com.playtime;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlaytimePlus extends JavaPlugin implements Listener {

    private final Map<UUID, Long> sessionStarts = new HashMap<>();
    private final Map<UUID, Long> totals = new HashMap<>();
    private final Map<UUID, Long> lastActivityTime = new HashMap<>();
    private int timerTaskId = -1;
    private final Map<String, Long> restrictedItems = new HashMap<>();
    
    private boolean showPlaytimeInChat = true;
    private boolean afkDetectionEnabled = true;
    private long afkTimeout = 600000; // 10 minutes in milliseconds
    private String adminPermission = "Admin";
    private String restrictionMessageUse = "&cYou need &f{TIME}&c more playtime to use &f{ITEM}&c!";
    private String restrictionMessagePlace = "&cYou need &f{TIME}&c more playtime to place &f{ITEM}&c!";
    private String playtimeCommandMessage = "&f{PLAYER}&7 playtime: &f{TIME}";
    private String playtimeCommandNotFound = "&cPlayer &f{PLAYER}&c not found!";
    private String playtimeCommandNoPermission = "&cYou don't have permission to view &f{PLAYER}&c's playtime!";
    private Object essentials = null;
    private Object permissionsEx = null;
    private Object groupManager = null;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        createDataFolder();
        loadConfig();
        loadRestrictedItems();

        // Try to hook into PermissionsEx
        try {
            Plugin pexPlugin = getServer().getPluginManager().getPlugin("PermissionsEx");
            if (pexPlugin != null) {
                permissionsEx = pexPlugin;
                Bukkit.getLogger().info("PlaytimePlus: PermissionsEx hooked for prefix/suffix support");
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("PlaytimePlus: Could not hook into PermissionsEx: " + e.getMessage());
        }
        
        // Try to hook into GroupManager
        try {
            Plugin gmPlugin = getServer().getPluginManager().getPlugin("GroupManager");
            if (gmPlugin != null) {
                groupManager = gmPlugin;
                Bukkit.getLogger().info("PlaytimePlus: GroupManager hooked for prefix/suffix support");
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("PlaytimePlus: Could not hook into GroupManager: " + e.getMessage());
        }
        
        // Try to hook into Essentials for AFK detection
        try {
            Plugin essentialsPlugin = getServer().getPluginManager().getPlugin("Essentials");
            if (essentialsPlugin != null) {
                essentials = essentialsPlugin;
                Bukkit.getLogger().info("PlaytimePlus: Essentials hooked for AFK detection");
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("PlaytimePlus: Could not hook into Essentials: " + e.getMessage());
        }

        timerTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            long now = System.currentTimeMillis();
            for (Player p : getServer().getOnlinePlayers()) {
                UUID uuid = p.getUniqueId();
                if (sessionStarts.containsKey(uuid)) {
                    // Check if player is AFK
                    if (isPlayerAFK(p)) {
                        // Don't count AFK time, but update session start to current time
                        // so we don't count the AFK period
                        sessionStarts.put(uuid, now);
                        lastActivityTime.put(uuid, now);
                    } else {
                        // Calculate session time and add to base total
                        long sessionSeconds = (now - sessionStarts.get(uuid)) / 1000;
                        if (sessionSeconds > 0) {
                            // Add the session time to the stored total
                            long currentTotal = totals.get(uuid);
                            totals.put(uuid, currentTotal + sessionSeconds);
                            // Reset session start for next interval
                            sessionStarts.put(uuid, now);
                        }
                    }
                }
            }
        }, 1200L, 1200L);
    }

    @Override
    public void onDisable() {
        if (timerTaskId != -1) {
            getServer().getScheduler().cancelTask(timerTaskId);
        }
        for (Player p : getServer().getOnlinePlayers()) {
            savePlayerTime(p);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        sessionStarts.put(uuid, System.currentTimeMillis());
        totals.put(uuid, loadTotal(uuid));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        savePlayerTime(e.getPlayer());
        UUID uuid = e.getPlayer().getUniqueId();
        sessionStarts.remove(uuid);
        totals.remove(uuid);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = p.getItemInHand();
        
        if (item == null) return;
        
        String materialName = item.getType().toString();
        if (!restrictedItems.containsKey(materialName)) return;
        
        long requiredSeconds = restrictedItems.get(materialName);
        long playtimeSeconds = getCurrentPlaytime(p);
        
        if (playtimeSeconds < requiredSeconds) {
            e.setCancelled(true);
            long shortfallSeconds = requiredSeconds - playtimeSeconds;
            String timeNeeded = formatTime(shortfallSeconds);
            String message = restrictionMessageUse
                .replace("{TIME}", timeNeeded)
                .replace("{ITEM}", formatItemName(materialName));
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItemInHand();
        
        if (item == null) return;
        
        // Check if player has admin permission bypass
        if (hasAdminPermission(p)) {
            return;
        }
        
        String materialName = item.getType().toString();
        if (!restrictedItems.containsKey(materialName)) return;
        
        long requiredSeconds = restrictedItems.get(materialName);
        long playtimeSeconds = getCurrentPlaytime(p);
        
        if (playtimeSeconds < requiredSeconds) {
            e.setCancelled(true);
            long shortfallSeconds = requiredSeconds - playtimeSeconds;
            String timeNeeded = formatTime(shortfallSeconds);
            String message = restrictionMessagePlace
                .replace("{TIME}", timeNeeded)
                .replace("{ITEM}", formatItemName(materialName));
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }

@EventHandler(priority = Event.Priority.Highest)
    public void onChat(PlayerChatEvent e) {
        if (e.isCancelled()) return;
        if (!showPlaytimeInChat) return;

        Player p = e.getPlayer();
        String message = e.getMessage();
        long playtimeSeconds = getCurrentPlaytime(p);
        String timeStr = formatTime(playtimeSeconds);

        String prefix = "";
        String suffix = "";

        // Try to get prefix/suffix from PermissionsEx if available
        if (permissionsEx != null) {
            try {
                Class<?> pexClass = permissionsEx.getClass();
                Object permManager = pexClass.getMethod("getPermissionManager").invoke(permissionsEx);
                Object user = permManager.getClass().getMethod("getUser", String.class).invoke(permManager, p.getName());
                if (user != null) {
                    prefix = (String) user.getClass().getMethod("getPrefix").invoke(user);
                    suffix = (String) user.getClass().getMethod("getSuffix").invoke(user);
                }
            } catch (Exception ex) {
                // Failed to get PEX prefix/suffix, try GroupManager
            }
        }
        
        // Try GroupManager if PEX didn't work or isn't available
        if ((prefix == null || prefix.isEmpty()) && (suffix == null || suffix.isEmpty()) && groupManager != null) {
            try {
                Class<?> gmClass = Class.forName("org.anjocaido.groupmanager.GroupManager");
                Object worldsHolder = gmClass.getMethod("getWorldsHolder").invoke(groupManager);
                Object worldData = worldsHolder.getClass().getMethod("getWorldData", String.class)
                    .invoke(worldsHolder, p.getWorld().getName());
                if (worldData != null) {
                    Object permHandler = worldData.getClass().getMethod("getPermissionsHandler").invoke(worldData);
                    prefix = (String) permHandler.getClass().getMethod("getUserPrefix", String.class).invoke(permHandler, p.getName());
                    suffix = (String) permHandler.getClass().getMethod("getUserSuffix", String.class).invoke(permHandler, p.getName());
                }
            } catch (Exception ex) {
                // Failed to get GroupManager prefix/suffix, fall through to displayname
            }
        }
        
        // Fallback: use display name if neither PEX nor GroupManager worked
        if ((prefix == null || prefix.isEmpty()) && (suffix == null || suffix.isEmpty())) {
            String display = p.getDisplayName();
            if (display != null && !display.equals(p.getName())) {
                prefix = display.replace(p.getName(), "").trim();
            }
        }

        if (prefix == null) prefix = "";
        if (suffix == null) suffix = "";
        
        prefix = prefix.replace("&r", "").replace("§r", "");
        suffix = suffix.replace("&r", "").replace("§r", "");
        
        prefix = ChatColor.translateAlternateColorCodes('&', prefix);
        suffix = ChatColor.translateAlternateColorCodes('&', suffix);

        String playtime = ChatColor.GRAY + timeStr;
        String separator = ChatColor.GRAY + " | ";
        String arrow = ChatColor.GRAY + " » " + ChatColor.WHITE;

        String formatted = playtime + separator + prefix + p.getName() + suffix + arrow + message;
        e.setFormat(formatted);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;
        Player target = player;

        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                String message = playtimeCommandNotFound.replace("{PLAYER}", args[0]);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                return true;
            }
            
            // Check permission to view other players' playtime
            if (!target.equals(player) && !canViewPlayerPlaytime(player, target)) {
                String message = playtimeCommandNoPermission.replace("{PLAYER}", target.getName());
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                return true;
            }
        }

        long playtimeSeconds = getCurrentPlaytime(target);
        String timeStr = formatTime(playtimeSeconds);
        String message = playtimeCommandMessage
            .replace("{PLAYER}", target.getName())
            .replace("{TIME}", timeStr);
        
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        return true;
    }

    private long getCurrentPlaytime(Player p) {
        UUID uuid = p.getUniqueId();
        if (!sessionStarts.containsKey(uuid)) {
            return totals.getOrDefault(uuid, loadTotal(uuid));
        }
        long session = (System.currentTimeMillis() - sessionStarts.get(uuid)) / 1000;
        return loadTotal(uuid) + session;
    }

    private void savePlayerTime(Player p) {
        UUID uuid = p.getUniqueId();
        if (!sessionStarts.containsKey(uuid)) return;

        long session = (System.currentTimeMillis() - sessionStarts.get(uuid)) / 1000;
        long total = loadTotal(uuid) + session;
        saveTotal(uuid, total);
    }

private String formatTime(long seconds) {
    long days = seconds / 86400; // 24h * 3600
    long hours = (seconds % 86400) / 3600;
    long minutes = (seconds % 3600) / 60;

    if (days > 0) {
        return days + "d" + hours + "h" + minutes + "m";
    } else if (hours > 0) {
        return hours + "h" + minutes + "m";
    } else {
        return minutes + "m";
    }
}


    private void createDataFolder() {
        File playersDir = new File(getDataFolder(), "players");
        if (!playersDir.exists()) playersDir.mkdirs();
    }

    private void loadConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            createDefaultConfig();
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                if (line.startsWith("show-playtime-in-chat:")) {
                    showPlaytimeInChat = parseBoolean(line.split(":", 2)[1].trim());
                } else if (line.startsWith("afk-detection-enabled:")) {
                    afkDetectionEnabled = parseBoolean(line.split(":", 2)[1].trim());
                } else if (line.startsWith("afk-timeout-minutes:")) {
                    try {
                        long minutes = Long.parseLong(line.split(":", 2)[1].trim());
                        afkTimeout = minutes * 60000; // Convert to milliseconds
                    } catch (NumberFormatException e) {
                        Bukkit.getLogger().warning("PlaytimePlus: Invalid afk-timeout-minutes value");
                    }
                } else if (line.startsWith("admin-permission:")) {
                    adminPermission = line.split(":", 2)[1].trim();
                } else if (line.startsWith("restriction-message-use:")) {
                    restrictionMessageUse = line.split(":", 2)[1].trim();
                } else if (line.startsWith("restriction-message-place:")) {
                    restrictionMessagePlace = line.split(":", 2)[1].trim();
                } else if (line.startsWith("playtime-command-message:")) {
                    playtimeCommandMessage = line.split(":", 2)[1].trim();
                } else if (line.startsWith("playtime-command-not-found:")) {
                    playtimeCommandNotFound = line.split(":", 2)[1].trim();
                } else if (line.startsWith("playtime-command-no-permission:")) {
                    playtimeCommandNoPermission = line.split(":", 2)[1].trim();
                }
            }
        } catch (IOException e) {
            Bukkit.getLogger().severe("Failed to load config: " + e.getMessage());
        }
    }

    private boolean parseBoolean(String value) {
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes");
    }

    private long loadTotal(UUID uuid) {
        File file = new File(getDataFolder() + "/players", uuid + ".txt");
        if (!file.exists()) return 0L;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line = br.readLine();
            return line != null ? Long.parseLong(line.trim()) : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
    private void saveTotal(UUID uuid, long total) {
        File file = new File(getDataFolder() + "/players", uuid + ".txt");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write(String.valueOf(total));
        } catch (IOException e) {
            Bukkit.getLogger().severe("Failed to save playtime for " + uuid + ": " + e.getMessage());
        }
    }

    private void loadRestrictedItems() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            createDefaultConfig();
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                if (line.contains(":")) {
                    String[] parts = line.split(":");
                    if (parts.length == 2) {
                        String itemName = parts[0].trim();
                        try {
                            long hours = Long.parseLong(parts[1].trim());
                            long seconds = hours * 3600;
                            restrictedItems.put(itemName, seconds);
                            Bukkit.getLogger().info("PlaytimePlus: Restricted " + itemName + " to " + hours + "h");
                        } catch (NumberFormatException e) {
                            Bukkit.getLogger().warning("PlaytimePlus: Invalid playtime for " + itemName);
                        }
                    }
                }
            }
        } catch (IOException e) {
            Bukkit.getLogger().severe("Failed to load restricted items config: " + e.getMessage());
        }
    }

    private void createDefaultConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(configFile))) {
            bw.write("# PlaytimePlus Configuration\n\n");
            bw.write("# Display playtime in chat messages (true/false)\n");
            bw.write("show-playtime-in-chat: true\n\n");
            bw.write("# AFK Detection Settings\n");
            bw.write("# Enable AFK detection to exclude idle time from playtime tracking\n");
            bw.write("afk-detection-enabled: true\n");
            bw.write("# Timeout before a player is considered AFK (in minutes)\n");
            bw.write("afk-timeout-minutes: 10\n\n");
            bw.write("# Admin Permission (from PermissionsEx)\n");
            bw.write("# Players with this permission can bypass block placement restrictions\n");
            bw.write("admin-permission: Admin\n\n");
            bw.write("# Restriction messages with color codes (&c = red, &f = white, &7 = gray)\n");
            bw.write("# Variables: {TIME} = required time, {ITEM} = item name\n");
            bw.write("restriction-message-use: '&cYou need &f{TIME}&c more playtime to use &f{ITEM}&c!'\n");
            bw.write("restriction-message-place: '&cYou need &f{TIME}&c more playtime to place &f{ITEM}&c!'\n\n");
            bw.write("# Playtime command messages\n");
            bw.write("# Variables: {PLAYER} = player name, {TIME} = playtime\n");
            bw.write("playtime-command-message: '&f{PLAYER}&7 playtime: &f{TIME}'\n");
            bw.write("playtime-command-not-found: '&cPlayer &f{PLAYER}&c not found!'\n");
            bw.write("playtime-command-no-permission: '&cYou don\\'t have permission to view &f{PLAYER}&c\\'s playtime!'\n\n");
            bw.write("# Item Playtime Restrictions\n");
            bw.write("# Format: ITEM_NAME: hours_required\n");
            bw.write("# Example:\n");
            bw.write("# TNT: 24\n");
            bw.write("# FLINT_AND_STEEL: 720\n\n");
            bw.write("# Add restrictions below:\n");
        } catch (IOException e) {
            Bukkit.getLogger().severe("Failed to create default config: " + e.getMessage());
        }
    }

    private String formatItemName(String materialName) {
        return materialName.toLowerCase().replace("_", " ");
    }

    /**
     * Check if a player has admin permission to bypass block placement restrictions
     */
    private boolean hasAdminPermission(Player p) {
        // First check Bukkit permissions
        if (p.hasPermission("playtimeplus.admin")) {
            return true;
        }
        
        // Try PEX permission check if available
        if (permissionsEx != null) {
            try {
                Class<?> pexClass = permissionsEx.getClass();
                Object permManager = pexClass.getMethod("getPermissionManager").invoke(permissionsEx);
                Object user = permManager.getClass().getMethod("getUser", String.class).invoke(permManager, p.getName());
                if (user != null) {
                    boolean hasPerm = (Boolean) user.getClass().getMethod("has", String.class).invoke(user, adminPermission);
                    if (hasPerm) {
                        return true;
                    }
                }
            } catch (Exception e) {
                // Try GroupManager next
            }
        }
        
        // Try GroupManager permission check if available
        if (groupManager != null) {
            try {
                Class<?> gmClass = Class.forName("org.anjocaido.groupmanager.GroupManager");
                Object worldsHolder = gmClass.getMethod("getWorldsHolder").invoke(groupManager);
                Object worldData = worldsHolder.getClass().getMethod("getWorldData", String.class)
                    .invoke(worldsHolder, p.getWorld().getName());
                if (worldData != null) {
                    Object permHandler = worldData.getClass().getMethod("getPermissionsHandler").invoke(worldData);
                    boolean hasPerm = (Boolean) permHandler.getClass().getMethod("has", String.class, String.class)
                        .invoke(permHandler, p.getName(), adminPermission);
                    if (hasPerm) {
                        return true;
                    }
                }
            } catch (Exception e) {
                // Fall back to false
            }
        }
        
        return false;
    }

    /**
     * Check if a player can view another player's playtime
     */
    private boolean canViewPlayerPlaytime(Player viewer, Player target) {
        // Admin/self always can view
        if (viewer.equals(target) || hasAdminPermission(viewer)) {
            return true;
        }
        
        // Check Bukkit permissions first
        if (viewer.hasPermission("playtimeplus.view.others")) {
            return true;
        }
        
        // Try PEX permission check if available
        if (permissionsEx != null) {
            try {
                Class<?> pexClass = permissionsEx.getClass();
                Object permManager = pexClass.getMethod("getPermissionManager").invoke(permissionsEx);
                Object user = permManager.getClass().getMethod("getUser", String.class).invoke(permManager, viewer.getName());
                if (user != null) {
                    boolean hasPerm = (Boolean) user.getClass().getMethod("has", String.class).invoke(user, "playtimeplus.view.others");
                    return hasPerm;
                }
            } catch (Exception e) {
                // Try GroupManager next
            }
        }
        
        // Try GroupManager permission check if available
        if (groupManager != null) {
            try {
                Class<?> gmClass = Class.forName("org.anjocaido.groupmanager.GroupManager");
                Object worldsHolder = gmClass.getMethod("getWorldsHolder").invoke(groupManager);
                Object worldData = worldsHolder.getClass().getMethod("getWorldData", String.class)
                    .invoke(worldsHolder, viewer.getWorld().getName());
                if (worldData != null) {
                    Object permHandler = worldData.getClass().getMethod("getPermissionsHandler").invoke(worldData);
                    boolean hasPerm = (Boolean) permHandler.getClass().getMethod("has", String.class, String.class)
                        .invoke(permHandler, viewer.getName(), "playtimeplus.view.others");
                    return hasPerm;
                }
            } catch (Exception e) {
                // Fall back to false
            }
        }
        
        return false;
    }

    /**
     * Check if a player is AFK using Essentials or a timeout mechanism
     */
    private boolean isPlayerAFK(Player p) {
        if (!afkDetectionEnabled) return false;
        
        // Try to use Essentials AFK detection if available
        if (essentials != null) {
            try {
                // Reflection-based approach to check if player is AFK in Essentials
                Class<?> essentialsClass = essentials.getClass();
                Object userData = essentialsClass.getMethod("getUser", String.class).invoke(essentials, p.getName());
                if (userData != null) {
                    Class<?> userClass = userData.getClass();
                    boolean isAfk = (Boolean) userClass.getMethod("isAfk").invoke(userData);
                    return isAfk;
                }
            } catch (Exception e) {
                // If Essentials check fails, fall back to timeout method
            }
        }
        
        // Fallback: check if player hasn't moved or acted in a while
        UUID uuid = p.getUniqueId();
        if (lastActivityTime.containsKey(uuid)) {
            long timeSinceActivity = System.currentTimeMillis() - lastActivityTime.get(uuid);
            return timeSinceActivity > afkTimeout;
        }
        
        return false;
    }
}