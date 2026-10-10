package com.scalarwide;

import java.math.BigInteger;

/**
 * Fixed-width 128-bit integer held in two little-endian {@code long} limbs
 * ({@code l0} least significant, {@code l1} most significant); the value is
 * interpreted as unsigned by default, but the bit pattern is two's complement.
 *
 * <p>All arithmetic wraps modulo 2^128. {@link #compareTo} is unsigned and
 * consistent with {@link #equals} (suitable for map keys); use
 * {@link #compareSigned} for signed order. Shift amounts must lie in
 * {@code [0, 128)} or {@link IllegalArgumentException} is thrown.
 *
 * <p>Arithmetic is register-specialized (no intermediate arrays or loops);
 * the generic {@link Limbs} engine remains as the internal correctness oracle.
 */
public final class Wide128 implements Comparable<Wide128> {

    public static final int BITS = 128;
    public static final int LIMBS = 2;
    public static final Wide128 ZERO = new Wide128(0, 0);
    public static final Wide128 ONE = new Wide128(1, 0);
    public static final Wide128 MAX_VALUE = new Wide128(-1L, -1L);

    private final long l0;
    private final long l1;

    private Wide128(long l0, long l1) {
        this.l0 = l0;
        this.l1 = l1;
    }

    /** Zero-extends a {@code long} value. */
    public static Wide128 of(long v) {
        return new Wide128(v, 0);
    }

    /** Sign-extends a {@code long} value. */
    public static Wide128 ofSigned(long v) {
        return new Wide128(v, v >> 63);
    }

    public static Wide128 of(long l0, long l1) {
        return new Wide128(l0, l1);
    }

