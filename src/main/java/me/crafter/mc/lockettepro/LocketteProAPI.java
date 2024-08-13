package me.crafter.mc.lockettepro;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;

public class LocketteProAPI {

    public static BlockFace[] newsfaces = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    public static BlockFace[] allfaces = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};

    public static boolean isLocked(Block block) {
        if (block == null) return false;
        if (block.getBlockData() instanceof Door) {
            Block[] doors = getDoors(block);
            if (doors == null) return false;
            for (BlockFace doorface : newsfaces) {
                Block relative0 = doors[0].getRelative(doorface), relative1 = doors[1].getRelative(doorface);
                if (relative0.getType() == doors[0].getType() && relative1.getType() == doors[1].getType()) {
                    if (isLockedSingleBlock(relative1.getRelative(BlockFace.UP), doorface.getOppositeFace()))
                        return true;
                    if (isLockedSingleBlock(relative1, doorface.getOppositeFace())) return true;
                    if (isLockedSingleBlock(relative0, doorface.getOppositeFace())) return true;
                    if (isLockedSingleBlock(relative0.getRelative(BlockFace.DOWN), doorface.getOppositeFace()))
                        return true;
                }
            }
            if (isLockedSingleBlock(doors[1].getRelative(BlockFace.UP), null)) return true;
            if (isLockedSingleBlock(doors[1], null)) return true;
            if (isLockedSingleBlock(doors[0], null)) return true;
            if (isLockedSingleBlock(doors[0].getRelative(BlockFace.DOWN), null)) return true;
        } else if (block.getBlockData() instanceof Chest) {
            // Check second chest sign
            BlockFace chestface = getRelativeChestFace(block);
            if (chestface != null) {
                Block relativechest = block.getRelative(chestface);
                if (isLockedSingleBlock(relativechest, chestface.getOppositeFace())) return true;
            }
            // Don't break here
            // Everything else (First block of container check goes here)
        }
        return isLockedSingleBlock(block, null);
    }

    public static boolean isOwner(Block block, Player player) {

        if (block.getBlockData() instanceof Door) {
            Block[] doors = getDoors(block);
            if (doors == null) return false;
            for (BlockFace doorface : newsfaces) {
                Block relative0 = doors[0].getRelative(doorface), relative1 = doors[1].getRelative(doorface);
                if (relative0.getType() == doors[0].getType() && relative1.getType() == doors[1].getType()) {
                    if (isOwnerSingleBlock(relative1.getRelative(BlockFace.UP), doorface.getOppositeFace(), player))
                        return true;
                    if (isOwnerSingleBlock(relative1, doorface.getOppositeFace(), player)) return true;
                    if (isOwnerSingleBlock(relative0, doorface.getOppositeFace(), player)) return true;
                    if (isOwnerSingleBlock(relative0.getRelative(BlockFace.DOWN), doorface.getOppositeFace(), player))
                        return true;
                }
            }
            if (isOwnerSingleBlock(doors[1].getRelative(BlockFace.UP), null, player)) return true;
            if (isOwnerSingleBlock(doors[1], null, player)) return true;
            if (isOwnerSingleBlock(doors[0], null, player)) return true;
            if (isOwnerSingleBlock(doors[0].getRelative(BlockFace.DOWN), null, player)) return true;
        } else if (block.getBlockData() instanceof Chest) {
            // Check second chest sign
            BlockFace chestface = getRelativeChestFace(block);
            if (chestface != null) {
                Block relativechest = block.getRelative(chestface);
                if (isOwnerSingleBlock(relativechest, chestface.getOppositeFace(), player)) return true;
            }
            // Don't break here
            // Everything else (First block of container check goes here)
        }

        return isOwnerSingleBlock(block, null, player);
    }

    public static boolean isUser(Block block, Player player) {
        if (block.getType().equals(Material.LECTERN)) return true; //Lecterns can be used, but not stolen from
        if (block.getBlockData() instanceof Door) {
            Block[] doors = getDoors(block);
            if (doors == null) return false;
            for (BlockFace doorface : newsfaces) {
                Block relative0 = doors[0].getRelative(doorface), relative1 = doors[1].getRelative(doorface);
                if (relative0.getType() == doors[0].getType() && relative1.getType() == doors[1].getType()) {
                    if (isUserSingleBlock(relative1.getRelative(BlockFace.UP), doorface.getOppositeFace(), player))
                        return true;
                    if (isUserSingleBlock(relative1, doorface.getOppositeFace(), player)) return true;
                    if (isUserSingleBlock(relative0, doorface.getOppositeFace(), player)) return true;
                    if (isUserSingleBlock(relative0.getRelative(BlockFace.DOWN), doorface.getOppositeFace(), player))
                        return true;
                }
            }
            if (isUserSingleBlock(doors[1].getRelative(BlockFace.UP), null, player)) return true;
            if (isUserSingleBlock(doors[1], null, player)) return true;
            if (isUserSingleBlock(doors[0], null, player)) return true;
            if (isUserSingleBlock(doors[0].getRelative(BlockFace.DOWN), null, player)) return true;
        } else if (block.getBlockData() instanceof Chest) {
            // Check second chest sign
            BlockFace chestface = getRelativeChestFace(block);
            if (chestface != null) {
                Block relativechest = block.getRelative(chestface);
                if (isUserSingleBlock(relativechest, chestface.getOppositeFace(), player)) return true;
            }
        }

        return isUserSingleBlock(block, null, player);
    }

    public static boolean isProtected(Block block) {
        return (isLockSign(block) || isLocked(block) || isUpDownLockedDoor(block));
    }

    public static boolean isLockedSingleBlock(Block block, BlockFace exempt) {
        for (BlockFace blockface : newsfaces) {
            if (blockface == exempt) continue;
            Block relativeblock = block.getRelative(blockface);
            // Find [Private] sign?
            if (isLockSign(relativeblock) && getFacing(relativeblock) == blockface) {
                // Found [Private] sign, is expire turned on and expired? (relativeblock is now sign)
                if (Config.isLockExpire() && LocketteProAPI.isSignExpired(relativeblock)) {
                    continue; // Private sign but expired... But impossible to have 2 [Private] signs anyway?
                }
                return true;
            }
        }
        return false;
    }

    public static boolean isOwnerSingleBlock(Block block, BlockFace exempt, Player player) { // Requires isLocked
        for (BlockFace blockface : newsfaces) {
            if (blockface == exempt) continue;
            Block relativeblock = block.getRelative(blockface);
            if (isLockSign(relativeblock) && getFacing(relativeblock) == blockface) {
                if (isOwnerOnSign(relativeblock, player)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isUserSingleBlock(Block block, BlockFace exempt, Player player) { // Requires isLocked
        for (BlockFace blockface : newsfaces) {
            if (blockface == exempt) continue;
            Block relativeblock = block.getRelative(blockface);
            if (isLockSignOrAdditionalSign(relativeblock) && getFacing(relativeblock) == blockface) {
                if (isUserOnSign(relativeblock, player)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isOwnerOfSign(Block block, Player player) { // Requires isSign
        Block protectedblock = getAttachedBlock(block);
        // Normal situation, that block is just locked by an adjacent sign
        if (isOwner(protectedblock, player)) return true;
        // Situation where double door's block
        return isUpDownLockedDoor(protectedblock) && isOwnerUpDownLockedDoor(protectedblock, player);
    }

    public static boolean isLockable(Block block) {
        Material material = block.getType();
        //Bad blocks
        if (Tag.SIGNS.isTagged(material)) {
            return false;
        }
        if (Config.isLockable(material)) { // Directly lockable
            return true;
        } else { // Indirectly lockable
            Block blockup = block.getRelative(BlockFace.UP);
            if (isUpDownAlsoLockableBlock(blockup)) return true;
            Block blockdown = block.getRelative(BlockFace.DOWN);
            return isUpDownAlsoLockableBlock(blockdown);
        }
    }


    public static boolean isChest(Block block) {
        return block.getBlockData() instanceof Chest || block.getBlockData() instanceof DoubleChest;
    }

    public static boolean isUpDownAlsoLockableBlock(Block block) {
        if (Config.isLockable(block.getType())) {
            return (block.getBlockData() instanceof Door);
        }
        return false;
    }

    public static boolean mayInterfere(Block block, Player player) {
        if (block.getState() instanceof Container) {
            for (BlockFace blockface : allfaces) {
                Block newblock = block.getRelative(blockface);
                if (isLocked(newblock) && !isOwner(newblock, player)) {
                    return true;
                }
            }
        }

        if (block.getBlockData() instanceof Door) {
            for (BlockFace blockface : newsfaces) {
                Block newblock = block.getRelative(blockface);
                if (newblock.getBlockData() instanceof Door) {
                    if (isLocked(newblock) && !isOwner(newblock, player)) {
                        return true;
                    }
                }
            }
            // Temp workaround bad code for checking up and down signs
            Block newblock2 = block.getRelative(BlockFace.UP, 2);
            if (isLocked(newblock2) && !isOwner(newblock2, player)) {
                return true;
            }
            Block newblock3 = block.getRelative(BlockFace.DOWN, 1);
            if (isLocked(newblock3) && !isOwner(newblock3, player)) {
                return true;
            }
            // End temp workaround bad code for checking up and down signs
        } if (Tag.SIGNS.isTagged(block.getType()) ||
            block.getBlockData() instanceof Chest ||
            block.getBlockData() instanceof DoubleChest) {
            for (BlockFace blockface : allfaces) {
                Block newblock = block.getRelative(blockface);
                if (newblock.getBlockData() instanceof Chest ||
                newblock.getBlockData() instanceof DoubleChest) {
                    if (isLockedSingleBlock(newblock, null) && !isOwnerSingleBlock(newblock, null, player)) {
                        return true;
                    }
                }
            }
        }

        // if LEFT may interfere RIGHT
        switch (block.getType()) {

            // This is extra interfere block
            case HOPPER, DISPENSER, DROPPER -> {
                if (!Config.isInterferePlacementBlocked()) return false;
                for (BlockFace blockface : allfaces) {
                    Block newblock = block.getRelative(blockface);
                    switch (newblock.getType()) {
                        case CHEST:
                        case TRAPPED_CHEST:
                        case HOPPER:
                        case DISPENSER:
                        case DROPPER:
                            if (isLocked(newblock) && !isOwner(newblock, player)) {
                                return true;
                            }
                        default:
                            break;
                    }
                }
            }
            default -> {
            }
        }
        return false;
    }

    public static boolean isSign(Block block) {
        return Tag.WALL_SIGNS.isTagged(block.getType());
    }

    public static boolean isLockSign(Block block) {
        return isSign(block) && isLockString(((Sign) block.getState()).getSide(Side.FRONT).getLine(0));
    }

    public static boolean isAdditionalSign(Block block) {
        return isSign(block) && isAdditionalString(((Sign) block.getState()).getSide(Side.FRONT).getLine(0));
    }

    public static boolean isLockSignOrAdditionalSign(Block block) {
        if (isSign(block)) {
            String line = ((Sign) block.getState()).getSide(Side.FRONT).getLine(0);
            return isLockStringOrAdditionalString(line);
        } else {
            return false;
        }
    }

    public static boolean isOwnerOnSign(Block block, Player player) { // Requires isLockSign
        String[] lines = ((Sign) block.getState()).getSide(Side.FRONT).getLines();
        if (Utils.isPlayerOnLine(player, lines[1])) {
            if (Config.isUuidEnabled()) {
                Utils.updateLineByPlayer(block, 1, player);
            }
            return true;
        }
        return false;
    }

    public static boolean isUserOnSign(Block block, Player player) { // Requires (isLockSign or isAdditionalSign)
        String[] lines = ((Sign) block.getState()).getSide(Side.FRONT).getLines();
        // Normal
        for (int i = 1; i < 4; i++) {
            if (Utils.isPlayerOnLine(player, lines[i])) {
                if (Config.isUuidEnabled()) {
                    Utils.updateLineByPlayer(block, i, player);
                }
                return true;
            } else if (Config.isEveryoneSignString(lines[i])) {
                return true;
            }
        }
        // For Vault & Scoreboard
        for (int i = 1; i < 4; i++) {
            if (Dependency.isPermissionGroupOf(lines[i], player)) return true;
            if (Dependency.isScoreboardTeamOf(lines[i], player)) return true;
        }
        return false;
    }

    public static boolean isSignExpired(Block block) {
        if (!isSign(block) || !isLockSign(block)) return false;
        return isLineExpired(((Sign) block.getState()).getSide(Side.FRONT).getLine(0));
    }

    public static boolean isLineExpired(String line) {
        long createdtime = Utils.getCreatedFromLine(line);
        if (createdtime == -1L) return false; // No expire
        long currenttime = (int) (System.currentTimeMillis() / 1000);
        return createdtime + Config.getLockExpireDays() * 86400L < currenttime;
    }

    public static boolean isUpDownLockedDoor(Block block) {
        Block blockup = block.getRelative(BlockFace.UP);
        if (isUpDownAlsoLockableBlock(blockup) && isLocked(blockup)) return true;
        Block blockdown = block.getRelative(BlockFace.DOWN);
        return isUpDownAlsoLockableBlock(blockdown) && isLocked(blockdown);
    }

    public static boolean isOwnerUpDownLockedDoor(Block block, Player player) {
        Block blockup = block.getRelative(BlockFace.UP);
        if (isUpDownAlsoLockableBlock(blockup) && isOwner(blockup, player)) return true;
        Block blockdown = block.getRelative(BlockFace.DOWN);
        return isUpDownAlsoLockableBlock(blockdown) && isOwner(blockdown, player);
    }

    public static boolean isUserUpDownLockedDoor(Block block, Player player) {
        Block blockup = block.getRelative(BlockFace.UP);
        if (isUpDownAlsoLockableBlock(blockup) && isUser(blockup, player)) return true;
        Block blockdown = block.getRelative(BlockFace.DOWN);
        return isUpDownAlsoLockableBlock(blockdown) && isUser(blockdown, player);
    }

    public static boolean isLockString(String line) {
        if (line.contains("#")) line = line.split("#", 2)[0];
        return Config.isPrivateSignString(line);
    }

    public static boolean isAdditionalString(String line) {
        if (line.contains("#")) line = line.split("#", 2)[0];
        return Config.isAdditionalSignString(line);
    }

    public static boolean isLockStringOrAdditionalString(String line) {
        return isLockString(line) || isAdditionalString(line);
    }

    public static Block getAttachedBlock(Block sign) { // Requires isSign
        BlockFace facing = getFacing(sign);
        return sign.getRelative(facing.getOppositeFace());
    }

    public static int getTimerOnSigns(Block block) {
        for (BlockFace blockface : newsfaces) {
            Block relative = block.getRelative(blockface);
            if (isSign(relative)) {
                Sign sign = (Sign) relative.getState();
                for (String line : sign.getSide(Side.FRONT).getLines()) {
                    int linetime = Config.getTimer(line);
                    if (linetime > 0) return linetime;
                }
            }
        }
        return 0;
    }

    public static int getTimerDoor(Block block) {
        int timersingle = getTimerSingleDoor(block);
        if (timersingle > 0) return timersingle;
        for (BlockFace blockface : newsfaces) {
            Block relative = block.getRelative(blockface);
            timersingle = getTimerSingleDoor(relative);
            if (timersingle > 0) return timersingle;
        }
        return 0;
    }

    public static int getTimerSingleDoor(Block block) {
        Block[] doors = getDoors(block);
        if (doors == null) return 0;
        Block relativeup = doors[1].getRelative(BlockFace.UP);
        int relativeuptimer = getTimerOnSigns(relativeup);
        if (relativeuptimer > 0) return relativeuptimer;
        int doors0 = getTimerOnSigns(doors[0]);
        if (doors0 > 0) return doors0;
        int doors1 = getTimerOnSigns(doors[1]);
        if (doors1 > 0) return doors1;
        Block relativedown = doors[0].getRelative(BlockFace.DOWN);
        int relativedowntimer = getTimerOnSigns(relativedown);
        return Math.max(relativedowntimer, 0);
    }

    public static Block[] getDoors(Block block) {
        Block[] doors = new Block[2];
        boolean found = false;
        Block up = block.getRelative(BlockFace.UP), down = block.getRelative(BlockFace.DOWN);
        if (up.getType() == block.getType()) {
            found = true;
            doors[0] = block;
            doors[1] = up;
        }
        if (down.getType() == block.getType()) {
            if (found) { // error 3 doors
                return null;
            }
            doors[1] = block;
            doors[0] = down;
            found = true;
        }
        if (!found) { // error 1 door
            return null;
        }
        return doors;
    }

    public static boolean isDoubleDoorBlock(Block block) {
        return Tag.DOORS.isTagged(block.getType());
    }

    public static boolean isSingleDoorBlock(Block block) {
        return block.getBlockData() instanceof TrapDoor || block.getBlockData() instanceof Gate;
    }

    public static Block getBottomDoorBlock(Block block) { // Requires isDoubleDoorBlock || isSingleDoorBlock
        if (isDoubleDoorBlock(block)) {
            Block relative = block.getRelative(BlockFace.DOWN);
            if (relative.getType() == block.getType()) {
                return relative;
            } else {
                return block;
            }
        } else {
            return block;
        }
    }

    public static void toggleDoor(Block block, boolean open) {
        org.bukkit.block.data.Openable openablestate = (org.bukkit.block.data.Openable) block.getBlockData();
        openablestate.setOpen(open);
        block.setBlockData(openablestate);
        block.getWorld().playSound(block.getLocation(), open ? Sound.BLOCK_WOODEN_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_CLOSE, 1,1);
    }

    public static void toggleDoor(Block block) {
        org.bukkit.block.data.Openable openablestate = (org.bukkit.block.data.Openable) block.getBlockData();
        toggleDoor(block,!openablestate.isOpen());
    }

    public static BlockFace getRelativeChestFace(Block block) {
        Chest chest = (Chest) block.getBlockData();
        BlockFace face = getFacing(block);
        BlockFace relativeFace = null;
        if (chest.getType() == Chest.Type.LEFT) {
            if (face == BlockFace.NORTH) {
                relativeFace = BlockFace.EAST;
            } else if (face == BlockFace.SOUTH) {
                relativeFace = BlockFace.WEST;
            } else if (face == BlockFace.WEST) {
                relativeFace = BlockFace.NORTH;
            } else if (face == BlockFace.EAST) {
                relativeFace = BlockFace.SOUTH;
            }
        } else if (chest.getType() == Chest.Type.RIGHT) {
            if (face == BlockFace.NORTH) {
                relativeFace = BlockFace.WEST;
            } else if (face == BlockFace.SOUTH) {
                relativeFace = BlockFace.EAST;
            } else if (face == BlockFace.WEST) {
                relativeFace = BlockFace.SOUTH;
            } else if (face == BlockFace.EAST) {
                relativeFace = BlockFace.NORTH;
            }
        }
        return relativeFace;
    }

    public static BlockFace getFacing(Block block) {
        BlockData data = block.getBlockData();
        BlockFace f = null;
        if (data instanceof Directional && data instanceof Waterlogged && ((Waterlogged) data).isWaterlogged()) {
            String str = data.toString();
            if (str.contains("facing=west")) {
                f = BlockFace.WEST;
            } else if (str.contains("facing=east")) {
                f = BlockFace.EAST;
            } else if (str.contains("facing=south")) {
                f = BlockFace.SOUTH;
            } else if (str.contains("facing=north")) {
                f = BlockFace.NORTH;
            }
        } else if (data instanceof Directional) {
            f = ((Directional) data).getFacing();
        }
        return f;
    }
}
