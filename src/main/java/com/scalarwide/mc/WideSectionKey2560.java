package com.scalarwide.mc;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Dedicated 2560-bit section key: chunk X 1024, section Y 512, chunk Z 1024.
 * Stored as one flat little-endian limb array; X occupies limbs 0..15,
 * Y 16..23 and Z 24..39.
 */
public final class WideSectionKey2560 {

    public static final int BITS = 2560;
    public static final int BYTES = BITS / 8;
    private static final int LIMBS = 40;
    private static final int X_OFF = 0;
    private static final int Y_OFF = 16;
    private static final int Z_OFF = 24;

    private final long[] limbs;
    private final int hash;

    private WideSectionKey2560(long[] limbs, boolean copy) {
        this.limbs = copy ? limbs.clone() : limbs;
        this.hash = computeHash(this.limbs);
    }

    public static WideSectionKey2560 of(long x, long y, long z) {
        long[] l = new long[LIMBS];
        writeSigned(l, X_OFF, 16, x);
        writeSigned(l, Y_OFF, 8, y);
        writeSigned(l, Z_OFF, 16, z);
        return new WideSectionKey2560(l, false);
    }

    public static WideSectionKey2560 of(BigInteger x, BigInteger y, BigInteger z) {
        WideChunkCoord1024 xv = WideChunkCoord1024.of(x);
        WideSectionCoord512 yv = WideSectionCoord512.of(y);
        WideChunkCoord1024 zv = WideChunkCoord1024.of(z);
        long[] l = new long[LIMBS];
        for (int i = 0; i < 16; i++) {
            l[X_OFF + i] = xv.limb(i);
            l[Z_OFF + i] = zv.limb(i);
        }
        for (int i = 0; i < 8; i++) {
            l[Y_OFF + i] = yv.limb(i);
        }
        return new WideSectionKey2560(l, false);
    }

    public static WideSectionKey2560 fromByteArray(byte[] bytes) {
        if (bytes.length != BYTES) {
            throw new IllegalArgumentException("expected " + BYTES + " bytes");
        }
        long[] l = new long[LIMBS];
        readAxis(bytes, 0, X_OFF, 16, l);
        readAxis(bytes, 128, Y_OFF, 8, l);
        readAxis(bytes, 192, Z_OFF, 16, l);
        return new WideSectionKey2560(l, false);
    }

    public WideChunkCoord1024 x() {
        return WideChunkCoord1024.fromLimbs(limbs, X_OFF);
    }

    public WideSectionCoord512 y() {
        return WideSectionCoord512.fromLimbs(limbs, Y_OFF);
    }

    public WideChunkCoord1024 z() {
        return WideChunkCoord1024.fromLimbs(limbs, Z_OFF);
    }

    public WideSectionKey2560 neighbor(long dx, long dy, long dz) {
        long[] out = limbs.clone();
        if (dx != 0L) {
            addSmall(out, X_OFF, 16, dx);
        }
        if (dy != 0L) {
            addSmall(out, Y_OFF, 8, dy);
        }
        if (dz != 0L) {
            addSmall(out, Z_OFF, 16, dz);
        }
        return new WideSectionKey2560(out, false);
    }

    public byte[] toByteArray() {
        byte[] out = new byte[BYTES];
        writeAxis(out, 0, X_OFF, 16);
        writeAxis(out, 128, Y_OFF, 8);
        writeAxis(out, 192, Z_OFF, 16);
        return out;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof WideSectionKey2560 other
            && Arrays.equals(limbs, other.limbs);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "(" + x() + ", " + y() + ", " + z() + ")";
    }

    private void writeAxis(byte[] out, int byteOffset, int limbOffset, int limbCount) {
        for (int i = 0; i < limbCount; i++) {
            long v = limbs[limbOffset + i];
            int base = byteOffset + (limbCount - 1 - i) * 8;
            for (int j = 0; j < 8; j++) {
                out[base + 7 - j] = (byte) (v >>> (j * 8));
            }
        }
    }

    private static void readAxis(byte[] in, int byteOffset, int limbOffset, int limbCount,
        long[] out) {
        for (int i = 0; i < limbCount; i++) {
            int base = byteOffset + (limbCount - 1 - i) * 8;
            long v = 0L;
            for (int j = 0; j < 8; j++) {
                v = (v << 8) | (in[base + j] & 0xFFL);
            }
            out[limbOffset + i] = v;
        }
    }

    private static void writeSigned(long[] out, int offset, int count, long value) {
        out[offset] = value;
        long fill = value >> 63;
        for (int i = 1; i < count; i++) {
            out[offset + i] = fill;
        }
    }

    private static void addSmall(long[] out, int offset, int count, long delta) {
        long oldTop = out[offset + count - 1];
        long carry = 0L;
        for (int i = 0; i < count; i++) {
            long b = i == 0 ? delta : (delta >> 63);
            long a = out[offset + i];
            long sum = a + b;
            long c1 = Long.compareUnsigned(sum, a) < 0 ? 1L : 0L;
            long sum2 = sum + carry;
            long c2 = Long.compareUnsigned(sum2, sum) < 0 ? 1L : 0L;
            out[offset + i] = sum2;
            carry = c1 + c2;
        }
        long newTop = out[offset + count - 1];
        if ((delta > 0L && oldTop >= 0L && newTop < 0L)
            || (delta < 0L && oldTop < 0L && newTop >= 0L)) {
            throw new ArithmeticException("section key axis overflow");
        }
    }

    private static int computeHash(long[] limbs) {
        long h = 0x9E3779B97F4A7C15L;
        for (long limb : limbs) {
            h ^= limb;
            h *= 0xBF58476D1CE4E5B9L;
            h = Long.rotateLeft(h, 27);
        }
        return (int) (h ^ (h >>> 32));
    }
}
