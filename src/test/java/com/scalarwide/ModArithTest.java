package com.scalarwide;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Differential gate for {@link ModArith}. Every modular result is compared against
 * {@link BigInteger} at the same width; bit and rotation cases are checked against closed forms.
 */
class ModArithTest {

    private static final long SEED = 12345L;
    private static final int N = 2000;
    private static final int[] WIDTHS = {2, 4, 8};

    @Test
    void addModAndSubModMatchBigInteger() {
        Random rnd = new Random(SEED);
        for (int len : WIDTHS) {
            for (int i = 0; i < N; i++) {
                BigInteger m = oddModulus(rnd, len);
                BigInteger a = randomBelow(rnd, m);
                BigInteger b = randomBelow(rnd, m);
                long[] out = new long[len];
                ModArith.addMod(toLimbs(a, len), toLimbs(b, len), toLimbs(m, len), out);
                assertEquals(a.add(b).mod(m), toBig(out), "addMod len=" + len);
                ModArith.subMod(toLimbs(a, len), toLimbs(b, len), toLimbs(m, len), out);
                assertEquals(a.subtract(b).mod(m), toBig(out), "subMod len=" + len);
            }
        }
    }

    @Test
    void mulModMatchesBigInteger() {
        Random rnd = new Random(SEED + 1);
        for (int len : WIDTHS) {
            for (int i = 0; i < N; i++) {
                BigInteger m = oddModulus(rnd, len);
                BigInteger a = randomBelow(rnd, m);
                BigInteger b = randomBelow(rnd, m);
                long[] out = new long[len];
                ModArith.mulMod(toLimbs(a, len), toLimbs(b, len), toLimbs(m, len), out);
                assertEquals(a.multiply(b).mod(m), toBig(out), "mulMod len=" + len);
            }
        }
    }

    @Test
    void powModMatchesBigInteger() {
        Random rnd = new Random(SEED + 2);
        for (int len : WIDTHS) {
            for (int i = 0; i < N / 4; i++) {
                BigInteger m = oddModulus(rnd, len);
                BigInteger base = randomBelow(rnd, m);
                BigInteger exp = new BigInteger(m.bitLength(), rnd).mod(m);
                long[] out = new long[len];
                ModArith.powMod(toLimbs(base, len), toLimbs(exp, len), toLimbs(m, len), out);
                assertEquals(base.modPow(exp, m), toBig(out), "powMod len=" + len);
            }
        }
    }

    @Test
    void powModCtMatchesPowMod() {
        Random rnd = new Random(SEED + 3);
        int len = 4;
        for (int i = 0; i < 100; i++) {
            BigInteger m = oddModulus(rnd, len);
            BigInteger base = randomBelow(rnd, m);
            BigInteger exp = new BigInteger(m.bitLength(), rnd).mod(m);
            long[] fast = new long[len];
            long[] ct = new long[len];
            ModArith.powMod(toLimbs(base, len), toLimbs(exp, len), toLimbs(m, len), fast);
            ModArith.powModCt(toLimbs(base, len), toLimbs(exp, len), toLimbs(m, len), ct);
            assertArrayEquals(fast, ct, "powModCt vs powMod");
        }
    }

    @Test
    void modInverseMatchesBigInteger() {
        Random rnd = new Random(SEED + 4);
        for (int len : WIDTHS) {
            int done = 0;
            while (done < N / 8) {
                BigInteger m = oddModulus(rnd, len);
                BigInteger a = randomBelow(rnd, m);
                if (!a.gcd(m).equals(BigInteger.ONE)) {
                    continue;
                }
                long[] out = new long[len];
                assertTrue(ModArith.modInverse(toLimbs(a, len), toLimbs(m, len), out),
                        "invertible");
                assertEquals(a.modInverse(m), toBig(out), "modInverse len=" + len);
                done++;
            }
        }
        // non-invertible: a = 0 with an odd modulus larger than 1
        BigInteger m = BigInteger.valueOf(1000003L);
        assertFalse(ModArith.modInverse(toLimbs(BigInteger.ZERO, 2), toLimbs(m, 2), new long[2]));
        assertThrows(ArithmeticException.class,
                () -> ModArith.modInverse(toLimbs(BigInteger.ONE, 2), toLimbs(BigInteger.valueOf(8), 2),
                        new long[2]));
    }

    @Test
    void gcdMatchesBigInteger() {
        Random rnd = new Random(SEED + 5);
        for (int len : WIDTHS) {
            for (int i = 0; i < N / 2; i++) {
                BigInteger a = new BigInteger(len * 64, rnd);
                BigInteger b = new BigInteger(len * 64, rnd);
                if (a.signum() == 0 && b.signum() == 0) {
                    continue;
                }
                long[] out = new long[len];
                ModArith.gcd(toLimbs(a, len), toLimbs(b, len), out);
                assertEquals(a.gcd(b), toBig(out), "gcd len=" + len);
            }
        }
    }

