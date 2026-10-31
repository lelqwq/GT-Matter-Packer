package dev.gtmatterpacker;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class RegionTransferTest {

    @Test
    public void everyNetworkDetachesBeforeAnyStructureBlockIsRemoved() throws Exception {
        List<String> events = new ArrayList<>();
        RegionTransfer.remove(3, i -> events.add("detach" + i), i -> events.add("clear" + i));
        assertEquals(Arrays.asList("detach0", "detach1", "detach2", "clear2", "clear1", "clear0"), events);
    }

    @Test
    public void controllersAndPipesOnlyReconnectAfterAllHatchesHaveData() throws Exception {
        boolean[] blocks = new boolean[27], tiles = new boolean[27];
        int[] connected = { 0 };
        RegionTransfer.restore(27, i -> blocks[i] = true, i -> {
            for (boolean b : blocks) assertTrue("Tile loaded into an incomplete structure", b);
            tiles[i] = true;
        }, i -> {
            for (boolean t : tiles) assertTrue("Network observed a missing hatch", t);
            connected[0]++;
        });
        assertEquals(27, connected[0]);
    }

    @Test
    public void failedTileLoadDoesNotReconnectAnIncompleteStructure() throws Exception {
        List<String> events = new ArrayList<>();
        try {
            RegionTransfer.restore(3, i -> events.add("block" + i), i -> {
                if (i == 1) throw new IOException("failed hatch");
                events.add("tile" + i);
            }, i -> fail("Reconnected an incomplete machine"));
            fail("Failure was swallowed");
        } catch (IOException expected) {
            assertEquals(Arrays.asList("block0", "block1", "block2", "tile0"), events);
        }
    }

    @Test
    public void failedDetachDoesNotStartClearingBlocks() throws Exception {
        try {
            RegionTransfer.remove(
                3,
                i -> { if (i == 1) throw new IOException("unload failure"); },
                i -> fail("Cleared attached network"));
            fail("Failure was swallowed");
        } catch (IOException expected) {}
    }
}
