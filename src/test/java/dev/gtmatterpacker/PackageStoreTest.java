package dev.gtmatterpacker;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Random;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PackageStoreTest {

    @Test
    public void recoveryOnlyReturnsOwnedPackedRecords() throws Exception {
        PackageStore store = new PackageStore(temp.newFolder());
        UUID owner = UUID.randomUUID();
        NBTTagCompound own = new NBTTagCompound();
        String id = store.create(own, owner);
        store.transition(id, own, "PACKING", "PACKED");
        store.create(new NBTTagCompound(), owner);
        NBTTagCompound foreign = new NBTTagCompound();
        String other = store.create(foreign, UUID.randomUUID());
        store.transition(other, foreign, "PACKING", "PACKED");
        assertEquals(id, store.latestPacked(owner));
        store.transition(id, own, "PACKED", "DEPLOYING");
        assertNull(store.latestPacked(owner));
    }

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private interface Action {

        void run() throws Exception;
    }

    private void rejects(Action action) throws Exception {
        try {
            action.run();
            fail("Unsafe operation accepted");
        } catch (IOException expected) {}
    }

    @Test
    public void persistsLargePayloadAndStateAcrossStoreInstances() throws Exception {
        java.io.File folder = temp.newFolder();
        PackageStore store = new PackageStore(folder);
        UUID owner = UUID.randomUUID();
        byte[] bytes = new byte[256 * 1024];
        new Random(284).nextBytes(bytes);
        NBTTagCompound data = new NBTTagCompound();
        data.setByteArray("Inventory", bytes);
        data.setInteger("SourceDimension", -1);
        String id = store.create(data, owner);
        rejects(() -> store.require(id, owner, "PACKED"));
        store.transition(id, data, "PACKING", "PACKED");
        NBTTagCompound loaded = new PackageStore(folder).require(id, owner, "PACKED");
        assertArrayEquals(bytes, loaded.getByteArray("Inventory"));
        assertEquals(-1, loaded.getInteger("SourceDimension"));
    }

    @Test
    public void refusesForeignOwnerAndCopiedConsumedReferences() throws Exception {
        PackageStore store = new PackageStore(temp.newFolder());
        UUID owner = UUID.randomUUID();
        NBTTagCompound data = new NBTTagCompound();
        String id = store.create(data, owner);
        store.transition(id, data, "PACKING", "PACKED");
        rejects(() -> store.require(id, UUID.randomUUID(), "PACKED"));
        store.transition(id, data, "PACKED", "DEPLOYING");
        rejects(() -> store.require(id, owner, "PACKED"));
        store.transition(id, data, "DEPLOYING", "CONSUMED");
        rejects(() -> store.require(id, owner, "PACKED"));
        rejects(() -> store.transition(id, data, "PACKED", "DEPLOYING"));
        assertTrue(
            store.list(owner)
                .isEmpty());
    }

    @Test
    public void interruptedOperationsRemainLockedAfterRestart() throws Exception {
        java.io.File folder = temp.newFolder();
        PackageStore store = new PackageStore(folder);
        UUID owner = UUID.randomUUID();
        NBTTagCompound data = new NBTTagCompound();
        String id = store.create(data, owner);
        assertEquals(
            "PACKING",
            new PackageStore(folder).read(id)
                .getString("State"));
        assertEquals(
            1,
            new PackageStore(folder).list(owner)
                .size());
        rejects(() -> new PackageStore(folder).require(id, owner, "PACKED"));
        store.transition(id, data, "PACKING", "PACKED");
        store.transition(id, data, "PACKED", "DEPLOYING");
        rejects(() -> new PackageStore(folder).require(id, owner, "PACKED"));
    }

    @Test
    public void rejectsMissingAndNonUuidPaths() throws Exception {
        PackageStore store = new PackageStore(temp.newFolder());
        for (String id : new String[] { "../outside", "", "0-0-0-0-0", UUID.randomUUID()
            .toString() }) rejects(() -> store.read(id));
    }
}
