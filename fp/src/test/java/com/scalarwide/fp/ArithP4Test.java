package com.scalarwide.fp;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P4 gate: the whole exponent domain — gradual underflow, overflow, and the boundaries where
 * normal turns into subnormal and where finite turns into infinity.
 *
 * <p>The reference is written from a different angle than {@code Arithmetic.pack}: it fixes the
 * <em>target LSB exponent</em> first ({@code max(e-255, EXP_MIN-255)}) and rounds the exact value
 * at that grid. That is the definition of the format rather than a transcription of the
 * implementation.
 */
class ArithP4Test {

    private static final long SEED = 12345L;
    private static final int P = Binary256.PRECISION;

    @Test
    void subnormalResultsAreCorrectlyRounded() {
        // Products of small magnitudes land in the subnormal range.
        Random rnd = new Random(SEED);
        int checked = 0;
        for (int i = 0; i < 4000; i++) {
            Binary256 a = tiny(rnd);
            Binary256 b = tiny(rnd);
            Binary256 got = a.multiply(b);
            assertRef("mul-sub", refMul(a, b), got);
            if (got.isSubnormal()) {
                checked++;
            }
        }
        assertTrue(checked > 0, "the sweep must actually reach the subnormal range");
    }

    @Test
    void underflowToZero() {
        Binary256 tiny = Binary256.raw(0, Binary256.EXP_MIN - (P - 1), 0L, 0L, 0L, Long.MIN_VALUE);
        // (MIN_VALUE/2) * 0.5 underflows to zero
        assertEquals(0, tiny.multiply(tiny).compareTo(Binary256.ZERO));
        assertEquals(Binary256.ZERO, tiny.multiply(tiny));
    }

    @Test
    void overflowToInfinity() {
        Random rnd = new Random(SEED + 1);
        int overflows = 0;
        for (int i = 0; i < 4000; i++) {
            Binary256 a = huge(rnd);
            Binary256 b = huge(rnd);
            Binary256 got = a.multiply(b);
            assertRef("mul-over", refMul(a, b), got);
            if (got.isInfinite()) {
                overflows++;
            }
        }
        assertTrue(overflows > 0, "the sweep must actually overflow");
    }

    @Test
    void boundaryNeighbourhoods() {
        // Just below MIN_NORMAL: subnormal, spacing is fixed at the smallest ulp.
        Binary256 belowNormal = Binary256.raw(0, Binary256.EXP_MIN - 1, 0L, 0L, 0L, -1L);
        assertTrue(belowNormal.isSubnormal());
        assertRef("below-min-normal", refMul(belowNormal, Binary256.ONE),
                belowNormal.multiply(Binary256.ONE));

        // MIN_NORMAL itself is normal.
        assertTrue(Binary256.MIN_NORMAL.isNormal());
        assertRef("min-normal", refMul(Binary256.MIN_NORMAL, Binary256.ONE),
                Binary256.MIN_NORMAL.multiply(Binary256.ONE));

        // MAX_VALUE * 1 keeps MAX_VALUE.
        assertRef("max", refMul(Binary256.MAX_VALUE, Binary256.ONE),
                Binary256.MAX_VALUE.multiply(Binary256.ONE));

        // MAX_VALUE + a tiny amount rounds back to MAX_VALUE or overflows; both are checked.
        assertRef("max+tiny", refAdd(Binary256.MAX_VALUE, Binary256.MIN_VALUE),
                Binary256.MAX_VALUE.add(Binary256.MIN_VALUE));

        // The smallest subnormal, halved, is a tie: rounds to zero (even).
        Binary256 min = Binary256.MIN_VALUE;
        assertEquals(Binary256.ZERO, min.multiply(Binary256.of(0.5)));
    }

    @Test
    void subnormalArithmeticStaysExact() {
        // Subnormals have a fixed spacing, so adding two of them is exact while it fits.
        Binary256 one = Binary256.MIN_VALUE;
        Binary256 two = one.add(one);
        assertRef("min+min", refAdd(one, one), two);
        assertEquals(0, two.compareTo(one.multiply(Binary256.of(2.0))));
    }

    // ---------------------------------------------------------------- reference

    private static void assertRef(String label, Binary256 want, Binary256 got) {
        assertEquals(want.scaleExponent(), got.scaleExponent(), label + ": exponent");
        assertEquals(want.signBit(), got.signBit(), label + ": sign");
        for (int i = 3; i >= 0; i--) {
            assertEquals(want.limb(i), got.limb(i), label + ": limb " + i);
        }
    }

