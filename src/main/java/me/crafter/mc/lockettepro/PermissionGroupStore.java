package me.crafter.mc.lockettepro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PermissionGroupStore {

    private static final Pattern GROUP_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,32}$");
    private static final Pattern GROUP_REFERENCE_PATTERN = Pattern.compile("^\\[g:([^\\]]+)]$", Pattern.CASE_INSENSITIVE);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Object MUTATE_LOCK = new Object();
    private static final Object SAVE_STATE_LOCK = new Object();
    private static final Object SAVE_IO_LOCK = new Object();
    private static final Map<String, GroupRecord> GROUPS_BY_KEY = new HashMap<>();
    private static final Map<UUID, String> GROUP_KEY_BY_OWNER = new HashMap<>();

    private static Plugin plugin;
    private static Path storagePath;
    private static CompatibleTask autosaveTask;
    private static boolean saveWorkerRunning;
    private static boolean saveWorkerQueued;
    private static long mutationVersion;
    private static long persistedVersion;
    private static long storageVersion;
    private static long persistedStorageVersion;

    private PermissionGroupStore() {
    }

    public enum GroupEditResult {
        SUCCESS,
        INVALID_NAME,
        ALREADY_HAS_GROUP,
        NAME_EXISTS,
        NOT_FOUND,
        NOT_OWNER,
        INVALID_NODE,
        NODE_EXISTS,
        NODE_NOT_FOUND,
        EMPTY_NODES
    }

    public static final class GroupSnapshot {
        private final String name;
        private final UUID owner;
        private final List<String> nodes;

        private GroupSnapshot(String name, UUID owner, List<String> nodes) {
            this.name = name;
            this.owner = owner;
            this.nodes = nodes;
        }

        public String name() {
            return name;
        }

        public UUID owner() {
            return owner;
        }

        public List<String> nodes() {
            return nodes;
        }
    }

    private static final class GroupRecord {
        private final String name;
        private final UUID owner;
        private final LinkedHashSet<String> nodes;

        private GroupRecord(String name, UUID owner, LinkedHashSet<String> nodes) {
            this.name = name;
            this.owner = owner;
            this.nodes = nodes;
        }

        private GroupSnapshot snapshot() {
            return new GroupSnapshot(this.name, this.owner, Collections.unmodifiableList(new ArrayList<>(this.nodes)));
        }
    }

    public static void initialize(Plugin pluginInstance) {
        plugin = pluginInstance;
        storagePath = resolveStoragePath();
        loadFromDisk();
        startAutoSaveTask();
    }

    public static void shutdown() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        saveNow();
    }

    public static void reloadAutoSaveTask() {
        if (plugin == null) return;
        Path newPath = resolveStoragePath();
        synchronized (MUTATE_LOCK) {
            if (!newPath.equals(storagePath)) {
                storagePath = newPath;
                storageVersion++;
            }
        }
        startAutoSaveTask();
        saveAsync();
    }

    private static void startAutoSaveTask() {
        if (plugin == null) return;
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        long ticks = Math.max(20L, Config.getPermissionGroupsAutosaveSeconds() * 20L);
        autosaveTask = CompatibleScheduler.runTaskTimer(plugin, null, PermissionGroupStore::saveAsync, ticks, ticks);
    }

    private static void loadFromDisk() {
        synchronized (MUTATE_LOCK) {
            GROUPS_BY_KEY.clear();
            GROUP_KEY_BY_OWNER.clear();
            mutationVersion = 0L;
            persistedVersion = 0L;
            storageVersion = 0L;
            persistedStorageVersion = 0L;
        }

        if (storagePath == null || !Files.exists(storagePath)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(storagePath, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonArray groups = root.getAsJsonArray("groups");
            if (groups == null) {
                return;
            }

            int loaded = 0;
            synchronized (MUTATE_LOCK) {
                for (JsonElement groupElement : groups) {
                    if (!groupElement.isJsonObject()) continue;
                    JsonObject obj = groupElement.getAsJsonObject();

                    String name = readString(obj, "name");
                    String ownerRaw = readString(obj, "owner");
                    if (name == null || ownerRaw == null) continue;

                    String validatedName = validateAndNormalizeGroupName(name);
                    if (validatedName == null) continue;

                    UUID owner;
                    try {
                        owner = UUID.fromString(ownerRaw);
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }

                    String key = toGroupKey(validatedName);
                    if (GROUPS_BY_KEY.containsKey(key) || GROUP_KEY_BY_OWNER.containsKey(owner)) {
                        continue;
                    }

                    LinkedHashSet<String> nodes = new LinkedHashSet<>();
                    JsonArray nodesArray = obj.getAsJsonArray("nodes");
                    if (nodesArray != null) {
                        for (JsonElement nodeElement : nodesArray) {
                            if (!nodeElement.isJsonPrimitive()) continue;
                            String nodeRaw = nodeElement.getAsString();
                            String node = normalizeGroupNode(nodeRaw);
                            if (node != null) {
                                nodes.add(node);
                            }
                        }
                    }

                    GroupRecord record = new GroupRecord(validatedName, owner, nodes);
                    GROUPS_BY_KEY.put(key, record);
                    GROUP_KEY_BY_OWNER.put(owner, key);
                    loaded++;
                }
                mutationVersion = 0L;
                persistedVersion = 0L;
                persistedStorageVersion = storageVersion;
            }
            if (loaded > 0) {
                plugin.getLogger().info("Loaded " + loaded + " permission group(s).");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load permission groups: " + e.getMessage());
        }
    }

    private static String readString(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonPrimitive()) return null;
        return element.getAsString();
    }

    private static Path resolveStoragePath() {
        return plugin.getDataFolder().toPath().resolve(Config.getPermissionGroupsFile());
    }

    private static void saveAsync() {
        if (plugin == null) return;
        synchronized (SAVE_STATE_LOCK) {
            if (saveWorkerRunning) {
                saveWorkerQueued = true;
                return;
            }
            saveWorkerRunning = true;
            saveWorkerQueued = false;
        }
        CompatibleScheduler.runTaskAsynchronously(plugin, PermissionGroupStore::runSaveWorker);
    }

    private static void runSaveWorker() {
        try {
            while (true) {
                saveNow();
                synchronized (SAVE_STATE_LOCK) {
                    if (!saveWorkerQueued) {
                        saveWorkerRunning = false;
                        return;
                    }
                    saveWorkerQueued = false;
                }
            }
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.getLogger().warning("Permission group save worker failed: " + t.getMessage());
            }
            synchronized (SAVE_STATE_LOCK) {
                saveWorkerRunning = false;
                if (saveWorkerQueued && plugin != null) {
                    saveWorkerQueued = false;
                    saveWorkerRunning = true;
                    CompatibleScheduler.runTaskAsynchronously(plugin, PermissionGroupStore::runSaveWorker);
                }
            }
        }
    }

    public static void saveNow() {
        if (plugin == null || storagePath == null) return;

        long targetVersion;
        long targetStorageVersion;
        Path targetPath;
        List<GroupRecord> snapshot;

        synchronized (MUTATE_LOCK) {
            if (mutationVersion == persistedVersion && storageVersion == persistedStorageVersion) {
                return;
            }
            targetVersion = mutationVersion;
            targetStorageVersion = storageVersion;
            targetPath = storagePath;
            snapshot = new ArrayList<>(GROUPS_BY_KEY.values());
        }

        snapshot.sort(Comparator.comparing(g -> g.name.toLowerCase(Locale.ROOT)));

        JsonObject root = new JsonObject();
        root.addProperty("format", "lockettepro-permission-groups-v1");
        JsonArray groups = new JsonArray();
        for (GroupRecord group : snapshot) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", group.name);
            obj.addProperty("owner", group.owner.toString());
            JsonArray nodes = new JsonArray();
            for (String node : group.nodes) {
                nodes.add(node);
            }
            obj.add("nodes", nodes);
            groups.add(obj);
        }
        root.add("groups", groups);

        synchronized (SAVE_IO_LOCK) {
            synchronized (MUTATE_LOCK) {
                if (targetStorageVersion < storageVersion) {
                    return;
                }
                if (targetStorageVersion < persistedStorageVersion) {
                    return;
                }
                if (targetStorageVersion == persistedStorageVersion && targetVersion <= persistedVersion) {
                    return;
                }
            }
            try {
                if (targetPath.getParent() != null) {
                    Files.createDirectories(targetPath.getParent());
                }
                try (BufferedWriter writer = Files.newBufferedWriter(
                        targetPath,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                )) {
                    GSON.toJson(root, writer);
                }
                synchronized (MUTATE_LOCK) {
                    if (targetStorageVersion > persistedStorageVersion) {
                        persistedStorageVersion = targetStorageVersion;
                        persistedVersion = targetVersion;
                    } else if (targetStorageVersion == persistedStorageVersion && targetVersion > persistedVersion) {
                        persistedVersion = targetVersion;
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save permission groups: " + e.getMessage());
            }
        }
    }

    public static GroupEditResult createGroup(Player owner, String rawName) {
        if (owner == null) return GroupEditResult.NOT_OWNER;
        String groupName = validateAndNormalizeGroupName(rawName);
        if (groupName == null) {
            return GroupEditResult.INVALID_NAME;
        }

        UUID ownerId = owner.getUniqueId();
        String groupKey = toGroupKey(groupName);

        synchronized (MUTATE_LOCK) {
            if (GROUP_KEY_BY_OWNER.containsKey(ownerId)) {
                return GroupEditResult.ALREADY_HAS_GROUP;
            }
            if (GROUPS_BY_KEY.containsKey(groupKey)) {
                return GroupEditResult.NAME_EXISTS;
            }

            LinkedHashSet<String> nodes = new LinkedHashSet<>();
            nodes.add(ownerId.toString());
            GroupRecord record = new GroupRecord(groupName, ownerId, nodes);
            GROUPS_BY_KEY.put(groupKey, record);
            GROUP_KEY_BY_OWNER.put(ownerId, groupKey);
            markMutated();
            return GroupEditResult.SUCCESS;
        }
    }

    public static GroupEditResult deleteGroup(Player operator, String rawName) {
        if (operator == null) return GroupEditResult.NOT_OWNER;
        String groupName = validateAndNormalizeGroupName(rawName);
        if (groupName == null) {
            return GroupEditResult.INVALID_NAME;
        }

        synchronized (MUTATE_LOCK) {
            GroupRecord group = GROUPS_BY_KEY.get(toGroupKey(groupName));
            if (group == null) {
                return GroupEditResult.NOT_FOUND;
            }
            if (!group.owner.equals(operator.getUniqueId())) {
                return GroupEditResult.NOT_OWNER;
            }

            GROUPS_BY_KEY.remove(toGroupKey(groupName));
            GROUP_KEY_BY_OWNER.remove(group.owner);
            markMutated();
            return GroupEditResult.SUCCESS;
        }
    }

    public static GroupEditResult addNode(Player operator, String rawGroupName, String rawNode) {
        if (operator == null) return GroupEditResult.NOT_OWNER;
        String groupName = validateAndNormalizeGroupName(rawGroupName);
        if (groupName == null) {
            return GroupEditResult.INVALID_NAME;
        }

        String node = normalizeGroupNode(rawNode);
        if (node == null) {
            return GroupEditResult.INVALID_NODE;
        }

        synchronized (MUTATE_LOCK) {
            GroupRecord group = GROUPS_BY_KEY.get(toGroupKey(groupName));
            if (group == null) {
                return GroupEditResult.NOT_FOUND;
            }
            if (!group.owner.equals(operator.getUniqueId())) {
                return GroupEditResult.NOT_OWNER;
            }
            if (group.nodes.contains(node)) {
                return GroupEditResult.NODE_EXISTS;
            }

            LinkedHashSet<String> newNodes = new LinkedHashSet<>(group.nodes);
            newNodes.add(node);
            GroupRecord newRecord = new GroupRecord(group.name, group.owner, newNodes);
            GROUPS_BY_KEY.put(toGroupKey(group.name), newRecord);
            markMutated();
            return GroupEditResult.SUCCESS;
        }
    }

    public static GroupEditResult removeNode(Player operator, String rawGroupName, String rawNode) {
        if (operator == null) return GroupEditResult.NOT_OWNER;
        String groupName = validateAndNormalizeGroupName(rawGroupName);
        if (groupName == null) {
            return GroupEditResult.INVALID_NAME;
        }

        String node = normalizeGroupNode(rawNode);
        if (node == null) {
            return GroupEditResult.INVALID_NODE;
        }

        synchronized (MUTATE_LOCK) {
            GroupRecord group = GROUPS_BY_KEY.get(toGroupKey(groupName));
            if (group == null) {
                return GroupEditResult.NOT_FOUND;
            }
            if (!group.owner.equals(operator.getUniqueId())) {
                return GroupEditResult.NOT_OWNER;
            }
            if (!group.nodes.contains(node)) {
                return GroupEditResult.NODE_NOT_FOUND;
            }

            LinkedHashSet<String> newNodes = new LinkedHashSet<>(group.nodes);
            newNodes.remove(node);
            if (newNodes.isEmpty()) {
                return GroupEditResult.EMPTY_NODES;
            }

            GroupRecord newRecord = new GroupRecord(group.name, group.owner, newNodes);
            GROUPS_BY_KEY.put(toGroupKey(group.name), newRecord);
            markMutated();
            return GroupEditResult.SUCCESS;
        }
    }

    public static GroupSnapshot getOwnedGroup(UUID owner) {
        if (owner == null) return null;
        synchronized (MUTATE_LOCK) {
            String groupKey = GROUP_KEY_BY_OWNER.get(owner);
            if (groupKey == null) return null;
            GroupRecord group = GROUPS_BY_KEY.get(groupKey);
            if (group == null) return null;
            return group.snapshot();
        }
    }

    public static GroupSnapshot getGroup(String groupNameRaw) {
        String groupName = validateAndNormalizeGroupName(groupNameRaw);
        if (groupName == null) return null;
        synchronized (MUTATE_LOCK) {
            GroupRecord group = GROUPS_BY_KEY.get(toGroupKey(groupName));
            return group == null ? null : group.snapshot();
        }
    }

    public static Collection<String> getAllGroupNames() {
        synchronized (MUTATE_LOCK) {
            List<String> names = new ArrayList<>();
            for (GroupRecord group : GROUPS_BY_KEY.values()) {
                names.add(group.name);
            }
            names.sort(String::compareToIgnoreCase);
            return names;
        }
    }

    private static void markMutated() {
        mutationVersion++;
    }

    public static boolean isGroupReference(String text) {
        if (text == null) return false;
        return GROUP_REFERENCE_PATTERN.matcher(text.trim()).matches();
    }

    public static String extractGroupName(String groupReference) {
        if (groupReference == null) return null;
        Matcher matcher = GROUP_REFERENCE_PATTERN.matcher(groupReference.trim());
        if (!matcher.matches()) return null;
        String name = matcher.group(1).trim();
        if (name.isEmpty()) return null;
        return name;
    }

    public static String normalizeGroupReferenceIfExists(String text) {
        String groupName = extractGroupName(text);
        if (groupName == null) return null;

        GroupSnapshot snapshot = getGroup(groupName);
        if (snapshot == null) {
            return null;
        }
        return "[g:" + snapshot.name() + "]";
    }

    public static boolean matchesPlayerGroupReference(String groupReference, Player player) {
        String groupName = extractGroupName(groupReference);
        if (groupName == null) return false;
        return groupAllowsPlayer(groupName, player);
    }

    public static boolean groupAllowsPlayer(String groupName, Player player) {
        if (player == null) return false;
        GroupSnapshot group = getGroup(groupName);
        if (group == null) return false;

        for (String node : group.nodes()) {
            if (nodeMatchesPlayer(node, player)) {
                return true;
            }
        }
        return false;
    }

    private static boolean nodeMatchesPlayer(String node, Player player) {
        if (node == null || node.isBlank()) return false;
        if (node.startsWith("#")) return false;
        if (isGroupReference(node)) return false;

        if (node.startsWith("[") && node.endsWith("]")) {
            if (Config.isEveryoneSignString(node) || Config.isEveryoneSignString(node.toLowerCase(Locale.ROOT))) {
                return true;
            }
            if (Dependency.isPermissionGroupOf(node, player, false)) {
                return true;
            }
            return Dependency.isScoreboardTeamOf(node, player);
        }

        try {
            UUID uuid = UUID.fromString(node);
            return player.getUniqueId().equals(uuid);
        } catch (IllegalArgumentException ignored) {
            return player.getName().equalsIgnoreCase(node);
        }
    }

    public static boolean groupAllowsEveryone(String groupName) {
        GroupSnapshot group = getGroup(groupName);
        if (group == null) return false;
        for (String node : group.nodes()) {
            if (node.startsWith("[") && node.endsWith("]")) {
                if (Config.isEveryoneSignString(node) || Config.isEveryoneSignString(node.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean groupHasContainerBypass(String groupName) {
        GroupSnapshot group = getGroup(groupName);
        if (group == null) return false;
        for (String node : group.nodes()) {
            if ("#hopper".equalsIgnoreCase(node)) {
                return true;
            }
            if (node.startsWith("[") && node.endsWith("]")) {
                if (Config.isContainerBypassSignString(node) || Config.isContainerBypassSignString(node.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean groupAllowsEntity(String groupName, String entityKey) {
        if (entityKey == null || entityKey.isBlank()) return false;
        GroupSnapshot group = getGroup(groupName);
        if (group == null) return false;
        for (String node : group.nodes()) {
            if (node.equalsIgnoreCase(entityKey)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeGroupNode(String rawNode) {
        if (rawNode == null) return null;
        if (isGroupReference(rawNode)) return null; // avoid nested group reference loops
        String node = ContainerPdcLockManager.normalizeSubject(rawNode);
        if (node == null || node.isBlank()) return null;
        if (isGroupReference(node)) return null;
        return node;
    }

    private static String validateAndNormalizeGroupName(String rawName) {
        if (rawName == null) return null;
        String name = rawName.trim();
        if (!GROUP_NAME_PATTERN.matcher(name).matches()) {
            return null;
        }
        return name;
    }

    private static String toGroupKey(String groupName) {
        return groupName.toLowerCase(Locale.ROOT);
    }
}
