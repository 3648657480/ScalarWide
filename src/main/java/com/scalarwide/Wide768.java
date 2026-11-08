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

    /** Memoised hash; 0 means not computed yet (see computeHash). */
    private int hash;

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

    /**
     * Unsigned division: `this / o`, truncated.
     *
     * @throws ArithmeticException if `o` is zero
     */
    public Wide768 div(Wide768 o) {
        long[] q = new long[LIMBS];
        Limbs.divmod(pack(), o.pack(), q, null);
        return of(q[0], q[1], q[2], q[3],
                q[4], q[5], q[6], q[7],
                q[8], q[9], q[10], q[11]);
    }

    /**
     * Unsigned remainder: `this % o`.
     *
     * @throws ArithmeticException if `o` is zero
     */
    public Wide768 rem(Wide768 o) {
        long[] q = new long[LIMBS];
        long[] r = new long[LIMBS];
        Limbs.divmod(pack(), o.pack(), q, r);
        return of(r[0], r[1], r[2], r[3],
                r[4], r[5], r[6], r[7],
                r[8], r[9], r[10], r[11]);
    }

    /**
     * Multiplicative power modulo 2^BITS (square-and-multiply).
     *
     * @throws IllegalArgumentException if `exp` is negative
     */
    public Wide768 pow(int exp) {
        if (exp < 0) {
            throw new IllegalArgumentException("negative exponent: " + exp);
        }
        Wide768 result = ONE;
        Wide768 base = this;
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
                + Long.bitCount(l10) + Long.bitCount(l11);
    }

    /** Unsigned minimum (consistent with {@link #compareUnsigned}). */
    public Wide768 min(Wide768 o) {
        return compareUnsigned(o) <= 0 ? this : o;
    }

    /** Unsigned maximum (consistent with {@link #compareUnsigned}). */
    public Wide768 max(Wide768 o) {
        return compareUnsigned(o) >= 0 ? this : o;
    }

    /** Two's-complement absolute value; MIN_VALUE wraps to itself. */
    public Wide768 abs() {
        return l11 < 0 ? neg() : this;
    }
    /** Unsigned big-endian minimal byte array (zero returns a single 0x00 byte). */
    public byte[] toByteArray() {
        int bits = bitLength();
        if (bits == 0) {
            return new byte[] {0};
        }
        int n = (bits + 7) >>> 3;
        byte[] out = new byte[n];
        int rem = n & 7;
        if (n >= 8) {
            int pos = n;
            pos -= 8; Limbs.BE_LONG.set(out, pos, l0);
            if (pos >= rem + 8) { pos -= 8; Limbs.BE_LONG.set(out, pos, l1); }
            if (pos >= rem + 8) { pos -= 8; Limbs.BE_LONG.set(out, pos, l2); }
            if (pos >= rem + 8) { pos -= 8; Limbs.BE_LONG.set(out, pos, l3); }
            if (pos >= rem + 8) { pos -= 8; Limbs.BE_LONG.set(out, pos, l4); }
            if (pos >= rem + 8) { pos -= 8; Limbs.BE_LONG.set(out, pos, l5); }
            if (pos >= rem + 8) { pos -= 8; Limbs.BE_LONG.set(out, pos, l6); }
            if (pos >= rem + 8) { pos -= 8; Limbs.BE_LONG.set(out, pos, l7); }
            if (pos >= rem + 8) { pos -= 8; Limbs.BE_LONG.set(out, pos, l8); }
            if (pos >= rem + 8) { pos -= 8; Limbs.BE_LONG.set(out, pos, l9); }
            if (pos >= rem + 8) { pos -= 8; Limbs.BE_LONG.set(out, pos, l10); }
            if (pos >= rem + 8) { pos -= 8; Limbs.BE_LONG.set(out, pos, l11); }
        }
        if (rem != 0) {
            long top = limb(n >> 3);
            for (int j = 0; j < rem; j++) {
                out[j] = (byte) (top >>> (8 * (rem - 1 - j)));
            }
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
        if (pos + 8 <= n) { Limbs.LE_LONG.set(out, pos, l0); pos += 8; }
        if (pos + 8 <= n) { Limbs.LE_LONG.set(out, pos, l1); pos += 8; }
        if (pos + 8 <= n) { Limbs.LE_LONG.set(out, pos, l2); pos += 8; }
        if (pos + 8 <= n) { Limbs.LE_LONG.set(out, pos, l3); pos += 8; }
        if (pos + 8 <= n) { Limbs.LE_LONG.set(out, pos, l4); pos += 8; }
        if (pos + 8 <= n) { Limbs.LE_LONG.set(out, pos, l5); pos += 8; }
        if (pos + 8 <= n) { Limbs.LE_LONG.set(out, pos, l6); pos += 8; }
        if (pos + 8 <= n) { Limbs.LE_LONG.set(out, pos, l7); pos += 8; }
        if (pos + 8 <= n) { Limbs.LE_LONG.set(out, pos, l8); pos += 8; }
        if (pos + 8 <= n) { Limbs.LE_LONG.set(out, pos, l9); pos += 8; }
        if (pos + 8 <= n) { Limbs.LE_LONG.set(out, pos, l10); pos += 8; }
        if (pos + 8 <= n) { Limbs.LE_LONG.set(out, pos, l11); pos += 8; }
        if (pos < n) {
            long top = limb(n >> 3);
            for (int j = 0; pos + j < n; j++) {
                out[pos + j] = (byte) (top >>> (j << 3));
            }
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
        return out;
    }

    /**
     * Unsigned big-endian decode; leading zero padding is tolerated (so
     * BigInteger.toByteArray output with a sign byte round-trips).
     *
     * @throws IllegalArgumentException if the significant length exceeds BITS/8
     */
    public static Wide768 fromByteArray(byte[] src, int off, int len) {
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
        return of(out[0], out[1], out[2], out[3], out[4], out[5], out[6], out[7], out[8], out[9], out[10], out[11]);
    }

    /** @see #fromByteArray(byte[], int, int) */
    public static Wide768 fromByteArray(byte[] src) {
        return fromByteArray(src, 0, src.length);
    }

    /** Unsigned little-endian decode. */
    public static Wide768 fromByteArrayLE(byte[] src, int off, int len) {
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
        return of(out[0], out[1], out[2], out[3], out[4], out[5], out[6], out[7], out[8], out[9], out[10], out[11]);
    }

    /** @see #fromByteArrayLE(byte[], int, int) */
    public static Wide768 fromByteArrayLE(byte[] src) {
        return fromByteArrayLE(src, 0, src.length);
    }
    public int bitLength() {
        long v = l11;
        if (v != 0) {
            return 704 + (64 - Long.numberOfLeadingZeros(v));
        }
        return bitLengthTail();
    }

    /** Lower limbs of {@link #bitLength}; kept out of line so the fast path stays inlinable. */
    private int bitLengthTail() {
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
        return l0 != 0 ? 64 - Long.numberOfLeadingZeros(l0) : 0;
    }

    /** Wrapping addition modulo 2^768. */
    public Wide768 add(Wide768 o) {
        long r0 = l0 + o.l0;
        long c = Carry.carryOut(l0, r0);
        long r1 = l1 + o.l1 + c;
        c = Long.compareUnsigned(r1, l1) < 0 || (c != 0 && r1 == l1) ? 1L : 0L;
        long r2 = l2 + o.l2 + c;
        c = Long.compareUnsigned(r2, l2) < 0 || (c != 0 && r2 == l2) ? 1L : 0L;
        long r3 = l3 + o.l3 + c;
        c = Long.compareUnsigned(r3, l3) < 0 || (c != 0 && r3 == l3) ? 1L : 0L;
        long r4 = l4 + o.l4 + c;
        c = Long.compareUnsigned(r4, l4) < 0 || (c != 0 && r4 == l4) ? 1L : 0L;
        long r5 = l5 + o.l5 + c;
        c = Long.compareUnsigned(r5, l5) < 0 || (c != 0 && r5 == l5) ? 1L : 0L;
        long r6 = l6 + o.l6 + c;
        c = Long.compareUnsigned(r6, l6) < 0 || (c != 0 && r6 == l6) ? 1L : 0L;
        long r7 = l7 + o.l7 + c;
        c = Long.compareUnsigned(r7, l7) < 0 || (c != 0 && r7 == l7) ? 1L : 0L;
        long r8 = l8 + o.l8 + c;
        c = Long.compareUnsigned(r8, l8) < 0 || (c != 0 && r8 == l8) ? 1L : 0L;
        long r9 = l9 + o.l9 + c;
        c = Long.compareUnsigned(r9, l9) < 0 || (c != 0 && r9 == l9) ? 1L : 0L;
        long r10 = l10 + o.l10 + c;
        c = Long.compareUnsigned(r10, l10) < 0 || (c != 0 && r10 == l10) ? 1L : 0L;
        long s11 = l11 + o.l11;
        long r11 = s11 + c;
        return new Wide768(r0, r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11);
    }

    /** Wrapping subtraction modulo 2^768. */
    public Wide768 sub(Wide768 o) {
        long r0 = l0 - o.l0;
        long b = Carry.borrowOut(l0, r0);
        long r1 = l1 - o.l1 - b;
        b = Long.compareUnsigned(r1, l1) > 0 || (b != 0 && r1 == l1) ? 1L : 0L;
        long r2 = l2 - o.l2 - b;
        b = Long.compareUnsigned(r2, l2) > 0 || (b != 0 && r2 == l2) ? 1L : 0L;
        long r3 = l3 - o.l3 - b;
        b = Long.compareUnsigned(r3, l3) > 0 || (b != 0 && r3 == l3) ? 1L : 0L;
        long r4 = l4 - o.l4 - b;
        b = Long.compareUnsigned(r4, l4) > 0 || (b != 0 && r4 == l4) ? 1L : 0L;
        long r5 = l5 - o.l5 - b;
        b = Long.compareUnsigned(r5, l5) > 0 || (b != 0 && r5 == l5) ? 1L : 0L;
        long r6 = l6 - o.l6 - b;
        b = Long.compareUnsigned(r6, l6) > 0 || (b != 0 && r6 == l6) ? 1L : 0L;
        long r7 = l7 - o.l7 - b;
        b = Long.compareUnsigned(r7, l7) > 0 || (b != 0 && r7 == l7) ? 1L : 0L;
        long r8 = l8 - o.l8 - b;
        b = Long.compareUnsigned(r8, l8) > 0 || (b != 0 && r8 == l8) ? 1L : 0L;
        long r9 = l9 - o.l9 - b;
        b = Long.compareUnsigned(r9, l9) > 0 || (b != 0 && r9 == l9) ? 1L : 0L;
        long r10 = l10 - o.l10 - b;
        b = Long.compareUnsigned(r10, l10) > 0 || (b != 0 && r10 == l10) ? 1L : 0L;
        long d11 = l11 - o.l11;
        long r11 = d11 - b;
        return new Wide768(r0, r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11);
    }

    /** Wrapping negation modulo 2^768. */
    public Wide768 neg() {
        return ZERO.sub(this);
    }

    public Wide768 and(Wide768 o) {
        return new Wide768(l0 & o.l0, l1 & o.l1, l2 & o.l2, l3 & o.l3, l4 & o.l4, l5 & o.l5,
                l6 & o.l6, l7 & o.l7, l8 & o.l8, l9 & o.l9, l10 & o.l10, l11 & o.l11);
    }

    public Wide768 or(Wide768 o) {
        return new Wide768(l0 | o.l0, l1 | o.l1, l2 | o.l2, l3 | o.l3, l4 | o.l4, l5 | o.l5,
                l6 | o.l6, l7 | o.l7, l8 | o.l8, l9 | o.l9, l10 | o.l10, l11 | o.l11);
    }

    public Wide768 xor(Wide768 o) {
        return new Wide768(l0 ^ o.l0, l1 ^ o.l1, l2 ^ o.l2, l3 ^ o.l3, l4 ^ o.l4, l5 ^ o.l5,
                l6 ^ o.l6, l7 ^ o.l7, l8 ^ o.l8, l9 ^ o.l9, l10 ^ o.l10, l11 ^ o.l11);
    }

    public Wide768 not() {
        return new Wide768(~l0, ~l1, ~l2, ~l3, ~l4, ~l5, ~l6, ~l7, ~l8, ~l9, ~l10, ~l11);
    }

    public Wide768 andNot(Wide768 o) {
        return new Wide768(l0 & ~o.l0, l1 & ~o.l1, l2 & ~o.l2, l3 & ~o.l3, l4 & ~o.l4, l5 & ~o.l5,
                l6 & ~o.l6, l7 & ~o.l7, l8 & ~o.l8, l9 & ~o.l9, l10 & ~o.l10, l11 & ~o.l11);
    }

    /**
     * Low 768 bits of the 1536-bit product, i.e. {@code (a*b) mod 2^768},
     * composed from 256-bit atom block products written into a single scratch
     * array (zero intermediate objects; limb-level assembly).
     */
    /**
     * Low N bits of the 2N-bit product, column-major with delayed carry: every output limb
     * is accumulated by its own pair of registers (running sum + overflow count) and
     * written exactly once, so no product round-trips through memory. Generated by
     * tools/gen-colmul.ps1 -- do not edit this method or the mulCols* helpers by hand.
     */
    /**
     * Low N bits of the 2N-bit product, column-major with delayed carry: every output limb
     * is accumulated by its own pair of registers (running sum + overflow count) and
     * written exactly once, so no product round-trips through memory. Generated by
     * tools/gen-colmul.ps1 -- do not edit this method or the mulCols* helpers by hand.
     */
    /**
     * Low N bits of the 2N-bit product, column-major with delayed carry: every output limb
     * is accumulated by its own pair of registers (running sum + overflow count) and
     * written exactly once, so no product round-trips through memory. Generated by
     * tools/gen-colmul.ps1 -- do not edit this method or the mulCols* helpers by hand.
     */
    /**
     * Low N bits of the 2N-bit product, column-major with delayed carry: every output limb
     * is accumulated by its own pair of registers (running sum + overflow count) and
     * written exactly once, so no product round-trips through memory. Generated by
     * tools/gen-colmul.ps1 -- do not edit this method or the mulCols* helpers by hand.
     */
    /**
     * Low N bits of the 2N-bit product, column-major with delayed carry: every output limb
     * is accumulated by its own pair of registers (running sum + overflow count) and
     * written exactly once, so no product round-trips through memory. Generated by
     * tools/gen-colmul.ps1 -- do not edit this method or the mulCols* helpers by hand.
     */
    /**
     * Low N bits of the 2N-bit product, column-major with delayed carry: every output limb
     * is accumulated by its own pair of registers (running sum + overflow count) and
     * written exactly once, so no product round-trips through memory. Generated by
     * tools/gen-colmul.ps1 -- do not edit this method or the mulCols* helpers by hand.
     */
    /**
     * Low N bits of the 2N-bit product, column-major with delayed carry: every output limb
     * is accumulated by its own pair of registers (running sum + overflow count) and
     * written exactly once, so no product round-trips through memory. Generated by
     * tools/gen-colmul.ps1 -- do not edit this method or the mulCols* helpers by hand.
     */
    /**
     * Low N bits of the 2N-bit product, column-major with delayed carry: every output limb
     * is accumulated by its own pair of registers (running sum + overflow count) and
     * written exactly once, so no product round-trips through memory. Generated by
     * tools/gen-colmul.ps1 -- do not edit this method or the mulCols* helpers by hand.
     */
    private long mulCols0(Wide768 o, long[] r, long carry) {
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
        return carry;
    }

    private long mulCols1(Wide768 o, long[] r, long carry) {
        long t;
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
        return carry;
    }

    /**
     * Low N bits of the 2N-bit product, column-major with delayed carry: every output limb
     * is accumulated by its own pair of registers (running sum + overflow count) and
     * written exactly once, so no product round-trips through memory. Generated by
     * tools/gen-colmul.ps1 -- do not edit this method or the mulCols* helpers by hand.
     */
    public Wide768 mul(Wide768 o) {
        long[] r = new long[12];
        long carry = 0;
        carry = mulCols0(o, r, carry);
        carry = mulCols1(o, r, carry);
        return Wide768.of(r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9], r[10], r[11]);
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

    /**
     * High 768 bits of the 1536-bit product, i.e. {@code (a*b) >>> 768},
     * composed from 256-bit atom block products written into a single scratch
     * array (zero intermediate objects; limb-level assembly).
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
    private long mulHighCols0(Wide768 o, long[] r, long carry) {
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
            long u = l1 * o.l11;
            long q = 0;
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
            t = u + Math.unsignedMultiplyHigh(l0, o.l11);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l1, o.l10);
            long q2 = 0;
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
            r[0] = t;
            carry = q;
        }
        return carry;
    }

    private long mulHighCols1(Wide768 o, long[] r, long carry) {
        long t;
        {
            long u = l2 * o.l11;
            long q = 0;
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
            t = u + Math.unsignedMultiplyHigh(l1, o.l11);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l2, o.l10);
            long q2 = 0;
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
            long u = l3 * o.l11;
            long q = 0;
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
            t = u + Math.unsignedMultiplyHigh(l2, o.l11);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l3, o.l10);
            long q2 = 0;
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
            long u = l4 * o.l11;
            long q = 0;
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
            t = u + Math.unsignedMultiplyHigh(l3, o.l11);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l4, o.l10);
            long q2 = 0;
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
            long u = l5 * o.l11;
            long q = 0;
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
            t = u + Math.unsignedMultiplyHigh(l4, o.l11);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l5, o.l10);
            long q2 = 0;
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
            long u = l6 * o.l11;
            long q = 0;
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
            t = u + Math.unsignedMultiplyHigh(l5, o.l11);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l6, o.l10);
            long q2 = 0;
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
            long u = l7 * o.l11;
            long q = 0;
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
            t = u + Math.unsignedMultiplyHigh(l6, o.l11);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l7, o.l10);
            long q2 = 0;
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
            long u = l8 * o.l11;
            long q = 0;
            t = u + l9 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l10 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l8;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l7, o.l11);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l8, o.l10);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l9, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l10, o.l8);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l11, o.l7);
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
            long u = l9 * o.l11;
            long q = 0;
            t = u + l10 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + l11 * o.l9;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l8, o.l11);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l9, o.l10);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l10, o.l9);
            q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;
            v = t;
            t = v + Math.unsignedMultiplyHigh(l11, o.l8);
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
            long u = l10 * o.l11;
            long q = 0;
            t = u + l11 * o.l10;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l9, o.l11);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            long v = Math.unsignedMultiplyHigh(l10, o.l10);
            long q2 = 0;
            t = v + Math.unsignedMultiplyHigh(l11, o.l9);
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
            long u = l11 * o.l11;
            long q = 0;
            t = u + Math.unsignedMultiplyHigh(l10, o.l11);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + Math.unsignedMultiplyHigh(l11, o.l10);
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            u = t;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[10] = t;
            carry = q;
        }
        {
            long u = Math.unsignedMultiplyHigh(l11, o.l11);
            long q = 0;
            t = u + carry;
            q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;
            r[11] = t;
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
    public Wide768 mulHigh(Wide768 o) {
        long[] r = new long[12];
        long carry = 0;
        carry = mulHighCols0(o, r, carry);
        carry = mulHighCols1(o, r, carry);
        return Wide768.of(r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9], r[10], r[11]);
    }

    /**
     * Logical left shift by {@code k} bits; bits shifted out are discarded.
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 768)}
     */
    public Wide768 shl(int k) {
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
        }
        return switch (k >>> 6) {
            case 0 -> new Wide768(b0, b1, b2, b3, b4, b5, b6, b7, b8, b9, b10, b11);
            case 1 -> new Wide768(0, b0, b1, b2, b3, b4, b5, b6, b7, b8, b9, b10);
            case 2 -> new Wide768(0, 0, b0, b1, b2, b3, b4, b5, b6, b7, b8, b9);
            case 3 -> new Wide768(0, 0, 0, b0, b1, b2, b3, b4, b5, b6, b7, b8);
            case 4 -> new Wide768(0, 0, 0, 0, b0, b1, b2, b3, b4, b5, b6, b7);
            case 5 -> new Wide768(0, 0, 0, 0, 0, b0, b1, b2, b3, b4, b5, b6);
            case 6 -> new Wide768(0, 0, 0, 0, 0, 0, b0, b1, b2, b3, b4, b5);
            case 7 -> new Wide768(0, 0, 0, 0, 0, 0, 0, b0, b1, b2, b3, b4);
            case 8 -> new Wide768(0, 0, 0, 0, 0, 0, 0, 0, b0, b1, b2, b3);
            case 9 -> new Wide768(0, 0, 0, 0, 0, 0, 0, 0, 0, b0, b1, b2);
            case 10 -> new Wide768(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, b0, b1);
            default -> new Wide768(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, b0);
        };
    }

    /**
     * Logical right shift by {@code k} bits (zero fill).
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 768)}
     */
    public Wide768 ushr(int k) {
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
            b11 = l11 >>> bs;
        }
        return switch (k >>> 6) {
            case 0 -> new Wide768(b0, b1, b2, b3, b4, b5, b6, b7, b8, b9, b10, b11);
            case 1 -> new Wide768(b1, b2, b3, b4, b5, b6, b7, b8, b9, b10, b11, 0);
            case 2 -> new Wide768(b2, b3, b4, b5, b6, b7, b8, b9, b10, b11, 0, 0);
            case 3 -> new Wide768(b3, b4, b5, b6, b7, b8, b9, b10, b11, 0, 0, 0);
            case 4 -> new Wide768(b4, b5, b6, b7, b8, b9, b10, b11, 0, 0, 0, 0);
            case 5 -> new Wide768(b5, b6, b7, b8, b9, b10, b11, 0, 0, 0, 0, 0);
            case 6 -> new Wide768(b6, b7, b8, b9, b10, b11, 0, 0, 0, 0, 0, 0);
            case 7 -> new Wide768(b7, b8, b9, b10, b11, 0, 0, 0, 0, 0, 0, 0);
            case 8 -> new Wide768(b8, b9, b10, b11, 0, 0, 0, 0, 0, 0, 0, 0);
            case 9 -> new Wide768(b9, b10, b11, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case 10 -> new Wide768(b10, b11, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            default -> new Wide768(b11, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        };
    }

    /**
     * Arithmetic right shift by {@code k} bits (sign-extending).
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 768)}
     */
    public Wide768 shr(int k) {
        checkShift(k);
        if (k == 0) {
            return this;
        }
        Wide768 u = ushr(k);
        if (l11 >> 63 == 0) {
            return u;
        }
        return u.or(MAX_VALUE.shl(768 - k));
    }

    public int compareUnsigned(Wide768 o) {
        int c = Long.compareUnsigned(l11, o.l11);
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

    public int compareSigned(Wide768 o) {
        int c = Long.compare(l11, o.l11);
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
        int h = hash;
        if (h == 0) {
            h = computeHash();
            hash = h;
        }
        return h;
    }

    /**
     * Twelve multiply steps, throughput-bound at roughly 1.4 cycles per limb; memoising is
     * the only lever (see Wide1024#computeHash). Filled lazily so arithmetic use pays nothing;
     * the racing write is benign because both threads compute the same value.
     */
    private int computeHash() {
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
        int r = (int) (h ^ (h >>> 32));
        return r == 0 ? 1 : r;
    }

    /** Unsigned decimal representation. */
    @Override
    public String toString() {
        return toBigInteger().toString();
    }

    /** Unsigned hexadecimal representation without prefix ({@code "0"} for zero). */
    public String toHexString() {
        return Limbs.toHexString(pack());
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
