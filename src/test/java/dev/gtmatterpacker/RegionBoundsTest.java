package dev.gtmatterpacker;

import static org.junit.Assert.*;

import org.junit.Test;

public class RegionBoundsTest {

    @Test
    public void supportsThirtyTwoCubedStructureAndLargeThinArrays() {
        RegionBounds cube = new RegionBounds(0, 0, 0, 31, 31, 31);
        assertEquals(32768, cube.sx * cube.sy * cube.sz);
        RegionBounds array = new RegionBounds(0, 0, 0, 63, 7, 63);
        assertEquals(32768, array.sx * array.sy * array.sz);
    }

    @Test
    public void reversedCornersAndInclusiveVolume() {
        RegionBounds b = new RegionBounds(15, 20, 15, 0, 5, 0);
        assertEquals(0, b.x);
        assertEquals(5, b.y);
        assertEquals(16, b.sx);
        assertEquals(4096, b.sx * b.sy * b.sz);
    }

    @Test
    public void singleBlockIsOnePosition() {
        RegionBounds b = new RegionBounds(-7, 4, 3, -7, 4, 3);
        assertEquals(1, b.sx * b.sy * b.sz);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsExcessVolume() {
        new RegionBounds(0, 0, 0, 32, 31, 31);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsIntegerOverflow() {
        new RegionBounds(Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 0, 0);
    }
}
