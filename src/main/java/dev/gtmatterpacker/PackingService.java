package dev.gtmatterpacker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.world.BlockEvent;

import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Location;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;
import com.recursive_pineapple.matter_manipulator.mixin.BlockCaptureDrops;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;

final class PackingService {

    private PackingService() {}

    static void pack(EntityPlayerMP player, ItemStack stack) throws Exception {
        if (ItemMatterManipulator.getOrCreateNbtData(stack)
            .hasKey("MatterPackage")) throw new IOException("Deploy the existing package first / 请先部署现有包裹");
        MMState state = ItemMatterManipulator.getState(stack);
        Location a = state.config.coordA, b = state.config.coordB;
        WorldServer world = (WorldServer) player.worldObj;
        if (world.levelSaving) throw new IOException("World saving is disabled");
        if (a == null || b == null || !a.isInWorld(world) || !b.isInWorld(world)) throw new IOException(
            "Select both corners in this dimension using the original Cut mode / 请先用原剪切模式选定本维度两角");
        RegionBounds bounds = new RegionBounds(a.x, a.y, a.z, b.x, b.y, b.z);
        List<Entry> entries = new ArrayList<>();
        for (int y = 0; y < bounds.sy; y++) for (int x = 0; x < bounds.sx; x++) for (int z = 0; z < bounds.sz; z++) {
            int px = bounds.x + x, py = bounds.y + y, pz = bounds.z + z;
            access(player, stack, world, px, py, pz);
            Block block = world.getBlock(px, py, pz);
            if (block == Blocks.air) continue;
            if (block.getBlockHardness(world, px, py, pz) < 0)
                throw new IOException("Unbreakable block: " + px + "," + py + "," + pz);
            TileEntity tile = world.getTileEntity(px, py, pz);
            TileLifecycle.checkAccess(tile, player);
            // Maintenance hatches use the active flag for their own maintenance state, not processing.
            if (tile instanceof IGregTechTileEntity && ((IGregTechTileEntity) tile).isActive()
                && !(((IGregTechTileEntity) tile).getMetaTileEntity() instanceof MTEHatchMaintenance))
                throw new IOException(
                    "Stop active machine / 请先停止活动机器: " + px
                        + ","
                        + py
                        + ","
                        + pz
                        + " ("
                        + Block.blockRegistry.getNameForObject(block)
                        + ")");
            if (tile instanceof BaseMetaTileEntity && !((BaseMetaTileEntity) tile).playerOwnsThis(player, false))
                throw new IOException("Machine belongs to another player");
            if (MinecraftForge.EVENT_BUS
                .post(new BlockEvent.BreakEvent(px, py, pz, world, block, world.getBlockMetadata(px, py, pz), player)))
                throw new IOException("Protected block / 区域受到保护");
            Entry entry = Entry.capture(world, px, py, pz);
            QuantumNodeData.checkAnchor(entry.tile, bounds, world.provider.dimensionId);
            entry.x = x;
            entry.y = y;
            entry.z = z;
            try {
                entry.validate(world);
            } catch (Exception failure) {
                throw new IOException(
                    "Cannot pack / 无法打包 " + px
                        + ","
                        + py
                        + ","
                        + pz
                        + " ("
                        + Block.blockRegistry.getNameForObject(block)
                        + "): "
                        + failure.getMessage(),
                    failure);
            }
            entries.add(entry);
        }
        if (entries.isEmpty()) throw new IOException("Empty selection / 选区为空");
        entries.sort(
            Comparator.comparingInt(Entry::placementOrder)
                .thenComparingInt(e -> e.y));
        for (Entry e : entries) if (!e.matches(Entry.capture(world, bounds.x + e.x, bounds.y + e.y, bounds.z + e.z)))
            throw new IOException("Selection changed during preflight / 检查期间选区发生变化");
        charge(player, stack, entries.size());
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("SizeX", bounds.sx);
        data.setInteger("SizeY", bounds.sy);
        data.setInteger("SizeZ", bounds.sz);
        data.setInteger("SourceDimension", world.provider.dimensionId);
        data.setInteger("SourceX", bounds.x);
        data.setInteger("SourceY", bounds.y);
        data.setInteger("SourceZ", bounds.z);
        NBTTagList list = new NBTTagList();
        for (Entry e : entries) list.appendTag(e.write());
        data.setTag("Blocks", list);
        PackageStore store = PackageStore.forWorld(world);
        String id = store.create(data, player.getUniqueID());
        ItemMatterManipulator.getOrCreateNbtData(stack)
            .setString("MatterPackage", id);
        ItemMatterManipulator.getOrCreateNbtData(stack)
            .setString("PackageName", PackageStore.displayName(id, data));
        boolean previousRestoring = world.restoringBlockSnapshots;
        world.restoringBlockSnapshots = true;
        BlockCaptureDrops.captureDrops(world);
        try {
            clearRegion(world, entries, bounds.x, bounds.y, bounds.z);
            notifyAll(world, entries, bounds.x, bounds.y, bounds.z);
            save(world);
            PackerControl.summary(stack, data);
            state.config.coordC = null;
            state.config.action = null;
            ItemMatterManipulator.setState(stack, state);
            store.transition(id, data, "PACKING", "PACKED");
        } catch (Exception failure) {
            try {
                clearRegion(world, entries, bounds.x, bounds.y, bounds.z);
                restoreRegion(world, entries, bounds.x, bounds.y, bounds.z);
                notifyAll(world, entries, bounds.x, bounds.y, bounds.z);
                rescanAll(world, entries, bounds.x, bounds.y, bounds.z);
                save(world);
            } catch (Exception rollback) {
                failure.addSuppressed(rollback);
            }
            throw new IOException("Package locked for inspection / 打包异常，记录已锁定: " + id, failure);
        } finally {
            BlockCaptureDrops.stopCapturingDrops(world);
            world.restoringBlockSnapshots = previousRestoring;
        }
    }

