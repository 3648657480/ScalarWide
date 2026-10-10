package com.scalarwide;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Internal division checks: fast (Knuth D) vs the binary oracle vs
 * BigInteger, plus crafted quotient-estimate correction stress.
 */
class DivisionInternalTest {

    private static final BigInteger MASK64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

    @Test
    void differentialFastVsBinaryVsBigInteger() {
        Random rnd = new Random(424242L);
        int[][] plan = {{128, 20_000}, {256, 20_000}, {384, 10_000}, {512, 5_000}, {768, 2_000}, {1024, 1_500}};
        for (int[] p : plan) {
            int bits = p[0];
            int n = bits / 64;
            for (int i = 0; i < p[1]; i++) {
                BigInteger a = new BigInteger(bits, rnd);
                BigInteger d = new BigInteger(1 + rnd.nextInt(bits), rnd);
                if (d.signum() == 0) {
                    continue;
                }
                check(bits, n, a, d);
            }
        }
    }

    @Test
    void correctionStress() {
        Random rnd = new Random(777001L);
        long[] divisorPatterns = {0xFFFFFFFFFFFFFFFFL, 0x8000000000000001L, 0x7FFFFFFFFFFFFFFFL,
                0x8000000000000000L, 1L, 0xFFFFFFFFFFFEL};
        for (int bits : new int[]{128, 256, 512, 1024}) {
            int n = bits / 64;
            for (long top : divisorPatterns) {
                for (int i = 0; i < 500; i++) {
                    BigInteger d = BigInteger.valueOf(top).and(MASK64);
                    for (int k = n - 2; k >= 0; k--) {
                        d = d.shiftLeft(64).or(BigInteger.valueOf(rnd.nextLong()).and(MASK64));
                    }
                    if (d.signum() == 0) {
                        continue;
                    }
                    BigInteger a = new BigInteger(bits, rnd);
                    check(bits, n, a, d);
                    BigInteger qmax = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
                    BigInteger crafted = d.multiply(qmax).add(d.subtract(BigInteger.ONE))
                            .mod(BigInteger.ONE.shiftLeft(bits));
                    check(bits, n, crafted, d);
                }
            }
        }
    }

    private static void check(int bits, int n, BigInteger a, BigInteger d) {
        long[] u = limbs(a, n);
        long[] v = limbs(d, n);
        long[] q1 = new long[n];
        long[] r1 = new long[n];
        long[] q2 = new long[n];
        long[] r2 = new long[n];
        Limbs.divmod(u, v, q1, r1);
        Limbs.divmodBinary(u, v, q2, r2);
        assertEquals(toBig(q2), toBig(q1), "q fast vs binary: " + a + "/" + d);
        assertEquals(toBig(r2), toBig(r1), "r fast vs binary: " + a + "%" + d);
        assertEquals(a.divide(d), toBig(q1), "q vs BigInteger: " + a + "/" + d);
        assertEquals(a.remainder(d), toBig(r1), "r vs BigInteger: " + a + "%" + d);
    }

    private static long[] limbs(BigInteger v, int n) {
        long[] out = new long[n];
        BigInteger m = v;
        for (int i = 0; i < n && m.signum() != 0; i++) {
            out[i] = m.longValue();
            m = m.shiftRight(64);
        }
        return out;
    }

    private static BigInteger toBig(long[] x) {
        BigInteger v = BigInteger.ZERO;
        for (int i = x.length - 1; i >= 0; i--) {
            v = v.shiftLeft(64).or(BigInteger.valueOf(x[i]).and(MASK64));
        }
        return v;
    }
}
