package com.scalarwide.fp;

import com.scalarwide.Limbs;

import java.math.BigInteger;

/**
 * Array-based binary float engine: the format is a runtime parameter, the arithmetic is built
 * from atomic limb primitives ({@link Limbs}), and no per-limb object is ever created.
 *
 * <p>This is the scalable counterpart to {@link Binary256}: the flat-field class is fastest at a
 * fixed 237-bit precision, while this one can be instantiated at any precision (e.g. 616 bits to
 * match Long2's {@code Binary640}).
 *
 * <p>Value model: {@code (-1)^sign * mag * 2^(exp - (p-1))}, {@code mag} normalised (bit {@code p-1}
 * set, nothing above it). Zero uses {@code exp = expMin - (p-1)} and an all-zero magnitude.
 */
public final class BigFloat {

    private static final byte FINITE = 0;
    private static final byte INFINITE = 1;
    private static final byte NAN = 2;

    private final FpFormat fmt;
    private final byte kind;
    private final byte sign;
    private final int exp;
    private final long[] mag;

    private BigFloat(FpFormat fmt, byte kind, int sign, int exp, long[] mag) {
        this.fmt = fmt;
        this.kind = kind;
        this.sign = (byte) sign;
        this.exp = exp;
        this.mag = mag;
    }

    // ---------------------------------------------------------------- constants

    public static BigFloat zero(FpFormat f) {
        return new BigFloat(f, FINITE, 0, lowestExp(f), new long[f.limbs()]);
    }

    public static BigFloat negativeZero(FpFormat f) {
        return new BigFloat(f, FINITE, 1, lowestExp(f), new long[f.limbs()]);
    }

    public static BigFloat one(FpFormat f) {
        long[] m = new long[f.limbs()];
        m[f.limbs() - 1] = 1L << (f.topBit() - 64 * (f.limbs() - 1));
        return new BigFloat(f, FINITE, 0, 0, m);
    }

    public static BigFloat infinity(FpFormat f, boolean negative) {
        return new BigFloat(f, INFINITE, negative ? 1 : 0, 0, new long[f.limbs()]);
    }

    public static BigFloat nan(FpFormat f) {
        return new BigFloat(f, NAN, 0, 0, new long[f.limbs()]);
    }

    public static BigFloat maxValue(FpFormat f) {
        long[] m = new long[f.limbs()];
        for (int i = 0; i < f.limbs(); i++) {
            m[i] = -1L;
        }
        m[f.limbs() - 1] &= f.topLimbMask();
        return new BigFloat(f, FINITE, 0, f.expMax(), m);
    }

    private static int lowestExp(FpFormat f) {
        return f.expMin() - f.topBit();
    }

    // ---------------------------------------------------------------- construction

    /** Exact conversion; every {@code long} fits any precision above 64. */
    public static BigFloat of(FpFormat f, long v) {
        if (v == 0L) {
            return zero(f);
        }
        int s = v < 0 ? 1 : 0;
        long m = v < 0 ? -v : v;
        int lead = 63 - Long.numberOfLeadingZeros(m);
        long[] mag = new long[f.limbs()];
        place(mag, m, f.topBit() - lead);
        return new BigFloat(f, FINITE, s, lead, mag);
    }

    /** Exact conversion of a finite double. */
    public static BigFloat of(FpFormat f, double v) {
        long bits = Double.doubleToRawLongBits(v);
        int s = bits < 0 ? 1 : 0;
        int biased = (int) ((bits >>> 52) & 0x7FFL);
        long frac = bits & 0x000F_FFFF_FFFF_FFFFL;
        if (biased == 0x7FF) {
            return frac != 0L ? nan(f) : infinity(f, s != 0);
        }
        if (biased == 0) {
            if (frac == 0L) {
                return s == 0 ? zero(f) : negativeZero(f);
            }
            int lead = 63 - Long.numberOfLeadingZeros(frac);
            long[] mag = new long[f.limbs()];
            place(mag, frac, f.topBit() - lead);
            return new BigFloat(f, FINITE, s, lead - 1074, mag);
        }
        long sig = (1L << 52) | frac;
        long[] mag = new long[f.limbs()];
        place(mag, sig, f.topBit() - 52);
        return new BigFloat(f, FINITE, s, 52 + (biased - 1075), mag);
    }

