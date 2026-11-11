package com.scalarwide.fp;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P5 gate for {@code fma}: one rounding, not two.
 *
 * <p>Compared against the exact {@code a*b + c} as a BigInteger rational rounded once. A separate
 * assertion proves the single-rounding behaviour is observable, so a naive
 * {@code multiply().add()} cannot pass this gate unnoticed.
 */
class FmaP5Test {

    private static final long SEED = 12345L;
    private static final int N = 20_000;

    @Test
    void fusedMultiplyAddRoundsOnce() {
        Random rnd = new Random(SEED);
        for (int i = 0; i < N; i++) {
            Binary256 a = random(rnd);
            Binary256 b = random(rnd);
            Binary256 c = random(rnd);
            assertRef("fma", refFma(a, b, c), a.fma(b, c));
        }
    }

    @Test
    void singleRoundingIsObservable() {
        // Find at least one case where fma differs from multiply-then-add; otherwise the gate
        // above could be passed by an implementation that rounds twice.
        Random rnd = new Random(SEED + 1);
        int differences = 0;
        for (int i = 0; i < N && differences == 0; i++) {
            Binary256 a = random(rnd);
            Binary256 b = random(rnd);
            Binary256 c = random(rnd);
            if (!a.fma(b, c).equals(a.multiply(b).add(c))) {
                differences++;
            }
        }
        assertTrue(differences > 0, "fma must differ from multiply-then-add somewhere");
    }

    @Test
    void landmarks() {
        assertEquals(Binary256.ONE, Binary256.ONE.fma(Binary256.ONE, Binary256.ZERO));
        assertEquals(Binary256.ZERO, Binary256.ONE.fma(Binary256.ONE, Binary256.NEGATIVE_ONE));
        assertTrue(Binary256.POSITIVE_INFINITY.fma(Binary256.ZERO, Binary256.ONE).isNaN());
        assertEquals(Binary256.POSITIVE_INFINITY,
                Binary256.POSITIVE_INFINITY.fma(Binary256.ONE, Binary256.ONE));
        assertTrue(Binary256.POSITIVE_INFINITY.fma(Binary256.ONE, Binary256.NEGATIVE_INFINITY).isNaN());
        assertEquals(Binary256.NEGATIVE_INFINITY,
                Binary256.ONE.fma(Binary256.ONE, Binary256.NEGATIVE_INFINITY));
        // 2*3 + 4 = 10 exactly
        assertEquals(Binary256.of(10.0), Binary256.of(2.0).fma(Binary256.of(3.0), Binary256.of(4.0)));
    }

    // ---------------------------------------------------------------- reference

    private static void assertRef(String label, Binary256 want, Binary256 got) {
        assertEquals(want.scaleExponent(), got.scaleExponent(), label + ": exponent");
        assertEquals(want.signBit(), got.signBit(), label + ": sign");
        for (int i = 3; i >= 0; i--) {
            assertEquals(want.limb(i), got.limb(i), label + ": limb " + i);
        }
    }

    private static Binary256 refFma(Binary256 a, Binary256 b, Binary256 c) {
        BigInteger[] va = exact(a);
        BigInteger[] vb = exact(b);
        BigInteger[] vc = exact(c);
        BigInteger prodNum = va[0].multiply(vb[0]);
        int prodExp = va[1].intValue() + vb[1].intValue();
        int e2 = Math.min(prodExp, vc[1].intValue());
        BigInteger num = prodNum.shiftLeft(prodExp - e2)
                .add(vc[0].shiftLeft(vc[1].intValue() - e2));
        return round(num, e2);
    }

    /** Same grid-based rounding as the P3/P4 references. */
    private static Binary256 round(BigInteger num, int exp2) {
        if (num.signum() == 0) {
            return Binary256.ZERO;
        }
        int sign = num.signum() < 0 ? 1 : 0;
        BigInteger mag = num.abs();
        int lead = exp2 + mag.bitLength() - 1;
        int lsb = Math.max(lead - (Binary256.PRECISION - 1),
                Binary256.EXP_MIN - (Binary256.PRECISION - 1));
        int shift = lsb - exp2;
        BigInteger m;
        boolean guard;
        boolean sticky;
        if (shift <= 0) {
            m = mag.shiftLeft(-shift);
            guard = false;
            sticky = false;
        } else {
            m = mag.shiftRight(shift);
            guard = mag.testBit(shift - 1);
            sticky = mag.and(BigInteger.ONE.shiftLeft(shift - 1).subtract(BigInteger.ONE)).signum() != 0;
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
        if (leadAfter < Binary256.EXP_MIN - (Binary256.PRECISION - 1)) {
            return sign == 0 ? Binary256.ZERO : Binary256.NEGATIVE_ZERO;
        }
        BigInteger imb = m.shiftLeft(Binary256.PRECISION - m.bitLength());
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

    private static Binary256 random(Random rnd) {
        int exp = rnd.nextInt(120) - 60;
        return Binary256.raw(rnd.nextBoolean() ? 1 : 0, exp,
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() | Long.MIN_VALUE);
    }
}
