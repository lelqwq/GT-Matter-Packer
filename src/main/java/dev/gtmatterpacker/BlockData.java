package dev.gtmatterpacker;

import java.io.IOException;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;

final class BlockData {

    private BlockData() {}

    static boolean requiresTileData(Block block, int meta) {
        return block.hasTileEntity(meta);
    }

    static int readMetadata(NBTTagCompound data) throws IOException {
        int meta = data.getInteger("Meta");
        // GTNH's extended metadata stores frame material IDs beyond vanilla's four-bit range.
        if (meta < 0 || meta > 0xFFFF) throw new IOException("Invalid block metadata: " + meta);
        return meta;
    }
}