    /**
     * @throws IllegalArgumentException if {@code v} is negative or does not
     *         fit in 128 bits
     */
    public static Wide128 fromBigInteger(BigInteger v) {
        if (v.signum() < 0) {
            throw new IllegalArgumentException("negative value: " + v);
        }
        if (v.bitLength() > BITS) {
            throw new IllegalArgumentException("value does not fit in " + BITS + " bits");
        }
        return new Wide128(v.longValue(), v.shiftRight(64).longValue());
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
            default -> throw new IndexOutOfBoundsException("limb " + i);
        };
    }

    /** Low 64 bits. */
    public long longValue() {
        return l0;
    }

    public boolean isZero() {
        return (l0 | l1) == 0;
    }

    public int bitLength() {
        if (l1 != 0) {
            return 64 + (64 - Long.numberOfLeadingZeros(l1));
        }
        if (l0 != 0) {
            return 64 - Long.numberOfLeadingZeros(l0);
        }
        return 0;
    }

    /** Wrapping addition modulo 2^128. */
    public Wide128 add(Wide128 o) {
        long r0 = l0 + o.l0;
        long c = Carry.carryOut(l0, r0);
        long r1 = l1 + o.l1 + c;
        return new Wide128(r0, r1);
    }

    /** Wrapping subtraction modulo 2^128. */
    public Wide128 sub(Wide128 o) {
        long r0 = l0 - o.l0;
        long b = Carry.borrowOut(l0, r0);
        long r1 = l1 - o.l1 - b;
        return new Wide128(r0, r1);
    }

    /** Wrapping negation modulo 2^128. */
    public Wide128 neg() {
        return ZERO.sub(this);
    }

    /** Low 128 bits of the 256-bit product, i.e. {@code (a*b) mod 2^128}. */
    public Wide128 mul(Wide128 o) {
        long lo = l0 * o.l0;
        long mid = Math.unsignedMultiplyHigh(l0, o.l0) + l0 * o.l1 + l1 * o.l0;
        return new Wide128(lo, mid);
    }

    /** High 128 bits of the 256-bit product, i.e. {@code (a*b) >>> 128}. */
    public Wide128 mulHigh(Wide128 o) {
        long hi00 = Math.unsignedMultiplyHigh(l0, o.l0);
        long lo01 = l0 * o.l1;
        long lo10 = l1 * o.l0;
        long t = hi00 + lo01;
        long k = Carry.addCarryOut(hi00, lo01, t);
        long limb1 = t + lo10;
        k += Carry.addCarryOut(t, lo10, limb1);

        long hi01 = Math.unsignedMultiplyHigh(l0, o.l1);
        long hi10 = Math.unsignedMultiplyHigh(l1, o.l0);
        long lo11 = l1 * o.l1;
        long hi11 = Math.unsignedMultiplyHigh(l1, o.l1);
        long t2 = hi01 + hi10;
        long k2 = Carry.addCarryOut(hi01, hi10, t2);
        long limb2 = t2 + lo11;
        k2 += Carry.addCarryOut(t2, lo11, limb2);
        long limb2b = limb2 + k;
        k2 += Carry.addCarryOut(limb2, k, limb2b);
        long limb3 = hi11 + k2;
        return new Wide128(limb2b, limb3);
    }

    /**
     * Full 256-bit product of the two unsigned operands.
     *
     * @return the product as a {@link Wide256}; limbs {@code 0..1} are the low
     *         half (equal to {@link #mul}), limbs {@code 2..3} the high half
     *         (equal to {@link #mulHigh})
     */
    public Wide256 mulFull(Wide128 o) {
        long limb0 = l0 * o.l0;
        long hi00 = Math.unsignedMultiplyHigh(l0, o.l0);
        long lo01 = l0 * o.l1;
        long lo10 = l1 * o.l0;
        long t = hi00 + lo01;
        long k = Carry.addCarryOut(hi00, lo01, t);
        long limb1 = t + lo10;
        k += Carry.addCarryOut(t, lo10, limb1);

        long hi01 = Math.unsignedMultiplyHigh(l0, o.l1);
        long hi10 = Math.unsignedMultiplyHigh(l1, o.l0);
        long lo11 = l1 * o.l1;
        long hi11 = Math.unsignedMultiplyHigh(l1, o.l1);
        long t2 = hi01 + hi10;
        long k2 = Carry.addCarryOut(hi01, hi10, t2);
        long limb2 = t2 + lo11;
        k2 += Carry.addCarryOut(t2, lo11, limb2);
        long limb2b = limb2 + k;
        k2 += Carry.addCarryOut(limb2, k, limb2b);
        long limb3 = hi11 + k2;
        return Wide256.of(limb0, limb1, limb2b, limb3);
    }

    public Wide128 and(Wide128 o) {
        return new Wide128(l0 & o.l0, l1 & o.l1);
    }

    public Wide128 or(Wide128 o) {
        return new Wide128(l0 | o.l0, l1 | o.l1);
    }

    public Wide128 xor(Wide128 o) {
        return new Wide128(l0 ^ o.l0, l1 ^ o.l1);
    }

    public Wide128 not() {
        return new Wide128(~l0, ~l1);
    }

    public Wide128 andNot(Wide128 o) {
        return new Wide128(l0 & ~o.l0, l1 & ~o.l1);
    }

    /**
     * Logical left shift by {@code k} bits; bits shifted out are discarded.
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 128)}
     */
    public Wide128 shl(int k) {
        checkShift(k);
        if (k == 0) {
            return this;
        }
        if (k < 64) {
            return new Wide128(l0 << k, (l1 << k) | (l0 >>> (64 - k)));
        }
        return new Wide128(0, l0 << (k - 64));
    }

    /**
     * Logical right shift by {@code k} bits (zero fill).
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 128)}
     */
    public Wide128 ushr(int k) {
        checkShift(k);
        if (k == 0) {
            return this;
        }
        if (k < 64) {
            return new Wide128((l0 >>> k) | (l1 << (64 - k)), l1 >>> k);
        }
        return new Wide128(l1 >>> (k - 64), 0);
    }

    /**
     * Arithmetic right shift by {@code k} bits (sign-extending).
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 128)}
     */
    public Wide128 shr(int k) {
        checkShift(k);
        if (k == 0) {
            return this;
        }
        if (k < 64) {
            return new Wide128((l0 >>> k) | (l1 << (64 - k)), l1 >> k);
        }
        return new Wide128(l1 >> (k - 64), l1 >> 63);
    }

    public int compareUnsigned(Wide128 o) {
        int c = Long.compareUnsigned(l1, o.l1);
        return c != 0 ? c : Long.compareUnsigned(l0, o.l0);
    }

    public int compareSigned(Wide128 o) {
        int c = Long.compare(l1, o.l1);
        return c != 0 ? c : Long.compareUnsigned(l0, o.l0);
    }

    /**
     * Unsigned comparison; consistent with {@link #equals}.
     *
     * @see #compareSigned(Wide128)
     */
    @Override
    public int compareTo(Wide128 o) {
        return compareUnsigned(o);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Wide128 o)) {
            return false;
        }
        return l0 == o.l0 && l1 == o.l1;
    }

    @Override
    public int hashCode() {
        long h = 0x243F6A8885A308D3L;
        h = (h ^ l0) * 0x9E3779B97F4A7C15L;
        h = (h ^ l1) * 0x9E3779B97F4A7C15L;
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
        return new long[] { l0, l1 };
    }

    Wide256 widen() {
        return Wide256.of(l0, l1, 0, 0);
    }

    private static void checkShift(int k) {
        if (k < 0 || k >= BITS) {
            throw new IllegalArgumentException("shift amount out of range: " + k);
        }
    }
}
