package com.scalarwide.mc.fp;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Differential gate for the 1280-bit container.
 *
 * <p>Expectations are derived from the container's own {@code (M, lsbExp, sign)} triple with an
 * exact {@link BigInteger} reference, so the test checks the split at the binary point instead of
 * re-deriving the normalisation rule. Landmarks are the magnitudes the G1.1 budget names:
 * 2^53, 2^600, 2^1016 and 2^1027, both signs.
 */
class Binary1280Test {

    private static final long SEED = 12345L;
    private static final int N = 5000;
    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger ZERO = BigInteger.ZERO;

    @Test
    void setLongIsExact() {
        long[] values = {0L, 1L, -1L, 2L, -2L, 1L << 53, (1L << 53) + 1, Long.MAX_VALUE, Long.MIN_VALUE};
        Binary1280 v = new Binary1280();
        for (long x : values) {
            v.set(x);
            assertEquals(BigInteger.valueOf(x), exact(v), "set(" + x + ")");
        }
        Random rnd = new Random(SEED);
        for (int i = 0; i < N; i++) {
            long x = rnd.nextLong();
            v.set(x);
            assertEquals(BigInteger.valueOf(x), exact(v), "set(" + x + ")");
            v.set(-x);
            assertEquals(BigInteger.valueOf(-x), exact(v), "set(" + -x + ")");
        }
    }

    @Test
    void scalePow2OnlyMovesTheExponent() {
        Random rnd = new Random(SEED + 1);
        Binary1280 v = new Binary1280();
        for (int i = 0; i < N; i++) {
            v.set(rnd.nextLong() | 1L);
            long[] before = limbs(v);
            int e = v.lsbExponent();
            int k = rnd.nextInt(2000) - 1000;
            v.scalePow2(k);
            assertEquals(e + k, v.lsbExponent(), "exponent");
            assertLimbs(before, limbs(v), "limbs must not move");
        }
        v.set(1L);
        v.scalePow2(Integer.MAX_VALUE);
        v.scalePow2(1251);
        assertEquals(Integer.MAX_VALUE, v.lsbExponent());
        assertThrows(ArithmeticException.class, () -> v.scalePow2(1));
    }

    @Test
    void mulSmallMatchesTheExactProduct() {
        Random rnd = new Random(SEED + 2);
        Binary1280 v = new Binary1280();
        for (int i = 0; i < N; i++) {
            v.set(rnd.nextLong());
            long[] before = limbs(v);
            int e0 = v.lsbExponent();
            boolean s0 = v.signBit();
            long w = rnd.nextBoolean() ? (rnd.nextLong() >>> 12) : rnd.nextLong();
            BigInteger prod = magnitudeOf(before).multiply(BigInteger.valueOf(w < 0 ? -w : w));
            int drop = Math.max(0, prod.bitLength() - Binary1280.PRECISION);
            BigInteger wantMag = prod.shiftRight(drop);
            v.mulSmall(w);
            assertEquals(wantMag, magnitudeOf(limbs(v)), "magnitude x=" + i + " w=" + w);
            assertEquals(e0 + drop, v.lsbExponent(), "exponent x=" + i);
            assertEquals(s0 ^ (w < 0), v.signBit(), "sign x=" + i);
        }
        v.set(7L);
        v.mulSmall(0L);
        assertTrue(v.isZero());
    }

    @Test
    void floorLow64FracMatchesTheExactSplit() {
        Random rnd = new Random(SEED + 3);
        Binary1280 v = new Binary1280();
        long[] out = new long[1];
        for (int i = 0; i < N; i++) {
            long[] mag = new long[Binary1280.LIMBS];
            for (int k = 0; k < mag.length; k++) {
                mag[k] = rnd.nextLong();
            }
            mag[Binary1280.LIMBS - 1] |= 1L << 44;
            int exp = rnd.nextInt(1500) - 1300;
            boolean neg = rnd.nextBoolean();
            v.setMagnitude(mag, exp, neg);
            double frac = v.floorLow64Frac(out);
            BigInteger m = magnitudeOf(mag);
            assertEquals(refLow64(m, exp, neg), out[0], "low64 e=" + exp + " neg=" + neg);
            assertEquals(refFrac(m, exp, neg), frac, 0.0, "frac e=" + exp + " neg=" + neg);
        }
    }

