package com.scalarwide.main.fp;

import java.math.BigInteger;

/**
 * Exact reference for the add/subtract differential gate.
 *
 * <p>Operands are aligned on a common binary grid and summed as exact {@link BigInteger}s, then
 * rounded once with nearest-even. This deliberately avoids two routes that turned out to be lossy
 * for extreme exponent gaps: {@code BigFloat}'s alignment (drops a ulp) and a decimal-rational
 * reduction (its leading-digit estimate can round one bit too coarsely).
 */
final class WideRef {

    private static final BigInteger ONE = BigInteger.ONE;

    private WideRef() {
    }

    record Value(int sign, int exp, long[] mag) {
    }

    static Value addOrSub(FpFormat f, long[] am, int aExp, boolean aNeg, long[] bm, int bExp, boolean bNeg, boolean add) {
        int p = f.precision();
        int minLsb = Math.min(aExp, bExp) - (p - 1);
        BigInteger a = toBig(am).shiftLeft(aExp - (p - 1) - minLsb);
        if (aNeg) {
            a = a.negate();
        }
        BigInteger b = toBig(bm).shiftLeft(bExp - (p - 1) - minLsb);
        if (bNeg) {
            b = b.negate();
        }
        BigInteger d = add ? a.add(b) : a.subtract(b);
        if (d.signum() == 0) {
            return new Value(0, 0, new long[f.limbs()]);
        }
        int sign = d.signum() < 0 ? 1 : 0;
        BigInteger mag = d.abs();
        int bits = mag.bitLength();
        int drop = bits - p;
        BigInteger q;
        if (drop <= 0) {
            q = mag.shiftLeft(-drop);
            drop = 0;
        } else {
            q = mag.shiftRight(drop);
            BigInteger rem = mag.and(ONE.shiftLeft(drop).subtract(ONE));
            BigInteger half = ONE.shiftLeft(drop - 1);
            int cmp = rem.compareTo(half);
            if (cmp > 0 || (cmp == 0 && q.testBit(0))) {
                q = q.add(ONE);
            }
            if (q.bitLength() > p) {
                q = q.shiftRight(1);
                drop++;
            }
        }
        int exp = minLsb + drop + (p - 1);
        long[] m = new long[f.limbs()];
        BigInteger work = q;
        for (int i = 0; i < m.length; i++) {
            m[i] = work.longValue();
            work = work.shiftRight(64);
        }
        return new Value(sign, exp, m);
    }

    private static BigInteger toBig(long[] limbs) {
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
