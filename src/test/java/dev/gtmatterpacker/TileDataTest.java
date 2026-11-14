package dev.gtmatterpacker;

import static org.junit.Assert.*;

import java.io.IOException;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.Test;

public class TileDataTest {

    private NBTTagCompound machine() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("id", "test:machine");
        tag.setInteger("x", 4);
        tag.setInteger("y", 80);
        tag.setInteger("z", -3);
        tag.setString("Inventory", "valuable items");
        tag.setInteger("mProgresstime", 12);
        tag.setInteger("mMaxProgresstime", 100);
        tag.setString("mOutputItem0", "pending item");
        tag.setString("mOutputFluids0", "pending fluid");
        tag.setLong("mStoredEnergy", 5000);
        return tag;
    }

    @Test
    public void relocationChangesOnlyTopLevelCoordinatesAndNeverMutatesPayload() {
        NBTTagCompound source = machine();
        NBTTagCompound binding = new NBTTagCompound();
        binding.setInteger("x", 123);
        source.setTag("ExternalBinding", binding);
        NBTTagCompound relocated = TileData.relocate(source, -100, 90, 20);
        assertEquals(-100, relocated.getInteger("x"));
        assertEquals(90, relocated.getInteger("y"));
        assertEquals(20, relocated.getInteger("z"));
        assertEquals(4, source.getInteger("x"));
        assertEquals(
            123,
            relocated.getCompoundTag("ExternalBinding")
                .getInteger("x"));
        assertEquals(source.getString("Inventory"), relocated.getString("Inventory"));
    }

    @Test
    public void genericTileMustPreserveAllItsConfiguration() throws Exception {
        NBTTagCompound source = machine();
        TileData.checkRoundTrip(source, TileData.relocate(source, 100, 80, 100), false, false);
        NBTTagCompound damaged = (NBTTagCompound) source.copy();
        damaged.removeTag("Inventory");
        try {
            TileData.checkRoundTrip(source, damaged, false, false);
            fail("Lost inventory accepted");
        } catch (IOException expected) {}
    }

    @Test
    public void everyCriticalGTResourceSurvivesTheRoundTrip() throws Exception {
        NBTTagCompound source = machine();
        TileData.checkRoundTrip(source, (NBTTagCompound) source.copy(), true, false);
        for (String key : new String[] { "Inventory", "mOutputItem0", "mOutputFluids0", "mStoredEnergy",
            "mProgresstime", "mMaxProgresstime" }) {
            NBTTagCompound damaged = (NBTTagCompound) source.copy();
            damaged.removeTag(key);
            try {
                TileData.checkRoundTrip(source, damaged, true, false);
                fail("Missing resource accepted: " + key);
            } catch (IOException expected) {}
        }
    }

    @Test
    public void legacyWirelessCoverAcceptsOnlyTheAddedDefaultInputFlag() throws Exception {
        NBTTagCompound original = machine();
        NBTTagCompound cover = new NBTTagCompound();
        cover.setInteger("id", 11076);
        cover.setByte("s", (byte) 0);
        NBTTagCompound coverData = new NBTTagCompound();
        coverData.setLong("storedEU", 3204485120L);
        coverData.setInteger("voltage", 2097152);
        cover.setTag("d", coverData);
        NBTTagList covers = new NBTTagList();
        covers.appendTag(cover);
        original.setTag("gt.covers", covers);
        NBTTagCompound restored = (NBTTagCompound) original.copy();
        NBTTagCompound restoredData = restored.getTagList("gt.covers", 10)
            .getCompoundTagAt(0)
            .getCompoundTag("d");
        restoredData.setBoolean("allowInput", true);
        NBTTagCompound comparison = (NBTTagCompound) original.copy();
        TileData.normalizeWirelessCover(comparison, restored, 0);
        TileData.checkRoundTrip(comparison, restored, true, false);
        assertFalse(coverData.hasKey("allowInput"));

        restoredData.setLong("storedEU", 0);
        try {
            TileData.checkRoundTrip(comparison, restored, true, false);
            fail("Lost cover energy accepted");
        } catch (IOException expected) {}
        restoredData.setLong("storedEU", 3204485120L);
        restoredData.setBoolean("allowInput", false);
        try {
            TileData.checkRoundTrip(comparison, restored, true, false);
            fail("Changed cover input setting accepted");
        } catch (IOException expected) {}
    }

    @Test
    public void multipartMissingOnePartIsRejected() throws Exception {
        NBTTagCompound source = new NBTTagCompound();
        source.setString("id", "savedMultipart");
        NBTTagList parts = new NBTTagList();
        NBTTagCompound first = new NBTTagCompound();
        first.setString("id", "cable");
        parts.appendTag(first);
        NBTTagCompound second = new NBTTagCompound();
        second.setString("id", "cover");
        parts.appendTag(second);
        source.setTag("parts", parts);
        NBTTagCompound restored = (NBTTagCompound) source.copy();
        restored.getTagList("parts", 10)
            .removeTag(1);
        try {
            TileData.checkRoundTrip(source, restored, false, true);
            fail("Missing multipart accepted");
        } catch (IOException expected) {}
    }

    @Test
    public void AEGridNodeCanBeDeferredButInventoryCannotBeLost() throws Exception {
        NBTTagCompound source = machine();
        NBTTagCompound node = new NBTTagCompound();
        node.setInteger("p", 1);
        node.setLong("k", 2);
        node.setLong("g", 3);
        source.setTag("proxy", node);
        NBTTagCompound restored = (NBTTagCompound) source.copy();
        restored.removeTag("proxy");
        TileData.checkRoundTrip(source, restored, false, true);
        restored.removeTag("Inventory");
        try {
            TileData.checkRoundTrip(source, restored, false, true);
            fail("AE lost inventory accepted");
        } catch (IOException expected) {}
    }

    @Test
    public void arbitraryDataNamedPartIsNeverTreatedAsAGridNode() throws Exception {
        NBTTagCompound source = machine();
        NBTTagCompound part = new NBTTagCompound();
        part.setString("Inventory", "precious contents");
        source.setTag("part", part);
        NBTTagCompound restored = (NBTTagCompound) source.copy();
        restored.removeTag("part");
        try {
            TileData.checkRoundTrip(source, restored, false, true);
            fail("Non-network part data ignored");
        } catch (IOException expected) {}
    }

    @Test(expected = IOException.class)
    public void tileIdentityCannotChange() throws Exception {
        NBTTagCompound source = machine(), wrong = machine();
        wrong.setString("id", "different");
        TileData.checkRoundTrip(source, wrong, true, false);
    }
}
