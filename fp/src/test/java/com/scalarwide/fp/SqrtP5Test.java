package com.scalarwide.fp;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P5 gate (basic case): {@code sqrt} correctness only, no performance work.
 *
 * <p>The reference derives the expected answer from the format definition — pick the target LSB
 * exponent for the result, take {@code BigInteger.sqrt} of the exact scaled value, and round on
 * that grid. My implementation instead runs a restoring bit-pair square root and hands the result
 * to the shared rounding path, so the two arrive at the answer by different routes.
 */
class SqrtP5Test {

    private static final long SEED = 12345L;
    private static final int N = 2000;
    private static final int P = Binary256.PRECISION;

    @Test
    void squareRootsMatchTheReference() {
        Random rnd = new Random(SEED);
        for (int i = 0; i < N; i++) {
            Binary256 a = random(rnd);
            assertRef("sqrt", refSqrt(a), a.sqrt());
        }
    }

    @Test
    void perfectSquaresRoundTrip() {
        Random rnd = new Random(SEED + 1);
        for (int i = 0; i < 200; i++) {
            Binary256 x = random(rnd);
            Binary256 sq = x.multiply(x);
            if (!sq.isFinite() || sq.isZero()) {
                continue;
            }
            Binary256 back = sq.sqrt();
            // sqrt(x*x) rounds to |x| when x*x was exact, otherwise to within one ulp of it.
            assertTrue(back.subtract(x.abs()).isZero()
                            || back.subtract(x.abs()).abs().compareTo(ulpOf(x)) <= 0,
                    "sqrt(x*x) should be x up to one ulp");
        }
    }

    @Test
    void landmarkValues() {
        assertEquals(Binary256.ONE, Binary256.ONE.sqrt());
        assertEquals(Binary256.ZERO, Binary256.ZERO.sqrt());
        assertEquals(Binary256.POSITIVE_INFINITY, Binary256.POSITIVE_INFINITY.sqrt());
        assertTrue(Binary256.NEGATIVE_ONE.sqrt().isNaN());
        assertRef("2", refSqrt(Binary256.of(2.0)), Binary256.of(2.0).sqrt());
        assertRef("4", refSqrt(Binary256.of(4.0)), Binary256.of(4.0).sqrt());
        assertRef("0.25", refSqrt(Binary256.of(0.25)), Binary256.of(0.25).sqrt());
        assertRef("max", refSqrt(Binary256.MAX_VALUE), Binary256.MAX_VALUE.sqrt());
        assertRef("min", refSqrt(Binary256.MIN_VALUE), Binary256.MIN_VALUE.sqrt());
    }

    private static void assertRef(String label, Binary256 want, Binary256 got) {
        assertEquals(want.scaleExponent(), got.scaleExponent(), label + ": exponent");
        assertEquals(want.signBit(), got.signBit(), label + ": sign");
        for (int i = 3; i >= 0; i--) {
            assertEquals(want.limb(i), got.limb(i), label + ": limb " + i);
        }
    }

    // ---------------------------------------------------------------- reference

    /** Rounds {@code sqrt(mag * 2^exp2)} onto the format grid using BigInteger.sqrt. */
    private static Binary256 refSqrt(Binary256 v) {
        if (v.isZero()) {
            return Binary256.ZERO;
        }
        BigInteger mag = magOf(v);
        int exp2 = v.scaleExponent() - (Binary256.PRECISION - 1);
        int lead = exp2 + mag.bitLength() - 1;
        if ((lead & 1) != 0) {
            mag = mag.shiftLeft(1);
            exp2 -= 1;
        }
        int half = Math.floorDiv(lead, 2);        // root leading bit; floor, not truncation
        int lsb = Math.max(half - (P - 1), Binary256.EXP_MIN - (P - 1));
        int scale = exp2 - 2 * lsb;                // floor(sqrt(value)/2^lsb) == sqrt(mag << scale)
        BigInteger n = mag.shiftLeft(scale);
        BigInteger root = n.sqrt();
        boolean exact = root.multiply(root).equals(n);
        // root * 2^lsb is floor(sqrt(value)); round to nearest-even using the remainder.
        int shift = root.bitLength() - P;
        BigInteger m = root;
        boolean guard = false;
        boolean sticky = !exact;
        if (shift > 0) {
            m = root.shiftRight(shift);
            guard = root.testBit(shift - 1);
            sticky = sticky || root.and(BigInteger.ONE.shiftLeft(shift - 1).subtract(BigInteger.ONE))
                    .signum() != 0;
        } else if (shift < 0) {
            m = root.shiftLeft(-shift);
        }
        if (guard && (sticky || m.testBit(0))) {
            m = m.add(BigInteger.ONE);
        }
        int e = lsb + root.bitLength() - 1;
        if (m.bitLength() > P) {
            m = m.shiftRight(1);
            e++;
        }
        long[] limbs = new long[4];
        BigInteger work = m;
        for (int i = 0; i < 4; i++) {
            limbs[i] = work.and(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)).longValue();
            work = work.shiftRight(64);
        }
        return Binary256.raw(0, e, limbs[0], limbs[1], limbs[2], limbs[3]);
    }

    private static BigInteger magOf(Binary256 v) {
        BigInteger r = BigInteger.ZERO;
        for (int i = 3; i >= 0; i--) {
            r = r.shiftLeft(64).or(unsigned(v.limb(i)));
        }
        return r;
    }

    private static BigInteger unsigned(long v) {
        BigInteger b = BigInteger.valueOf(v & Long.MAX_VALUE);
        return v < 0 ? b.setBit(63) : b;
    }

    private static Binary256 ulpOf(Binary256 x) {
        return Binary256.raw(0, Math.max(x.scaleExponent(), Binary256.EXP_MIN) - (P - 1),
                0L, 0L, 0L, Long.MIN_VALUE);
    }

    private static Binary256 random(Random rnd) {
        int exp = rnd.nextInt(200) - 100;
        return Binary256.raw(0, exp, rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                topLimb(rnd));
    }
    /** Top limb of a normalised significand: leading bit set, nothing above it. */
    private static long topLimb(Random r) {
        int used = Binary256.PRECISION - 64 * 3;
        long mask = (1L << used) - 1L;
        return (r.nextLong() & mask) | (1L << (used - 1));
    }
}
