package com.scalarwide.fp;

import com.scalarwide.Limbs;

import java.math.BigInteger;

/**
 * Arithmetic for the flat wide formats, parameterised by {@link FpFormat}.
 *
 * <p>Same shape as the 256-bit {@code Arithmetic}: every operation builds an exact fixed-point
 * frame, then hands it to a single {@link #pack} that performs the only rounding. Values are
 * {@code (-1)^sign * mag * 2^(exp - (p-1))} with {@code mag} normalised (top bit at {@code p-1}).
 *
 * <p>Alignment uses a frame wide enough for both operands exactly ({@code 2L+1} limbs); when the
 * exponent gap exceeds {@code p+2} the smaller operand is entirely below the rounding point and
 * collapses into the sticky flag.
 */
final class WideOps {

    static final byte FINITE = 0;
    static final byte INFINITE = 1;
    static final byte NAN = 2;

    /** A finished value: kind, sign, leading-bit exponent, and exactly {@code limbs} mantissa limbs. */
    record Value(byte kind, int sign, int exp, long[] mag) {
    }

    private WideOps() {
    }

    // ---------------------------------------------------------------- construction

    static Value zero(int sign) {
        return new Value(FINITE, sign, 0, null);
    }

    static Value of(FpFormat f, long v) {
        if (v == 0L) {
            return zero(0);
        }
        int sign = v < 0 ? 1 : 0;
        long mag = v < 0 ? -v : v;
        int lead = 63 - Long.numberOfLeadingZeros(mag);
        return new Value(FINITE, sign, lead, place(f, mag, lead));
    }

    static Value of(FpFormat f, double v) {
        long bits = Double.doubleToRawLongBits(v);
        int sign = bits < 0 ? 1 : 0;
        int biased = (int) ((bits >>> 52) & 0x7FFL);
        long frac = bits & 0x000F_FFFF_FFFF_FFFFL;
        if (biased == 0x7FF) {
            return frac != 0L ? new Value(NAN, 0, 0, null) : new Value(INFINITE, sign, 0, null);
        }
        if (biased == 0) {
            if (frac == 0L) {
                return zero(sign);
            }
            int lead = 63 - Long.numberOfLeadingZeros(frac);
            return new Value(FINITE, sign, lead - 1074, place(f, frac, lead));
        }
        long sig = (1L << 52) | frac;
        return new Value(FINITE, sign, 52 + (biased - 1075), place(f, sig, 52));
    }

    private static long[] place(FpFormat f, long mag, int lead) {
        long[] m = new long[f.limbs()];
        int shift = f.topBit() - lead;
        int li = shift >>> 6;
        int bs = shift & 63;
        m[li] = mag << bs;
        if (bs != 0 && li + 1 < m.length) {
            m[li + 1] = mag >>> (64 - bs);
        }
        return m;
    }

    // ---------------------------------------------------------------- add / subtract

