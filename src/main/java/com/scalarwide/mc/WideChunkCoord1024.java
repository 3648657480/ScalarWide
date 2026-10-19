package com.scalarwide.mc;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Dedicated fixed-width 1024-bit signed chunk coordinate for far-domain keys.
 * Sixteen little-endian limbs, two's-complement semantics. This is not a
 * general arithmetic type: it only provides the operations needed by chunk
 * keys (signed comparison, +/-1 neighbour, byte round-trip, hash/equality).
 */
public final class WideChunkCoord1024 implements Comparable<WideChunkCoord1024> {

    public static final int BITS = 1024;
    public static final int LIMBS = 16;

    private static final BigInteger TWO_1023 = BigInteger.ONE.shiftLeft(1023);
    private static final BigInteger MAX_BI = TWO_1023.subtract(BigInteger.ONE);
    private static final BigInteger MIN_BI = TWO_1023.negate();

    public static final WideChunkCoord1024 ZERO = new WideChunkCoord1024(new long[LIMBS], false);
    public static final WideChunkCoord1024 MAX_VALUE =
        new WideChunkCoord1024(maxLimbs(), false);
    public static final WideChunkCoord1024 MIN_VALUE =
        new WideChunkCoord1024(minLimbs(), false);

    private final long[] limbs;
    private final int hash;

    private WideChunkCoord1024(long[] limbs, boolean copy) {
        this.limbs = copy ? limbs.clone() : limbs;
        this.hash = computeHash(this.limbs);
    }

    public static WideChunkCoord1024 of(long value) {
        long[] l = new long[LIMBS];
        l[0] = value;
        long fill = value >> 63;
        for (int i = 1; i < LIMBS; i++) {
            l[i] = fill;
        }
        return new WideChunkCoord1024(l, false);
    }

    public static WideChunkCoord1024 of(BigInteger value) {
        if (value.compareTo(MIN_BI) < 0 || value.compareTo(MAX_BI) > 0) {
            throw new IllegalArgumentException("chunk coordinate out of signed 1024-bit range: " + value);
        }
        long[] l = new long[LIMBS];
        BigInteger rest = value;
        for (int i = 0; i < LIMBS; i++) {
            l[i] = rest.longValue();
            rest = rest.shiftRight(64);
        }
        return new WideChunkCoord1024(l, false);
    }

    public static WideChunkCoord1024 fromByteArray(byte[] bytes) {
        if (bytes.length > BITS / 8) {
            throw new IllegalArgumentException("expected <= " + (BITS / 8) + " bytes");
        }
        return of(new BigInteger(bytes));
    }

    static WideChunkCoord1024 fromLimbs(long[] source, int offset) {
        return new WideChunkCoord1024(Arrays.copyOfRange(source, offset, offset + LIMBS), false);
    }

    public long limb(int index) {
        return limbs[index];
    }

    /** Arithmetic right shift, used for region/local decomposition. */
    public WideChunkCoord1024 shiftRight(int bits) {
        if (bits < 0 || bits >= BITS) {
            throw new IllegalArgumentException("shift out of range: " + bits);
        }
        return of(toBigInteger().shiftRight(bits));
    }

    public BigInteger toBigInteger() {
        return new BigInteger(toByteArray());
    }

    public byte[] toByteArray() {
        byte[] out = new byte[BITS / 8];
        for (int i = 0; i < LIMBS; i++) {
            long v = limbs[i];
            int base = out.length - (i + 1) * 8;
            for (int j = 0; j < 8; j++) {
                out[base + 7 - j] = (byte) (v >>> (j * 8));
            }
        }
        return out;
    }

    public WideChunkCoord1024 neighbor(long delta) {
        if (delta == 0L) {
            return this;
        }
        long[] out = new long[LIMBS];
        long carry = 0L;
        for (int i = 0; i < LIMBS; i++) {
            long b = i == 0 ? delta : (delta >> 63);
            long a = limbs[i];
            long sum = a + b;
            long c1 = Long.compareUnsigned(sum, a) < 0 ? 1L : 0L;
            long sum2 = sum + carry;
            long c2 = Long.compareUnsigned(sum2, sum) < 0 ? 1L : 0L;
            out[i] = sum2;
            carry = c1 + c2;
        }
        WideChunkCoord1024 result = new WideChunkCoord1024(out, false);
        if ((delta > 0L && result.compareTo(this) < 0)
            || (delta < 0L && result.compareTo(this) > 0)) {
            throw new ArithmeticException("chunk coordinate overflow");
        }
        return result;
    }

    @Override
    public int compareTo(WideChunkCoord1024 other) {
        int high = Long.compare(limbs[LIMBS - 1], other.limbs[LIMBS - 1]);
        if (high != 0) {
            return high;
        }
        for (int i = LIMBS - 2; i >= 0; i--) {
            int c = Long.compareUnsigned(limbs[i], other.limbs[i]);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof WideChunkCoord1024 other
            && Arrays.equals(limbs, other.limbs);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return toBigInteger().toString();
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

    private static long[] minLimbs() {
        long[] l = new long[LIMBS];
        l[LIMBS - 1] = Long.MIN_VALUE;
        return l;
    }

    private static long[] maxLimbs() {
        long[] l = new long[LIMBS];
        Arrays.fill(l, -1L);
        l[LIMBS - 1] = Long.MAX_VALUE;
        return l;
    }
}
