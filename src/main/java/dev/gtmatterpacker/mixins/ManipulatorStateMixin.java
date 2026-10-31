package dev.gtmatterpacker.mixins;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;

import dev.gtmatterpacker.ItemPackedManipulator;
import dev.gtmatterpacker.PackerData;

@Mixin(value = ItemMatterManipulator.class, remap = false)
public abstract class ManipulatorStateMixin {

    @Inject(method = "setState", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void preservePackage(ItemStack stack, MMState state, CallbackInfo ci) {
        if (!(stack.getItem() instanceof ItemPackedManipulator)) return;
        // Upstream replaces the entire item NBT on every menu/selection update.
        stack.setTagCompound(PackerData.preserve(stack.getTagCompound(), state.save()));
        ci.cancel();
    }
}