    static Value add(FpFormat f, Value a, Value b) {
        if (a.kind() == NAN || b.kind() == NAN) {
            return new Value(NAN, 0, 0, null);
        }
        if (a.kind() == INFINITE || b.kind() == INFINITE) {
            if (a.kind() == INFINITE && b.kind() == INFINITE && a.sign() != b.sign()) {
                return new Value(NAN, 0, 0, null);
            }
            return a.kind() == INFINITE ? a : b;
        }
        boolean az = isZeroMag(a);
        boolean bz = isZeroMag(b);
        if (az && bz) {
            return zero(a.sign() == b.sign() ? a.sign() : 0);
        }
        if (az) {
            return b;
        }
        if (bz) {
            return a;
        }
        int p = f.precision();
        int l = f.limbs();
        int gap = a.exp() - b.exp();
        Value big = gap >= 0 ? a : b;
        Value small = gap >= 0 ? b : a;
        gap = Math.abs(gap);
        boolean sameSign = a.sign() == b.sign();
        int frameExp;
        long[] m;
        boolean sticky = false;
        boolean down = false;
        if (gap > p + 2) {
            // The smaller operand sits entirely below big's rounding point.
            frameExp = big.exp();
            m = new long[l + 1];
            placeAt(m, 0, big.mag());
            sticky = true;
            down = !sameSign;
        } else {
            // Frame sits on the smaller exponent; big is shifted up and still fits.
            frameExp = small.exp();
            m = new long[2 * l + 1];
            placeAt(m, gap, big.mag());
            if (sameSign) {
                addShifted(m, 0, small.mag());
            } else if (gap == 0 && compareFrames(m, big.mag(), small.mag()) <= 0) {
                long[] neg = new long[m.length];
                placeAt(neg, 0, small.mag());
                subFrameAt(neg, 0, big.mag());
                m = neg;
                return pack(f, small.sign(), frameExp, m, false, false);
            } else {
                subFrameAt(m, 0, small.mag());
            }
        }
        return pack(f, big.sign(), frameExp, m, sticky, down);
    }
    // ---------------------------------------------------------------- multiply

    static Value multiply(FpFormat f, Value a, Value b) {
        if (a.kind() == NAN || b.kind() == NAN) {
            return new Value(NAN, 0, 0, null);
        }
        if (a.kind() == INFINITE || b.kind() == INFINITE) {
            if (isZeroMag(a) || isZeroMag(b)) {
                return new Value(NAN, 0, 0, null);
            }
            return new Value(INFINITE, a.sign() ^ b.sign(), 0, null);
        }
        if (isZeroMag(a) || isZeroMag(b)) {
            return zero(a.sign() ^ b.sign());
        }
        int l = f.limbs();
        long[] prod = new long[2 * l];
        mulInto(prod, a.mag(), b.mag(), l);
        int exp = a.exp() + b.exp() - (f.precision() - 1);
        return pack(f, a.sign() ^ b.sign(), exp, prod, false, false);
    }

    // ---------------------------------------------------------------- fma

    /** Finite {@code a*b + c}: exact product, exact alignment, one rounding. */
    static Value fma(FpFormat f, Value a, Value b, Value c) {
        int l = f.limbs();
        int p = f.precision();
        long[] prod = new long[2 * l];
        mulInto(prod, a.mag(), b.mag(), l);
        int prodExp = a.exp() + b.exp() - (p - 1);
        int cExp = c.exp();
        boolean prodSign = (a.sign() ^ b.sign()) != 0;
        boolean sameSign = prodSign == (c.sign() != 0);

        int gap = prodExp - cExp;
        boolean dropProd = false;
        boolean dropC = false;
        int base;
        // c is p bits, the product 2p bits, so the two drop windows are not symmetric.
        if (gap > p - 1) {
            base = prodExp;
            dropC = true;
        } else if (gap < -2 * (p - 1)) {
            base = cExp;
            dropProd = true;
        } else {
            base = Math.min(prodExp, cExp);
        }
        int frame = 4 * l;
        long[] m = new long[frame];
        int sign;
        boolean sticky;
        if (dropProd) {
            placeAt(m, 0, c.mag());
            sign = c.sign();
            sticky = true;
        } else if (dropC) {
            placeAt(m, 0, prod);
            sign = prodSign ? 1 : 0;
            sticky = true;
        } else {
            long[] pc = new long[frame];
            placeAt(pc, prodExp - base, prod);
            long[] cc = new long[frame];
            placeAt(cc, cExp - base, c.mag());
            if (sameSign) {
                addFrame(pc, cc);
                m = pc;
                sign = prodSign ? 1 : 0;
            } else {
                int cmp = compareFrames(pc, cc);
                if (cmp == 0) {
                    return zero(0);
                }
                if (cmp > 0) {
                    subFrame(pc, cc);
                    m = pc;
                    sign = prodSign ? 1 : 0;
                } else {
                    subFrame(cc, pc);
                    m = cc;
                    sign = c.sign();
                }
            }
            sticky = false;
        }
        boolean down = sticky && !sameSign;
        if (Boolean.getBoolean("sw.dbg")) {
            System.out.println("fma2 prodExp=" + prodExp + " cExp=" + cExp + " base=" + base
                    + " dropC=" + dropC + " dropProd=" + dropProd + " same=" + sameSign
                    + " cBits=" + (c.mag() == null ? -1 : bitLength(c.mag()))
                    + " mBits=" + bitLength(m) + " sign=" + sign + " down=" + down);
        }
        return pack(f, sign, base, m, sticky, down);
    }

