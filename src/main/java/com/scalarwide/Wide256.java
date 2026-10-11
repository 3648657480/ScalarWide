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

    /**
     * Unsigned division: `this / o`, truncated.
     *
     * @throws ArithmeticException if `o` is zero
     */
    public Wide256 div(Wide256 o) {
        long[] q = new long[LIMBS];
        Limbs.divmod(pack(), o.pack(), q, null);
        return of(q[0], q[1], q[2], q[3]);
    }

    /**
     * Unsigned remainder: `this % o`.
     *
     * @throws ArithmeticException if `o` is zero
     */
    public Wide256 rem(Wide256 o) {
        long[] q = new long[LIMBS];
        long[] r = new long[LIMBS];
        Limbs.divmod(pack(), o.pack(), q, r);
        return of(r[0], r[1], r[2], r[3]);
    }

    /**
     * Multiplicative power modulo 2^BITS (square-and-multiply).
     *
     * @throws IllegalArgumentException if `exp` is negative
     */
    public Wide256 pow(int exp) {
        if (exp < 0) {
            throw new IllegalArgumentException("negative exponent: " + exp);
        }
        Wide256 result = ONE;
        Wide256 base = this;
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
                + Long.bitCount(l2) + Long.bitCount(l3);
    }

    /** Unsigned minimum (consistent with {@link #compareUnsigned}). */
    public Wide256 min(Wide256 o) {
        return compareUnsigned(o) <= 0 ? this : o;
    }

    /** Unsigned maximum (consistent with {@link #compareUnsigned}). */
    public Wide256 max(Wide256 o) {
        return compareUnsigned(o) >= 0 ? this : o;
    }

    /** Two's-complement absolute value; MIN_VALUE wraps to itself. */
    public Wide256 abs() {
        return l3 < 0 ? neg() : this;
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
        return out;
    }

    /**
     * Unsigned big-endian decode; leading zero padding is tolerated (so
     * BigInteger.toByteArray output with a sign byte round-trips).
     *
     * @throws IllegalArgumentException if the significant length exceeds BITS/8
     */
    public static Wide256 fromByteArray(byte[] src, int off, int len) {
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
        return of(out[0], out[1], out[2], out[3]);
    }

    /** @see #fromByteArray(byte[], int, int) */
    public static Wide256 fromByteArray(byte[] src) {
        return fromByteArray(src, 0, src.length);
    }

    /** Unsigned little-endian decode. */
    public static Wide256 fromByteArrayLE(byte[] src, int off, int len) {
        int max = LIMBS * 8;
        if (len > max) {
            throw new IllegalArgumentException("byte array too long: " + len);
        }
        long[] out = new long[LIMBS];
        for (int i = 0; i < len; i++) {
            int b = i << 3;
            out[b >>> 6] |= (src[off + i] & 0xFFL) << (b & 63);
        }
        return of(out[0], out[1], out[2], out[3]);
    }

    /** @see #fromByteArrayLE(byte[], int, int) */
    public static Wide256 fromByteArrayLE(byte[] src) {
        return fromByteArrayLE(src, 0, src.length);
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
        long c = Carry.carryOut(l0, r0);
        long s1 = l1 + o.l1;
        long r1 = s1 + c;
        c = Carry.carryOut(l1, s1) + Carry.carryOut(s1, r1);
        long s2 = l2 + o.l2;
        long r2 = s2 + c;
        c = Carry.carryOut(l2, s2) + Carry.carryOut(s2, r2);
        long r3 = l3 + o.l3 + c;
        return new Wide256(r0, r1, r2, r3);
    }

    /** Wrapping subtraction modulo 2^256. */
    public Wide256 sub(Wide256 o) {
        long r0 = l0 - o.l0;
        long b = Carry.borrowOut(l0, r0);
        long d1 = l1 - o.l1;
        long r1 = d1 - b;
        b = Carry.borrowOut(l1, d1) + Carry.borrowOut(d1, r1);
        long d2 = l2 - o.l2;
        long r2 = d2 - b;
        b = Carry.borrowOut(l2, d2) + Carry.borrowOut(d2, r2);
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

    // GEN-ATOM-BEGIN
    /** Full 512-bit product via the raw-limb entry (zero block temporaries). */
    public Wide512 mulFullDeferred(Wide256 o) {
        return mulFullDeferred8(l0, l1, l2, l3, o.limb(0), o.limb(1), o.limb(2), o.limb(3));
    }

    /**
     * Full 512-bit product from raw limbs (per-column 128-bit accumulators, deferred carries).
     */
    static Wide512 mulFullDeferred8(long a0, long a1, long a2, long a3, long b0, long b1, long b2, long b3) {
        long p0_0 = a0 * b0;
        long h0_0 = Math.unsignedMultiplyHigh(a0, b0);
        long p0_1 = a0 * b1;
        long h0_1 = Math.unsignedMultiplyHigh(a0, b1);
        long p0_2 = a0 * b2;
        long h0_2 = Math.unsignedMultiplyHigh(a0, b2);
        long p0_3 = a0 * b3;
        long h0_3 = Math.unsignedMultiplyHigh(a0, b3);
        long p1_0 = a1 * b0;
        long h1_0 = Math.unsignedMultiplyHigh(a1, b0);
        long p1_1 = a1 * b1;
        long h1_1 = Math.unsignedMultiplyHigh(a1, b1);
        long p1_2 = a1 * b2;
        long h1_2 = Math.unsignedMultiplyHigh(a1, b2);
        long p1_3 = a1 * b3;
        long h1_3 = Math.unsignedMultiplyHigh(a1, b3);
        long p2_0 = a2 * b0;
        long h2_0 = Math.unsignedMultiplyHigh(a2, b0);
        long p2_1 = a2 * b1;
        long h2_1 = Math.unsignedMultiplyHigh(a2, b1);
        long p2_2 = a2 * b2;
        long h2_2 = Math.unsignedMultiplyHigh(a2, b2);
        long p2_3 = a2 * b3;
        long h2_3 = Math.unsignedMultiplyHigh(a2, b3);
        long p3_0 = a3 * b0;
        long h3_0 = Math.unsignedMultiplyHigh(a3, b0);
        long p3_1 = a3 * b1;
        long h3_1 = Math.unsignedMultiplyHigh(a3, b1);
        long p3_2 = a3 * b2;
        long h3_2 = Math.unsignedMultiplyHigh(a3, b2);
        long p3_3 = a3 * b3;
        long h3_3 = Math.unsignedMultiplyHigh(a3, b3);
        long lo0 = p0_0;
        long hi0 = 0;
        long lo1 = p0_1;
        long hi1 = 0;
        {
            long s = lo1 + p1_0;
            hi1 += (Long.compareUnsigned(s, lo1) < 0 ? 1 : 0);
            lo1 = s;
        }
        {
            long s = lo1 + h0_0;
            hi1 += (Long.compareUnsigned(s, lo1) < 0 ? 1 : 0);
            lo1 = s;
        }
        long lo2 = p0_2;
        long hi2 = 0;
        {
            long s = lo2 + p1_1;
            hi2 += (Long.compareUnsigned(s, lo2) < 0 ? 1 : 0);
            lo2 = s;
        }
        {
            long s = lo2 + p2_0;
            hi2 += (Long.compareUnsigned(s, lo2) < 0 ? 1 : 0);
            lo2 = s;
        }
        {
            long s = lo2 + h0_1;
            hi2 += (Long.compareUnsigned(s, lo2) < 0 ? 1 : 0);
            lo2 = s;
        }
        {
            long s = lo2 + h1_0;
            hi2 += (Long.compareUnsigned(s, lo2) < 0 ? 1 : 0);
            lo2 = s;
        }
        long lo3 = p0_3;
        long hi3 = 0;
        {
            long s = lo3 + p1_2;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        {
            long s = lo3 + p2_1;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        {
            long s = lo3 + p3_0;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        {
            long s = lo3 + h0_2;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        {
            long s = lo3 + h1_1;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        {
            long s = lo3 + h2_0;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        long lo4 = p1_3;
        long hi4 = 0;
        {
            long s = lo4 + p2_2;
            hi4 += (Long.compareUnsigned(s, lo4) < 0 ? 1 : 0);
            lo4 = s;
        }
        {
            long s = lo4 + p3_1;
            hi4 += (Long.compareUnsigned(s, lo4) < 0 ? 1 : 0);
            lo4 = s;
        }
        {
            long s = lo4 + h0_3;
            hi4 += (Long.compareUnsigned(s, lo4) < 0 ? 1 : 0);
            lo4 = s;
        }
        {
            long s = lo4 + h1_2;
            hi4 += (Long.compareUnsigned(s, lo4) < 0 ? 1 : 0);
            lo4 = s;
        }
        {
            long s = lo4 + h2_1;
            hi4 += (Long.compareUnsigned(s, lo4) < 0 ? 1 : 0);
            lo4 = s;
        }
        {
            long s = lo4 + h3_0;
            hi4 += (Long.compareUnsigned(s, lo4) < 0 ? 1 : 0);
            lo4 = s;
        }
        long lo5 = p2_3;
        long hi5 = 0;
        {
            long s = lo5 + p3_2;
            hi5 += (Long.compareUnsigned(s, lo5) < 0 ? 1 : 0);
            lo5 = s;
        }
        {
            long s = lo5 + h1_3;
            hi5 += (Long.compareUnsigned(s, lo5) < 0 ? 1 : 0);
            lo5 = s;
        }
        {
            long s = lo5 + h2_2;
            hi5 += (Long.compareUnsigned(s, lo5) < 0 ? 1 : 0);
            lo5 = s;
        }
        {
            long s = lo5 + h3_1;
            hi5 += (Long.compareUnsigned(s, lo5) < 0 ? 1 : 0);
            lo5 = s;
        }
        long lo6 = p3_3;
        long hi6 = 0;
        {
            long s = lo6 + h2_3;
            hi6 += (Long.compareUnsigned(s, lo6) < 0 ? 1 : 0);
            lo6 = s;
        }
        {
            long s = lo6 + h3_2;
            hi6 += (Long.compareUnsigned(s, lo6) < 0 ? 1 : 0);
            lo6 = s;
        }
        long lo7 = h3_3;
        long hi7 = 0;
        long r0 = lo0;
        long carry = 0;
        long s1 = lo1 + hi0;
        long c1 = (Long.compareUnsigned(s1, lo1) < 0 ? 1 : 0);
        long r1 = s1 + carry;
        carry = c1 + (Long.compareUnsigned(r1, s1) < 0 ? 1 : 0);
        long s2 = lo2 + hi1;
        long c2 = (Long.compareUnsigned(s2, lo2) < 0 ? 1 : 0);
        long r2 = s2 + carry;
        carry = c2 + (Long.compareUnsigned(r2, s2) < 0 ? 1 : 0);
        long s3 = lo3 + hi2;
        long c3 = (Long.compareUnsigned(s3, lo3) < 0 ? 1 : 0);
        long r3 = s3 + carry;
        carry = c3 + (Long.compareUnsigned(r3, s3) < 0 ? 1 : 0);
        long s4 = lo4 + hi3;
        long c4 = (Long.compareUnsigned(s4, lo4) < 0 ? 1 : 0);
        long r4 = s4 + carry;
        carry = c4 + (Long.compareUnsigned(r4, s4) < 0 ? 1 : 0);
        long s5 = lo5 + hi4;
        long c5 = (Long.compareUnsigned(s5, lo5) < 0 ? 1 : 0);
        long r5 = s5 + carry;
        carry = c5 + (Long.compareUnsigned(r5, s5) < 0 ? 1 : 0);
        long s6 = lo6 + hi5;
        long c6 = (Long.compareUnsigned(s6, lo6) < 0 ? 1 : 0);
        long r6 = s6 + carry;
        carry = c6 + (Long.compareUnsigned(r6, s6) < 0 ? 1 : 0);
        long s7 = lo7 + hi6;
        long c7 = (Long.compareUnsigned(s7, lo7) < 0 ? 1 : 0);
        long r7 = s7 + carry;
        carry = c7 + (Long.compareUnsigned(r7, s7) < 0 ? 1 : 0);
        return Wide512.of(r0, r1, r2, r3, r4, r5, r6, r7);
    }

    /**
     * Low 256 bits of the product from raw limbs (per-column 128-bit accumulators, deferred carries).
     */
    static Wide256 mulLow8(long a0, long a1, long a2, long a3, long b0, long b1, long b2, long b3) {
        long p0_0 = a0 * b0;
        long h0_0 = Math.unsignedMultiplyHigh(a0, b0);
        long p0_1 = a0 * b1;
        long h0_1 = Math.unsignedMultiplyHigh(a0, b1);
        long p0_2 = a0 * b2;
        long h0_2 = Math.unsignedMultiplyHigh(a0, b2);
        long p0_3 = a0 * b3;
        long p1_0 = a1 * b0;
        long h1_0 = Math.unsignedMultiplyHigh(a1, b0);
        long p1_1 = a1 * b1;
        long h1_1 = Math.unsignedMultiplyHigh(a1, b1);
        long p1_2 = a1 * b2;
        long p2_0 = a2 * b0;
        long h2_0 = Math.unsignedMultiplyHigh(a2, b0);
        long p2_1 = a2 * b1;
        long p3_0 = a3 * b0;
        long lo0 = p0_0;
        long hi0 = 0;
        long lo1 = p0_1;
        long hi1 = 0;
        {
            long s = lo1 + p1_0;
            hi1 += (Long.compareUnsigned(s, lo1) < 0 ? 1 : 0);
            lo1 = s;
        }
        {
            long s = lo1 + h0_0;
            hi1 += (Long.compareUnsigned(s, lo1) < 0 ? 1 : 0);
            lo1 = s;
        }
        long lo2 = p0_2;
        long hi2 = 0;
        {
            long s = lo2 + p1_1;
            hi2 += (Long.compareUnsigned(s, lo2) < 0 ? 1 : 0);
            lo2 = s;
        }
        {
            long s = lo2 + p2_0;
            hi2 += (Long.compareUnsigned(s, lo2) < 0 ? 1 : 0);
            lo2 = s;
        }
        {
            long s = lo2 + h0_1;
            hi2 += (Long.compareUnsigned(s, lo2) < 0 ? 1 : 0);
            lo2 = s;
        }
        {
            long s = lo2 + h1_0;
            hi2 += (Long.compareUnsigned(s, lo2) < 0 ? 1 : 0);
            lo2 = s;
        }
        long lo3 = p0_3;
        long hi3 = 0;
        {
            long s = lo3 + p1_2;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        {
            long s = lo3 + p2_1;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        {
            long s = lo3 + p3_0;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        {
            long s = lo3 + h0_2;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        {
            long s = lo3 + h1_1;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        {
            long s = lo3 + h2_0;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        long r0 = lo0;
        long carry = 0;
        long s1 = lo1 + hi0;
        long c1 = (Long.compareUnsigned(s1, lo1) < 0 ? 1 : 0);
        long r1 = s1 + carry;
        carry = c1 + (Long.compareUnsigned(r1, s1) < 0 ? 1 : 0);
        long s2 = lo2 + hi1;
        long c2 = (Long.compareUnsigned(s2, lo2) < 0 ? 1 : 0);
        long r2 = s2 + carry;
        carry = c2 + (Long.compareUnsigned(r2, s2) < 0 ? 1 : 0);
        long s3 = lo3 + hi2;
        long c3 = (Long.compareUnsigned(s3, lo3) < 0 ? 1 : 0);
        long r3 = s3 + carry;
        carry = c3 + (Long.compareUnsigned(r3, s3) < 0 ? 1 : 0);
        return Wide256.of(r0, r1, r2, r3);
    }

    /**
     * Full 512-bit product written into {@code out[off..]} (zero-object variant).
     */
    static void mulFullInto8(long[] out, int off, long a0, long a1, long a2, long a3, long b0, long b1, long b2, long b3) {
        long p0_0 = a0 * b0;
        long h0_0 = Math.unsignedMultiplyHigh(a0, b0);
        long p0_1 = a0 * b1;
        long h0_1 = Math.unsignedMultiplyHigh(a0, b1);
        long p0_2 = a0 * b2;
        long h0_2 = Math.unsignedMultiplyHigh(a0, b2);
        long p0_3 = a0 * b3;
        long h0_3 = Math.unsignedMultiplyHigh(a0, b3);
        long p1_0 = a1 * b0;
        long h1_0 = Math.unsignedMultiplyHigh(a1, b0);
        long p1_1 = a1 * b1;
        long h1_1 = Math.unsignedMultiplyHigh(a1, b1);
        long p1_2 = a1 * b2;
        long h1_2 = Math.unsignedMultiplyHigh(a1, b2);
        long p1_3 = a1 * b3;
        long h1_3 = Math.unsignedMultiplyHigh(a1, b3);
        long p2_0 = a2 * b0;
        long h2_0 = Math.unsignedMultiplyHigh(a2, b0);
        long p2_1 = a2 * b1;
        long h2_1 = Math.unsignedMultiplyHigh(a2, b1);
        long p2_2 = a2 * b2;
        long h2_2 = Math.unsignedMultiplyHigh(a2, b2);
        long p2_3 = a2 * b3;
        long h2_3 = Math.unsignedMultiplyHigh(a2, b3);
        long p3_0 = a3 * b0;
        long h3_0 = Math.unsignedMultiplyHigh(a3, b0);
        long p3_1 = a3 * b1;
        long h3_1 = Math.unsignedMultiplyHigh(a3, b1);
        long p3_2 = a3 * b2;
        long h3_2 = Math.unsignedMultiplyHigh(a3, b2);
        long p3_3 = a3 * b3;
        long h3_3 = Math.unsignedMultiplyHigh(a3, b3);
        long lo0 = p0_0;
        long hi0 = 0;
        long lo1 = p0_1;
        long hi1 = 0;
        {
            long s = lo1 + p1_0;
            hi1 += (Long.compareUnsigned(s, lo1) < 0 ? 1 : 0);
            lo1 = s;
        }
        {
            long s = lo1 + h0_0;
            hi1 += (Long.compareUnsigned(s, lo1) < 0 ? 1 : 0);
            lo1 = s;
        }
        long lo2 = p0_2;
        long hi2 = 0;
        {
            long s = lo2 + p1_1;
            hi2 += (Long.compareUnsigned(s, lo2) < 0 ? 1 : 0);
            lo2 = s;
        }
        {
            long s = lo2 + p2_0;
            hi2 += (Long.compareUnsigned(s, lo2) < 0 ? 1 : 0);
            lo2 = s;
        }
        {
            long s = lo2 + h0_1;
            hi2 += (Long.compareUnsigned(s, lo2) < 0 ? 1 : 0);
            lo2 = s;
        }
        {
            long s = lo2 + h1_0;
            hi2 += (Long.compareUnsigned(s, lo2) < 0 ? 1 : 0);
            lo2 = s;
        }
        long lo3 = p0_3;
        long hi3 = 0;
        {
            long s = lo3 + p1_2;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        {
            long s = lo3 + p2_1;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        {
            long s = lo3 + p3_0;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        {
            long s = lo3 + h0_2;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        {
            long s = lo3 + h1_1;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        {
            long s = lo3 + h2_0;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        long lo4 = p1_3;
        long hi4 = 0;
        {
            long s = lo4 + p2_2;
            hi4 += (Long.compareUnsigned(s, lo4) < 0 ? 1 : 0);
            lo4 = s;
        }
        {
            long s = lo4 + p3_1;
            hi4 += (Long.compareUnsigned(s, lo4) < 0 ? 1 : 0);
            lo4 = s;
        }
        {
            long s = lo4 + h0_3;
            hi4 += (Long.compareUnsigned(s, lo4) < 0 ? 1 : 0);
            lo4 = s;
        }
        {
            long s = lo4 + h1_2;
            hi4 += (Long.compareUnsigned(s, lo4) < 0 ? 1 : 0);
            lo4 = s;
        }
        {
            long s = lo4 + h2_1;
            hi4 += (Long.compareUnsigned(s, lo4) < 0 ? 1 : 0);
            lo4 = s;
        }
        {
            long s = lo4 + h3_0;
            hi4 += (Long.compareUnsigned(s, lo4) < 0 ? 1 : 0);
            lo4 = s;
        }
        long lo5 = p2_3;
        long hi5 = 0;
        {
            long s = lo5 + p3_2;
            hi5 += (Long.compareUnsigned(s, lo5) < 0 ? 1 : 0);
            lo5 = s;
        }
        {
            long s = lo5 + h1_3;
            hi5 += (Long.compareUnsigned(s, lo5) < 0 ? 1 : 0);
            lo5 = s;
        }
        {
            long s = lo5 + h2_2;
            hi5 += (Long.compareUnsigned(s, lo5) < 0 ? 1 : 0);
            lo5 = s;
        }
        {
            long s = lo5 + h3_1;
            hi5 += (Long.compareUnsigned(s, lo5) < 0 ? 1 : 0);
            lo5 = s;
        }
        long lo6 = p3_3;
        long hi6 = 0;
        {
            long s = lo6 + h2_3;
            hi6 += (Long.compareUnsigned(s, lo6) < 0 ? 1 : 0);
            lo6 = s;
        }
        {
            long s = lo6 + h3_2;
            hi6 += (Long.compareUnsigned(s, lo6) < 0 ? 1 : 0);
            lo6 = s;
        }
        long lo7 = h3_3;
        long hi7 = 0;
        long r0 = lo0;
        long carry = 0;
        long s1 = lo1 + hi0;
        long c1 = (Long.compareUnsigned(s1, lo1) < 0 ? 1 : 0);
        long r1 = s1 + carry;
        carry = c1 + (Long.compareUnsigned(r1, s1) < 0 ? 1 : 0);
        long s2 = lo2 + hi1;
        long c2 = (Long.compareUnsigned(s2, lo2) < 0 ? 1 : 0);
        long r2 = s2 + carry;
        carry = c2 + (Long.compareUnsigned(r2, s2) < 0 ? 1 : 0);
        long s3 = lo3 + hi2;
        long c3 = (Long.compareUnsigned(s3, lo3) < 0 ? 1 : 0);
        long r3 = s3 + carry;
        carry = c3 + (Long.compareUnsigned(r3, s3) < 0 ? 1 : 0);
        long s4 = lo4 + hi3;
        long c4 = (Long.compareUnsigned(s4, lo4) < 0 ? 1 : 0);
        long r4 = s4 + carry;
        carry = c4 + (Long.compareUnsigned(r4, s4) < 0 ? 1 : 0);
        long s5 = lo5 + hi4;
        long c5 = (Long.compareUnsigned(s5, lo5) < 0 ? 1 : 0);
        long r5 = s5 + carry;
        carry = c5 + (Long.compareUnsigned(r5, s5) < 0 ? 1 : 0);
        long s6 = lo6 + hi5;
        long c6 = (Long.compareUnsigned(s6, lo6) < 0 ? 1 : 0);
        long r6 = s6 + carry;
        carry = c6 + (Long.compareUnsigned(r6, s6) < 0 ? 1 : 0);
        long s7 = lo7 + hi6;
        long c7 = (Long.compareUnsigned(s7, lo7) < 0 ? 1 : 0);
        long r7 = s7 + carry;
        carry = c7 + (Long.compareUnsigned(r7, s7) < 0 ? 1 : 0);
        out[off + 0] = r0;
        out[off + 1] = r1;
        out[off + 2] = r2;
        out[off + 3] = r3;
        out[off + 4] = r4;
        out[off + 5] = r5;
        out[off + 6] = r6;
        out[off + 7] = r7;
    }

    /**
     * Low 256 bits of the product written into {@code out[off..]} (zero-object variant).
     */
    static void mulLowInto8(long[] out, int off, long a0, long a1, long a2, long a3, long b0, long b1, long b2, long b3) {
        long p0_0 = a0 * b0;
        long h0_0 = Math.unsignedMultiplyHigh(a0, b0);
        long p0_1 = a0 * b1;
        long h0_1 = Math.unsignedMultiplyHigh(a0, b1);
        long p0_2 = a0 * b2;
        long h0_2 = Math.unsignedMultiplyHigh(a0, b2);
        long p0_3 = a0 * b3;
        long p1_0 = a1 * b0;
        long h1_0 = Math.unsignedMultiplyHigh(a1, b0);
        long p1_1 = a1 * b1;
        long h1_1 = Math.unsignedMultiplyHigh(a1, b1);
        long p1_2 = a1 * b2;
        long p2_0 = a2 * b0;
        long h2_0 = Math.unsignedMultiplyHigh(a2, b0);
        long p2_1 = a2 * b1;
        long p3_0 = a3 * b0;
        long lo0 = p0_0;
        long hi0 = 0;
        long lo1 = p0_1;
        long hi1 = 0;
        {
            long s = lo1 + p1_0;
            hi1 += (Long.compareUnsigned(s, lo1) < 0 ? 1 : 0);
            lo1 = s;
        }
        {
            long s = lo1 + h0_0;
            hi1 += (Long.compareUnsigned(s, lo1) < 0 ? 1 : 0);
            lo1 = s;
        }
        long lo2 = p0_2;
        long hi2 = 0;
        {
            long s = lo2 + p1_1;
            hi2 += (Long.compareUnsigned(s, lo2) < 0 ? 1 : 0);
            lo2 = s;
        }
        {
            long s = lo2 + p2_0;
            hi2 += (Long.compareUnsigned(s, lo2) < 0 ? 1 : 0);
            lo2 = s;
        }
        {
            long s = lo2 + h0_1;
            hi2 += (Long.compareUnsigned(s, lo2) < 0 ? 1 : 0);
            lo2 = s;
        }
        {
            long s = lo2 + h1_0;
            hi2 += (Long.compareUnsigned(s, lo2) < 0 ? 1 : 0);
            lo2 = s;
        }
        long lo3 = p0_3;
        long hi3 = 0;
        {
            long s = lo3 + p1_2;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        {
            long s = lo3 + p2_1;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        {
            long s = lo3 + p3_0;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        {
            long s = lo3 + h0_2;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        {
            long s = lo3 + h1_1;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        {
            long s = lo3 + h2_0;
            hi3 += (Long.compareUnsigned(s, lo3) < 0 ? 1 : 0);
            lo3 = s;
        }
        long r0 = lo0;
        long carry = 0;
        long s1 = lo1 + hi0;
        long c1 = (Long.compareUnsigned(s1, lo1) < 0 ? 1 : 0);
        long r1 = s1 + carry;
        carry = c1 + (Long.compareUnsigned(r1, s1) < 0 ? 1 : 0);
        long s2 = lo2 + hi1;
        long c2 = (Long.compareUnsigned(s2, lo2) < 0 ? 1 : 0);
        long r2 = s2 + carry;
        carry = c2 + (Long.compareUnsigned(r2, s2) < 0 ? 1 : 0);
        long s3 = lo3 + hi2;
        long c3 = (Long.compareUnsigned(s3, lo3) < 0 ? 1 : 0);
        long r3 = s3 + carry;
        carry = c3 + (Long.compareUnsigned(r3, s3) < 0 ? 1 : 0);
        out[off + 0] = r0;
        out[off + 1] = r1;
        out[off + 2] = r2;
        out[off + 3] = r3;
    }
    // GEN-ATOM-END
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
        long k = Carry.carryOut(a, t);
        long c = l1 * o.l0;
        long r = t + c;
        k += Carry.carryOut(t, r);
        acc.c1 = r;
        acc.carry = k;
    }

    private void col2(MulAcc acc, Wide256 o) {
        long a = Math.unsignedMultiplyHigh(l0, o.l1);
        long b = Math.unsignedMultiplyHigh(l1, o.l0);
        long t = a + b;
        long k = Carry.carryOut(a, t);
        long c = l0 * o.l2;
        long t2 = t + c;
        k += Carry.carryOut(t, t2);
        long d = l1 * o.l1;
        long t3 = t2 + d;
        k += Carry.carryOut(t2, t3);
        long e = l2 * o.l0;
        long t4 = t3 + e;
        k += Carry.carryOut(t3, t4);
        long r = t4 + acc.carry;
        k += Carry.carryOut(t4, r);
        acc.c2 = r;
        acc.carry = k;
    }

    private void col3(MulAcc acc, Wide256 o) {
        long a = Math.unsignedMultiplyHigh(l0, o.l2);
        long b = Math.unsignedMultiplyHigh(l1, o.l1);
        long t = a + b;
        long k = Carry.carryOut(a, t);
        long c = Math.unsignedMultiplyHigh(l2, o.l0);
        long t2 = t + c;
        k += Carry.carryOut(t, t2);
        long d = l0 * o.l3;
        long t3 = t2 + d;
        k += Carry.carryOut(t2, t3);
        long e = l1 * o.l2;
        long t4 = t3 + e;
        k += Carry.carryOut(t3, t4);
        long f = l2 * o.l1;
        long t5 = t4 + f;
        k += Carry.carryOut(t4, t5);
        long g = l3 * o.l0;
        long t6 = t5 + g;
        k += Carry.carryOut(t5, t6);
        long r = t6 + acc.carry;
        k += Carry.carryOut(t6, r);
        acc.c3 = r;
        acc.carry = k;
    }

    private void col4(MulAcc acc, Wide256 o) {
        long a = Math.unsignedMultiplyHigh(l0, o.l3);
        long b = Math.unsignedMultiplyHigh(l1, o.l2);
        long t = a + b;
        long k = Carry.carryOut(a, t);
        long c = Math.unsignedMultiplyHigh(l2, o.l1);
        long t2 = t + c;
        k += Carry.carryOut(t, t2);
        long d = Math.unsignedMultiplyHigh(l3, o.l0);
        long t3 = t2 + d;
        k += Carry.carryOut(t2, t3);
        long e = l1 * o.l3;
        long t4 = t3 + e;
        k += Carry.carryOut(t3, t4);
        long f = l2 * o.l2;
        long t5 = t4 + f;
        k += Carry.carryOut(t4, t5);
        long g = l3 * o.l1;
        long t6 = t5 + g;
        k += Carry.carryOut(t5, t6);
        long r = t6 + acc.carry;
        k += Carry.carryOut(t6, r);
        acc.c4 = r;
        acc.carry = k;
    }

    private void col5(MulAcc acc, Wide256 o) {
        long a = Math.unsignedMultiplyHigh(l1, o.l3);
        long b = Math.unsignedMultiplyHigh(l2, o.l2);
        long t = a + b;
        long k = Carry.carryOut(a, t);
        long c = Math.unsignedMultiplyHigh(l3, o.l1);
        long t2 = t + c;
        k += Carry.carryOut(t, t2);
        long d = l2 * o.l3;
        long t3 = t2 + d;
        k += Carry.carryOut(t2, t3);
        long e = l3 * o.l2;
        long t4 = t3 + e;
        k += Carry.carryOut(t3, t4);
        long r = t4 + acc.carry;
        k += Carry.carryOut(t4, r);
        acc.c5 = r;
        acc.carry = k;
    }

    private void col6(MulAcc acc, Wide256 o) {
        long a = Math.unsignedMultiplyHigh(l2, o.l3);
        long b = Math.unsignedMultiplyHigh(l3, o.l2);
        long t = a + b;
        long k = Carry.carryOut(a, t);
        long c = l3 * o.l3;
        long t2 = t + c;
        k += Carry.carryOut(t, t2);
        long r = t2 + acc.carry;
        k += Carry.carryOut(t2, r);
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