    @Test
    void landmarkMagnitudes() {
        Binary1280 v = new Binary1280();
        long[] out = new long[1];
        for (int lead : new int[] {53, 600, 1016, 1027}) {
            for (boolean neg : new boolean[] {false, true}) {
                // 2^lead, 2^lead - 0.5 and 2^lead + 0.5 at the magnitudes the budget names.
                BigInteger[] magnitudes = {
                        ONE.shiftLeft(1251),
                        ONE.shiftLeft(1251).subtract(ONE.shiftLeft(1250 - lead)),
                        ONE.shiftLeft(1251).add(ONE.shiftLeft(1250 - lead)),
                };
                for (BigInteger m : magnitudes) {
                    int exp = lead - 1251;
                    v.setMagnitude(limbsOf(m), exp, neg);
                    double frac = v.floorLow64Frac(out);
                    String label = "2^" + lead + " m=" + m.bitLength() + " neg=" + neg;
                    assertEquals(refLow64(m, exp, neg), out[0], label + " low64");
                    assertEquals(refFrac(m, exp, neg), frac, 0.0, label + " frac");
                }
            }
        }
        // The two halves at 2^53 specifically: floor stays integral below the point.
        v.setMagnitude(limbsOf(ONE.shiftLeft(1251).subtract(ONE.shiftLeft(1197))), 53 - 1251, false);
        assertEquals(0.5, v.floorLow64Frac(out), 0.0, "2^53 - 0.5 fraction");
        assertEquals((1L << 53) - 1, out[0], "2^53 - 0.5 floor");
    }

    @Test
    void setFromLoadsAWideIntegerExactly() {
        Random rnd = new Random(SEED + 4);
        Binary1280 v = new Binary1280();
        for (int i = 0; i < N; i++) {
            int len = 1 + rnd.nextInt(20);
            long[] limbs = new long[len];
            for (int k = 0; k < len; k++) {
                limbs[k] = rnd.nextLong();
            }
            limbs[len - 1] &= 0x0FFFL;
            if (limbs[len - 1] == 0L) {
                limbs[len - 1] = 1L;
            }
            v.setFrom(limbs);
            assertEquals(magnitudeOf(limbs), exact(v), "setFrom len=" + len);
        }
        // A 1024-bit chunk coordinate, the cold-path payload the requirement names.
        long[] chunk = new long[16];
        chunk[15] = 1L << 40;
        chunk[7] = 0x0123_4567_89AB_CDEFL;
        v.setFrom(chunk);
        assertEquals(magnitudeOf(chunk), exact(v), "1024-bit chunk coordinate");

        long[] oversized = new long[20];
        java.util.Arrays.fill(oversized, -1L);
        assertThrows(ArithmeticException.class, () -> v.setFrom(oversized));
    }

    @Test
    void addSmallStaysExact() {
        Random rnd = new Random(SEED + 5);
        Binary1280 v = new Binary1280();
        for (int i = 0; i < N; i++) {
            long[] limbs = new long[16];
            for (int k = 0; k < limbs.length; k++) {
                limbs[k] = rnd.nextLong();
            }
            limbs[15] |= 1L << 40;
            v.setFrom(limbs);
            if (rnd.nextBoolean()) {
                v.mulSmall(-1L);
            }
            long delta = rnd.nextInt(31) - 15;
            BigInteger before = exact(v);
            v.addSmall(delta);
            assertEquals(before.add(BigInteger.valueOf(delta)), exact(v), "addSmall delta=" + delta);
        }
        // The far-field pattern the requirement describes: 2^1027 + local offset 0..15.
        v.set(1L);
        v.scalePow2(1027);
        v.addSmall(15L);
        assertEquals(ONE.shiftLeft(1027).add(BigInteger.valueOf(15)), exact(v), "2^1027 + 15");

        // Crossing zero and the renormalisation that follows.
        v.set(3L);
        v.addSmall(-10L);
        assertEquals(BigInteger.valueOf(-7), exact(v), "3 - 10");
        v.set(5L);
        v.addSmall(-5L);
        assertTrue(v.isZero(), "5 - 5");
        v.setZero();
        v.addSmall(-9L);
        assertEquals(BigInteger.valueOf(-9), exact(v), "zero - 9");
    }

