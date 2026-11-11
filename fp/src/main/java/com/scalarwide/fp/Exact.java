package com.scalarwide.fp;

import com.scalarwide.Wide1024;
import com.scalarwide.Wide256;
import com.scalarwide.Wide512;

/**
 * An exact-but-unrounded intermediate value.
 *
 * <p>The true value is {@code sign * (mag - (down ? delta : 0)) * 2^(exp - 255)} with
 * {@code 0 <= delta < 1}, and {@code sticky} says whether {@code delta} is non-zero.
 * {@code down} selects the side of {@code mag} the true value lies on.
 *
 * <p>Why direction matters: aligning a smaller addend and truncating it, or dividing and
 * truncating the quotient, both leave a sub-ULP remainder. Carrying its direction explicitly
 * keeps every primitive exact (no rounding is performed here) and lets the P2 gate bracket the
 * true value with plain integer arithmetic.
 */
record Exact(int sign, int exp, Wide512 mag, boolean sticky, boolean down) {

    private static final int PRECISION = Binary256.PRECISION;

    private static Wide256 m256(Binary256 v) {
        return Wide256.of(v.limb(0), v.limb(1), v.limb(2), v.limb(3));
    }

    private static Wide512 widen(Wide256 v) {
        return Wide512.of(v.limb(0), v.limb(1), v.limb(2), v.limb(3), 0, 0, 0, 0);
    }

    private static Wide1024 widen1024(Wide256 v) {
        return Wide1024.of(v.limb(0), v.limb(1), v.limb(2), v.limb(3),
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static Wide512 low512(Wide1024 v) {
        return Wide512.of(v.limb(0), v.limb(1), v.limb(2), v.limb(3),
                v.limb(4), v.limb(5), v.limb(6), v.limb(7));
    }

    /**
     * Exact product: {@code 2p} bits, nothing truncated.
     * {@code exp = ea + eb - 255} since {@code (ma*mb) * 2^(ea+eb-510) = M * 2^(exp-255)}.
     */
    static Exact mul(Binary256 a, Binary256 b) {
        int sign = (a.signBit() ^ b.signBit()) ? 1 : 0;
        Wide512 m = m256(a).mulFull(m256(b));
        return new Exact(sign, a.scaleExponent() + b.scaleExponent() - 255, m, false, false);
    }

    /**
     * Exact aligned sum or difference. The larger exponent becomes the result exponent; the
     * smaller operand is shifted right and whatever falls off is remembered.
     *
     * <p>{@code down} is true for a truncated subtraction: dropping the tail of the subtrahend
     * makes the truncated difference <em>larger</em> than the true one.
     */
    static Exact alignAdd(Binary256 a, Binary256 b) {
        Binary256 big = a.scaleExponent() >= b.scaleExponent() ? a : b;
        Binary256 small = big == a ? b : a;
        int shift = big.scaleExponent() - small.scaleExponent();
        boolean sameSign = a.signBit() == b.signBit();
        int bigSign = big.signBit() ? 1 : 0;
        Wide512 ZERO = Wide512.of(0, 0, 0, 0, 0, 0, 0, 0);

        if (shift <= 256) {
            // Both operands fit exactly in 512 bits when the frame sits at the smaller
            // exponent, so this branch performs no truncation at all: the sum is exact.
            Wide512 bigWide = widen(m256(big)).shl(shift);
            Wide512 smallWide = widen(m256(small));
            if (sameSign) {
                return new Exact(bigSign, small.scaleExponent(), bigWide.add(smallWide), false, false);
            }
            int cmp = bigWide.compareUnsigned(smallWide);
            if (cmp == 0) {
                return new Exact(0, small.scaleExponent(), ZERO, false, false);
            }
            if (cmp > 0) {
                return new Exact(bigSign, small.scaleExponent(), bigWide.sub(smallWide), false, false);
            }
            return new Exact(small.signBit() ? 1 : 0, small.scaleExponent(),
                    smallWide.sub(bigWide), false, false);
        }

        // Beyond 256 bits of separation the smaller operand is below one ULP of big and cannot
        // cancel it, so the whole operand collapses into a single dropped tail.
        Wide512 bigWide = widen(m256(big));
        if (sameSign) {
            return new Exact(bigSign, big.scaleExponent(), bigWide, true, false);
        }
        return new Exact(bigSign, big.scaleExponent(), bigWide, true, true);
    }

    /**
     * Exact quotient to {@code p+2} bits plus a non-zero remainder flag.
     *
     * <p>{@code q = floor(ma * 2^257 / mb)} has 258 bits; the true quotient is
     * {@code (q + frac) * 2^-257}, so {@code exp = ea - eb - 2} and the true value is at or above
     * {@code q}.
     */
    static Exact div(Binary256 a, Binary256 b) {
        int sign = (a.signBit() ^ b.signBit()) ? 1 : 0;
        Wide1024 num = widen1024(m256(a)).shl(PRECISION + 1);
        Wide1024 den = widen1024(m256(b));
        Wide1024 q = num.div(den);
        boolean remainder = !num.rem(den).isZero();
        return new Exact(sign, a.scaleExponent() - b.scaleExponent() - 2, low512(q), remainder, false);
    }
}
