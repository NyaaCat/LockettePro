package me.crafter.mc.lockettepro;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.nbt.NbtBase;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.comphenix.protocol.wrappers.nbt.NbtList;
import org.bukkit.Bukkit;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

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
                StructureModifier<NbtBase<?>> NbtModifier =  packet.getNbtModifier();
                NbtCompound signNbt = (NbtCompound) NbtModifier.read(0);
                NbtModifier.write(0,onSignSend(player,signNbt));
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
                List<InternalStructure> chunkData = packet.getStructures().read(0).getLists(InternalStructure.getConverter()).read(0);
                var chunkX = packet.getIntegers().read(0);
                var chunkZ = packet.getIntegers().read(1);
                var chunk = player.getWorld().getChunkAt(chunkX, chunkZ);
                for (InternalStructure struct : chunkData) {
                    var packedXZ = struct.getIntegers().read(0);
                    var y = struct.getIntegers().read(1);
                    var block = chunk.getBlock((packedXZ >> 4) & 15, y, ((packedXZ) & 15));
                    if (!(block.getState() instanceof Sign)) return;
                    StructureModifier<NbtBase<?>> NbtModifier =  struct.getNbtModifier();
                    NbtCompound signNbt = (NbtCompound) NbtModifier.read(0);
                    NbtModifier.write(0,onSignSend(player,signNbt));
                }
            }
        });
    }

    public static NbtCompound onSignSend(Player player, NbtCompound signNbt) {
        NbtList<String> msgs = signNbt.getCompound("front_text").getList("messages");
        List<NbtBase<String>> msgList = msgs.getValue();
        if (msgList.size() == 0) return signNbt;
        String raw_line1 = msgList.get(0).getValue();
        if (LocketteProAPI.isLockStringOrAdditionalString(Utils.getSignLineFromUnknown(raw_line1))) {
            // Private line
            String line1 = Utils.getSignLineFromUnknown(raw_line1);
            if (LocketteProAPI.isLineExpired(line1)) {
                msgList.get(0).setValue(formatText(Config.getLockExpireString()));
            } else {
                msgList.get(0).setValue(formatText(Utils.StripSharpSign(line1)));
            }
            // Other line
            for (int i = 1; i < msgList.size(); i++) {
                String line = Utils.getSignLineFromUnknown(msgList.get(i).getValue());
                if (Utils.isUsernameUuidLine(line)) {
                    msgList.get(i).setValue(formatText(Utils.getUsernameFromLine(line)));
                }
            }
        }
        return signNbt;
    }
    private static String formatText(String string){
        return "{\"text\":\""+string+"\"}";
    }
}
