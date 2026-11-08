package dev.gtmatterpacker;

import java.io.IOException;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;

import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Location;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMConfig;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState.PendingAction;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState.PlaceMode;
import com.recursive_pineapple.matter_manipulator.common.utils.MMUtils;

final class PackerControl {

    static boolean active(ItemStack stack) {
        return ItemMatterManipulator.getOrCreateNbtData(stack)
            .getBoolean("PackingMode") && ItemMatterManipulator.getState(stack).config.placeMode == PlaceMode.MOVING;
    }

    static boolean loaded(ItemStack stack) {
        return ItemMatterManipulator.getOrCreateNbtData(stack)
            .hasKey("MatterPackage");
    }

    static void perform(EntityPlayerMP player, ItemStack stack, PackerAction action) {
        try {
            if (player.isUsingItem()) throw new IOException("Release the use button first / 请先松开使用键");
            MMState state = ItemMatterManipulator.getState(stack);
            NBTTagCompound tag = ItemMatterManipulator.getOrCreateNbtData(stack);
            switch (action) {
                case PACK_MODE:
                    tag.setBoolean("PackingMode", true);
                    state.config.placeMode = PlaceMode.MOVING;
                    state.config.action = null;
                    state.config.coordC = null;
                    state.config.coordAOffset = null;
                    state.config.coordBOffset = null;
                    state.config.coordCOffset = null;
                    state.config.arraySpan = null;
                    state.config.transform = null;
                    break;
                case GEOMETRY:
                case MOVING:
                case COPYING:
                case EXCHANGING:
                case CABLES:
                    if (loaded(stack))
                        stash(PackageStore.forWorld(player.worldObj), player.getUniqueID(), tag, state.config);
                    tag.setBoolean("PackingMode", false);
                    state.config.placeMode = PlaceMode.valueOf(action.name());
                    state.config.action = null;
                    break;
                case MARK_SOURCE:
                    requireMode(stack);
                    if (loaded(stack)) throw new IOException("Deploy the existing package first / 请先部署包裹");
                    state.config.coordA = null;
                    state.config.coordB = null;
                    state.config.coordC = null;
                    state.config.action = PendingAction.MARK_CUT_A;
                    break;
                case MARK_TARGET:
                    requireMode(stack);
                    if (!loaded(stack)) throw new IOException("No package / 尚未打包");
                    summary(
                        stack,
                        PackageStore.forWorld(player.worldObj)
                            .require(tag.getString("MatterPackage"), player.getUniqueID(), "PACKED"));
                    state.config.coordC = null;
                    state.config.action = PendingAction.MARK_PASTE;
                    previewSource(state, stack, new Location(player.worldObj, MMUtils.getLookingAtLocation(player)));
                    break;
                case EXECUTE:
                    requireMode(stack);
                    if (state.config.action != null) throw new IOException("Finish marking first / 请先完成标记");
                    if (loaded(stack)) PackingService.deploy(player, stack);
                    else PackingService.pack(player, stack);
                    player.addChatMessage(new ChatComponentText("Matter Packer: OK / 操作完成"));
                    return;
                case STATUS:
                    for (String record : PackageStore.forWorld(player.worldObj)
                        .list(player.getUniqueID())) player.addChatMessage(new ChatComponentText(record));
                    return;
                case CLEAR:
                    PackageStore.forWorld(player.worldObj)
                        .require(tag.getString("MatterPackage"), player.getUniqueID(), "CONSUMED");
                    tag.removeTag("MatterPackage");
                    tag.removeTag("PackageName");
                    break;
                case RECOVER:
                    if (loaded(stack)) throw new IOException("This tool already carries a package / 已携带包裹");
                    String id = PackageStore.forWorld(player.worldObj)
                        .latestPacked(player.getUniqueID());
                    if (id == null) throw new IOException("No stored package to recover / 没有可找回的包裹");
                    NBTTagCompound record = PackageStore.forWorld(player.worldObj)
                        .require(id, player.getUniqueID(), "PACKED");
                    tag.setString("MatterPackage", id);
                    tag.setString("PackageName", PackageStore.displayName(id, record));
                    summary(stack, record);
                    state.config.coordC = null;
                    state.config.action = null;
                    break;
                default:
                    throw new IOException("Unknown action");
            }
            ItemMatterManipulator.setState(stack, state);
        } catch (Exception failure) {
            player.addChatMessage(new ChatComponentText("Matter Packer: " + failure.getMessage()));
            GTMatterPacker.LOG.warn("Matter Packer operation rejected or failed", failure);
        } finally {
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
        }
    }

    static void stash(PackageStore store, UUID owner, NBTTagCompound tag, MMConfig config) throws IOException {
        String id = tag.getString("MatterPackage");
        if (id.isEmpty()) throw new IOException("No selected package / 没有选中的包裹");
        store.require(id, owner, "PACKED");
        tag.removeTag("MatterPackage");
        tag.removeTag("PackageName");
        tag.removeTag("PackageSizeX");
        tag.removeTag("PackageSizeY");
        tag.removeTag("PackageSizeZ");
        // Pack and deployment previews share these coordinates with the original modes.
        config.coordA = null;
        config.coordB = null;
        config.coordC = null;
        config.action = null;
    }

    private static void requireMode(ItemStack stack) throws IOException {
        if (!active(stack)) throw new IOException("Select Packing mode first / 请先选择打包储存模式");
    }

    static void summary(ItemStack stack, NBTTagCompound record) {
        NBTTagCompound tag = ItemMatterManipulator.getOrCreateNbtData(stack);
        tag.setInteger("PackageSizeX", record.getInteger("SizeX"));
        tag.setInteger("PackageSizeY", record.getInteger("SizeY"));
        tag.setInteger("PackageSizeZ", record.getInteger("SizeZ"));
    }

    static void previewSource(MMState state, ItemStack stack, Location corner) {
        NBTTagCompound tag = ItemMatterManipulator.getOrCreateNbtData(stack);
        state.config.coordA = corner.clone();
        state.config.coordB = corner.clone()
            .offset(
                Math.max(1, tag.getInteger("PackageSizeX")) - 1,
                Math.max(1, tag.getInteger("PackageSizeY")) - 1,
                Math.max(1, tag.getInteger("PackageSizeZ")) - 1);
        state.config.transform = null;
        state.config.arraySpan = null;
        state.config.coordAOffset = null;
        state.config.coordBOffset = null;
        state.config.coordCOffset = null;
    }
}
