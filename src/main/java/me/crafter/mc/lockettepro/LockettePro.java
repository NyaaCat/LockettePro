package me.crafter.mc.lockettepro;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LockettePro extends JavaPlugin {

    private static Plugin plugin;
    private boolean debug = false;
    private static boolean needcheckhand = true;

    public void onEnable() {
        plugin = this;
        // Read config
        new Config(this);
        // Register Listeners
        // If debug mode is not on, debug listener won't register
        if (debug) getServer().getPluginManager().registerEvents(new BlockDebugListener(), this);
        getServer().getPluginManager().registerEvents(new BlockPlayerListener(), this);
        getServer().getPluginManager().registerEvents(new BlockEnvironmentListener(), this);
        getServer().getPluginManager().registerEvents(new BlockInventoryMoveListener(), this);
        getServer().getPluginManager().registerEvents(new BlockInventoryAccessListener(), this);
        // Dependency
        new Dependency(this);
        // If UUID is not enabled, UUID listener won't register
        if (Config.isUuidEnabled() || Config.isLockExpire()) {
            if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
                DependencyProtocolLib.setUpProtocolLib(this);
            } else {
                plugin.getLogger().info("ProtocolLib is not found!");
                plugin.getLogger().info("UUID & expiracy support requires ProtocolLib, or else signs will be ugly!");
            }
        }
        CompatibleScheduler.runTask(this, null, Utils::refreshLockedContainerPdcTagsInLoadedChunks);
    }

    public void onDisable() {
        if (Config.isUuidEnabled() && Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
            DependencyProtocolLib.cleanUpProtocolLib(this);
        }
    }

    public static Plugin getPlugin() {
        return plugin;
    }

    public static boolean needCheckHand() {
        return needcheckhand;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> commands = new ArrayList<>();
        commands.add("reload");
        commands.add("version");
        commands.add("on");
        commands.add("info");
        commands.add("rename");
        commands.add("permission");
        commands.add("clone");
        commands.add("1");
        commands.add("2");
        commands.add("3");
        commands.add("4");
        commands.add("uuid");
        commands.add("update");
        commands.add("debug");
        if (args != null && args.length == 1) {
            List<String> list = new ArrayList<>();
            for (String s : commands) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    list.add(s);
                }
            }
            return list;
        }
        if (args != null && args.length == 2 && "permission".equalsIgnoreCase(args[0])) {
            List<String> list = new ArrayList<>();
            list.add("xx:");
            list.add("rw:");
            list.add("ro:");
            list.add("--:");
            return list;
        }
        return null;
    }

    public boolean onCommand(@NotNull CommandSender sender, Command cmd, @NotNull String commandLabel, final String[] args) {
        if (cmd.getName().equals("lockettepro")) {
            if (args.length == 0) {
                Utils.sendMessages(sender, Config.getLang("command-usage"));
            } else {
                // The following commands does not require player
                switch (args[0]) {
                    case "reload" -> {
                        if (sender.hasPermission("lockettepro.reload")) {
                            if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
                                DependencyProtocolLib.cleanUpProtocolLib(this);
                            }
                            Config.reload();
                            if (Config.isUuidEnabled() && Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
                                DependencyProtocolLib.setUpProtocolLib(this);
                            }
                            Utils.sendMessages(sender, Config.getLang("config-reloaded"));
                        } else {
                            Utils.sendMessages(sender, Config.getLang("no-permission"));
                        }
                        return true;
                    }
                    case "version" -> {
                        if (sender.hasPermission("lockettepro.version")) {
                            sender.sendMessage(plugin.getDescription().getFullName());
                        } else {
                            Utils.sendMessages(sender, Config.getLang("no-permission"));
                        }
                        return true;
                    }
                    case "debug" -> {
                        // This is not the author debug, this prints out info
                        if (sender.hasPermission("lockettepro.debug")) {
                            sender.sendMessage("LockettePro Debug Message");
                            // Basic
                            sender.sendMessage("LockettePro: " + getDescription().getVersion());
                            // Version
                            sender.sendMessage("Bukkit: " + "v" + Bukkit.getServer().getClass().getPackage().getName().split("v")[1]);
                            sender.sendMessage("Server version: " + Bukkit.getVersion());
                            // Config
                            sender.sendMessage("UUID: " + Config.isUuidEnabled());
                            sender.sendMessage("Expire: " + Config.isLockExpire() + " " + (Config.isLockExpire() ? Config.getLockExpireDays() : ""));
                            // ProtocolLib
                            sender.sendMessage("ProtocolLib info:");
                            if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
                                sender.sendMessage(" - ProtocolLib missing");
                            } else {
                                sender.sendMessage(" - ProtocolLib: " + Bukkit.getPluginManager().getPlugin("ProtocolLib").getDescription().getVersion());
                            }
                            // Other
                            sender.sendMessage("Linked plugins:");
                            boolean linked = false;
                            if (Dependency.worldguard != null) {
                                linked = true;
                                sender.sendMessage(" - Worldguard: " + Dependency.worldguard.getDescription().getVersion());
                            }
                            if (Dependency.vault != null) {
                                linked = true;
                                sender.sendMessage(" - Vault: " + Dependency.vault.getDescription().getVersion());
                            }
                            if (Bukkit.getPluginManager().getPlugin("CoreProtect") != null) {
                                linked = true;
                                sender.sendMessage(" - CoreProtect: " + Bukkit.getPluginManager().getPlugin("CoreProtect").getDescription().getVersion());
                            }
                            if (!linked) {
                                sender.sendMessage(" - none");
                            }
                        } else {
                            Utils.sendMessages(sender, Config.getLang("no-permission"));
                        }
                        return true;
                    }
                }
                // The following commands requires player
                if (!(sender instanceof Player)) {
                    Utils.sendMessages(sender, Config.getLang("command-usage"));
                    return false;
                }
                Player player = (Player) sender;
                String sub = args[0].toLowerCase(Locale.ROOT);
                switch (sub) {
                    case "1":
                    case "2":
                    case "3":
                    case "4":
                        if (player.hasPermission("lockettepro.edit")) {
                            StringBuilder message = new StringBuilder();
                            Block block = Utils.getSelectedSign(player);
                            if (block == null) {
                                Utils.sendMessages(player, Config.getLang("no-sign-selected"));
                            } else if (!LocketteProAPI.isSign(block) || !(player.hasPermission("lockettepro.edit.admin") || LocketteProAPI.isOwnerOfSign(block, player))) {
                                Utils.sendMessages(player, Config.getLang("sign-need-reselect"));
                            } else {
                                for (int i = 1; i < args.length; i++) {
                                    message.append(args[i]);
                                }
                                message = new StringBuilder(ChatColor.translateAlternateColorCodes('&', message.toString()));
                                if (!player.hasPermission("lockettepro.admin.edit") && !debug && message.length() > 18) {
                                    Utils.sendMessages(player, Config.getLang("line-is-too-long"));
                                    return true;
                                }
                                if (LocketteProAPI.isLockSign(block)) {
                                    switch (args[0]) {
                                        case "1":
                                            if (!debug || !player.hasPermission("lockettepro.admin.edit")) {
                                                Utils.sendMessages(player, Config.getLang("cannot-change-this-line"));
                                                break;
                                            }
                                        case "2":
                                            if (!player.hasPermission("lockettepro.admin.edit")) {
                                                Utils.sendMessages(player, Config.getLang("cannot-change-this-line"));
                                                break;
                                            }
                                        case "3":
                                        case "4":
                                            Utils.setSignLine(block, Integer.parseInt(args[0]) - 1, message.toString());
                                            Utils.sendMessages(player, Config.getLang("sign-changed"));
                                            if (Config.isUuidEnabled()) {
                                                Block selectedSign = Utils.getSelectedSign(player);
                                                if (selectedSign != null)
                                                    Utils.updateUuidByUsername(selectedSign, Integer.parseInt(args[0]) - 1);
                                            }
                                            Utils.refreshLockedContainerPdcTagLater(LocketteProAPI.getAttachedBlock(block));
                                            break;
                                    }
                                } else if (LocketteProAPI.isAdditionalSign(block)) {
                                    switch (args[0]) {
                                        case "1":
                                            if (!debug || !player.hasPermission("lockettepro.admin.edit")) {
                                                Utils.sendMessages(player, Config.getLang("cannot-change-this-line"));
                                                break;
                                            }
                                        case "2":
                                        case "3":
                                        case "4":
                                            Utils.setSignLine(block, Integer.parseInt(args[0]) - 1, message.toString());
                                            Utils.sendMessages(player, Config.getLang("sign-changed"));
                                            if (Config.isUuidEnabled()) {
                                                Block selectedSign = Utils.getSelectedSign(player);
                                                if (selectedSign != null)
                                                    Utils.updateUuidByUsername(selectedSign, Integer.parseInt(args[0]) - 1);
                                            }
                                            Utils.refreshLockedContainerPdcTagLater(LocketteProAPI.getAttachedBlock(block));
                                            break;
                                    }
                                } else {
                                    Utils.sendMessages(player, Config.getLang("sign-need-reselect"));
                                }
                            }
                        } else {
                            Utils.sendMessages(player, Config.getLang("no-permission"));
                        }
                        break;
                    case "on":
                        handlePdcLockOn(player);
                        break;
                    case "info":
                        handlePdcInfo(player);
                        break;
                    case "rename":
                        handlePdcRename(player, args);
                        break;
                    case "permission":
                        handlePdcPermission(player, args);
                        break;
                    case "clone":
                        handlePdcClone(player);
                        break;
                    case "force":
                        if (debug && player.hasPermission("lockettepro.debug")) {
                            Block selectedSign = Utils.getSelectedSign(player);
                            if (selectedSign != null)
                                Utils.setSignLine(selectedSign, Integer.parseInt(args[1]), args[2]);
                            break;
                        }
                    case "update":
                        if (debug && player.hasPermission("lockettepro.debug")) {
                            Block selectedSign = Utils.getSelectedSign(player);
                            if (selectedSign != null)
                                Utils.updateSign(selectedSign);
                            break;
                        }
                    case "uuid":
                        if (debug && player.hasPermission("lockettepro.debug")) {
                            Utils.updateUuidOnSign(Utils.getSelectedSign(player));
                            break;
                        }
                    default:
                        Utils.sendMessages(player, Config.getLang("command-usage"));
                        break;
                }
            }
        }
        return true;
    }

    private void handlePdcLockOn(Player player) {
        Block block = ContainerPdcLockManager.getTargetedContainer(player);
        if (block == null) {
            Utils.sendMessages(player, Config.getLang("pdc-target-container-needed"));
            return;
        }

        ContainerPdcLockManager.LockData data = ContainerPdcLockManager.getLockData(block);
        if (data.hasPdcData() && data.isLocked()) {
            if (ContainerPdcLockManager.isOwner(block, player)) {
                Utils.sendMessages(player, Config.getLang("pdc-lock-already-enabled"));
            } else {
                Utils.sendMessages(player, Config.getLang("pdc-no-owner-permission"));
            }
            return;
        }

        if (LocketteProAPI.isLocked(block) && !LocketteProAPI.isOwner(block, player)) {
            Utils.sendMessages(player, Config.getLang("pdc-no-owner-permission"));
            Utils.playAccessDenyEffect(player, block);
            return;
        }

        if (ContainerPdcLockManager.lockWithOwner(block, player)) {
            Utils.sendMessages(player, Config.getLang("pdc-lock-enabled"));
            Utils.refreshLockedContainerPdcTagLater(block);
        } else {
            Utils.sendMessages(player, Config.getLang("pdc-lock-failed"));
        }
    }

    private void handlePdcInfo(Player player) {
        Block block = ContainerPdcLockManager.getTargetedContainer(player);
        if (block == null) {
            Utils.sendMessages(player, Config.getLang("pdc-target-container-needed"));
            return;
        }

        ContainerPdcLockManager.LockData data = ContainerPdcLockManager.getLockData(block);
        if (!data.hasPdcData() || !data.isLocked()) {
            Utils.sendMessages(player, Config.getLang("pdc-not-locked"));
            return;
        }

        Utils.sendMessages(player, Config.getLang("pdc-info-header"));
        String customName = ContainerPdcLockManager.getContainerCustomName(block);
        if (customName == null || customName.isBlank()) {
            customName = ChatColor.GRAY + "(none)" + ChatColor.RESET;
        }
        player.sendMessage(ChatColor.GOLD + " - Name: " + ChatColor.RESET + customName);
        player.sendMessage(ChatColor.GOLD + " - Owners: " + ChatColor.RESET + ContainerPdcLockManager.formatList(
                ContainerPdcLockManager.summarizePermissions(data.permissions(),
                        EnumSet.of(ContainerPdcLockManager.PermissionAccess.OWNER))));
        player.sendMessage(ChatColor.GOLD + " - RW: " + ChatColor.RESET + ContainerPdcLockManager.formatList(
                ContainerPdcLockManager.summarizePermissions(data.permissions(),
                        EnumSet.of(ContainerPdcLockManager.PermissionAccess.READ_WRITE))));
        player.sendMessage(ChatColor.GOLD + " - RO: " + ChatColor.RESET + ContainerPdcLockManager.formatList(
                ContainerPdcLockManager.summarizePermissions(data.permissions(),
                        EnumSet.of(ContainerPdcLockManager.PermissionAccess.READ_ONLY))));
        player.sendMessage(ChatColor.GOLD + " - locked_container: " + ChatColor.RESET + (LocketteProAPI.isContainerEffectivelyLocked(block) ? "true" : "false"));
    }

    private void handlePdcRename(Player player, String[] args) {
        Block block = ContainerPdcLockManager.getTargetedContainer(player);
        if (block == null) {
            Utils.sendMessages(player, Config.getLang("pdc-target-container-needed"));
            return;
        }

        ContainerPdcLockManager.LockData data = ContainerPdcLockManager.getLockData(block);
        if (!data.hasPdcData() || !data.isLocked()) {
            Utils.sendMessages(player, Config.getLang("pdc-not-locked"));
            return;
        }

        if (!ContainerPdcLockManager.isOwner(block, player)) {
            Utils.sendMessages(player, Config.getLang("pdc-no-owner-permission"));
            Utils.playAccessDenyEffect(player, block);
            return;
        }

        if (args.length < 2) {
            Utils.sendMessages(player, Config.getLang("pdc-rename-usage"));
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) builder.append(' ');
            builder.append(args[i]);
        }
        String renamed = Utils.colorize(builder.toString());
        if (renamed.isBlank()) {
            renamed = null;
        }

        if (ContainerPdcLockManager.setContainerCustomName(block, renamed)) {
            Utils.sendMessages(player, Config.getLang("pdc-rename-updated"));
        } else {
            Utils.sendMessages(player, Config.getLang("pdc-rename-failed"));
        }
    }

    private void handlePdcPermission(Player player, String[] args) {
        Block block = ContainerPdcLockManager.getTargetedContainer(player);
        if (block == null) {
            Utils.sendMessages(player, Config.getLang("pdc-target-container-needed"));
            return;
        }

        ContainerPdcLockManager.LockData data = ContainerPdcLockManager.getLockData(block);
        if (!data.hasPdcData() || !data.isLocked()) {
            Utils.sendMessages(player, Config.getLang("pdc-not-locked"));
            return;
        }

        if (!ContainerPdcLockManager.isOwner(block, player)) {
            Utils.sendMessages(player, Config.getLang("pdc-no-owner-permission"));
            Utils.playAccessDenyEffect(player, block);
            return;
        }

        if (args.length != 2) {
            Utils.sendMessages(player, Config.getLang("pdc-permission-usage"));
            return;
        }

        ContainerPdcLockManager.PermissionMutation mutation = ContainerPdcLockManager.parsePermissionMutation(args[1]);
        if (mutation == null) {
            Utils.sendMessages(player, Config.getLang("pdc-permission-invalid"));
            return;
        }

        LinkedHashMap<String, ContainerPdcLockManager.PermissionAccess> preview = new LinkedHashMap<>(data.permissions());
        if (mutation.access() == ContainerPdcLockManager.PermissionAccess.NONE) {
            preview.remove(mutation.subject());
        } else {
            preview.put(mutation.subject(), mutation.access());
        }
        boolean hasOwner = preview.values().stream().anyMatch(v -> v == ContainerPdcLockManager.PermissionAccess.OWNER);
        if (!preview.isEmpty() && !hasOwner) {
            Utils.sendMessages(player, Config.getLang("pdc-owner-required"));
            return;
        }

        ContainerPdcLockManager.applyPermissionMutation(block, mutation);
        Utils.refreshLockedContainerPdcTagLater(block);
        Utils.sendMessages(player, Config.getLang("pdc-permission-updated"));
    }

    private void handlePdcClone(Player player) {
        Block block = ContainerPdcLockManager.getTargetedContainer(player);
        if (block == null) {
            Utils.sendMessages(player, Config.getLang("pdc-target-container-needed"));
            return;
        }

        ContainerPdcLockManager.LockData data = ContainerPdcLockManager.getLockData(block);
        if (!data.hasPdcData() || !data.isLocked()) {
            Utils.sendMessages(player, Config.getLang("pdc-not-locked"));
            return;
        }

        if (!ContainerPdcLockManager.isOwner(block, player)) {
            Utils.sendMessages(player, Config.getLang("pdc-no-owner-permission"));
            Utils.playAccessDenyEffect(player, block);
            return;
        }

        ItemStack item = ContainerPdcLockManager.buildCloneItem(block);
        if (item == null) {
            Utils.sendMessages(player, Config.getLang("pdc-clone-failed"));
            return;
        }
        Map<Integer, ItemStack> left = player.getInventory().addItem(item);
        if (!left.isEmpty()) {
            left.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
        }
        Utils.sendMessages(player, Config.getLang("pdc-clone-given"));
    }

}
