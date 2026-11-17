package com.scalarwide.mc.fp;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Differential gate for the shared {@link Container} implementation, run through both the 256-bit
 * and 640-bit shells. The 1280-bit container has its own fully unrolled implementation and gate.
 *
 * <p>The container stores the magnitude left-aligned in the precision window: a value with
 * {@code b} significant bits is kept as {@code M = magnitude << (precision - b)} with
 * {@code lsbExp = b - precision}, so {@code value = (-1)^sign * M * 2^lsbExp}. Every expectation
 * below is derived from that model, never from the container's own intermediate state.
 */
class ContainerTest {

    private static final long SEED = 12345L;
    private static final int N = 1500;

    private static List<Supplier<Container>> factories() {
        return List.of(Binary256::new, Binary640::new);
    }

    @Test
    void setAndSetFromLandInThePrecisionWindow() {
        for (Supplier<Container> f : factories()) {
            Container v = f.get();
            Random rnd = new Random(SEED);
            for (int i = 0; i < N; i++) {
                long x = rnd.nextLong();
                v.set(x);
                assertEquals(BigInteger.valueOf(x), exact(v), "set " + x);
            }
            for (int i = 0; i < N; i++) {
                int bits = 1 + rnd.nextInt(v.precision);
                BigInteger mag = BigInteger.ONE.shiftLeft(bits - 1).or(new BigInteger(bits - 1, rnd));
                v.setFrom(toLimbs(mag, v.limbs));
                assertEquals(bits - v.precision, v.lsbExponent(), "setFrom exponent");
                assertEquals(mag.shiftLeft(v.precision - bits), stored(v), "setFrom window");
                assertEquals(mag, exact(v), "setFrom value");
            }
        }
    }

    @Test
    void mulSmallMatchesTheTruncationContract() {
        for (Supplier<Container> f : factories()) {
            Container v = f.get();
            Random rnd = new Random(SEED + 1);
            for (int i = 0; i < N; i++) {
                int bits = 1 + rnd.nextInt(v.precision);
                BigInteger mag = BigInteger.ONE.shiftLeft(bits - 1).or(new BigInteger(bits - 1, rnd));
                v.setFrom(toLimbs(mag, v.limbs));
                BigInteger m0 = stored(v);
                int e0 = v.lsbExponent();
                long w = rnd.nextBoolean() ? rnd.nextInt() : rnd.nextLong();
                BigInteger prod = m0.multiply(BigInteger.valueOf(w < 0 ? -w : w));
                int drop = Math.max(0, prod.bitLength() - v.precision);
                v.mulSmall(w);
                assertEquals(prod.shiftRight(drop), stored(v), "mulSmall magnitude");
                assertEquals(e0 + drop, v.lsbExponent(), "mulSmall exponent");
                assertEquals(w < 0 ? -1 : 1, v.signum(), "mulSmall sign");
                if (!v.isZero()) {
                    assertEquals(v.precision, stored(v).bitLength(), "mulSmall stays normalised");
                }
            }
        }
    }

    @Test
    void addSmallIsExactAtEveryEntryLimb() {
        for (Supplier<Container> f : factories()) {
            Container v = f.get();
            Random rnd = new Random(SEED + 2);
            for (int entry = 0; entry < v.limbs; entry++) {
                for (int k = 0; k < 150; k++) {
                    int bits = v.precision - 64 * entry - rnd.nextInt(64);
                    if (bits <= 0) {
                        continue;
                    }
                    BigInteger mag = BigInteger.ONE.shiftLeft(bits - 1)
                            .or(new BigInteger(bits - 1, rnd));
                    v.setFrom(toLimbs(mag, v.limbs));
                    int e = v.lsbExponent();
                    if (stored(v).bitLength() + e < 54) {
                        // addSmall documents |value| >= 2^53 (far field); skip the near field.
                        continue;
                    }
                    long delta = rnd.nextInt(63) - 31;
                    BigInteger before = signed(v);
                    v.addSmall(delta);
                    // value = sign * M * 2^e; adding delta keeps the window exact,
                    // so M_new = M_old + sign*delta*2^-e
                    BigInteger expect = before.add(BigInteger.valueOf(delta).shiftLeft(-e));
                    assertEquals(expect, signed(v),
                            "addSmall entry=" + entry + " delta=" + delta);
                }
            }
        }
    }