    @Test
    void compareFollowsNumericOrder() {
        Binary1280 a = new Binary1280();
        Binary1280 b = new Binary1280();
        a.set(1L);
        b.set(2L);
        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
        assertEquals(0, a.compareTo(b.set(1L)));
        a.set(-1L);
        assertTrue(a.compareTo(b) < 0);
        a.setZero();
        assertTrue(a.compareTo(b) < 0);
        b.setZero();
        assertEquals(0, a.compareTo(b));
        a.set(-5L);
        b.set(-7L);
        assertTrue(a.compareTo(b) > 0);
    }

    @Test
    void addSmallStaysExactAtEveryEntryLimb() {
        Random rnd = new Random(SEED + 7);
        Binary1280 v = new Binary1280();
        int groups = 0;
        for (int len = 1; len <= Binary1280.LIMBS; len++) {
            for (int k = 0; k < 15; k++) {
                long[] limbs = new long[len];
                for (int i = 0; i < len; i++) {
                    limbs[i] = rnd.nextLong();
                }
                // A top limb of 1..12 significant bits puts the LSB at limb 20 - len, so the
                // sequence walks every addSmall entry limb; integer values keep exact() lossless.
                limbs[len - 1] = (limbs[len - 1] & 0x0FFFL) | 1L;
                v.setFrom(limbs);
                assertEquals(20 - len, (-v.lsbExponent()) >>> 6, "entry limb for len=" + len);
                if (rnd.nextBoolean()) {
                    v.mulSmall(-1L);
                }
                long delta = (rnd.nextInt(4) == 0 ? -1L : 1L) * (1L + rnd.nextInt(15));
                BigInteger before = exact(v);
                v.addSmall(delta);
                assertEquals(before.add(BigInteger.valueOf(delta)), exact(v),
                        "addSmall len=" + len + " delta=" + delta);
                groups++;
            }
        }
        // Crossing zero needs a magnitude the offset can pass; those values enter at limb 19.
        for (int k = 0; k < 100; k++) {
            long base = 1L + rnd.nextInt(4000);
            long delta = -base - rnd.nextInt(16);
            v.set(base);
            assertEquals(19, (-v.lsbExponent()) >>> 6, "small values enter at limb 19");
            BigInteger before = exact(v);
            v.addSmall(delta);
            assertEquals(before.add(BigInteger.valueOf(delta)), exact(v),
                    "addSmall crossing base=" + base + " delta=" + delta);
            groups++;
        }
        assertTrue(groups >= 300, "addSmall coverage groups: " + groups);
    }

    @Test
    void mulSmallMatchesExactProductOnFullWidthMantissas() {
        Random rnd = new Random(SEED + 8);
        Binary1280 v = new Binary1280();
        long[] multipliers = {3L, -3L, 0x9E3779B97F4A7C15L, -0x9E3779B97F4A7C15L,
                0x1_0000_0001L, Long.MAX_VALUE};
        int truncations = 0;
        for (int i = 0; i < 400; i++) {
            long[] mag = new long[Binary1280.LIMBS];
            for (int k = 0; k < mag.length; k++) {
                mag[k] = rnd.nextLong();
            }
            mag[Binary1280.LIMBS - 1] =
                    (mag[Binary1280.LIMBS - 1] & 0xFFFFFFFFFL) | (1L << 35);
            int exp = -rnd.nextInt(1300);
            boolean neg = rnd.nextBoolean();
            v.setMagnitude(mag, exp, neg);
            long w = multipliers[i % multipliers.length];
            BigInteger prod = magnitudeOf(mag).multiply(bigMagnitude(w));
            int drop = Math.max(0, prod.bitLength() - Binary1280.PRECISION);
            if (drop > 0) {
                truncations++;
            }
            v.mulSmall(w);
            assertEquals(prod.shiftRight(drop), magnitudeOf(limbs(v)),
                    "mulSmall i=" + i + " w=" + w);
            assertEquals(exp + drop, v.lsbExponent(), "exponent i=" + i + " w=" + w);
            assertEquals(neg ^ (w < 0L), v.signBit(), "sign i=" + i + " w=" + w);
        }
        assertTrue(truncations >= 300, "truncation coverage: " + truncations);
    }

