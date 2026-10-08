package com.scalarwide;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MulFullTest {

    private static final Random RND = new Random(TestSupport.SEED);

    @Test
    void wide128FullProductMatchesBigInteger() {
        for (BigInteger a : TestSupport.extremes(128)) {
            for (BigInteger b : TestSupport.extremes(128)) {
                check128(a, b);
            }
        }
        for (int i = 0; i < 5000; i++) {
            check128(new BigInteger(128, RND), new BigInteger(128, RND));
        }
    }

    @Test
    void wide256FullProductMatchesBigInteger() {
        for (BigInteger a : TestSupport.extremes(256)) {
            for (BigInteger b : TestSupport.extremes(256)) {
                check256(a, b);
            }
        }
        for (int i = 0; i < 3000; i++) {
            check256(new BigInteger(256, RND), new BigInteger(256, RND));
        }
    }

    @Test
    void engineCrossOracle() {
        for (int i = 0; i < 2000; i++) {
            checkEngine128(new BigInteger(128, RND), new BigInteger(128, RND));
            checkEngine256(new BigInteger(256, RND), new BigInteger(256, RND));
        }
    }

    private static void check128(BigInteger a, BigInteger b) {
        Wide128 wa = Wide128.fromBigInteger(a);
        Wide128 wb = Wide128.fromBigInteger(b);
        Wide256 full = wa.mulFull(wb);
        assertEquals(a.multiply(b), full.toBigInteger(), "mulFull 128");
        Wide128 lo = wa.mul(wb);
        Wide128 hi = wa.mulHigh(wb);
        assertEquals(full.limb(0), lo.limb(0), "mul vs mulFull limb 0");
        assertEquals(full.limb(1), lo.limb(1), "mul vs mulFull limb 1");
        assertEquals(full.limb(2), hi.limb(0), "mulHigh vs mulFull limb 0");
        assertEquals(full.limb(3), hi.limb(1), "mulHigh vs mulFull limb 1");
    }

    private static void check256(BigInteger a, BigInteger b) {
        Wide256 wa = Wide256.fromBigInteger(a);
        Wide256 wb = Wide256.fromBigInteger(b);
        Wide512 full = wa.mulFull(wb);
        assertEquals(a.multiply(b), full.toBigInteger(), "mulFull 256");
        Wide256 lo = wa.mul(wb);
        Wide256 hi = wa.mulHigh(wb);
        for (int i = 0; i < 4; i++) {
            assertEquals(full.limb(i), lo.limb(i), "mul vs mulFull limb " + i);
            assertEquals(full.limb(i + 4), hi.limb(i), "mulHigh vs mulFull limb " + i);
        }
    }

    private static void checkEngine128(BigInteger a, BigInteger b) {
        Wide128 wa = Wide128.fromBigInteger(a);
        Wide128 wb = Wide128.fromBigInteger(b);
        long[] pa = new long[Wide128.LIMBS];
        long[] pb = new long[Wide128.LIMBS];
        for (int i = 0; i < Wide128.LIMBS; i++) {
            pa[i] = wa.limb(i);
            pb[i] = wb.limb(i);
        }
        long[] ref = Limbs.mul(pa, pb);
        Wide256 full = wa.mulFull(wb);
        for (int i = 0; i < ref.length; i++) {
            assertEquals(ref[i], full.limb(i), "engine 128 limb " + i);
        }
    }

    private static void checkEngine256(BigInteger a, BigInteger b) {
        Wide256 wa = Wide256.fromBigInteger(a);
        Wide256 wb = Wide256.fromBigInteger(b);
        long[] pa = new long[Wide256.LIMBS];
        long[] pb = new long[Wide256.LIMBS];
        for (int i = 0; i < Wide256.LIMBS; i++) {
            pa[i] = wa.limb(i);
            pb[i] = wb.limb(i);
        }
        long[] ref = Limbs.mul(pa, pb);
        Wide512 full = wa.mulFull(wb);
        for (int i = 0; i < ref.length; i++) {
            assertEquals(ref[i], full.limb(i), "engine 256 limb " + i);
        }
    }
}
