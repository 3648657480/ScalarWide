package com.scalarwide.main.fp;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1 gate: representation, construction, conversion and special values.
 *
 * <p>The conversion gate is the strong one: {@code of(double).toDouble()} must reproduce the
 * exact bit pattern of every input double, and {@code of(long).toDouble()} must agree with
 * Java's own correctly rounded {@code long -> double} conversion.
 */
class Binary256P1Test {

    private static final long SEED = 12345L;

    // ---------------------------------------------------------------- round trip

    @Test
    void doubleRoundTripIsBitExact() {
        Random rnd = new Random(SEED);
        long[] specials = {
                0x0000_0000_0000_0000L, // +0
                0x8000_0000_0000_0000L, // -0
                0x7FF0_0000_0000_0000L, // +Inf
                0xFFF0_0000_0000_0000L, // -Inf
                0x7FF8_0000_0000_0000L, // NaN
                0x7FF0_0000_0000_0001L, // signalling NaN
                0x0000_0000_0000_0001L, // min subnormal
                0x8000_0000_0000_0001L,
                0x000F_FFFF_FFFF_FFFFL, // max subnormal
                0x0010_0000_0000_0000L, // min normal
                0x7FEF_FFFF_FFFF_FFFFL, // max finite
                0x3FF0_0000_0000_0000L, // 1.0
                0xBFF0_0000_0000_0000L, // -1.0
        };
        for (long bits : specials) {
            checkRoundTrip(bits);
        }
        for (int i = 0; i < 1_000_000; i++) {
            checkRoundTrip(rnd.nextLong());
        }
    }

    private static void checkRoundTrip(long bits) {
        double in = Double.longBitsToDouble(bits);
        double out = Binary256.of(in).toDouble();
        if (Double.isNaN(in)) {
            assertTrue(Double.isNaN(out), "NaN must stay NaN for bits " + Long.toHexString(bits));
            return;
        }
        assertEquals(bits, Double.doubleToRawLongBits(out),
                "round trip differs for " + Long.toHexString(bits));
    }

    // ---------------------------------------------------------------- long conversion

    @Test
    void longToDoubleMatchesJava() {
        Random rnd = new Random(SEED + 1);
        long[] edges = {0, 1, -1, Long.MAX_VALUE, Long.MIN_VALUE,
                1L << 53, (1L << 53) + 1, (1L << 53) - 1, -(1L << 53) - 1};
        for (long v : edges) {
            assertEquals((double) v, Binary256.of(v).toDouble(), "long edge " + v);
        }
        for (int i = 0; i < 1_000_000; i++) {
            long v = rnd.nextLong();
            assertEquals((double) v, Binary256.of(v).toDouble(), "long " + v);
        }
    }

    // ---------------------------------------------------------------- special values

    @Test
    void specialValueTruthTable() {
        assertTrue(Binary256.NaN.isNaN());
        assertFalse(Binary256.NaN.isInfinite());
        assertFalse(Binary256.NaN.isFinite());

        assertTrue(Binary256.POSITIVE_INFINITY.isInfinite());
        assertTrue(Binary256.NEGATIVE_INFINITY.isInfinite());
        assertFalse(Binary256.POSITIVE_INFINITY.isFinite());

        assertTrue(Binary256.ZERO.isZero());
        assertFalse(Binary256.ZERO.signBit());
        assertTrue(Binary256.NEGATIVE_ZERO.isZero());
        assertTrue(Binary256.NEGATIVE_ZERO.signBit());

        assertTrue(Binary256.ONE.isNormal());
        assertFalse(Binary256.ONE.isSubnormal());
        assertTrue(Binary256.MIN_NORMAL.isNormal());
        assertTrue(Binary256.MIN_VALUE.isSubnormal());
        assertFalse(Binary256.MIN_VALUE.isNormal());
        assertTrue(Binary256.MAX_VALUE.isNormal());

        assertFalse(Binary256.ZERO.isNormal());
        assertFalse(Binary256.ZERO.isSubnormal());
        assertFalse(Binary256.NaN.isNormal());
        assertFalse(Binary256.POSITIVE_INFINITY.isNormal());
    }

    @Test
    void derivedConstants() {
        assertEquals(Double.POSITIVE_INFINITY, Binary256.POSITIVE_INFINITY.toDouble());
        assertEquals(Double.POSITIVE_INFINITY, Binary256.MAX_VALUE.toDouble());
        assertEquals(0.0, Binary256.MIN_VALUE.toDouble());
        assertEquals(1.0, Binary256.ONE.toDouble());
        // MIN_NORMAL is 2^-262142, far below binary64's range: it underflows to zero.
        assertEquals(0.0, Binary256.MIN_NORMAL.toDouble());
        assertTrue(Double.isNaN(Binary256.NaN.toDouble()));

        assertEquals(Binary256.EXP_MIN, Binary256.MIN_NORMAL.scaleExponent());
        assertEquals(Binary256.EXP_MIN - (Binary256.PRECISION - 1), Binary256.MIN_VALUE.scaleExponent());
        assertEquals(Binary256.EXP_MAX, Binary256.MAX_VALUE.scaleExponent());
    }

    // ---------------------------------------------------------------- equality and ordering

    @Test
    void equalityFollowsJavaDoubleConventions() {
        assertNotEqualsPair(Binary256.ZERO, Binary256.NEGATIVE_ZERO);
        assertEquals(Binary256.NaN, Binary256.NaN);
        assertEquals(Binary256.NaN.hashCode(), Binary256.NaN.hashCode());
        assertEquals(Binary256.ONE, Binary256.of(1.0));
        assertEquals(Binary256.of(1.0).hashCode(), Binary256.of(1.0).hashCode());
        assertEquals(Binary256.ZERO, Binary256.of(0.0));
        assertEquals(Binary256.NEGATIVE_ZERO, Binary256.of(-0.0));
    }

    private static void assertNotEqualsPair(Binary256 a, Binary256 b) {
        assertFalse(a.equals(b), "expected distinct: " + a + " vs " + b);
    }

    @Test
    void totalOrderSortsLikeDoubleCompareTo() {
        Binary256[] order = {
                Binary256.NEGATIVE_INFINITY,
                Binary256.NEGATIVE_ONE,
                Binary256.of(-Double.MIN_VALUE),
                Binary256.NEGATIVE_ZERO,
                Binary256.ZERO,
                Binary256.MIN_VALUE,
                Binary256.MIN_NORMAL,
                Binary256.ONE,
                Binary256.MAX_VALUE,
                Binary256.POSITIVE_INFINITY,
                Binary256.NaN,
        };
        for (int i = 0; i + 1 < order.length; i++) {
            assertTrue(order[i].compareTo(order[i + 1]) < 0,
                    "expected " + order[i] + " < " + order[i + 1]);
            assertTrue(order[i + 1].compareTo(order[i]) > 0);
        }
        assertEquals(0, Binary256.NaN.compareTo(Binary256.NaN));
        assertEquals(Binary256.Comparison.UNORDERED, Binary256.NaN.compareNumeric(Binary256.ONE));
        assertEquals(Binary256.Comparison.EQUAL, Binary256.ZERO.compareNumeric(Binary256.NEGATIVE_ZERO));
    }
}
