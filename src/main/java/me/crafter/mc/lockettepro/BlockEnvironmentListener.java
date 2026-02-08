package me.crafter.mc.lockettepro;

import java.util.Iterator;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wither;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.StructureGrowEvent;

public class BlockEnvironmentListener implements Listener {

    // Prevent explosion break block
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (Config.isProtectionExempted("explosion")) return;
        event.blockList().removeIf(LocketteProAPI::isProtected);
    }

    // Prevent bed break block
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (Config.isProtectionExempted("explosion")) return;
        event.blockList().removeIf(LocketteProAPI::isProtected);
    }

    // Prevent tree break block
    @EventHandler(priority = EventPriority.HIGH)
    public void onStructureGrow(StructureGrowEvent event) {
        if (Config.isProtectionExempted("growth")) return;
        for (BlockState blockstate : event.getBlocks()) {
            if (LocketteProAPI.isProtected(blockstate.getBlock())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // Prevent piston extend break lock
    @EventHandler(priority = EventPriority.HIGH)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (Config.isProtectionExempted("piston")) return;
        for (Block block : event.getBlocks()) {
            if (LocketteProAPI.isProtected(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // Prevent piston retract break lock
    @EventHandler(priority = EventPriority.HIGH)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (Config.isProtectionExempted("piston")) return;
        for (Block block : event.getBlocks()) {
            if (LocketteProAPI.isProtected(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // Prevent redstone current open doors
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockRedstoneChange(BlockRedstoneEvent event) {
        if (Config.isProtectionExempted("redstone")) return;
        if (LocketteProAPI.isProtected(event.getBlock())) {
            event.setNewCurrent(event.getOldCurrent());
        }
    }

    // Prevent villager open door
    @EventHandler(priority = EventPriority.HIGH)
    public void onVillagerOpenDoor(EntityInteractEvent event) {
        if (Config.isProtectionExempted("villager")) return;
        // Explicitly to villager vs all doors
        if (event.getEntity() instanceof Villager &&
                (LocketteProAPI.isSingleDoorBlock(event.getBlock()) || LocketteProAPI.isDoubleDoorBlock(event.getBlock())) &&
                LocketteProAPI.isProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    // Prevent mob change block
    @EventHandler(priority = EventPriority.HIGH)
    public void onMobChangeBlock(EntityChangeBlockEvent event) {
        if ((event.getEntity() instanceof Enderman && !Config.isProtectionExempted("enderman")) ||// enderman pick up/place block
                (event.getEntity() instanceof Wither && !Config.isProtectionExempted("wither")) ||// wither break block
                (event.getEntity() instanceof Zombie && !Config.isProtectionExempted("zombie")) ||// zombie break door
                (event.getEntity() instanceof Silverfish && !Config.isProtectionExempted("silverfish"))) {
            if (LocketteProAPI.isProtected(event.getBlock())) {
                event.setCancelled(true);
            }
        }// ignore other reason (boat break lily pad, arrow ignite tnt, etc)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoadSyncPdc(ChunkLoadEvent event) {
        Utils.refreshLockedContainerPdcTagsInChunk(event.getChunk());
    }

}
