package dev.gtmatterpacker;

final class RegionBounds {

    static final int LIMIT = 32768;
    final int x, y, z, sx, sy, sz;

    RegionBounds(int ax, int ay, int az, int bx, int by, int bz) {
        x = Math.min(ax, bx);
        y = Math.min(ay, by);
        z = Math.min(az, bz);
        long dx = Math.abs((long) ax - bx) + 1, dy = Math.abs((long) ay - by) + 1, dz = Math.abs((long) az - bz) + 1;
        if (dx > LIMIT || dy > LIMIT || dz > LIMIT || dx * dy * dz > LIMIT)
            throw new IllegalArgumentException("Selection exceeds " + LIMIT + " positions / 选区体积超限");
        sx = (int) dx;
        sy = (int) dy;
        sz = (int) dz;
    }
}
