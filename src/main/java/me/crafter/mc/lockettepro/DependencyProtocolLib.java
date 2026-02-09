package me.crafter.mc.lockettepro;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.nbt.NbtBase;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.comphenix.protocol.wrappers.nbt.NbtList;
import com.comphenix.protocol.wrappers.nbt.NbtType;
import org.bukkit.Bukkit;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.List;

public class DependencyProtocolLib {

    public static void setUpProtocolLib(Plugin plugin) {
        if (Config.protocollib) {
            addTileEntityDataListener(plugin);
            addMapChunkListener(plugin);
        }
    }

    public static void cleanUpProtocolLib(Plugin plugin) {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
                ProtocolLibrary.getProtocolManager().removePacketListeners(plugin);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void addTileEntityDataListener(Plugin plugin) {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(plugin, ListenerPriority.LOW, PacketType.Play.Server.TILE_ENTITY_DATA) {
            //PacketPlayOutTileEntityData -> ClientboundBlockEntityDataPacket
            @Override
            public void onPacketSending(PacketEvent event) {
                var player = event.getPlayer();
                var packet = event.getPacket();
                var blockPos = packet.getBlockPositionModifier().read(0);
                var block = player.getWorld().getBlockAt(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                if (!(block.getState() instanceof Sign)) return;
                StructureModifier<NbtBase<?>> NbtModifier = packet.getNbtModifier();
                NbtCompound signNbt = (NbtCompound) NbtModifier.read(0);
                NbtModifier.write(0, onSignSend(player, signNbt));
            }
        });
    }

    public static void addMapChunkListener(Plugin plugin) {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(plugin, ListenerPriority.LOW, PacketType.Play.Server.MAP_CHUNK) {
            //PacketPlayOutMapChunk - > ClientboundLevelChunkPacket -> ClientboundLevelChunkWithLightPacket
            @Override
            public void onPacketSending(PacketEvent event) {
                var player = event.getPlayer();
                PacketContainer packet = event.getPacket();
                // refer: https://wiki.vg/Protocol#Chunk_Data_and_Update_Light
                List<InternalStructure> chunkData = packet.getStructures().read(0)
                        .getLists(InternalStructure.getConverter()).read(0);
                var chunkX = packet.getIntegers().read(0);
                var chunkZ = packet.getIntegers().read(1);
                var chunk = player.getWorld().getChunkAt(chunkX, chunkZ);
                for (InternalStructure struct : chunkData) {
                    var packedXZ = struct.getIntegers().read(0);
                    var x = (packedXZ >> 4) & 15;
                    var z = packedXZ & 15;
                    var y = struct.getIntegers().read(1);
                    var block = chunk.getBlock(x, y, z);
                    if (!LocketteProAPI.isLockSign(block))
                        continue; //skip non-wall-signs and non-lock signs but continue process other block entities.
                    StructureModifier<NbtBase<?>> NbtModifier = struct.getNbtModifier();
                    NbtCompound signNbt = (NbtCompound) NbtModifier.read(0);
                    NbtModifier.write(0, onSignSend(player, signNbt));
                }
            }
        });
    }

    public static NbtCompound onSignSend(Player player, NbtCompound signNbt) {
        if (signNbt == null) {
            return null;
        }
        NbtCompound frontText = signNbt.getCompound("front_text");
        if (frontText == null) {
            return signNbt;
        }
        NbtList<?> msgs = getListSafely(frontText, "messages");
        if (msgs == null) {
            return signNbt;
        }
        @SuppressWarnings("unchecked")
        List<NbtBase<?>> msgList = (List<NbtBase<?>>) (List<?>) msgs.getValue();
        if (msgList.isEmpty()) {
            return signNbt;
        }
        String rawLine1 = readMessageValue(msgList.get(0));
        if (LocketteProAPI.isLockStringOrAdditionalString(Utils.getSignLineFromUnknown(rawLine1))) {
            // Private line
            String line1 = Utils.getSignLineFromUnknown(rawLine1);
            if (LocketteProAPI.isLineExpired(line1)) {
                writeMessageValue(msgList.get(0), Config.getLockExpireString());
            } else {
                writeMessageValue(msgList.get(0), Utils.StripSharpSign(line1));
            }
            // Other line
            for (int i = 1; i < msgList.size(); i++) {
                String line = Utils.getSignLineFromUnknown(readMessageValue(msgList.get(i)));
                if (Utils.isUsernameUuidLine(line)) {
                    writeMessageValue(msgList.get(i), Utils.getUsernameFromLine(line));
                }
            }
        }
        return signNbt;
    }

    private static String readMessageValue(NbtBase<?> messageBase) {
        if (messageBase == null) {
            return "";
        }
        NbtType type = messageBase.getType();
        if (type == NbtType.TAG_STRING) {
            Object value = messageBase.getValue();
            return value == null ? "" : value.toString();
        }
        if (type == NbtType.TAG_COMPOUND) {
            return readMessageFromCompound((NbtCompound) messageBase);
        }
        Object value = messageBase.getValue();
        return value == null ? "" : value.toString();
    }

    private static String readMessageFromCompound(NbtCompound compound) {
        if (compound == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        String text = compound.getStringOrDefault("text");
        if (text != null && !text.isEmpty()) {
            builder.append(text);
        }
        NbtList<?> extraList = getListSafely(compound, "extra");
        if (extraList != null) {
            @SuppressWarnings("unchecked")
            List<NbtBase<?>> extras = (List<NbtBase<?>>) (List<?>) extraList.getValue();
            for (NbtBase<?> extra : extras) {
                builder.append(readMessageValue(extra));
            }
        }
        return builder.toString();
    }

    private static void writeMessageValue(NbtBase<?> messageBase, String text) {
        if (messageBase == null) {
            return;
        }
        if (messageBase.getType() == NbtType.TAG_STRING) {
            @SuppressWarnings("unchecked")
            NbtBase<String> stringBase = (NbtBase<String>) messageBase;
            stringBase.setValue(formatText(text));
            return;
        }
        if (messageBase.getType() == NbtType.TAG_COMPOUND) {
            NbtCompound compound = (NbtCompound) messageBase;
            compound.put("text", text);
            compound.remove("extra");
        }
    }
    private static String formatText(String string) {
        return "{\"text\":\"" + string + "\"}";
    }

    private static NbtList<?> getListSafely(NbtCompound compound, String key) {
        if (compound == null || key == null) {
            return null;
        }
        try {
            return compound.getList(key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
