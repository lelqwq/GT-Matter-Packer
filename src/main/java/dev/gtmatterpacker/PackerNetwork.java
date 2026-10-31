package dev.gtmatterpacker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

public final class PackerNetwork implements IMessageHandler<PackerNetwork.Request, IMessage> {

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("gtmatterpacker");
    // One pending action per player limits packet flooding and keeps every mutation on the server tick.
    private static final Map<EntityPlayerMP, Request> PENDING = new ConcurrentHashMap<>();

    public static void init() {
        CHANNEL.registerMessage(PackerNetwork.class, Request.class, 0, Side.SERVER);
        FMLCommonHandler.instance()
            .bus()
            .register(new PackerNetwork());
    }

    static void send(PackerAction action, int slot) {
        CHANNEL.sendToServer(new Request(action, slot));
    }

    static void clear() {
        PENDING.clear();
    }

    @Override
    public IMessage onMessage(Request request, MessageContext context) {
        if (request.action != null && request.slot >= 0 && request.slot < 9)
            PENDING.put(context.getServerHandler().playerEntity, request);
        return null;
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Map.Entry<EntityPlayerMP, Request> entry : PENDING.entrySet()) {
            if (!PENDING.remove(entry.getKey(), entry.getValue())) continue;
            EntityPlayerMP player = entry.getKey();
            Request request = entry.getValue();
            ItemStack held = player.getHeldItem();
            if (player.isDead || player.playerNetServerHandler.netManager == null
                || !player.playerNetServerHandler.netManager.isChannelOpen()
                || player.inventory.currentItem != request.slot
                || held == null
                || held.stackSize != 1
                || held.getItem() != GTMatterPacker.ITEM) continue;
            PackerControl.perform(player, held, request.action);
        }
    }

    public static final class Request implements IMessage {

        PackerAction action;
        int slot;

        public Request() {}

        Request(PackerAction action, int slot) {
            this.action = action;
            this.slot = slot;
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeByte(action.ordinal());
            buffer.writeByte(slot);
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            int ordinal = buffer.readUnsignedByte();
            slot = buffer.readUnsignedByte();
            if (ordinal < PackerAction.values().length) action = PackerAction.values()[ordinal];
        }
    }
}