    private static Binary256 refMul(Binary256 a, Binary256 b) {
        BigInteger[] va = exact(a);
        BigInteger[] vb = exact(b);
        return round(va[0].multiply(vb[0]), va[1].intValue() + vb[1].intValue(), false);
    }

    private static Binary256 refAdd(Binary256 a, Binary256 b) {
        BigInteger[] va = exact(a);
        BigInteger[] vb = exact(b);
        int e2 = Math.min(va[1].intValue(), vb[1].intValue());
        BigInteger num = va[0].shiftLeft(va[1].intValue() - e2)
                .add(vb[0].shiftLeft(vb[1].intValue() - e2));
        return round(num, e2, false);
    }

    /**
     * Rounds {@code num * 2^exp2} onto the format grid: the target LSB exponent is
     * {@code max(leadingExp-255, EXP_MIN-255)}, then round-to-nearest-even on that grid.
     */
    private static Binary256 round(BigInteger num, int exp2, boolean extraSticky) {
        if (num.signum() == 0) {
            return Binary256.ZERO;
        }
        int sign = num.signum() < 0 ? 1 : 0;
        BigInteger mag = num.abs();
        int lead = exp2 + mag.bitLength() - 1;
        if (lead > Binary256.EXP_MAX + 1) {
            return sign == 0 ? Binary256.POSITIVE_INFINITY : Binary256.NEGATIVE_INFINITY;
        }
        int lsb = Math.max(lead - (P - 1), Binary256.EXP_MIN - (P - 1));
        int shift = lsb - exp2; // bits to drop from mag
        BigInteger m;
        boolean guard;
        boolean sticky;
        if (shift <= 0) {
            m = mag.shiftLeft(-shift);
            guard = false;
            sticky = extraSticky;
        } else {
            m = mag.shiftRight(shift);
            guard = mag.testBit(shift - 1);
            sticky = extraSticky
                    || mag.and(BigInteger.ONE.shiftLeft(shift - 1).subtract(BigInteger.ONE)).signum() != 0;
        }
        if (guard && (sticky || m.testBit(0))) {
            m = m.add(BigInteger.ONE);
        }
        if (m.signum() == 0) {
            return sign == 0 ? Binary256.ZERO : Binary256.NEGATIVE_ZERO;
        }
        int leadAfter = lsb + m.bitLength() - 1;
        if (leadAfter > Binary256.EXP_MAX) {
            return sign == 0 ? Binary256.POSITIVE_INFINITY : Binary256.NEGATIVE_INFINITY;
        }
        if (leadAfter < Binary256.EXP_MIN - (P - 1)) {
            return sign == 0 ? Binary256.ZERO : Binary256.NEGATIVE_ZERO;
        }
        BigInteger imb = m.shiftLeft(P - m.bitLength());
        long[] limbs = new long[4];
        BigInteger work = imb;
        for (int i = 0; i < 4; i++) {
            limbs[i] = work.and(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)).longValue();
            work = work.shiftRight(64);
        }
        return Binary256.raw(sign, leadAfter, limbs[0], limbs[1], limbs[2], limbs[3]);
    }

    private static BigInteger[] exact(Binary256 v) {
        BigInteger m = BigInteger.ZERO;
        for (int i = 3; i >= 0; i--) {
            m = m.shiftLeft(64).or(unsigned(v.limb(i)));
        }
        return new BigInteger[] {v.signBit() ? m.negate() : m,
                BigInteger.valueOf(v.scaleExponent() - 255)};
    }

    private static BigInteger unsigned(long v) {
        BigInteger b = BigInteger.valueOf(v & Long.MAX_VALUE);
        return v < 0 ? b.setBit(63) : b;
    }

    /** Magnitude around 2^-262000, i.e. products land in the subnormal range. */
    private static Binary256 tiny(Random rnd) {
        // Half of EXP_MIN: the product lands on and around the subnormal boundary.
        return Binary256.raw(rnd.nextBoolean() ? 1 : 0, (Binary256.EXP_MIN / 2) - 50 + rnd.nextInt(150),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() | Long.MIN_VALUE);
    }

    /** Magnitude around 2^262000, i.e. products overflow. */
    private static Binary256 huge(Random rnd) {
        return Binary256.raw(rnd.nextBoolean() ? 1 : 0, Binary256.EXP_MAX - 100 - rnd.nextInt(100),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() | Long.MIN_VALUE);
    }
}
