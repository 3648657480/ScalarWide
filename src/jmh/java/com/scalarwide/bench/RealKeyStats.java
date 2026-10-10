package com.scalarwide.bench;

import com.scalarwide.Wide256;

import java.math.BigInteger;
import java.util.Random;

/**
 * Prints high-limb occupancy statistics for "real workload" far keys.
 *
 * <p>Workload model (26.x far-coordinate usage): a key packs a pair of chunk
 * coordinates (X, Z) into {@code X | Z<<128}. Practical exploration covers
 * magnitudes from ~2^20 up to ~2^127 (k0 ~ 2^80). Coordinates are
 * non-negative magnitudes; sign is assumed handled by the caller.
 */
public final class RealKeyStats {

    /** Samples X, Z with log-uniform magnitudes in [2^20, 2^128). */
    public static BigInteger sampleCoord(Random rnd) {
        int e = 20 + rnd.nextInt(108);
        return BigInteger.ONE.shiftLeft(e - 1).add(new BigInteger(e - 1, rnd));
    }

    public static Wide256 packKey(BigInteger x, BigInteger z) {
        return Wide256.of(x.longValue(), x.shiftRight(64).longValue(),
                z.longValue(), z.shiftRight(64).longValue());
    }

    public static void main(String[] args) {
        Random rnd = new Random(20261010L);
        int n = 100_000;
        long l0z = 0;
        long l1z = 0;
        long l2z = 0;
        long l3z = 0;
        long below64 = 0;
        long below128 = 0;
        for (int i = 0; i < n; i++) {
            BigInteger x = sampleCoord(rnd);
            BigInteger z = sampleCoord(rnd);
            Wide256 k = packKey(x, z);
            l0z += k.limb(0) == 0 ? 1 : 0;
            l1z += k.limb(1) == 0 ? 1 : 0;
            l2z += k.limb(2) == 0 ? 1 : 0;
            l3z += k.limb(3) == 0 ? 1 : 0;
            below64 += x.bitLength() <= 64 ? 1 : 0;
            below128 += x.bitLength() <= 128 ? 1 : 0;
        }
        System.out.println("samples=" + n + "  (X,Z log-uniform bits 20..127)");
        System.out.println("limb0==0: " + pct(l0z, n));
        System.out.println("limb1==0: " + pct(l1z, n) + "   (X < 2^64)");
        System.out.println("limb2==0: " + pct(l2z, n));
        System.out.println("limb3==0: " + pct(l3z, n) + "   (Z < 2^64)");
        System.out.println("X bitLength <= 64 : " + pct(below64, n));
        System.out.println("X bitLength <= 128: " + pct(below128, n));
    }

    private static String pct(long c, long n) {
        return String.format("%5.1f%%", 100.0 * c / n);
    }

    private RealKeyStats() {
    }
}