    /** Writes {@code value} shifted left by {@code shift} bits into {@code dst}. */
    private static void place(long[] dst, long value, int shift) {
        int li = shift >> 6;
        int bi = shift & 63;
        if (li < dst.length) {
            dst[li] |= value << bi;
            if (bi != 0 && li + 1 < dst.length) {
                dst[li + 1] |= value >>> (64 - bi);
            }
        }
    }

    /** Test-only raw construction; the caller must supply a normalised magnitude. */
    static BigFloat raw(FpFormat f, int sign, int exp, long[] mag) {
        return new BigFloat(f, FINITE, sign, exp, mag);
    }
    // ---------------------------------------------------------------- predicates

    public boolean isNaN() {
        return kind == NAN;
    }

    public boolean isInfinite() {
        return kind == INFINITE;
    }

    public boolean isFinite() {
        return kind == FINITE;
    }

    public boolean isZero() {
        return kind == FINITE && isZeroMag(mag);
    }

    public boolean isNormal() {
        return kind == FINITE && !isZeroMag(mag) && exp >= fmt.expMin();
    }

    public boolean isSubnormal() {
        return kind == FINITE && !isZeroMag(mag) && exp < fmt.expMin();
    }

    public boolean signBit() {
        return sign != 0;
    }

    public int scaleExponent() {
        return exp;
    }

    public FpFormat format() {
        return fmt;
    }

    static boolean isZeroMag(long[] m) {
        long acc = 0L;
        for (long w : m) {
            acc |= w;
        }
        return acc == 0L;
    }

    // ---------------------------------------------------------------- arithmetic

    public BigFloat negate() {
        return kind == NAN ? this : new BigFloat(fmt, kind, sign ^ 1, exp, mag);
    }

    public BigFloat abs() {
        return sign == 0 ? this : negate();
    }

    public BigFloat add(BigFloat o) {
        if (isNaN() || o.isNaN()) {
            return nan(fmt);
        }
        if (isInfinite() || o.isInfinite()) {
            if (isInfinite() && o.isInfinite() && sign != o.sign) {
                return nan(fmt);
            }
            return isInfinite() ? this : o;
        }
        if (isZero() && o.isZero()) {
            return sign == o.sign ? this : zero(fmt);
        }
        boolean sameSign = sign == o.sign;
        BigFloat big = exp >= o.exp ? this : o;
        BigFloat small = big == this ? o : this;
        int diff = big.exp - small.exp;
        int p = fmt.precision();
        int width = 2 * fmt.limbs() + 1;

        if (diff <= p) {
            // Exact: both fit in a 2p-bit frame positioned at the smaller exponent.
            // Widen first, then shift in place: Limbs shifts within the array length, so a short
            // source would silently drop the bits that should land above it.
            long[] x = new long[width];
            long[] y = new long[width];
            System.arraycopy(big.mag, 0, x, 0, fmt.limbs());
            Limbs.shl(x, diff, x);
            System.arraycopy(small.mag, 0, y, 0, fmt.limbs());
            boolean bigSign = big.sign != 0;
            if (sameSign) {
                long[] sum = new long[width];
                Limbs.add(x, y, sum);
                return pack(fmt, bigSign ? 1 : 0, small.exp, sum, false, false);
            }
            int cmp = Limbs.compareUnsigned(x, y);
            if (cmp == 0) {
                return zero(fmt);
            }
            long[] d = new long[width];
            if (cmp > 0) {
                Limbs.sub(x, y, d);
                return pack(fmt, bigSign ? 1 : 0, small.exp, d, false, false);
            }
            Limbs.sub(y, x, d);
            return pack(fmt, small.sign != 0 ? 1 : 0, small.exp, d, false, false);
        }
        // The smaller operand sits entirely below one ulp of the larger: only its existence
        // matters, and it cannot cancel the larger one.
        long[] widened = new long[width];
        System.arraycopy(big.mag, 0, widened, 0, fmt.limbs());
        return pack(fmt, big.sign != 0 ? 1 : 0, big.exp, widened, true, !sameSign);
    }

