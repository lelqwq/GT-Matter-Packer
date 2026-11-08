package dev.gtmatterpacker;

import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.Field;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.World;

import org.junit.Test;

import gregtech.common.blocks.BlockFrameBox;
import sun.misc.Unsafe;

public class BlockDataTest {

    private BlockFrameBox bareFrame() throws Exception {
        // The real constructor registers with Forge; this test only needs its metadata/tile policy.
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (BlockFrameBox) ((Unsafe) field.get(null)).allocateInstance(BlockFrameBox.class);
    }

    @Test
    public void bareGTFrameDoesNotRequireATile() throws Exception {
        BlockFrameBox frame = bareFrame();
        assertFalse(frame.hasTileEntity(316));
        assertNull(frame.createNewTileEntity(null, 316));
        assertFalse(BlockData.requiresTileData(frame, 316));
        PackingService.Entry entry = new PackingService.Entry();
        entry.block = frame;
        entry.meta = 316;
        entry.validate(null);
    }

    @Test(expected = IOException.class)
    public void genuineMissingMachineDataIsStillRejected() throws Exception {
        Block machine = new BlockContainer(Material.iron) {

            @Override
            public TileEntity createNewTileEntity(World world, int meta) {
                return new TileEntityChest();
            }
        };
        assertTrue(BlockData.requiresTileData(machine, 1));
        PackingService.Entry entry = new PackingService.Entry();
        entry.block = machine;
        entry.meta = 1;
        entry.validate(null);
    }

    @Test
    public void existingFrameTileDataIsNotDiscarded() throws Exception {
        TileEntityChest tile = new TileEntityChest();
        tile.func_145976_a("optional frame data retained");
        PackingService.Entry entry = new PackingService.Entry();
        entry.block = bareFrame();
        entry.meta = 316 | BlockFrameBox.MTE_BIT;
        entry.tile = new NBTTagCompound();
        tile.writeToNBT(entry.tile);
        NBTTagCompound original = (NBTTagCompound) entry.tile.copy();
        entry.validate(null);
        assertEquals(original, entry.tile);
    }

    @Test(expected = IOException.class)
    public void coveredFrameMissingItsTileIsRejected() throws Exception {
        PackingService.Entry entry = new PackingService.Entry();
        entry.block = bareFrame();
        entry.meta = 316 | BlockFrameBox.MTE_BIT;
        assertTrue(BlockData.requiresTileData(entry.block, entry.meta));
        entry.validate(null);
    }

    @Test
    public void extendedFrameMaterialMetadataIsDecodedWithoutTruncation() throws Exception {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("Meta", 316);
        assertEquals(316, BlockData.readMetadata(data));
        data.setInteger("Meta", 65535);
        assertEquals(65535, BlockData.readMetadata(data));
    }

    @Test(expected = IOException.class)
    public void negativeMetadataIsRejected() throws Exception {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("Meta", -1);
        BlockData.readMetadata(data);
    }

    @Test(expected = IOException.class)
    public void outOfRangeMetadataIsRejected() throws Exception {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("Meta", 65536);
        BlockData.readMetadata(data);
    }
}
