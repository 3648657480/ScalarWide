package com.scalarwide.mc;

import com.scalarwide.Wide512;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WideChunkKeyTest {

    @Test
    void packRoundTrip() {
        long[] coords = {0, 1, -1, 62, -62, 1L << 49, -(1L << 49), 1L << 62, -(1L << 62),
            Long.MAX_VALUE, Long.MIN_VALUE};
        for (long x : coords) {
            for (long z : coords) {
                Wide512 k = WideChunkKey.pack(x, z);
                assertEquals(x, WideChunkKey.x(k), "x(" + x + "," + z + ")");
                assertEquals(z, WideChunkKey.z(k), "z(" + x + "," + z + ")");
            }
        }
    }

    @Test
    void layoutMatchesPackWide() {
        long x = -(1L << 60) + 12345;
        long z = (1L << 60) - 6789;
        long s = z >> 63;
        long t = x >> 63;
        assertEquals(Wide512.of(z, s, s, s, x, t, t, t), WideChunkKey.pack(x, z));
    }

    @Test
    void neighborDoesNotBleedAcrossHalfBoundary() {
        // z = -1 -> 0 must not increment x: the sign-extended low half is all
        // ones, so a plain packed +1 would carry into the x half.
        Wide512 k = WideChunkKey.pack(5, -1);
        Wide512 up = WideChunkKey.neighbor(k, 0, 1);
        assertEquals(5, WideChunkKey.x(up));
        assertEquals(0, WideChunkKey.z(up));

        Wide512 down = WideChunkKey.neighbor(WideChunkKey.pack(5, 0), 0, -1);
        assertEquals(5, WideChunkKey.x(down));
        assertEquals(-1, WideChunkKey.z(down));
    }

    @Test
    void neighborAcrossAxesAndFarCoords() {
        long base = 1L << 49;
        Wide512 k = WideChunkKey.pack(base, -base);
        assertEquals(base + 1, WideChunkKey.x(WideChunkKey.neighbor(k, 1, 0)));
        assertEquals(-base, WideChunkKey.z(WideChunkKey.neighbor(k, 1, 0)));
        assertEquals(base, WideChunkKey.x(WideChunkKey.neighbor(k, 0, 1)));
        assertEquals(-base + 1, WideChunkKey.z(WideChunkKey.neighbor(k, 0, 1)));
        assertEquals(base - 1, WideChunkKey.x(WideChunkKey.neighbor(k, -1, 0)));
        assertEquals(-base - 1, WideChunkKey.z(WideChunkKey.neighbor(k, 0, -1)));
        assertEquals(k, WideChunkKey.neighbor(k, 0, 0));
    }
}
