package com.scalarwide;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consumer-view smoke test: uses ONLY the public API surface in the patterns a
 * consumer (framework) relies on. This is the drop-in contract for the 0.11
 * line - breaking this test means a breaking change, which must go to a new
 * minor line with a migration note (see docs/COMPATIBILITY.md).
 */
class ConsumerApiTest {

    @Test
    void bigIntegerRoundTrip() {
        BigInteger v = BigInteger.valueOf(2).pow(200).add(BigInteger.valueOf(12345));
        Wide256 w = Wide256.fromBigInteger(v);
        assertEquals(v, w.toBigInteger());
        assertEquals(201, w.bitLength());
        assertEquals(0, w.compareUnsigned(Wide256.fromBigInteger(v)));
        assertTrue(w.compareUnsigned(Wide256.ONE) > 0);
    }

    @Test
    void mapKeyPattern() {
        Map<Wide256, String> map = new HashMap<>();
        Wide256 k1 = Wide256.of(1, 2, 3, 4);
        Wide256 k2 = k1.shl(3).or(Wide256.of(7));
        map.put(k1, "one");
        map.put(k2, "two");
        assertEquals("one", map.get(Wide256.of(1, 2, 3, 4)));
        assertEquals("two", map.get(k1.shl(3).or(Wide256.of(7))));
        assertEquals(k1, Wide256.of(1, 2, 3, 4));
        assertEquals(k1.hashCode(), Wide256.of(1, 2, 3, 4).hashCode());
    }

    @Test
    void arithmeticPatternAcrossWidths() {
        Wide128 a128 = Wide128.of(123456789L);
        assertEquals(123456789L * 123456789L, a128.mul(a128).longValue());

        Wide768 x = Wide768.ONE.shl(700);
        assertEquals(701, x.bitLength());
        assertEquals(x, x.ushr(700).shl(700));
        assertEquals(Wide768.ZERO, x.add(x.neg()));

        Wide1024 y = Wide1024.ONE.shl(1000);
        assertEquals(1001, y.bitLength());
        assertEquals(y, y.shr(0));
        assertTrue(y.compareSigned(Wide1024.ofSigned(-5)) > 0);
    }

    @Test
    void mulFullPattern() {
        Wide512 p = Wide256.of(0xFFFFFFFFL).mulFull(Wide256.of(0xFFFFFFFFL));
        assertEquals(0xFFFFFFFE00000001L, p.longValue());
    }

    @Test
    void byteIoPattern() {
        Wide256 k = Wide256.of(0x1122334455667788L, 0x99AABBCCDDEEFF00L, 1, 2);
        byte[] be = k.toByteArray();
        assertEquals(k, Wide256.fromByteArray(be));
        assertEquals(k, Wide256.fromByteArrayLE(k.toByteArrayLE()));
        assertEquals(32, k.toByteArrayFixed().length);
        assertEquals(k, Wide256.fromByteArray(k.toByteArrayFixed()));
    }

    @Test
    void mathOpsPattern() {
        assertEquals(Wide256.of(14), Wide256.of(100).div(Wide256.of(7)));
        assertEquals(Wide256.of(2), Wide256.of(100).rem(Wide256.of(7)));
        assertEquals(Wide256.of(81), Wide256.of(3).pow(4));
        assertEquals(3, Wide256.of(0b1011).bitCount());
        assertEquals(Wide256.of(2), Wide256.of(3).min(Wide256.of(2)));
        assertEquals(Wide256.of(3), Wide256.of(3).max(Wide256.of(2)));
        assertEquals(Wide256.of(5), Wide256.ofSigned(-5).abs());
    }

    @Test
    void mutableCarrierPattern() {
        MutableWide256 acc = new MutableWide256();
        acc.set(Wide256.of(3)).add(Wide256.of(4)).mul(Wide256.of(5));
        assertEquals(Wide256.of(35), acc.toWide256());
        acc.setZero();
        assertTrue(acc.toWide256().isZero());

        MutableWide128 m128 = new MutableWide128();
        m128.set(Wide128.of(10)).sub(Wide128.of(3));
        assertEquals(7, m128.toWide128().longValue());
    }

    @Test
    void shiftsAndConversions() {
        Wide512 big = Wide512.fromBigInteger(BigInteger.ONE.shiftLeft(511));
        assertEquals(512, big.bitLength());
        assertTrue(big.compareUnsigned(Wide512.MAX_VALUE) <= 0);
        assertEquals(big, Wide512.fromBigInteger(big.toBigInteger()));
        assertEquals(128, big.toHexString().length());
    }
}
