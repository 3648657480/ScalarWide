package com.scalarwide;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Sparse fast-path gates (mc hard-wiring layer): operands that fit 128 bits
 * take the 128-bit shortcuts; results must match BigInteger exactly, and
 * mixed (dense) operands must fall back to the generic path unchanged.
 */
class SparseFastPathTest {

    @Test
    void sparseBothOperands() {
        Random rnd = new Random(424242L);
        for (int i = 0; i < 20_000; i++) {
            BigInteger a = new BigInteger(1 + rnd.nextInt(128), rnd);
            BigInteger b = new BigInteger(1 + rnd.nextInt(127), rnd).add(BigInteger.ONE);
            Wide256 wa = Wide256.fromBigInteger(a);
            Wide256 wb = Wide256.fromBigInteger(b);
            assertEquals(a.multiply(b), wa.mul(wb).toBigInteger(), "mul " + a + "*" + b);
            assertEquals(a.multiply(b), wa.mulFull(wb).toBigInteger(), "mulFull " + a + "*" + b);
            assertEquals(BigInteger.ZERO, wa.mulHigh(wb).toBigInteger(), "mulHigh " + a + "*" + b);
            assertEquals(a.divide(b), wa.div(wb).toBigInteger(), "div " + a + "/" + b);
            assertEquals(a.remainder(b), wa.rem(wb).toBigInteger(), "rem " + a + "%" + b);
        }
    }

    @Test
    void boundaryAndMixedFallback() {
        BigInteger max128 = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);
        Wide256 wm = Wide256.fromBigInteger(max128);
        assertEquals(max128.multiply(max128), wm.mul(wm).toBigInteger());
        assertEquals(max128.multiply(max128), wm.mulFull(wm).toBigInteger());
        assertEquals(BigInteger.ZERO, wm.mulHigh(wm).toBigInteger());
        assertEquals(BigInteger.ONE, wm.div(wm).toBigInteger());
        assertEquals(BigInteger.ZERO, wm.rem(wm).toBigInteger());

        Random rnd = new Random(777777L);
        for (int i = 0; i < 8000; i++) {
            BigInteger a = rnd.nextBoolean() ? new BigInteger(128, rnd) : new BigInteger(256, rnd);
            BigInteger b = rnd.nextBoolean() ? new BigInteger(128, rnd) : new BigInteger(256, rnd);
            b = b.max(BigInteger.ONE);
            Wide256 wa = Wide256.fromBigInteger(a);
            Wide256 wb = Wide256.fromBigInteger(b);
            assertEquals(a.multiply(b).and(BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE)),
                    wa.mul(wb).toBigInteger(), "mixed mul");
            assertEquals(a.multiply(b), wa.mulFull(wb).toBigInteger(), "mixed mulFull");
            assertEquals(a.multiply(b).shiftRight(256), wa.mulHigh(wb).toBigInteger(), "mixed mulHigh");
            assertEquals(a.divide(b), wa.div(wb).toBigInteger(), "mixed div");
            assertEquals(a.remainder(b), wa.rem(wb).toBigInteger(), "mixed rem");
        }
    }

    @Test
    void sparseDivByZeroThrows() {
        assertThrows(ArithmeticException.class, () -> Wide256.of(5).div(Wide256.ZERO));
        assertThrows(ArithmeticException.class, () -> Wide256.of(5).rem(Wide256.ZERO));
    }

    @Test
    void wide512SparseBothOperands() {
        Random rnd = new Random(512512L);
        BigInteger mask512 = BigInteger.ONE.shiftLeft(512).subtract(BigInteger.ONE);
        for (int i = 0; i < 10_000; i++) {
            BigInteger a = new BigInteger(1 + rnd.nextInt(255), rnd);
            BigInteger b = new BigInteger(1 + rnd.nextInt(254), rnd).add(BigInteger.ONE);
            Wide512 wa = Wide512.fromBigInteger(a);
            Wide512 wb = Wide512.fromBigInteger(b);
            assertEquals(a.multiply(b), wa.mul(wb).toBigInteger(), "mul " + a + "*" + b);
            assertEquals(BigInteger.ZERO, wa.mulHigh(wb).toBigInteger(), "mulHigh " + a + "*" + b);
            assertEquals(a.divide(b), wa.div(wb).toBigInteger(), "div " + a + "/" + b);
            assertEquals(a.remainder(b), wa.rem(wb).toBigInteger(), "rem " + a + "%" + b);
        }
        BigInteger max256 = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);
        Wide512 wm = Wide512.fromBigInteger(max256);
        assertEquals(max256.multiply(max256), wm.mul(wm).toBigInteger());
        assertEquals(BigInteger.ZERO, wm.mulHigh(wm).toBigInteger());
        assertEquals(BigInteger.ONE, wm.div(wm).toBigInteger());
    }

    @Test
    void wide512MixedFallback() {
        Random rnd = new Random(513513L);
        BigInteger mask512 = BigInteger.ONE.shiftLeft(512).subtract(BigInteger.ONE);
        for (int i = 0; i < 5000; i++) {
            BigInteger a = rnd.nextBoolean() ? new BigInteger(256, rnd) : new BigInteger(512, rnd);
            BigInteger b = rnd.nextBoolean() ? new BigInteger(256, rnd) : new BigInteger(512, rnd);
            b = b.max(BigInteger.ONE);
            Wide512 wa = Wide512.fromBigInteger(a);
            Wide512 wb = Wide512.fromBigInteger(b);
            assertEquals(a.multiply(b).and(mask512), wa.mul(wb).toBigInteger(), "mixed mul");
            assertEquals(a.multiply(b).shiftRight(512), wa.mulHigh(wb).toBigInteger(), "mixed mulHigh");
            assertEquals(a.divide(b), wa.div(wb).toBigInteger(), "mixed div");
            assertEquals(a.remainder(b), wa.rem(wb).toBigInteger(), "mixed rem");
        }
        assertThrows(ArithmeticException.class, () -> Wide512.of(5).div(Wide512.ZERO));
    }
}