    static void deploy(EntityPlayerMP player, ItemStack stack) throws Exception {
        String id = ItemMatterManipulator.getOrCreateNbtData(stack)
            .getString("MatterPackage");
        PackageStore store = PackageStore.forWorld(player.worldObj);
        NBTTagCompound data = store.require(id, player.getUniqueID(), "PACKED");
        Location target = ItemMatterManipulator.getState(stack).config.coordC;
        if (target == null || !target.isInWorld(player.worldObj))
            throw new IOException("Mark the destination in this dimension first / 请先标记本维度的部署位置");
        int ox = target.x, oy = target.y, oz = target.z;
        WorldServer world = (WorldServer) player.worldObj;
        if (world.levelSaving) throw new IOException("World saving is disabled");
        int sx = data.getInteger("SizeX"), sy = data.getInteger("SizeY"), sz = data.getInteger("SizeZ");
        if (sx < 1 || sy < 1
            || sz < 1
            || sx > RegionBounds.LIMIT
            || sy > RegionBounds.LIMIT
            || sz > RegionBounds.LIMIT
            || (long) sx * sy * sz > RegionBounds.LIMIT) throw new IOException("Invalid package bounds");
        // Check the whole bounding box, including the original air holes.
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) {
            access(player, stack, world, ox + x, oy + y, oz + z);
            if (world.getBlock(ox + x, oy + y, oz + z) != Blocks.air)
                throw new IOException("Target area occupied / 目标区域被占用");
        }
        NBTTagList list = data.getTagList("Blocks", 10);
        if (list.tagCount() < 1 || list.tagCount() > RegionBounds.LIMIT) throw new IOException("Invalid block count");
        List<Entry> entries = new ArrayList<>();
        Set<String> positions = new HashSet<>();
        for (int i = 0; i < list.tagCount(); i++) {
            Entry e = Entry.read(list.getCompoundTagAt(i));
            if (e.x < 0 || e.x >= sx
                || e.y < 0
                || e.y >= sy
                || e.z < 0
                || e.z >= sz
                || !positions.add(e.x + "," + e.y + "," + e.z)) throw new IOException("Invalid block position");
            e.validate(world);
            entries.add(e);
        }
        entries.sort(
            Comparator.comparingInt(Entry::placementOrder)
                .thenComparingInt(e -> e.y));
        charge(player, stack, entries.size());
        data.setInteger("TargetDimension", world.provider.dimensionId);
        data.setInteger("TargetX", ox);
        data.setInteger("TargetY", oy);
        data.setInteger("TargetZ", oz);
        store.transition(id, data, "PACKED", "DEPLOYING");
        int controllers = 0;
        boolean previousRestoring = world.restoringBlockSnapshots;
        world.restoringBlockSnapshots = true;
        BlockCaptureDrops.captureDrops(world);
        try {
            List<BlockSnapshot> snapshots = new ArrayList<>();
            for (Entry e : entries) snapshots.add(BlockSnapshot.getBlockSnapshot(world, ox + e.x, oy + e.y, oz + e.z));
            restoreRegion(world, entries, ox, oy, oz);
            for (int i = 0; i < entries.size(); i++) {
                if (MinecraftForge.EVENT_BUS.post(new BlockEvent.PlaceEvent(snapshots.get(i), Blocks.air, player)))
                    throw new IOException("Placement denied / 无放置权限");
            }
            notifyAll(world, entries, ox, oy, oz);
            for (Entry e : entries) if (world.getBlock(ox + e.x, oy + e.y, oz + e.z) != e.block
                || (e.tile != null && world.getTileEntity(ox + e.x, oy + e.y, oz + e.z) == null))
                throw new IOException("Block failed to survive placement");
            controllers = rescanAll(world, entries, ox, oy, oz);
            save(world);
            store.transition(id, data, "DEPLOYING", "CONSUMED");
            stack.getTagCompound()
                .removeTag("MatterPackage");
            stack.getTagCompound()
                .removeTag("PackageName");
        } catch (Exception failure) {
            try {
                clearRegion(world, entries, ox, oy, oz);
                notifyAll(world, entries, ox, oy, oz);
                save(world);
            } catch (Exception rollback) {
                failure.addSuppressed(rollback);
            }
            throw new IOException("Package locked for inspection / 部署异常，记录已锁定: " + id, failure);
        } finally {
            BlockCaptureDrops.stopCapturingDrops(world);
            world.restoringBlockSnapshots = previousRestoring;
        }
        player.addChatMessage(
            new ChatComponentText(
                "Deployed / 已部署 " + entries.size() + " blocks; multiblock checks queued / 多方块检查已安排: " + controllers));
    }

    private static void access(EntityPlayerMP player, ItemStack stack, WorldServer world, int x, int y, int z)
        throws IOException {
        if (y < 0 || y >= world.getActualHeight()
            || Math.abs((long) x) >= 29999984
            || Math.abs((long) z) >= 29999984
            || !world.blockExists(x, y, z)) throw new IOException("Outside loaded world / 超出已加载世界范围");
        if (!world.canMineBlock(player, x, y, z) || !player.canPlayerEdit(x, y, z, 1, stack))
            throw new IOException("No permission / 无操作权限");
        if (player.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) > 256D * 256D)
            throw new IOException("Selection is too far away / 选区过远");
    }

    private static void charge(EntityPlayerMP player, ItemStack stack, int count) throws IOException {
        if (!player.capabilities.isCreativeMode && !GTMatterPacker.ITEM.use(stack, count * 512D, player))
            throw new IOException("Insufficient EU / 电量不足");
    }

    private static void save(WorldServer world) throws Exception {
        if (world.levelSaving) throw new IOException("World saving is disabled");
        world.saveAllChunks(true, null);
        world.getSaveHandler()
            .flush();
    }

    private static void clear(WorldServer world, int x, int y, int z) throws IOException {
        if (world.getBlock(x, y, z) != Blocks.air && !world.setBlock(x, y, z, Blocks.air, 0, 2))
            throw new IOException("Block removal failed");
    }

    private static void clearRegion(WorldServer world, List<Entry> entries, int ox, int oy, int oz) throws Exception {
        RegionTransfer.remove(entries.size(), i -> {
            Entry e = entries.get(i);
            TileLifecycle.detach(world, ox + e.x, oy + e.y, oz + e.z);
        }, i -> {
            Entry e = entries.get(i);
            clear(world, ox + e.x, oy + e.y, oz + e.z);
        });
    }

    private static void restoreRegion(WorldServer world, List<Entry> entries, int ox, int oy, int oz) throws Exception {
        RegionTransfer.restore(entries.size(), i -> {
            Entry e = entries.get(i);
            e.placeBlock(world, ox + e.x, oy + e.y, oz + e.z);
        }, i -> {
            Entry e = entries.get(i);
            e.placeTile(world, ox + e.x, oy + e.y, oz + e.z);
        }, i -> {
            Entry e = entries.get(i);
            TileLifecycle.reconnect(world, ox + e.x, oy + e.y, oz + e.z);
        });
    }

    private static int rescanAll(WorldServer world, List<Entry> entries, int ox, int oy, int oz) {
        int count = 0;
        for (Entry e : entries) if (TileLifecycle.rescan(world, ox + e.x, oy + e.y, oz + e.z)) count++;
        return count;
    }

    private static void notifyAll(WorldServer world, List<Entry> entries, int ox, int oy, int oz) {
        for (Entry e : entries) {
            int x = ox + e.x, y = oy + e.y, z = oz + e.z;
            world.markBlockForUpdate(x, y, z);
            world.notifyBlocksOfNeighborChange(x, y, z, world.getBlock(x, y, z));
        }
    }

    static final class Entry {

        int x, y, z, meta;
        Block block;
        NBTTagCompound tile;

        static Entry capture(WorldServer world, int x, int y, int z) {
            Entry e = new Entry();
            e.block = world.getBlock(x, y, z);
            e.meta = world.getBlockMetadata(x, y, z);
            TileEntity te = world.getTileEntity(x, y, z);
            if (te != null) {
                e.tile = new NBTTagCompound();
                te.writeToNBT(e.tile);
            }
            return e;
        }

        NBTTagCompound write() {
            NBTTagCompound n = new NBTTagCompound();
            n.setInteger("X", x);
            n.setInteger("Y", y);
            n.setInteger("Z", z);
            n.setString("Block", (String) Block.blockRegistry.getNameForObject(block));
            n.setInteger("Meta", meta);
            if (tile != null) n.setTag("Tile", tile.copy());
            return n;
        }

        static Entry read(NBTTagCompound n) throws IOException {
            String name = n.getString("Block");
            if (!Block.blockRegistry.containsKey(name)) throw new IOException("Unknown block: " + name);
            Entry e = new Entry();
            e.x = n.getInteger("X");
            e.y = n.getInteger("Y");
            e.z = n.getInteger("Z");
            e.block = (Block) Block.blockRegistry.getObject(name);
            e.meta = BlockData.readMetadata(n);
            if (e.block == Blocks.air) throw new IOException("Invalid stored air block");
            e.tile = n.hasKey("Tile", 10) ? n.getCompoundTag("Tile") : null;
            return e;
        }

        void validate(WorldServer world) throws IOException {
            if (tile == null && BlockData.requiresTileData(block, meta)) throw new IOException(
                "Missing tile entity data / 方块实体缺失: " + Block.blockRegistry.getNameForObject(block) + " meta=" + meta);
            if (tile == null) return;
            if (!block.hasTileEntity(meta)) throw new IOException("Block does not accept a tile entity");
            TileLifecycle.validate(world, tile);
        }

        boolean matches(Entry other) {
            return block == other.block && meta == other.meta
                && (tile == null ? other.tile == null : tile.equals(other.tile));
        }

        int placementOrder() {
            return block.isOpaqueCube() ? 0
                : tile != null || block.getMaterial()
                    .isSolid() ? 1
                        : block.getMaterial()
                            .isLiquid() ? 3 : 2;
        }

        void placeBlock(WorldServer world, int x, int y, int z) throws IOException {
            if (world.getBlock(x, y, z) != Blocks.air) throw new IOException("Target changed during deployment");
            if (!world.setBlock(x, y, z, block, meta, 2)) throw new IOException("Block placement failed");
        }

        void placeTile(WorldServer world, int x, int y, int z) throws IOException {
            if (tile != null) {
                TileEntity te = TileLifecycle.create(world, TileData.relocate(tile, x, y, z));
                world.removeTileEntity(x, y, z);
                world.setTileEntity(x, y, z, te);
                te.markDirty();
                if (world.getTileEntity(x, y, z) != te) throw new IOException("World rejected restored tile entity");
            }
        }
    }
}
