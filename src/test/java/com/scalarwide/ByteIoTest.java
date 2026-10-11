package com.scalarwide;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Byte-serialization gates: round-trips (BE/LE minimal, BE fixed-width),
 * minimality, BigInteger interop (sign-byte tolerance), and length limits.
 */
class ByteIoTest {

    @Test
    void allWidths() {
        Random rnd = new Random(909090L);
        for (int bits : new int[]{128, 256, 384, 512, 768, 1024}) {
            BigInteger max = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
            BigInteger[] edges = {BigInteger.ZERO, BigInteger.ONE, max,
                    BigInteger.ONE.shiftLeft(bits - 1),
                    BigInteger.ONE.shiftLeft(64), BigInteger.ONE.shiftLeft(63),
                    max.subtract(BigInteger.ONE), BigInteger.valueOf(255)};
            for (int i = 0; i < 1500; i++) {
                check(bits, new BigInteger(bits, rnd));
            }
            for (BigInteger e : edges) {
                check(bits, e);
            }
        }
    }

    @Test
    void rejectsOversizedArrays() {
        byte[] tooBig = new byte[17];
        tooBig[0] = 1;
        assertThrows(IllegalArgumentException.class, () -> Wide128.fromByteArray(tooBig));
        assertThrows(IllegalArgumentException.class, () -> Wide128.fromByteArrayLE(new byte[17]));
        byte[] padded = new byte[17];
        padded[16] = 42;
        assertEquals(Wide128.of(42), Wide128.fromByteArray(padded));
    }

    private static void check(int bits, BigInteger v) {
        switch (bits) {
            case 128 -> checkOne(Wide128.fromBigInteger(v), v);
            case 256 -> checkOne(Wide256.fromBigInteger(v), v);
            case 384 -> checkOne(Wide384.fromBigInteger(v), v);
            case 512 -> checkOne(Wide512.fromBigInteger(v), v);
            case 768 -> checkOne(Wide768.fromBigInteger(v), v);
            case 1024 -> checkOne(Wide1024.fromBigInteger(v), v);
            default -> throw new IllegalArgumentException();
        }
    }

    private static void checkOne(Wide128 w, BigInteger v) {
        byte[] be = w.toByteArray();
        byte[] le = w.toByteArrayLE();
        byte[] fx = w.toByteArrayFixed();
        assertEquals(16, fx.length);
        assertEquals(w, Wide128.fromByteArray(be));
        assertEquals(w, Wide128.fromByteArrayLE(le));
        assertEquals(w, Wide128.fromByteArray(fx));
        assertEquals(w, Wide128.fromByteArray(v.toByteArray()));
        assertMinimal(w, be, le, v, 16);
    }

    private static void checkOne(Wide256 w, BigInteger v) {
        byte[] be = w.toByteArray();
        byte[] le = w.toByteArrayLE();
        byte[] fx = w.toByteArrayFixed();
        assertEquals(32, fx.length);
        assertEquals(w, Wide256.fromByteArray(be));
        assertEquals(w, Wide256.fromByteArrayLE(le));
        assertEquals(w, Wide256.fromByteArray(fx));
        assertEquals(w, Wide256.fromByteArray(v.toByteArray()));
        assertMinimal(w, be, le, v, 32);
    }

    private static void checkOne(Wide384 w, BigInteger v) {
        byte[] be = w.toByteArray();
        byte[] le = w.toByteArrayLE();
        byte[] fx = w.toByteArrayFixed();
        assertEquals(48, fx.length);
        assertEquals(w, Wide384.fromByteArray(be));
        assertEquals(w, Wide384.fromByteArrayLE(le));
        assertEquals(w, Wide384.fromByteArray(fx));
        assertEquals(w, Wide384.fromByteArray(v.toByteArray()));
        assertMinimal(w, be, le, v, 48);
    }

    private static void checkOne(Wide512 w, BigInteger v) {
        byte[] be = w.toByteArray();
        byte[] le = w.toByteArrayLE();
        byte[] fx = w.toByteArrayFixed();
        assertEquals(64, fx.length);
        assertEquals(w, Wide512.fromByteArray(be));
        assertEquals(w, Wide512.fromByteArrayLE(le));
        assertEquals(w, Wide512.fromByteArray(fx));
        assertEquals(w, Wide512.fromByteArray(v.toByteArray()));
        assertMinimal(w, be, le, v, 64);
    }

    private static void checkOne(Wide768 w, BigInteger v) {
        byte[] be = w.toByteArray();
        byte[] le = w.toByteArrayLE();
        byte[] fx = w.toByteArrayFixed();
        assertEquals(96, fx.length);
        assertEquals(w, Wide768.fromByteArray(be));
        assertEquals(w, Wide768.fromByteArrayLE(le));
        assertEquals(w, Wide768.fromByteArray(fx));
        assertEquals(w, Wide768.fromByteArray(v.toByteArray()));
        assertMinimal(w, be, le, v, 96);
    }

    private static void checkOne(Wide1024 w, BigInteger v) {
        byte[] be = w.toByteArray();
        byte[] le = w.toByteArrayLE();
        byte[] fx = w.toByteArrayFixed();
        assertEquals(128, fx.length);
        assertEquals(w, Wide1024.fromByteArray(be));
        assertEquals(w, Wide1024.fromByteArrayLE(le));
        assertEquals(w, Wide1024.fromByteArray(fx));
        assertEquals(w, Wide1024.fromByteArray(v.toByteArray()));
        assertMinimal(w, be, le, v, 128);
    }

    private static void assertMinimal(Object w, byte[] be, byte[] le, BigInteger v, int widthBytes) {
        assertEquals(v.signum() == 0, be.length == 1 && be[0] == 0 && le.length == 1 && le[0] == 0);
        if (v.signum() != 0) {
            assertEquals((v.bitLength() + 7) / 8, be.length);
            assertTrue((be[0] & 0xFF) != 0);
            assertTrue((le[le.length - 1] & 0xFF) != 0);
        }
        assertTrue(be.length <= widthBytes && le.length <= widthBytes);
    }
}
