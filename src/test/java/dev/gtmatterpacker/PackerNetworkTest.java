package dev.gtmatterpacker;

import static org.junit.Assert.*;

import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PackerNetworkTest {

    @Test
    public void everyMenuActionRoundTripsWithItsHotbarSlot() {
        for (PackerAction action : PackerAction.values()) {
            ByteBuf buffer = Unpooled.buffer();
            try {
                new PackerNetwork.Request(action, 7).toBytes(buffer);
                PackerNetwork.Request decoded = new PackerNetwork.Request();
                decoded.fromBytes(buffer);
                assertEquals(action, decoded.action);
                assertEquals(7, decoded.slot);
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    public void unknownActionDoesNotBecomeAnExecutableRequest() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeByte(255);
            buffer.writeByte(0);
            PackerNetwork.Request decoded = new PackerNetwork.Request();
            decoded.fromBytes(buffer);
            assertNull(decoded.action);
        } finally {
            buffer.release();
        }
    }
}
