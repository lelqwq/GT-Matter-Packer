package dev.gtmatterpacker;

import static org.junit.Assert.*;

import java.io.IOException;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

public class QuantumNodeDataTest {

    private static class DeferredTile {

        private NBTTagCompound pendingProxyNBT;
    }

    private NBTTagCompound source() {
        NBTTagCompound n = new NBTTagCompound();
        n.setString("id", QuantumNodeData.ID);
        n.setInteger("anchorDim", 0);
        n.setInteger("anchorX", 33);
        n.setInteger("anchorY", 73);
        n.setInteger("anchorZ", 1);
        n.setInteger("color", 4);
        n.setBoolean("linked", true);
        NBTTagCompound proxy = new NBTTagCompound();
        proxy.setInteger("p", 5);
        proxy.setLong("k", 6);
        proxy.setLong("g", 7);
        n.setTag("proxy", proxy);
        NBTTagCompound parts = new NBTTagCompound();
        parts.setString("config", "important");
        n.setTag("partContainer", parts);
        return n;
    }

    private void reject(NBTTagCompound source, NBTTagCompound restored) throws Exception {
        try {
            TileData.checkRoundTrip(source, restored, false, false);
            fail("Lost data accepted");
        } catch (IOException expected) {}
    }

    @Test
    public void liveConnectedNodeCanValidateWithoutDiscardingItsDeferredProxy() throws Exception {
        NBTTagCompound source = source(), restored = (NBTTagCompound) source.copy();
        restored.setBoolean("linked", false);
        restored.removeTag("proxy");
        DeferredTile tile = new DeferredTile();
        tile.pendingProxyNBT = (NBTTagCompound) source.copy();
        QuantumNodeData.recoverDeferredProxy(tile, source, restored);
        TileData.checkRoundTrip(source, restored, false, false);
        assertTrue(source.getBoolean("linked"));
        assertEquals(source.getTag("proxy"), restored.getTag("proxy"));
    }

    @Test
    public void changedAnchorColorOrPartConfigurationStillFails() throws Exception {
        NBTTagCompound source = source();
        for (String key : new String[] { "anchorDim", "anchorX", "anchorY", "anchorZ", "color", "partContainer",
            "proxy" }) {
            NBTTagCompound changed = (NBTTagCompound) source.copy();
            changed.removeTag(key);
            reject(source, changed);
        }
    }

    @Test(expected = IOException.class)
    public void missingDeferredProxyFailsSafely() throws Exception {
        NBTTagCompound source = source(), restored = (NBTTagCompound) source.copy();
        restored.removeTag("proxy");
        QuantumNodeData.recoverDeferredProxy(new DeferredTile(), source, restored);
    }

    @Test(expected = IOException.class)
    public void changedDeferredProxyFailsSafely() throws Exception {
        NBTTagCompound source = source(), restored = (NBTTagCompound) source.copy();
        restored.removeTag("proxy");
        DeferredTile tile = new DeferredTile();
        tile.pendingProxyNBT = (NBTTagCompound) source.copy();
        tile.pendingProxyNBT.getCompoundTag("proxy")
            .setLong("k", 99);
        QuantumNodeData.recoverDeferredProxy(tile, source, restored);
    }

    @Test
    public void linkedExceptionOnlyAppliesToTheQuantumNode() throws Exception {
        NBTTagCompound source = source(), restored = (NBTTagCompound) source.copy();
        source.setString("id", "some_other_mod:node");
        restored.setString("id", "some_other_mod:node");
        restored.setBoolean("linked", false);
        reject(source, restored);
    }

    @Test
    public void anchorOutsideSelectionOrInOtherDimensionRemainsAllowed() throws Exception {
        NBTTagCompound source = source();
        RegionBounds box = new RegionBounds(30, 70, 0, 32, 75, 3);
        QuantumNodeData.checkAnchor(source, box, 0);
        QuantumNodeData.checkAnchor(source, new RegionBounds(30, 70, 0, 34, 75, 3), 1);
    }

    @Test(expected = IOException.class)
    public void movingAnchorAndNodeTogetherIsRejected() throws Exception {
        QuantumNodeData.checkAnchor(source(), new RegionBounds(30, 70, 0, 34, 75, 3), 0);
    }

    @Test(expected = IOException.class)
    public void incompleteAnchorDataIsRejected() throws Exception {
        NBTTagCompound source = source();
        source.removeTag("anchorX");
        QuantumNodeData.checkAnchor(source, new RegionBounds(0, 0, 0, 1, 1, 1), 0);
    }
}
