package com.scalarwide;

import java.math.BigInteger;

/**
 * Fixed-width 768-bit integer held in twelve little-endian {@code long} limbs
 * ({@code l0} least significant .. {@code l11} most significant); the value is
 * interpreted as unsigned by default, but the bit pattern is two's complement.
 *
 * <p>All arithmetic wraps modulo 2^768. {@link #compareTo} is unsigned and
 * consistent with {@link #equals} (suitable for map keys); use
 * {@link #compareSigned} for signed order. Shift amounts must lie in
 * {@code [0, 768)} or {@link IllegalArgumentException} is thrown.
 *
 * <p>Current implementation delegates to the generic {@link Limbs} engine
 * (correctness first); composition from the 256-bit atom (3x256) is planned.
 */
public final class Wide768 implements Comparable<Wide768> {

    public static final int BITS = 768;
    public static final int LIMBS = 12;
    public static final Wide768 ZERO = new Wide768(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    public static final Wide768 ONE = new Wide768(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    public static final Wide768 MAX_VALUE =
            new Wide768(-1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L);

    private final long l0;
    private final long l1;
    private final long l2;
    private final long l3;
    private final long l4;
    private final long l5;
    private final long l6;
    private final long l7;
    private final long l8;
    private final long l9;
    private final long l10;
    private final long l11;

    private Wide768(long l0, long l1, long l2, long l3, long l4, long l5,
                    long l6, long l7, long l8, long l9, long l10, long l11) {
        this.l0 = l0;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
        this.l4 = l4;
        this.l5 = l5;
        this.l6 = l6;
        this.l7 = l7;
        this.l8 = l8;
        this.l9 = l9;
        this.l10 = l10;
        this.l11 = l11;
    }

    /** Zero-extends a {@code long} value. */
    public static Wide768 of(long v) {
        return new Wide768(v, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /** Sign-extends a {@code long} value. */
    public static Wide768 ofSigned(long v) {
        long fill = v >> 63;
        return new Wide768(v, fill, fill, fill, fill, fill, fill, fill, fill, fill, fill, fill);
    }

    public static Wide768 of(long l0, long l1, long l2, long l3, long l4, long l5,
                             long l6, long l7, long l8, long l9, long l10, long l11) {
        return new Wide768(l0, l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11);
    }

    /**
     * @throws IllegalArgumentException if {@code v} is negative or does not
     *         fit in 768 bits
     */
    public static Wide768 fromBigInteger(BigInteger v) {
        if (v.signum() < 0) {
            throw new IllegalArgumentException("negative value: " + v);
        }
        if (v.bitLength() > BITS) {
            throw new IllegalArgumentException("value does not fit in " + BITS + " bits");
        }
        return new Wide768(
                v.longValue(),
                v.shiftRight(64).longValue(),
                v.shiftRight(128).longValue(),
                v.shiftRight(192).longValue(),
                v.shiftRight(256).longValue(),
                v.shiftRight(320).longValue(),
                v.shiftRight(384).longValue(),
                v.shiftRight(448).longValue(),
                v.shiftRight(512).longValue(),
                v.shiftRight(576).longValue(),
                v.shiftRight(640).longValue(),
                v.shiftRight(704).longValue());
    }

    /** Unsigned value as a non-negative {@link BigInteger}. */
    public BigInteger toBigInteger() {
        return Limbs.toBigInteger(pack());
    }

    /** {@code i}-th limb, {@code 0} = least significant. */
    public long limb(int i) {
        return switch (i) {
            case 0 -> l0;
            case 1 -> l1;
            case 2 -> l2;
            case 3 -> l3;
            case 4 -> l4;
            case 5 -> l5;
            case 6 -> l6;
            case 7 -> l7;
            case 8 -> l8;
            case 9 -> l9;
            case 10 -> l10;
            case 11 -> l11;
            default -> throw new IndexOutOfBoundsException("limb " + i);
        };
    }

    /** Low 64 bits. */
    public long longValue() {
        return l0;
    }

    public boolean isZero() {
        return (l0 | l1 | l2 | l3 | l4 | l5 | l6 | l7 | l8 | l9 | l10 | l11) == 0;
    }

    public int bitLength() {
        if (l11 != 0) {
            return 704 + (64 - Long.numberOfLeadingZeros(l11));
        }
        if (l10 != 0) {
            return 640 + (64 - Long.numberOfLeadingZeros(l10));
        }
        if (l9 != 0) {
            return 576 + (64 - Long.numberOfLeadingZeros(l9));
        }
        if (l8 != 0) {
            return 512 + (64 - Long.numberOfLeadingZeros(l8));
        }
        if (l7 != 0) {
            return 448 + (64 - Long.numberOfLeadingZeros(l7));
        }
        if (l6 != 0) {
            return 384 + (64 - Long.numberOfLeadingZeros(l6));
        }
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

    /** Wrapping addition modulo 2^768. */
    public Wide768 add(Wide768 o) {
        long[] out = new long[LIMBS];
        Limbs.add(pack(), o.pack(), out);
        return unpack(out);
    }

    /** Wrapping subtraction modulo 2^768. */
    public Wide768 sub(Wide768 o) {
        long[] out = new long[LIMBS];
        Limbs.sub(pack(), o.pack(), out);
        return unpack(out);
    }

    /** Wrapping negation modulo 2^768. */
    public Wide768 neg() {
        long[] out = new long[LIMBS];
        Limbs.neg(pack(), out);
        return unpack(out);
    }

    /** Low 768 bits of the 1536-bit product, i.e. {@code (a*b) mod 2^768}. */
    public Wide768 mul(Wide768 o) {
        long[] out = new long[LIMBS];
        Limbs.mulLow(pack(), o.pack(), out);
        return unpack(out);
    }

    /** High 768 bits of the 1536-bit product, i.e. {@code (a*b) >>> 768}. */
    public Wide768 mulHigh(Wide768 o) {
        long[] out = new long[LIMBS];
        Limbs.mulHigh(pack(), o.pack(), out);
        return unpack(out);
    }

    public Wide768 and(Wide768 o) {
        long[] out = new long[LIMBS];
        Limbs.and(pack(), o.pack(), out);
        return unpack(out);
    }

    public Wide768 or(Wide768 o) {
        long[] out = new long[LIMBS];
        Limbs.or(pack(), o.pack(), out);
        return unpack(out);
    }

    public Wide768 xor(Wide768 o) {
        long[] out = new long[LIMBS];
        Limbs.xor(pack(), o.pack(), out);
        return unpack(out);
    }

    public Wide768 not() {
        long[] out = new long[LIMBS];
        Limbs.not(pack(), out);
        return unpack(out);
    }

    public Wide768 andNot(Wide768 o) {
        long[] out = new long[LIMBS];
        Limbs.andNot(pack(), o.pack(), out);
        return unpack(out);
    }

    /**
     * Logical left shift by {@code k} bits; bits shifted out are discarded.
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 768)}
     */
    public Wide768 shl(int k) {
        checkShift(k);
        long[] out = new long[LIMBS];
        Limbs.shl(pack(), k, out);
        return unpack(out);
    }

    /**
     * Logical right shift by {@code k} bits (zero fill).
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 768)}
     */
    public Wide768 ushr(int k) {
        checkShift(k);
        long[] out = new long[LIMBS];
        Limbs.ushr(pack(), k, out);
        return unpack(out);
    }

    /**
     * Arithmetic right shift by {@code k} bits (sign-extending).
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 768)}
     */
    public Wide768 shr(int k) {
        checkShift(k);
        long[] out = new long[LIMBS];
        Limbs.shr(pack(), k, out);
        return unpack(out);
    }

    public int compareUnsigned(Wide768 o) {
        return Limbs.compareUnsigned(pack(), o.pack());
    }

    public int compareSigned(Wide768 o) {
        return Limbs.compareSigned(pack(), o.pack());
    }

    /**
     * Unsigned comparison; consistent with {@link #equals}.
     *
     * @see #compareSigned(Wide768)
     */
    @Override
    public int compareTo(Wide768 o) {
        return compareUnsigned(o);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Wide768 o)) {
            return false;
        }
        return l0 == o.l0 && l1 == o.l1 && l2 == o.l2 && l3 == o.l3
                && l4 == o.l4 && l5 == o.l5 && l6 == o.l6 && l7 == o.l7
                && l8 == o.l8 && l9 == o.l9 && l10 == o.l10 && l11 == o.l11;
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
        h = (h ^ l6) * 0x9E3779B97F4A7C15L;
        h = (h ^ l7) * 0x9E3779B97F4A7C15L;
        h = (h ^ l8) * 0x9E3779B97F4A7C15L;
        h = (h ^ l9) * 0x9E3779B97F4A7C15L;
        h = (h ^ l10) * 0x9E3779B97F4A7C15L;
        h = (h ^ l11) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        return (int) (h ^ (h >>> 32));
    }

    /** Unsigned decimal representation. */
    @Override
    public String toString() {
        return toBigInteger().toString();
    }

    /** Unsigned hexadecimal representation without prefix ({@code "0"} for zero). */
    public String toHexString() {
        return toBigInteger().toString(16);
    }

    private long[] pack() {
        return new long[] { l0, l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11 };
    }

    private static Wide768 unpack(long[] v) {
        return new Wide768(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11]);
    }

    private static void checkShift(int k) {
        if (k < 0 || k >= BITS) {
            throw new IllegalArgumentException("shift amount out of range: " + k);
        }
    }
}
