package dev.gtmatterpacker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

public final class LibraryNetwork {

    static final int PAGE_SIZE = 8;
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("gtmp_library");
    private static final Map<EntityPlayerMP, Request> PENDING = new ConcurrentHashMap<>();

    public static void init() {
        CHANNEL.registerMessage(ServerHandler.class, Request.class, 0, Side.SERVER);
        CHANNEL.registerMessage(ClientHandler.class, Response.class, 1, Side.CLIENT);
        FMLCommonHandler.instance()
            .bus()
            .register(new LibraryNetwork());
    }

    static void clear() {
        PENDING.clear();
    }

    static void send(int action, int slot, int page, String id, String name) {
        CHANNEL.sendToServer(new Request(action, slot, page, id, name));
    }

    public static final class Request implements IMessage {

        int action, slot, page;
        String id = "", name = "";

        public Request() {}

        Request(int action, int slot, int page, String id, String name) {
            this.action = action;
            this.slot = slot;
            this.page = page;
            this.id = id;
            this.name = name;
        }

        @Override
        public void toBytes(ByteBuf out) {
            out.writeByte(action);
            out.writeByte(slot);
            out.writeInt(page);
            ByteBufUtils.writeUTF8String(out, id);
            ByteBufUtils.writeUTF8String(out, name);
        }

        @Override
        public void fromBytes(ByteBuf in) {
            action = in.readUnsignedByte();
            slot = in.readUnsignedByte();
            page = in.readInt();
            id = ByteBufUtils.readUTF8String(in);
            name = ByteBufUtils.readUTF8String(in);
            if (id.length() > 36 || name.length() > 64) action = 255;
        }
    }

    public static final class Response implements IMessage {

        int page, pages;
        String selected = "", message = "";
        List<PackageStore.PackageInfo> entries = new ArrayList<>();

        public Response() {}

        @Override
        public void toBytes(ByteBuf out) {
            out.writeInt(page);
            out.writeInt(pages);
            ByteBufUtils.writeUTF8String(out, selected);
            ByteBufUtils.writeUTF8String(out, message);
            out.writeByte(entries.size());
            for (PackageStore.PackageInfo e : entries) {
                ByteBufUtils.writeUTF8String(out, e.id);
                ByteBufUtils.writeUTF8String(out, e.name);
                ByteBufUtils.writeUTF8String(out, e.state);
            }
        }

        @Override
        public void fromBytes(ByteBuf in) {
            page = in.readInt();
            pages = in.readInt();
            selected = ByteBufUtils.readUTF8String(in);
            message = ByteBufUtils.readUTF8String(in);
            int count = in.readUnsignedByte();
            if (count > PAGE_SIZE) throw new IllegalArgumentException("Invalid package page");
            for (int i = 0; i < count; i++) entries.add(
                new PackageStore.PackageInfo(
                    ByteBufUtils.readUTF8String(in),
                    ByteBufUtils.readUTF8String(in),
                    ByteBufUtils.readUTF8String(in)));
        }
    }

    public static final class ServerHandler implements IMessageHandler<Request, IMessage> {

        @Override
        public IMessage onMessage(Request request, MessageContext context) {
            if (request.action <= 3 && request.slot < 9 && request.page >= 0 && request.page < 1000000)
                PENDING.put(context.getServerHandler().playerEntity, request);
            return null;
        }
    }

    public static final class ClientHandler implements IMessageHandler<Response, IMessage> {

        @Override
        public IMessage onMessage(Response response, MessageContext context) {
            GTMatterPacker.proxy.libraryResponse(response);
            return null;
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Map.Entry<EntityPlayerMP, Request> entry : PENDING.entrySet()) {
            if (!PENDING.remove(entry.getKey(), entry.getValue())) continue;
            EntityPlayerMP player = entry.getKey();
            Request req = entry.getValue();
            ItemStack stack = player.getHeldItem();
            if (player.isDead || player.playerNetServerHandler.netManager == null
                || !player.playerNetServerHandler.netManager.isChannelOpen()
                || player.inventory.currentItem != req.slot
                || stack == null
                || stack.stackSize != 1
                || stack.getItem() != GTMatterPacker.ITEM) continue;
            Response response = new Response();
            try {
                if (!PackerControl.active(stack)) throw new IOException("Select Packing mode first / 请先选择打包储存模式");
                PackageStore store = PackageStore.forWorld(player.worldObj);
                NBTTagCompound tag = ItemMatterManipulator.getOrCreateNbtData(stack);
                String previous = tag.getString("MatterPackage");
                if (req.action == 1) {
                    NBTTagCompound data = store.require(req.id, player.getUniqueID(), "PACKED");
                    if (!previous.isEmpty() && !previous.equals(req.id))
                        store.require(previous, player.getUniqueID(), "PACKED");
                    tag.setString("MatterPackage", req.id);
                    tag.setString("PackageName", PackageStore.displayName(req.id, data));
                    PackerControl.summary(stack, data);
                    MMState state = ItemMatterManipulator.getState(stack);
                    state.config.action = null;
                    state.config.coordC = null;
                    ItemMatterManipulator.setState(stack, state);
                    response.message = "Selected / 已选中";
                } else if (req.action == 2) {
                    store.rename(req.id, player.getUniqueID(), req.name);
                    if (req.id.equals(previous)) tag.setString("PackageName", req.name.trim());
                    response.message = "Renamed / 已重命名";
                } else if (req.action == 3) {
                    MMState state = ItemMatterManipulator.getState(stack);
                    PackerControl.stash(store, player.getUniqueID(), tag, state.config);
                    ItemMatterManipulator.setState(stack, state);
                    response.message = "Stored / 已存入列表";
                }
                player.inventory.markDirty();
                player.inventoryContainer.detectAndSendChanges();
                List<PackageStore.PackageInfo> all = store.records(player.getUniqueID());
                response.pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
                response.page = Math.min(req.page, response.pages - 1);
                int from = response.page * PAGE_SIZE;
                response.entries.addAll(all.subList(from, Math.min(from + PAGE_SIZE, all.size())));
                response.selected = ItemMatterManipulator.getOrCreateNbtData(stack)
                    .getString("MatterPackage");
            } catch (Exception failure) {
                response.message = "Error / 错误: " + failure.getMessage();
                response.pages = 1;
                GTMatterPacker.LOG.warn("Package library failed", failure);
            }
            CHANNEL.sendTo(response, player);
        }
    }
}
