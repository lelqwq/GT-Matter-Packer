package dev.gtmatterpacker;

import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Location;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMConfig;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState.PendingAction;

import sun.misc.Unsafe;

public class PackageLibraryTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private String saved(PackageStore store, UUID owner, int value) throws Exception {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger("Machine", value);
        String id = store.create(payload, owner);
        store.transition(id, payload, "PACKING", "PACKED");
        return id;
    }

    private MMConfig configWithoutForge() throws Exception {
        // The constructor initializes block specs through the Forge loader.
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (MMConfig) ((Unsafe) field.get(null)).allocateInstance(MMConfig.class);
    }

    @Test
    public void stashingFreesTheToolAndLeavesTheNamedPackageReadyToSelect() throws Exception {
        PackageStore store = new PackageStore(folder.newFolder());
        UUID owner = UUID.randomUUID();
        String id = saved(store, owner, 10);
        store.rename(id, owner, "采矿场");
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("MatterPackage", id);
        tag.setString("PackageName", "采矿场");
        tag.setInteger("PackageSizeX", 5);
        MMConfig config = configWithoutForge();
        config.coordA = new Location(0, 1, 2, 3);
        config.coordB = new Location(0, 4, 5, 6);
        config.coordC = new Location(0, 7, 8, 9);
        config.action = PendingAction.MARK_PASTE;

        PackerControl.stash(store, owner, tag, config);

        assertFalse(tag.hasKey("MatterPackage"));
        assertFalse(tag.hasKey("PackageName"));
        assertFalse(tag.hasKey("PackageSizeX"));
        assertNull(config.coordA);
        assertNull(config.coordB);
        assertNull(config.coordC);
        assertNull(config.action);
        NBTTagCompound saved = store.require(id, owner, "PACKED");
        assertEquals("采矿场", saved.getString("Name"));
        assertEquals(10, saved.getInteger("Machine"));
    }

    @Test
    public void stashingALockedPackageKeepsTheBindingAndCoordinates() throws Exception {
        PackageStore store = new PackageStore(folder.newFolder());
        UUID owner = UUID.randomUUID();
        String id = saved(store, owner, 10);
        store.transition(id, store.require(id, owner, "PACKED"), "PACKED", "DEPLOYING");
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("MatterPackage", id);
        MMConfig config = configWithoutForge();
        Location target = new Location(0, 7, 8, 9);
        config.coordC = target;

        try {
            PackerControl.stash(store, owner, tag, config);
            fail("Stashed a locked package");
        } catch (IOException expected) {
            assertEquals(id, tag.getString("MatterPackage"));
            assertSame(target, config.coordC);
        }
    }

    @Test
    public void multipleNamedPackagesSurviveRestartAndRemainIndependent() throws Exception {
        java.io.File path = folder.newFolder();
        PackageStore store = new PackageStore(path);
        UUID owner = UUID.randomUUID();
        String a = saved(store, owner, 10), b = saved(store, owner, 20);
        saved(store, UUID.randomUUID(), 99);
        store.rename(a, owner, "采矿场");
        store.rename(b, owner, "电力高炉");
        PackageStore reloaded = new PackageStore(path);
        assertEquals(
            2,
            reloaded.records(owner)
                .size());
        assertEquals("采矿场", PackageStore.displayName(a, reloaded.require(a, owner, "PACKED")));
        assertEquals("电力高炉", PackageStore.displayName(b, reloaded.require(b, owner, "PACKED")));
        NBTTagCompound first = reloaded.require(a, owner, "PACKED");
        reloaded.transition(a, first, "PACKED", "DEPLOYING");
        reloaded.transition(a, first, "DEPLOYING", "CONSUMED");
        assertEquals(
            1,
            reloaded.records(owner)
                .size());
        assertEquals(
            20,
            reloaded.require(b, owner, "PACKED")
                .getInteger("Machine"));
    }

    @Test
    public void renamingCannotChangeAnotherPlayersOrLockedRecord() throws Exception {
        PackageStore store = new PackageStore(folder.newFolder());
        UUID owner = UUID.randomUUID();
        String id = saved(store, owner, 5);
        try {
            store.rename(id, UUID.randomUUID(), "stolen");
            fail();
        } catch (IOException expected) {}
        NBTTagCompound data = store.require(id, owner, "PACKED");
        store.transition(id, data, "PACKED", "DEPLOYING");
        try {
            store.rename(id, owner, "locked");
            fail();
        } catch (IOException expected) {}
    }

    @Test
    public void namesRejectControlsEmptyAndExcessiveLengths() throws Exception {
        PackageStore store = new PackageStore(folder.newFolder());
        UUID owner = UUID.randomUUID();
        String id = saved(store, owner, 5);
        for (String value : new String[] { "  ", "a\nb", "\u00a7c", new String(new char[65]).replace('\0', 'a') }) {
            try {
                store.rename(id, owner, value);
                fail("Accepted invalid name");
            } catch (IOException expected) {}
        }
    }

    @Test
    public void legacyRecordsReceiveStableFallbackNames() throws Exception {
        PackageStore store = new PackageStore(folder.newFolder());
        UUID owner = UUID.randomUUID();
        String id = saved(store, owner, 5);
        NBTTagCompound data = store.require(id, owner, "PACKED");
        data.removeTag("Name");
        assertEquals("Structure " + id.substring(0, 8), PackageStore.displayName(id, data));
    }
}
