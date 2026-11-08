package dev.gtmatterpacker;

import java.io.IOException;
import java.lang.reflect.Field;

import net.minecraft.nbt.NBTTagCompound;

final class QuantumNodeData {

    static final String ID = "gtswn.network_quantum_node";

    private QuantumNodeData() {}

    static void recoverDeferredProxy(Object tile, NBTTagCompound source, NBTTagCompound again) throws IOException {
        if (!ID.equals(source.getString("id")) || !source.hasKey("proxy")) return;
        if (!source.hasKey("proxy", 10)) throw new IOException("Invalid quantum node proxy data");
        if (again.hasKey("proxy")) return;
        try {
            Field field = tile.getClass()
                .getDeclaredField("pendingProxyNBT");
            field.setAccessible(true);
            Object pending = field.get(tile);
            if (!(pending instanceof NBTTagCompound))
                throw new IOException("Quantum node did not retain deferred proxy data");
            NBTTagCompound tag = (NBTTagCompound) pending;
            if (!source.getTag("proxy")
                .equals(tag.getTag("proxy"))) throw new IOException("Quantum node deferred proxy data changed");
            again.setTag(
                "proxy",
                tag.getTag("proxy")
                    .copy());
        } catch (ReflectiveOperationException | SecurityException failure) {
            throw new IOException("Cannot verify quantum node deferred proxy data", failure);
        }
    }

    static void checkAnchor(NBTTagCompound tile, RegionBounds bounds, int dimension) throws IOException {
        if (tile == null || !ID.equals(tile.getString("id")) || !tile.hasKey("anchorDim")) return;
        if (!tile.hasKey("anchorDim", 3) || !tile.hasKey("anchorX", 3)
            || !tile.hasKey("anchorY", 3)
            || !tile.hasKey("anchorZ", 3)) throw new IOException("Invalid quantum node anchor / 量子节点锚点数据无效");
        if (tile.getInteger("anchorDim") != dimension) return;
        long x = (long) tile.getInteger("anchorX") - bounds.x;
        long y = (long) tile.getInteger("anchorY") - bounds.y;
        long z = (long) tile.getInteger("anchorZ") - bounds.z;
        if (x >= 0 && x < bounds.sx && y >= 0 && y < bounds.sy && z >= 0 && z < bounds.sz) throw new IOException(
            "Quantum node anchor is inside selection; moving both together is not supported / 量子节点的锚点位于选区内，暂不支持一起搬迁");
    }
}
