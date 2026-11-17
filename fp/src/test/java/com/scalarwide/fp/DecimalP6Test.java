package com.scalarwide.fp;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P6 gate: decimal output at a requested precision and correctly rounded parsing.
 *
 * <p>Output is compared against {@link BigDecimal} rounding of the exact value at the same digit
 * count; parsing is compared against an exact-rational reference. Round-tripping through
 * {@code toString(80)} must return the original value bit for bit.
 */
class DecimalP6Test {

    private static final long SEED = 12345L;
    private static final int N = 3000;
    private static final MathContext[] CTX = new MathContext[81];

    static {
        for (int i = 1; i <= 80; i++) {
            CTX[i] = new MathContext(i, RoundingMode.HALF_EVEN);
        }
    }

    @Test
    void outputMatchesBigDecimalAtEveryPrecision() {
        Random rnd = new Random(SEED);
        for (int i = 0; i < N; i++) {
            Binary256 v = random(rnd);
            BigDecimal exact = exact(v);
            for (int digits : new int[] {1, 2, 5, 17, 40, 80}) {
                String want = exact.round(CTX[digits]).toString();
                String got = v.toString(digits);
                assertEquals(strip(want), strip(got), "digits=" + digits + " v=" + v);
            }
        }
    }

    @Test
    void roundingCarryMovesTheScale() {
        // 999.6 at three digits carries into a new decade: the scale must move with it instead of
        // bouncing between the two scales.
        assertEquals("1000", Binary256.of(999.6).toString(3));
        assertEquals("10", Binary256.of(9.6).toString(1));
        assertEquals("0.0000001", Binary256.of(9.99e-8).toString(1));
        // The other direction: a value that truncates one digit short must not stay there
        // (-7 is outside the plain range, so this one prints in scientific notation).
        assertEquals("9E-8", Binary256.of(9.19e-8).toString(1));
    }

    @Test
    void roundTripThroughDecimal() {
        Random rnd = new Random(SEED + 1);
        for (int i = 0; i < N; i++) {
            Binary256 v = random(rnd);
            Binary256 back = Binary256.of(v.toString(80));
            assertEquals(v.scaleExponent(), back.scaleExponent(), "round trip exponent");
            assertEquals(v.signBit(), back.signBit(), "round trip sign");
            for (int k = 3; k >= 0; k--) {
                assertEquals(v.limb(k), back.limb(k), "round trip limb " + k);
            }
        }
    }

    @Test
    void parseMatchesExactRationalRounding() {
        Random rnd = new Random(SEED + 2);
        String[] literals = {"0", "-0", "1", "-1", "0.5", "1.5", "2.5", "1e10", "1e-10",
                "3.14159265358979323846264338327950288", "1.7976931348623157e308",
                "123456789012345678901234567890"};
        for (String s : literals) {
            assertRef("parse " + s, refParse(s), Binary256.of(s));
        }
        for (int i = 0; i < N; i++) {
            long mag = rnd.nextLong();
            int exp = rnd.nextInt(600) - 300;
            String s = (mag < 0 ? "-" : "") + Math.abs(mag) + "e" + exp;
            assertRef("parse " + s, refParse(s), Binary256.of(s));
        }
    }

    @Test
    void parsedValueIsTheNearestRepresentable() {
        // Algorithm-independent: the parsed result must be at least as close to the exact decimal
        // value as either neighbour in the format grid.
        Random rnd = new Random(SEED + 3);
        for (int i = 0; i < 2000; i++) {
            String s = randomLiteral(rnd);
            Binary256 got = Binary256.of(s);
            if (got.isZero() || !got.isFinite()) {
                continue;
            }
            BigDecimal exactV = new BigDecimal(s);
            BigDecimal gotV = toBigDecimal(got);
            BigDecimal ulp = ulpDecimal(got);
            BigDecimal dGot = exactV.subtract(gotV).abs();
            BigDecimal dUp = exactV.subtract(gotV.add(ulp)).abs();
            BigDecimal dDown = exactV.subtract(gotV.subtract(ulp)).abs();
            assertTrue(dGot.compareTo(dUp) <= 0 && dGot.compareTo(dDown) <= 0,
                    "not nearest for " + s);
        }
    }

    private static BigDecimal toBigDecimal(Binary256 v) {
        BigInteger mag = v.signBit() ? magOf(v).negate() : magOf(v);
        int s = v.scaleExponent() - (Binary256.PRECISION - 1);
        BigDecimal base = new BigDecimal(mag);
        return s >= 0 ? base.multiply(new BigDecimal(BigInteger.ONE.shiftLeft(s)))
                : base.divide(new BigDecimal(BigInteger.ONE.shiftLeft(-s)));
    }