    @Test
    void bitAccessAndRotations() {
        Random rnd = new Random(SEED + 6);
        for (int len : WIDTHS) {
            for (int i = 0; i < 200; i++) {
                BigInteger v = new BigInteger(len * 64, rnd);
                long[] limbs = toLimbs(v, len);
                int width = len * 64;
                int bit = rnd.nextInt(width);
                assertEquals(v.testBit(bit), ModArith.testBit(limbs, bit), "testBit");
                long[] copy = limbs.clone();
                ModArith.setBit(copy, bit);
                assertEquals(v.setBit(bit), toBig(copy), "setBit");
                copy = limbs.clone();
                ModArith.clearBit(copy, bit);
                assertEquals(v.clearBit(bit), toBig(copy), "clearBit");
                copy = limbs.clone();
                ModArith.flipBit(copy, bit);
                assertEquals(v.flipBit(bit), toBig(copy), "flipBit");

                int k = rnd.nextInt(width * 2) - width;
                long[] rot = new long[len];
                ModArith.rotl(limbs, k, rot);
                assertEquals(rotateLeft(v, k, width), toBig(rot), "rotl k=" + k);
                ModArith.rotr(limbs, k, rot);
                assertEquals(rotateRight(v, k, width), toBig(rot), "rotr k=" + k);
            }
        }
        long[] zero = new long[2];
        assertEquals(-1, ModArith.getLowestSetBit(zero));
        long[] eight = new long[2];
        eight[0] = 8L;
        assertEquals(3, ModArith.getLowestSetBit(eight));
    }

    @Test
    void ctCompareAndSelectMatchPlainForms() {
        Random rnd = new Random(SEED + 7);
        int len = 4;
        for (int i = 0; i < N; i++) {
            BigInteger a = new BigInteger(len * 64, rnd);
            BigInteger b = new BigInteger(len * 64, rnd);
            assertEquals(Integer.signum(a.compareTo(b)),
                    ModArith.ctCompare(toLimbs(a, len), toLimbs(b, len)), "ctCompare");
            long[] out = new long[len];
            ModArith.ctSelect(toLimbs(a, len), toLimbs(b, len), rnd.nextInt(2), out);
            ModArith.ctSelect(toLimbs(a, len), toLimbs(b, len), 0, out);
            assertEquals(a, toBig(out), "ctSelect 0");
            ModArith.ctSelect(toLimbs(a, len), toLimbs(b, len), 1, out);
            assertEquals(b, toBig(out), "ctSelect 1");
        }
        long[] x = toLimbs(BigInteger.valueOf(7), len);
        long[] y = toLimbs(BigInteger.valueOf(9), len);
        ModArith.ctSwap(x, y, 1);
        assertEquals(BigInteger.valueOf(9), toBig(x));
        assertEquals(BigInteger.valueOf(7), toBig(y));
        ModArith.ctSwap(x, y, 0);
        assertEquals(BigInteger.valueOf(9), toBig(x));
    }

    @Test
    void modulusZeroIsRejected() {
        long[] m = new long[2];
        assertThrows(ArithmeticException.class,
                () -> ModArith.addMod(new long[2], new long[2], m, new long[2]));
        assertThrows(ArithmeticException.class,
                () -> ModArith.mulMod(new long[2], new long[2], m, new long[2]));
    }

    // ---------------------------------------------------------------- helpers

    private static BigInteger rotateLeft(BigInteger v, int k, int width) {
        BigInteger mask = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);
        int s = Math.floorMod(k, width);
        return v.shiftLeft(s).and(mask).or(v.shiftRight(width - s)).and(mask);
    }

    private static BigInteger rotateRight(BigInteger v, int k, int width) {
        BigInteger mask = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);
        int s = Math.floorMod(k, width);
        return v.shiftRight(s).or(v.shiftLeft(width - s).and(mask)).and(mask);
    }

    private static BigInteger oddModulus(Random rnd, int len) {
        BigInteger m = new BigInteger(len * 64, rnd).setBit(len * 64 - 1).setBit(0);
        return m;
    }

    private static BigInteger randomBelow(Random rnd, BigInteger m) {
        BigInteger v;
        do {
            v = new BigInteger(m.bitLength(), rnd);
        } while (v.compareTo(m) >= 0);
        return v;
    }

    static long[] toLimbs(BigInteger v, int len) {
        long[] out = new long[len];
        BigInteger work = v;
        for (int i = 0; i < len; i++) {
            out[i] = work.longValue();
            work = work.shiftRight(64);
        }
        return out;
    }

    static BigInteger toBig(long[] limbs) {
        BigInteger m = BigInteger.ZERO;
        for (int i = limbs.length - 1; i >= 0; i--) {
            long v = limbs[i];
            BigInteger w = BigInteger.valueOf(v & Long.MAX_VALUE);
            if (v < 0) {
                w = w.setBit(63);
            }
            m = m.shiftLeft(64).or(w);
        }
        return m;
    }
}
