package dev.gtmatterpacker;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState.PendingAction;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class ItemPackedManipulator extends ItemMatterManipulator {

    public ItemPackedManipulator() {
        // The superclass registers under the active mod's namespace, keeping the original item intact.
        super(ManipulatorTier.Tier3);
        setUnlocalizedName("gtmatterpacker.manipulator");
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        super.addInformation(stack, player, lines, advanced);
        lines.add(StatCollector.translateToLocal("gtmatterpacker.help"));
        if (stack.hasTagCompound() && stack.getTagCompound()
            .hasKey("MatterPackage")) {
            String name = stack.getTagCompound()
                .getString("PackageName");
            lines.add(
                StatCollector.translateToLocal("gtmatterpacker.loaded") + " "
                    + (name.isEmpty() ? stack.getTagCompound()
                        .getString("MatterPackage") : name));
        }
    }

    @Override
    public void onUsingTick(ItemStack stack, EntityPlayer player, int count) {
        if (PackerControl.active(stack)) {
            if (!player.worldObj.isRemote && Integer.MAX_VALUE - count == 10) {
                player.stopUsingItem();
                PackerControl.perform((EntityPlayerMP) player, stack, PackerAction.EXECUTE);
            }
            return;
        }
        // Prevent the inherited move operation from acting on stale coordinates while carrying a package.
        if (stack.hasTagCompound() && stack.getTagCompound()
            .hasKey("MatterPackage")) return;
        super.onUsingTick(stack, player, count);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModularWindow createWindow(UIBuildContext context) {
        return PackerMenu.extend(super.createWindow(context), context);
    }

    @Override
    public boolean handleAction(ItemStack stack, World world, EntityPlayer player, MMState state,
        MovingObjectPosition hit) {
        boolean target = PackerControl.active(stack) && PackerControl.loaded(stack)
            && state.config.action == PendingAction.MARK_PASTE;
        boolean handled = super.handleAction(stack, world, player, state, hit);
        if (target && handled && state.config.coordC != null)
            PackerControl.previewSource(state, stack, state.config.coordC);
        return handled;
    }
}
