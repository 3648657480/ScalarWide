package com.scalarwide.mc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WideChunkCoord1024Test {

    private static final BigInteger P53 = BigInteger.ONE.shiftLeft(53);
    private static final BigInteger P1020 = BigInteger.ONE.shiftLeft(1020);

    @Test
    void signedLongRoundTrip() {
        for (long v : new long[]{0L, 1L, -1L, 62L, -62L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            assertEquals(BigInteger.valueOf(v), WideChunkCoord1024.of(v).toBigInteger());
        }
    }

    @Test
    void farValuesRoundTrip() {
        for (BigInteger v : new BigInteger[]{
            P53, P53.add(BigInteger.ONE), P53.negate(), P1020, P1020.negate(),
            BigInteger.ONE.shiftLeft(1023).subtract(BigInteger.ONE),
            BigInteger.ONE.shiftLeft(1023).negate()}) {
            assertEquals(v, WideChunkCoord1024.of(v).toBigInteger());
        }
    }

    @Test
    void neighborAroundFarValues() {
        WideChunkCoord1024 c53 = WideChunkCoord1024.of(P53);
        assertEquals(P53.add(BigInteger.ONE), c53.neighbor(1L).toBigInteger());
        assertEquals(P53.subtract(BigInteger.ONE), c53.neighbor(-1L).toBigInteger());

        WideChunkCoord1024 c1020 = WideChunkCoord1024.of(P1020);
        assertEquals(P1020.add(BigInteger.ONE), c1020.neighbor(1L).toBigInteger());
        assertEquals(P1020.subtract(BigInteger.ONE), c1020.neighbor(-1L).toBigInteger());
    }

    @Test
    void neighborAcrossNegativeAndZero() {
        assertEquals(BigInteger.valueOf(-1L).add(BigInteger.ONE),
            WideChunkCoord1024.of(-1L).neighbor(1L).toBigInteger());
        assertEquals(BigInteger.valueOf(1L).subtract(BigInteger.ONE),
            WideChunkCoord1024.of(1L).neighbor(-1L).toBigInteger());
        assertEquals(BigInteger.ZERO, WideChunkCoord1024.of(0L).neighbor(0L).toBigInteger());
    }

    @Test
    void signedBoundariesAreExplicit() {
        assertThrows(ArithmeticException.class,
            () -> WideChunkCoord1024.MAX_VALUE.neighbor(1L));
        assertThrows(ArithmeticException.class,
            () -> WideChunkCoord1024.MIN_VALUE.neighbor(-1L));
        assertEquals(WideChunkCoord1024.MAX_VALUE,
            WideChunkCoord1024.MAX_VALUE.neighbor(0L));
    }

    @Test
    void outOfRangeBigIntegerRejected() {
        BigInteger tooHigh = BigInteger.ONE.shiftLeft(1023);
        BigInteger tooLow = tooHigh.negate().subtract(BigInteger.ONE);
        assertThrows(IllegalArgumentException.class, () -> WideChunkCoord1024.of(tooHigh));
        assertThrows(IllegalArgumentException.class, () -> WideChunkCoord1024.of(tooLow));
    }

    @Test
    void byteRoundTripIs128Bytes() {
        for (BigInteger v : new BigInteger[]{BigInteger.ZERO, BigInteger.valueOf(-1L), P1020,
            P1020.negate(), WideChunkCoord1024.MAX_VALUE.toBigInteger()}) {
            WideChunkCoord1024 c = WideChunkCoord1024.of(v);
            byte[] bytes = c.toByteArray();
            assertEquals(128, bytes.length);
            assertEquals(c, WideChunkCoord1024.fromByteArray(bytes));
            assertArrayEquals(bytes, WideChunkCoord1024.fromByteArray(bytes).toByteArray());
        }
    }

    @Test
    void randomBigIntegerOracle() {
        Random rnd = new Random(0x1024C0DEL);
        for (int i = 0; i < 500; i++) {
            BigInteger x = new BigInteger(1019, rnd);
            if (rnd.nextBoolean()) {
                x = x.negate();
            }
            WideChunkCoord1024 c = WideChunkCoord1024.of(x);
            assertEquals(x, c.toBigInteger());
            assertEquals(x.add(BigInteger.ONE), c.neighbor(1L).toBigInteger());
            assertEquals(x.subtract(BigInteger.ONE), c.neighbor(-1L).toBigInteger());
            assertEquals(c, WideChunkCoord1024.fromByteArray(c.toByteArray()));
        }
    }

    @Test
    void hashAndEqualsAreStable() {
        WideChunkCoord1024 a = WideChunkCoord1024.of(P1020);
        WideChunkCoord1024 b = WideChunkCoord1024.of(P1020);
        WideChunkCoord1024 c = a.neighbor(1L);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
