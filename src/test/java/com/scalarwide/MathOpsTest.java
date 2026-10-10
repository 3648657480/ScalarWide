package com.scalarwide;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Math-completeness ops (div/rem/pow/bitCount/min/max/abs) vs BigInteger. */
class MathOpsTest {

    @Test
    void wide128Ops() {
        ops128();
    }

    @Test
    void wide256Ops() {
        ops256();
    }

    @Test
    void wide384Ops() {
        ops384();
    }

    @Test
    void wide512Ops() {
        ops512();
    }

    @Test
    void wide768Ops() {
        ops768();
    }

    @Test
    void wide1024Ops() {
        ops1024();
    }

    @Test
    void divisionByZeroThrows() {
        assertThrows(ArithmeticException.class, () -> Wide128.ONE.div(Wide128.ZERO));
        assertThrows(ArithmeticException.class, () -> Wide256.ONE.rem(Wide256.ZERO));
        assertThrows(ArithmeticException.class, () -> Wide512.ONE.div(Wide512.ZERO));
        assertThrows(ArithmeticException.class, () -> Wide1024.ONE.rem(Wide1024.ZERO));
    }

    @Test
    void negativeExponentThrows() {
        assertThrows(IllegalArgumentException.class, () -> Wide256.of(2).pow(-1));
    }

    private static BigInteger randomNonZero(Random rnd, int bits) {
        BigInteger d = new BigInteger(1 + rnd.nextInt(bits), rnd);
        return d.signum() == 0 ? BigInteger.ONE : d;
    }

    private static BigInteger absOracle(BigInteger a, int bits) {
        return a.testBit(bits - 1) ? BigInteger.ONE.shiftLeft(bits).subtract(a) : a;
    }

    private static void ops128() {
        Random rnd = new Random(128001L);
        for (int i = 0; i < 3000; i++) {
            BigInteger a = new BigInteger(128, rnd);
            BigInteger d = randomNonZero(rnd, 128);
            Wide128 wa = Wide128.fromBigInteger(a);
            Wide128 wd = Wide128.fromBigInteger(d);
            assertEquals(a.divide(d), wa.div(wd).toBigInteger(), "div " + a + "/" + d);
            assertEquals(a.remainder(d), wa.rem(wd).toBigInteger(), "rem " + a + "%" + d);
        }
        Wide128 max = Wide128.MAX_VALUE;
        assertEquals(max.toBigInteger(), max.div(Wide128.ONE).toBigInteger());
        assertEquals(BigInteger.ONE, max.div(Wide128.MAX_VALUE).toBigInteger());
        assertEquals(BigInteger.ZERO, max.rem(Wide128.MAX_VALUE).toBigInteger());
        assertEquals(BigInteger.ZERO, Wide128.ZERO.div(Wide128.of(7)).rem(Wide128.of(7)).toBigInteger());
        for (int i = 0; i < 200; i++) {
            BigInteger b = new BigInteger(128, rnd);
            int e = rnd.nextInt(24);
            assertEquals(b.modPow(BigInteger.valueOf(e), BigInteger.ONE.shiftLeft(128)),
                    Wide128.fromBigInteger(b).pow(e).toBigInteger());
        }
        assertEquals(Wide128.ONE, Wide128.MAX_VALUE.pow(0));
        for (int i = 0; i < 800; i++) {
            BigInteger a = new BigInteger(128, rnd);
            BigInteger b = new BigInteger(128, rnd);
            Wide128 wa = Wide128.fromBigInteger(a);
            Wide128 wb = Wide128.fromBigInteger(b);
            assertEquals(a.bitCount(), wa.bitCount());
            assertEquals(a.min(b), wa.min(wb).toBigInteger());
            assertEquals(a.max(b), wa.max(wb).toBigInteger());
            assertEquals(absOracle(a, 128), wa.abs().toBigInteger());
        }
    }

