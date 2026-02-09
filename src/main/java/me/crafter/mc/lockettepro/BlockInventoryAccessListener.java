package me.crafter.mc.lockettepro;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class BlockInventoryAccessListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory topInventory = event.getView().getTopInventory();
        Block block = getContainerBlock(topInventory);
        if (block == null) return;

        ContainerPdcLockManager.LockData data = ContainerPdcLockManager.getLockData(block);
        if (!data.hasPdcData() || !data.isLocked()) return;

        ContainerPdcLockManager.PermissionAccess access = ContainerPdcLockManager.getPlayerAccess(block, player);
        if (access.atLeast(ContainerPdcLockManager.PermissionAccess.READ_WRITE)) return;

        if (!access.atLeast(ContainerPdcLockManager.PermissionAccess.READ_ONLY)) {
            event.setCancelled(true);
            return;
        }

        int topSize = topInventory.getSize();
        if (event.getRawSlot() >= 0 && event.getRawSlot() < topSize) {
            event.setCancelled(true);
            return;
        }

        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        InventoryAction action = event.getAction();
        ClickType clickType = event.getClick();
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.COLLECT_TO_CURSOR
                || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.HOTBAR_MOVE_AND_READD
                || clickType == ClickType.NUMBER_KEY
                || clickType == ClickType.SWAP_OFFHAND
                || clickType == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory topInventory = event.getView().getTopInventory();
        Block block = getContainerBlock(topInventory);
        if (block == null) return;

        ContainerPdcLockManager.LockData data = ContainerPdcLockManager.getLockData(block);
        if (!data.hasPdcData() || !data.isLocked()) return;

        ContainerPdcLockManager.PermissionAccess access = ContainerPdcLockManager.getPlayerAccess(block, player);
        if (access.atLeast(ContainerPdcLockManager.PermissionAccess.READ_WRITE)) return;

        int topSize = topInventory.getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private Block getContainerBlock(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof DoubleChest doubleChest) {
            holder = doubleChest.getLeftSide();
        }
        if (holder instanceof BlockState blockState) {
            return blockState.getBlock();
        }
        return null;
    }
}
