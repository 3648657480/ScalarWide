package com.scalarwide.mc;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Dedicated signed 512-bit section-Y coordinate. Eight little-endian limbs,
 * two's-complement semantics. Only key-oriented operations are implemented.
 */
public final class WideSectionCoord512 implements Comparable<WideSectionCoord512> {

    public static final int BITS = 512;
    public static final int LIMBS = 8;

    private static final BigInteger TWO_511 = BigInteger.ONE.shiftLeft(511);
    private static final BigInteger MAX_BI = TWO_511.subtract(BigInteger.ONE);
    private static final BigInteger MIN_BI = TWO_511.negate();

    public static final WideSectionCoord512 ZERO =
        new WideSectionCoord512(new long[LIMBS], false);
    public static final WideSectionCoord512 MAX_VALUE =
        new WideSectionCoord512(maxLimbs(), false);
    public static final WideSectionCoord512 MIN_VALUE =
        new WideSectionCoord512(minLimbs(), false);

    private final long[] limbs;
    private final int hash;

    private WideSectionCoord512(long[] limbs, boolean copy) {
        this.limbs = copy ? limbs.clone() : limbs;
        this.hash = computeHash(this.limbs);
    }

    public static WideSectionCoord512 of(long value) {
        long[] l = new long[LIMBS];
        l[0] = value;
        long fill = value >> 63;
        for (int i = 1; i < LIMBS; i++) {
            l[i] = fill;
        }
        return new WideSectionCoord512(l, false);
    }

    public static WideSectionCoord512 of(BigInteger value) {
        if (value.compareTo(MIN_BI) < 0 || value.compareTo(MAX_BI) > 0) {
            throw new IllegalArgumentException("section coordinate out of signed 512-bit range: " + value);
        }
        long[] l = new long[LIMBS];
        BigInteger rest = value;
        for (int i = 0; i < LIMBS; i++) {
            l[i] = rest.longValue();
            rest = rest.shiftRight(64);
        }
        return new WideSectionCoord512(l, false);
    }

    public static WideSectionCoord512 fromByteArray(byte[] bytes) {
        if (bytes.length > BITS / 8) {
            throw new IllegalArgumentException("expected <= " + (BITS / 8) + " bytes");
        }
        return of(new BigInteger(bytes));
    }

    static WideSectionCoord512 fromLimbs(long[] source, int offset) {
        return new WideSectionCoord512(Arrays.copyOfRange(source, offset, offset + LIMBS), false);
    }

    public long limb(int index) {
        return limbs[index];
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

    public WideSectionCoord512 neighbor(long delta) {
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
        WideSectionCoord512 result = new WideSectionCoord512(out, false);
        if ((delta > 0L && result.compareTo(this) < 0)
            || (delta < 0L && result.compareTo(this) > 0)) {
            throw new ArithmeticException("section coordinate overflow");
        }
        return result;
    }

    @Override
    public int compareTo(WideSectionCoord512 other) {
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
        return obj instanceof WideSectionCoord512 other
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
