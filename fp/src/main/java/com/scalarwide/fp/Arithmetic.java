package com.scalarwide.fp;

import com.scalarwide.Limbs;
import com.scalarwide.Wide1024;
import com.scalarwide.Wide512;

/**
 * The single rounding point. {@link Exact} produces a value exact to within one ULP together
 * with the sign of the remaining tail; {@code pack} is the only place that rounds.
 */
final class Arithmetic {

    private static final Wide1024 ONE = Wide1024.of(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    private static final Wide1024 ZERO = Wide1024.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    /** Frame width in limbs for the 512-bit path: every value except an fma product fits. */
    private static final int FRAME512 = 8;
    /** Frame width in limbs for the fma path: an exact 2p-bit product plus an aligned addend. */
    private static final int FRAME1024 = 16;

    /** bitLength of a fixed 8-limb frame. */
    private static int bitLength8(long[] m) {
        for (int i = 7; i >= 0; i--) {
            if (m[i] != 0L) {
                return i * 64 + 64 - Long.numberOfLeadingZeros(m[i]);
            }
        }
        return 0;
    }

    private static long bitAt8(long[] m, int i) {
        return (i < 0 || i >= 512) ? 0L : (m[i >> 6] >>> (i & 63)) & 1L;
    }

    /** Any bit strictly below {@code i} (i.e. in {@code [0, i-1]}). */
    private static boolean anyBelow8(long[] m, int i) {
        if (i <= 0) {
            return false;
        }
        int li = i >> 6;
        for (int j = 0; j < li && j < 8; j++) {
            if (m[j] != 0L) {
                return true;
            }
        }
        if (li >= 8) {
            return false;
        }
        int bi = i & 63;
        long mask = bi == 0 ? 0L : (bi == 64 ? -1L : (1L << bi) - 1L);
        return (m[li] & mask) != 0L;
    }

    private static void shl8(long[] a, int k) {
        int ls = k >>> 6;
        int bs = k & 63;
        for (int i = 7; i >= 0; i--) {
            int src = i - ls;
            long v = 0L;
            if (src >= 0) {
                v = a[src] << bs;
                if (bs != 0 && src - 1 >= 0) {
                    v |= a[src - 1] >>> (64 - bs);
                }
            }
            a[i] = v;
        }
    }

    private static void shr8(long[] a, int k, long[] out) {
        int ls = k >>> 6;
        int bs = k & 63;
        for (int i = 0; i < 8; i++) {
            int src = i + ls;
            long v = 0L;
            if (src < 8) {
                v = a[src] >>> bs;
                if (bs != 0 && src + 1 < 8) {
                    v |= a[src + 1] << (64 - bs);
                }
            }
            out[i] = v;
        }
    }

    /** {@code a + b} through the 512-bit frame, without the Exact/Wide object pipeline. */
    static Binary256 addFramed(Binary256 a, Binary256 b) {
        Binary256 big = a.scaleExponent() >= b.scaleExponent() ? a : b;
        Binary256 small = big == a ? b : a;
        int diff = big.scaleExponent() - small.scaleExponent();
        if (diff > P || big.signBit() != small.signBit()) {
            return addFramedSlow(a, b, big, small, diff);
        }
        long[] f = new long[FRAME512];
        f[0] = big.limb(0);
        f[1] = big.limb(1);
        f[2] = big.limb(2);
        f[3] = big.limb(3);
        if (diff != 0) {
            shl8(f, diff);
        }
        addInto(f, small.limb(0), small.limb(1), small.limb(2), small.limb(3));
        return pack512(big.signBit() ? 1 : 0, small.scaleExponent(), f, false, false);
    }

    /** Cold half of {@link #addFramed}: opposite signs or an exponent gap wider than the format. */
    private static Binary256 addFramedSlow(Binary256 a, Binary256 b, Binary256 big, Binary256 small,
                                           int diff) {
        if (diff > P) {
            long[] f = new long[FRAME512];
            f[0] = big.limb(0);
            f[1] = big.limb(1);
            f[2] = big.limb(2);
            f[3] = big.limb(3);
            return pack512(big.signBit() ? 1 : 0, big.scaleExponent(), f, true,
                    big.signBit() != small.signBit());
        }
        long[] f = new long[FRAME512];
        f[0] = big.limb(0);
        f[1] = big.limb(1);
        f[2] = big.limb(2);
        f[3] = big.limb(3);
        if (diff != 0) {
            shl8(f, diff);
        }
        long b0 = small.limb(0);
        long b1 = small.limb(1);
        long b2 = small.limb(2);
        long b3 = small.limb(3);
        int cmp = cmpShifted(f, b0, b1, b2, b3);
        if (cmp == 0) {
            return Binary256.ZERO;
        }
        if (cmp > 0) {
            subFrom4(f, b0, b1, b2, b3);
            return pack512(big.signBit() ? 1 : 0, small.scaleExponent(), f, false, false);
        }
        subFromFrame(f, b0, b1, b2, b3);
        return pack512(small.signBit() ? 1 : 0, small.scaleExponent(), f, false, false);
    }
    private static void addInto(long[] f, long b0, long b1, long b2, long b3) {
        long c = 0L;
        long s = f[0] + b0;
        c = Long.compareUnsigned(s, f[0]) < 0 ? 1L : 0L;
        f[0] = s;
        s = f[1] + b1 + c;
        c = (Long.compareUnsigned(s, b1) < 0 || (c != 0 && s == b1)) ? 1L : 0L;
        f[1] = s;
        s = f[2] + b2 + c;
        c = (Long.compareUnsigned(s, b2) < 0 || (c != 0 && s == b2)) ? 1L : 0L;
        f[2] = s;
        s = f[3] + b3 + c;
        c = (Long.compareUnsigned(s, b3) < 0 || (c != 0 && s == b3)) ? 1L : 0L;
        f[3] = s;
        for (int i = 4; i < 8 && c != 0L; i++) {
            long old = f[i];
            f[i] = old + c;
            c = (Long.compareUnsigned(f[i], old) < 0) ? 1L : 0L;
        }
    }

    private static int cmpShifted(long[] f, long b0, long b1, long b2, long b3) {
        for (int i = 7; i > 3; i--) {
            if (f[i] != 0L) {
                return 1;
            }
        }
        int c = Long.compareUnsigned(f[3], b3);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(f[2], b2);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(f[1], b1);
        if (c != 0) {
            return c;
        }
        return Long.compareUnsigned(f[0], b0);
    }

    private static void subFrom(long[] f, long b0, long b1, long b2, long b3) {
        subFrom4(f, b0, b1, b2, b3);
    }

    private static void subFromFrame(long[] f, long b0, long b1, long b2, long b3) {
        for (int i = 0; i < 8; i++) {
            f[i] = ~f[i];
        }
        for (int i = 0; i < 8; i++) {
            long old = f[i];
            f[i] = old + 1L;
            if (old != -1L) {
                break;
            }
        }
        addInto(f, b0, b1, b2, b3);
    }

    private static void subFrom4(long[] f, long b0, long b1, long b2, long b3) {
        long br = 0L;
        for (int i = 0; i < 8; i++) {
            long bi = switch (i) {
                case 0 -> b0;
                case 1 -> b1;
                case 2 -> b2;
                case 3 -> b3;
                default -> 0L;
            };
            long cur = f[i];
            long d = cur - bi;
            long nbr = Long.compareUnsigned(cur, bi) < 0 ? 1L : 0L;
            long d2 = d - br;
            if (br != 0L && d == 0L) {
                nbr = 1L;
            }
            f[i] = d2;
            br = nbr;
        }
    }
    /** Adds {@code v} at limb {@code i}, propagating the carry. */
    private static void addAt(long[] acc, int i, long v) {
        while (v != 0L && i < acc.length) {
            long old = acc[i];
            acc[i] = old + v;
            v = Long.compareUnsigned(acc[i], old) < 0 ? 1L : 0L;
            i++;
        }
    }

    /** Accumulates the 128-bit product {@code x*y} at limb {@code i}. */
    private static void mulAdd(long[] acc, int i, long x, long y) {
        if (x == 0L || y == 0L) {
            return;
        }
        addAt(acc, i, x * y);
        addAt(acc, i + 1, Math.unsignedMultiplyHigh(x, y));
    }

    /** {@code a * b} through the 512-bit frame: a 4x4 limb product, then one rounding. */
    static Binary256 mulFramed(Binary256 a, Binary256 b) {
        long a0 = a.limb(0);
        long a1 = a.limb(1);
        long a2 = a.limb(2);
        long a3 = a.limb(3);
        long b0 = b.limb(0);
        long b1 = b.limb(1);
        long b2 = b.limb(2);
        long b3 = b.limb(3);
        long[] f = new long[FRAME512];
        mulAdd(f, 0, a0, b0);
        mulAdd(f, 1, a0, b1);
        mulAdd(f, 1, a1, b0);
        mulAdd(f, 2, a0, b2);
        mulAdd(f, 2, a1, b1);
        mulAdd(f, 2, a2, b0);
        mulAdd(f, 3, a0, b3);
        mulAdd(f, 3, a1, b2);
        mulAdd(f, 3, a2, b1);
        mulAdd(f, 3, a3, b0);
        mulAdd(f, 4, a1, b3);
        mulAdd(f, 4, a2, b2);
        mulAdd(f, 4, a3, b1);
        mulAdd(f, 5, a2, b3);
        mulAdd(f, 5, a3, b2);
        mulAdd(f, 6, a3, b3);
        int exp = a.scaleExponent() + b.scaleExponent() - (P - 1);
        return pack512((a.signBit() ^ b.signBit()) ? 1 : 0, exp, f, false, false);
    }
    /** Rounds a 512-bit frame {@code sign * v * 2^(exp-(P-1))}, nearest-even. Mutates {@code m}. */
    static Binary256 pack512(int sign, int exp, long[] m, boolean sticky, boolean down) {
        if (down && sticky) {
            subOne8(m);
        }
        int bits = bitLength8(m);
        if (bits == 0) {
            return sign == 0 ? Binary256.ZERO : Binary256.NEGATIVE_ZERO;
        }
        int shift = (bits - 1) - (P - 1);
        int target = exp + shift;
        if (target < Binary256.EXP_MIN) {
            shift += Binary256.EXP_MIN - target;
        }
        int guard = 0;
        boolean low = sticky;
        if (shift <= 0) {
            if (shift < 0) {
                shl8(m, -shift);
            }
        } else {
            guard = (int) bitAt8(m, shift - 1);
            low = sticky || anyBelow8(m, shift - 1);
            shr8InPlace(m, shift);
        }
        if (guard != 0 && (low || (m[0] & 1L) != 0L)) {
            inc8(m);
        }
        int e = exp + shift;
        int qb = bitLength8(m);
        if (qb > P) {
            shr8InPlace(m, 1);
            e++;
        } else if (qb < P && qb > 0) {
            int grow = P - qb;
            shl8(m, grow);
            e -= grow;
        }
        if (e > Binary256.EXP_MAX) {
            return sign == 0 ? Binary256.POSITIVE_INFINITY : Binary256.NEGATIVE_INFINITY;
        }
        if (e < Binary256.EXP_MIN - (P - 1)) {
            return sign == 0 ? Binary256.ZERO : Binary256.NEGATIVE_ZERO;
        }
        return Binary256.raw(sign, e, m[0], m[1], m[2], m[3]);
    }

    /** {@code a / b} through the 512-bit frame: one scaled division, then one rounding. */
    static Binary256 divFramed(Binary256 a, Binary256 b) {
        int k = P + 32;
        long[] num = new long[FRAME512];
        num[0] = a.limb(0);
        num[1] = a.limb(1);
        num[2] = a.limb(2);
        num[3] = a.limb(3);
        shl8(num, k);
        long[] den = new long[FRAME512];
        den[0] = b.limb(0);
        den[1] = b.limb(1);
        den[2] = b.limb(2);
        den[3] = b.limb(3);
        long[] q = new long[FRAME512];
        long[] r = new long[FRAME512];
        Limbs.divmod(num, den, q, r);
        boolean sticky = false;
        for (int i = 0; i < FRAME512; i++) {
            if (r[i] != 0L) {
                sticky = true;
                break;
            }
        }
        int exp = a.scaleExponent() - b.scaleExponent() - k + (P - 1);
        return pack512((a.signBit() ^ b.signBit()) ? 1 : 0, exp, q, sticky, false);
    }
    private static void subOne8(long[] m) {
        for (int i = 0; i < 8; i++) {
            long old = m[i];
            m[i] = old - 1L;
            if (old != 0L) {
                break;
            }
        }
    }

    private static void inc8(long[] m) {
        for (int i = 0; i < 8; i++) {
            long old = m[i];
            m[i] = old + 1L;
            if (old != -1L) {
                break;
            }
        }
    }

    private static void shr8InPlace(long[] a, int k) {
        int ls = k >>> 6;
        int bs = k & 63;
        for (int i = 0; i < 8; i++) {
            int src = i + ls;
            long v = 0L;
            if (src < 8) {
                v = a[src] >>> bs;
                if (bs != 0 && src + 1 < 8) {
                    v |= a[src + 1] << (64 - bs);
                }
            }
            a[i] = v;
        }
    }
    private static final int P = Binary256.PRECISION;

    private Arithmetic() {
    }

    /**
     * Rounds {@code sign * (mag - down*delta) * 2^(exp-255)} to nearest-even at {@code P} bits,
     * with gradual underflow below {@code EXP_MIN}.
     */
    static Binary256 pack(int sign, int exp, Wide1024 mag, boolean sticky, boolean down) {
        if (down && sticky) {
            // (mag - delta) == (mag - 1) + (1 - delta), which is the "at or above" form.
            mag = mag.sub(ONE);
        }
        if (mag.isZero()) {
            return sign == 0 ? Binary256.ZERO : Binary256.NEGATIVE_ZERO;
        }

        // Normalise the leading bit to position P-1; a subnormal target forces extra shift.
        int lead = mag.bitLength() - 1;
        int shift = lead - (P - 1);
        int target = exp + shift;
        if (target < Binary256.EXP_MIN) {
            shift += Binary256.EXP_MIN - target;
        }

        Wide1024 q;
        int guard = 0;
        boolean low;
        if (shift <= 0) {
            q = mag.shl(-shift);
            low = sticky;
        } else if (shift >= 512) {
            q = ZERO;
            guard = 0;
            low = true;
        } else {
            q = mag.ushr(shift);
            guard = (int) (mag.ushr(shift - 1).limb(0) & 1L);
            // Every bit strictly below the guard bit counts into the sticky decision.
            low = sticky || (shift >= 2 && !mag.ushr(shift - 1).shl(shift - 1).equals(mag));
        }

        if (guard != 0 && (low || (q.limb(0) & 1L) != 0L)) {
            q = q.add(ONE);
        }

        int e = exp + shift;
        int bits = q.bitLength();
        if (bits > P) {
            q = q.ushr(1);
            e++;
            bits = P;
        }
        if (bits < P) {
            // Subnormal result: re-normalise internally, the exponent drops below EXP_MIN.
            int grow = P - bits;
            q = q.shl(grow);
            e -= grow;
        }
        if (e > Binary256.EXP_MAX) {
            return sign == 0 ? Binary256.POSITIVE_INFINITY : Binary256.NEGATIVE_INFINITY;
        }
        if (e < Binary256.EXP_MIN - (P - 1)) {
            return sign == 0 ? Binary256.ZERO : Binary256.NEGATIVE_ZERO;
        }
        return Binary256.raw(sign, e, q.limb(0), q.limb(1), q.limb(2), q.limb(3));
    }
    /**
     * {@code a*b + c} with exactly <em>one</em> rounding, in a 16-limb frame.
     *
     * <p>The product is exact (2p bits) and {@code c} is aligned into the same fixed-point frame,
     * so nothing is rounded before the shared {@link #pack512}. Once the exponent gap exceeds what
     * the frame can hold, the smaller operand collapses into a sticky tail; it is then more than
     * 270 bits below the frame's LSB, so it can only make the result inexact — never move it
     * across a rounding boundary.
     */
    static Binary256 fma(Binary256 a, Binary256 b, Binary256 c) {
        long c0 = c.limb(0);
        long c1 = c.limb(1);
        long c2 = c.limb(2);
        long c3 = c.limb(3);
        int expP = a.scaleExponent() + b.scaleExponent() - 2 * (P - 1);
        int expC = c.scaleExponent() - (P - 1);
        boolean prodSign = a.signBit() != b.signBit();
        boolean sameSign = prodSign == c.signBit();

        int exp;
        boolean dropProd = false;
        boolean dropC = false;
        if (expP >= expC) {
            if (expP - expC <= 512) {
                exp = expC;
            } else {
                exp = expP;
                dropC = true;
            }
        } else if (expC - expP <= 768) {
            exp = expP;
        } else {
            exp = expC;
            dropProd = true;
        }

        long[] f = new long[FRAME1024];
        int sign;
        if (dropProd) {
            // The dropped product sits more than 270 bits below the frame's LSB, so it is a pure
            // sticky tail. Two spare low bits are kept: without them the rounder would have no
            // guard position to express "at or below" when the addend is a bare p-bit value.
            sign = c.signBit() ? 1 : 0;
            exp = expC - 2;
            addShifted4(f, 2, c0, c1, c2, c3);
        } else {
            mulIntoFrame(f, a, b);
            if (expP > exp) {
                Limbs.shl(f, expP - exp, f);
            }
            if (dropC) {
                sign = prodSign ? 1 : 0;
            } else if (sameSign) {
                sign = prodSign ? 1 : 0;
                addShifted4(f, expC - exp, c0, c1, c2, c3);
            } else if (subShifted4(f, expC - exp, c0, c1, c2, c3)) {
                Limbs.neg(f, f);
                sign = c.signBit() ? 1 : 0;
            } else {
                sign = prodSign ? 1 : 0;
            }
        }

        boolean sticky = dropProd || dropC;
        boolean down = sticky && !sameSign;
        int bits = Limbs.bitLength(f);
        if (bits == 0) {
            return Binary256.ZERO;
        }
        int shift = 0;
        if (bits > 512) {
            shift = bits - 512;
            sticky |= anyBelow16(f, shift);
            Limbs.ushr(f, shift, f);
        }
        return pack512(sign, exp + shift + (P - 1), f, sticky, down);
    }

    /** The exact 4x4 limb product of {@code a} and {@code b}, written into a 16-limb frame. */
    private static void mulIntoFrame(long[] f, Binary256 a, Binary256 b) {
        long a0 = a.limb(0);
        long a1 = a.limb(1);
        long a2 = a.limb(2);
        long a3 = a.limb(3);
        long b0 = b.limb(0);
        long b1 = b.limb(1);
        long b2 = b.limb(2);
        long b3 = b.limb(3);
        mulAdd(f, 0, a0, b0);
        mulAdd(f, 1, a0, b1);
        mulAdd(f, 1, a1, b0);
        mulAdd(f, 2, a0, b2);
        mulAdd(f, 2, a1, b1);
        mulAdd(f, 2, a2, b0);
        mulAdd(f, 3, a0, b3);
        mulAdd(f, 3, a1, b2);
        mulAdd(f, 3, a2, b1);
        mulAdd(f, 3, a3, b0);
        mulAdd(f, 4, a1, b3);
        mulAdd(f, 4, a2, b2);
        mulAdd(f, 4, a3, b1);
        mulAdd(f, 5, a2, b3);
        mulAdd(f, 5, a3, b2);
        mulAdd(f, 6, a3, b3);
    }

    /** Adds the 4-limb magnitude {@code c}, shifted left by {@code shift}, into the frame. */
    private static void addShifted4(long[] f, int shift, long c0, long c1, long c2, long c3) {
        int li = shift >>> 6;
        int bs = shift & 63;
        long w0 = c0;
        long w1 = c1;
        long w2 = c2;
        long w3 = c3;
        long w4 = 0L;
        if (bs != 0) {
            w4 = c3 >>> (64 - bs);
            w3 = (c3 << bs) | (c2 >>> (64 - bs));
            w2 = (c2 << bs) | (c1 >>> (64 - bs));
            w1 = (c1 << bs) | (c0 >>> (64 - bs));
            w0 = c0 << bs;
        }
        long carry = addLimb(f, li, w0, 0L);
        carry = addLimb(f, li + 1, w1, carry);
        carry = addLimb(f, li + 2, w2, carry);
        carry = addLimb(f, li + 3, w3, carry);
        carry = addLimb(f, li + 4, w4, carry);
        for (int i = li + 5; carry != 0L && i < FRAME1024; i++) {
            carry = addLimb(f, i, 0L, carry);
        }
    }

    /**
     * Subtracts the 4-limb magnitude {@code c}, shifted left by {@code shift}, from the frame.
     * A true return means the frame held the smaller magnitude, so {@code f} now holds the
     * two's-complement difference and must be negated by the caller.
     */
    private static boolean subShifted4(long[] f, int shift, long c0, long c1, long c2, long c3) {
        int li = shift >>> 6;
        int bs = shift & 63;
        long w0 = c0;
        long w1 = c1;
        long w2 = c2;
        long w3 = c3;
        long w4 = 0L;
        if (bs != 0) {
            w4 = c3 >>> (64 - bs);
            w3 = (c3 << bs) | (c2 >>> (64 - bs));
            w2 = (c2 << bs) | (c1 >>> (64 - bs));
            w1 = (c1 << bs) | (c0 >>> (64 - bs));
            w0 = c0 << bs;
        }
        long borrow = subLimb(f, li, w0, 0L);
        borrow = subLimb(f, li + 1, w1, borrow);
        borrow = subLimb(f, li + 2, w2, borrow);
        borrow = subLimb(f, li + 3, w3, borrow);
        borrow = subLimb(f, li + 4, w4, borrow);
        for (int i = li + 5; borrow != 0L && i < FRAME1024; i++) {
            borrow = subLimb(f, i, 0L, borrow);
        }
        return borrow != 0L;
    }

    private static long addLimb(long[] f, int i, long v, long carry) {
        if (i >= FRAME1024) {
            return carry;
        }
        long cur = f[i];
        long s = cur + v;
        long c = Long.compareUnsigned(s, cur) < 0 ? 1L : 0L;
        long r = s + carry;
        if (carry != 0L && Long.compareUnsigned(r, s) < 0) {
            c++;
        }
        f[i] = r;
        return c;
    }

    private static long subLimb(long[] f, int i, long v, long borrow) {
        if (i >= FRAME1024) {
            return borrow;
        }
        long cur = f[i];
        long d = cur - v;
        long b = Long.compareUnsigned(cur, v) < 0 ? 1L : 0L;
        long r = d - borrow;
        if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
            b++;
        }
        f[i] = r;
        return b;
    }

    /** Any bit strictly below {@code i} (i.e. in {@code [0, i-1]}) of a 16-limb frame. */
    private static boolean anyBelow16(long[] m, int i) {
        if (i <= 0) {
            return false;
        }
        int li = i >> 6;
        for (int j = 0; j < li && j < FRAME1024; j++) {
            if (m[j] != 0L) {
                return true;
            }
        }
        if (li >= FRAME1024) {
            return false;
        }
        int bi = i & 63;
        long mask = bi == 0 ? 0L : (1L << bi) - 1L;
        return (m[li] & mask) != 0L;
    }

    /** Widens a 512-bit magnitude into the 1024-bit rounding frame. */
    static Wide1024 widen(Wide512 m) {
        return Wide1024.of(m.limb(0), m.limb(1), m.limb(2), m.limb(3),
                m.limb(4), m.limb(5), m.limb(6), m.limb(7),
                0, 0, 0, 0, 0, 0, 0, 0);
    }
    private static Wide512 low512(Wide1024 v) {
        return Wide512.of(v.limb(0), v.limb(1), v.limb(2), v.limb(3),
                v.limb(4), v.limb(5), v.limb(6), v.limb(7));
    }
}
