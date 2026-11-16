package com.scalarwide.fp;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate for the array-based engine.
 *
 * <p>Two independent checks:
 * <ul>
 *   <li>at the 237-bit format it must agree <em>bit for bit</em> with the flat-field
 *       {@link Binary256}, which is a genuinely separate implementation;</li>
 *   <li>at 616 bits (Long2's {@code Binary640}) it must match exact rational rounding, which the
 *       flat implementation cannot even represent.</li>
 * </ul>
 */
class BigFloatTest {

    private static final long SEED = 12345L;
    private static final FpFormat F237 = FpFormat.BINARY256;
    private static final FpFormat F616 = FpFormat.BINARY640;

    @Test
    void matchesFlatImplementationAt237() {
        Random rnd = new Random(SEED);
        for (int i = 0; i < 20_000; i++) {
            double d = randomDouble(rnd);
            double e = randomDouble(rnd);
            Binary256 fa = Binary256.of(d);
            Binary256 fb = Binary256.of(e);
            BigFloat aa = BigFloat.of(F237, d);
            BigFloat ab = BigFloat.of(F237, e);
            assertSame("add", fa.add(fb), aa.add(ab));
            assertSame("mul", fa.multiply(fb), aa.multiply(ab));
            assertSame("sub", fa.subtract(fb), aa.add(ab.negate()));
        }
    }

    @Test
    void constantsMatchTheFlatFormat() {
        assertSame("one", Binary256.ONE, BigFloat.one(F237));
        assertSame("zero", Binary256.ZERO, BigFloat.zero(F237));
        assertSame("max", Binary256.MAX_VALUE, BigFloat.maxValue(F237));
        BigFloat max616 = BigFloat.maxValue(F616);
        assertTrue(max616.isFinite() && !max616.isZero());
        assertEquals(F616.expMax(), max616.scaleExponent());
    }

    @Test
    void roundsCorrectlyAt616() {
        Random rnd = new Random(SEED + 1);
        for (int i = 0; i < 5_000; i++) {
            BigFloat a = randomValue(F616, rnd);
            BigFloat b = randomValue(F616, rnd);
            assertRef("add", refAdd(F616, a, b), a.add(b));
            assertRef("mul", refMul(F616, a, b), a.multiply(b));
        }
    }

    @Test
    void exponentLandmarksAt616() {
        BigFloat max = BigFloat.maxValue(F616);
        assertTrue(max.multiply(BigFloat.of(F616, 2L)).isInfinite());
        BigFloat one = BigFloat.one(F616);
        assertEquals(one, one.multiply(one));
        assertEquals(one, one.add(BigFloat.zero(F616)));
        assertTrue(one.subtract(one).isZero());
        assertTrue(BigFloat.infinity(F616, false).add(BigFloat.infinity(F616, true)).isNaN());
    }

    @Test
    void matchesFlatImplementationOnRawValuesAt237() {
        // Same input style as the 616 test: full-precision random significands, exponents close
        // together, which exercises alignment and cancellation rather than neat doubles.
        Random rnd = new Random(SEED + 9);
        int checked211 = 0;
        for (int i = 0; i < 5_000; i++) {
            BigFloat a = randomValue(F237, rnd);
            BigFloat b = randomValue(F237, rnd);
            Binary256 fa = toFlat(a);
            Binary256 fb = toFlat(b);
            BigFloat gotAdd = a.add(b);
            Binary256 wantAdd = fa.add(fb);
            assertEquals(wantAdd.signBit(), gotAdd.signBit(), "add sign i=" + i);
            assertEquals(wantAdd.scaleExponent() - (Binary256.PRECISION - 1), gotAdd.lsbExponent(),
                    "add lsb i=" + i + " a.exp=" + a.scaleExponent() + " b.exp=" + b.scaleExponent());
            assertEquals(magOf(wantAdd), gotAdd.magnitude(), "add mag i=" + i);
            checked211++;
        }
        assertTrue(checked211 == 5_000);
    }

    private static Binary256 toFlat(BigFloat v) {
        java.math.BigInteger m = v.magnitude();
        long[] l = new long[4];
        for (int i = 0; i < 4; i++) {
            l[i] = m.and(java.math.BigInteger.ONE.shiftLeft(64).subtract(java.math.BigInteger.ONE)).longValue();
            m = m.shiftRight(64);
        }
        return Binary256.raw(v.signBit() ? 1 : 0, v.scaleExponent(), l[0], l[1], l[2], l[3]);
    }
    // ---------------------------------------------------------------- helpers

    private static double randomDouble(Random r) {
        return (r.nextDouble() - 0.5) * Math.pow(2, r.nextInt(60) - 30);
    }

    private static BigFloat randomValue(FpFormat f, Random r) {
        int limbs = f.limbs();
        long[] m = new long[limbs];
        for (int i = 0; i < limbs; i++) {
            m[i] = r.nextLong();
        }
        m[limbs - 1] &= f.topLimbMask();
        m[limbs - 1] |= 1L << (f.precision() - 64 * (limbs - 1) - 1);
        return BigFloat.raw(f, r.nextInt(2), r.nextInt(80) - 40, m);
    }

    private static void assertSame(String what, Binary256 want, BigFloat got) {
        assertEquals(want.signBit(), got.signBit(), what + ": sign");
        assertEquals(want.scaleExponent() - (Binary256.PRECISION - 1), got.lsbExponent(), what + ": lsb");
        assertEquals(magOf(want), got.magnitude(), what + ": magnitude");
    }

    private static BigFloat refAdd(FpFormat f, BigFloat a, BigFloat b) {
        BigInteger na = signedMag(a);
        BigInteger nb = signedMag(b);
        int e = Math.min(a.lsbExponent(), b.lsbExponent());
        BigInteger sum = na.shiftLeft(a.lsbExponent() - e).add(nb.shiftLeft(b.lsbExponent() - e));
        return refRound(f, sum, e);
    }

    private static BigFloat refMul(FpFormat f, BigFloat a, BigFloat b) {
        BigInteger prod = signedMag(a).multiply(signedMag(b));
        return refRound(f, prod, a.lsbExponent() + b.lsbExponent());
    }

    private static BigInteger signedMag(BigFloat v) {
        return v.signBit() ? v.magnitude().negate() : v.magnitude();
    }

    /** Rounds {@code num * 2^exp2} to the format, nearest-even. */
    private static BigFloat refRound(FpFormat f, BigInteger num, int exp2) {
        if (num.signum() == 0) {
            return BigFloat.zero(f);
        }
        int sign = num.signum() < 0 ? 1 : 0;
        BigInteger mag = num.abs();
        int p = f.precision();
        int lead = exp2 + mag.bitLength() - 1;
        int lsb = Math.max(lead - (p - 1), f.expMin() - (p - 1));
        int shift = lsb - exp2;
        BigInteger q;
        boolean guard = false;
        boolean sticky = false;
        if (shift <= 0) {
            q = mag.shiftLeft(-shift);
        } else {
            q = mag.shiftRight(shift);
            guard = mag.testBit(shift - 1);
            sticky = mag.and(BigInteger.ONE.shiftLeft(shift - 1).subtract(BigInteger.ONE)).signum() != 0;
        }
        if (guard && (sticky || q.testBit(0))) {
            q = q.add(BigInteger.ONE);
        }
        if (q.signum() == 0) {
            return sign == 0 ? BigFloat.zero(f) : BigFloat.negativeZero(f);
        }
        int leadAfter = lsb + q.bitLength() - 1;
        if (leadAfter > f.expMax()) {
            return BigFloat.infinity(f, sign != 0);
        }
        if (leadAfter < f.expMin() - (p - 1)) {
            return sign == 0 ? BigFloat.zero(f) : BigFloat.negativeZero(f);
        }
        BigInteger norm = q.shiftLeft(p - q.bitLength());
        int limbs = f.limbs();
        long[] out = new long[limbs];
        BigInteger work = norm;
        BigInteger mask = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
        for (int i = 0; i < limbs; i++) {
            out[i] = work.and(mask).longValue();
            work = work.shiftRight(64);
        }
        return BigFloat.raw(f, sign, leadAfter, out);
    }

    private static void assertRef(String what, BigFloat want, BigFloat got) {
        assertEquals(want.signBit(), got.signBit(), what + ": sign");
        assertEquals(want.lsbExponent(), got.lsbExponent(), what + ": lsb");
        assertEquals(want.magnitude(), got.magnitude(), what + ": magnitude");
    }

    private static BigInteger magOf(Binary256 v) {
        BigInteger r = BigInteger.ZERO;
        for (int i = 3; i >= 0; i--) {
            BigInteger w = BigInteger.valueOf(v.limb(i) & Long.MAX_VALUE);
            if (v.limb(i) < 0) {
                w = w.setBit(63);
            }
            r = r.shiftLeft(64).or(w);
        }
        return r;
    }
}
