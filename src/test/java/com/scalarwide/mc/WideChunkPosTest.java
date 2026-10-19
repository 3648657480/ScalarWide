package com.scalarwide.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class WideChunkPosTest {

    private static final BigInteger P1020 = BigInteger.ONE.shiftLeft(1020);

    @Test
    void twoAxisFarPositionRoundTrip() {
        WideChunkPos pos = WideChunkPos.of(P1020, P1020.negate());
        assertEquals(P1020, pos.x().toBigInteger());
        assertEquals(P1020.negate(), pos.z().toBigInteger());
        assertEquals(pos, WideChunkPos.fromByteArray(pos.toByteArray()));
        assertEquals(256, pos.toByteArray().length);
    }

    @Test
    void neighborChangesOnlyRequestedAxis() {
        WideChunkPos pos = WideChunkPos.of(P1020, P1020.negate());
        assertEquals(P1020.add(BigInteger.ONE), pos.neighbor(1L, 0L).x().toBigInteger());
        assertEquals(P1020.negate(), pos.neighbor(1L, 0L).z().toBigInteger());
        assertEquals(P1020, pos.neighbor(0L, 1L).x().toBigInteger());
        assertEquals(P1020.negate().add(BigInteger.ONE), pos.neighbor(0L, 1L).z().toBigInteger());
    }

    @Test
    void hashAndEqualsAreStable() {
        WideChunkPos a = WideChunkPos.of(P1020, P1020.negate());
        WideChunkPos b = WideChunkPos.of(P1020, P1020.negate());
        WideChunkPos c = a.neighbor(1L, 0L);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