    private static void ops256() {
        Random rnd = new Random(256001L);
        for (int i = 0; i < 3000; i++) {
            BigInteger a = new BigInteger(256, rnd);
            BigInteger d = randomNonZero(rnd, 256);
            Wide256 wa = Wide256.fromBigInteger(a);
            Wide256 wd = Wide256.fromBigInteger(d);
            assertEquals(a.divide(d), wa.div(wd).toBigInteger(), "div " + a + "/" + d);
            assertEquals(a.remainder(d), wa.rem(wd).toBigInteger(), "rem " + a + "%" + d);
        }
        Wide256 max = Wide256.MAX_VALUE;
        assertEquals(max.toBigInteger(), max.div(Wide256.ONE).toBigInteger());
        assertEquals(BigInteger.ONE, max.div(Wide256.MAX_VALUE).toBigInteger());
        assertEquals(BigInteger.ZERO, max.rem(Wide256.MAX_VALUE).toBigInteger());
        for (int i = 0; i < 200; i++) {
            BigInteger b = new BigInteger(256, rnd);
            int e = rnd.nextInt(24);
            assertEquals(b.modPow(BigInteger.valueOf(e), BigInteger.ONE.shiftLeft(256)),
                    Wide256.fromBigInteger(b).pow(e).toBigInteger());
        }
        assertEquals(Wide256.ONE, Wide256.MAX_VALUE.pow(0));
        for (int i = 0; i < 800; i++) {
            BigInteger a = new BigInteger(256, rnd);
            BigInteger b = new BigInteger(256, rnd);
            Wide256 wa = Wide256.fromBigInteger(a);
            Wide256 wb = Wide256.fromBigInteger(b);
            assertEquals(a.bitCount(), wa.bitCount());
            assertEquals(a.min(b), wa.min(wb).toBigInteger());
            assertEquals(a.max(b), wa.max(wb).toBigInteger());
            assertEquals(absOracle(a, 256), wa.abs().toBigInteger());
        }
    }

    private static void ops384() {
        Random rnd = new Random(384001L);
        for (int i = 0; i < 1500; i++) {
            BigInteger a = new BigInteger(384, rnd);
            BigInteger d = randomNonZero(rnd, 384);
            Wide384 wa = Wide384.fromBigInteger(a);
            Wide384 wd = Wide384.fromBigInteger(d);
            assertEquals(a.divide(d), wa.div(wd).toBigInteger(), "div " + a + "/" + d);
            assertEquals(a.remainder(d), wa.rem(wd).toBigInteger(), "rem " + a + "%" + d);
        }
        Wide384 max = Wide384.MAX_VALUE;
        assertEquals(BigInteger.ONE, max.div(Wide384.MAX_VALUE).toBigInteger());
        for (int i = 0; i < 100; i++) {
            BigInteger b = new BigInteger(384, rnd);
            int e = rnd.nextInt(20);
            assertEquals(b.modPow(BigInteger.valueOf(e), BigInteger.ONE.shiftLeft(384)),
                    Wide384.fromBigInteger(b).pow(e).toBigInteger());
        }
        for (int i = 0; i < 400; i++) {
            BigInteger a = new BigInteger(384, rnd);
            BigInteger b = new BigInteger(384, rnd);
            Wide384 wa = Wide384.fromBigInteger(a);
            Wide384 wb = Wide384.fromBigInteger(b);
            assertEquals(a.bitCount(), wa.bitCount());
            assertEquals(a.min(b), wa.min(wb).toBigInteger());
            assertEquals(a.max(b), wa.max(wb).toBigInteger());
            assertEquals(absOracle(a, 384), wa.abs().toBigInteger());
        }
    }

    private static void ops512() {
        Random rnd = new Random(512001L);
        for (int i = 0; i < 1000; i++) {
            BigInteger a = new BigInteger(512, rnd);
            BigInteger d = randomNonZero(rnd, 512);
            Wide512 wa = Wide512.fromBigInteger(a);
            Wide512 wd = Wide512.fromBigInteger(d);
            assertEquals(a.divide(d), wa.div(wd).toBigInteger(), "div " + a + "/" + d);
            assertEquals(a.remainder(d), wa.rem(wd).toBigInteger(), "rem " + a + "%" + d);
        }
        Wide512 max = Wide512.MAX_VALUE;
        assertEquals(BigInteger.ONE, max.div(Wide512.MAX_VALUE).toBigInteger());
        for (int i = 0; i < 60; i++) {
            BigInteger b = new BigInteger(512, rnd);
            int e = rnd.nextInt(16);
            assertEquals(b.modPow(BigInteger.valueOf(e), BigInteger.ONE.shiftLeft(512)),
                    Wide512.fromBigInteger(b).pow(e).toBigInteger());
        }
        for (int i = 0; i < 300; i++) {
            BigInteger a = new BigInteger(512, rnd);
            BigInteger b = new BigInteger(512, rnd);
            Wide512 wa = Wide512.fromBigInteger(a);
            Wide512 wb = Wide512.fromBigInteger(b);
            assertEquals(a.bitCount(), wa.bitCount());
            assertEquals(a.min(b), wa.min(wb).toBigInteger());
            assertEquals(a.max(b), wa.max(wb).toBigInteger());
            assertEquals(absOracle(a, 512), wa.abs().toBigInteger());
        }
    }

