package com.scalarwide;

import java.math.BigInteger;

/**
 * Fixed-width 384-bit integer held in six little-endian {@code long} limbs
 * ({@code l0} least significant .. {@code l5} most significant); the value is
 * interpreted as unsigned by default, but the bit pattern is two's complement.
 *
 * <p>All arithmetic wraps modulo 2^384. {@link #compareTo} is unsigned and
 * consistent with {@link #equals} (suitable for map keys); use
 * {@link #compareSigned} for signed order. Shift amounts must lie in
 * {@code [0, 384)} or {@link IllegalArgumentException} is thrown.
 *
 * <p>Current implementation delegates to the generic {@link Limbs} engine
 * (correctness first); composition from the 128-bit atom is planned.
 */
public final class Wide384 implements Comparable<Wide384> {

    public static final int BITS = 384;
    public static final int LIMBS = 6;
    public static final Wide384 ZERO = new Wide384(0, 0, 0, 0, 0, 0);
    public static final Wide384 ONE = new Wide384(1, 0, 0, 0, 0, 0);
    public static final Wide384 MAX_VALUE = new Wide384(-1L, -1L, -1L, -1L, -1L, -1L);

    private final long l0;
    private final long l1;
    private final long l2;
    private final long l3;
    private final long l4;
    private final long l5;

    private Wide384(long l0, long l1, long l2, long l3, long l4, long l5) {
        this.l0 = l0;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
        this.l4 = l4;
        this.l5 = l5;
    }

    public static Wide384 of(long v) {
        return new Wide384(v, 0, 0, 0, 0, 0);
    }

    public static Wide384 ofSigned(long v) {
        long fill = v >> 63;
        return new Wide384(v, fill, fill, fill, fill, fill);
    }

    public static Wide384 of(long l0, long l1, long l2, long l3, long l4, long l5) {
        return new Wide384(l0, l1, l2, l3, l4, l5);
    }

    public static Wide384 fromBigInteger(BigInteger v) {
        if (v.signum() < 0) {
            throw new IllegalArgumentException("negative value: " + v);
        }
        if (v.bitLength() > BITS) {
            throw new IllegalArgumentException("value does not fit in " + BITS + " bits");
        }
        return new Wide384(
                v.longValue(),
                v.shiftRight(64).longValue(),
                v.shiftRight(128).longValue(),
                v.shiftRight(192).longValue(),
                v.shiftRight(256).longValue(),
                v.shiftRight(320).longValue());
    }

    public BigInteger toBigInteger() {
        return Limbs.toBigInteger(pack());
    }

    public long limb(int i) {
        return switch (i) {
            case 0 -> l0;
            case 1 -> l1;
            case 2 -> l2;
            case 3 -> l3;
            case 4 -> l4;
            case 5 -> l5;
            default -> throw new IndexOutOfBoundsException("limb " + i);
        };
    }

    public long longValue() {
        return l0;
    }

    public boolean isZero() {
        return (l0 | l1 | l2 | l3 | l4 | l5) == 0;
    }

    public int bitLength() {
        if (l5 != 0) {
            return 320 + (64 - Long.numberOfLeadingZeros(l5));
        }
        if (l4 != 0) {
            return 256 + (64 - Long.numberOfLeadingZeros(l4));
        }
        if (l3 != 0) {
            return 192 + (64 - Long.numberOfLeadingZeros(l3));
        }
        if (l2 != 0) {
            return 128 + (64 - Long.numberOfLeadingZeros(l2));
        }
        if (l1 != 0) {
            return 64 + (64 - Long.numberOfLeadingZeros(l1));
        }
        if (l0 != 0) {
            return 64 - Long.numberOfLeadingZeros(l0);
        }
        return 0;
    }

    public Wide384 add(Wide384 o) {
        long[] out = new long[LIMBS];
        Limbs.add(pack(), o.pack(), out);
        return unpack(out);
    }

    public Wide384 sub(Wide384 o) {
        long[] out = new long[LIMBS];
        Limbs.sub(pack(), o.pack(), out);
        return unpack(out);
    }

