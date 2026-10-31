package dev.gtmatterpacker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

final class PackageStore {

    static final int MAX_BYTES = 16 * 1024 * 1024;
    private final File folder;

    PackageStore(File folder) {
        this.folder = folder;
    }

    static PackageStore forWorld(World world) {
        World root = DimensionManager.getWorld(0);
        if (root == null) throw new IllegalStateException("Overworld is unavailable");
        return new PackageStore(
            new File(
                root.getSaveHandler()
                    .getWorldDirectory(),
                "gtmatterpacker/packages"));
    }

    String create(NBTTagCompound data, UUID owner) throws IOException {
        String id = UUID.randomUUID()
            .toString();
        data.setInteger("Version", 1);
        data.setString("Owner", owner.toString());
        data.setString("State", "PACKING");
        write(id, data);
        return id;
    }

    NBTTagCompound read(String id) throws IOException {
        Path path = path(id);
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_BYTES)
            throw new IOException("Missing/oversized package: " + id);
        NBTTagCompound data = CompressedStreamTools
            .func_152457_a(Files.readAllBytes(path), new NBTSizeTracker(64L * 1024 * 1024));
        if (data.getInteger("Version") != 1) throw new IOException("Unsupported package version");
        return data;
    }

    NBTTagCompound require(String id, UUID owner, String state) throws IOException {
        NBTTagCompound data = read(id);
        if (!owner.toString()
            .equals(data.getString("Owner"))) throw new IOException("Package belongs to another player");
        if (!state.equals(data.getString("State")))
            throw new IOException("Package state: " + data.getString("State") + "; cannot deploy");
        return data;
    }

    void transition(String id, NBTTagCompound data, String expected, String next) throws IOException {
        if (!expected.equals(read(id).getString("State"))) throw new IOException("Package state changed");
        data.setString("State", next);
        write(id, data);
    }

    List<String> list(UUID owner) throws IOException {
        List<String> ids = new ArrayList<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".nbt"));
        if (files == null) return ids;
        for (File file : files) {
            String id = file.getName()
                .replace(".nbt", "");
            NBTTagCompound data = read(id);
            if (owner.toString()
                .equals(data.getString("Owner")) && !"CONSUMED".equals(data.getString("State")))
                ids.add(id + " " + data.getString("State"));
        }
        return ids;
    }

    private Path path(String id) throws IOException {
        try {
            if (!UUID.fromString(id)
                .toString()
                .equals(id)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid package ID", e);
        }
        return new File(folder, id + ".nbt").toPath();
    }

    String latestPacked(UUID owner) throws IOException {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".nbt"));
        String latest = null;
        long time = Long.MIN_VALUE;
        if (files == null) return null;
        for (File file : files) {
            String id = file.getName()
                .replace(".nbt", "");
            NBTTagCompound record = read(id);
            if (owner.toString()
                .equals(record.getString("Owner")) && "PACKED".equals(record.getString("State"))
                && file.lastModified() >= time) {
                latest = id;
                time = file.lastModified();
            }
        }
        return latest;
    }

    private void write(String id, NBTTagCompound data) throws IOException {
        byte[] bytes = CompressedStreamTools.compress(data);
        if (bytes.length > MAX_BYTES) throw new IOException("Package exceeds 16 MiB");
        CompressedStreamTools.func_152457_a(bytes, new NBTSizeTracker(64L * 1024 * 1024));
        Path target = path(id);
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), id, ".pending");
        try {
            try (FileOutputStream out = new FileOutputStream(temp.toFile())) {
                out.write(bytes);
                out.getFD()
                    .sync();
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
