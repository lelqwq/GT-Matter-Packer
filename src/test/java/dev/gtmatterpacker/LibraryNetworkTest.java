package dev.gtmatterpacker;

import static org.junit.Assert.*;

import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class LibraryNetworkTest {

    @Test
    public void renameRequestPreservesUnicodeAndPage() {
        ByteBuf bytes = Unpooled.buffer();
        try {
            new LibraryNetwork.Request(2, 3, 7, "01234567-0123-0123-0123-012345678901", "采矿场").toBytes(bytes);
            LibraryNetwork.Request result = new LibraryNetwork.Request();
            result.fromBytes(bytes);
            assertEquals(2, result.action);
            assertEquals(3, result.slot);
            assertEquals(7, result.page);
            assertEquals("采矿场", result.name);
        } finally {
            bytes.release();
        }
    }

    @Test
    public void excessiveNameDisablesAction() {
        ByteBuf bytes = Unpooled.buffer();
        try {
            new LibraryNetwork.Request(2, 0, 0, "", new String(new char[65]).replace('\0', 'a')).toBytes(bytes);
            LibraryNetwork.Request result = new LibraryNetwork.Request();
            result.fromBytes(bytes);
            assertEquals(255, result.action);
        } finally {
            bytes.release();
        }
    }

    @Test
    public void responsePreservesSeparateSameNamedRecords() {
        ByteBuf bytes = Unpooled.buffer();
        try {
            LibraryNetwork.Response source = new LibraryNetwork.Response();
            source.page = 1;
            source.pages = 3;
            source.selected = "first";
            source.entries.add(new PackageStore.PackageInfo("first", "采矿场", "PACKED"));
            source.entries.add(new PackageStore.PackageInfo("second", "采矿场", "PACKED"));
            source.toBytes(bytes);
            LibraryNetwork.Response result = new LibraryNetwork.Response();
            result.fromBytes(bytes);
            assertEquals(1, result.page);
            assertEquals(3, result.pages);
            assertEquals("first", result.selected);
            assertEquals(2, result.entries.size());
            assertEquals("second", result.entries.get(1).id);
            assertEquals("采矿场", result.entries.get(1).name);
        } finally {
            bytes.release();
        }
    }
}