    private static void ops768() {
        Random rnd = new Random(768001L);
        for (int i = 0; i < 400; i++) {
            BigInteger a = new BigInteger(768, rnd);
            BigInteger d = randomNonZero(rnd, 768);
            Wide768 wa = Wide768.fromBigInteger(a);
            Wide768 wd = Wide768.fromBigInteger(d);
            assertEquals(a.divide(d), wa.div(wd).toBigInteger(), "div " + a + "/" + d);
            assertEquals(a.remainder(d), wa.rem(wd).toBigInteger(), "rem " + a + "%" + d);
        }
        Wide768 max = Wide768.MAX_VALUE;
        assertEquals(BigInteger.ONE, max.div(Wide768.MAX_VALUE).toBigInteger());
        for (int i = 0; i < 40; i++) {
            BigInteger b = new BigInteger(768, rnd);
            int e = rnd.nextInt(12);
            assertEquals(b.modPow(BigInteger.valueOf(e), BigInteger.ONE.shiftLeft(768)),
                    Wide768.fromBigInteger(b).pow(e).toBigInteger());
        }
        for (int i = 0; i < 200; i++) {
            BigInteger a = new BigInteger(768, rnd);
            BigInteger b = new BigInteger(768, rnd);
            Wide768 wa = Wide768.fromBigInteger(a);
            Wide768 wb = Wide768.fromBigInteger(b);
            assertEquals(a.bitCount(), wa.bitCount());
            assertEquals(a.min(b), wa.min(wb).toBigInteger());
            assertEquals(a.max(b), wa.max(wb).toBigInteger());
            assertEquals(absOracle(a, 768), wa.abs().toBigInteger());
        }
    }

    private static void ops1024() {
        Random rnd = new Random(1024001L);
        for (int i = 0; i < 300; i++) {
            BigInteger a = new BigInteger(1024, rnd);
            BigInteger d = randomNonZero(rnd, 1024);
            Wide1024 wa = Wide1024.fromBigInteger(a);
            Wide1024 wd = Wide1024.fromBigInteger(d);
            assertEquals(a.divide(d), wa.div(wd).toBigInteger(), "div " + a + "/" + d);
            assertEquals(a.remainder(d), wa.rem(wd).toBigInteger(), "rem " + a + "%" + d);
        }
        Wide1024 max = Wide1024.MAX_VALUE;
        assertEquals(BigInteger.ONE, max.div(Wide1024.MAX_VALUE).toBigInteger());
        for (int i = 0; i < 40; i++) {
            BigInteger b = new BigInteger(1024, rnd);
            int e = rnd.nextInt(10);
            assertEquals(b.modPow(BigInteger.valueOf(e), BigInteger.ONE.shiftLeft(1024)),
                    Wide1024.fromBigInteger(b).pow(e).toBigInteger());
        }
        for (int i = 0; i < 150; i++) {
            BigInteger a = new BigInteger(1024, rnd);
            BigInteger b = new BigInteger(1024, rnd);
            Wide1024 wa = Wide1024.fromBigInteger(a);
            Wide1024 wb = Wide1024.fromBigInteger(b);
            assertEquals(a.bitCount(), wa.bitCount());
            assertEquals(a.min(b), wa.min(wb).toBigInteger());
            assertEquals(a.max(b), wa.max(wb).toBigInteger());
            assertEquals(absOracle(a, 1024), wa.abs().toBigInteger());
        }
    }
}
