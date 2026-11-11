package com.scalarwide.fp;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P3 gate: add / subtract / multiply / divide, correctly rounded to nearest-even.
 *
 * <p>Every case is compared against an exact BigInteger rational rounded by an independent
 * reference routine, so "0 errors" means the rounding decisions match, not that the results
 * merely look plausible. Normal range only; overflow, underflow and subnormals are P4.
 */
class ArithP3Test {

    private static final long SEED = 12345L;
    private static final int N = 20_000;

    // ---------------------------------------------------------------- gates

    @Test
    void additionMatchesExactRounding() {
        Random rnd = new Random(SEED);
        for (int i = 0; i < N; i++) {
            Binary256 a = random(rnd);
            Binary256 b = random(rnd);
            assertRef("add", refAdd(a, b), a.add(b));
            assertRef("sub", refAdd(a, b.negate()), a.subtract(b));
        }
    }

    @Test
    void multiplicationMatchesExactRounding() {
        Random rnd = new Random(SEED + 1);
        for (int i = 0; i < N; i++) {
            Binary256 a = random(rnd);
            Binary256 b = random(rnd);
            assertRef("mul", refMul(a, b), a.multiply(b));
        }
    }

    @Test
    void divisionMatchesExactRounding() {
        Random rnd = new Random(SEED + 2);
        for (int i = 0; i < N; i++) {
            Binary256 a = random(rnd);
            Binary256 b = random(rnd);
            assertRef("div", refDiv(a, b), a.divide(b));
        }
    }

    @Test
    void landmarkCases() {
        assertRef("1+1", refAdd(Binary256.ONE, Binary256.ONE), Binary256.ONE.add(Binary256.ONE));
        assertRef("1-1", refAdd(Binary256.ONE, Binary256.ONE.negate()),
                Binary256.ONE.subtract(Binary256.ONE));
        assertRef("x-x", refAdd(Binary256.ONE, Binary256.ONE.negate()),
                Binary256.ONE.add(Binary256.ONE.negate()));
        assertRef("1*1", refMul(Binary256.ONE, Binary256.ONE), Binary256.ONE.multiply(Binary256.ONE));
        assertRef("1/1", refDiv(Binary256.ONE, Binary256.ONE), Binary256.ONE.divide(Binary256.ONE));
        assertRef("-1*1", refMul(Binary256.NEGATIVE_ONE, Binary256.ONE),
                Binary256.NEGATIVE_ONE.multiply(Binary256.ONE));
        // 1/3 exercises a non-terminating quotient
        Binary256 third = Binary256.ONE.divide(Binary256.of(3.0));
        assertRef("1/3", refDiv(Binary256.ONE, Binary256.of(3.0)), third);
        // a value and its negation cancel exactly
        assertEquals(0, Binary256.of(0.0).compareTo(Binary256.ONE.add(Binary256.ONE.negate())));
    }

    // ---------------------------------------------------------------- assertions

    private static void assertRef(String op, Binary256 want, Binary256 got) {
        assertEquals(want.scaleExponent(), got.scaleExponent(), op + ": exponent");
        assertEquals(want.signBit(), got.signBit(), op + ": sign");
        for (int i = 3; i >= 0; i--) {
            assertEquals(want.limb(i), got.limb(i), op + ": limb " + i);
        }
    }

    // ---------------------------------------------------------------- reference

    /** Exact value as {@code num * 2^exp2}. */
    private static BigInteger[] exact(Binary256 v) {
        return new BigInteger[] {v.signBit() ? magOf(v).negate() : magOf(v),
                BigInteger.valueOf(v.scaleExponent() - 255)};
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

    private static Binary256 refAdd(Binary256 a, Binary256 b) {
        BigInteger[] va = exact(a);
        BigInteger[] vb = exact(b);
        int e2 = Math.min(va[1].intValue(), vb[1].intValue());
        BigInteger num = va[0].shiftLeft(va[1].intValue() - e2)
                .add(vb[0].shiftLeft(vb[1].intValue() - e2));
        return round(num, e2, false);
    }

    private static Binary256 refMul(Binary256 a, Binary256 b) {
        BigInteger[] va = exact(a);
        BigInteger[] vb = exact(b);
        return round(va[0].multiply(vb[0]), va[1].intValue() + vb[1].intValue(), false);
    }

    private static Binary256 refDiv(Binary256 a, Binary256 b) {
        BigInteger[] va = exact(a);
        BigInteger[] vb = exact(b);
        BigInteger n = va[0].abs();
        BigInteger d = vb[0].abs();
        int exp2 = va[1].intValue() - vb[1].intValue();
        int k = 260 + d.bitLength() - n.bitLength();
        BigInteger[] qr = n.shiftLeft(k).divideAndRemainder(d);
        int sign = va[0].signum() * vb[0].signum() < 0 ? 1 : 0;
        Binary256 r = round(qr[0], exp2 - k, !qr[1].equals(BigInteger.ZERO));
        return sign == 0 ? r : r.negate();
    }

    /** Rounds {@code sign * mag * 2^exp2} to 256 significant bits, nearest-even. */
    private static Binary256 round(BigInteger num, int exp2, boolean extraSticky) {
        if (num.signum() == 0) {
            return Binary256.ZERO;
        }
        int sign = num.signum() < 0 ? 1 : 0;
        BigInteger mag = num.abs();
        int bl = mag.bitLength();
        int shift = bl - Binary256.PRECISION;
        BigInteger m;
        boolean guard;
        boolean sticky;
        if (shift > 0) {
            m = mag.shiftRight(shift);
            guard = mag.testBit(shift - 1);
            sticky = extraSticky
                    || mag.and(BigInteger.ONE.shiftLeft(shift - 1).subtract(BigInteger.ONE)).signum() != 0;
        } else {
            m = mag.shiftLeft(-shift);
            guard = false;
            sticky = extraSticky;
        }
        if (guard && (sticky || m.testBit(0))) {
            m = m.add(BigInteger.ONE);
        }
        int e = exp2 + bl - 1;
        if (m.bitLength() > Binary256.PRECISION) {
            m = m.shiftRight(1);
            e++;
        }
        BigInteger[] limbs = new BigInteger[4];
        BigInteger work = m;
        for (int i = 0; i < 4; i++) {
            limbs[i] = work.and(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE));
            work = work.shiftRight(64);
        }
        return Binary256.raw(sign, e, limbs[0].longValue(), limbs[1].longValue(),
                limbs[2].longValue(), limbs[3].longValue());
    }

    private static Binary256 random(Random rnd) {
        int exp = rnd.nextInt(200) - 100;
        return Binary256.raw(rnd.nextBoolean() ? 1 : 0, exp,
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() | Long.MIN_VALUE);
    }
}
