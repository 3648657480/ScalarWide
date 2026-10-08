package com.scalarwide;

import java.math.BigInteger;

/**
 * Fixed-width 1024-bit integer held in sixteen little-endian {@code long} limbs
 * ({@code l0} least significant .. {@code l15} most significant); the value is
 * interpreted as unsigned by default, but the bit pattern is two's complement.
 *
 * <p>All arithmetic wraps modulo 2^1024. {@link #compareTo} is unsigned and
 * consistent with {@link #equals} (suitable for map keys); use
 * {@link #compareSigned} for signed order. Shift amounts must lie in
 * {@code [0, 1024)} or {@link IllegalArgumentException} is thrown.
 *
 * <p>Current implementation delegates to the generic {@link Limbs} engine
 * (correctness first); composition from the 256-bit atom (4x256) is planned.
 */
public final class Wide1024 implements Comparable<Wide1024> {

    public static final int BITS = 1024;
    public static final int LIMBS = 16;
    public static final Wide1024 ZERO = new Wide1024(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    public static final Wide1024 ONE = new Wide1024(
            1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    public static final Wide1024 MAX_VALUE = new Wide1024(
            -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L,
            -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L);

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
    private final long l12;
    private final long l13;
    private final long l14;
    private final long l15;

    private Wide1024(long l0, long l1, long l2, long l3, long l4, long l5, long l6, long l7,
                     long l8, long l9, long l10, long l11, long l12, long l13, long l14, long l15) {
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
        this.l12 = l12;
        this.l13 = l13;
        this.l14 = l14;
        this.l15 = l15;
    }

    /** Zero-extends a {@code long} value. */
    public static Wide1024 of(long v) {
        return new Wide1024(v, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /** Sign-extends a {@code long} value. */
    public static Wide1024 ofSigned(long v) {
        long fill = v >> 63;
        return new Wide1024(v, fill, fill, fill, fill, fill, fill, fill,
                fill, fill, fill, fill, fill, fill, fill, fill);
    }

    public static Wide1024 of(long l0, long l1, long l2, long l3, long l4, long l5, long l6, long l7,
                              long l8, long l9, long l10, long l11, long l12, long l13, long l14, long l15) {
        return new Wide1024(l0, l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11, l12, l13, l14, l15);
    }

    /**
     * @throws IllegalArgumentException if {@code v} is negative or does not
     *         fit in 1024 bits
     */
    public static Wide1024 fromBigInteger(BigInteger v) {
        if (v.signum() < 0) {
            throw new IllegalArgumentException("negative value: " + v);
        }
        if (v.bitLength() > BITS) {
            throw new IllegalArgumentException("value does not fit in " + BITS + " bits");
        }
        return new Wide1024(
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
                v.shiftRight(704).longValue(),
                v.shiftRight(768).longValue(),
                v.shiftRight(832).longValue(),
                v.shiftRight(896).longValue(),
                v.shiftRight(960).longValue());
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
            case 12 -> l12;
            case 13 -> l13;
            case 14 -> l14;
            case 15 -> l15;
            default -> throw new IndexOutOfBoundsException("limb " + i);
        };
    }

    /** Low 64 bits. */
    public long longValue() {
        return l0;
    }

    public boolean isZero() {
        return (l0 | l1 | l2 | l3 | l4 | l5 | l6 | l7 | l8 | l9 | l10 | l11 | l12 | l13 | l14 | l15) == 0;
    }

    public int bitLength() {
        if (l15 != 0) {
            return 960 + (64 - Long.numberOfLeadingZeros(l15));
        }
        if (l14 != 0) {
            return 896 + (64 - Long.numberOfLeadingZeros(l14));
        }
        if (l13 != 0) {
            return 832 + (64 - Long.numberOfLeadingZeros(l13));
        }
        if (l12 != 0) {
            return 768 + (64 - Long.numberOfLeadingZeros(l12));
        }
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

    /** Wrapping addition modulo 2^1024. */
    public Wide1024 add(Wide1024 o) {
        long[] out = new long[LIMBS];
        Limbs.add(pack(), o.pack(), out);
        return unpack(out);
    }

    /** Wrapping subtraction modulo 2^1024. */
    public Wide1024 sub(Wide1024 o) {
        long[] out = new long[LIMBS];
        Limbs.sub(pack(), o.pack(), out);
        return unpack(out);
    }

    /** Wrapping negation modulo 2^1024. */
    public Wide1024 neg() {
        long[] out = new long[LIMBS];
        Limbs.neg(pack(), out);
        return unpack(out);
    }

    /**
     * Low 1024 bits of the 2048-bit product, i.e. {@code (a*b) mod 2^1024},
     * composed from 256-bit atom block products (six full, four low-block-only).
     */
    public Wide1024 mul(Wide1024 o) {
        Wide512 q00 = block0().mulFull(o.block0());
        Wide512 q01 = block0().mulFull(o.block1());
        Wide512 q10 = block1().mulFull(o.block0());
        Wide512 q02 = block0().mulFull(o.block2());
        Wide512 q11 = block1().mulFull(o.block1());
        Wide512 q20 = block2().mulFull(o.block0());
        Wide256 q03l = block0().mul(o.block3());
        Wide256 q12l = block1().mul(o.block2());
        Wide256 q21l = block2().mul(o.block1());
        Wide256 q30l = block3().mul(o.block0());

        Wide256 s1 = q00.highHalf().add(q01.lowHalf());
        long w1 = s1.compareUnsigned(q00.highHalf()) < 0 ? 1 : 0;
        Wide256 b1v = s1.add(q10.lowHalf());
        long w2 = b1v.compareUnsigned(s1) < 0 ? 1 : 0;
        long carry1 = w1 + w2;

        Wide256 t1 = q01.highHalf().add(q10.highHalf());
        long c1 = t1.compareUnsigned(q01.highHalf()) < 0 ? 1 : 0;
        Wide256 t2 = t1.add(q02.lowHalf());
        long c2 = t2.compareUnsigned(t1) < 0 ? 1 : 0;
        Wide256 t3 = t2.add(q11.lowHalf());
        long c3 = t3.compareUnsigned(t2) < 0 ? 1 : 0;
        Wide256 t4 = t3.add(q20.lowHalf());
        long c4 = t4.compareUnsigned(t3) < 0 ? 1 : 0;
        Wide256 t5 = t4.add(Wide256.of(carry1, 0, 0, 0));
        long c5 = t5.compareUnsigned(t4) < 0 ? 1 : 0;
        long carry2 = c1 + c2 + c3 + c4 + c5;

        Wide256 u1 = q02.highHalf().add(q11.highHalf());
        Wide256 u2 = u1.add(q20.highHalf());
        Wide256 u3 = u2.add(q03l);
        Wide256 u4 = u3.add(q12l);
        Wide256 u5 = u4.add(q21l);
        Wide256 u6 = u5.add(q30l);
        Wide256 b3v = u6.add(Wide256.of(carry2, 0, 0, 0));

        Wide256 b0v = q00.lowHalf();
        return Wide1024.of(
                b0v.limb(0), b0v.limb(1), b0v.limb(2), b0v.limb(3),
                b1v.limb(0), b1v.limb(1), b1v.limb(2), b1v.limb(3),
                t5.limb(0), t5.limb(1), t5.limb(2), t5.limb(3),
                b3v.limb(0), b3v.limb(1), b3v.limb(2), b3v.limb(3));
    }

    private Wide256 block0() {
        return Wide256.of(l0, l1, l2, l3);
    }

    private Wide256 block1() {
        return Wide256.of(l4, l5, l6, l7);
    }

    private Wide256 block2() {
        return Wide256.of(l8, l9, l10, l11);
    }

    private Wide256 block3() {
        return Wide256.of(l12, l13, l14, l15);
    }

    /** High 1024 bits of the 2048-bit product, i.e. {@code (a*b) >>> 1024}. */
    public Wide1024 mulHigh(Wide1024 o) {
        long[] out = new long[LIMBS];
        Limbs.mulHigh(pack(), o.pack(), out);
        return unpack(out);
    }

    public Wide1024 and(Wide1024 o) {
        long[] out = new long[LIMBS];
        Limbs.and(pack(), o.pack(), out);
        return unpack(out);
    }

    public Wide1024 or(Wide1024 o) {
        long[] out = new long[LIMBS];
        Limbs.or(pack(), o.pack(), out);
        return unpack(out);
    }

    public Wide1024 xor(Wide1024 o) {
        long[] out = new long[LIMBS];
        Limbs.xor(pack(), o.pack(), out);
        return unpack(out);
    }

    public Wide1024 not() {
        long[] out = new long[LIMBS];
        Limbs.not(pack(), out);
        return unpack(out);
    }

    public Wide1024 andNot(Wide1024 o) {
        long[] out = new long[LIMBS];
        Limbs.andNot(pack(), o.pack(), out);
        return unpack(out);
    }

    /**
     * Logical left shift by {@code k} bits; bits shifted out are discarded.
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 1024)}
     */
    public Wide1024 shl(int k) {
        checkShift(k);
        long[] out = new long[LIMBS];
        Limbs.shl(pack(), k, out);
        return unpack(out);
    }

    /**
     * Logical right shift by {@code k} bits (zero fill).
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 1024)}
     */
    public Wide1024 ushr(int k) {
        checkShift(k);
        long[] out = new long[LIMBS];
        Limbs.ushr(pack(), k, out);
        return unpack(out);
    }

    /**
     * Arithmetic right shift by {@code k} bits (sign-extending).
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 1024)}
     */
    public Wide1024 shr(int k) {
        checkShift(k);
        long[] out = new long[LIMBS];
        Limbs.shr(pack(), k, out);
        return unpack(out);
    }

    public int compareUnsigned(Wide1024 o) {
        return Limbs.compareUnsigned(pack(), o.pack());
    }

    public int compareSigned(Wide1024 o) {
        return Limbs.compareSigned(pack(), o.pack());
    }

    /**
     * Unsigned comparison; consistent with {@link #equals}.
     *
     * @see #compareSigned(Wide1024)
     */
    @Override
    public int compareTo(Wide1024 o) {
        return compareUnsigned(o);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Wide1024 o)) {
            return false;
        }
        return l0 == o.l0 && l1 == o.l1 && l2 == o.l2 && l3 == o.l3
                && l4 == o.l4 && l5 == o.l5 && l6 == o.l6 && l7 == o.l7
                && l8 == o.l8 && l9 == o.l9 && l10 == o.l10 && l11 == o.l11
                && l12 == o.l12 && l13 == o.l13 && l14 == o.l14 && l15 == o.l15;
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
        h = (h ^ l12) * 0x9E3779B97F4A7C15L;
        h = (h ^ l13) * 0x9E3779B97F4A7C15L;
        h = (h ^ l14) * 0x9E3779B97F4A7C15L;
        h = (h ^ l15) * 0x9E3779B97F4A7C15L;
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
        return new long[] {
                l0, l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11, l12, l13, l14, l15
        };
    }

    private static Wide1024 unpack(long[] v) {
        return new Wide1024(
                v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7],
                v[8], v[9], v[10], v[11], v[12], v[13], v[14], v[15]);
    }

    private static void checkShift(int k) {
        if (k < 0 || k >= BITS) {
            throw new IllegalArgumentException("shift amount out of range: " + k);
        }
    }
}
