package com.scalarwide.mc;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * 2D region position: X/Z chunk coordinates divided by 32. X and Z are
 * 1024-bit signed values, independent of the int .mca layout.
 */
public final class WideRegionPos {

    public static final int CHUNKS_PER_REGION = 32;
    public static final int BYTES = 2 * (WideChunkCoord1024.BITS / 8);

    private final WideChunkCoord1024 x;
    private final WideChunkCoord1024 z;
    private final int hash;

    private WideRegionPos(WideChunkCoord1024 x, WideChunkCoord1024 z) {
        this.x = x;
        this.z = z;
        this.hash = 31 * x.hashCode() + z.hashCode();
    }

    public static WideRegionPos of(WideChunkPos chunk) {
        return new WideRegionPos(chunk.x().shiftRight(5), chunk.z().shiftRight(5));
    }

    public static WideRegionPos of(BigInteger x, BigInteger z) {
        return new WideRegionPos(WideChunkCoord1024.of(x), WideChunkCoord1024.of(z));
    }

    public static WideRegionPos fromByteArray(byte[] bytes) {
        if (bytes.length != BYTES) {
            throw new IllegalArgumentException("expected " + BYTES + " bytes");
        }
        byte[] xb = Arrays.copyOfRange(bytes, 0, WideChunkCoord1024.BITS / 8);
        byte[] zb = Arrays.copyOfRange(bytes, WideChunkCoord1024.BITS / 8, BYTES);
        return new WideRegionPos(WideChunkCoord1024.fromByteArray(xb),
            WideChunkCoord1024.fromByteArray(zb));
    }

    public WideChunkCoord1024 x() {
        return x;
    }

    public WideChunkCoord1024 z() {
        return z;
    }

    public byte[] toByteArray() {
        byte[] out = new byte[BYTES];
        System.arraycopy(x.toByteArray(), 0, out, 0, WideChunkCoord1024.BITS / 8);
        System.arraycopy(z.toByteArray(), 0, out, WideChunkCoord1024.BITS / 8,
            WideChunkCoord1024.BITS / 8);
        return out;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof WideRegionPos other && x.equals(other.x) && z.equals(other.z);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + z + ")";
    }
}