    // ---------------------------------------------------------------- divide

    static Value divide(FpFormat f, Value a, Value b) {
        if (a.kind() == NAN || b.kind() == NAN) {
            return new Value(NAN, 0, 0, null);
        }
        if (a.kind() == INFINITE) {
            return b.kind() == INFINITE ? new Value(NAN, 0, 0, null)
                    : new Value(INFINITE, a.sign() ^ b.sign(), 0, null);
        }
        if (b.kind() == INFINITE) {
            return zero(a.sign() ^ b.sign());
        }
        if (isZeroMag(b)) {
            return isZeroMag(a) ? new Value(NAN, 0, 0, null)
                    : new Value(INFINITE, a.sign() ^ b.sign(), 0, null);
        }
        if (isZeroMag(a)) {
            return zero(a.sign() ^ b.sign());
        }
        int l = f.limbs();
        int k = f.precision() + 32;
        int frame = 2 * l;
        long[] num = new long[frame];
        long[] den = new long[frame];
        System.arraycopy(a.mag(), 0, num, 0, l);
        System.arraycopy(b.mag(), 0, den, 0, l);
        shlInPlace(num, k);
        long[] q = new long[frame];
        long[] r = new long[frame];
        Limbs.divmod(num, den, q, r);
        boolean sticky = false;
        for (int i = 0; i < frame; i++) {
            if (r[i] != 0L) {
                sticky = true;
                break;
            }
        }
        int exp = a.exp() - b.exp() - k + (f.precision() - 1);
        return pack(f, a.sign() ^ b.sign(), exp, q, sticky, false);
    }

    // ---------------------------------------------------------------- rounding

    /**
     * The single rounding point: normalises {@code mag * 2^(exp-(p-1))} to {@code p} bits,
     * nearest-even, with gradual underflow and overflow to infinity.
     */
    static Value pack(FpFormat f, int sign, int exp, long[] mag, boolean sticky, boolean down) {
        int p = f.precision();
        if (down && sticky) {
            // The borrow transform needs one guard bit below the result's LSB. When the value is
            // already exactly p bits wide there is none, and the dropped tail is by construction
            // far below half an ulp, so the value rounds back to mag.
            int bits0 = bitLength(mag);
            int sh = (bits0 - 1) - (p - 1);
            int tgt = exp + sh;
            if (tgt < f.expMin()) {
                sh += f.expMin() - tgt;
            }
            if (sh == 0) {
                sticky = false;
                down = false;
            } else {
                dec(mag);
            }
        }
        int bits = bitLength(mag);
        if (bits == 0) {
            return zero(sign);
        }
        int shift = (bits - 1) - (p - 1);
        int target = exp + shift;
        if (target < f.expMin()) {
            shift += f.expMin() - target;
        }
        int guard = 0;
        boolean low = sticky;
        if (shift <= 0) {
            if (shift < 0) {
                shlInPlace(mag, -shift);
            }
        } else {
            guard = (int) bitAt(mag, shift - 1);
            low = sticky || anyBelow(mag, shift - 1);
            shrInPlace(mag, shift);
        }
        if (guard != 0 && (low || (mag[0] & 1L) != 0L)) {
            inc(mag);
        }
        int e = exp + shift;
        int qb = bitLength(mag);
        if (qb > p) {
            shrInPlace(mag, 1);
            e++;
            qb = p;
        } else if (qb < p && qb > 0) {
            int grow = p - qb;
            shlInPlace(mag, grow);
            e -= grow;
        }
        if (e > f.expMax()) {
            return new Value(INFINITE, sign, 0, null);
        }
        if (e < f.expMin() - (p - 1)) {
            return zero(sign);
        }
        long[] out = new long[f.limbs()];
        System.arraycopy(mag, 0, out, 0, Math.min(mag.length, out.length));
        return new Value(FINITE, sign, e, out);
    }

