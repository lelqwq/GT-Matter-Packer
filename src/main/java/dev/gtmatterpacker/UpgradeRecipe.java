package dev.gtmatterpacker;

import java.util.Arrays;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.world.World;

import com.recursive_pineapple.matter_manipulator.common.items.MMItemList;

public final class UpgradeRecipe extends ShapelessRecipes {

    public UpgradeRecipe() {
        super(new ItemStack(GTMatterPacker.ITEM), Arrays.asList(MMItemList.MK0.get(), new ItemStack(Items.ender_eye)));
    }

    @Override
    public boolean matches(InventoryCrafting inv, World world) {
        return source(inv) != null;
    }

    private ItemStack source(InventoryCrafting inv) {
        ItemStack source = null;
        boolean eye = false;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s == null) continue;
            if (s.getItem() == MMItemList.MK0.get()
                .getItem() && source == null) source = s;
            else if (s.getItem() == Items.ender_eye && !eye) eye = true;
            else return null;
        }
        return eye ? source : null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        ItemStack original = source(inv);
        if (original == null) return null;
        ItemStack result = getRecipeOutput();
        if (original.hasTagCompound()) result.setTagCompound(
            (net.minecraft.nbt.NBTTagCompound) original.getTagCompound()
                .copy());
        return result;
    }

    @Override
    public int getRecipeSize() {
        return 2;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return new ItemStack(GTMatterPacker.ITEM);
    }
}
