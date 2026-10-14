package com.scalarwide.mc;

import com.scalarwide.Wide512;

/**
 * Static helpers for the W4 chunk-key encoding: {@code (x, z)} packed into one
 * {@link Wide512} as x high 256 / z low 256, each 64-bit chunk coordinate
 * sign-extended into its half (the same layout as the patched
 * {@code ChunkPos.packWide()}).
 *
 * <p>Node identity note: the ticket trackers ({@code DynamicGraphMinFixedPoint})
 * and the {@code ChunkMap} holder maps key the SAME chunk by this value, so the
 * encoding must be shared and stable. Neighbour arithmetic must NOT be a plain
 * {@code +-1} on the packed value: the sign-extended low half would carry into
 * the x half, so {@link #neighbor} unpacks, offsets the halves and repacks.</p>
 */
public final class WideChunkKey {

    private WideChunkKey() {
    }

    /** Packs {@code (x, z)} chunk coordinates (sign-extended halves). */
    public static Wide512 pack(long x, long z) {
        long s = z >> 63;
        long t = x >> 63;
        return Wide512.of(z, s, s, s, x, t, t, t);
    }

    /** @return the x chunk coordinate (limb 4 holds the raw 64-bit value). */
    public static long x(Wide512 key) {
        return key.limb(4);
    }

    /** @return the z chunk coordinate (limb 0 holds the raw 64-bit value). */
    public static long z(Wide512 key) {
        return key.limb(0);
    }

    /**
     * Neighbour at chunk offset {@code (dx, dz)}.
     *
     * <p>Long arithmetic wraps at the extremes of the 64-bit chunk domain
     * (same boundary behaviour as the long-domain code it replaces).</p>
     */
    public static Wide512 neighbor(Wide512 key, int dx, int dz) {
        return pack(x(key) + dx, z(key) + dz);
    }
}