    public Wide384 neg() {
        long[] out = new long[LIMBS];
        Limbs.neg(pack(), out);
        return unpack(out);
    }

    /** Low 384 bits of the 768-bit product, i.e. {@code (a*b) mod 2^384}. */
    public Wide384 mul(Wide384 o) {
        long[] out = new long[LIMBS];
        Limbs.mulLow(pack(), o.pack(), out);
        return unpack(out);
    }

    /** High 384 bits of the 768-bit product, i.e. {@code (a*b) >>> 384}. */
    public Wide384 mulHigh(Wide384 o) {
        long[] out = new long[LIMBS];
        Limbs.mulHigh(pack(), o.pack(), out);
        return unpack(out);
    }

    public Wide384 and(Wide384 o) {
        long[] out = new long[LIMBS];
        Limbs.and(pack(), o.pack(), out);
        return unpack(out);
    }

    public Wide384 or(Wide384 o) {
        long[] out = new long[LIMBS];
        Limbs.or(pack(), o.pack(), out);
        return unpack(out);
    }

    public Wide384 xor(Wide384 o) {
        long[] out = new long[LIMBS];
        Limbs.xor(pack(), o.pack(), out);
        return unpack(out);
    }

    public Wide384 not() {
        long[] out = new long[LIMBS];
        Limbs.not(pack(), out);
        return unpack(out);
    }

    public Wide384 andNot(Wide384 o) {
        long[] out = new long[LIMBS];
        Limbs.andNot(pack(), o.pack(), out);
        return unpack(out);
    }

    /**
     * Logical left shift by {@code k} bits; bits shifted out are discarded.
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 384)}
     */
    public Wide384 shl(int k) {
        checkShift(k);
        long[] out = new long[LIMBS];
        Limbs.shl(pack(), k, out);
        return unpack(out);
    }

    /**
     * Logical right shift by {@code k} bits (zero fill).
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 384)}
     */
    public Wide384 ushr(int k) {
        checkShift(k);
        long[] out = new long[LIMBS];
        Limbs.ushr(pack(), k, out);
        return unpack(out);
    }

    /**
     * Arithmetic right shift by {@code k} bits (sign-extending).
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 384)}
     */
    public Wide384 shr(int k) {
        checkShift(k);
        long[] out = new long[LIMBS];
        Limbs.shr(pack(), k, out);
        return unpack(out);
    }

    public int compareUnsigned(Wide384 o) {
        return Limbs.compareUnsigned(pack(), o.pack());
    }

    public int compareSigned(Wide384 o) {
        return Limbs.compareSigned(pack(), o.pack());
    }

    /**
     * Unsigned comparison; consistent with {@link #equals}.
     *
     * @see #compareSigned(Wide384)
     */
    @Override
    public int compareTo(Wide384 o) {
        return compareUnsigned(o);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Wide384 o)) {
            return false;
        }
        return l0 == o.l0 && l1 == o.l1 && l2 == o.l2 && l3 == o.l3 && l4 == o.l4 && l5 == o.l5;
    }

    @Override
    public int hashCode() {
        long h = 0x243F6A8885A308D3L;
        h = (h ^ l0) * 0x9E3779B97F4A7C15L;
        h = (h ^ l1) * 0x9E3779B97F4A7C15L;
        h = (h ^ l2) * 0x9E3779B97F4A7C15L;
        h = (h ^ l3) * 0x9E3779B97F4A7C15L;
        h = (h ^ l4) * 0x9E3779B97F4A7C15L;
        h = (h ^ l5) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        return (int) (h ^ (h >>> 32));
    }

    @Override
    public String toString() {
        return toBigInteger().toString();
    }

    public String toHexString() {
        return toBigInteger().toString(16);
    }

    private long[] pack() {
        return new long[] { l0, l1, l2, l3, l4, l5 };
    }

    private static Wide384 unpack(long[] v) {
        return new Wide384(v[0], v[1], v[2], v[3], v[4], v[5]);
    }

    private static void checkShift(int k) {
        if (k < 0 || k >= BITS) {
            throw new IllegalArgumentException("shift amount out of range: " + k);
        }
    }
}
