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
    /** 2 = 128, 4 = 256, 6 = 384, 8 = 512, 9 = 576 lanes (ECC P-521 uses 9). */
    private static final int[] WIDTHS = {2, 4, 6, 8, 9};

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
    void laneRotationsMatchShaShapes() {
        Random rnd = new Random(SEED + 8);
        for (int len : WIDTHS) {
            long[] v = new long[len];
            for (int i = 0; i < len; i++) {
                v[i] = rnd.nextLong();
            }
            for (int k : new int[] {1, 8, 19, 28, 34, 39, 41, 61}) {
                long[] rotR = v.clone();
                ModArith.rotrEach(rotR, k);
                long[] rotL = v.clone();
                ModArith.rotlEach(rotL, k);
                long[] shr = v.clone();
                ModArith.shrEach(shr, k);
                for (int i = 0; i < len; i++) {
                    assertEquals(Long.rotateRight(v[i], k), rotR[i], "rotrEach " + k);
                    assertEquals(Long.rotateLeft(v[i], k), rotL[i], "rotlEach " + k);
                    assertEquals(v[i] >>> k, shr[i], "shrEach " + k);
                }
            }
            for (int k : new int[] {2, 7, 13, 6, 11, 25}) {
                long[] rot = v.clone();
                ModArith.rotrEach32(rot, k);
                long[] shr = v.clone();
                ModArith.shrEach32(shr, k);
                for (int i = 0; i < len; i++) {
                    long lo = Integer.toUnsignedLong(Integer.rotateRight((int) v[i], k));
                    long hi = Integer.toUnsignedLong(Integer.rotateRight((int) (v[i] >>> 32), k));
                    assertEquals((hi << 32) | lo, rot[i], "rotrEach32 " + k);
                    long slo = (v[i] & 0xFFFF_FFFFL) >>> k;
                    long shi = (v[i] >>> 32) >>> k;
                    assertEquals((shi << 32) | slo, shr[i], "shrEach32 " + k);
                }
            }
        }
    }

    @Test
    void bytesRoundTripBigEndianWords() {
        Random rnd = new Random(SEED + 9);
        for (int len : WIDTHS) {
            long[] v = new long[len];
            for (int i = 0; i < len; i++) {
                v[i] = rnd.nextLong();
            }
            byte[] bytes = new byte[len * 8];
            ModArith.toBytesBE(v, bytes, 0);
            long first = 0L;
            for (int b = 0; b < 8; b++) {
                first = (first << 8) | (bytes[b] & 0xFFL);
            }
            assertEquals(v[0], first, "first word byte order");
            long[] back = new long[len];
            ModArith.fromBytesBE(bytes, 0, back);
            assertArrayEquals(v, back, "round trip");
        }
    }

    @Test
    void p521MersennePrimeWorksAtNineLimbs() {
        // P-521 uses p = 2^521 - 1; nine 64-bit limbs hold it with 55 spare bits.
        BigInteger p521 = BigInteger.ONE.shiftLeft(521).subtract(BigInteger.ONE);
        int len = 9;
        long[] p = toLimbs(p521, len);
        long[] minusOne = toLimbs(p521.subtract(BigInteger.ONE), len);
        long[] out = new long[len];

        // (p-1)^2 mod p == 1, because p-1 == -1
        ModArith.mulMod(minusOne, minusOne, p, out);
        assertEquals(BigInteger.ONE, toBig(out), "(p-1)^2 mod p");

        // (p-1) + 1 == 0, (p-1) - 1 == p-2
        long[] one = toLimbs(BigInteger.ONE, len);
        ModArith.addMod(minusOne, one, p, out);
        assertEquals(BigInteger.ZERO, toBig(out), "(p-1)+1");
        ModArith.subMod(minusOne, one, p, out);
        assertEquals(p521.subtract(BigInteger.TWO), toBig(out), "(p-1)-1");

        // 2^521 needs bit 521, which nine limbs hold; p itself does not reach it.
        long[] two521 = toLimbs(BigInteger.ONE.shiftLeft(521), len);
        assertTrue(ModArith.testBit(two521, 521), "bit 521 fits in nine limbs");
        assertFalse(ModArith.testBit(p, 521), "p = 2^521-1 has no bit 521");
        assertEquals(521, toBig(p).bitLength(), "p is 521 bits");
    }

    @Test
    void modulusZeroIsRejected() {
        long[] m = new long[2];
        assertThrows(ArithmeticException.class,
                () -> ModArith.addMod(new long[2], new long[2], m, new long[2]));
        assertThrows(ArithmeticException.class,
                () -> ModArith.mulMod(new long[2], new long[2], m, new long[2]));
    }

    // ---------------------------------------------------------------- Montgomery

    @Test
    void montgomeryMatchesBigIntegerAtEveryWidth() {
        Random rnd = new Random(SEED + 10);
        BigInteger two64 = BigInteger.ONE.shiftLeft(64);
        for (int len : WIDTHS) {
            BigInteger r = BigInteger.ONE.shiftLeft(64 * len);
            for (int i = 0; i < N / 2; i++) {
                BigInteger m = oddModulus(rnd, len);
                BigInteger a = randomBelow(rnd, m);
                BigInteger b = randomBelow(rnd, m);
                long[] ml = toLimbs(m, len);
                long[] al = toLimbs(a, len);
                long[] bl = toLimbs(b, len);

                // n0 = -m^-1 mod 2^64
                long n0 = ModArith.n0inv(ml);
                assertEquals(BigInteger.ONE.negate().mod(two64),
                        unsigned(ml[0]).multiply(unsigned(n0)).mod(two64), "n0inv len=" + len);

                // toMont / fromMont round trip
                long[] am = new long[len];
                ModArith.toMont(al, ml, am);
                assertEquals(a.multiply(r).mod(m), toBig(am), "toMont len=" + len);
                long[] back = new long[len];
                ModArith.fromMont(am, ml, back);
                assertEquals(a, toBig(back), "toMont->fromMont len=" + len);

                // mulMont(a, b) == a*b*R^-1 mod m
                long[] out = new long[len];
                ModArith.mulMont(al, bl, ml, out);
                assertEquals(a.multiply(b).mod(m).multiply(r.modInverse(m)).mod(m), toBig(out),
                        "mulMont len=" + len);

                // the whole Montgomery pipeline is the plain product
                long[] bm = new long[len];
                ModArith.toMont(bl, ml, bm);
                ModArith.mulMont(am, bm, ml, out);
                ModArith.fromMont(out, ml, out);
                assertEquals(a.multiply(b).mod(m), toBig(out), "montgomery pipeline len=" + len);
            }
        }
    }

    @Test
    void montgomeryNeighbourhoodAndExtremeModuli() {
        Random rnd = new Random(SEED + 11);
        int len = 4;
        long[] out = new long[len];
        // m = 1: every residue is 0, R mod 1 = 0, n0 = -1
        long[] one = new long[len];
        one[0] = 1L;
        long[] zero = new long[len];
        assertEquals(-1L, ModArith.n0inv(one), "n0inv(1)");
        ModArith.mulMod(zero, zero, one, out);
        assertEquals(BigInteger.ZERO, toBig(out), "mulMod mod 1");
        ModArith.powMod(zero, zero, one, out);
        assertEquals(BigInteger.ZERO, toBig(out), "powMod mod 1");
        ModArith.toMont(zero, one, out);
        assertEquals(BigInteger.ZERO, toBig(out), "toMont mod 1");

        for (int i = 0; i < N / 4; i++) {
            BigInteger m = oddModulus(rnd, len);
            long[] ml = toLimbs(m, len);
            BigInteger[] vals = {
                    BigInteger.ZERO, BigInteger.ONE, BigInteger.TWO,
                    m.shiftRight(1), m.subtract(BigInteger.TWO), m.subtract(BigInteger.ONE)};
            for (BigInteger a : vals) {
                for (BigInteger b : vals) {
                    ModArith.mulMod(toLimbs(a, len), toLimbs(b, len), ml, out);
                    assertEquals(a.multiply(b).mod(m), toBig(out), "mulMod neighbourhood");
                }
            }
            // the modulus that fills the width completely, and one whose top limb is 0x8000...
            BigInteger fill = BigInteger.ONE.shiftLeft(64 * len).subtract(BigInteger.ONE);
            BigInteger half = BigInteger.ONE.shiftLeft(64 * len - 1).add(BigInteger.ONE);
            for (BigInteger mm : new BigInteger[] {fill, half, BigInteger.valueOf(3)}) {
                long[] mml = toLimbs(mm, len);
                for (BigInteger a : new BigInteger[] {
                        BigInteger.ZERO, BigInteger.ONE, mm.subtract(BigInteger.ONE)}) {
                    for (BigInteger b : new BigInteger[] {
                            BigInteger.ZERO, BigInteger.ONE, mm.subtract(BigInteger.ONE)}) {
                        ModArith.mulMod(toLimbs(a, len), toLimbs(b, len), mml, out);
                        assertEquals(a.multiply(b).mod(mm), toBig(out), "mulMod modulus=" + mm);
                        ModArith.powMod(toLimbs(a, len), toLimbs(BigInteger.valueOf(5), len), mml, out);
                        assertEquals(a.modPow(BigInteger.valueOf(5), mm), toBig(out), "powMod modulus=" + mm);
                    }
                }
            }
        }
    }

    @Test
    void montgomeryEvenModulusFallsBackAndRejects() {
        Random rnd = new Random(SEED + 12);
        for (int len : WIDTHS) {
            for (int i = 0; i < N / 8; i++) {
                BigInteger m = new BigInteger(len * 64, rnd).setBit(len * 64 - 1).clearBit(0);
                BigInteger a = randomBelow(rnd, m);
                BigInteger b = randomBelow(rnd, m);
                long[] ml = toLimbs(m, len);
                long[] al = toLimbs(a, len);
                long[] bl = toLimbs(b, len);
                long[] out = new long[len];
                ModArith.mulMod(al, bl, ml, out);
                assertEquals(a.multiply(b).mod(m), toBig(out), "mulMod even len=" + len);
                BigInteger exp = new BigInteger(m.bitLength(), rnd).mod(m);
                ModArith.powMod(al, toLimbs(exp, len), ml, out);
                assertEquals(a.modPow(exp, m), toBig(out), "powMod even len=" + len);
                ModArith.powModCt(al, toLimbs(exp, len), ml, out);
                assertEquals(a.modPow(exp, m), toBig(out), "powModCt even len=" + len);
                assertThrows(ArithmeticException.class, () -> ModArith.n0inv(ml), "n0inv even");
                assertThrows(ArithmeticException.class, () -> ModArith.toMont(al, ml, out), "toMont even");
                assertThrows(ArithmeticException.class, () -> ModArith.fromMont(al, ml, out), "fromMont even");
                assertThrows(ArithmeticException.class, () -> ModArith.mulMont(al, bl, ml, out), "mulMont even");
            }
        }
    }

    @Test
    void montgomeryP521FixedPoints() {
        BigInteger p = BigInteger.ONE.shiftLeft(521).subtract(BigInteger.ONE);
        BigInteger r = BigInteger.ONE.shiftLeft(64 * 9);
        int len = 9;
        long[] pl = toLimbs(p, len);
        long[] pm1 = toLimbs(p.subtract(BigInteger.ONE), len);
        long[] out = new long[len];
        assertEquals(BigInteger.ONE.negate().mod(BigInteger.ONE.shiftLeft(64)),
                unsigned(pl[0]).multiply(unsigned(ModArith.n0inv(pl)))
                        .mod(BigInteger.ONE.shiftLeft(64)), "n0inv(p)");

        long[] pm1m = new long[len];
        ModArith.toMont(pm1, pl, pm1m);
        assertEquals(p.subtract(BigInteger.ONE).multiply(r).mod(p), toBig(pm1m), "toMont(p-1)");
        ModArith.mulMont(pm1m, pm1m, pl, out);
        ModArith.fromMont(out, pl, out);
        assertEquals(BigInteger.ONE, toBig(out), "(p-1)^2 mod p");

        // R mod p is 2^(576-521) = 2^55, and fromMont maps it back to 1
        long[] one = toLimbs(BigInteger.ONE, len);
        long[] rm = new long[len];
        ModArith.toMont(one, pl, rm);
        assertEquals(BigInteger.TWO.pow(55), toBig(rm), "R mod p");
        ModArith.fromMont(rm, pl, out);
        assertEquals(BigInteger.ONE, toBig(out), "fromMont(R)");
        ModArith.mulMont(rm, rm, pl, out);
        assertEquals(BigInteger.TWO.pow(55), toBig(out), "R*R*R^-1 = R");
    }

    @Test
    void montgomeryExhaustiveSmallModuli() {
        // Every pair below m for the smallest odd moduli, at widths whose top limbs are zero:
        // exercises hidden leading zeros, m = 1, and the 1-limb / 9-limb loop bounds.
        for (int len : new int[] {1, 2, 3, 9}) {
            for (long mv = 1; mv <= 31; mv += 2) {
                BigInteger m = BigInteger.valueOf(mv);
                long[] ml = toLimbs(m, len);
                for (long x = 0; x < mv; x++) {
                    for (long y = 0; y < mv; y++) {
                        long[] out = new long[len];
                        ModArith.mulMod(toLimbs(BigInteger.valueOf(x), len),
                                toLimbs(BigInteger.valueOf(y), len), ml, out);
                        assertEquals(BigInteger.valueOf(x * y).mod(m), toBig(out),
                                "mulMod m=" + mv + " len=" + len + " x=" + x + " y=" + y);
                    }
                }
            }
        }
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

    /** A {@code long} read as unsigned. */
    static BigInteger unsigned(long v) {
        BigInteger w = BigInteger.valueOf(v & Long.MAX_VALUE);
        return v < 0 ? w.setBit(63) : w;
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
