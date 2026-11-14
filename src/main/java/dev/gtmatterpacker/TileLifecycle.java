package dev.gtmatterpacker;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.tile.AEBaseTile;
import codechicken.multipart.MultipartHelper;
import codechicken.multipart.TileMultipart;
import codechicken.multipart.handler.MultipartSaveLoad;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.IIC2Enet;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.CoverableTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import tectech.thing.metaTileEntity.pipe.MTEPipeData;
import tectech.thing.metaTileEntity.pipe.MTEPipeLaser;

final class TileLifecycle {

    private TileLifecycle() {}

    static void checkAccess(Object tile, EntityPlayer player) throws IOException {
        checkAccess(tile, player, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static void checkAccess(Object host, EntityPlayer player, Set<Object> seen) throws IOException {
        if (host == null || !seen.add(host)) return;
        if (host instanceof IGridHost) for (ForgeDirection side : ForgeDirection.values()) {
            checkNode(((IGridHost) host).getGridNode(side), player);
        }
        if (host instanceof IPart) {
            checkNode(((IPart) host).getGridNode(), player);
            checkNode(((IPart) host).getExternalFacingNode(), player);
        }
        if (host instanceof IPartHost) for (ForgeDirection side : ForgeDirection.values())
            checkAccess(((IPartHost) host).getPart(side), player, seen);
        if (host instanceof TileMultipart)
            for (Object part : ((TileMultipart) host).jPartList()) checkAccess(part, player, seen);
    }

    private static void checkNode(IGridNode node, EntityPlayer player) throws IOException {
        if (node == null || node.getGrid() == null) return;
        ISecurityGrid security = node.getGrid()
            .getCache(ISecurityGrid.class);
        if (security != null && !security.hasPermission(player, SecurityPermissions.BUILD))
            throw new IOException("AE network denies building permission / AE 网络拒绝建造权限");
    }

    static TileEntity create(World world, NBTTagCompound data) throws IOException {
        NBTTagCompound copy = (NBTTagCompound) data.copy();
        TileEntity tile;
        if ("savedMultipart".equals(copy.getString("id"))) {
            World previous = MultipartSaveLoad.loadingWorld();
            try {
                tile = MultipartHelper.createTileFromNBT(world, copy);
            } finally {
                MultipartSaveLoad.loadingWorld_$eq(previous);
            }
        } else tile = TileEntity.createAndLoadEntity(copy);
        if (tile == null) throw new IOException("Tile entity could not be restored: " + data.getString("id"));
        return tile;
    }

    static void validate(World world, NBTTagCompound data) throws IOException {
        TileEntity restored = create(world, data);
        boolean gt = restored instanceof IGregTechTileEntity;
        if (gt && ((IGregTechTileEntity) restored).getMetaTileEntity() == null)
            throw new IOException("Unknown GT machine");
        NBTTagCompound again = new NBTTagCompound();
        restored.writeToNBT(again);
        QuantumNodeData.recoverDeferredProxy(restored, data, again);
        NBTTagCompound comparison = (NBTTagCompound) data.copy();
        if (restored instanceof CoverableTileEntity) {
            CoverableTileEntity coverable = (CoverableTileEntity) restored;
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                if (coverable.getCoverAtSide(side)
                    .getClass()
                    .getName()
                    .equals("com.miaokatze.gtswn.common.covers.GTswn_Cover_EnergyWireless"))
                    TileData.normalizeWirelessCover(comparison, again, side.ordinal());
            }
        }
        TileData
            .checkRoundTrip(comparison, again, gt, restored instanceof AEBaseTile || restored instanceof TileMultipart);
    }

    static void detach(World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof IGregTechTileEntity) world.getBlock(x, y, z)
            .getDrops(world, x, y, z, world.getBlockMetadata(x, y, z), 0);
        if (tile instanceof CoverableTileEntity && !(tile instanceof BaseMetaTileEntity))
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS)
                ((CoverableTileEntity) tile).onCoverUnloadAtSide(side);
        // Disconnect AE/multipart network nodes before any structure blocks are removed.
        if (tile != null) tile.onChunkUnload();
        world.removeTileEntity(x, y, z);
    }

    static void reconnect(World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile == null) return;
        if (tile instanceof TileMultipart) {
            TileMultipart multipart = (TileMultipart) tile;
            multipart.onMoved();
            multipart.markDirty();
            multipart.markRender();
            MultipartHelper.sendDescPacket(world, multipart);
        }
        if (tile instanceof BaseMetaTileEntity) ((BaseMetaTileEntity) tile).setCableUpdateDelay(100);
        if (tile instanceof IGregTechTileEntity) {
            IGregTechTileEntity gt = (IGregTechTileEntity) tile;
            if (gt.getMetaTileEntity() instanceof MTEPipeLaser)
                ((MTEPipeLaser) gt.getMetaTileEntity()).updateNeighboringNetworks();
            if (gt.getMetaTileEntity() instanceof MTEPipeData)
                ((MTEPipeData) gt.getMetaTileEntity()).updateNeighboringNetworks();
            gt.issueTileUpdate();
            gt.issueTextureUpdate();
        }
        if (tile instanceof IIC2Enet) ((IIC2Enet) tile).doEnetUpdate();
        tile.markDirty();
    }

    static boolean rescan(World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof IGregTechTileEntity)) return false;
        Object machine = ((IGregTechTileEntity) tile).getMetaTileEntity();
        if (!(machine instanceof MTEMultiBlockBase)) return false;
        MTEMultiBlockBase multi = (MTEMultiBlockBase) machine;
        multi.onStructureChange();
        multi.mStartUpCheck = 1;
        multi.mUpdate = 1;
        // Let the normal first tick initialize subclasses before their forced structure check.
        return true;
    }
}
