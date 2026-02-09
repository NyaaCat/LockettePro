package me.crafter.mc.lockettepro;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class ContainerPdcLockManager {

    private static final String LOCKETTE_NAMESPACE = "lockettepro";

    private static final String LOCKED_KEY_STRING = "lockettepro:locked";
    private static final String LOCKED_KEY_DEFAULT_PATH = "locked";

    private static final String PERMISSION_KEY_PATH_PREFIX = "perm.";

    private static final String CLONE_MARKER_KEY_STRING = "lockettepro:perm_clone";
    private static final String CLONE_MARKER_DEFAULT_PATH = "perm_clone";
    private static final String CLONE_NAME_KEY_STRING = "lockettepro:perm_clone_name";
    private static final String CLONE_NAME_DEFAULT_PATH = "perm_clone_name";
    private static final String CLONE_PERMISSION_KEY_PATH_PREFIX = "clone_perm.";
    private static final String SUBJECT_HEX_ENCODING_PREFIX = "h_";

    private static final String ENTITY_HOPPER = "#hopper";
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
    private static final String PLAYER_NAME_CACHE_MISS = "";
    private static volatile Cache<String, LockData> runtimeLockDataCache = createRuntimeLockDataCache();
    private static volatile Cache<UUID, String> runtimePlayerNameCache = createRuntimePlayerNameCache();
    private static volatile int runtimeCacheConfigSignature = Integer.MIN_VALUE;

    private ContainerPdcLockManager() {
    }

    public enum PermissionAccess {
        NONE(0, "--"),
        READ_ONLY(1, "ro"),
        READ_WRITE(2, "rw"),
        OWNER(3, "xx");

        private final int power;
        private final String token;

        PermissionAccess(int power, String token) {
            this.power = power;
            this.token = token;
        }

        public boolean atLeast(PermissionAccess other) {
            return this.power >= other.power;
        }

        public String token() {
            return this.token;
        }

        public static PermissionAccess parseToken(String token) {
            if (token == null) return null;
            return switch (token.toLowerCase(Locale.ROOT)) {
                case "ro" -> READ_ONLY;
                case "rw" -> READ_WRITE;
                case "xx" -> OWNER;
                case "--" -> NONE;
                default -> null;
            };
        }
    }

    public static final class LockData {
        private final boolean hasPdcData;
        private final boolean locked;
        private final LinkedHashMap<String, PermissionAccess> permissions;

        private LockData(boolean hasPdcData, boolean locked, LinkedHashMap<String, PermissionAccess> permissions) {
            this.hasPdcData = hasPdcData;
            this.locked = locked;
            this.permissions = permissions;
        }

        public boolean hasPdcData() {
            return hasPdcData;
        }

        public boolean isLocked() {
            return locked;
        }

        public Map<String, PermissionAccess> permissions() {
            return Collections.unmodifiableMap(permissions);
        }
    }

    public static final class PermissionMutation {
        private final PermissionAccess access;
        private final String subject;

        private PermissionMutation(PermissionAccess access, String subject) {
            this.access = access;
            this.subject = subject;
        }

        public PermissionAccess access() {
            return access;
        }

        public String subject() {
            return subject;
        }
    }

    private static NamespacedKey lockedContainerKey() {
        return createNamespacedKey(Config.getLockedContainerPdcKeyString(), Utils.LOCKED_CONTAINER_PDC_KEY_STRING, "locked_container");
    }

    private static NamespacedKey lockedKey() {
        return createNamespacedKey(LOCKED_KEY_STRING, LOCKED_KEY_STRING, LOCKED_KEY_DEFAULT_PATH);
    }

    private static NamespacedKey cloneMarkerKey() {
        return createNamespacedKey(CLONE_MARKER_KEY_STRING, CLONE_MARKER_KEY_STRING, CLONE_MARKER_DEFAULT_PATH);
    }

    private static NamespacedKey cloneNameKey() {
        return createNamespacedKey(CLONE_NAME_KEY_STRING, CLONE_NAME_KEY_STRING, CLONE_NAME_DEFAULT_PATH);
    }

    private static NamespacedKey createPermissionKey(String subject) {
        return new NamespacedKey(LOCKETTE_NAMESPACE, PERMISSION_KEY_PATH_PREFIX + encodeSubject(subject));
    }

    private static NamespacedKey createClonePermissionKey(String subject) {
        return new NamespacedKey(LOCKETTE_NAMESPACE, CLONE_PERMISSION_KEY_PATH_PREFIX + encodeSubject(subject));
    }

    private static NamespacedKey createNamespacedKey(String raw, String fullDefault, String pathDefault) {
        String selected = raw;
        if (selected == null || selected.isBlank()) {
            selected = fullDefault;
        }
        selected = selected.trim().toLowerCase(Locale.ROOT);

        int split = selected.indexOf(':');
        if (split >= 0) {
            String namespace = selected.substring(0, split);
            String key = selected.substring(split + 1);
            if (!namespace.isBlank() && !key.isBlank()) {
                try {
                    return new NamespacedKey(namespace, key);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        String key = selected;
        if (split >= 0 && split + 1 < selected.length()) {
            key = selected.substring(split + 1);
        }
        if (key.isBlank()) {
            key = pathDefault;
        }

        try {
            return new NamespacedKey(LOCKETTE_NAMESPACE, key);
        } catch (IllegalArgumentException ignored) {
            return new NamespacedKey(LOCKETTE_NAMESPACE, pathDefault);
        }
    }

    private static Cache<String, LockData> createRuntimeLockDataCache() {
        CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
        if (Config.isRuntimeKvCacheEnabled()) {
            int ttl = Math.max(0, Config.getRuntimeKvCacheTtlMillis());
            int maxEntries = Math.max(256, Config.getRuntimeKvCacheMaxEntries());
            builder.maximumSize(maxEntries);
            if (ttl > 0) {
                builder.expireAfterWrite(ttl, TimeUnit.MILLISECONDS);
            }
        } else {
            builder.maximumSize(1);
            builder.expireAfterWrite(1, TimeUnit.MILLISECONDS);
        }
        return builder.build();
    }

    private static Cache<UUID, String> createRuntimePlayerNameCache() {
        return CacheBuilder.newBuilder()
                .maximumSize(8192)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();
    }

    private static int getRuntimeCacheConfigSignature() {
        int signature = Config.isRuntimeKvCacheEnabled() ? 1 : 0;
        signature = 31 * signature + Config.getRuntimeKvCacheTtlMillis();
        signature = 31 * signature + Config.getRuntimeKvCacheMaxEntries();
        return signature;
    }

    private static void ensureRuntimeCacheConfiguration() {
        int signature = getRuntimeCacheConfigSignature();
        if (runtimeCacheConfigSignature == signature) {
            return;
        }
        synchronized (ContainerPdcLockManager.class) {
            if (runtimeCacheConfigSignature == signature) {
                return;
            }
            runtimeLockDataCache = createRuntimeLockDataCache();
            runtimeCacheConfigSignature = signature;
        }
    }

    public static void refreshRuntimeCacheConfig() {
        synchronized (ContainerPdcLockManager.class) {
            runtimeLockDataCache = createRuntimeLockDataCache();
            runtimePlayerNameCache = createRuntimePlayerNameCache();
            runtimeCacheConfigSignature = getRuntimeCacheConfigSignature();
        }
    }

    public static void clearRuntimeCache() {
        ensureRuntimeCacheConfiguration();
        runtimeLockDataCache.invalidateAll();
        runtimePlayerNameCache.invalidateAll();
    }

    public static void invalidateRuntimeCache(Block block) {
        if (block == null) return;
        ensureRuntimeCacheConfiguration();
        if (!Config.isRuntimeKvCacheEnabled()) return;

        if (isContainerBlock(block)) {
            for (Block related : getLinkedContainerBlocks(block)) {
                runtimeLockDataCache.invalidate(toRuntimeCacheKey(related));
            }
        } else {
            runtimeLockDataCache.invalidate(toRuntimeCacheKey(block));
        }
    }

    private static String toRuntimeCacheKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    public static Block getTargetedContainer(Player player) {
        Block block = player.getTargetBlockExact(8);
        if (block == null) return null;
        if (!isContainerBlock(block)) return null;
        return block;
    }

    public static boolean isContainerBlock(Block block) {
        if (block == null) return false;
        return block.getState() instanceof Container;
    }

    public static boolean hasPdcData(Block block) {
        if (!isContainerBlock(block)) return false;
        LockData data = getLockData(block);
        return data.hasPdcData();
    }

    public static boolean isLocked(Block block) {
        if (!isContainerBlock(block)) return false;
        LockData data = getLockData(block);
        return data.hasPdcData() && data.isLocked();
    }

    public static boolean isOwner(Block block, Player player) {
        return getPlayerAccess(block, player).atLeast(PermissionAccess.OWNER);
    }

    public static boolean canRead(Block block, Player player) {
        return getPlayerAccess(block, player).atLeast(PermissionAccess.READ_ONLY);
    }

    public static boolean canWrite(Block block, Player player) {
        return getPlayerAccess(block, player).atLeast(PermissionAccess.READ_WRITE);
    }

    public static PermissionAccess getPlayerAccess(Block block, Player player) {
        if (!isContainerBlock(block) || player == null) {
            return PermissionAccess.NONE;
        }

        LockData data = getLockData(block);
        if (!data.hasPdcData() || !data.isLocked()) {
            return PermissionAccess.NONE;
        }

        PermissionAccess level = PermissionAccess.NONE;
        for (Map.Entry<String, PermissionAccess> entry : data.permissions.entrySet()) {
            String subject = entry.getKey();
            PermissionAccess access = entry.getValue();
            if (subjectMatchesPlayer(subject, player)) {
                if (access.power > level.power) {
                    level = access;
                }
            }
        }
        return level;
    }

    private static boolean subjectMatchesPlayer(String subject, Player player) {
        if (subject == null || subject.isBlank()) return false;
        if (subject.startsWith("#")) {
            return false;
        }
        if (PermissionGroupStore.isGroupReference(subject)) {
            return PermissionGroupStore.matchesPlayerGroupReference(subject, player);
        }
        if (subject.startsWith("[") && subject.endsWith("]")) {
            if (Config.isEveryoneSignString(subject) || Config.isEveryoneSignString(subject.toLowerCase(Locale.ROOT))) {
                return true;
            }
            if (Dependency.isPermissionGroupOf(subject, player, false)) {
                return true;
            }
            return Dependency.isScoreboardTeamOf(subject, player);
        }
        if (isUuid(subject)) {
            return player.getUniqueId().toString().equalsIgnoreCase(subject);
        }
        return player.getName().equalsIgnoreCase(subject);
    }

    public static boolean isOpenToEveryone(Block block) {
        if (!isContainerBlock(block)) return false;
        LockData data = getLockData(block);
        if (!data.hasPdcData() || !data.isLocked()) return false;
        return isOpenToEveryone(data.permissions);
    }

    public static boolean hasBypassTag(Block block) {
        if (!isContainerBlock(block)) return false;
        LockData data = getLockData(block);
        if (!data.hasPdcData() || !data.isLocked()) return false;
        return hasBypassTag(data.permissions);
    }

    public static boolean isContainerEffectivelyLocked(Block block) {
        if (!isContainerBlock(block)) return false;
        LockData data = getLockData(block);
        if (!data.hasPdcData()) return false;
        if (!data.isLocked()) return false;
        return !isOpenToEveryone(data.permissions) && !hasBypassTag(data.permissions);
    }

    public static LockData getLockData(Block block) {
        if (!isContainerBlock(block)) {
            return new LockData(false, false, new LinkedHashMap<>());
        }
        ensureRuntimeCacheConfiguration();
        if (!Config.isRuntimeKvCacheEnabled()) {
            return readLockData(block);
        }

        String key = toRuntimeCacheKey(block);
        LockData cached = runtimeLockDataCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        LockData loaded = readLockData(block);
        for (Block related : getLinkedContainerBlocks(block)) {
            runtimeLockDataCache.put(toRuntimeCacheKey(related), loaded);
        }
        return loaded;
    }

    private static LockData readLockData(Block block) {
        boolean hasPdc = false;
        boolean locked = false;
        LinkedHashMap<String, PermissionAccess> permissions = new LinkedHashMap<>();

        for (Block containerBlock : getLinkedContainerBlocks(block)) {
            BlockState blockState = containerBlock.getState();
            if (!(blockState instanceof Container container)) continue;
            PersistentDataContainer pdc = container.getPersistentDataContainer();

            boolean hasLockedKey = pdc.has(lockedKey(), PersistentDataType.BYTE);
            if (hasLockedKey) {
                hasPdc = true;
                Byte lockedByte = pdc.get(lockedKey(), PersistentDataType.BYTE);
                if (lockedByte != null && lockedByte != 0) {
                    locked = true;
                }
            }

            for (NamespacedKey key : pdc.getKeys()) {
                if (!isPermissionKey(key)) continue;
                hasPdc = true;
                String subject = decodeSubject(key.getKey().substring(PERMISSION_KEY_PATH_PREFIX.length()));
                if (subject == null || subject.isBlank()) continue;

                String token = pdc.get(key, PersistentDataType.STRING);
                PermissionAccess access = PermissionAccess.parseToken(token);
                if (access == null || access == PermissionAccess.NONE) continue;
                PermissionAccess old = permissions.get(subject);
                if (old == null || access.power > old.power) {
                    permissions.put(subject, access);
                }
            }
        }

        return new LockData(hasPdc, locked, permissions);
    }

    private static boolean isPermissionKey(NamespacedKey key) {
        if (!Objects.equals(key.getNamespace(), LOCKETTE_NAMESPACE)) return false;
        return key.getKey().startsWith(PERMISSION_KEY_PATH_PREFIX);
    }

    private static boolean isClonePermissionKey(NamespacedKey key) {
        if (!Objects.equals(key.getNamespace(), LOCKETTE_NAMESPACE)) return false;
        return key.getKey().startsWith(CLONE_PERMISSION_KEY_PATH_PREFIX);
    }

    public static boolean lockWithOwner(Block block, Player owner) {
        if (!isContainerBlock(block) || owner == null) return false;
        LinkedHashMap<String, PermissionAccess> permissions = new LinkedHashMap<>();
        permissions.put(owner.getUniqueId().toString(), PermissionAccess.OWNER);
        return writeLockData(block, true, permissions);
    }

    public static boolean writeLockData(Block block, boolean locked, Map<String, PermissionAccess> newPermissions) {
        if (!isContainerBlock(block)) return false;
        invalidateRuntimeCache(block);

        LinkedHashMap<String, PermissionAccess> permissions = new LinkedHashMap<>();
        if (newPermissions != null) {
            for (Map.Entry<String, PermissionAccess> entry : newPermissions.entrySet()) {
                String subject = normalizeSubject(entry.getKey());
                PermissionAccess access = entry.getValue();
                if (subject == null || access == null || access == PermissionAccess.NONE) continue;
                permissions.put(subject, access);
            }
        }

        if (locked && permissions.isEmpty()) {
            // fallback to avoid creating an unmanageable lock
            return false;
        }

        boolean effectiveLockedContainer = false;
        if (locked) {
            effectiveLockedContainer = shouldSetLockedContainerTag(permissions);
        }

        NamespacedKey lockedKey = lockedKey();
        NamespacedKey lockedContainerKey = lockedContainerKey();

        for (Block containerBlock : getLinkedContainerBlocks(block)) {
            BlockState blockState = containerBlock.getState();
            if (!(blockState instanceof Container container)) continue;
            PersistentDataContainer pdc = container.getPersistentDataContainer();

            removePermissionKeys(pdc);

            if (locked) {
                pdc.set(lockedKey, PersistentDataType.BYTE, (byte) 1);
                for (Map.Entry<String, PermissionAccess> entry : permissions.entrySet()) {
                    pdc.set(createPermissionKey(entry.getKey()), PersistentDataType.STRING, entry.getValue().token());
                }
                if (effectiveLockedContainer) {
                    pdc.set(lockedContainerKey, PersistentDataType.BYTE, (byte) 1);
                } else {
                    pdc.remove(lockedContainerKey);
                }
            } else {
                pdc.remove(lockedKey);
                pdc.remove(lockedContainerKey);
            }

            container.update(true, false);
        }

        Utils.resetCache(block);
        invalidateRuntimeCache(block);
        return true;
    }

    public static boolean refreshLockedContainerTag(Block block) {
        if (!isContainerBlock(block)) return false;
        invalidateRuntimeCache(block);
        LockData data = getLockData(block);
        if (!data.hasPdcData()) return false;

        boolean shouldHaveTag = data.isLocked() && shouldSetLockedContainerTag(data.permissions);
        NamespacedKey key = lockedContainerKey();

        for (Block containerBlock : getLinkedContainerBlocks(block)) {
            BlockState state = containerBlock.getState();
            if (!(state instanceof Container container)) continue;
            PersistentDataContainer pdc = container.getPersistentDataContainer();
            if (shouldHaveTag) {
                pdc.set(key, PersistentDataType.BYTE, (byte) 1);
            } else {
                pdc.remove(key);
            }
            container.update(true, false);
        }

        Utils.resetCache(block);
        invalidateRuntimeCache(block);
        return true;
    }

    private static boolean shouldSetLockedContainerTag(Map<String, PermissionAccess> permissions) {
        if (permissions == null || permissions.isEmpty()) return false;
        return !isOpenToEveryone(permissions) && !hasBypassTag(permissions);
    }

    private static boolean isOpenToEveryone(Map<String, PermissionAccess> permissions) {
        for (Map.Entry<String, PermissionAccess> entry : permissions.entrySet()) {
            PermissionAccess access = entry.getValue();
            if (access == PermissionAccess.NONE) continue;
            if (!access.atLeast(PermissionAccess.READ_WRITE)) continue;
            String subject = entry.getKey();
            if (PermissionGroupStore.isGroupReference(subject)) {
                String groupName = PermissionGroupStore.extractGroupName(subject);
                if (groupName != null && PermissionGroupStore.groupAllowsEveryone(groupName)) {
                    return true;
                }
                continue;
            }
            if (subject.startsWith("[") && subject.endsWith("]")) {
                if (Config.isEveryoneSignString(subject) || Config.isEveryoneSignString(subject.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasBypassTag(Map<String, PermissionAccess> permissions) {
        for (Map.Entry<String, PermissionAccess> entry : permissions.entrySet()) {
            PermissionAccess access = entry.getValue();
            if (access == PermissionAccess.NONE) continue;
            if (!access.atLeast(PermissionAccess.READ_WRITE)) continue;
            String subject = entry.getKey();
            if (PermissionGroupStore.isGroupReference(subject)) {
                String groupName = PermissionGroupStore.extractGroupName(subject);
                if (groupName != null && PermissionGroupStore.groupHasContainerBypass(groupName)) {
                    return true;
                }
                continue;
            }
            if (ENTITY_HOPPER.equalsIgnoreCase(subject)) {
                return true;
            }
            if (subject.startsWith("[") && subject.endsWith("]")) {
                if (Config.isContainerBypassSignString(subject) || Config.isContainerBypassSignString(subject.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void removePermissionKeys(PersistentDataContainer pdc) {
        List<NamespacedKey> toRemove = new ArrayList<>();
        for (NamespacedKey key : pdc.getKeys()) {
            if (isPermissionKey(key)) {
                toRemove.add(key);
            }
        }
        for (NamespacedKey key : toRemove) {
            pdc.remove(key);
        }
    }

    public static PermissionMutation parsePermissionMutation(String raw) {
        if (raw == null) return null;
        int split = raw.indexOf(':');
        if (split <= 0 || split == raw.length() - 1) return null;

        String accessToken = raw.substring(0, split).trim();
        String subjectRaw = raw.substring(split + 1).trim();
        PermissionAccess access = PermissionAccess.parseToken(accessToken);
        if (access == null) return null;

        String subject = normalizeSubject(subjectRaw);
        if (subject == null || subject.isBlank()) return null;
        if (access == PermissionAccess.OWNER && !isOwnerSubject(subject)) return null;
        return new PermissionMutation(access, subject);
    }

    public static String normalizeSubject(String raw) {
        if (raw == null) return null;
        String subject = raw.trim();
        if (subject.isBlank()) return null;

        if (subject.startsWith("[") && subject.endsWith("]")) {
            if (PermissionGroupStore.isGroupReference(subject)) {
                return PermissionGroupStore.normalizeGroupReferenceIfExists(subject);
            }
            return subject;
        }
        if (subject.startsWith("#")) {
            return subject.toLowerCase(Locale.ROOT);
        }
        if (isUuid(subject)) {
            return subject.toLowerCase(Locale.ROOT);
        }

        Player exact = Bukkit.getPlayerExact(subject);
        if (exact != null) {
            return exact.getUniqueId().toString();
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(subject)) {
                return online.getUniqueId().toString();
            }
        }

        return subject;
    }

    public static String describeSubject(String subject) {
        if (subject == null || subject.isBlank()) return subject;
        if (subject.startsWith("[") || subject.startsWith("#")) {
            return subject;
        }
        if (isUuid(subject)) {
            try {
                UUID uuid = UUID.fromString(subject);
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    runtimePlayerNameCache.put(uuid, online.getName());
                    return online.getName() + "#" + uuid;
                }
                String cachedName = runtimePlayerNameCache.getIfPresent(uuid);
                if (cachedName != null) {
                    if (cachedName.isEmpty()) {
                        return subject;
                    }
                    return cachedName + "#" + uuid;
                }
                OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                String offlineName = offline.getName();
                if (offlineName == null || offlineName.isBlank()) {
                    runtimePlayerNameCache.put(uuid, PLAYER_NAME_CACHE_MISS);
                    return subject;
                }
                runtimePlayerNameCache.put(uuid, offlineName);
                return offlineName + "#" + uuid;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return subject;
    }

    public static boolean isSelfSubject(Player player, String subject) {
        if (player == null || subject == null) return false;
        if (!isOwnerSubject(subject)) return false;
        if (isUuid(subject)) {
            return player.getUniqueId().toString().equalsIgnoreCase(subject);
        }
        return player.getName().equalsIgnoreCase(subject);
    }

    private static boolean isOwnerSubject(String subject) {
        if (subject == null || subject.isBlank()) return false;
        if (subject.startsWith("[") || subject.startsWith("#")) {
            return false;
        }
        if (isUuid(subject)) return true;
        for (int i = 0; i < subject.length(); i++) {
            char c = subject.charAt(i);
            if (!((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_')) {
                return false;
            }
        }
        return true;
    }

    public static void applyPermissionMutation(Block block, PermissionMutation mutation) {
        LockData data = getLockData(block);
        LinkedHashMap<String, PermissionAccess> permissions = new LinkedHashMap<>(data.permissions);

        if (mutation.access == PermissionAccess.NONE) {
            permissions.remove(mutation.subject);
        } else {
            permissions.put(mutation.subject, mutation.access);
        }

        boolean keepLocked = !permissions.isEmpty();
        writeLockData(block, keepLocked, permissions);
    }

    public static boolean setContainerCustomName(Block block, String customName) {
        if (!isContainerBlock(block)) return false;
        for (Block containerBlock : getLinkedContainerBlocks(block)) {
            BlockState blockState = containerBlock.getState();
            if (!(blockState instanceof Container container)) continue;
            container.setCustomName(customName);
            container.update(true, false);
        }
        return true;
    }

    public static String getContainerCustomName(Block block) {
        if (!isContainerBlock(block)) return null;
        BlockState blockState = block.getState();
        if (!(blockState instanceof Container container)) return null;
        return container.getCustomName();
    }

    public static ItemStack buildCloneItem(Block sourceBlock) {
        if (!isContainerBlock(sourceBlock)) return null;

        LockData data = getLockData(sourceBlock);
        if (!data.hasPdcData() || !data.isLocked()) return null;

        Material material = Config.getPermissionCloneItemMaterial();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String display = Utils.colorize(Config.getPermissionCloneItemName());
        if (!display.isEmpty()) {
            meta.setDisplayName(display);
        }
        List<String> lore = new ArrayList<>();
        for (String line : Config.getPermissionCloneItemLore()) {
            lore.add(Utils.colorize(line));
        }
        meta.setLore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(cloneMarkerKey(), PersistentDataType.BYTE, (byte) 1);

        String customName = getContainerCustomName(sourceBlock);
        if (customName != null && !customName.isBlank()) {
            pdc.set(cloneNameKey(), PersistentDataType.STRING, customName);
        } else {
            pdc.remove(cloneNameKey());
        }

        removeClonePermissionKeys(pdc);
        for (Map.Entry<String, PermissionAccess> entry : data.permissions.entrySet()) {
            pdc.set(createClonePermissionKey(entry.getKey()), PersistentDataType.STRING, entry.getValue().token());
        }

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isCloneItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) return false;
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(cloneMarkerKey(), PersistentDataType.BYTE);
    }

    public static boolean applyCloneItem(Block targetBlock, Player operator, ItemStack itemStack) {
        if (!isContainerBlock(targetBlock)) return false;
        if (!isCloneItem(itemStack)) return false;

        Map<String, PermissionAccess> clonedPermissions = readClonePermissions(itemStack);
        if (clonedPermissions.isEmpty()) return false;

        boolean hasOwner = clonedPermissions.values().stream().anyMatch(v -> v == PermissionAccess.OWNER);
        if (!hasOwner) {
            clonedPermissions.put(operator.getUniqueId().toString(), PermissionAccess.OWNER);
        }

        String customName = getCloneCustomName(itemStack);

        writeLockData(targetBlock, true, clonedPermissions);
        if (customName != null) {
            setContainerCustomName(targetBlock, customName);
        }
        return true;
    }

    private static Map<String, PermissionAccess> readClonePermissions(ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return Collections.emptyMap();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        LinkedHashMap<String, PermissionAccess> permissions = new LinkedHashMap<>();
        for (NamespacedKey key : pdc.getKeys()) {
            if (!isClonePermissionKey(key)) continue;
            String encoded = key.getKey().substring(CLONE_PERMISSION_KEY_PATH_PREFIX.length());
            String subject = decodeSubject(encoded);
            if (subject == null || subject.isBlank()) continue;
            PermissionAccess access = PermissionAccess.parseToken(pdc.get(key, PersistentDataType.STRING));
            if (access == null || access == PermissionAccess.NONE) continue;
            permissions.put(subject, access);
        }
        return permissions;
    }

    private static String getCloneCustomName(ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(cloneNameKey(), PersistentDataType.STRING);
    }

    private static void removeClonePermissionKeys(PersistentDataContainer pdc) {
        List<NamespacedKey> remove = new ArrayList<>();
        for (NamespacedKey key : pdc.getKeys()) {
            if (isClonePermissionKey(key)) {
                remove.add(key);
            }
        }
        for (NamespacedKey key : remove) {
            pdc.remove(key);
        }
    }

    public static boolean canUseCloneTarget(Block targetBlock, Player player) {
        if (!isContainerBlock(targetBlock)) return false;

        LockData data = getLockData(targetBlock);
        if (data.hasPdcData() && data.isLocked()) {
            return isOwner(targetBlock, player);
        }

        boolean signLocked = LocketteProAPI.isLocked(targetBlock);
        if (signLocked) {
            return LocketteProAPI.isOwner(targetBlock, player);
        }

        return true;
    }

    public static List<Block> getLinkedContainerBlocks(Block block) {
        if (!isContainerBlock(block)) return Collections.emptyList();
        LinkedHashSet<Block> blocks = new LinkedHashSet<>();
        blocks.add(block);

        BlockState blockState = block.getState();
        if (!(blockState instanceof Container container)) {
            return new ArrayList<>(blocks);
        }

        InventoryHolder holder = container.getInventory().getHolder();
        if (!(holder instanceof DoubleChest doubleChest)) {
            return new ArrayList<>(blocks);
        }

        addHolderBlock(doubleChest.getLeftSide(), blocks);
        addHolderBlock(doubleChest.getRightSide(), blocks);

        return new ArrayList<>(blocks);
    }

    private static void addHolderBlock(InventoryHolder holder, Set<Block> blocks) {
        if (holder instanceof BlockState blockState && blockState instanceof Container) {
            blocks.add(blockState.getBlock());
        }
    }

    private static String encodeSubject(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder builder = new StringBuilder(SUBJECT_HEX_ENCODING_PREFIX.length() + bytes.length * 2);
        builder.append(SUBJECT_HEX_ENCODING_PREFIX);
        for (byte b : bytes) {
            int v = b & 0xFF;
            builder.append(HEX_DIGITS[v >>> 4]);
            builder.append(HEX_DIGITS[v & 0x0F]);
        }
        return builder.toString();
    }

    private static String decodeSubject(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }

        if (encoded.startsWith(SUBJECT_HEX_ENCODING_PREFIX)) {
            return decodeHexSubject(encoded.substring(SUBJECT_HEX_ENCODING_PREFIX.length()));
        }

        // Backward compatibility for previous base64-url encoding.
        String legacyDecoded = decodeLegacyBase64Subject(encoded);
        if (legacyDecoded != null) {
            return legacyDecoded;
        }

        // Safety fallback for potential prefix-less hex payloads.
        return decodeHexSubject(encoded);
    }

    private static String decodeLegacyBase64Subject(String encoded) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String decodeHexSubject(String hex) {
        if (hex == null || hex.isEmpty() || (hex.length() & 1) == 1) {
            return null;
        }

        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = hexValue(hex.charAt(i * 2));
            int low = hexValue(hex.charAt(i * 2 + 1));
            if (high < 0 || low < 0) {
                return null;
            }
            bytes[i] = (byte) ((high << 4) | low);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int hexValue(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    private static boolean isUuid(String text) {
        if (text == null) return false;
        try {
            UUID.fromString(text);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static Collection<String> summarizePermissions(Map<String, PermissionAccess> permissions, EnumSet<PermissionAccess> types) {
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, PermissionAccess> entry : permissions.entrySet()) {
            if (types.contains(entry.getValue())) {
                values.add(describeSubject(entry.getKey()));
            }
        }
        return values;
    }

    public static String formatList(Collection<String> list) {
        if (list == null || list.isEmpty()) {
            return ChatColor.GRAY + "(none)" + ChatColor.RESET;
        }
        return String.join(ChatColor.GRAY + ", " + ChatColor.RESET, list);
    }
}
