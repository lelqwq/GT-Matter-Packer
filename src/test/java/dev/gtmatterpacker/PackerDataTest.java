package dev.gtmatterpacker;

import static org.junit.Assert.*;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

public class PackerDataTest {

    @Test
    public void preservesModeReferenceAndDimensionsWhenOriginalStateIsReplaced() {
        NBTTagCompound original = new NBTTagCompound();
        original.setBoolean("PackingMode", true);
        original.setString("MatterPackage", "persistent-reference");
        original.setInteger("PackageSizeX", 16);
        original.setInteger("PackageSizeY", 12);
        original.setInteger("PackageSizeZ", 8);
        original.setInteger("charge", 100);
        NBTTagCompound updated = new NBTTagCompound();
        updated.setInteger("charge", 80);
        PackerData.preserve(original, updated);
        assertTrue(updated.getBoolean("PackingMode"));
        assertEquals("persistent-reference", updated.getString("MatterPackage"));
        assertEquals(16, updated.getInteger("PackageSizeX"));
        assertEquals(12, updated.getInteger("PackageSizeY"));
        assertEquals(8, updated.getInteger("PackageSizeZ"));
        assertEquals(80, updated.getInteger("charge"));
        assertEquals(100, original.getInteger("charge"));
    }

    @Test
    public void clearedReferenceIsNotRecreated() {
        NBTTagCompound original = new NBTTagCompound();
        original.setBoolean("PackingMode", false);
        NBTTagCompound updated = PackerData.preserve(original, new NBTTagCompound());
        assertFalse(updated.hasKey("MatterPackage"));
        assertFalse(updated.getBoolean("PackingMode"));
    }
}
