package com.scalarwide;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * mulHigh monolith vs the block-composed oracle vs BigInteger (768/1024).
 */
class MulHighInternalTest {

    @Test
    void differential768() {
        Random rnd = new Random(768777L);
        for (int i = 0; i < 3000; i++) {
            BigInteger a = new BigInteger(768, rnd);
            BigInteger b = new BigInteger(768, rnd);
            Wide768 wa = Wide768.fromBigInteger(a);
            Wide768 wb = Wide768.fromBigInteger(b);
            assertEquals(a.multiply(b).shiftRight(768), wa.mulHigh(wb).toBigInteger(),
                    "vs BigInteger " + a + " * " + b);
        }
        Wide768 max = Wide768.MAX_VALUE;
        assertEquals(BigInteger.ZERO, Wide768.ZERO.mulHigh(max).toBigInteger());
        assertEquals(BigInteger.ZERO, max.mulHigh(Wide768.ZERO).toBigInteger());
    }

    @Test
    void differential1024() {
        Random rnd = new Random(1024777L);
        for (int i = 0; i < 2000; i++) {
            BigInteger a = new BigInteger(1024, rnd);
            BigInteger b = new BigInteger(1024, rnd);
            Wide1024 wa = Wide1024.fromBigInteger(a);
            Wide1024 wb = Wide1024.fromBigInteger(b);
            assertEquals(wa.mulHighComposed(wb).toBigInteger(), wa.mulHigh(wb).toBigInteger(),
                    "mono vs composed " + a + " * " + b);
            assertEquals(a.multiply(b).shiftRight(1024), wa.mulHigh(wb).toBigInteger(),
                    "vs BigInteger " + a + " * " + b);
        }
        Wide1024 max = Wide1024.MAX_VALUE;
        assertEquals(max.mulHighComposed(max).toBigInteger(), max.mulHigh(max).toBigInteger());
        assertEquals(BigInteger.ZERO, Wide1024.ZERO.mulHigh(max).toBigInteger());
        for (int k = 0; k < 1024; k += 131) {
            Wide1024 p = Wide1024.ONE.shl(k);
            assertEquals(p.mulHighComposed(p).toBigInteger(), p.mulHigh(p).toBigInteger(),
                    "power " + k);
        }
    }
}