    public BigFloat subtract(BigFloat o) {
        return add(o.negate());
    }
    public BigFloat multiply(BigFloat o) {
        if (isNaN() || o.isNaN()) {
            return nan(fmt);
        }
        if (isInfinite() || o.isInfinite()) {
            if (isZero() || o.isZero()) {
                return nan(fmt);
            }
            return infinity(fmt, (sign ^ o.sign) != 0);
        }
        if (isZero() || o.isZero()) {
            return (sign ^ o.sign) == 0 ? zero(fmt) : negativeZero(fmt);
        }
        long[] prod = Limbs.mul(mag, o.mag);
        // prod has 2*limbs limbs; keep the low 2*limbs+1 for the frame
        long[] frame = new long[2 * fmt.limbs() + 1];
        System.arraycopy(prod, 0, frame, 0, prod.length);
        return pack(fmt, (sign ^ o.sign) != 0 ? 1 : 0, exp + o.exp - fmt.topBit(), frame, false, false);
    }

    public BigFloat divide(BigFloat o) {
        if (isNaN() || o.isNaN()) {
            return nan(fmt);
        }
        if (isInfinite()) {
            return o.isInfinite() ? nan(fmt) : infinity(fmt, (sign ^ o.sign) != 0);
        }
        if (o.isInfinite()) {
            return (sign ^ o.sign) == 0 ? zero(fmt) : negativeZero(fmt);
        }
        if (o.isZero()) {
            return isZero() ? nan(fmt) : infinity(fmt, (sign ^ o.sign) != 0);
        }
        if (isZero()) {
            return (sign ^ o.sign) == 0 ? zero(fmt) : negativeZero(fmt);
        }
        int p = fmt.precision();
        int t = fmt.topBit();
        int ba = magBitLength(mag);
        int bb = magBitLength(o.mag);
        int k = p + 128 - (ba - bb);
        if (k < 0) {
            k = 0;
        }
        long[] num = new long[fmt.limbs() + (k >> 6) + 2];
        System.arraycopy(mag, 0, num, 0, fmt.limbs());
        Limbs.shl(num, k, num);
        long[] q = new long[num.length];
        long[] r = new long[num.length];
        // Limbs treats every array as width-delimited, so the divisor must be padded to match.
        long[] den = new long[num.length];
        System.arraycopy(o.mag, 0, den, 0, o.mag.length);
        Limbs.divmod(num, den, q, r);
        boolean sticky = !isZeroMag(r);
        return pack(fmt, (sign ^ o.sign) != 0 ? 1 : 0, exp - o.exp - k + t, q, sticky, false);
    }

    private static int magBitLength(long[] m) {
        return Limbs.bitLength(m);
    }
    // ---------------------------------------------------------------- rounding

    /**
     * Rounds {@code sign * (mag - down*delta) * 2^(exp - (p-1))} to the format, nearest-even.
     *
     * <p>{@code mag} is a scratch frame at least {@code limbs} long; anything above the leading bit
     * is handled by the normalisation shift.
     */
    static BigFloat pack(FpFormat f, int sign, int exp, long[] mag, boolean sticky, boolean down) {
        int p = f.precision();
        int limbs = f.limbs();
        long[] work = mag.clone();
        if (down && sticky) {
            long[] one = new long[work.length];
            one[0] = 1L;
            Limbs.sub(work, one, work);
        }
        int bits = Limbs.bitLength(work);
        if (bits == 0) {
            return sign == 0 ? zero(f) : negativeZero(f);
        }
        int lead = bits - 1;
        int shift = lead - f.topBit();
        int target = exp + shift;
        if (target < f.expMin()) {
            shift += f.expMin() - target;
        }

        long[] q = new long[work.length];
        boolean low;
        int guard = 0;
        if (shift <= 0) {
            Limbs.shl(work, -shift, q);
            low = sticky;
        } else {
            Limbs.ushr(work, shift, q);
            guard = (int) (bitAt(work, shift - 1) & 1L);
            // Strictly below the guard bit (exclusive): an exact tie must be able to round down.
            low = sticky || anyBelow(work, shift - 2);
        }
        if (guard != 0 && (low || (q[0] & 1L) != 0L)) {
            long[] one = new long[q.length];
            one[0] = 1L;
            Limbs.add(q, one, q);
        }
        int e = exp + shift;
        int qb = Limbs.bitLength(q);
        if (qb > p) {
            Limbs.ushr(q, 1, q);
            e++;
            qb = p;
        }
        if (qb < p) {
            int grow = p - qb;
            Limbs.shl(q, grow, q);
            e -= grow;
        }
        if (e > f.expMax()) {
            return sign == 0 ? infinity(f, false) : infinity(f, true);
        }
        if (e < f.expMin() - f.topBit()) {
            return sign == 0 ? zero(f) : negativeZero(f);
        }
        long[] out = new long[limbs];
        System.arraycopy(q, 0, out, 0, limbs);
        out[limbs - 1] &= f.topLimbMask();
        return new BigFloat(f, FINITE, sign, e, out);
    }

