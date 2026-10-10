package com.scalarwide;

import java.math.BigInteger;

/**
 * Fixed-width 512-bit integer held in eight little-endian {@code long} limbs
 * ({@code l0} least significant .. {@code l7} most significant); the value is
 * interpreted as unsigned by default, but the bit pattern is two's complement.
 *
 * <p>All arithmetic wraps modulo 2^512. {@link #compareTo} is unsigned and
 * consistent with {@link #equals} (suitable for map keys); use
 * {@link #compareSigned} for signed order. Shift amounts must lie in
 * {@code [0, 512)} or {@link IllegalArgumentException} is thrown.
 *
 * <p>Addition, comparison and multiplication are register-specialized (the
 * multiplication composes four 256-bit atom products); the remaining operations
 * still route through the generic {@link Limbs} engine.
 */
public final class Wide512 implements Comparable<Wide512> {

    public static final int BITS = 512;
    public static final int LIMBS = 8;
    public static final Wide512 ZERO = new Wide512(0, 0, 0, 0, 0, 0, 0, 0);
    public static final Wide512 ONE = new Wide512(1, 0, 0, 0, 0, 0, 0, 0);
    public static final Wide512 MAX_VALUE =
            new Wide512(-1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L);

    private final long l0;
    private final long l1;
    private final long l2;
    private final long l3;
    private final long l4;
    private final long l5;
    private final long l6;
    private final long l7;

    private Wide512(long l0, long l1, long l2, long l3, long l4, long l5, long l6, long l7) {
        this.l0 = l0;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
        this.l4 = l4;
        this.l5 = l5;
        this.l6 = l6;
        this.l7 = l7;
    }

    /** Zero-extends a {@code long} value. */
    public static Wide512 of(long v) {
        return new Wide512(v, 0, 0, 0, 0, 0, 0, 0);
    }

    /** Sign-extends a {@code long} value. */
    public static Wide512 ofSigned(long v) {
        long fill = v >> 63;
        return new Wide512(v, fill, fill, fill, fill, fill, fill, fill);
    }

    public static Wide512 of(long l0, long l1, long l2, long l3, long l4, long l5, long l6, long l7) {
        return new Wide512(l0, l1, l2, l3, l4, l5, l6, l7);
    }

