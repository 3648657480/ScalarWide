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
