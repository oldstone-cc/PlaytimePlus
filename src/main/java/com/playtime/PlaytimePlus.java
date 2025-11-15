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
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import ru.tehkode.permissions.PermissionUser;
import ru.tehkode.permissions.bukkit.PermissionsEx;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlaytimePlus extends JavaPlugin implements Listener {

    private final Map<UUID, Long> sessionStarts = new HashMap<>();
    private final Map<UUID, Long> totals = new HashMap<>();
    private int timerTaskId = -1;
    private final Map<String, Long> restrictedItems = new HashMap<>();
    
    private boolean showPlaytimeInChat = true;
    private String restrictionMessageUse = "&cYou need &f{TIME}&c more playtime to use &f{ITEM}&c!";
    private String restrictionMessagePlace = "&cYou need &f{TIME}&c more playtime to place &f{ITEM}&c!";
    private String playtimeCommandMessage = "&f{PLAYER}&7 playtime: &f{TIME}";
    private String playtimeCommandNotFound = "&cPlayer &f{PLAYER}&c not found!";

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        createDataFolder();
        loadConfig();
        loadRestrictedItems();

        timerTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            long now = System.currentTimeMillis();
            for (Player p : getServer().getOnlinePlayers()) {
                UUID uuid = p.getUniqueId();
                if (sessionStarts.containsKey(uuid)) {
                    long sessionSeconds = (now - sessionStarts.get(uuid)) / 1000;
                    long base = loadTotal(uuid);
                    totals.put(uuid, base + sessionSeconds);
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

        try {
            PermissionUser user = PermissionsEx.getPermissionManager().getUser(p.getName());
            if (user != null) {
                prefix = user.getPrefix();
                suffix = user.getSuffix();
            }
        } catch (Exception ex) {
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
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        return h > 0 ? h + "h" + m + "m" : m + "m";
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
                } else if (line.startsWith("restriction-message-use:")) {
                    restrictionMessageUse = line.split(":", 2)[1].trim();
                } else if (line.startsWith("restriction-message-place:")) {
                    restrictionMessagePlace = line.split(":", 2)[1].trim();
                } else if (line.startsWith("playtime-command-message:")) {
                    playtimeCommandMessage = line.split(":", 2)[1].trim();
                } else if (line.startsWith("playtime-command-not-found:")) {
                    playtimeCommandNotFound = line.split(":", 2)[1].trim();
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
            bw.write("# Restriction messages with color codes (&c = red, &f = white, &7 = gray)\n");
            bw.write("# Variables: {TIME} = required time, {ITEM} = item name\n");
            bw.write("restriction-message-use: '&cYou need &f{TIME}&c more playtime to use &f{ITEM}&c!'\n");
            bw.write("restriction-message-place: '&cYou need &f{TIME}&c more playtime to place &f{ITEM}&c!'\n\n");
            bw.write("# Playtime command messages\n");
            bw.write("# Variables: {PLAYER} = player name, {TIME} = playtime\n");
            bw.write("playtime-command-message: '&f{PLAYER}&7 playtime: &f{TIME}'\n");
            bw.write("playtime-command-not-found: '&cPlayer &f{PLAYER}&c not found!'\n\n");
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
}