    @Test
    void floorLow64FracMatchesEveryWindowPosition() {
        Random rnd = new Random(SEED + 9);
        Binary1280 v = new Binary1280();
        long[] out = new long[1];
        for (int i = 0; i < 400; i++) {
            int s = i * 1251 / 399;
            long[] mag = new long[Binary1280.LIMBS];
            for (int k = 0; k < mag.length; k++) {
                mag[k] = rnd.nextLong();
            }
            mag[Binary1280.LIMBS - 1] =
                    (mag[Binary1280.LIMBS - 1] & 0xFFFFFFFFFL) | (1L << 35);
            boolean neg = rnd.nextBoolean();
            v.setMagnitude(mag, -s, neg);
            double frac = v.floorLow64Frac(out);
            BigInteger m = magnitudeOf(mag);
            assertEquals(refLow64(m, -s, neg), out[0], "low64 s=" + s + " neg=" + neg);
            assertEquals(refFrac(m, -s, neg), frac, 0.0, "frac s=" + s + " neg=" + neg);
        }
    }

    @Test
    void setFromLoadsSixteenLimbChunkCoordinates() {
        Random rnd = new Random(SEED + 10);
        Binary1280 v = new Binary1280();
        for (int i = 0; i < 300; i++) {
            long[] chunk = new long[16];
            for (int k = 0; k < chunk.length; k++) {
                chunk[k] = rnd.nextLong();
            }
            chunk[15] = (chunk[15] & 0xFFFFFFFFFFL) | 1L;
            v.setFrom(chunk);
            assertEquals(magnitudeOf(chunk), exact(v), "16-limb chunk i=" + i);
        }
        // The 1024-bit payload the requirement names, this time as a named landmark.
        long[] named = new long[16];
        named[15] = 1L << 40;
        named[7] = 0x0123_4567_89AB_CDEFL;
        v.setFrom(named);
        assertEquals(magnitudeOf(named), exact(v), "1024-bit chunk coordinate");
    }

    @Test
    void compareToFollowsNumericOrderAcrossExponents() {
        Random rnd = new Random(SEED + 11);
        Binary1280 a = new Binary1280();
        Binary1280 b = new Binary1280();
        for (int i = 0; i < 400; i++) {
            long[] ma = randomMantissa(rnd);
            long[] mb = rnd.nextInt(5) == 0 ? ma.clone() : randomMantissa(rnd);
            int ea = -rnd.nextInt(1300);
            int eb = rnd.nextInt(4) == 0 ? ea : -rnd.nextInt(1300);
            a.setMagnitude(ma, ea, rnd.nextBoolean());
            b.setMagnitude(mb, eb, rnd.nextBoolean());
            if (rnd.nextInt(10) == 0) {
                a.setZero();
            }
            if (rnd.nextInt(10) == 0) {
                b.setZero();
            }
            int want = numericCompare(a, b);
            assertEquals(Integer.signum(want), Integer.signum(a.compareTo(b)), "compare i=" + i);
            assertEquals(Integer.signum(-want), Integer.signum(b.compareTo(a)),
                    "reverse i=" + i);
        }
    }

    // ---------------------------------------------------------------- reference

