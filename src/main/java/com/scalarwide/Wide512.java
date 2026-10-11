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

    /**
     * Unsigned division: `this / o`, truncated.
     *
     * @throws ArithmeticException if `o` is zero
     */
    public Wide512 div(Wide512 o) {
        if (((l4 | l5 | l6 | l7) | (o.l4 | o.l5 | o.l6 | o.l7)) == 0) {
            Wide256 q = Wide256.of(l0, l1, l2, l3).div(Wide256.of(o.l0, o.l1, o.l2, o.l3));
            return of(q.limb(0), q.limb(1), q.limb(2), q.limb(3), 0, 0, 0, 0);
        }
        long[] q = new long[LIMBS];
        Limbs.divmod(pack(), o.pack(), q, null);
        return of(q[0], q[1], q[2], q[3],
                q[4], q[5], q[6], q[7]);
    }

    /**
     * Unsigned remainder: `this % o`.
     *
     * @throws ArithmeticException if `o` is zero
     */
    public Wide512 rem(Wide512 o) {
        if (((l4 | l5 | l6 | l7) | (o.l4 | o.l5 | o.l6 | o.l7)) == 0) {
            Wide256 r = Wide256.of(l0, l1, l2, l3).rem(Wide256.of(o.l0, o.l1, o.l2, o.l3));
            return of(r.limb(0), r.limb(1), r.limb(2), r.limb(3), 0, 0, 0, 0);
        }
        long[] q = new long[LIMBS];
        long[] r = new long[LIMBS];
        Limbs.divmod(pack(), o.pack(), q, r);
        return of(r[0], r[1], r[2], r[3],
                r[4], r[5], r[6], r[7]);
    }

    /**
     * Multiplicative power modulo 2^BITS (square-and-multiply).
     *
     * @throws IllegalArgumentException if `exp` is negative
     */
    public Wide512 pow(int exp) {
        if (exp < 0) {
            throw new IllegalArgumentException("negative exponent: " + exp);
        }
        Wide512 result = ONE;
        Wide512 base = this;
        while (exp > 0) {
            if ((exp & 1) == 1) {
                result = result.mul(base);
            }
            base = base.mul(base);
            exp >>= 1;
        }
        return result;
    }

    /** Population count over all BITS bits. */
    public int bitCount() {
        return Long.bitCount(l0) + Long.bitCount(l1)
                + Long.bitCount(l2) + Long.bitCount(l3)
                + Long.bitCount(l4) + Long.bitCount(l5)
                + Long.bitCount(l6) + Long.bitCount(l7);
    }

    /** Unsigned minimum (consistent with {@link #compareUnsigned}). */
    public Wide512 min(Wide512 o) {
        return compareUnsigned(o) <= 0 ? this : o;
    }

    /** Unsigned maximum (consistent with {@link #compareUnsigned}). */
    public Wide512 max(Wide512 o) {
        return compareUnsigned(o) >= 0 ? this : o;
    }

    /** Two's-complement absolute value; MIN_VALUE wraps to itself. */
    public Wide512 abs() {
        return l7 < 0 ? neg() : this;
    }
    /** Unsigned big-endian minimal byte array (zero returns a single 0x00 byte). */
    public byte[] toByteArray() {
        int bits = bitLength();
        if (bits == 0) {
            return new byte[] {0};
        }
        byte[] out = new byte[(bits + 7) >>> 3];
        int pos = out.length;
        long x = l0;
        for (int k = 0; k < 8 && pos > 0; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l1;
        for (int k = 0; k < 8 && pos > 0; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l2;
        for (int k = 0; k < 8 && pos > 0; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l3;
        for (int k = 0; k < 8 && pos > 0; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l4;
        for (int k = 0; k < 8 && pos > 0; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l5;
        for (int k = 0; k < 8 && pos > 0; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l6;
        for (int k = 0; k < 8 && pos > 0; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l7;
        for (int k = 0; k < 8 && pos > 0; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        return out;
    }

    /** Unsigned little-endian minimal byte array (zero returns a single 0x00 byte). */
    public byte[] toByteArrayLE() {
        int bits = bitLength();
        if (bits == 0) {
            return new byte[] {0};
        }
        int n = (bits + 7) >>> 3;
        byte[] out = new byte[n];
        int pos = 0;
        long x = l0;
        for (int k = 0; k < 8 && pos < n; k++) {
            out[pos++] = (byte) x;
            x >>>= 8;
        }
        x = l1;
        for (int k = 0; k < 8 && pos < n; k++) {
            out[pos++] = (byte) x;
            x >>>= 8;
        }
        x = l2;
        for (int k = 0; k < 8 && pos < n; k++) {
            out[pos++] = (byte) x;
            x >>>= 8;
        }
        x = l3;
        for (int k = 0; k < 8 && pos < n; k++) {
            out[pos++] = (byte) x;
            x >>>= 8;
        }
        x = l4;
        for (int k = 0; k < 8 && pos < n; k++) {
            out[pos++] = (byte) x;
            x >>>= 8;
        }
        x = l5;
        for (int k = 0; k < 8 && pos < n; k++) {
            out[pos++] = (byte) x;
            x >>>= 8;
        }
        x = l6;
        for (int k = 0; k < 8 && pos < n; k++) {
            out[pos++] = (byte) x;
            x >>>= 8;
        }
        x = l7;
        for (int k = 0; k < 8 && pos < n; k++) {
            out[pos++] = (byte) x;
            x >>>= 8;
        }
        return out;
    }

    /** Unsigned big-endian fixed-width byte array (BITS/8 bytes, zero-padded). */
    public byte[] toByteArrayFixed() {
        byte[] out = new byte[BITS >>> 3];
        int pos = out.length;
        long x = l0;
        for (int k = 0; k < 8; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l1;
        for (int k = 0; k < 8; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l2;
        for (int k = 0; k < 8; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l3;
        for (int k = 0; k < 8; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l4;
        for (int k = 0; k < 8; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l5;
        for (int k = 0; k < 8; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l6;
        for (int k = 0; k < 8; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l7;
        for (int k = 0; k < 8; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        return out;
    }

    /**
     * Unsigned big-endian decode; leading zero padding is tolerated (so
     * BigInteger.toByteArray output with a sign byte round-trips).
     *
     * @throws IllegalArgumentException if the significant length exceeds BITS/8
     */
    public static Wide512 fromByteArray(byte[] src, int off, int len) {
        int max = LIMBS * 8;
        while (len > max && src[off] == 0) {
            off++;
            len--;
        }
        if (len > max) {
            throw new IllegalArgumentException("byte array too long: " + len);
        }
        long[] out = new long[LIMBS];
        for (int i = 0; i < len; i++) {
            int b = (len - 1 - i) << 3;
            out[b >>> 6] |= (src[off + i] & 0xFFL) << (b & 63);
        }
        return of(out[0], out[1], out[2], out[3], out[4], out[5], out[6], out[7]);
    }

    /** @see #fromByteArray(byte[], int, int) */
    public static Wide512 fromByteArray(byte[] src) {
        return fromByteArray(src, 0, src.length);
    }

    /** Unsigned little-endian decode. */
    public static Wide512 fromByteArrayLE(byte[] src, int off, int len) {
        int max = LIMBS * 8;
        if (len > max) {
            throw new IllegalArgumentException("byte array too long: " + len);
        }
        long[] out = new long[LIMBS];
        for (int i = 0; i < len; i++) {
            int b = i << 3;
            out[b >>> 6] |= (src[off + i] & 0xFFL) << (b & 63);
        }
        return of(out[0], out[1], out[2], out[3], out[4], out[5], out[6], out[7]);
    }

    /** @see #fromByteArrayLE(byte[], int, int) */
    public static Wide512 fromByteArrayLE(byte[] src) {
        return fromByteArrayLE(src, 0, src.length);
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
        long r0 = l0 - o.l0;
        long b = Carry.borrowOut(l0, r0);
        long d1 = l1 - o.l1;
        long r1 = d1 - b;
        b = Carry.borrowOut(l1, d1) + Carry.borrowOut(d1, r1);
        long d2 = l2 - o.l2;
        long r2 = d2 - b;
        b = Carry.borrowOut(l2, d2) + Carry.borrowOut(d2, r2);
        long d3 = l3 - o.l3;
        long r3 = d3 - b;
        b = Carry.borrowOut(l3, d3) + Carry.borrowOut(d3, r3);
        long d4 = l4 - o.l4;
        long r4 = d4 - b;
        b = Carry.borrowOut(l4, d4) + Carry.borrowOut(d4, r4);
        long d5 = l5 - o.l5;
        long r5 = d5 - b;
        b = Carry.borrowOut(l5, d5) + Carry.borrowOut(d5, r5);
        long d6 = l6 - o.l6;
        long r6 = d6 - b;
        b = Carry.borrowOut(l6, d6) + Carry.borrowOut(d6, r6);
        long d7 = l7 - o.l7;
        long r7 = d7 - b;
        return new Wide512(r0, r1, r2, r3, r4, r5, r6, r7);
    }

    /** Wrapping negation modulo 2^512. */
    public Wide512 neg() {
        return ZERO.sub(this);
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
        if (((l4 | l5 | l6 | l7) | (o.l4 | o.l5 | o.l6 | o.l7)) == 0) {
            return ZERO;
        }
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
        return new Wide512(l0 & o.l0, l1 & o.l1, l2 & o.l2, l3 & o.l3, l4 & o.l4, l5 & o.l5, l6 & o.l6,
                l7 & o.l7);
    }

    public Wide512 or(Wide512 o) {
        return new Wide512(l0 | o.l0, l1 | o.l1, l2 | o.l2, l3 | o.l3, l4 | o.l4, l5 | o.l5, l6 | o.l6,
                l7 | o.l7);
    }

    public Wide512 xor(Wide512 o) {
        return new Wide512(l0 ^ o.l0, l1 ^ o.l1, l2 ^ o.l2, l3 ^ o.l3, l4 ^ o.l4, l5 ^ o.l5, l6 ^ o.l6,
                l7 ^ o.l7);
    }

    public Wide512 not() {
        return new Wide512(~l0, ~l1, ~l2, ~l3, ~l4, ~l5, ~l6, ~l7);
    }

    public Wide512 andNot(Wide512 o) {
        return new Wide512(l0 & ~o.l0, l1 & ~o.l1, l2 & ~o.l2, l3 & ~o.l3, l4 & ~o.l4, l5 & ~o.l5,
                l6 & ~o.l6, l7 & ~o.l7);
    }

    /**
     * Logical left shift by {@code k} bits; bits shifted out are discarded.
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 512)}
     */
    public Wide512 shl(int k) {
        checkShift(k);
        if (k == 0) {
            return this;
        }
        int bs = k & 63;
        long b0;
        long b1;
        long b2;
        long b3;
        long b4;
        long b5;
        long b6;
        long b7;
        if (bs == 0) {
            b0 = l0;
            b1 = l1;
            b2 = l2;
            b3 = l3;
            b4 = l4;
            b5 = l5;
            b6 = l6;
            b7 = l7;
        } else {
            long inv = 64 - bs;
            b0 = l0 << bs;
            b1 = (l1 << bs) | (l0 >>> inv);
            b2 = (l2 << bs) | (l1 >>> inv);
            b3 = (l3 << bs) | (l2 >>> inv);
            b4 = (l4 << bs) | (l3 >>> inv);
            b5 = (l5 << bs) | (l4 >>> inv);
            b6 = (l6 << bs) | (l5 >>> inv);
            b7 = (l7 << bs) | (l6 >>> inv);
        }
        return switch (k >>> 6) {
            case 0 -> new Wide512(b0, b1, b2, b3, b4, b5, b6, b7);
            case 1 -> new Wide512(0, b0, b1, b2, b3, b4, b5, b6);
            case 2 -> new Wide512(0, 0, b0, b1, b2, b3, b4, b5);
            case 3 -> new Wide512(0, 0, 0, b0, b1, b2, b3, b4);
            case 4 -> new Wide512(0, 0, 0, 0, b0, b1, b2, b3);
            case 5 -> new Wide512(0, 0, 0, 0, 0, b0, b1, b2);
            case 6 -> new Wide512(0, 0, 0, 0, 0, 0, b0, b1);
            default -> new Wide512(0, 0, 0, 0, 0, 0, 0, b0);
        };
    }

    /**
     * Logical right shift by {@code k} bits (zero fill).
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 512)}
     */
    public Wide512 ushr(int k) {
        checkShift(k);
        if (k == 0) {
            return this;
        }
        int bs = k & 63;
        long b0;
        long b1;
        long b2;
        long b3;
        long b4;
        long b5;
        long b6;
        long b7;
        if (bs == 0) {
            b0 = l0;
            b1 = l1;
            b2 = l2;
            b3 = l3;
            b4 = l4;
            b5 = l5;
            b6 = l6;
            b7 = l7;
        } else {
            long inv = 64 - bs;
            b0 = (l0 >>> bs) | (l1 << inv);
            b1 = (l1 >>> bs) | (l2 << inv);
            b2 = (l2 >>> bs) | (l3 << inv);
            b3 = (l3 >>> bs) | (l4 << inv);
            b4 = (l4 >>> bs) | (l5 << inv);
            b5 = (l5 >>> bs) | (l6 << inv);
            b6 = (l6 >>> bs) | (l7 << inv);
            b7 = l7 >>> bs;
        }
        return switch (k >>> 6) {
            case 0 -> new Wide512(b0, b1, b2, b3, b4, b5, b6, b7);
            case 1 -> new Wide512(b1, b2, b3, b4, b5, b6, b7, 0);
            case 2 -> new Wide512(b2, b3, b4, b5, b6, b7, 0, 0);
            case 3 -> new Wide512(b3, b4, b5, b6, b7, 0, 0, 0);
            case 4 -> new Wide512(b4, b5, b6, b7, 0, 0, 0, 0);
            case 5 -> new Wide512(b5, b6, b7, 0, 0, 0, 0, 0);
            case 6 -> new Wide512(b6, b7, 0, 0, 0, 0, 0, 0);
            default -> new Wide512(b7, 0, 0, 0, 0, 0, 0, 0);
        };
    }

    /**
     * Arithmetic right shift by {@code k} bits (sign-extending).
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 512)}
     */
    public Wide512 shr(int k) {
        checkShift(k);
        if (k == 0) {
            return this;
        }
        Wide512 u = ushr(k);
        if (l7 >> 63 == 0) {
            return u;
        }
        return u.or(MAX_VALUE.shl(512 - k));
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
