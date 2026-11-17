package com.scalarwide.main.fp;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Differential gate for the flat 640-bit format.
 *
 * <p>The reference is {@link BigFloat} at the same {@link FpFormat}: it is the array-based
 * implementation, independently written and already cross-validated against the 256-bit flat type
 * at {@code p = 237}. Operands are built from long decimal literals so they carry full-width
 * significands rather than the 53 bits a {@code double} would give.
 */
class Binary640Test {

    private static final FpFormat F = FpFormat.BINARY640;
    private static final long SEED = 12345L;
    private static final int N = 500;

    @Test
    void parseMatchesBigFloat() {
        Random rnd = new Random(SEED);
        for (int i = 0; i < N; i++) {
            String lit = literal(rnd);
            assertSame("parse " + lit, Binary640.of(lit), BigFloat.parse(F, lit));
        }
    }

    @Test
    void arithmeticMatchesBigFloat() {
        Random rnd = new Random(SEED + 1);
        for (int i = 0; i < N; i++) {
            String la = literal(rnd);
            String lb = literal(rnd);
            Binary640 a = Binary640.of(la);
            Binary640 b = Binary640.of(lb);
            BigFloat ba = BigFloat.parse(F, la);
            BigFloat bb = BigFloat.parse(F, lb);
            assertRef("add", a.add(b), WideRef.addOrSub(F, a.mag(), a.scaleExponent(), a.signBit(), b.mag(), b.scaleExponent(), b.signBit(), true));
            assertRef("subtract", a.subtract(b), WideRef.addOrSub(F, a.mag(), a.scaleExponent(), a.signBit(), b.mag(), b.scaleExponent(), b.signBit(), false));
            assertSame("multiply", a.multiply(b), ba.multiply(bb));
            if (!b.isZero()) {
                assertSame("divide", a.divide(b), ba.divide(bb));
            }
        }
    }

    @Test
    void sqrtMatchesBigFloat() {
        Random rnd = new Random(SEED + 2);
        for (int i = 0; i < N; i++) {
            String lit = literal(rnd);
            Binary640 a = Binary640.of(lit).abs();
            assertSame("sqrt " + lit, a.sqrt(), BigFloat.parse(F, lit).abs().sqrt());
        }
    }

    @Test
    void fmaMatchesBigFloat() {
        Random rnd = new Random(SEED + 3);
        for (int i = 0; i < N; i++) {
            String la = literal(rnd);
            String lb = literal(rnd);
            String lc = literal(rnd);
            Binary640 a = Binary640.of(la);
            Binary640 b = Binary640.of(lb);
            Binary640 c = Binary640.of(lc);
            BigFloat ba = BigFloat.parse(F, la);
            BigFloat bb = BigFloat.parse(F, lb);
            BigFloat bc = BigFloat.parse(F, lc);
            assertSame("fma", a.fma(b, c), ba.fma(bb, bc));
        }
    }

    @Test
    void specialValuesFollowIeee() {
        Binary640 inf = Binary640.POSITIVE_INFINITY;
        Binary640 ninf = Binary640.NEGATIVE_INFINITY;
        assertTrue(inf.add(ninf).isNaN());
        assertTrue(Binary640.ZERO.divide(Binary640.ZERO).isNaN());
        assertTrue(inf.multiply(Binary640.ZERO).isNaN());
        assertEquals(inf, Binary640.ONE.divide(Binary640.ZERO));
        assertEquals(Binary640.NEGATIVE_ZERO, Binary640.NEGATIVE_ONE.divide(Binary640.POSITIVE_INFINITY));
        assertEquals(Binary640.ZERO, Binary640.ONE.multiply(Binary640.ZERO));
        assertTrue(Binary640.NaN.add(Binary640.ONE).isNaN());
        assertTrue(Binary640.NaN.equals(Binary640.NaN));
        assertEquals(0, Binary640.NaN.compareTo(Binary640.NaN));
        assertTrue(Binary640.NaN.compareTo(inf) > 0);
        // MAX_VALUE * 2 overflows, MIN_VALUE / 2 underflows to zero
        assertTrue(Binary640.MAX_VALUE.multiply(Binary640.of(2L)).isInfinite());
        assertTrue(Binary640.MIN_VALUE.divide(Binary640.of(2L)).isZero());
    }

    @Test
    void decimalRoundTrip() {
        Random rnd = new Random(SEED + 4);
        for (int i = 0; i < N / 5; i++) {
            Binary640 v = Binary640.of(literal(rnd));
            Binary640 back = Binary640.of(v.toString(420));
            assertEquals(0, v.compareTo(back), "round trip");
            assertEquals(v.scaleExponent(), back.scaleExponent(), "round trip exponent");
            for (int k = 0; k < v.mag().length; k++) {
                assertEquals(v.mag()[k], back.mag()[k], "round trip limb " + k);
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private static void assertSame(String label, Binary640 a, BigFloat b) {
        boolean az = a.isZero();
        assertEquals(b.isZero(), az, label + ": zero");
        if (!az) {
            assertEquals(b.scaleExponent(), a.scaleExponent(), label + ": exponent");
        }
        assertEquals(b.signBit(), a.signBit(), label + ": sign");
        for (int i = 0; i < a.mag().length; i++) {
            assertEquals(b.limbs()[i], a.mag()[i], label + ": limb " + i);
        }
    }

    private static String literal(Random rnd) {
        StringBuilder sb = new StringBuilder();
        if (rnd.nextBoolean()) {
            sb.append('-');
        }
        int digits = 1 + rnd.nextInt(320);
        for (int i = 0; i < digits; i++) {
            sb.append((char) ('0' + rnd.nextInt(10)));
        }
        if (rnd.nextBoolean()) {
            sb.append('e').append(rnd.nextInt(2000) - 1000);
        }
        return sb.toString();
    }
    private static void assertRef(String label, Binary640 v, WideRef.Value r) {
        boolean zero = v.isZero();
        for (int i = 0; i < v.mag().length; i++) {
            if (r.mag()[i] != 0L) {
                zero = false;
                break;
            }
        }
        assertEquals(zero, v.isZero(), label + ": zero");
        if (!v.isZero()) {
            assertEquals(r.exp(), v.scaleExponent(), label + ": exponent");
        }
        assertEquals(r.sign() != 0, v.signBit(), label + ": sign");
        for (int i = 0; i < v.mag().length; i++) {
            assertEquals(r.mag()[i], v.mag()[i], label + ": limb " + i);
        }
    }
}