    /**
     * @throws IllegalArgumentException if {@code v} is negative or does not
     *         fit in 512 bits
     */
    public static Wide512 fromBigInteger(BigInteger v) {
        if (v.signum() < 0) {
            throw new IllegalArgumentException("negative value: " + v);
        }
        if (v.bitLength() > BITS) {
            throw new IllegalArgumentException("value does not fit in " + BITS + " bits");
        }
        return new Wide512(
                v.longValue(),
                v.shiftRight(64).longValue(),
                v.shiftRight(128).longValue(),
                v.shiftRight(192).longValue(),
                v.shiftRight(256).longValue(),
                v.shiftRight(320).longValue(),
                v.shiftRight(384).longValue(),
                v.shiftRight(448).longValue());
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
            default -> throw new IndexOutOfBoundsException("limb " + i);
        };
    }

    /** Low 64 bits. */
    public long longValue() {
        return l0;
    }

    public boolean isZero() {
        return (l0 | l1 | l2 | l3 | l4 | l5 | l6 | l7) == 0;
    }

    public int bitLength() {
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

    /** Wrapping addition modulo 2^512. */
    public Wide512 add(Wide512 o) {
        long r0 = l0 + o.l0;
        long c = Carry.carryOut(l0, r0);
        long s1 = l1 + o.l1;
        long r1 = s1 + c;
        c = Carry.carryOut(l1, s1) + Carry.carryOut(s1, r1);
        long s2 = l2 + o.l2;
        long r2 = s2 + c;
        c = Carry.carryOut(l2, s2) + Carry.carryOut(s2, r2);
        long s3 = l3 + o.l3;
        long r3 = s3 + c;
        c = Carry.carryOut(l3, s3) + Carry.carryOut(s3, r3);
        long s4 = l4 + o.l4;
        long r4 = s4 + c;
        c = Carry.carryOut(l4, s4) + Carry.carryOut(s4, r4);
        long s5 = l5 + o.l5;
        long r5 = s5 + c;
        c = Carry.carryOut(l5, s5) + Carry.carryOut(s5, r5);
        long s6 = l6 + o.l6;
        long r6 = s6 + c;
        c = Carry.carryOut(l6, s6) + Carry.carryOut(s6, r6);
        long s7 = l7 + o.l7;
        long r7 = s7 + c;
        return new Wide512(r0, r1, r2, r3, r4, r5, r6, r7);
    }

    /** Wrapping subtraction modulo 2^512. */
    public Wide512 sub(Wide512 o) {
        long[] out = new long[LIMBS];
        Limbs.sub(pack(), o.pack(), out);
        return unpack(out);
    }

    /** Wrapping negation modulo 2^512. */
    public Wide512 neg() {
        long[] out = new long[LIMBS];
        Limbs.neg(pack(), out);
        return unpack(out);
    }

    /**
     * Low 512 bits of the 1024-bit product, i.e. {@code (a*b) mod 2^512},
     * composed from four 256-bit atom products.
     */
    public Wide512 mul(Wide512 o) {
        Wide512 lo = lowHalf().mulFull(o.lowHalf());
        Wide512 m1 = lowHalf().mulFull(o.highHalf());
        Wide512 m2 = highHalf().mulFull(o.lowHalf());
        return lo.add(m1.add(m2).shiftLeft256());
    }

    /**
     * High 512 bits of the 1024-bit product, i.e. {@code (a*b) >>> 512}.
     *
     * <p>Four quarter products written into a single scratch array (zero
     * intermediate objects), with compare-based carry fix-ups at limb level.
     */
    public Wide512 mulHigh(Wide512 o) {
        long[] s = new long[32];
        Wide256.mulFullInto8(s, 0, l0, l1, l2, l3, o.limb(0), o.limb(1), o.limb(2), o.limb(3));
        Wide256.mulFullInto8(s, 8, l0, l1, l2, l3, o.limb(4), o.limb(5), o.limb(6), o.limb(7));
        Wide256.mulFullInto8(s, 16, l4, l5, l6, l7, o.limb(0), o.limb(1), o.limb(2), o.limb(3));
        Wide256.mulFullInto8(s, 24, l4, l5, l6, l7, o.limb(4), o.limb(5), o.limb(6), o.limb(7));

        long carry = 0;
        for (int i = 0; i < 8; i++) {
            long u = s[8 + i] + s[16 + i];
            long k = Carry.carryOut(s[8 + i], u);
            long w = u + carry;
            k += Carry.carryOut(u, w);
            s[16 + i] = w;
            carry = k;
        }
        long c1 = carry;

        carry = 0;
        for (int i = 0; i < 4; i++) {
            long u = s[4 + i] + s[16 + i];
            long k = Carry.carryOut(s[4 + i], u);
            long w = u + carry;
            k += Carry.carryOut(u, w);
            carry = k;
        }
        long c2 = carry;

        carry = c2;
        for (int i = 0; i < 4; i++) {
            long u = s[24 + i] + s[20 + i];
            long k = Carry.carryOut(s[24 + i], u);
            long w = u + carry;
            k += Carry.carryOut(u, w);
            s[i] = w;
            carry = k;
        }
        long u4 = s[28] + c1;
        long k4 = Carry.carryOut(s[28], u4);
        long x4 = u4 + carry;
        k4 += Carry.carryOut(u4, x4);
        s[4] = x4;
        carry = k4;
        for (int i = 5; i < 8; i++) {
            long w = s[24 + i] + carry;
            carry = Carry.carryOut(s[24 + i], w);
            s[i] = w;
        }
        return Wide512.of(s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7]);
    }

    Wide256 lowHalf() {
        return Wide256.of(l0, l1, l2, l3);
    }

    Wide256 highHalf() {
        return Wide256.of(l4, l5, l6, l7);
    }

    private Wide512 shiftLeft256() {
        return new Wide512(0, 0, 0, 0, l0, l1, l2, l3);
    }

    private Wide512 shiftRight256() {
        return new Wide512(l4, l5, l6, l7, 0, 0, 0, 0);
    }

    public Wide512 and(Wide512 o) {
        long[] out = new long[LIMBS];
        Limbs.and(pack(), o.pack(), out);
        return unpack(out);
    }

    public Wide512 or(Wide512 o) {
        long[] out = new long[LIMBS];
        Limbs.or(pack(), o.pack(), out);
        return unpack(out);
    }

    public Wide512 xor(Wide512 o) {
        long[] out = new long[LIMBS];
        Limbs.xor(pack(), o.pack(), out);
        return unpack(out);
    }

    public Wide512 not() {
        long[] out = new long[LIMBS];
        Limbs.not(pack(), out);
        return unpack(out);
    }

    public Wide512 andNot(Wide512 o) {
        long[] out = new long[LIMBS];
        Limbs.andNot(pack(), o.pack(), out);
        return unpack(out);
    }

    /**
     * Logical left shift by {@code k} bits; bits shifted out are discarded.
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 512)}
     */
    public Wide512 shl(int k) {
        checkShift(k);
        long[] out = new long[LIMBS];
        Limbs.shl(pack(), k, out);
        return unpack(out);
    }

    /**
     * Logical right shift by {@code k} bits (zero fill).
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 512)}
     */
    public Wide512 ushr(int k) {
        checkShift(k);
        long[] out = new long[LIMBS];
        Limbs.ushr(pack(), k, out);
        return unpack(out);
    }

    /**
     * Arithmetic right shift by {@code k} bits (sign-extending).
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 512)}
     */
    public Wide512 shr(int k) {
        checkShift(k);
        long[] out = new long[LIMBS];
        Limbs.shr(pack(), k, out);
        return unpack(out);
    }

    public int compareUnsigned(Wide512 o) {
        int c = Long.compareUnsigned(l7, o.l7);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l6, o.l6);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l5, o.l5);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l4, o.l4);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l3, o.l3);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l2, o.l2);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l1, o.l1);
        return c != 0 ? c : Long.compareUnsigned(l0, o.l0);
    }

    public int compareSigned(Wide512 o) {
        int c = Long.compare(l7, o.l7);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l6, o.l6);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l5, o.l5);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l4, o.l4);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l3, o.l3);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l2, o.l2);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l1, o.l1);
        return c != 0 ? c : Long.compareUnsigned(l0, o.l0);
    }

    /**
     * Unsigned comparison; consistent with {@link #equals}.
     *
     * @see #compareSigned(Wide512)
     */
    @Override
    public int compareTo(Wide512 o) {
        return compareUnsigned(o);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Wide512 o)) {
            return false;
        }
        return l0 == o.l0 && l1 == o.l1 && l2 == o.l2 && l3 == o.l3
                && l4 == o.l4 && l5 == o.l5 && l6 == o.l6 && l7 == o.l7;
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
        return new long[] { l0, l1, l2, l3, l4, l5, l6, l7 };
    }

    private static Wide512 unpack(long[] v) {
        return new Wide512(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]);
    }

    private static void checkShift(int k) {
        if (k < 0 || k >= BITS) {
            throw new IllegalArgumentException("shift amount out of range: " + k);
        }
    }
}
