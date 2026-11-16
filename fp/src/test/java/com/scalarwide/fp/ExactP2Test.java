package com.scalarwide.fp;

import com.scalarwide.Wide512;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2 gate: the exact primitives, with <em>no rounding anywhere</em>.
 *
 * <p>Each primitive describes {@code sign * (mag - down*delta) * 2^(exp-255)} with
 * {@code 0 <= delta < 1}, {@code sticky} telling whether {@code delta} is non-zero and
 * {@code down} which side of {@code mag} the true value is on. The gate brackets the exact value
 * computed with BigInteger and requires the bracket to be tight (one ULP wide).
 */
class ExactP2Test {

    private static final long SEED = 12345L;
    private static final int N = 100_000;

    // ---------------------------------------------------------------- gates

    @Test
    void multiplyIsExact() {
        Random rnd = new Random(SEED);
        for (int i = 0; i < N; i++) {
            Binary256 a = random(rnd);
            Binary256 b = random(rnd);
            Exact e = Exact.mul(a, b);
            BigInteger[] va = value(a);
            BigInteger[] vb = value(b);
            BigInteger num = va[0].multiply(vb[0]);
            int exp2 = va[1].intValue() + vb[1].intValue();
            assertFalse(e.sticky(), "mul must be exact");
            assertFalse(e.down(), "mul has no truncation direction");
            assertEquals(0, cmp(num.abs(), exp2, magOf(e), e.exp() - (Binary256.PRECISION - 1)), "mul must be bit-exact");
            checkBracket(e, num, exp2, "mul");
        }
    }

    @Test
    void additionBracketsTheExactSum() {
        Random rnd = new Random(SEED + 1);
        int cancels = 0;
        for (int i = 0; i < N; i++) {
            Binary256 a = random(rnd);
            Binary256 b = rnd.nextBoolean() ? random(rnd) : a.negate();
            Exact e = Exact.alignAdd(a, b);
            BigInteger[] va = value(a);
            BigInteger[] vb = value(b);
            int exp2 = Math.min(va[1].intValue(), vb[1].intValue());
            BigInteger num = va[0].shiftLeft(va[1].intValue() - exp2)
                    .add(vb[0].shiftLeft(vb[1].intValue() - exp2));
            if (num.signum() == 0) {
                cancels++;
                assertTrue(e.mag().isZero(), "exact cancellation must give zero magnitude");
                continue;
            }
            checkBracket(e, num, exp2, "add");
        }
        assertTrue(cancels > 0, "the pool should include exact cancellations");
    }

    @Test
    void nearCancellationIsBracketed() {
        // 1 - (the least subnormal): differs far below the precision, must stay bracketed
        Binary256 a = Binary256.ONE;
        Binary256 b = Binary256.raw(1, Binary256.MIN_VALUE.scaleExponent(),
                0L, 0L, 0L, Long.MIN_VALUE);
        Exact e = Exact.alignAdd(a, b);
        BigInteger[] va = value(a);
        BigInteger[] vb = value(b);
        int exp2 = Math.min(va[1].intValue(), vb[1].intValue());
        BigInteger num = va[0].shiftLeft(va[1].intValue() - exp2)
                .add(vb[0].shiftLeft(vb[1].intValue() - exp2));
        assertTrue(e.sticky(), "the tiny subtrahend must leave a remainder");
        assertTrue(e.down(), "truncated subtraction leaves the result above the true value");
        checkBracket(e, num, exp2, "near-cancel");
    }

