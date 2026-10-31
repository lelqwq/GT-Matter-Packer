package dev.gtmatterpacker;

import static org.junit.Assert.*;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;

import org.junit.Test;

public class TileCodecTest {

    @Test
    public void realChestCodecRestoresCustomNameAtNewCoordinates() throws Exception {
        TileEntityChest source = new TileEntityChest();
        source.xCoord = 10;
        source.yCoord = 70;
        source.zCoord = 20;
        source.func_145976_a("preserve me");
        NBTTagCompound packed = new NBTTagCompound();
        source.writeToNBT(packed);
        TileLifecycle.validate(null, packed);
        TileEntityChest restored = (TileEntityChest) TileLifecycle
            .create(null, TileData.relocate(packed, -40, 90, 100));
        assertEquals(-40, restored.xCoord);
        assertEquals(90, restored.yCoord);
        assertEquals(100, restored.zCoord);
        assertEquals(10, source.xCoord);
        assertEquals(10, packed.getInteger("x"));
        assertEquals("preserve me", restored.getInventoryName());
    }
}
