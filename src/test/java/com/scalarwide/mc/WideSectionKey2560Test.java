package com.scalarwide.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WideSectionKey2560Test {

    private static final BigInteger P1020 = BigInteger.ONE.shiftLeft(1020);
    private static final BigInteger P511 = BigInteger.ONE.shiftLeft(511);

    @Test
    void farSectionRoundTrip() {
        BigInteger x = P1020;
        BigInteger y = P511.subtract(BigInteger.ONE);
        BigInteger z = P1020.negate();
        WideSectionKey2560 key = WideSectionKey2560.of(x, y, z);
        assertEquals(x, key.x().toBigInteger());
        assertEquals(y, key.y().toBigInteger());
        assertEquals(z, key.z().toBigInteger());
        assertEquals(320, key.toByteArray().length);
        assertEquals(key, WideSectionKey2560.fromByteArray(key.toByteArray()));
    }

    @Test
    void neighborChangesOnlyRequestedAxis() {
        WideSectionKey2560 key = WideSectionKey2560.of(P1020, BigInteger.ZERO, P1020.negate());
        WideSectionKey2560 nx = key.neighbor(1L, 0L, 0L);
        assertEquals(P1020.add(BigInteger.ONE), nx.x().toBigInteger());
        assertEquals(BigInteger.ZERO, nx.y().toBigInteger());
        assertEquals(P1020.negate(), nx.z().toBigInteger());

        WideSectionKey2560 ny = key.neighbor(0L, 1L, 0L);
        assertEquals(BigInteger.ONE, ny.y().toBigInteger());

        WideSectionKey2560 nz = key.neighbor(0L, 0L, 1L);
        assertEquals(P1020.negate().add(BigInteger.ONE), nz.z().toBigInteger());
    }

    @Test
    void boundariesAreExplicit() {
        WideSectionKey2560 maxY = WideSectionKey2560.of(BigInteger.ZERO,
            P511.subtract(BigInteger.ONE), BigInteger.ZERO);
        assertThrows(ArithmeticException.class, () -> maxY.neighbor(0L, 1L, 0L));
        assertThrows(IllegalArgumentException.class,
            () -> WideSectionKey2560.of(BigInteger.ZERO, P511, BigInteger.ZERO));
    }

    @Test
    void flatByteLayoutMatchesAxisViews() {
        WideSectionKey2560 key = WideSectionKey2560.of(P1020, P511.subtract(BigInteger.ONE),
            P1020.negate());
        byte[] xb = key.x().toByteArray();
        byte[] yb = key.y().toByteArray();
        byte[] zb = key.z().toByteArray();
        byte[] expected = new byte[WideSectionKey2560.BYTES];
        System.arraycopy(xb, 0, expected, 0, xb.length);
        System.arraycopy(yb, 0, expected, xb.length, yb.length);
        System.arraycopy(zb, 0, expected, xb.length + yb.length, zb.length);
        org.junit.jupiter.api.Assertions.assertArrayEquals(expected, key.toByteArray());
    }

    @Test
    void randomBigIntegerOracle() {
        Random rnd = new Random(0x2560C0DEL);
        for (int i = 0; i < 300; i++) {
            BigInteger x = new BigInteger(1019, rnd);
            BigInteger z = new BigInteger(1019, rnd);
            BigInteger y = new BigInteger(510, rnd);
            if (rnd.nextBoolean()) {
                x = x.negate();
            }
            if (rnd.nextBoolean()) {
                z = z.negate();
            }
            if (rnd.nextBoolean()) {
                y = y.negate();
            }
            WideSectionKey2560 key = WideSectionKey2560.of(x, y, z);
            assertEquals(x, key.x().toBigInteger());
            assertEquals(y, key.y().toBigInteger());
            assertEquals(z, key.z().toBigInteger());
            WideSectionKey2560 next = key.neighbor(1L, 1L, 1L);
            assertEquals(x.add(BigInteger.ONE), next.x().toBigInteger());
            assertEquals(y.add(BigInteger.ONE), next.y().toBigInteger());
            assertEquals(z.add(BigInteger.ONE), next.z().toBigInteger());
            assertEquals(key, WideSectionKey2560.fromByteArray(key.toByteArray()));
        }
    }

    @Test
    void hashAndEqualsAreStable() {
        WideSectionKey2560 a = WideSectionKey2560.of(P1020, BigInteger.ZERO, P1020.negate());
        WideSectionKey2560 b = WideSectionKey2560.of(P1020, BigInteger.ZERO, P1020.negate());
        WideSectionKey2560 c = a.neighbor(1L, 0L, 0L);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
