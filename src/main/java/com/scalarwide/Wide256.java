package com.scalarwide;

import java.math.BigInteger;

/**
 * Fixed-width 256-bit integer held in four little-endian {@code long} limbs
 * ({@code l0} least significant .. {@code l3} most significant); the value is
 * interpreted as unsigned by default, but the bit pattern is two's complement.
 *
 * <p>All arithmetic wraps modulo 2^256. {@link #compareTo} is unsigned and
 * consistent with {@link #equals} (suitable for map keys); use
 * {@link #compareSigned} for signed order. Shift amounts must lie in
 * {@code [0, 256)} or {@link IllegalArgumentException} is thrown.
 *
 * <p>Arithmetic is register-specialized (no intermediate arrays or loops).
 * Multiplication is structured as a small inlinable orchestrator plus
 * per-column chunks (each chunk well below the C2 inline size threshold), with
 * the running carry passed through a scalar-replaceable {@code MulAcc}. The
 * generic {@link Limbs} engine remains as the internal correctness oracle.
 */
public final class Wide256 implements Comparable<Wide256> {

    public static final int BITS = 256;
    public static final int LIMBS = 4;
    public static final Wide256 ZERO = new Wide256(0, 0, 0, 0);
    public static final Wide256 ONE = new Wide256(1, 0, 0, 0);
    public static final Wide256 MAX_VALUE = new Wide256(-1L, -1L, -1L, -1L);

    private final long l0;
    private final long l1;
    private final long l2;
    private final long l3;

    private Wide256(long l0, long l1, long l2, long l3) {
        this.l0 = l0;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
    }

    /** Zero-extends a {@code long} value. */
    public static Wide256 of(long v) {
        return new Wide256(v, 0, 0, 0);
    }

    /** Sign-extends a {@code long} value. */
    public static Wide256 ofSigned(long v) {
        long fill = v >> 63;
        return new Wide256(v, fill, fill, fill);
    }

    public static Wide256 of(long l0, long l1, long l2, long l3) {
        return new Wide256(l0, l1, l2, l3);
    }