    @Test
    void floorLow64FracMatchesTheExactSplit() {
        for (Supplier<Container> f : factories()) {
            Container v = f.get();
            Random rnd = new Random(SEED + 3);
            long[] out = new long[1];
            for (int i = 0; i < N; i++) {
                int bits = 1 + rnd.nextInt(v.precision);
                BigInteger mag = BigInteger.ONE.shiftLeft(bits - 1).or(new BigInteger(bits - 1, rnd));
                v.setFrom(toLimbs(mag, v.limbs));
                boolean neg = rnd.nextBoolean();
                if (neg) {
                    v.mulSmall(-1L);
                }
                double frac = v.floorLow64Frac(out);
                assertEquals(refFloor(v, neg), out[0], "low64");
                assertEquals(refFrac(v, neg), frac, 0.0, "frac");
            }
        }
    }

    @Test
    void compareAndScaleFollowTheNumericOrder() {
        for (Supplier<Container> f : factories()) {
            Container a = f.get();
            Container b = f.get();
            Random rnd = new Random(SEED + 4);
            for (int i = 0; i < N; i++) {
                int ba = 1 + rnd.nextInt(a.precision);
                int bb = 1 + rnd.nextInt(b.precision);
                a.setFrom(toLimbs(BigInteger.ONE.shiftLeft(ba - 1).or(new BigInteger(ba - 1, rnd)),
                        a.limbs));
                b.setFrom(toLimbs(BigInteger.ONE.shiftLeft(bb - 1).or(new BigInteger(bb - 1, rnd)),
                        b.limbs));
                if (rnd.nextBoolean()) {
                    a.mulSmall(-1L);
                }
                if (rnd.nextBoolean()) {
                    b.mulSmall(-1L);
                }
                BigInteger va = exact(a);
                BigInteger vb = exact(b);
                assertEquals(Integer.signum(va.compareTo(vb)), Integer.signum(a.compareTo(b)),
                        "compareTo");
                int k = rnd.nextInt(300);
                a.scalePow2(k);
                assertEquals(va.shiftLeft(k), exact(a), "scalePow2");
            }
        }
    }

    // ---------------------------------------------------------------- reference model

    /** Stored magnitude: the unsigned integer held in the limbs. */
    private static BigInteger stored(Container v) {
        BigInteger m = BigInteger.ZERO;
        for (int i = v.limbs - 1; i >= 0; i--) {
            long limb = v.limb(i);
            BigInteger w = BigInteger.valueOf(limb & Long.MAX_VALUE);
            if (limb < 0) {
                w = w.setBit(63);
            }
            m = m.shiftLeft(64).or(w);
        }
        return m;
    }

    /** {@code sign * M * 2^lsbExp}, exact only when the exponent is non-negative. */
    private static BigInteger exact(Container v) {
        BigInteger mag = stored(v).shiftLeft(v.lsbExponent());
        return v.signum() < 0 ? mag.negate() : mag;
    }

    /** {@code sign * M}, i.e. the value in units of {@code 2^lsbExp}. */
    private static BigInteger signed(Container v) {
        BigInteger mag = stored(v);
        return v.signum() < 0 ? mag.negate() : mag;
    }

    private static long refFloor(Container v, boolean neg) {
        int e = v.lsbExponent();
        BigInteger m = stored(v);
        BigInteger floor;
        if (e >= 0) {
            floor = m.shiftLeft(e);
        } else {
            int s = -e;
            floor = m.shiftRight(s);
            BigInteger rem = m.and(BigInteger.ONE.shiftLeft(s).subtract(BigInteger.ONE));
            if (neg && rem.signum() != 0) {
                floor = floor.add(BigInteger.ONE);
            }
        }
        return (neg ? floor.negate() : floor).longValue();
    }

    private static double refFrac(Container v, boolean neg) {
        int e = v.lsbExponent();
        if (e >= 0) {
            return 0.0;
        }
        int s = -e;
        BigInteger m = stored(v);
        BigInteger rem = m.and(BigInteger.ONE.shiftLeft(s).subtract(BigInteger.ONE));
        if (rem.signum() == 0) {
            return 0.0;
        }
        BigInteger fracMag = neg ? BigInteger.ONE.shiftLeft(s).subtract(rem) : rem;
        BigInteger f53 = s <= 53 ? fracMag.shiftLeft(53 - s) : fracMag.shiftRight(s - 53);
        return f53.longValue() * 0x1.0p-53;
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
}
