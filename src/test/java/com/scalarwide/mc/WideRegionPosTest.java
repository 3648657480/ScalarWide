package com.scalarwide.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class WideRegionPosTest {

    @Test
    void regionLocalDecompositionAroundZero() {
        WideChunkPos zero = WideChunkPos.of(0, 0);
        assertEquals(0, zero.region().x().toBigInteger().intValueExact());
        assertEquals(0, zero.region().z().toBigInteger().intValueExact());
        assertEquals(0, zero.localX());
        assertEquals(0, zero.localZ());

        WideChunkPos high = WideChunkPos.of(31, 31);
        assertEquals(0, high.region().x().toBigInteger().intValueExact());
        assertEquals(31, high.localX());

        WideChunkPos next = WideChunkPos.of(32, 32);
        assertEquals(1, next.region().x().toBigInteger().intValueExact());
        assertEquals(0, next.localX());

        WideChunkPos neg = WideChunkPos.of(-1, -1);
        assertEquals(-1, neg.region().x().toBigInteger().intValueExact());
        assertEquals(31, neg.localX());
    }

    @Test
    void farRegionRoundTrip() {
        BigInteger base = BigInteger.ONE.shiftLeft(1000);
        WideChunkPos chunk = WideChunkPos.of(base.add(BigInteger.valueOf(31)),
            base.negate().add(BigInteger.valueOf(-1)));
        WideRegionPos region = chunk.region();
        assertEquals(WideRegionPos.fromByteArray(region.toByteArray()), region);
        assertEquals(WideRegionPos.of(chunk), region);
    }

    @Test
    void equalityAndHashAreStable() {
        WideRegionPos a = WideRegionPos.of(WideChunkPos.of(1, 2));
        WideRegionPos b = WideRegionPos.of(WideChunkPos.of(1, 2));
        WideRegionPos c = WideRegionPos.of(WideChunkPos.of(33, 2));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void regionBytesAre256() {
        byte[] bytes = WideRegionPos.of(WideChunkPos.of(1, 2)).toByteArray();
        assertEquals(WideRegionPos.BYTES, bytes.length);
        assertTrue(WideRegionPos.BYTES == 256);
    }
}
