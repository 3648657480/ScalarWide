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
    @Test
    void worksAt1280Bits() {
        FpFormat f = FpFormat.BINARY1280;
        assertEquals(1252, f.precision());
        assertEquals(20, f.limbs());
        Random rnd = new Random(SEED + 3);
        for (int i = 0; i < 400; i++) {
            BigFloat a = randomValue(f, rnd);
            BigFloat b = randomValue(f, rnd);
            assertRef("1280 add", refAdd(f, a, b), a.add(b));
            assertRef("1280 mul", refMul(f, a, b), a.multiply(b));
            if (!b.isZero()) {
                assertRef("1280 div", refDiv(f, a, b), a.divide(b));
            }
        }
        BigFloat max = BigFloat.maxValue(f);
        assertEquals(f.expMax(), max.scaleExponent());
        assertTrue(max.multiply(BigFloat.of(f, 2L)).isInfinite());
        BigFloat third = BigFloat.one(f).divide(BigFloat.of(f, 3L));
        assertTrue(third.multiply(BigFloat.of(f, 3L)).subtract(BigFloat.one(f)).abs()
                .compareTo(BigFloat.of(f, 1e-300)) < 0);
    }

    /** Rounds {@code a/b} from the exact quotient plus its remainder. */
    private static BigFloat refDiv(FpFormat f, BigFloat a, BigFloat b) {
        BigInteger ma = a.magnitude();
        BigInteger mb = b.magnitude();
        int ea = a.lsbExponent();
        int eb = b.lsbExponent();
        int p = f.precision();
        int k = p + 80 + mb.bitLength() - ma.bitLength();
        BigInteger num = ma.shiftLeft(k);
        BigInteger q = num.divide(mb);
        BigInteger r = num.subtract(q.multiply(mb));
        return refRoundSticky(f, (a.signBit() ^ b.signBit()), q, ea - eb - k, r.signum() != 0);
    }

    /** Rounds {@code sign * mag * 2^exp2} with an extra sticky bit, nearest-even. */
    private static BigFloat refRoundSticky(FpFormat f, boolean negative, BigInteger mag, int exp2,
                                           boolean sticky) {
        if (mag.signum() == 0) {
            return negative ? BigFloat.negativeZero(f) : BigFloat.zero(f);
        }
        int p = f.precision();
        int lead = exp2 + mag.bitLength() - 1;
        int lsb = Math.max(lead - (p - 1), f.expMin() - (p - 1));
        int shift = lsb - exp2;
        BigInteger q;
        boolean guard = false;
        boolean low = sticky;
        if (shift <= 0) {
            q = mag.shiftLeft(-shift);
        } else {
            q = mag.shiftRight(shift);
            guard = mag.testBit(shift - 1);
            low = sticky || mag.and(BigInteger.ONE.shiftLeft(shift - 1).subtract(BigInteger.ONE))
                    .signum() != 0;
        }
        if (guard && (low || q.testBit(0))) {
            q = q.add(BigInteger.ONE);
        }
        if (q.signum() == 0) {
            return negative ? BigFloat.negativeZero(f) : BigFloat.zero(f);
        }
        int leadAfter = lsb + q.bitLength() - 1;
        if (leadAfter > f.expMax()) {
            return BigFloat.infinity(f, negative);
        }
        if (leadAfter < f.expMin() - (p - 1)) {
            return negative ? BigFloat.negativeZero(f) : BigFloat.zero(f);
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
        return BigFloat.raw(f, negative ? 1 : 0, leadAfter, out);
    }
    @Test
    void sqrtAndFmaAt237MatchTheFlatImplementation() {
        Random rnd = new Random(SEED + 4);
        for (int i = 0; i < 5_000; i++) {
            BigFloat a = randomValue(F237, rnd);
            BigFloat b = randomValue(F237, rnd);
            BigFloat c = randomValue(F237, rnd);
            assertSame("sqrt", toFlat(a).sqrt(), a.sqrt());
            assertSame("fma", toFlat(a).fma(toFlat(b), toFlat(c)), a.fma(b, c));
        }
    }

    @Test
    void sqrtAndFmaWorkAt1280() {
        FpFormat f = FpFormat.BINARY1280;
        Random rnd = new Random(SEED + 5);
        for (int i = 0; i < 200; i++) {
            BigFloat a = randomValue(f, rnd);
            BigFloat b = randomValue(f, rnd);
            BigFloat c = randomValue(f, rnd);
            assertRef("1280 fma", refFma(f, a, b, c), a.fma(b, c));
            assertRef("1280 sqrt", refSqrt(f, a), a.sqrt());
        }
    }

    @Test
    void decimalRoundTripEveryFormat() {
        for (FpFormat f : new FpFormat[] {F237, F616, FpFormat.BINARY1280}) {
            Random rnd = new Random(SEED + 6);
            int digits = (int) Math.ceil(f.precision() * 0.30102999566398120) + 3;
            for (int i = 0; i < 200; i++) {
                BigFloat v = randomValue(f, rnd);
                BigFloat back = BigFloat.parse(f, v.toString(digits));
                assertRef(f + " round trip", v, back);
            }
        }
    }

    /** Exact sqrt reference: floor(sqrt(value)/2^lsb) then one rounding. */
    private static BigFloat refSqrt(FpFormat f, BigFloat v) {
        if (v.signBit()) {
            return BigFloat.nan(f);
        }
        int p = f.precision();
        BigInteger m = v.magnitude();
        int e = v.lsbExponent();
        int lead = e + m.bitLength() - 1;
        int lsb = Math.floorDiv(lead, 2) - (p - 1);
        BigInteger n = m.shiftLeft(e - 2 * lsb);
        BigInteger root = n.sqrt();
        boolean exact = root.multiply(root).equals(n);
        return refRoundSticky(f, false, root, lsb, !exact);
    }

    /** Exact fma reference. */
    private static BigFloat refFma(FpFormat f, BigFloat a, BigFloat b, BigFloat c) {
        BigInteger prod = signedMag(a).multiply(signedMag(b));
        int pe = a.lsbExponent() + b.lsbExponent();
        int frame = Math.min(pe, c.lsbExponent());
        BigInteger sum = prod.shiftLeft(pe - frame)
                .add(signedMag(c).shiftLeft(c.lsbExponent() - frame));
        return refRoundSticky(f, sum.signum() < 0, sum.abs(), frame, false);
    }
    @Test
    void fullOperationSetAt640() {
        FpFormat f = FpFormat.BINARY640;
        assertEquals(616, f.precision());
        assertEquals(10, f.limbs());
        Random rnd = new Random(SEED + 7);
        for (int i = 0; i < 800; i++) {
            BigFloat a = randomValue(f, rnd);
            BigFloat b = randomValue(f, rnd);
            BigFloat c = randomValue(f, rnd);
            assertRef("640 add", refAdd(f, a, b), a.add(b));
            assertRef("640 mul", refMul(f, a, b), a.multiply(b));
            if (!b.isZero()) {
                assertRef("640 div", refDiv(f, a, b), a.divide(b));
            }
            assertRef("640 fma", refFma(f, a, b, c), a.fma(b, c));
            assertRef("640 sqrt", refSqrt(f, a), a.sqrt());
        }
        BigFloat max = BigFloat.maxValue(f);
        assertEquals(f.expMax(), max.scaleExponent());
        assertTrue(max.multiply(BigFloat.of(f, 2L)).isInfinite());
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
