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
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class LockettePro extends JavaPlugin {

    private static final String PERM_PDC_ON = "lockettepro.pdc.on";
    private static final String PERM_PDC_OFF = "lockettepro.pdc.off";
    private static final String PERM_PDC_INFO = "lockettepro.pdc.info";
    private static final String PERM_PDC_RENAME = "lockettepro.pdc.rename";
    private static final String PERM_PDC_PERMISSION = "lockettepro.pdc.permission";
    private static final String PERM_PDC_CLONE = "lockettepro.pdc.clone";
    private static final String PERM_PDC_GROUP = "lockettepro.pdc.group";

    @FunctionalInterface
    private interface PlayerSubCommandExecutor {
        void execute(Player player, String[] args);
    }

    private static final class PlayerSubCommand {
        private final String permission;
        private final PlayerSubCommandExecutor executor;

        private PlayerSubCommand(String permission, PlayerSubCommandExecutor executor) {
            this.permission = permission;
            this.executor = executor;
        }
    }

    private static Plugin plugin;
    private boolean debug = false;
    private static boolean needcheckhand = true;
    private final Map<String, PlayerSubCommand> playerSubCommands = new LinkedHashMap<>();

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
        PermissionGroupStore.initialize(this);
        ContainerPdcLockManager.refreshRuntimeCacheConfig();
        registerPlayerSubCommands();
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
        PermissionGroupStore.shutdown();
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
        if (sender.hasPermission("lockettepro.reload")) commands.add("reload");
        if (sender.hasPermission("lockettepro.version")) commands.add("version");
        if (sender.hasPermission("lockettepro.edit")) {
            commands.add("1");
            commands.add("2");
            commands.add("3");
            commands.add("4");
        }
        if (sender.hasPermission("lockettepro.debug")) {
            commands.add("debug");
            commands.add("uuid");
            commands.add("update");
        }
        for (Map.Entry<String, PlayerSubCommand> entry : playerSubCommands.entrySet()) {
            if (sender.hasPermission(entry.getValue().permission)) {
                commands.add(entry.getKey());
            }
        }
        if (args != null && args.length == 1) {
            List<String> list = new ArrayList<>();
            for (String s : commands) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    list.add(s);
                }
            }
            return list;
        }
        if (args != null && args.length >= 2 && "permission".equalsIgnoreCase(args[0]) && sender.hasPermission(PERM_PDC_PERMISSION)) {
            return tabCompletePermission(args, sender);
        }
        if (args != null && args.length == 2 && "group".equalsIgnoreCase(args[0]) && sender.hasPermission(PERM_PDC_GROUP)) {
            return List.of("create", "delete", "add", "remove", "info", "list");
        }
        if (args != null && args.length == 3 && "group".equalsIgnoreCase(args[0]) && sender.hasPermission(PERM_PDC_GROUP)) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if ("delete".equals(action) || "add".equals(action) || "remove".equals(action) || "info".equals(action)) {
                var own = PermissionGroupStore.getOwnedGroup((sender instanceof Player p) ? p.getUniqueId() : null);
                if (own != null) {
                    return List.of(own.name());
                }
            }
        }
        return null;
    }

    private List<String> tabCompletePermission(String[] args, CommandSender sender) {
        List<String> modes = List.of("xx:", "rw:", "ro:", "--:");
        if (args.length == 2) {
            String token = args[1] == null ? "" : args[1];
            int split = token.indexOf(':');
            if (split < 0) {
                return filterByPrefix(modes, token);
            }

            String modePrefix = token.substring(0, split + 1);
            String subjectPrefix = token.substring(split + 1);
            if (!modes.contains(modePrefix.toLowerCase(Locale.ROOT))) {
                return filterByPrefix(modes, token);
            }
            return filterByPrefix(
                    buildPermissionSubjectCandidates(sender).stream()
                            .map(subject -> modePrefix + subject)
                            .toList(),
                    token
            );
        }

        if (args.length == 3 && args[1] != null && args[1].contains(":")) {
            return filterByPrefix(buildPermissionSubjectCandidates(sender), args[2]);
        }

        return List.of();
    }

    private List<String> buildPermissionSubjectCandidates(CommandSender sender) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        if (sender instanceof Player player) {
            PermissionGroupStore.GroupSnapshot own = PermissionGroupStore.getOwnedGroup(player.getUniqueId());
            if (own != null) {
                candidates.add("[g:" + own.name() + "]");
            }
        }

        List<String> onlineNames = Bukkit.getOnlinePlayers()
                .stream()
                .map(Player::getName)
                .sorted(String::compareToIgnoreCase)
                .toList();
        candidates.addAll(onlineNames);

        candidates.add("#hopper");
        candidates.add("#redstone");

        candidates.addAll(Config.getEveryoneSignStrings());
        candidates.addAll(Config.getContainerBypassSignStrings());

        return new ArrayList<>(candidates);
    }

    private List<String> filterByPrefix(List<String> candidates, String rawPrefix) {
        String prefix = rawPrefix == null ? "" : rawPrefix.toLowerCase(Locale.ROOT);
        List<String> list = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                list.add(candidate);
            }
        }
        return list;
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
                            ContainerPdcLockManager.refreshRuntimeCacheConfig();
                            PermissionGroupStore.reloadAutoSaveTask();
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
                if (dispatchPlayerSubCommand(player, sub, args)) {
                    return true;
                }
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

    private void registerPlayerSubCommands() {
        playerSubCommands.clear();
        playerSubCommands.put("on", new PlayerSubCommand(PERM_PDC_ON, (player, args) -> handlePdcLockOn(player)));
        playerSubCommands.put("off", new PlayerSubCommand(PERM_PDC_OFF, (player, args) -> handlePdcLockOff(player)));
        playerSubCommands.put("info", new PlayerSubCommand(PERM_PDC_INFO, (player, args) -> handlePdcInfo(player)));
        playerSubCommands.put("rename", new PlayerSubCommand(PERM_PDC_RENAME, this::handlePdcRename));
        playerSubCommands.put("permission", new PlayerSubCommand(PERM_PDC_PERMISSION, this::handlePdcPermission));
        playerSubCommands.put("clone", new PlayerSubCommand(PERM_PDC_CLONE, (player, args) -> handlePdcClone(player)));
        playerSubCommands.put("group", new PlayerSubCommand(PERM_PDC_GROUP, this::handleGroupCommand));
    }

    private boolean dispatchPlayerSubCommand(Player player, String sub, String[] args) {
        PlayerSubCommand command = playerSubCommands.get(sub);
        if (command == null) {
            return false;
        }

        if (!player.hasPermission(command.permission)) {
            Utils.sendMessages(player, Config.getLang("no-permission"));
            return true;
        }

        command.executor.execute(player, args);
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

        if (LocketteProAPI.isLockedBySign(block) && !LocketteProAPI.isOwnerBySign(block, player)) {
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

    private void handlePdcLockOff(Player player) {
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

        boolean owner = ContainerPdcLockManager.isOwner(block, player);
        boolean adminOverride = player.hasPermission("lockettepro.admin.break");
        if (!owner && !adminOverride) {
            Utils.sendMessages(player, Config.getLang("pdc-no-owner-permission"));
            Utils.playAccessDenyEffect(player, block);
            return;
        }

        if (ContainerPdcLockManager.writeLockData(block, false, java.util.Collections.emptyMap())) {
            Utils.sendMessages(player, Config.getLang("pdc-lock-disabled"));
            Utils.refreshLockedContainerPdcTagLater(block);
        } else {
            Utils.sendMessages(player, Config.getLang("pdc-lock-disable-failed"));
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

        if (args.length < 2) {
            Utils.sendMessages(player, Config.getLang("pdc-permission-usage"));
            return;
        }

        String mutationRaw = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (mutationRaw.isEmpty()) {
            Utils.sendMessages(player, Config.getLang("pdc-permission-usage"));
            return;
        }

        ContainerPdcLockManager.PermissionMutation mutation = ContainerPdcLockManager.parsePermissionMutation(mutationRaw);
        if (mutation == null) {
            Utils.sendMessages(player, Config.getLang("pdc-permission-invalid"));
            return;
        }

        if (ContainerPdcLockManager.isSelfSubject(player, mutation.subject())
                && mutation.access() != ContainerPdcLockManager.PermissionAccess.OWNER) {
            Utils.sendMessages(player, Config.getLang("pdc-self-owner-change-blocked"));
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

        ItemStack item = ContainerPdcLockManager.buildCloneItem(block, player);
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

    private void handleGroupCommand(Player player, String[] args) {
        if (args.length < 2) {
            Utils.sendMessages(player, Config.getLang("group-usage"));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create" -> {
                if (args.length != 3) {
                    Utils.sendMessages(player, Config.getLang("group-create-usage"));
                    return;
                }
                sendGroupResult(player, PermissionGroupStore.createGroup(player, args[2]));
            }
            case "delete" -> {
                if (args.length != 3) {
                    Utils.sendMessages(player, Config.getLang("group-delete-usage"));
                    return;
                }
                sendGroupResult(player, PermissionGroupStore.deleteGroup(player, args[2]));
            }
            case "add" -> {
                if (args.length != 4) {
                    Utils.sendMessages(player, Config.getLang("group-add-usage"));
                    return;
                }
                sendGroupResult(player, PermissionGroupStore.addNode(player, args[2], args[3]));
            }
            case "remove" -> {
                if (args.length != 4) {
                    Utils.sendMessages(player, Config.getLang("group-remove-usage"));
                    return;
                }
                sendGroupResult(player, PermissionGroupStore.removeNode(player, args[2], args[3]));
            }
            case "info" -> {
                if (args.length != 3) {
                    Utils.sendMessages(player, Config.getLang("group-info-usage"));
                    return;
                }
                PermissionGroupStore.GroupSnapshot group = PermissionGroupStore.getGroup(args[2]);
                if (group == null) {
                    Utils.sendMessages(player, Config.getLang("group-not-found"));
                    return;
                }
                if (!group.owner().equals(player.getUniqueId())) {
                    Utils.sendMessages(player, Config.getLang("group-not-owner"));
                    return;
                }
                sendGroupInfo(player, group);
            }
            case "list" -> {
                PermissionGroupStore.GroupSnapshot own = PermissionGroupStore.getOwnedGroup(player.getUniqueId());
                if (own == null) {
                    Utils.sendMessages(player, Config.getLang("group-list-empty"));
                    return;
                }
                Utils.sendMessages(player, Config.getLang("group-list-header"));
                player.sendMessage(ChatColor.GOLD + " - " + own.name() + ChatColor.RESET + " (" + own.nodes().size() + " nodes)");
            }
            default -> Utils.sendMessages(player, Config.getLang("group-usage"));
        }
    }

    private void sendGroupInfo(Player player, PermissionGroupStore.GroupSnapshot group) {
        Utils.sendMessages(player, Config.getLang("group-info-header"));
        player.sendMessage(ChatColor.GOLD + " - Name: " + ChatColor.RESET + group.name());
        UUID ownerId = group.owner();
        String ownerText = ContainerPdcLockManager.describeSubject(ownerId.toString());
        player.sendMessage(ChatColor.GOLD + " - Owner: " + ChatColor.RESET + ownerText);
        if (group.nodes().isEmpty()) {
            player.sendMessage(ChatColor.GOLD + " - Nodes: " + ChatColor.RESET + ChatColor.GRAY + "(none)" + ChatColor.RESET);
            return;
        }
        String rendered = String.join(
                ChatColor.GRAY + ", " + ChatColor.RESET,
                group.nodes().stream().map(ContainerPdcLockManager::describeSubject).toList()
        );
        player.sendMessage(ChatColor.GOLD + " - Nodes: " + ChatColor.RESET + rendered);
    }

    private void sendGroupResult(Player player, PermissionGroupStore.GroupEditResult result) {
        switch (result) {
            case SUCCESS -> Utils.sendMessages(player, Config.getLang("group-op-success"));
            case INVALID_NAME -> Utils.sendMessages(player, Config.getLang("group-name-invalid"));
            case ALREADY_HAS_GROUP -> Utils.sendMessages(player, Config.getLang("group-already-has-group"));
            case NAME_EXISTS -> Utils.sendMessages(player, Config.getLang("group-name-exists"));
            case NOT_FOUND -> Utils.sendMessages(player, Config.getLang("group-not-found"));
            case NOT_OWNER -> Utils.sendMessages(player, Config.getLang("group-not-owner"));
            case INVALID_NODE -> Utils.sendMessages(player, Config.getLang("group-node-invalid"));
            case NODE_EXISTS -> Utils.sendMessages(player, Config.getLang("group-node-exists"));
            case NODE_NOT_FOUND -> Utils.sendMessages(player, Config.getLang("group-node-not-found"));
            case EMPTY_NODES -> Utils.sendMessages(player, Config.getLang("group-node-empty"));
        }
    }

}