    private static BigDecimal ulpDecimal(Binary256 v) {
        int lsb = Math.max(v.scaleExponent(), Binary256.EXP_MIN) - (Binary256.PRECISION - 1);
        return lsb >= 0 ? new BigDecimal(BigInteger.ONE.shiftLeft(lsb))
                : BigDecimal.ONE.divide(new BigDecimal(BigInteger.ONE.shiftLeft(-lsb)));
    }

    private static String randomLiteral(Random rnd) {
        StringBuilder sb = new StringBuilder();
        if (rnd.nextBoolean()) {
            sb.append('-');
        }
        int n = 1 + rnd.nextInt(25);
        for (int i = 0; i < n; i++) {
            sb.append((char) ('0' + rnd.nextInt(10)));
        }
        if (rnd.nextBoolean()) {
            sb.append('e').append(rnd.nextInt(400) - 200);
        }
        return sb.toString();
    }
    // ---------------------------------------------------------------- reference

    private static void assertRef(String label, Binary256 want, Binary256 got) {
        assertEquals(want.scaleExponent(), got.scaleExponent(), label + ": exponent");
        assertEquals(want.signBit(), got.signBit(), label + ": sign");
        for (int i = 3; i >= 0; i--) {
            assertEquals(want.limb(i), got.limb(i), label + ": limb " + i);
        }
    }

    /** Exact decimal of the value; representable because the exponent is small here. */
    private static BigDecimal exact(Binary256 v) {
        BigInteger m = magOf(v);
        int e = v.scaleExponent() - (Binary256.PRECISION - 1);
        BigDecimal bd = new BigDecimal(v.signBit() ? m.negate() : m);
        return e >= 0 ? bd.multiply(new BigDecimal(BigInteger.ONE.shiftLeft(e)))
                : bd.divide(new BigDecimal(BigInteger.ONE.shiftLeft(-e)));
    }

    /** {@code BigDecimal} -> binary, using the exact rational and nearest-even. */
    private static Binary256 refParse(String s) {
        BigDecimal bd = new BigDecimal(s);
        boolean negativeLiteral = s.startsWith("-");
        BigInteger num = bd.unscaledValue();
        int scale = bd.scale();
        boolean negative = negativeLiteral;
        num = num.abs();
        BigInteger den = BigInteger.ONE;
        if (scale > 0) {
            den = BigInteger.TEN.pow(scale);
        } else {
            num = num.multiply(BigInteger.TEN.pow(-scale));
        }
        if (num.signum() == 0) {
            return negative ? Binary256.NEGATIVE_ZERO : Binary256.ZERO;
        }
        int lead = num.bitLength() - den.bitLength();
        if (lead >= 0) {
            if (num.compareTo(den.shiftLeft(lead)) < 0) {
                lead--;
            }
        } else if (num.shiftLeft(-lead).compareTo(den) < 0) {
            lead--;
        }
        int shift = (Binary256.PRECISION - 1) - lead;
        if (shift >= 0) {
            num = num.shiftLeft(shift);
        } else {
            den = den.shiftLeft(-shift);
        }
        BigInteger[] qr = num.divideAndRemainder(den);
        BigInteger q = qr[0];
        int cmp = qr[1].shiftLeft(1).compareTo(den);
        if (cmp > 0 || (cmp == 0 && q.testBit(0))) {
            q = q.add(BigInteger.ONE);
        }
        int e = lead;
        if (q.bitLength() > Binary256.PRECISION) {
            q = q.shiftRight(1);
            e++;
        }
        if (q.bitLength() < Binary256.PRECISION) {
            int grow = Binary256.PRECISION - q.bitLength();
            q = q.shiftLeft(grow);
            e -= grow;
        }
        BigInteger mask = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
        long[] limbs = new long[4];
        for (int k = 0; k < 4; k++) {
            limbs[k] = q.and(mask).longValue();
            q = q.shiftRight(64);
        }
        return Binary256.raw(negative ? 1 : 0, e, limbs[0], limbs[1], limbs[2], limbs[3]);
    }

    private static String strip(String s) {
        if (s.indexOf('E') >= 0 || s.indexOf('e') >= 0) {
            s = new BigDecimal(s).toPlainString();
        }
        if (s.contains(".")) {
            while (s.endsWith("0")) {
                s = s.substring(0, s.length() - 1);
            }
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return s;
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

    private static Binary256 random(Random rnd) {
        int exp = rnd.nextInt(120) - 60;
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