    // ---------------------------------------------------------------- frame helpers

    /** Adds {@code src << shift} into the frame; the ranges may overlap. */
    static void addShifted(long[] frame, int shift, long[] src) {
        int li = shift >>> 6;
        int bs = shift & 63;
        long carry = 0L;
        for (int i = li; i < frame.length; i++) {
            int j = i - li;
            long v = j < src.length ? src[j] : 0L;
            long w;
            if (bs == 0) {
                w = v;
            } else {
                w = v << bs;
                if (j > 0 && j - 1 < src.length) {
                    w |= src[j - 1] >>> (64 - bs);
                }
            }
            long t = frame[i] + w;
            long c = Long.compareUnsigned(t, frame[i]) < 0 ? 1L : 0L;
            long s = t + carry;
            if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                c = 1L;
            }
            frame[i] = s;
            carry = c;
        }
    }
    static void placeAt(long[] frame, int shift, long[] src) {
        int li = shift >>> 6;
        int bs = shift & 63;
        for (int i = 0; i < src.length; i++) {
            int k = li + i;
            if (k >= frame.length) {
                return;
            }
            if (bs == 0) {
                frame[k] |= src[i];
            } else {
                frame[k] |= src[i] << bs;
                if (k + 1 < frame.length) {
                    frame[k + 1] |= src[i] >>> (64 - bs);
                }
            }
        }
    }

    static void mulInto(long[] dst, long[] a, long[] b, int l) {
        for (int i = 0; i < l; i++) {
            long ai = a[i];
            if (ai == 0L) {
                continue;
            }
            long carry = 0L;
            for (int j = 0; j < l; j++) {
                long lo = ai * b[j];
                long hi = Math.unsignedMultiplyHigh(ai, b[j]);
                long t = dst[i + j] + lo;
                long c1 = Long.compareUnsigned(t, dst[i + j]) < 0 ? 1L : 0L;
                long s = t + carry;
                long c2 = Long.compareUnsigned(s, t) < 0 ? 1L : 0L;
                dst[i + j] = s;
                carry = hi + c1 + c2;
            }
            int k = i + l;
            while (carry != 0L && k < dst.length) {
                long s = dst[k] + carry;
                carry = Long.compareUnsigned(s, dst[k]) < 0 ? 1L : 0L;
                dst[k] = s;
                k++;
            }
        }
    }

    static int compareFrames(long[] a, long[] b) {
        for (int i = a.length - 1; i >= 0; i--) {
            int c = Long.compareUnsigned(a[i], b[i]);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private static int compareFrames(long[] a, long[] b, long[] c) {
        long[] bc = new long[a.length];
        placeAt(bc, 0, b);
        long[] cc = new long[a.length];
        placeAt(cc, 0, c);
        return compareFrames(bc, cc);
    }

    private static void addFrame(long[] a, long[] b) {
        long carry = 0L;
        for (int i = 0; i < a.length; i++) {
            long t = a[i] + b[i];
            long c = Long.compareUnsigned(t, a[i]) < 0 ? 1L : 0L;
            long s = t + carry;
            if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                c = 1L;
            }
            a[i] = s;
            carry = c;
        }
    }
    private static void subFrame(long[] a, long[] b) {
        long borrow = 0L;
        for (int i = 0; i < a.length; i++) {
            long d = a[i] - b[i];
            long br = Long.compareUnsigned(a[i], b[i]) < 0 ? 1L : 0L;
            long r = d - borrow;
            if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                br = 1L;
            }
            a[i] = r;
            borrow = br;
        }
    }

    private static void subFrameAt(long[] frame, int shift, long[] sub) {
        int li = shift >>> 6;
        int bs = shift & 63;
        long borrow = 0L;
        for (int i = li; i < frame.length; i++) {
            int j = i - li;
            long v = j < sub.length ? sub[j] : 0L;
            long w;
            if (bs == 0) {
                w = v;
            } else {
                w = v << bs;
                if (j > 0 && j - 1 < sub.length) {
                    w |= sub[j - 1] >>> (64 - bs);
                }
            }
            long d = frame[i] - w;
            long br = Long.compareUnsigned(frame[i], w) < 0 ? 1L : 0L;
            long r = d - borrow;
            if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                br = 1L;
            }
            frame[i] = r;
            borrow = br;
        }
    }

    // ---------------------------------------------------------------- helpers

    static boolean isZeroMag(Value v) {
        return v.kind() == FINITE && (v.mag() == null || bitLength(v.mag()) == 0);
    }

    static int bitLength(long[] a) {
        for (int i = a.length - 1; i >= 0; i--) {
            if (a[i] != 0L) {
                return i * 64 + 64 - Long.numberOfLeadingZeros(a[i]);
            }
        }
        return 0;
    }

    static long bitAt(long[] a, int i) {
        if (i < 0) {
            return 0L;
        }
        int li = i >>> 6;
        if (li >= a.length) {
            return 0L;
        }
        return (a[li] >>> (i & 63)) & 1L;
    }

    private static boolean anyBelow(long[] a, int i) {
        if (i <= 0) {
            return false;
        }
        int li = i >>> 6;
        for (int j = 0; j < li && j < a.length; j++) {
            if (a[j] != 0L) {
                return true;
            }
        }
        if (li >= a.length) {
            return false;
        }
        int bi = i & 63;
        long mask = bi == 0 ? 0L : (1L << bi) - 1L;
        return (a[li] & mask) != 0L;
    }

    static void shlInPlace(long[] a, int k) {
        int terms = k >>> 6;
        int bits = k & 63;
        for (int i = a.length - 1; i >= 0; i--) {
            int src = i - terms;
            long v = 0L;
            if (src >= 0) {
                v = a[src] << bits;
                if (bits != 0 && src - 1 >= 0) {
                    v |= a[src - 1] >>> (64 - bits);
                }
            }
            a[i] = v;
        }
    }

    static void shrInPlace(long[] a, int k) {
        int terms = k >>> 6;
        int bits = k & 63;
        for (int i = 0; i < a.length; i++) {
            int src = i + terms;
            long v = src < a.length ? a[src] >>> bits : 0L;
            if (bits != 0 && src + 1 < a.length) {
                v |= a[src + 1] << (64 - bits);
            }
            a[i] = v;
        }
    }

    private static void inc(long[] a) {
        for (int i = 0; i < a.length; i++) {
            long old = a[i];
            a[i] = old + 1L;
            if (old != -1L) {
                return;
            }
        }
    }

    private static void dec(long[] a) {
        for (int i = 0; i < a.length; i++) {
            long old = a[i];
            a[i] = old - 1L;
            if (old != 0L) {
                return;
            }
        }
    }

    // ---------------------------------------------------------------- compare / convert

    static int compareMagnitude(Value a, Value b) {
        boolean az = isZeroMag(a);
        boolean bz = isZeroMag(b);
        if (az || bz) {
            return az && bz ? 0 : (az ? -1 : 1);
        }
        if (a.exp() != b.exp()) {
            return Integer.compare(a.exp(), b.exp());
        }
        int l = a.mag().length;
        for (int i = l - 1; i >= 0; i--) {
            int c = Long.compareUnsigned(a.mag()[i], b.mag()[i]);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    /** Correctly rounded conversion to binary64. */
    static double toDouble(FpFormat f, Value v) {
        if (v.kind() == NAN) {
            return Double.NaN;
        }
        if (v.kind() == INFINITE) {
            return v.sign() == 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }
        if (isZeroMag(v)) {
            return v.sign() == 0 ? 0.0 : -0.0;
        }
        int p = f.precision();
        int sub = v.exp() < -1022 ? (-1022 - v.exp()) : 0;
        int k = (p - 1 - 52) + sub;
        long q = shiftLow(v.mag(), k);
        long guard = bitAt(v.mag(), k - 1);
        boolean sticky = anyBelow(v.mag(), k - 1);
        if (guard != 0 && (sticky || (q & 1L) != 0L)) {
            q++;
        }
        int e = v.exp();
        if (e < -1022) {
            return Double.longBitsToDouble(((long) v.sign() << 63) | q);
        }
        if (q >= (1L << 53)) {
            q >>>= 1;
            e++;
            if (e > 1023) {
                return v.sign() == 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            }
        }
        long bits = ((long) v.sign() << 63) | ((long) (e + 1023) << 52) | (q & ((1L << 52) - 1));
        return Double.longBitsToDouble(bits);
    }

    private static long shiftLow(long[] mag, int k) {
        if (k < 0) {
            return mag[0];
        }
        int li = k >>> 6;
        if (li >= mag.length) {
            return 0L;
        }
        int bi = k & 63;
        long lo = mag[li] >>> bi;
        if (bi != 0 && li + 1 < mag.length) {
            lo |= mag[li + 1] << (64 - bi);
        }
        return lo;
    }

    /** Converts an exact decimal literal into the format, nearest-even. */
    static Value fromDecimal(FpFormat f, Decimal.Scanned sc) {
        BigInteger num = sc.unscaled();
        if (num.signum() == 0) {
            return zero(sc.negative() ? 1 : 0);
        }
        BigInteger den = BigInteger.ONE;
        if (sc.exp10() >= 0) {
            num = num.multiply(BigInteger.TEN.pow(sc.exp10()));
        } else {
            den = den.multiply(BigInteger.TEN.pow(-sc.exp10()));
        }
        int lead = num.bitLength() - den.bitLength();
        if (lead >= 0) {
            if (num.compareTo(den.shiftLeft(lead)) < 0) {
                lead--;
            }
        } else if (num.shiftLeft(-lead).compareTo(den) < 0) {
            lead--;
        }
        int p = f.precision();
        int shift = (p - 1) - lead;
        if (shift >= 0) {
            num = num.shiftLeft(shift);
        } else {
            den = den.shiftLeft(-shift);
        }
        BigInteger[] qr = num.divideAndRemainder(den);
        BigInteger q = qr[0];
        int cmp = qr[1].shiftLeft(1).compareTo(den);
        if (cmp > 0 || (cmp == 0 && q.testBit(0))) {
            q = q.add(BigInteger.ONE);
        }
        int e = lead;
        if (q.bitLength() > p) {
            q = q.shiftRight(1);
            e++;
        }
        if (q.bitLength() < p) {
            int grow = p - q.bitLength();
            q = q.shiftLeft(grow);
            e -= grow;
        }
        if (e > f.expMax()) {
            return new Value(INFINITE, sc.negative() ? 1 : 0, 0, null);
        }
        if (e < f.expMin() - (p - 1)) {
            return zero(sc.negative() ? 1 : 0);
        }
        long[] m = new long[f.limbs()];
        BigInteger mask = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
        for (int k = 0; k < m.length; k++) {
            m[k] = q.and(mask).longValue();
            q = q.shiftRight(64);
        }
        return new Value(FINITE, sc.negative() ? 1 : 0, e, m);
    }
}
