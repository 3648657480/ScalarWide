package com.scalarwide;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exhaustive coverage of the minimal-length byte encoders across <em>every</em> bit length.
 *
 * <p>The rest of the suite only feeds full-width random values, whose byte length is always a
 * multiple of eight — so {@code toByteArray}'s head path (the {@code rem != 0} branch that takes
 * the top bytes of the highest limb) and the {@code n < 8} single-limb path were never executed.
 * Those are precisely the branches a limb-store rewrite can break, so sweep them all here.
 */
class MinimalBytesTest {

    private static final long SEED = 12345L;

    /** Minimal unsigned big-endian bytes, per BigInteger's two's-complement output. */
    private static byte[] refMinimalBe(BigInteger v) {
        byte[] b = v.toByteArray();
        if (b.length > 1 && b[0] == 0) {
            return Arrays.copyOfRange(b, 1, b.length);
        }
        return b;
    }

    private static byte[] reversed(byte[] a) {
        byte[] r = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            r[i] = a[a.length - 1 - i];
        }
        return r;
    }

    @Test
    void everyBitLengthRoundTrips() {
        Random rnd = new Random(SEED);
        for (int width : new int[] {128, 256, 384, 512, 768, 1024}) {
            for (int len = 1; len <= width; len++) {
                // exactly `len` significant bits: bit len-1 set, the rest random below it
                BigInteger v = BigInteger.ONE.shiftLeft(len - 1);
                if (len > 1) {
                    BigInteger fill = new BigInteger(len - 1, rnd);
                    v = v.or(fill);
                }
                check(width, v, "len=" + len);
            }
        }
    }

    @Test
    void boundaryValues() {
        for (int width : new int[] {128, 256, 384, 512, 768, 1024}) {
            check(width, BigInteger.ZERO, "zero");
            check(width, BigInteger.ONE, "one");
            check(width, BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE), "max");
            check(width, BigInteger.ONE.shiftLeft(width - 1), "min-bit");
            // every remainder of the byte length
            for (int rem = 1; rem <= 7; rem++) {
                check(width, BigInteger.ONE.shiftLeft(rem * 8).subtract(BigInteger.ONE), "ones-" + rem);
                check(width, BigInteger.ONE.shiftLeft(rem * 8), "pow-" + rem);
            }
            // single-limb and just-over-single-limb values
            for (long x : new long[] {1, 0xFFL, 0x1FFL, 0x7FFFFFFFFFFFL, Long.MAX_VALUE, -1L}) {
                check(width, BigInteger.valueOf(x).and(BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE)),
                        "limb0=" + x);
            }
        }
    }

    private static void check(int width, BigInteger v, String label) {
        switch (width) {
            case 128 -> checkOne(Wide128.fromBigInteger(v), v, label, 16);
            case 256 -> checkOne(Wide256.fromBigInteger(v), v, label, 32);
            case 384 -> checkOne(Wide384.fromBigInteger(v), v, label, 48);
            case 512 -> checkOne(Wide512.fromBigInteger(v), v, label, 64);
            case 768 -> checkOne(Wide768.fromBigInteger(v), v, label, 96);
            case 1024 -> checkOne(Wide1024.fromBigInteger(v), v, label, 128);
            default -> throw new IllegalArgumentException("width " + width);
        }
    }

    private static void checkOne(Wide128 w, BigInteger v, String label, int widthBytes) {
        checkBytes(w.toByteArray(), w.toByteArrayLE(), v, label, widthBytes, 16);
        assertEquals(w, Wide128.fromByteArray(w.toByteArray()), label);
        assertEquals(w, Wide128.fromByteArrayLE(w.toByteArrayLE()), label);
    }

    private static void checkOne(Wide256 w, BigInteger v, String label, int widthBytes) {
        checkBytes(w.toByteArray(), w.toByteArrayLE(), v, label, widthBytes, 32);
        assertEquals(w, Wide256.fromByteArray(w.toByteArray()), label);
        assertEquals(w, Wide256.fromByteArrayLE(w.toByteArrayLE()), label);
    }

    private static void checkOne(Wide384 w, BigInteger v, String label, int widthBytes) {
        checkBytes(w.toByteArray(), w.toByteArrayLE(), v, label, widthBytes, 48);
        assertEquals(w, Wide384.fromByteArray(w.toByteArray()), label);
        assertEquals(w, Wide384.fromByteArrayLE(w.toByteArrayLE()), label);
    }

    private static void checkOne(Wide512 w, BigInteger v, String label, int widthBytes) {
        checkBytes(w.toByteArray(), w.toByteArrayLE(), v, label, widthBytes, 64);
        assertEquals(w, Wide512.fromByteArray(w.toByteArray()), label);
        assertEquals(w, Wide512.fromByteArrayLE(w.toByteArrayLE()), label);
    }

    private static void checkOne(Wide768 w, BigInteger v, String label, int widthBytes) {
        checkBytes(w.toByteArray(), w.toByteArrayLE(), v, label, widthBytes, 96);
        assertEquals(w, Wide768.fromByteArray(w.toByteArray()), label);
        assertEquals(w, Wide768.fromByteArrayLE(w.toByteArrayLE()), label);
    }

    private static void checkOne(Wide1024 w, BigInteger v, String label, int widthBytes) {
        checkBytes(w.toByteArray(), w.toByteArrayLE(), v, label, widthBytes, 128);
        assertEquals(w, Wide1024.fromByteArray(w.toByteArray()), label);
        assertEquals(w, Wide1024.fromByteArrayLE(w.toByteArrayLE()), label);
    }

    private static void checkBytes(byte[] be, byte[] le, BigInteger v, String label,
                                   int widthBytes, int limbs) {
        byte[] refBe = refMinimalBe(v);
        assertArrayEquals(refBe, be, label + ": minimal BE");
        assertArrayEquals(reversed(refBe), le, label + ": minimal LE");
        assertTrue(be.length <= widthBytes, label + ": BE too long");
        if (v.signum() != 0) {
            assertEquals((v.bitLength() + 7) / 8, be.length, label + ": BE length");
        } else {
            assertEquals(1, be.length, label + ": zero encodes to one byte");
            assertEquals(0, be[0], label + ": zero byte value");
        }
        assertTrue(limbs > 0, label);
    }
}