    private static BigInteger exact(Binary1280 v) {
        BigInteger m = BigInteger.ZERO;
        for (int i = Binary1280.LIMBS - 1; i >= 0; i--) {
            m = m.shiftLeft(64).or(unsigned(v.limb(i)));
        }
        int e = v.lsbExponent();
        BigInteger mag = e >= 0 ? m.shiftLeft(e) : m.shiftRight(-e);
        return v.signBit() ? mag.negate() : mag;
    }

    private static long refLow64(BigInteger m, int e, boolean negative) {
        BigInteger floor;
        BigInteger rem;
        if (e >= 0) {
            floor = m.shiftLeft(e);
            rem = ZERO;
        } else {
            floor = m.shiftRight(-e);
            rem = m.and(ONE.shiftLeft(-e).subtract(ONE));
        }
        if (negative) {
            floor = rem.signum() == 0 ? floor.negate() : floor.add(ONE).negate();
        }
        return floor.longValue();
    }

    private static double refFrac(BigInteger m, int e, boolean negative) {
        if (e >= 0) {
            return 0.0;
        }
        int s = -e;
        BigInteger rem = m.and(ONE.shiftLeft(s).subtract(ONE));
        if (rem.signum() == 0) {
            return 0.0;
        }
        if (negative) {
            rem = ONE.shiftLeft(s).subtract(rem);
        }
        BigInteger f53 = s >= 53 ? rem.shiftRight(s - 53) : rem.shiftLeft(53 - s);
        return f53.longValue() * 0x1.0p-53;
    }

    private static BigInteger magnitudeOf(long[] limbs) {
        BigInteger m = BigInteger.ZERO;
        for (int i = limbs.length - 1; i >= 0; i--) {
            m = m.shiftLeft(64).or(unsigned(limbs[i]));
        }
        return m;
    }

    private static long[] limbsOf(BigInteger m) {
        long[] r = new long[Binary1280.LIMBS];
        BigInteger work = m;
        for (int i = 0; i < r.length; i++) {
            r[i] = work.longValue();
            work = work.shiftRight(64);
        }
        return r;
    }

    private static BigInteger unsigned(long v) {
        BigInteger b = BigInteger.valueOf(v & Long.MAX_VALUE);
        return v < 0 ? b.setBit(63) : b;
    }

    private static long[] limbs(Binary1280 v) {
        long[] r = new long[Binary1280.LIMBS];
        for (int i = 0; i < r.length; i++) {
            r[i] = v.limb(i);
        }
        return r;
    }

    private static void assertLimbs(long[] a, long[] b, String label) {
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i], b[i], label + " limb " + i);
        }
    }

    /** Canonical 1252-bit mantissa: bit 35 set, nothing above it. */
    private static long[] randomMantissa(Random rnd) {
        long[] mag = new long[Binary1280.LIMBS];
        for (int i = 0; i < mag.length; i++) {
            mag[i] = rnd.nextLong();
        }
        mag[Binary1280.LIMBS - 1] = (mag[Binary1280.LIMBS - 1] & 0xFFFFFFFFFL) | (1L << 35);
        return mag;
    }

    /** {@code |w|} as an unsigned integer; {@code w} never equals {@code Long.MIN_VALUE} here. */
    private static BigInteger bigMagnitude(long w) {
        return w < 0L ? BigInteger.valueOf(-w) : unsigned(w);
    }

    /** Numeric order of the two value triples, with both sides scaled to a common exponent. */
    private static int numericCompare(Binary1280 a, Binary1280 b) {
        BigInteger ma = magnitudeOf(limbs(a));
        BigInteger mb = magnitudeOf(limbs(b));
        boolean na = a.signBit();
        boolean nb = b.signBit();
        if (ma.signum() == 0 || mb.signum() == 0) {
            if (ma.signum() == 0) {
                return mb.signum() == 0 ? 0 : (nb ? 1 : -1);
            }
            return na ? -1 : 1;
        }
        if (na != nb) {
            return na ? -1 : 1;
        }
        int ea = a.lsbExponent();
        int eb = b.lsbExponent();
        int e = Math.min(ea, eb);
        int c = ma.shiftLeft(ea - e).compareTo(mb.shiftLeft(eb - e));
        return na ? -c : c;
    }
}