    @Test
    void divisionBracketsTheExactQuotient() {
        Random rnd = new Random(SEED + 2);
        for (int i = 0; i < N; i++) {
            Binary256 a = random(rnd);
            Binary256 b = random(rnd);
            Exact e = Exact.div(a, b);
            BigInteger[] va = value(a);
            BigInteger[] vb = value(b);
            BigInteger numAbs = va[0].abs();
            BigInteger denAbs = vb[0].abs();
            int exp2 = va[1].intValue() - vb[1].intValue();
            int e2 = e.exp() - (Binary256.PRECISION - 1);
            int d = exp2 - e2;
            BigInteger left = d >= 0 ? numAbs.shiftLeft(d) : numAbs;
            BigInteger right = d >= 0 ? denAbs : denAbs.shiftLeft(-d);
            BigInteger mag = magOf(e);
            BigInteger floor = mag.multiply(right);
            assertTrue(left.compareTo(floor) >= 0, "quotient must not exceed the true value");
            assertTrue(left.compareTo(mag.add(BigInteger.ONE).multiply(right)) < 0,
                    "quotient must be within one ULP");
            assertEquals(left.compareTo(floor) != 0, e.sticky(),
                    "sticky must mean a non-zero remainder");
            assertEquals(va[0].signum() * vb[0].signum() < 0 ? 1 : 0, e.sign(), "sign");
        }
    }

    // ---------------------------------------------------------------- bracket

    private static void checkBracket(Exact e, BigInteger num, int exp2, String label) {
        assertTrue(num.signum() != 0, label + ": exact value must be non-zero");
        int e2 = e.exp() - (Binary256.PRECISION - 1);
        assertEquals(num.signum() < 0 ? 1 : 0, e.sign(), label + ": sign");

        BigInteger abs = num.abs();
        BigInteger mag = magOf(e);
        int rel = cmp(abs, exp2, mag, e2);

        if (!e.down()) {
            assertTrue(rel >= 0, label + ": |V| must be >= mag");
            assertTrue(cmp(abs, exp2, mag.add(BigInteger.ONE), e2) < 0,
                    label + ": |V| must be < mag+1");
        } else {
            assertTrue(rel <= 0, label + ": |V| must be <= mag");
            assertTrue(cmp(abs, exp2, mag.subtract(BigInteger.ONE), e2) > 0,
                    label + ": |V| must be > mag-1");
        }
        assertEquals(rel != 0, e.sticky(), label + ": sticky must match the fraction");
    }

    /** Sign of {@code a*2^ae - b*2^be}. */
    private static int cmp(BigInteger a, int ae, BigInteger b, int be) {
        int d = ae - be;
        return d >= 0
                ? a.shiftLeft(d).compareTo(b)
                : a.compareTo(b.shiftLeft(-d));
    }

    // ---------------------------------------------------------------- helpers

    private static BigInteger[] value(Binary256 v) {
        return new BigInteger[] {v.signBit() ? magOf(v).negate() : magOf(v),
                BigInteger.valueOf(v.scaleExponent() - (Binary256.PRECISION - 1))};
    }

    private static BigInteger magOf(Binary256 v) {
        BigInteger r = BigInteger.ZERO;
        for (int i = 3; i >= 0; i--) {
            r = r.shiftLeft(64).or(unsigned(v.limb(i)));
        }
        return r;
    }

    private static BigInteger magOf(Exact e) {
        BigInteger r = BigInteger.ZERO;
        Wide512 m = e.mag();
        for (int i = 7; i >= 0; i--) {
            r = r.shiftLeft(64).or(unsigned(m.limb(i)));
        }
        return r;
    }

    private static BigInteger unsigned(long v) {
        BigInteger b = BigInteger.valueOf(v & Long.MAX_VALUE);
        return v < 0 ? b.setBit(63) : b;
    }

    private static Binary256 random(Random rnd) {
        int exp = rnd.nextInt(200) - 100;
        return Binary256.raw(rnd.nextBoolean() ? 1 : 0, exp,
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), topLimb(rnd));
    }
    /** Top limb of a normalised significand: leading bit set, nothing above it. */
    private static long topLimb(Random r) {
        int used = Binary256.PRECISION - 64 * 3;
        long mask = (1L << used) - 1L;
        return (r.nextLong() & mask) | (1L << (used - 1));
    }
}
