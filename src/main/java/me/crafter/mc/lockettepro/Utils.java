package me.crafter.mc.lockettepro;

import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class Utils {

    public static final String usernamepattern = "^[a-zA-Z0-9_]*$";

    private static final LoadingCache<UUID, Block> selectedsign = CacheBuilder.newBuilder()
            .expireAfterAccess(30, TimeUnit.SECONDS)
            .build(new CacheLoader<>() {
                public Block load(UUID key) {
                    return null;
                }
            });
    private static final Set<UUID> notified = new HashSet<>();

    // Helper functions
    public static Block putSignOn(Block block, BlockFace blockface, String line1, String line2, Material material) {
        Block newsign = block.getRelative(blockface);
        Material blockType = Material.getMaterial(material.name().replace("_SIGN", "_WALL_SIGN"));
        if (blockType != null && Tag.WALL_SIGNS.isTagged(blockType)) {
            newsign.setType(blockType);
        } else {
            newsign.setType(Material.OAK_WALL_SIGN);
        }
        BlockData data = newsign.getBlockData();
        if (data instanceof Directional) {
            ((Directional) data).setFacing(blockface);
            newsign.setBlockData(data, true);
        }
        updateSign(newsign);
        Sign sign = (Sign) newsign.getState();
        if (newsign.getType() == Material.DARK_OAK_WALL_SIGN) {
            sign.getSide(Side.FRONT).setColor(DyeColor.WHITE);
        }
        sign.getSide(Side.FRONT).setLine(0, line1);
        sign.getSide(Side.FRONT).setLine(1, line2);
        sign.setWaxed(true);
        sign.update();
        return newsign;
    }

    public static void setSignLine(Block block, int line, String text) { // Requires isSign
        Sign sign = (Sign) block.getState();
        sign.getSide(Side.FRONT).setLine(line, text);
        sign.update();
    }

    public static void removeASign(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (player.getInventory().getItemInMainHand().getAmount() == 1) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
        }
    }

    public static void updateSign(Block block) {
        block.getState().update();
    }

    public static Block getSelectedSign(Player player) {
        Block b = selectedsign.getIfPresent(player.getUniqueId());
        if (b != null && !player.getWorld().getName().equals(b.getWorld().getName())) {
            selectedsign.invalidate(player.getUniqueId());
            return null;
        }
        return b;
    }

    public static void selectSign(Player player, Block block) {
        selectedsign.put(player.getUniqueId(), block);
    }

    public static void playLockEffect(Player player, Block block) {
//		player.playSound(block.getLocation(), Sound.DOOR_CLOSE, 0.3F, 1.4F);
//		player.spigot().playEffect(block.getLocation().add(0.5, 0.5, 0.5), Effect.CRIT, 0, 0, 0.3F, 0.3F, 0.3F, 0.1F, 64, 64);
    }

    public static void playAccessDenyEffect(Player player, Block block) {
//		player.playSound(block.getLocation(), Sound.VILLAGER_NO, 0.3F, 0.9F);
//		player.spigot().playEffect(block.getLocation().add(0.5, 0.5, 0.5), Effect.FLAME, 0, 0, 0.3F, 0.3F, 0.3F, 0.01F, 64, 64);
    }

    public static void sendMessages(CommandSender sender, String messages) {
        if (messages == null || messages.equals("")) return;
        sender.sendMessage(messages);
    }

    public static boolean shouldNotify(Player player) {
        if (notified.contains(player.getUniqueId())) {
            return false;
        } else {
            notified.add(player.getUniqueId());
            return true;
        }
    }

    public static boolean hasValidCache(Block block) {
        List<MetadataValue> metadatas = block.getMetadata("expires");
        if (!metadatas.isEmpty()) {
            long expires = metadatas.get(0).asLong();
            return expires > System.currentTimeMillis();
        }
        return false;
    }

    public static boolean getAccess(Block block) { // Requires hasValidCache()
        List<MetadataValue> metadatas = block.getMetadata("locked");
        return metadatas.get(0).asBoolean();
    }

    public static void setCache(Block block, boolean access) {
        block.removeMetadata("expires", LockettePro.getPlugin());
        block.removeMetadata("locked", LockettePro.getPlugin());
        block.setMetadata("expires", new FixedMetadataValue(LockettePro.getPlugin(), System.currentTimeMillis() + Config.getCacheTimeMillis()));
        block.setMetadata("locked", new FixedMetadataValue(LockettePro.getPlugin(), access));
    }

    public static void resetCache(Block block) {
        block.removeMetadata("expires", LockettePro.getPlugin());
        block.removeMetadata("locked", LockettePro.getPlugin());
        for (BlockFace blockface : LocketteProAPI.newsfaces) {
            Block relative = block.getRelative(blockface);
            if (relative.getType() == block.getType()) {
                relative.removeMetadata("expires", LockettePro.getPlugin());
                relative.removeMetadata("locked", LockettePro.getPlugin());
            }
        }
    }

    public static void updateUuidOnSign(Block block) {
        for (int line = 1; line < 4; line++) {
            updateUuidByUsername(block, line);
        }
    }

    public static void updateUuidByUsername(final Block block, final int line) {
        Sign sign = (Sign) block.getState();
        final String original = sign.getSide(Side.FRONT).getLine(line);
        Bukkit.getScheduler().runTaskAsynchronously(LockettePro.getPlugin(), () -> {
            String username = original;
            if (username.contains("#")) {
                username = username.split("#")[0];
            }
            if (!isUserName(username)) return;
            String uuid;
            Player user = Bukkit.getPlayerExact(username);
            if (user != null) { // User is online
                uuid = user.getUniqueId().toString();
            } else { // User is not online, fetch string
                uuid = getUuidByUsernameFromMojang(username);
            }
            if (uuid != null) {
                final String towrite = username + "#" + uuid;
                Bukkit.getScheduler().runTask(LockettePro.getPlugin(), () -> setSignLine(block, line, towrite));
            }
        });
    }

    public static void updateUsernameByUuid(Block block, int line) {
        Sign sign = (Sign) block.getState();
        String original = sign.getSide(Side.FRONT).getLine(line);
        if (isUsernameUuidLine(original)) {
            String uuid = getUuidFromLine(original);
            if (uuid == null) return;
            Player player = Bukkit.getPlayer(UUID.fromString(uuid));
            if (player == null) return;
            setSignLine(block, line, player.getName() + "#" + uuid);
        }
    }

    public static void updateLineByPlayer(Block block, int line, Player player) {
        setSignLine(block, line, player.getName() + "#" + player.getUniqueId());
    }

    public static void updateLineWithTime(Block block, boolean noexpire) {
        Sign sign = (Sign) block.getState();
        if (noexpire) {
            sign.getSide(Side.FRONT).setLine(0, sign.getSide(Side.FRONT).getLine(0) + "#created:" + -1);
        } else {
            sign.getSide(Side.FRONT).setLine(0, sign.getSide(Side.FRONT).getLine(0) + "#created:" + (int) (System.currentTimeMillis() / 1000));
        }
        sign.update();
    }

    public static boolean isUserName(String text) {
        return text.length() < 17 && text.length() > 2 && text.matches(usernamepattern);
    }

    // Warning: don't use this in a sync way
    public static String getUuidByUsernameFromMojang(String username) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            String responsestring = response.toString();
            JsonObject json = JsonParser.parseString(responsestring).getAsJsonObject();
            String rawuuid = json.get("id").getAsString();
            return rawuuid.substring(0, 8) + "-" + rawuuid.substring(8, 12) + "-" + rawuuid.substring(12, 16) + "-" + rawuuid.substring(16, 20) + "-" + rawuuid.substring(20);
        } catch (Exception ignored) {
        }
        return null;
    }

    public static boolean isUsernameUuidLine(String text) {
        if (text.contains("#")) {
            String[] splitted = text.split("#", 2);
            return splitted[1].length() == 36;
        }
        return false;
    }

    public static boolean isPrivateTimeLine(String text) {
        if (text.contains("#")) {
            String[] splitted = text.split("#", 2);
            return splitted[1].startsWith("created:");
        }
        return false;
    }

    public static String StripSharpSign(String text) {
        if (text.contains("#")) {
            return text.split("#", 2)[0];
        } else {
            return text;
        }
    }

    public static String getUsernameFromLine(String text) {
        if (isUsernameUuidLine(text)) {
            return text.split("#", 2)[0];
        } else {
            return text;
        }
    }

    public static String getUuidFromLine(String text) {
        if (isUsernameUuidLine(text)) {
            return text.split("#", 2)[1];
        } else {
            return null;
        }
    }

    public static long getCreatedFromLine(String text) {
        if (isPrivateTimeLine(text)) {
            return Long.parseLong(text.split("#created:", 2)[1]);
        } else {
            return Config.getLockDefaultCreateTimeUnix();
        }
    }

    public static boolean isAxe(ItemStack itemStack) {
        if (itemStack == null)
            return false;
        Material eventItemType = itemStack.getType();
        return Tag.ITEMS_AXES.isTagged(eventItemType);
    }

    public static boolean isPlayerOnLine(Player player, String text) {
        if (Utils.isUsernameUuidLine(text)) {
            if (Config.isUuidEnabled()) {
                return player.getUniqueId().toString().equals(getUuidFromLine(text));
            } else {
                return player.getName().equals(getUsernameFromLine(text));
            }
        } else {
            return text.equals(player.getName());
        }
    }

    public static String getSignLineFromUnknown(WrappedChatComponent rawline) {
        String json = rawline.getJson();
        return getSignLineFromUnknown(json);
    }

    public static String getSignLineFromUnknown(String json) {
        JsonObject line = getJsonObjectOrNull(json);
        if (line == null) return json;
        StringBuilder result = new StringBuilder();
        if (line.has("text")) {
            result.append(line.get("text").getAsString());
        }
        if (line.has("extra")) {
            try {
                result.append(line.get("extra").getAsJsonArray().get(0).getAsJsonObject().get("text").getAsString());
            } catch (Exception ignored) {
            }
        }

        return result.toString();
    }

    @Nullable
    public static JsonObject getJsonObjectOrNull(String json) {
        int i = json.indexOf("{");
        if (i < 0) return null;
        if (json.indexOf("}", 1) < 0) return null;
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (JsonParseException e) {
            return null;
        }
    }

}
