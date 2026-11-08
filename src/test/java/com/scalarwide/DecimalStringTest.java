package com.scalarwide;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Decimal conversion across every chunk boundary.
 *
 * <p>The converter peels off nineteen-digit chunks; the interesting cases are the values that sit
 * exactly on a chunk boundary, one below it, and one above it, because an off-by-one in the
 * reciprocal division shows up as a wrong 19th digit rather than an obviously broken string.
 */
class DecimalStringTest {

    private static final long SEED = 12345L;
    private static final BigInteger CHUNK = BigInteger.TEN.pow(19);

    @Test
    void chunkBoundaries() {
        Random rnd = new Random(SEED);
        for (int width : new int[] {128, 256, 384, 512, 768, 1024}) {
            BigInteger max = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);
            BigInteger p = BigInteger.ONE;
            while (p.compareTo(max) <= 0) {
                check(width, p.subtract(BigInteger.ONE), "p-1");
                check(width, p, "p");
                check(width, p.add(BigInteger.ONE), "p+1");
                check(width, p.subtract(BigInteger.valueOf(2)), "p-2");
                p = p.multiply(CHUNK);
            }
            for (int i = 0; i < 300; i++) {
                check(width, new BigInteger(width, rnd), "random");
            }
        }
    }

    @Test
    void topBitAndAllOnes() {
        for (int width : new int[] {128, 256, 384, 512, 768, 1024}) {
            check(width, BigInteger.ZERO, "zero");
            check(width, BigInteger.ONE, "one");
            check(width, BigInteger.ONE.shiftLeft(width - 1), "top-bit");
            check(width, BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE), "all-ones");
            check(width, BigInteger.ONE.shiftLeft(width).subtract(BigInteger.TWO), "all-ones-minus-1");
        }
    }

    private static void check(int width, BigInteger v, String label) {
        if (v.signum() < 0) {
            return;
        }
        String want = v.toString();
        String got;
        switch (width) {
            case 128 -> got = Wide128.fromBigInteger(v).toString();
            case 256 -> got = Wide256.fromBigInteger(v).toString();
            case 384 -> got = Wide384.fromBigInteger(v).toString();
            case 512 -> got = Wide512.fromBigInteger(v).toString();
            case 768 -> got = Wide768.fromBigInteger(v).toString();
            case 1024 -> got = Wide1024.fromBigInteger(v).toString();
            default -> throw new IllegalArgumentException("width " + width);
        }
        assertEquals(want, got, width + " " + label + " decimal");
    }
}
