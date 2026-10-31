package dev.gtmatterpacker;

import net.minecraft.nbt.NBTTagCompound;

public final class PackerData {

    private PackerData() {}

    public static NBTTagCompound preserve(NBTTagCompound original, NBTTagCompound updated) {
        if (original == null) return updated;
        for (String key : new String[] {
            "MatterPackage", "PackingMode", "PackageSizeX", "PackageSizeY", "PackageSizeZ" })
            if (original.hasKey(key)) updated.setTag(
                key,
                original.getTag(key)
                    .copy());
        return updated;
    }
}