    private static long bitAt(long[] m, int i) {
        if (i < 0 || i >= m.length * 64) {
            return 0L;
        }
        return (m[i >> 6] >>> (i & 63)) & 1L;
    }

    private static boolean anyBelow(long[] m, int i) {
        if (i < 0) {
            return false;
        }
        int li = i >> 6;
        int bi = i & 63;
        long acc = 0L;
        for (int j = 0; j < li && j < m.length; j++) {
            acc |= m[j];
        }
        if (li < m.length) {
            long mask = bi == 63 ? -1L : (1L << (bi + 1)) - 1L;
            acc |= m[li] & mask;
        }
        return acc != 0L;
    }

    // ---------------------------------------------------------------- comparison

    public int compareTo(BigFloat o) {
        boolean an = isNaN();
        boolean bn = o.isNaN();
        if (an || bn) {
            return an && bn ? 0 : (an ? 1 : -1);
        }
        boolean az = isZero();
        boolean bz = o.isZero();
        if (az && bz) {
            return Integer.compare(o.sign, sign);
        }
        if (az) {
            return o.sign == 0 ? -1 : 1;
        }
        if (bz) {
            return sign == 0 ? 1 : -1;
        }
        if (sign != o.sign) {
            return sign == 0 ? 1 : -1;
        }
        int ae = isInfinite() ? Integer.MAX_VALUE : exp;
        int be = o.isInfinite() ? Integer.MAX_VALUE : o.exp;
        int magCmp = ae != be ? Integer.compare(ae, be) : Limbs.compareUnsigned(mag, o.mag);
        return sign == 0 ? magCmp : -magCmp;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BigFloat o) || fmt != o.fmt || kind != o.kind) {
            return false;
        }
        if (kind == NAN) {
            return true;
        }
        if (kind == INFINITE) {
            return sign == o.sign;
        }
        if (isZero() && o.isZero()) {
            return sign == o.sign;
        }
        return sign == o.sign && exp == o.exp && java.util.Arrays.equals(mag, o.mag);
    }

    @Override
    public int hashCode() {
        if (kind == NAN) {
            return 0x7FF8_0000;
        }
        int h = kind * 31 + sign;
        h = h * 31 + (isZero() ? 0 : exp);
        for (long w : mag) {
            h = h * 31 + Long.hashCode(w);
        }
        return h;
    }

    // ---------------------------------------------------------------- diagnostics

    /** Exact magnitude, for tests and reference comparisons. */
    BigInteger magnitude() {
        return Limbs.toBigInteger(mag);
    }

    /** Binary exponent of the magnitude's LSB, i.e. value = magnitude * 2^lsb. */
    int lsbExponent() {
        return exp - fmt.topBit();
    }

    @Override
    public String toString() {
        if (kind == NAN) {
            return "NaN";
        }
        if (kind == INFINITE) {
            return sign == 0 ? "Infinity" : "-Infinity";
        }
        if (isZero()) {
            return sign == 0 ? "0" : "-0";
        }
        return (sign == 0 ? "" : "-") + magnitude().toString()
                + " * 2^" + lsbExponent() + " " + fmt;
    }
}
