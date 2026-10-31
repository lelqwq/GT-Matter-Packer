package dev.gtmatterpacker;

import java.io.IOException;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;

import gregtech.common.blocks.BlockFrameBox;

final class BlockData {

    private BlockData() {}

    static boolean requiresTileData(Block block, int meta) {
        // Bare GT frames deliberately return null from their tile factory; covers add an optional tile.
        return block.hasTileEntity(meta) && !(block instanceof BlockFrameBox);
    }

    static int readMetadata(NBTTagCompound data) throws IOException {
        int meta = data.getInteger("Meta");
        // GTNH's extended metadata stores frame material IDs beyond vanilla's four-bit range.
        if (meta < 0 || meta > 0xFFFF) throw new IOException("Invalid block metadata: " + meta);
        return meta;
    }
}