    /**
     * @throws IllegalArgumentException if {@code v} is negative or does not
     *         fit in 256 bits
     */
    public static Wide256 fromBigInteger(BigInteger v) {
        if (v.signum() < 0) {
            throw new IllegalArgumentException("negative value: " + v);
        }
        if (v.bitLength() > BITS) {
            throw new IllegalArgumentException("value does not fit in " + BITS + " bits");
        }
        return new Wide256(
                v.longValue(),
                v.shiftRight(64).longValue(),
                v.shiftRight(128).longValue(),
                v.shiftRight(192).longValue());
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
            default -> throw new IndexOutOfBoundsException("limb " + i);
        };
    }

    /** Low 64 bits. */
    public long longValue() {
        return l0;
    }

    public boolean isZero() {
        return (l0 | l1 | l2 | l3) == 0;
    }

    public int bitLength() {
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

    /** Wrapping addition modulo 2^256. */
    public Wide256 add(Wide256 o) {
        long r0 = l0 + o.l0;
        long c = Carry.addCarryOut(l0, o.l0, r0);
        long s1 = l1 + o.l1;
        long r1 = s1 + c;
        c = Carry.addCarryOut(l1, o.l1, s1) + Carry.addCarryOut(s1, c, r1);
        long s2 = l2 + o.l2;
        long r2 = s2 + c;
        c = Carry.addCarryOut(l2, o.l2, s2) + Carry.addCarryOut(s2, c, r2);
        long r3 = l3 + o.l3 + c;
        return new Wide256(r0, r1, r2, r3);
    }

    /** Wrapping subtraction modulo 2^256. */
    public Wide256 sub(Wide256 o) {
        long r0 = l0 - o.l0;
        long b = Carry.subBorrowOut(l0, o.l0, r0);
        long d1 = l1 - o.l1;
        long r1 = d1 - b;
        b = Carry.subBorrowOut(l1, o.l1, d1) + Carry.subBorrowOut(d1, b, r1);
        long d2 = l2 - o.l2;
        long r2 = d2 - b;
        b = Carry.subBorrowOut(l2, o.l2, d2) + Carry.subBorrowOut(d2, b, r2);
        long r3 = l3 - o.l3 - b;
        return new Wide256(r0, r1, r2, r3);
    }

    /** Wrapping negation modulo 2^256. */
    public Wide256 neg() {
        return ZERO.sub(this);
    }

    /** Low 256 bits of the 512-bit product, i.e. {@code (a*b) mod 2^256}. */
    public Wide256 mul(Wide256 o) {
        MulAcc acc = new MulAcc();
        col0(acc, o);
        col1(acc, o);
        col2(acc, o);
        col3(acc, o);
        return new Wide256(acc.c0, acc.c1, acc.c2, acc.c3);
    }

    /** High 256 bits of the 512-bit product, i.e. {@code (a*b) >>> 256}. */
    public Wide256 mulHigh(Wide256 o) {
        MulAcc acc = new MulAcc();
        col0(acc, o);
        col1(acc, o);
        col2(acc, o);
        col3(acc, o);
        col4(acc, o);
        col5(acc, o);
        col6(acc, o);
        col7(acc, o);
        return new Wide256(acc.c4, acc.c5, acc.c6, acc.c7);
    }

    /**
     * Full 512-bit product of the two unsigned operands.
     *
     * @return the product as a {@link Wide512}; limbs {@code 0..3} are the low
     *         half (equal to {@link #mul}), limbs {@code 4..7} the high half
     *         (equal to {@link #mulHigh})
     */
    public Wide512 mulFull(Wide256 o) {
        MulAcc acc = new MulAcc();
        col0(acc, o);
        col1(acc, o);
        col2(acc, o);
        col3(acc, o);
        col4(acc, o);
        col5(acc, o);
        col6(acc, o);
        col7(acc, o);
        return Wide512.of(acc.c0, acc.c1, acc.c2, acc.c3, acc.c4, acc.c5, acc.c6, acc.c7);
    }

    private static final class MulAcc {
        long carry;
        long c0;
        long c1;
        long c2;
        long c3;
        long c4;
        long c5;
        long c6;
        long c7;
    }

    private void col0(MulAcc acc, Wide256 o) {
        acc.c0 = l0 * o.l0;
        acc.carry = 0;
    }

    private void col1(MulAcc acc, Wide256 o) {
        long a = Math.unsignedMultiplyHigh(l0, o.l0);
        long b = l0 * o.l1;
        long t = a + b;
        long k = Carry.addCarryOut(a, b, t);
        long c = l1 * o.l0;
        long r = t + c;
        k += Carry.addCarryOut(t, c, r);
        acc.c1 = r;
        acc.carry = k;
    }

    private void col2(MulAcc acc, Wide256 o) {
        long a = Math.unsignedMultiplyHigh(l0, o.l1);
        long b = Math.unsignedMultiplyHigh(l1, o.l0);
        long t = a + b;
        long k = Carry.addCarryOut(a, b, t);
        long c = l0 * o.l2;
        long t2 = t + c;
        k += Carry.addCarryOut(t, c, t2);
        long d = l1 * o.l1;
        long t3 = t2 + d;
        k += Carry.addCarryOut(t2, d, t3);
        long e = l2 * o.l0;
        long t4 = t3 + e;
        k += Carry.addCarryOut(t3, e, t4);
        long r = t4 + acc.carry;
        k += Carry.addCarryOut(t4, acc.carry, r);
        acc.c2 = r;
        acc.carry = k;
    }

    private void col3(MulAcc acc, Wide256 o) {
        long a = Math.unsignedMultiplyHigh(l0, o.l2);
        long b = Math.unsignedMultiplyHigh(l1, o.l1);
        long t = a + b;
        long k = Carry.addCarryOut(a, b, t);
        long c = Math.unsignedMultiplyHigh(l2, o.l0);
        long t2 = t + c;
        k += Carry.addCarryOut(t, c, t2);
        long d = l0 * o.l3;
        long t3 = t2 + d;
        k += Carry.addCarryOut(t2, d, t3);
        long e = l1 * o.l2;
        long t4 = t3 + e;
        k += Carry.addCarryOut(t3, e, t4);
        long f = l2 * o.l1;
        long t5 = t4 + f;
        k += Carry.addCarryOut(t4, f, t5);
        long g = l3 * o.l0;
        long t6 = t5 + g;
        k += Carry.addCarryOut(t5, g, t6);
        long r = t6 + acc.carry;
        k += Carry.addCarryOut(t6, acc.carry, r);
        acc.c3 = r;
        acc.carry = k;
    }

    private void col4(MulAcc acc, Wide256 o) {
        long a = Math.unsignedMultiplyHigh(l0, o.l3);
        long b = Math.unsignedMultiplyHigh(l1, o.l2);
        long t = a + b;
        long k = Carry.addCarryOut(a, b, t);
        long c = Math.unsignedMultiplyHigh(l2, o.l1);
        long t2 = t + c;
        k += Carry.addCarryOut(t, c, t2);
        long d = Math.unsignedMultiplyHigh(l3, o.l0);
        long t3 = t2 + d;
        k += Carry.addCarryOut(t2, d, t3);
        long e = l1 * o.l3;
        long t4 = t3 + e;
        k += Carry.addCarryOut(t3, e, t4);
        long f = l2 * o.l2;
        long t5 = t4 + f;
        k += Carry.addCarryOut(t4, f, t5);
        long g = l3 * o.l1;
        long t6 = t5 + g;
        k += Carry.addCarryOut(t5, g, t6);
        long r = t6 + acc.carry;
        k += Carry.addCarryOut(t6, acc.carry, r);
        acc.c4 = r;
        acc.carry = k;
    }

    private void col5(MulAcc acc, Wide256 o) {
        long a = Math.unsignedMultiplyHigh(l1, o.l3);
        long b = Math.unsignedMultiplyHigh(l2, o.l2);
        long t = a + b;
        long k = Carry.addCarryOut(a, b, t);
        long c = Math.unsignedMultiplyHigh(l3, o.l1);
        long t2 = t + c;
        k += Carry.addCarryOut(t, c, t2);
        long d = l2 * o.l3;
        long t3 = t2 + d;
        k += Carry.addCarryOut(t2, d, t3);
        long e = l3 * o.l2;
        long t4 = t3 + e;
        k += Carry.addCarryOut(t3, e, t4);
        long r = t4 + acc.carry;
        k += Carry.addCarryOut(t4, acc.carry, r);
        acc.c5 = r;
        acc.carry = k;
    }

    private void col6(MulAcc acc, Wide256 o) {
        long a = Math.unsignedMultiplyHigh(l2, o.l3);
        long b = Math.unsignedMultiplyHigh(l3, o.l2);
        long t = a + b;
        long k = Carry.addCarryOut(a, b, t);
        long c = l3 * o.l3;
        long t2 = t + c;
        k += Carry.addCarryOut(t, c, t2);
        long r = t2 + acc.carry;
        k += Carry.addCarryOut(t2, acc.carry, r);
        acc.c6 = r;
        acc.carry = k;
    }

    private void col7(MulAcc acc, Wide256 o) {
        acc.c7 = Math.unsignedMultiplyHigh(l3, o.l3) + acc.carry;
    }

    public Wide256 and(Wide256 o) {
        return new Wide256(l0 & o.l0, l1 & o.l1, l2 & o.l2, l3 & o.l3);
    }

    public Wide256 or(Wide256 o) {
        return new Wide256(l0 | o.l0, l1 | o.l1, l2 | o.l2, l3 | o.l3);
    }

    public Wide256 xor(Wide256 o) {
        return new Wide256(l0 ^ o.l0, l1 ^ o.l1, l2 ^ o.l2, l3 ^ o.l3);
    }

    public Wide256 not() {
        return new Wide256(~l0, ~l1, ~l2, ~l3);
    }

    public Wide256 andNot(Wide256 o) {
        return new Wide256(l0 & ~o.l0, l1 & ~o.l1, l2 & ~o.l2, l3 & ~o.l3);
    }

    /**
     * Logical left shift by {@code k} bits; bits shifted out are discarded.
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 256)}
     */
    public Wide256 shl(int k) {
        checkShift(k);
        if (k == 0) {
            return this;
        }
        int ls = k >>> 6;
        int bs = k & 63;
        if (bs == 0) {
            return switch (ls) {
                case 1 -> new Wide256(0, l0, l1, l2);
                case 2 -> new Wide256(0, 0, l0, l1);
                default -> new Wide256(0, 0, 0, l0);
            };
        }
        long inv = 64 - bs;
        return switch (ls) {
            case 0 -> new Wide256(
                    l0 << bs,
                    (l1 << bs) | (l0 >>> inv),
                    (l2 << bs) | (l1 >>> inv),
                    (l3 << bs) | (l2 >>> inv));
            case 1 -> new Wide256(
                    0,
                    l0 << bs,
                    (l1 << bs) | (l0 >>> inv),
                    (l2 << bs) | (l1 >>> inv));
            case 2 -> new Wide256(
                    0,
                    0,
                    l0 << bs,
                    (l1 << bs) | (l0 >>> inv));
            default -> new Wide256(0, 0, 0, l0 << bs);
        };
    }

    /**
     * Logical right shift by {@code k} bits (zero fill).
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 256)}
     */
    public Wide256 ushr(int k) {
        checkShift(k);
        if (k == 0) {
            return this;
        }
        int ls = k >>> 6;
        int bs = k & 63;
        if (bs == 0) {
            return switch (ls) {
                case 1 -> new Wide256(l1, l2, l3, 0);
                case 2 -> new Wide256(l2, l3, 0, 0);
                default -> new Wide256(l3, 0, 0, 0);
            };
        }
        long inv = 64 - bs;
        return switch (ls) {
            case 0 -> new Wide256(
                    (l0 >>> bs) | (l1 << inv),
                    (l1 >>> bs) | (l2 << inv),
                    (l2 >>> bs) | (l3 << inv),
                    l3 >>> bs);
            case 1 -> new Wide256(
                    (l1 >>> bs) | (l2 << inv),
                    (l2 >>> bs) | (l3 << inv),
                    l3 >>> bs,
                    0);
            case 2 -> new Wide256(
                    (l2 >>> bs) | (l3 << inv),
                    l3 >>> bs,
                    0,
                    0);
            default -> new Wide256(l3 >>> bs, 0, 0, 0);
        };
    }

    /**
     * Arithmetic right shift by {@code k} bits (sign-extending).
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 256)}
     */
    public Wide256 shr(int k) {
        checkShift(k);
        if (k == 0) {
            return this;
        }
        int ls = k >>> 6;
        int bs = k & 63;
        long fill = l3 >> 63;
        if (bs == 0) {
            return switch (ls) {
                case 1 -> new Wide256(l1, l2, l3, fill);
                case 2 -> new Wide256(l2, l3, fill, fill);
                default -> new Wide256(l3, fill, fill, fill);
            };
        }
        long inv = 64 - bs;
        return switch (ls) {
            case 0 -> new Wide256(
                    (l0 >>> bs) | (l1 << inv),
                    (l1 >>> bs) | (l2 << inv),
                    (l2 >>> bs) | (l3 << inv),
                    (l3 >>> bs) | (fill << inv));
            case 1 -> new Wide256(
                    (l1 >>> bs) | (l2 << inv),
                    (l2 >>> bs) | (l3 << inv),
                    (l3 >>> bs) | (fill << inv),
                    fill);
            case 2 -> new Wide256(
                    (l2 >>> bs) | (l3 << inv),
                    (l3 >>> bs) | (fill << inv),
                    fill,
                    fill);
            default -> new Wide256((l3 >>> bs) | (fill << inv), fill, fill, fill);
        };
    }

    public int compareUnsigned(Wide256 o) {
        int c = Long.compareUnsigned(l3, o.l3);
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

    public int compareSigned(Wide256 o) {
        int c = Long.compare(l3, o.l3);
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
     * @see #compareSigned(Wide256)
     */
    @Override
    public int compareTo(Wide256 o) {
        return compareUnsigned(o);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Wide256 o)) {
            return false;
        }
        return l0 == o.l0 && l1 == o.l1 && l2 == o.l2 && l3 == o.l3;
    }

    @Override
    public int hashCode() {
        long h = 0x243F6A8885A308D3L;
        h = (h ^ l0) * 0x9E3779B97F4A7C15L;
        h = (h ^ l1) * 0x9E3779B97F4A7C15L;
        h = (h ^ l2) * 0x9E3779B97F4A7C15L;
        h = (h ^ l3) * 0x9E3779B97F4A7C15L;
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
        return new long[] { l0, l1, l2, l3 };
    }

    Wide512 widenLow() {
        return Wide512.of(l0, l1, l2, l3, 0, 0, 0, 0);
    }

    Wide128 low128() {
        return Wide128.of(l0, l1);
    }

    Wide128 high128() {
        return Wide128.of(l2, l3);
    }

    private static void checkShift(int k) {
        if (k < 0 || k >= BITS) {
            throw new IllegalArgumentException("shift amount out of range: " + k);
        }
    }
}
