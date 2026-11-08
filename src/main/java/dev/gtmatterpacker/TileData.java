package dev.gtmatterpacker;

import java.io.IOException;
import java.util.ArrayList;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

final class TileData {

    private TileData() {}

    static NBTTagCompound relocate(NBTTagCompound source, int x, int y, int z) {
        NBTTagCompound copy = (NBTTagCompound) source.copy();
        copy.setInteger("x", x);
        copy.setInteger("y", y);
        copy.setInteger("z", z);
        return copy;
    }

    static void checkRoundTrip(NBTTagCompound source, NBTTagCompound restored, boolean gt, boolean ae)
        throws IOException {
        if (!source.getString("id")
            .equals(restored.getString("id"))) throw new IOException("Tile identity changed during NBT validation");
        if (gt) {
            // GT load migrates some runtime fields; require its inventories, pending outputs, covers and stored
            // resources.
            for (Object object : source.func_150296_c()) {
                String key = (String) object;
                String lower = key.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("invent") || lower.contains("item")
                    || lower.contains("fluid")
                    || lower.contains("tank")
                    || key.equals("gt.covers")
                    || key.equals("mID")
                    || key.startsWith("mStored")
                    || key.equals("mProgresstime")
                    || key.equals("mMaxProgresstime")) {
                    if (!source.getTag(key)
                        .equals(restored.getTag(key))) throw new IOException("Machine data cannot round-trip: " + key);
                }
            }
        } else {
            NBTTagCompound before = normalized(source, ae), after = normalized(restored, ae);
            if (!before.equals(after))
                throw new IOException("Tile data cannot round-trip / 方块实体数据无法完整恢复: " + source.getString("id"));
        }
    }

    private static NBTTagCompound normalized(NBTTagCompound source, boolean ae) {
        NBTTagCompound copy = (NBTTagCompound) source.copy();
        copy.removeTag("x");
        copy.removeTag("y");
        copy.removeTag("z");
        if (QuantumNodeData.ID.equals(copy.getString("id"))) {
            // Online state is recomputed after the node rejoins its network.
            copy.removeTag("linked");
            if (copy.hasKey("partContainer", 10)) stripNetworkNodes(copy.getCompoundTag("partContainer"));
        }
        if (ae) stripNetworkNodes(copy);
        return copy;
    }

    private static void stripNetworkNodes(NBTBase tag) {
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            // AE defers writing these grid nodes until onReady; inventory/configuration must still match.
            for (String key : new String[] { "proxy", "part" }) {
                if (!compound.hasKey(key, 10)) continue;
                NBTTagCompound node = compound.getCompoundTag(key);
                if (node.func_150296_c()
                    .size() == 3 && node.hasKey("p", 3)
                    && node.hasKey("k", 4)
                    && node.hasKey("g", 4)) compound.removeTag(key);
            }
            for (Object key : new ArrayList<>(compound.func_150296_c()))
                stripNetworkNodes(compound.getTag((String) key));
        } else if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag;
            if (list.func_150303_d() == 10)
                for (int i = 0; i < list.tagCount(); i++) stripNetworkNodes(list.getCompoundTagAt(i));
        }
    }
}
