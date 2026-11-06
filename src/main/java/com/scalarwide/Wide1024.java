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

    /**
     * Unsigned division: `this / o`, truncated.
     *
     * @throws ArithmeticException if `o` is zero
     */
    public Wide1024 div(Wide1024 o) {
        long[] q = new long[LIMBS];
        Limbs.divmod(pack(), o.pack(), q, null);
        return of(q[0], q[1], q[2], q[3],
                q[4], q[5], q[6], q[7],
                q[8], q[9], q[10], q[11],
                q[12], q[13], q[14], q[15]);
    }

    /**
     * Unsigned remainder: `this % o`.
     *
     * @throws ArithmeticException if `o` is zero
     */
    public Wide1024 rem(Wide1024 o) {
        long[] q = new long[LIMBS];
        long[] r = new long[LIMBS];
        Limbs.divmod(pack(), o.pack(), q, r);
        return of(r[0], r[1], r[2], r[3],
                r[4], r[5], r[6], r[7],
                r[8], r[9], r[10], r[11],
                r[12], r[13], r[14], r[15]);
    }

    /**
     * Multiplicative power modulo 2^BITS (square-and-multiply).
     *
     * @throws IllegalArgumentException if `exp` is negative
     */
    public Wide1024 pow(int exp) {
        if (exp < 0) {
            throw new IllegalArgumentException("negative exponent: " + exp);
        }
        Wide1024 result = ONE;
        Wide1024 base = this;
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
                + Long.bitCount(l6) + Long.bitCount(l7)
                + Long.bitCount(l8) + Long.bitCount(l9)
                + Long.bitCount(l10) + Long.bitCount(l11)
                + Long.bitCount(l12) + Long.bitCount(l13)
                + Long.bitCount(l14) + Long.bitCount(l15);
    }

    /** Unsigned minimum (consistent with {@link #compareUnsigned}). */
    public Wide1024 min(Wide1024 o) {
        return compareUnsigned(o) <= 0 ? this : o;
    }

    /** Unsigned maximum (consistent with {@link #compareUnsigned}). */
    public Wide1024 max(Wide1024 o) {
        return compareUnsigned(o) >= 0 ? this : o;
    }

    /** Two's-complement absolute value; MIN_VALUE wraps to itself. */
    public Wide1024 abs() {
        return l15 < 0 ? neg() : this;
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
        x = l8;
        for (int k = 0; k < 8 && pos > 0; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l9;
        for (int k = 0; k < 8 && pos > 0; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l10;
        for (int k = 0; k < 8 && pos > 0; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l11;
        for (int k = 0; k < 8 && pos > 0; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l12;
        for (int k = 0; k < 8 && pos > 0; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l13;
        for (int k = 0; k < 8 && pos > 0; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l14;
        for (int k = 0; k < 8 && pos > 0; k++) {
            out[--pos] = (byte) x;
            x >>>= 8;
        }
        x = l15;
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
        x = l8;
        for (int k = 0; k < 8 && pos < n; k++) {
            out[pos++] = (byte) x;
            x >>>= 8;
        }
        x = l9;
        for (int k = 0; k < 8 && pos < n; k++) {
            out[pos++] = (byte) x;
            x >>>= 8;
        }
        x = l10;
        for (int k = 0; k < 8 && pos < n; k++) {
            out[pos++] = (byte) x;
            x >>>= 8;
        }
        x = l11;
        for (int k = 0; k < 8 && pos < n; k++) {
            out[pos++] = (byte) x;
            x >>>= 8;
        }
        x = l12;
        for (int k = 0; k < 8 && pos < n; k++) {
            out[pos++] = (byte) x;
            x >>>= 8;
        }
        x = l13;
        for (int k = 0; k < 8 && pos < n; k++) {
            out[pos++] = (byte) x;
            x >>>= 8;
        }
        x = l14;
        for (int k = 0; k < 8 && pos < n; k++) {
            out[pos++] = (byte) x;
            x >>>= 8;
        }
        x = l15;
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
        pos -= 8; Limbs.BE_LONG.set(out, pos, l0);
        pos -= 8; Limbs.BE_LONG.set(out, pos, l1);
        pos -= 8; Limbs.BE_LONG.set(out, pos, l2);
        pos -= 8; Limbs.BE_LONG.set(out, pos, l3);
        pos -= 8; Limbs.BE_LONG.set(out, pos, l4);
        pos -= 8; Limbs.BE_LONG.set(out, pos, l5);
        pos -= 8; Limbs.BE_LONG.set(out, pos, l6);
        pos -= 8; Limbs.BE_LONG.set(out, pos, l7);
        pos -= 8; Limbs.BE_LONG.set(out, pos, l8);
        pos -= 8; Limbs.BE_LONG.set(out, pos, l9);
        pos -= 8; Limbs.BE_LONG.set(out, pos, l10);
        pos -= 8; Limbs.BE_LONG.set(out, pos, l11);
        pos -= 8; Limbs.BE_LONG.set(out, pos, l12);
        pos -= 8; Limbs.BE_LONG.set(out, pos, l13);
        pos -= 8; Limbs.BE_LONG.set(out, pos, l14);
        pos -= 8; Limbs.BE_LONG.set(out, pos, l15);
        return out;
    }

    /**
     * Unsigned big-endian decode; leading zero padding is tolerated (so
     * BigInteger.toByteArray output with a sign byte round-trips).
     *
     * @throws IllegalArgumentException if the significant length exceeds BITS/8
     */
    public static Wide1024 fromByteArray(byte[] src, int off, int len) {
        int max = LIMBS * 8;
        while (len > max && src[off] == 0) {
            off++;
            len--;
        }
        if (len > max) {
            throw new IllegalArgumentException("byte array too long: " + len);
        }
        long[] out = new long[LIMBS];
        int pos = off;
        int idx = ((len + 7) >>> 3) - 1;
        int rem = len & 7;
        if (rem != 0) {
            long head = 0;
            for (int k = 0; k < rem; k++) {
                head = (head << 8) | (src[pos + k] & 0xFFL);
            }
            out[idx--] = head;
            pos += rem;
        }
        while (idx >= 0) {
            out[idx--] = (long) Limbs.BE_LONG.get(src, pos);
            pos += 8;
        }
        return of(out[0], out[1], out[2], out[3], out[4], out[5], out[6], out[7], out[8], out[9], out[10], out[11], out[12], out[13], out[14], out[15]);
    }

    /** @see #fromByteArray(byte[], int, int) */
    public static Wide1024 fromByteArray(byte[] src) {
        return fromByteArray(src, 0, src.length);
    }

    /** Unsigned little-endian decode. */
    public static Wide1024 fromByteArrayLE(byte[] src, int off, int len) {
        int max = LIMBS * 8;
        if (len > max) {
            throw new IllegalArgumentException("byte array too long: " + len);
        }
        long[] out = new long[LIMBS];
        int pos = off;
        int end = off + len;
        int idx = 0;
        while (end - pos >= 8) {
            out[idx++] = (long) Limbs.LE_LONG.get(src, pos);
            pos += 8;
        }
        if (pos < end) {
            long tail = 0;
            int shift = 0;
            while (pos < end) {
                tail |= (src[pos++] & 0xFFL) << shift;
                shift += 8;
            }
            out[idx] = tail;
        }
        return of(out[0], out[1], out[2], out[3], out[4], out[5], out[6], out[7], out[8], out[9], out[10], out[11], out[12], out[13], out[14], out[15]);
    }

    /** @see #fromByteArrayLE(byte[], int, int) */
    public static Wide1024 fromByteArrayLE(byte[] src) {
        return fromByteArrayLE(src, 0, src.length);
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
        c = Carry.carryOut(l7, s7) + Carry.carryOut(s7, r7);
        long s8 = l8 + o.l8;
        long r8 = s8 + c;
        c = Carry.carryOut(l8, s8) + Carry.carryOut(s8, r8);
        long s9 = l9 + o.l9;
        long r9 = s9 + c;
        c = Carry.carryOut(l9, s9) + Carry.carryOut(s9, r9);
        long s10 = l10 + o.l10;
        long r10 = s10 + c;
        c = Carry.carryOut(l10, s10) + Carry.carryOut(s10, r10);
        long s11 = l11 + o.l11;
        long r11 = s11 + c;
        c = Carry.carryOut(l11, s11) + Carry.carryOut(s11, r11);
        long s12 = l12 + o.l12;
        long r12 = s12 + c;
        c = Carry.carryOut(l12, s12) + Carry.carryOut(s12, r12);
        long s13 = l13 + o.l13;
        long r13 = s13 + c;
        c = Carry.carryOut(l13, s13) + Carry.carryOut(s13, r13);
        long s14 = l14 + o.l14;
        long r14 = s14 + c;
        c = Carry.carryOut(l14, s14) + Carry.carryOut(s14, r14);
        long s15 = l15 + o.l15;
        long r15 = s15 + c;
        return new Wide1024(r0, r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11, r12, r13, r14, r15);
    }

    /** Wrapping subtraction modulo 2^1024. */
    public Wide1024 sub(Wide1024 o) {
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
        b = Carry.borrowOut(l7, d7) + Carry.borrowOut(d7, r7);
        long d8 = l8 - o.l8;
        long r8 = d8 - b;
        b = Carry.borrowOut(l8, d8) + Carry.borrowOut(d8, r8);
        long d9 = l9 - o.l9;
        long r9 = d9 - b;
        b = Carry.borrowOut(l9, d9) + Carry.borrowOut(d9, r9);
        long d10 = l10 - o.l10;
        long r10 = d10 - b;
        b = Carry.borrowOut(l10, d10) + Carry.borrowOut(d10, r10);
        long d11 = l11 - o.l11;
        long r11 = d11 - b;
        b = Carry.borrowOut(l11, d11) + Carry.borrowOut(d11, r11);
        long d12 = l12 - o.l12;
        long r12 = d12 - b;
        b = Carry.borrowOut(l12, d12) + Carry.borrowOut(d12, r12);
        long d13 = l13 - o.l13;
        long r13 = d13 - b;
        b = Carry.borrowOut(l13, d13) + Carry.borrowOut(d13, r13);
        long d14 = l14 - o.l14;
        long r14 = d14 - b;
        b = Carry.borrowOut(l14, d14) + Carry.borrowOut(d14, r14);
        long d15 = l15 - o.l15;
        long r15 = d15 - b;
        return new Wide1024(r0, r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11, r12, r13, r14, r15);
    }

    /** Wrapping negation modulo 2^1024. */
    public Wide1024 neg() {
        return ZERO.sub(this);
    }

    /**
     * Low 1024 bits of the 2048-bit product, i.e. {@code (a*b) mod 2^1024},
     * composed from 256-bit atom block products written into a single scratch
     * array (zero intermediate objects; limb-level assembly).
     */
    /**
     * Low N bits of the 2N-bit product, column-major with delayed carry: every output limb
     * is accumulated by its own pair of registers (running sum + overflow count) and
     * written exactly once, so no product round-trips through memory. Generated by
     * tools/gen-colmul.ps1 -- do not edit this method or the mulCols* helpers by hand.
     */
    private long mulCols0(Wide1024 o, long[] r, long carry) {
        long t;
        {
            long u = l0 * o.l0;
            long q = 0;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[0] = t;
            carry = q;
        }
        {
            long u = l0 * o.l1;
            long q = 0;
            t = u + l1 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l0, o.l0);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[1] = t;
            carry = q;
        }
        {
            long u = l0 * o.l2;
            long q = 0;
            t = u + l1 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l1);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[2] = t;
            carry = q;
        }
        {
            long u = l0 * o.l3;
            long q = 0;
            t = u + l1 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l2);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[3] = t;
            carry = q;
        }
        {
            long u = l0 * o.l4;
            long q = 0;
            t = u + l1 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l3);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[4] = t;
            carry = q;
        }
        {
            long u = l0 * o.l5;
            long q = 0;
            t = u + l1 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l4);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[5] = t;
            carry = q;
        }
        {
            long u = l0 * o.l6;
            long q = 0;
            t = u + l1 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l5);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[6] = t;
            carry = q;
        }
        {
            long u = l0 * o.l7;
            long q = 0;
            t = u + l1 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l6);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[7] = t;
            carry = q;
        }
        {
            long u = l0 * o.l8;
            long q = 0;
            t = u + l1 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l7);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[8] = t;
            carry = q;
        }
        {
            long u = l0 * o.l9;
            long q = 0;
            t = u + l1 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l8);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l8, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[9] = t;
            carry = q;
        }
        {
            long u = l0 * o.l10;
            long q = 0;
            t = u + l1 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l9);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l8, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l9, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[10] = t;
            carry = q;
        }
        {
            long u = l0 * o.l11;
            long q = 0;
            t = u + l1 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l10);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l8, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l9, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l10, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[11] = t;
            carry = q;
        }
        {
            long u = l0 * o.l12;
            long q = 0;
            t = u + l1 * o.l11;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l12 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l11);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l10);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l8, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l9, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l10, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l11, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[12] = t;
            carry = q;
        }
        return carry;
    }

    private long mulCols1(Wide1024 o, long[] r, long carry) {
        long t;
        {
            long u = l0 * o.l13;
            long q = 0;
            t = u + l1 * o.l12;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l11;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l12 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l13 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l12);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l11);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l10);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l8, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l9, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l10, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l11, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l12, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[13] = t;
            carry = q;
        }
        {
            long u = l0 * o.l14;
            long q = 0;
            t = u + l1 * o.l13;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l12;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l11;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l12 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l13 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l14 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l13);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l12);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l11);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l10);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l8, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l9, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l10, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l11, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l12, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l13, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[14] = t;
            carry = q;
        }
        {
            long u = l0 * o.l15;
            long q = 0;
            t = u + l1 * o.l14;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l13;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l12;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l11;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l12 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l13 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l14 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l15 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l14);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l13);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l12);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l11);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l10);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l8, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l9, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l10, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l11, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l12, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l13, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l14, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[15] = t;
            carry = q;
        }
        return carry;
    }

    /**
     * Low N bits of the 2N-bit product, column-major with delayed carry: every output limb
     * is accumulated by its own pair of registers (running sum + overflow count) and
     * written exactly once, so no product round-trips through memory. Generated by
     * tools/gen-colmul.ps1 -- do not edit this method or the mulCols* helpers by hand.
     */
    public Wide1024 mul(Wide1024 o) {
        long[] r = new long[16];
        long carry = 0;
        carry = mulCols0(o, r, carry);
        carry = mulCols1(o, r, carry);
        return Wide1024.of(r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9], r[10], r[11], r[12], r[13], r[14], r[15]);
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

    /**
     * High 1024 bits of the 2048-bit product, i.e. {@code (a*b) >>> 1024}
     * (monolithic 64-bit-limb schoolbook). The earlier block-composed
     * implementation is kept as {@link #mulHighComposed} for differential
     * testing.
     *
     * <p>{@code a}'s limb is taken once per row; {@code b}'s sixteen limbs are
     * read as fields at constant offsets. The inner loop is unrolled so no
     * 16-way {@link #limb} switch is re-entered on any of the 256 products
     * (P17: runtime-index accessors inside a loop defeat constant folding).
     */
    /**
     * High N bits of the 2N-bit product, column-major with delayed carry. Columns below N
     * only feed the carry chain (their value is discarded); columns N..2N-1 are the result.
     * Generated by tools/gen-colmul.ps1 -- do not edit this method or the mulHighCols*
     * helpers by hand.
     */
    /**
     * High N bits of the 2N-bit product, column-major with delayed carry. Columns below N
     * only feed the carry chain (their value is discarded); columns N..2N-1 are the result.
     * Generated by tools/gen-colmul.ps1 -- do not edit this method or the mulHighCols*
     * helpers by hand.
     */
    private long mulHighCols0(Wide1024 o, long[] r, long carry) {
        long t;
        {
            long u = l0 * o.l0;
            long q = 0;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            carry = q;
        }
        {
            long u = l0 * o.l1;
            long q = 0;
            t = u + l1 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l0, o.l0);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            carry = q;
        }
        {
            long u = l0 * o.l2;
            long q = 0;
            t = u + l1 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l1);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            carry = q;
        }
        {
            long u = l0 * o.l3;
            long q = 0;
            t = u + l1 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l2);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            carry = q;
        }
        {
            long u = l0 * o.l4;
            long q = 0;
            t = u + l1 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l3);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            carry = q;
        }
        {
            long u = l0 * o.l5;
            long q = 0;
            t = u + l1 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l4);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            carry = q;
        }
        {
            long u = l0 * o.l6;
            long q = 0;
            t = u + l1 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l5);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            carry = q;
        }
        {
            long u = l0 * o.l7;
            long q = 0;
            t = u + l1 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l6);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            carry = q;
        }
        {
            long u = l0 * o.l8;
            long q = 0;
            t = u + l1 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l7);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            carry = q;
        }
        {
            long u = l0 * o.l9;
            long q = 0;
            t = u + l1 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l8);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l8, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            carry = q;
        }
        {
            long u = l0 * o.l10;
            long q = 0;
            t = u + l1 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l9);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l8, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l9, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            carry = q;
        }
        {
            long u = l0 * o.l11;
            long q = 0;
            t = u + l1 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l10);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l8, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l9, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l10, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            carry = q;
        }
        {
            long u = l0 * o.l12;
            long q = 0;
            t = u + l1 * o.l11;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l12 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l11);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l10);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l8, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l9, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l10, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l11, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            carry = q;
        }
        return carry;
    }

    private long mulHighCols1(Wide1024 o, long[] r, long carry) {
        long t;
        {
            long u = l0 * o.l13;
            long q = 0;
            t = u + l1 * o.l12;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l11;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l12 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l13 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l12);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l11);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l10);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l8, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l9, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l10, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l11, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l12, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            carry = q;
        }
        {
            long u = l0 * o.l14;
            long q = 0;
            t = u + l1 * o.l13;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l12;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l11;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l12 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l13 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l14 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l13);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l12);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l11);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l10);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l8, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l9, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l10, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l11, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l12, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l13, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            carry = q;
        }
        {
            long u = l0 * o.l15;
            long q = 0;
            t = u + l1 * o.l14;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l2 * o.l13;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l12;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l11;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l12 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l13 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l14 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l15 * o.l0;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l0, o.l14);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l1, o.l13);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l2, o.l12);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l11);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l10);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l8, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l9, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l10, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l11, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l12, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l13, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l14, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            carry = q;
        }
        {
            long u = l1 * o.l15;
            long q = 0;
            t = u + l2 * o.l14;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l3 * o.l13;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l12;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l11;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l12 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l13 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l14 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l15 * o.l1;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l0, o.l15);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l1, o.l14);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l2, o.l13);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l3, o.l12);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l11);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l10);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l8, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l9, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l10, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l11, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l12, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l13, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l14, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l15, o.l0);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[0] = t;
            carry = q;
        }
        {
            long u = l2 * o.l15;
            long q = 0;
            t = u + l3 * o.l14;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l4 * o.l13;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l12;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l11;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l12 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l13 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l14 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l15 * o.l2;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l1, o.l15);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l2, o.l14);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l3, o.l13);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l4, o.l12);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l11);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l10);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l8, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l9, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l10, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l11, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l12, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l13, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l14, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l15, o.l1);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[1] = t;
            carry = q;
        }
        {
            long u = l3 * o.l15;
            long q = 0;
            t = u + l4 * o.l14;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l5 * o.l13;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l12;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l11;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l12 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l13 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l14 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l15 * o.l3;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l2, o.l15);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l3, o.l14);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l4, o.l13);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l5, o.l12);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l11);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l10);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l8, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l9, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l10, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l11, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l12, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l13, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l14, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l15, o.l2);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[2] = t;
            carry = q;
        }
        return carry;
    }

    private long mulHighCols2(Wide1024 o, long[] r, long carry) {
        long t;
        {
            long u = l4 * o.l15;
            long q = 0;
            t = u + l5 * o.l14;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l6 * o.l13;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l12;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l11;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l12 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l13 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l14 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l15 * o.l4;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l3, o.l15);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l4, o.l14);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l5, o.l13);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l6, o.l12);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l11);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l8, o.l10);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l9, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l10, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l11, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l12, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l13, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l14, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l15, o.l3);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[3] = t;
            carry = q;
        }
        {
            long u = l5 * o.l15;
            long q = 0;
            t = u + l6 * o.l14;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l7 * o.l13;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l12;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l11;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l12 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l13 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l14 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l15 * o.l5;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l4, o.l15);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l5, o.l14);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l6, o.l13);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l7, o.l12);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l8, o.l11);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l9, o.l10);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l10, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l11, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l12, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l13, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l14, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l15, o.l4);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[4] = t;
            carry = q;
        }
        {
            long u = l6 * o.l15;
            long q = 0;
            t = u + l7 * o.l14;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l8 * o.l13;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l12;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l11;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l12 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l13 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l14 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l15 * o.l6;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l5, o.l15);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l6, o.l14);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l7, o.l13);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l8, o.l12);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l9, o.l11);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l10, o.l10);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l11, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l12, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l13, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l14, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l15, o.l5);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[5] = t;
            carry = q;
        }
        {
            long u = l7 * o.l15;
            long q = 0;
            t = u + l8 * o.l14;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l9 * o.l13;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l12;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l11;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l12 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l13 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l14 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l15 * o.l7;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l6, o.l15);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l7, o.l14);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l8, o.l13);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l9, o.l12);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l10, o.l11);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l11, o.l10);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l12, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l13, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l14, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l15, o.l6);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[6] = t;
            carry = q;
        }
        {
            long u = l8 * o.l15;
            long q = 0;
            t = u + l9 * o.l14;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l13;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l12;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l12 * o.l11;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l13 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l14 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l15 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l7, o.l15);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l8, o.l14);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l9, o.l13);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l10, o.l12);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l11, o.l11);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l12, o.l10);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l13, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l14, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l15, o.l7);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[7] = t;
            carry = q;
        }
        {
            long u = l9 * o.l15;
            long q = 0;
            t = u + l10 * o.l14;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l13;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l12 * o.l12;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l13 * o.l11;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l14 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l15 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l8, o.l15);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l9, o.l14);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l10, o.l13);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l11, o.l12);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l12, o.l11);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l13, o.l10);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l14, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l15, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[8] = t;
            carry = q;
        }
        {
            long u = l10 * o.l15;
            long q = 0;
            t = u + l11 * o.l14;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l12 * o.l13;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l13 * o.l12;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l14 * o.l11;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l15 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l9, o.l15);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l10, o.l14);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l11, o.l13);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l12, o.l12);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l13, o.l11);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l14, o.l10);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l15, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[9] = t;
            carry = q;
        }
        {
            long u = l11 * o.l15;
            long q = 0;
            t = u + l12 * o.l14;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l13 * o.l13;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l14 * o.l12;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l15 * o.l11;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l10, o.l15);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l11, o.l14);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l12, o.l13);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l13, o.l12);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l14, o.l11);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l15, o.l10);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[10] = t;
            carry = q;
        }
        {
            long u = l12 * o.l15;
            long q = 0;
            t = u + l13 * o.l14;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l14 * o.l13;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l15 * o.l12;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l11, o.l15);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l12, o.l14);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l13, o.l13);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l14, o.l12);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l15, o.l11);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[11] = t;
            carry = q;
        }
        {
            long u = l13 * o.l15;
            long q = 0;
            t = u + l14 * o.l14;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l15 * o.l13;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l12, o.l15);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l13, o.l14);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l14, o.l13);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l15, o.l12);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[12] = t;
            carry = q;
        }
        {
            long u = l14 * o.l15;
            long q = 0;
            t = u + l15 * o.l14;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l13, o.l15);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l14, o.l14);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l15, o.l13);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = u + v;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            q += q2;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[13] = t;
            carry = q;
        }
        {
            long u = l15 * o.l15;
            long q = 0;
            t = u + Math.unsignedMultiplyHigh(l14, o.l15);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l15, o.l14);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[14] = t;
            carry = q;
        }
        {
            long u = Math.unsignedMultiplyHigh(l15, o.l15);
            long q = 0;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[15] = t;
            carry = q;
        }
        return carry;
    }

    /**
     * High N bits of the 2N-bit product, column-major with delayed carry. Columns below N
     * only feed the carry chain (their value is discarded); columns N..2N-1 are the result.
     * Generated by tools/gen-colmul.ps1 -- do not edit this method or the mulHighCols*
     * helpers by hand.
     */
    public Wide1024 mulHigh(Wide1024 o) {
        long[] r = new long[16];
        long carry = 0;
        carry = mulHighCols0(o, r, carry);
        carry = mulHighCols1(o, r, carry);
        carry = mulHighCols2(o, r, carry);
        return Wide1024.of(r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9], r[10], r[11], r[12], r[13], r[14], r[15]);
    }

    /**
     * High 1024 bits of the 2048-bit product, composed from 256-bit atom block
     * products written into a single scratch array (kept as the differential
     * oracle for {@link #mulHigh}).
     */
    Wide1024 mulHighComposed(Wide1024 o) {
        long[] s = new long[128];
        Wide256.mulFullInto8(s, 0, l0, l1, l2, l3, o.l0, o.l1, o.l2, o.l3);
        Wide256.mulFullInto8(s, 8, l0, l1, l2, l3, o.l4, o.l5, o.l6, o.l7);
        Wide256.mulFullInto8(s, 16, l4, l5, l6, l7, o.l0, o.l1, o.l2, o.l3);
        Wide256.mulFullInto8(s, 24, l0, l1, l2, l3, o.l8, o.l9, o.l10, o.l11);
        Wide256.mulFullInto8(s, 32, l4, l5, l6, l7, o.l4, o.l5, o.l6, o.l7);
        Wide256.mulFullInto8(s, 40, l8, l9, l10, l11, o.l0, o.l1, o.l2, o.l3);
        Wide256.mulFullInto8(s, 48, l0, l1, l2, l3, o.l12, o.l13, o.l14, o.l15);
        Wide256.mulFullInto8(s, 56, l4, l5, l6, l7, o.l8, o.l9, o.l10, o.l11);
        Wide256.mulFullInto8(s, 64, l8, l9, l10, l11, o.l4, o.l5, o.l6, o.l7);
        Wide256.mulFullInto8(s, 72, l12, l13, l14, l15, o.l0, o.l1, o.l2, o.l3);
        Wide256.mulFullInto8(s, 80, l4, l5, l6, l7, o.l12, o.l13, o.l14, o.l15);
        Wide256.mulFullInto8(s, 88, l8, l9, l10, l11, o.l8, o.l9, o.l10, o.l11);
        Wide256.mulFullInto8(s, 96, l12, l13, l14, l15, o.l4, o.l5, o.l6, o.l7);
        Wide256.mulFullInto8(s, 104, l8, l9, l10, l11, o.l12, o.l13, o.l14, o.l15);
        Wide256.mulFullInto8(s, 112, l12, l13, l14, l15, o.l8, o.l9, o.l10, o.l11);
        Wide256.mulFullInto8(s, 120, l12, l13, l14, l15, o.l12, o.l13, o.l14, o.l15);

        long carry = 0;
        for (int i = 4; i < 8; i++) {
            long u = s[i] + s[i + 4];
            long k = Carry.addCarryOut(s[i], s[i + 4], u);
            long v = u + s[i + 12];
            k += Carry.addCarryOut(u, s[i + 12], v);
            long w = v + carry;
            k += Carry.addCarryOut(v, carry, w);
            carry = k;
        }
        for (int i = 8; i < 12; i++) {
            long u1 = s[i + 4] + s[i + 12];
            long k = Carry.addCarryOut(s[i + 4], s[i + 12], u1);
            long u2 = u1 + s[i + 16];
            k += Carry.addCarryOut(u1, s[i + 16], u2);
            long u3 = u2 + s[i + 24];
            k += Carry.addCarryOut(u2, s[i + 24], u3);
            long u4 = u3 + s[i + 32];
            k += Carry.addCarryOut(u3, s[i + 32], u4);
            long w = u4 + carry;
            k += Carry.addCarryOut(u4, carry, w);
            carry = k;
        }
        for (int i = 12; i < 16; i++) {
            long u1 = s[i + 16] + s[i + 24];
            long k = Carry.addCarryOut(s[i + 16], s[i + 24], u1);
            long u2 = u1 + s[i + 32];
            k += Carry.addCarryOut(u1, s[i + 32], u2);
            long u3 = u2 + s[i + 36];
            k += Carry.addCarryOut(u2, s[i + 36], u3);
            long u4 = u3 + s[i + 44];
            k += Carry.addCarryOut(u3, s[i + 44], u4);
            long u5 = u4 + s[i + 52];
            k += Carry.addCarryOut(u4, s[i + 52], u5);
            long u6 = u5 + s[i + 60];
            k += Carry.addCarryOut(u5, s[i + 60], u6);
            long w = u6 + carry;
            k += Carry.addCarryOut(u6, carry, w);
            carry = k;
        }

        long[] r = new long[16];
        long c = carry;
        for (int j = 0; j < 4; j++) {
            long u1 = s[52 + j] + s[60 + j];
            long k = Carry.addCarryOut(s[52 + j], s[60 + j], u1);
            long u2 = u1 + s[68 + j];
            k += Carry.addCarryOut(u1, s[68 + j], u2);
            long u3 = u2 + s[76 + j];
            k += Carry.addCarryOut(u2, s[76 + j], u3);
            long u4 = u3 + s[80 + j];
            k += Carry.addCarryOut(u3, s[80 + j], u4);
            long u5 = u4 + s[88 + j];
            k += Carry.addCarryOut(u4, s[88 + j], u5);
            long u6 = u5 + s[96 + j];
            k += Carry.addCarryOut(u5, s[96 + j], u6);
            long w = u6 + c;
            k += Carry.addCarryOut(u6, c, w);
            r[j] = w;
            c = k;
        }
        for (int j = 0; j < 4; j++) {
            long u1 = s[84 + j] + s[92 + j];
            long k = Carry.addCarryOut(s[84 + j], s[92 + j], u1);
            long u2 = u1 + s[100 + j];
            k += Carry.addCarryOut(u1, s[100 + j], u2);
            long u3 = u2 + s[104 + j];
            k += Carry.addCarryOut(u2, s[104 + j], u3);
            long u4 = u3 + s[112 + j];
            k += Carry.addCarryOut(u3, s[112 + j], u4);
            long w = u4 + c;
            k += Carry.addCarryOut(u4, c, w);
            r[4 + j] = w;
            c = k;
        }
        for (int j = 0; j < 4; j++) {
            long u1 = s[108 + j] + s[116 + j];
            long k = Carry.addCarryOut(s[108 + j], s[116 + j], u1);
            long u2 = u1 + s[120 + j];
            k += Carry.addCarryOut(u1, s[120 + j], u2);
            long w = u2 + c;
            k += Carry.addCarryOut(u2, c, w);
            r[8 + j] = w;
            c = k;
        }
        for (int j = 0; j < 4; j++) {
            long w = s[124 + j] + c;
            r[12 + j] = w;
            c = Carry.addCarryOut(s[124 + j], c, w);
        }
        return Wide1024.of(r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7],
                r[8], r[9], r[10], r[11], r[12], r[13], r[14], r[15]);
    }

    public Wide1024 and(Wide1024 o) {
        return new Wide1024(l0 & o.l0, l1 & o.l1, l2 & o.l2, l3 & o.l3, l4 & o.l4, l5 & o.l5,
                l6 & o.l6, l7 & o.l7, l8 & o.l8, l9 & o.l9, l10 & o.l10, l11 & o.l11,
                l12 & o.l12, l13 & o.l13, l14 & o.l14, l15 & o.l15);
    }

    public Wide1024 or(Wide1024 o) {
        return new Wide1024(l0 | o.l0, l1 | o.l1, l2 | o.l2, l3 | o.l3, l4 | o.l4, l5 | o.l5,
                l6 | o.l6, l7 | o.l7, l8 | o.l8, l9 | o.l9, l10 | o.l10, l11 | o.l11,
                l12 | o.l12, l13 | o.l13, l14 | o.l14, l15 | o.l15);
    }

    public Wide1024 xor(Wide1024 o) {
        return new Wide1024(l0 ^ o.l0, l1 ^ o.l1, l2 ^ o.l2, l3 ^ o.l3, l4 ^ o.l4, l5 ^ o.l5,
                l6 ^ o.l6, l7 ^ o.l7, l8 ^ o.l8, l9 ^ o.l9, l10 ^ o.l10, l11 ^ o.l11,
                l12 ^ o.l12, l13 ^ o.l13, l14 ^ o.l14, l15 ^ o.l15);
    }

    public Wide1024 not() {
        return new Wide1024(~l0, ~l1, ~l2, ~l3, ~l4, ~l5, ~l6, ~l7, ~l8, ~l9, ~l10, ~l11,
                ~l12, ~l13, ~l14, ~l15);
    }

    public Wide1024 andNot(Wide1024 o) {
        return new Wide1024(l0 & ~o.l0, l1 & ~o.l1, l2 & ~o.l2, l3 & ~o.l3, l4 & ~o.l4, l5 & ~o.l5,
                l6 & ~o.l6, l7 & ~o.l7, l8 & ~o.l8, l9 & ~o.l9, l10 & ~o.l10, l11 & ~o.l11,
                l12 & ~o.l12, l13 & ~o.l13, l14 & ~o.l14, l15 & ~o.l15);
    }

    /**
     * Logical left shift by {@code k} bits; bits shifted out are discarded.
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 1024)}
     */
    public Wide1024 shl(int k) {
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
        long b8;
        long b9;
        long b10;
        long b11;
        long b12;
        long b13;
        long b14;
        long b15;
        if (bs == 0) {
            b0 = l0;
            b1 = l1;
            b2 = l2;
            b3 = l3;
            b4 = l4;
            b5 = l5;
            b6 = l6;
            b7 = l7;
            b8 = l8;
            b9 = l9;
            b10 = l10;
            b11 = l11;
            b12 = l12;
            b13 = l13;
            b14 = l14;
            b15 = l15;
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
            b8 = (l8 << bs) | (l7 >>> inv);
            b9 = (l9 << bs) | (l8 >>> inv);
            b10 = (l10 << bs) | (l9 >>> inv);
            b11 = (l11 << bs) | (l10 >>> inv);
            b12 = (l12 << bs) | (l11 >>> inv);
            b13 = (l13 << bs) | (l12 >>> inv);
            b14 = (l14 << bs) | (l13 >>> inv);
            b15 = (l15 << bs) | (l14 >>> inv);
        }
        return switch (k >>> 6) {
            case 0 -> new Wide1024(b0, b1, b2, b3, b4, b5, b6, b7, b8, b9, b10, b11, b12, b13, b14, b15);
            case 1 -> new Wide1024(0, b0, b1, b2, b3, b4, b5, b6, b7, b8, b9, b10, b11, b12, b13, b14);
            case 2 -> new Wide1024(0, 0, b0, b1, b2, b3, b4, b5, b6, b7, b8, b9, b10, b11, b12, b13);
            case 3 -> new Wide1024(0, 0, 0, b0, b1, b2, b3, b4, b5, b6, b7, b8, b9, b10, b11, b12);
            case 4 -> new Wide1024(0, 0, 0, 0, b0, b1, b2, b3, b4, b5, b6, b7, b8, b9, b10, b11);
            case 5 -> new Wide1024(0, 0, 0, 0, 0, b0, b1, b2, b3, b4, b5, b6, b7, b8, b9, b10);
            case 6 -> new Wide1024(0, 0, 0, 0, 0, 0, b0, b1, b2, b3, b4, b5, b6, b7, b8, b9);
            case 7 -> new Wide1024(0, 0, 0, 0, 0, 0, 0, b0, b1, b2, b3, b4, b5, b6, b7, b8);
            case 8 -> new Wide1024(0, 0, 0, 0, 0, 0, 0, 0, b0, b1, b2, b3, b4, b5, b6, b7);
            case 9 -> new Wide1024(0, 0, 0, 0, 0, 0, 0, 0, 0, b0, b1, b2, b3, b4, b5, b6);
            case 10 -> new Wide1024(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, b0, b1, b2, b3, b4, b5);
            case 11 -> new Wide1024(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, b0, b1, b2, b3, b4);
            case 12 -> new Wide1024(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, b0, b1, b2, b3);
            case 13 -> new Wide1024(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, b0, b1, b2);
            case 14 -> new Wide1024(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, b0, b1);
            default -> new Wide1024(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, b0);
        };
    }

    /**
     * Logical right shift by {@code k} bits (zero fill).
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 1024)}
     */
    public Wide1024 ushr(int k) {
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
        long b8;
        long b9;
        long b10;
        long b11;
        long b12;
        long b13;
        long b14;
        long b15;
        if (bs == 0) {
            b0 = l0;
            b1 = l1;
            b2 = l2;
            b3 = l3;
            b4 = l4;
            b5 = l5;
            b6 = l6;
            b7 = l7;
            b8 = l8;
            b9 = l9;
            b10 = l10;
            b11 = l11;
            b12 = l12;
            b13 = l13;
            b14 = l14;
            b15 = l15;
        } else {
            long inv = 64 - bs;
            b0 = (l0 >>> bs) | (l1 << inv);
            b1 = (l1 >>> bs) | (l2 << inv);
            b2 = (l2 >>> bs) | (l3 << inv);
            b3 = (l3 >>> bs) | (l4 << inv);
            b4 = (l4 >>> bs) | (l5 << inv);
            b5 = (l5 >>> bs) | (l6 << inv);
            b6 = (l6 >>> bs) | (l7 << inv);
            b7 = (l7 >>> bs) | (l8 << inv);
            b8 = (l8 >>> bs) | (l9 << inv);
            b9 = (l9 >>> bs) | (l10 << inv);
            b10 = (l10 >>> bs) | (l11 << inv);
            b11 = (l11 >>> bs) | (l12 << inv);
            b12 = (l12 >>> bs) | (l13 << inv);
            b13 = (l13 >>> bs) | (l14 << inv);
            b14 = (l14 >>> bs) | (l15 << inv);
            b15 = l15 >>> bs;
        }
        return switch (k >>> 6) {
            case 0 -> new Wide1024(b0, b1, b2, b3, b4, b5, b6, b7, b8, b9, b10, b11, b12, b13, b14, b15);
            case 1 -> new Wide1024(b1, b2, b3, b4, b5, b6, b7, b8, b9, b10, b11, b12, b13, b14, b15, 0);
            case 2 -> new Wide1024(b2, b3, b4, b5, b6, b7, b8, b9, b10, b11, b12, b13, b14, b15, 0, 0);
            case 3 -> new Wide1024(b3, b4, b5, b6, b7, b8, b9, b10, b11, b12, b13, b14, b15, 0, 0, 0);
            case 4 -> new Wide1024(b4, b5, b6, b7, b8, b9, b10, b11, b12, b13, b14, b15, 0, 0, 0, 0);
            case 5 -> new Wide1024(b5, b6, b7, b8, b9, b10, b11, b12, b13, b14, b15, 0, 0, 0, 0, 0);
            case 6 -> new Wide1024(b6, b7, b8, b9, b10, b11, b12, b13, b14, b15, 0, 0, 0, 0, 0, 0);
            case 7 -> new Wide1024(b7, b8, b9, b10, b11, b12, b13, b14, b15, 0, 0, 0, 0, 0, 0, 0);
            case 8 -> new Wide1024(b8, b9, b10, b11, b12, b13, b14, b15, 0, 0, 0, 0, 0, 0, 0, 0);
            case 9 -> new Wide1024(b9, b10, b11, b12, b13, b14, b15, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case 10 -> new Wide1024(b10, b11, b12, b13, b14, b15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case 11 -> new Wide1024(b11, b12, b13, b14, b15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case 12 -> new Wide1024(b12, b13, b14, b15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case 13 -> new Wide1024(b13, b14, b15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case 14 -> new Wide1024(b14, b15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            default -> new Wide1024(b15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        };
    }

    /**
     * Arithmetic right shift by {@code k} bits (sign-extending).
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 1024)}
     */
    public Wide1024 shr(int k) {
        checkShift(k);
        if (k == 0) {
            return this;
        }
        Wide1024 u = ushr(k);
        if (l15 >> 63 == 0) {
            return u;
        }
        return u.or(MAX_VALUE.shl(1024 - k));
    }

    public int compareUnsigned(Wide1024 o) {
        int c = Long.compareUnsigned(l15, o.l15);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l14, o.l14);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l13, o.l13);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l12, o.l12);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l11, o.l11);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l10, o.l10);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l9, o.l9);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l8, o.l8);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l7, o.l7);
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

    public int compareSigned(Wide1024 o) {
        int c = Long.compare(l15, o.l15);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l14, o.l14);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l13, o.l13);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l12, o.l12);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l11, o.l11);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l10, o.l10);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l9, o.l9);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l8, o.l8);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l7, o.l7);
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
