package com.scalarwide.fp;

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
        boolean aNeg = a.signBit();
        boolean bNeg = b.signBit();
        int ea = a.scaleExponent();
        int eb = b.scaleExponent();
        Binary256 big = ea >= eb ? a : b;
        Binary256 small = big == a ? b : a;
        int diff = big.scaleExponent() - small.scaleExponent();
        long[] f = new long[FRAME512];
        f[0] = big.limb(0);
        f[1] = big.limb(1);
        f[2] = big.limb(2);
        f[3] = big.limb(3);
        if (diff <= P) {
            shl8(f, diff);
            long b0 = small.limb(0);
            long b1 = small.limb(1);
            long b2 = small.limb(2);
            long b3 = small.limb(3);
            boolean same = (big.signBit() == small.signBit());
            if (same) {
                addInto(f, b0, b1, b2, b3);
                return pack512(big.signBit() ? 1 : 0, small.scaleExponent(), f, false, false);
            }
            int cmp = cmpShifted(f, b0, b1, b2, b3);
            if (cmp == 0) {
                return Binary256.ZERO;
            }
            if (cmp > 0) {
                subFrom(f, b0, b1, b2, b3);
                return pack512(big.signBit() ? 1 : 0, small.scaleExponent(), f, false, false);
            }
            subFromFrame(f, b0, b1, b2, b3);
            return pack512(small.signBit() ? 1 : 0, small.scaleExponent(), f, false, false);
        }
        return pack512(big.signBit() ? 1 : 0, big.scaleExponent(), f, true, big.signBit() != small.signBit());
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
    /** Rounds a 512-bit frame {@code sign * v * 2^(exp-(P-1))}, nearest-even. Mutates {@code m}. */
    static Binary256 pack512(int sign, int exp, long[] m, boolean sticky, boolean down) {
        if (down && sticky) {
            for (int i = 0; i < 8; i++) {
                long old = m[i];
                m[i] = old - 1L;
                if (old != 0L) {
                    break;
                }
            }
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
        long[] q = new long[8];
        int guard = 0;
        boolean low;
        if (shift <= 0) {
            System.arraycopy(m, 0, q, 0, 8);
            if (shift < 0) {
                shl8(q, -shift);
            }
            low = sticky;
        } else {
            shr8(m, shift, q);
            guard = (int) bitAt8(m, shift - 1);
            low = sticky || anyBelow8(m, shift - 1);
        }
        if (guard != 0 && (low || (q[0] & 1L) != 0L)) {
            for (int i = 0; i < 8; i++) {
                long old = q[i];
                q[i] = old + 1L;
                if (old != -1L) {
                    break;
                }
            }
        }
        int e = exp + shift;
        int qb = bitLength8(q);
        if (qb > P) {
            shr8(q, 1, q);
            e++;
            qb = P;
        }
        if (qb < P && qb > 0) {
            int grow = P - qb;
            shl8(q, grow);
            e -= grow;
        }
        if (e > Binary256.EXP_MAX) {
            return sign == 0 ? Binary256.POSITIVE_INFINITY : Binary256.NEGATIVE_INFINITY;
        }
        if (e < Binary256.EXP_MIN - (P - 1)) {
            return sign == 0 ? Binary256.ZERO : Binary256.NEGATIVE_ZERO;
        }
        return Binary256.raw(sign, e, q[0], q[1], q[2], q[3]);
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
     * {@code a*b + c} with exactly <em>one</em> rounding.
     *
     * <p>The product is exact (2p bits) and {@code c} is aligned into the same fixed-point frame,
     * so no intermediate result is ever rounded. When the exponent gap exceeds what a 1024-bit
     * frame can hold, the smaller operand becomes a sticky tail — it is then more than 512 bits
     * below the other's LSB and cannot affect anything above the rounding point except by
     * making the result inexact.
     */
    static Binary256 fma(Binary256 a, Binary256 b, Binary256 c) {
        Exact p = Exact.mul(a, b);
        // Pick the frame: normally the smaller exponent so both fit exactly. If the gap is too
        // wide for the 1024-bit container, sit on the dominant operand and let the other become a
        // sticky tail (it is then more than its own width below the frame's LSB).
        int expP = p.exp();
        int expC = c.scaleExponent();
        int exp;
        if (expP <= expC) {
            exp = expC - expP <= 768 ? expP : expC;
        } else {
            exp = expP - expC <= 512 ? expC : expP;
        }

        Wide1024 prod;
        boolean prodSticky;
        int dP = p.exp() - exp;
        if (dP < 0) {
            // frame sits above the product: its LSB is below the frame, so it is pure tail
            prod = Wide1024.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            prodSticky = true;
        } else if (dP <= 512) {
            Wide512 m = p.mag();
            prod = Wide1024.of(m.limb(0), m.limb(1), m.limb(2), m.limb(3),
                    m.limb(4), m.limb(5), m.limb(6), m.limb(7),
                    0, 0, 0, 0, 0, 0, 0, 0).shl(dP);
            prodSticky = false;
        } else {
            prod = Wide1024.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            prodSticky = true;
        }

        Wide1024 add;
        boolean addSticky;
        int dC = c.scaleExponent() - exp;
        if (dC < 0) {
            add = Wide1024.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            addSticky = true;
        } else if (dC <= 768) {
            add = Wide1024.of(c.limb(0), c.limb(1), c.limb(2), c.limb(3),
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0).shl(dC);
            addSticky = false;
        } else {
            add = Wide1024.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            addSticky = true;
        }

        boolean prodSign = p.sign() != 0;
        boolean addSign = c.signBit();
        boolean sticky = prodSticky || addSticky;
        if (prodSign == addSign) {
            return pack(prodSign ? 1 : 0, exp, prod.add(add), sticky, false);
        }
        int cmp = prod.compareUnsigned(add);
        if (cmp == 0) {
            return sticky
                    ? pack(addSign ? 1 : 0, exp, ONE, true, true)
                    : (prodSign ? Binary256.NEGATIVE_ZERO : Binary256.ZERO);
        }
        if (cmp > 0) {
            return pack(prodSign ? 1 : 0, exp, prod.sub(add), sticky, sticky);
        }
        return pack(addSign ? 1 : 0, exp, add.sub(prod), sticky, sticky);
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
