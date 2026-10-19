package com.scalarwide.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WideSectionCoord512Test {

    private static final BigInteger P511 = BigInteger.ONE.shiftLeft(511);

    @Test
    void signedRoundTrip() {
        for (long v : new long[]{0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            assertEquals(BigInteger.valueOf(v), WideSectionCoord512.of(v).toBigInteger());
        }
    }

    @Test
    void farNeighbors() {
        BigInteger v = P511.subtract(BigInteger.ONE);
        assertEquals(v, WideSectionCoord512.of(v).neighbor(0L).toBigInteger());
        assertEquals(v.subtract(BigInteger.ONE),
            WideSectionCoord512.of(v).neighbor(-1L).toBigInteger());
        assertEquals(P511.negate(),
            WideSectionCoord512.of(P511.negate()).neighbor(0L).toBigInteger());
    }

    @Test
    void boundariesAreExplicit() {
        assertThrows(ArithmeticException.class,
            () -> WideSectionCoord512.MAX_VALUE.neighbor(1L));
        assertThrows(ArithmeticException.class,
            () -> WideSectionCoord512.MIN_VALUE.neighbor(-1L));
        assertThrows(IllegalArgumentException.class,
            () -> WideSectionCoord512.of(P511));
    }

    @Test
    void randomBigIntegerOracle() {
        Random rnd = new Random(0x512C0DEL);
        for (int i = 0; i < 500; i++) {
            BigInteger y = new BigInteger(510, rnd);
            if (rnd.nextBoolean()) {
                y = y.negate();
            }
            WideSectionCoord512 c = WideSectionCoord512.of(y);
            assertEquals(y, c.toBigInteger());
            assertEquals(y.add(BigInteger.ONE), c.neighbor(1L).toBigInteger());
            assertEquals(y.subtract(BigInteger.ONE), c.neighbor(-1L).toBigInteger());
            assertEquals(c, WideSectionCoord512.fromByteArray(c.toByteArray()));
        }
    }

    @Test
    void byteRoundTrip() {
        for (BigInteger v : new BigInteger[]{
            BigInteger.ZERO, BigInteger.valueOf(-1L), P511.subtract(BigInteger.ONE),
            P511.negate()}) {
            WideSectionCoord512 c = WideSectionCoord512.of(v);
            assertEquals(64, c.toByteArray().length);
            assertEquals(c, WideSectionCoord512.fromByteArray(c.toByteArray()));
        }
    }
}
