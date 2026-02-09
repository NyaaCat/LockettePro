package me.crafter.mc.lockettepro;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Config {

    private static Plugin plugin;
    private static FileConfiguration config;
    private static FileConfiguration lang;
    private static String langfilename = "lang.yml";
    private static boolean uuid = false;
    private static Set<Material> lockables = new HashSet<>();
    private static Set<String> privatestrings = new HashSet<>();
    private static Set<String> additionalstrings = new HashSet<>();
    private static Set<String> everyonestrings = new HashSet<>();
    private static Set<String> containerbypassstrings = new HashSet<>();
    private static Set<String> timerstrings = new HashSet<>();
    private static String lockedcontainerpdckey = "lockettepro:locked_container";
    private static String defaultprivatestring = "[Private]";
    private static String defaultadditionalstring = "[More Users]";
    private static byte enablequickprotect = (byte) 1;
    private static boolean blockinterfereplacement = true;
    private static boolean blockitemtransferin = false;
    private static boolean blockitemtransferout = false;
    private static int cachetime = 0;
    private static boolean cacheenabled = false;
    private static byte blockhopperminecart = 0;
    private static boolean lockexpire = false;
    private static double lockexpiredays = 60D;
    private static Material permissioncloneitemmaterial = Material.FEATHER;
    private static String permissioncloneitemname = "&6[LockettePro] &ePermission Clone";
    private static List<String> permissioncloneitemlore = new ArrayList<>();
    public static boolean protocollib = false;
    public static boolean worldguard = false;
    public static boolean coreprotect = false;
    private static long lockdefaultcreatetime = -1L;
    private static String lockexpirestring = "";
    private static Set<String> protectionexempt = new HashSet<>();

    public Config(Plugin _plugin) {
        plugin = _plugin;
        reload();
    }

    public static void reload() {
        initDefaultConfig();
        initAdditionalFiles();
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        uuid = config.getBoolean("enable-uuid-support", false);
        protocollib = config.getBoolean("protocollib", true);
        worldguard = config.getBoolean("worldguard", true);
        coreprotect = config.getBoolean("coreprotect", true);
        langfilename = config.getString("language-file-name", "lang.yml");
        lang = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), langfilename));
        String enablequickprotectstring = config.getString("enable-quick-protect", "true");

        switch (enablequickprotectstring.toLowerCase()) {
            case "true" -> enablequickprotect = 1;
            case "false" -> enablequickprotect = 0;
            case "sneak" -> enablequickprotect = 2;
            default -> enablequickprotect = 1;
        }
        blockinterfereplacement = config.getBoolean("block-interfere-placement", true);
        blockitemtransferin = config.getBoolean("block-item-transfer-in", false);
        blockitemtransferout = config.getBoolean("block-item-transfer-out", true);

        List<String> privatestringlist = config.getStringList("private-signs");
        List<String> additionalstringlist = config.getStringList("additional-signs");
        List<String> everyonestringlist = config.getStringList("everyone-signs");
        List<String> containerbypassstringlist = config.getStringList("container-bypass-sign-tags");
        List<String> protectionexemptstringlist = config.getStringList("protection-exempt");
        privatestrings = new HashSet<>(privatestringlist);
        additionalstrings = new HashSet<>(additionalstringlist);
        everyonestrings = new HashSet<>(everyonestringlist);
        containerbypassstrings = new HashSet<>(containerbypassstringlist);
        lockedcontainerpdckey = config.getString("locked-container-pdc-key", "lockettepro:locked_container");
        if (lockedcontainerpdckey == null || lockedcontainerpdckey.isBlank()) {
            lockedcontainerpdckey = "lockettepro:locked_container";
        }
        protectionexempt = new HashSet<>(protectionexemptstringlist);
        defaultprivatestring = privatestringlist.get(0);
        defaultadditionalstring = additionalstringlist.get(0);

        List<String> timerstringlist = config.getStringList("timer-signs");
        List<String> timerstringlist2 = new ArrayList<>();
        for (String timerstring : timerstringlist) {
            if (timerstring.contains("@")) timerstringlist2.add(timerstring);
        }
        timerstrings = new HashSet<>(timerstringlist2);

        cachetime = config.getInt("cache-time-seconds", 0) * 1000;
        cacheenabled = (config.getInt("cache-time-seconds", 0) > 0);
        if (cacheenabled) {
            plugin.getLogger().info("Cache is enabled! In case of inconsistency, turn off immediately.");
        }

        String blockhopperminecartstring = config.getString("block-hopper-minecart", "remove");
        switch (blockhopperminecartstring.toLowerCase()) {
            case "true" -> blockhopperminecart = 1;
            case "false" -> blockhopperminecart = 0;
            case "remove" -> blockhopperminecart = 2;
            default -> blockhopperminecart = 2;
        }

        lockexpire = config.getBoolean("lock-expire", false);
        lockexpiredays = config.getDouble("lock-expire-days", 999.9D);
        lockdefaultcreatetime = config.getLong("lock-default-create-time-unix", -1L);
        if (lockdefaultcreatetime < -1L) lockdefaultcreatetime = -1L;
        lockexpirestring = ChatColor.translateAlternateColorCodes('&',
                config.getString("lock-expire-string", "&3[Expired]"));
        String cloneMaterialString = config.getString("permission-clone-item-material", "FEATHER");
        Material cloneMaterial = Material.matchMaterial(cloneMaterialString == null ? "" : cloneMaterialString.toUpperCase(Locale.ROOT));
        if (cloneMaterial == null || cloneMaterial.isAir() || !cloneMaterial.isItem()) {
            cloneMaterial = Material.FEATHER;
        }
        permissioncloneitemmaterial = cloneMaterial;
        permissioncloneitemname = config.getString("permission-clone-item-name", "&6[LockettePro] &ePermission Clone");
        if (permissioncloneitemname == null || permissioncloneitemname.isBlank()) {
            permissioncloneitemname = "&6[LockettePro] &ePermission Clone";
        }
        permissioncloneitemlore = new ArrayList<>(config.getStringList("permission-clone-item-lore"));
        if (permissioncloneitemlore.isEmpty()) {
            permissioncloneitemlore = new ArrayList<>(Arrays.asList(
                    "&7Use this item on a container to",
                    "&7apply copied lock permissions."
            ));
        }
        List<String> unprocesseditems = config.getStringList("lockables");
        lockables = new HashSet<Material>();
        for (String unprocesseditem : unprocesseditems) {
            if (unprocesseditem.equals("*")) {
                Collections.addAll(lockables, Material.values());
                plugin.getLogger().info("All blocks are default to be lockable!");
                plugin.getLogger().info("Add '-<Material>' to exempt a block, such as '-STONE'!");
                continue;
            }
            boolean add = true;
            if (unprocesseditem.startsWith("-")) {
                add = false;
                unprocesseditem = unprocesseditem.substring(1);
            }
            Material material = Material.getMaterial(unprocesseditem);
            if (material == null || !material.isBlock()) {
                plugin.getLogger().warning(unprocesseditem + " is not a block!");
            } else {
                if (add) {
                    lockables.add(material);
                } else {
                    lockables.remove(material);
                }
            }
        }
        lockables.removeAll(Tag.SIGNS.getValues());
        lockables.remove(Material.SCAFFOLDING);
    }

    public static void initDefaultConfig() {
        plugin.saveDefaultConfig();
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        config.addDefault("language-file-name", "lang.yml");
        config.addDefault("enable-quick-protect", true);
        config.addDefault("enable-uuid-support", false);
        config.addDefault("block-interfere-placement", true);
        config.addDefault("block-item-transfer-in", false);
        config.addDefault("block-item-transfer-out", true);
        config.addDefault("block-hopper-minecart", "remove");
        config.addDefault("cache-time-seconds", 0);

        String[] private_signs = {"[Private]", "[private]"};
        config.addDefault("private-signs", private_signs);
        String[] additional_signs = {"[More Users]", "[more Users]"};
        config.addDefault("additional-signs", additional_signs);
        String[] everyone_signs = {"[Everyone]", "[everyone]"};
        config.addDefault("everyone-signs", everyone_signs);
        String[] container_bypass_sign_tags = {"[hopper]"};
        config.addDefault("container-bypass-sign-tags", container_bypass_sign_tags);
        config.addDefault("locked-container-pdc-key", "lockettepro:locked_container");
        String[] timer_signs = {"[Timer:@]", "[timer:@]"};
        config.addDefault("timer-signs", timer_signs);
        String[] lockables = {"CHEST","TRAPPED_CHEST","FURNACE","BURNING_FURNACE","HOPPER","BREWING_STAND","DIAMOND_BLOCK",
                "OAK_DOOR","SPRUCE_DOOR","BIRCH_DOOR","JUNGLE_DOOR","ACACIA_DOOR","DARK_OAK_DOOR","IRON_DOOR", "LECTERN"};
        config.addDefault("lockables", lockables);
        String[] protection_exempt = {"nothing"};
        config.addDefault("protection-exempt", protection_exempt);
        config.addDefault("lock-expire", false);
        config.addDefault("lock-expire-days", 999.9D);
        config.addDefault("lock-default-create-time-unix", -1L);
        config.addDefault("lock-expire-string", "&3[Expired]");
        config.addDefault("permission-clone-item-material", "FEATHER");
        config.addDefault("permission-clone-item-name", "&6[LockettePro] &ePermission Clone");
        String[] permission_clone_item_lore = {
                "&7Use this item on a container to",
                "&7apply copied lock permissions."
        };
        config.addDefault("permission-clone-item-lore", permission_clone_item_lore);

        config.options().copyDefaults(true);
        try {
            config.save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void initAdditionalFiles() {
        String[] availablefiles = {"lang.yml", "lang_zh-cn.yml", "lang_es.yml", "lang_it.yml"};
        for (String filename : availablefiles) {
            File langfile = new File(plugin.getDataFolder(), filename);
            if (!langfile.exists()) {
                plugin.saveResource(filename, false);
            }
        }
    }

    public static byte getQuickProtectAction() {
        return enablequickprotect;
    }

    public static boolean isInterferePlacementBlocked() {
        return blockinterfereplacement;
    }

    public static boolean isItemTransferInBlocked() {
        return blockitemtransferin;
    }

    public static boolean isItemTransferOutBlocked() {
        return blockitemtransferout;
    }

    public static byte getHopperMinecartAction() {
        return blockhopperminecart;
    }

    public static boolean isLockExpire() {
        return lockexpire;
    }

    public static Double getLockExpireDays() {
        return lockexpiredays;
    }

    public static long getLockDefaultCreateTimeUnix() {
        return lockdefaultcreatetime;
    }

    public static String getLockExpireString() {
        return lockexpirestring;
    }

    public static String getLang(String path) {
        return ChatColor.translateAlternateColorCodes('&', lang.getString(path, ""));
    }

    public static boolean isUuidEnabled() {
        return uuid;
    }

    public static boolean isLockable(Material material) {
        return lockables.contains(material);
    }

    public static boolean isPrivateSignString(String message) {
        return privatestrings.contains(message);
    }

    public static boolean isAdditionalSignString(String message) {
        return additionalstrings.contains(message);
    }

    public static boolean isEveryoneSignString(String message) {
        return everyonestrings.contains(message);
    }

    public static boolean isContainerBypassSignString(String message) {
        return containerbypassstrings.contains(message);
    }

    public static String getLockedContainerPdcKeyString() {
        return lockedcontainerpdckey;
    }

    public static boolean isTimerSignString(String message) {
        for (String timerstring : timerstrings) {
            String[] splitted = timerstring.split("@", 2);
            if (message.startsWith(splitted[0]) && message.endsWith(splitted[1])) {
                return true;
            }
        }
        return false;
    }

    public static int getTimer(String message) {
        for (String timerstring : timerstrings) {
            String[] splitted = timerstring.split("@", 2);
            if (message.startsWith(splitted[0]) && message.endsWith(splitted[1])) {
                String newmessage = message.replace(splitted[0], "").replace(splitted[1], "");
                try {
                    int seconds = Integer.parseInt(newmessage);
                    return Math.min(seconds, 20);
                } catch (Exception ignored) {
                }
            }
        }
        return 0;
    }

    public static String getDefaultPrivateString() {
        return defaultprivatestring;
    }

    public static String getDefaultAdditionalString() {
        return defaultadditionalstring;
    }

    public static int getCacheTimeMillis() {
        return cachetime;
    }

    public static boolean isCacheEnabled() {
        return cacheenabled;
    }

    public static boolean isProtectionExempted(String against) {
        return protectionexempt.contains(against);
    }

    public static Material getPermissionCloneItemMaterial() {
        return permissioncloneitemmaterial;
    }

    public static String getPermissionCloneItemName() {
        return permissioncloneitemname;
    }

    public static List<String> getPermissionCloneItemLore() {
        return new ArrayList<>(permissioncloneitemlore);
    }

}
