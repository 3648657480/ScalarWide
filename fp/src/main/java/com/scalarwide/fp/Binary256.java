package com.scalarwide.fp;

/**
 * 256-bit binary floating point, {@code p = 256}, exponent range {@code [-262142, 262143]}.
 *
 * <p>Value model: {@code (-1)^sign * m * 2^(exp - 255)} with {@code m} a 256-bit integer.
 * Internally every finite non-zero value is <em>normalised</em> (bit 255 of {@code m} set) and
 * the exponent is unbounded; subnormalisation happens only when a result is packed, so the
 * arithmetic never needs a subnormal branch.
 *
 * <p>Semantics follow IEEE 754 for values (correct rounding, signed zero, infinities, NaN,
 * gradual underflow); the in-memory representation is deliberately <em>not</em> packed.
 * See {@code fp/docs/SEMANTICS.md}.
 */
public final class Binary256 implements Comparable<Binary256> {

    /** Significant bits (including the implicit leading one). */
    public static final int PRECISION = 256;
    /** Smallest exponent of a normal value. */
    public static final int EXP_MIN = -262142;
    /** Largest exponent of a normal value. */
    public static final int EXP_MAX = 262143;

    private static final int LIMBS = 4;
    private static final int BIAS = PRECISION - 1;
    /** Exponent of the smallest representable magnitude: {@code 1 * 2^(EXP_MIN - BIAS - 255 + 255)}. */
    private static final int MIN_MAG_EXP = EXP_MIN - BIAS;

    private static final byte FINITE = 0;
    private static final byte INFINITE = 1;
    private static final byte NAN = 2;

    /** IEEE comparison result: NaN makes any comparison unordered. */
    public enum Comparison {
        LESS, EQUAL, GREATER, UNORDERED
    }

    private final byte kind;
    private final byte sign;
    private final int exp;
    private final long l0;
    private final long l1;
    private final long l2;
    private final long l3;

    private Binary256(byte kind, int sign, int exp, long l0, long l1, long l2, long l3) {
        this.kind = kind;
        this.sign = (byte) sign;
        this.exp = exp;
        this.l0 = l0;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
    }

    /** Package-private raw construction for tests; caller is responsible for the invariants. */
    static Binary256 raw(int sign, int exp, long l0, long l1, long l2, long l3) {
        return finite(sign, exp, l0, l1, l2, l3);
    }

    private static Binary256 finite(int sign, int exp, long l0, long l1, long l2, long l3) {
        return new Binary256(FINITE, sign, exp, l0, l1, l2, l3);
    }

    // ------------------------------------------------------------------ constants

    public static final Binary256 ZERO = finite(0, MIN_MAG_EXP, 0L, 0L, 0L, 0L);
    public static final Binary256 NEGATIVE_ZERO = finite(1, MIN_MAG_EXP, 0L, 0L, 0L, 0L);
    public static final Binary256 ONE = finite(0, 0, 0L, 0L, 0L, Long.MIN_VALUE);
    public static final Binary256 NEGATIVE_ONE = finite(1, 0, 0L, 0L, 0L, Long.MIN_VALUE);
    public static final Binary256 POSITIVE_INFINITY = new Binary256(INFINITE, 0, 0, 0L, 0L, 0L, 0L);
    public static final Binary256 NEGATIVE_INFINITY = new Binary256(INFINITE, 1, 0, 0L, 0L, 0L, 0L);
    public static final Binary256 NaN = new Binary256(NAN, 0, 0, 0L, 0L, 0L, 0L);

    /** {@code (2^256 - 1) * 2^(EXP_MAX - 255)}. */
    public static final Binary256 MAX_VALUE =
            finite(0, EXP_MAX, -1L, -1L, -1L, -1L);
    /** {@code 1 * 2^EXP_MIN}. */
    public static final Binary256 MIN_NORMAL = finite(0, EXP_MIN, 0L, 0L, 0L, Long.MIN_VALUE);
    /** Smallest positive value, the least subnormal: {@code 1 * 2^(EXP_MIN - 255)}. */
    public static final Binary256 MIN_VALUE = finite(0, MIN_MAG_EXP, 0L, 0L, 0L, Long.MIN_VALUE);

    // ------------------------------------------------------------------ construction

    /** Exact conversion; every {@code long} is representable. */
    public static Binary256 of(long v) {
        if (v == 0L) {
            return ZERO;
        }
        int s = v < 0 ? 1 : 0;
        long mag = v < 0 ? -v : v; // Long.MIN_VALUE maps to itself, correct as an unsigned magnitude
        int lead = 63 - Long.numberOfLeadingZeros(mag);
        long[] m = place(mag, BIAS - lead);
        return finite(s, lead, m[0], m[1], m[2], m[3]);
    }

    /** Exact conversion; every finite {@code double} is representable. */
    public static Binary256 of(double v) {
        long bits = Double.doubleToRawLongBits(v);
        int s = bits < 0 ? 1 : 0;
        int biased = (int) ((bits >>> 52) & 0x7FFL);
        long frac = bits & 0x000F_FFFF_FFFF_FFFFL;
        if (biased == 0x7FF) {
            if (frac != 0L) {
                return NaN;
            }
            return s == 0 ? POSITIVE_INFINITY : NEGATIVE_INFINITY;
        }
        if (biased == 0) {
            if (frac == 0L) {
                return s == 0 ? ZERO : NEGATIVE_ZERO;
            }
            int lead = 63 - Long.numberOfLeadingZeros(frac);
            long[] m = place(frac, BIAS - lead);
            return finite(s, lead - 1074, m[0], m[1], m[2], m[3]);
        }
        long sig = (1L << 52) | frac;
        long[] m = place(sig, BIAS - 52);
        return finite(s, 52 + (biased - 1075), m[0], m[1], m[2], m[3]);
    }

    /** Places a {@code bits}-wide magnitude so that its leading bit lands at bit 255. */
    private static long[] place(long mag, int shift) {
        long[] m = new long[LIMBS];
        int li = shift >> 6;
        int bi = shift & 63;
        if (li < LIMBS) {
            m[li] |= mag << bi;
            if (bi != 0 && li + 1 < LIMBS) {
                m[li + 1] |= mag >>> (64 - bi);
            }
        }
        return m;
    }

    // ------------------------------------------------------------------ predicates

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
        return kind == FINITE && (l0 | l1 | l2 | l3) == 0L;
    }

    /** Finite, non-zero, and inside the normal exponent range. */
    public boolean isNormal() {
        return kind == FINITE && (l0 | l1 | l2 | l3) != 0L && exp >= EXP_MIN;
    }

    /** Finite, non-zero, and below the normal range (gradual underflow). */
    public boolean isSubnormal() {
        return kind == FINITE && (l0 | l1 | l2 | l3) != 0L && exp < EXP_MIN;
    }

    public boolean signBit() {
        return sign != 0;
    }

    /** Exponent of the leading bit; meaning is only defined for finite non-zero values. */
    public int scaleExponent() {
        return exp;
    }

    // ------------------------------------------------------------------ unary

    /** Sign flip; NaN is unchanged (its sign is not part of the value). */
    public Binary256 negate() {
        if (kind == NAN) {
            return this;
        }
        return new Binary256(kind, sign ^ 1, exp, l0, l1, l2, l3);
    }

    /** Magnitude; {@code abs(-0) == +0}. */
    public Binary256 abs() {
        return sign == 0 ? this : negate();
    }

    // ------------------------------------------------------------------ comparison

    /**
     * Total order, matching {@link Double#compareTo}: {@code -0 < +0}, NaN is greater than
     * everything and all NaNs are equal to each other.
     */
    @Override
    public int compareTo(Binary256 o) {
        boolean an = isNaN();
        boolean bn = o.isNaN();
        if (an || bn) {
            return an && bn ? 0 : (an ? 1 : -1);
        }
        boolean az = isZero();
        boolean bz = o.isZero();
        if (az && bz) {
            // -0 sorts below +0
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
        int mag = compareMagnitude(o);
        return sign == 0 ? mag : -mag;
    }

    /** Numeric comparison; any NaN makes the result unordered, as in IEEE 754. */
    public Comparison compareNumeric(Binary256 o) {
        if (isNaN() || o.isNaN()) {
            return Comparison.UNORDERED;
        }
        if (isZero() && o.isZero()) {
            // Numerically +0 == -0, unlike the total order.
            return Comparison.EQUAL;
        }
        int c = compareTo(o);
        return c < 0 ? Comparison.LESS : (c > 0 ? Comparison.GREATER : Comparison.EQUAL);
    }

    private int compareMagnitude(Binary256 o) {
        int ae = isInfinite() ? Integer.MAX_VALUE : exp;
        int be = o.isInfinite() ? Integer.MAX_VALUE : o.exp;
        if (ae != be) {
            return Integer.compare(ae, be);
        }
        int c = Long.compareUnsigned(l3, o.l3);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l2, o.l2);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l1, o.l1);
        if (c != 0) {
            return c;
        }
        return Long.compareUnsigned(l0, o.l0);
    }

    /**
     * Value equality with Java {@code Double} conventions: {@code +0} and {@code -0} differ,
     * every NaN equals every NaN.
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Binary256 o)) {
            return false;
        }
        if (kind != o.kind) {
            return false;
        }
        if (kind != FINITE) {
            return kind == NAN || sign == o.sign;
        }
        if (expressionCanonicalised() != o.expressionCanonicalised()) {
            return false;
        }
        return sign == o.sign && l0 == o.l0 && l1 == o.l1 && l2 == o.l2 && l3 == o.l3;
    }

    private int expressionCanonicalised() {
        return isZero() ? 0 : exp;
    }

    @Override
    public int hashCode() {
        if (kind == NAN) {
            return 0x7FF8_0000;
        }
        if (kind == INFINITE) {
            return sign == 0 ? 0x7FF0_0000 : 0xFFF0_0000;
        }
        int h = sign * 31 + expressionCanonicalised();
        h = h * 31 + Long.hashCode(l3);
        h = h * 31 + Long.hashCode(l2);
        h = h * 31 + Long.hashCode(l1);
        return h * 31 + Long.hashCode(l0);
    }

    // ------------------------------------------------------------------ toDouble

    /** Correctly rounded conversion to binary64; overflow gives infinity, underflow gives zero. */
    public double toDouble() {
        if (kind == NAN) {
            return Double.NaN;
        }
        if (kind == INFINITE) {
            return sign == 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }
        if (isZero()) {
            return sign == 0 ? 0.0 : -0.0;
        }
        int e = exp;
        if (e > 1023) {
            return sign == 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }
        // Keep 53 bits of m for a normal double; a subnormal destination keeps fewer.
        int sub = e < -1022 ? (-1022 - e) : 0;
        int k = (BIAS - 52) + sub;
        long q = shiftLow(k);
        long guard = bitAt(k - 1);
        boolean sticky = anyBelow(k - 2);
        if (guard != 0L && (sticky || (q & 1L) != 0L)) {
            q++;
        }
        if (e < -1022) {
            // q < 2^52, or exactly 2^52 when rounding carried into the normal range
            return Double.longBitsToDouble(((long) sign << 63) | q);
        }
        if (q >= (1L << 53)) {
            q >>>= 1;
            e++;
            if (e > 1023) {
                return sign == 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            }
        }
        long bits = ((long) sign << 63) | ((long) (e + 1023) << 52) | (q & ((1L << 52) - 1));
        return Double.longBitsToDouble(bits);
    }

    /** {@code m >>> k} for a 256-bit {@code m}, for {@code k} in {@code [0, 256]}. */
    private long shiftLow(int k) {
        if (k < 0) {
            return limb(0);
        }
        if (k >= 256) {
            return 0L;
        }
        int li = k >> 6;
        int bi = k & 63;
        long lo = limb(li) >>> bi;
        if (bi != 0) {
            lo |= limb(li + 1) << (64 - bi);
        }
        return lo;
    }

    /** Bit {@code i} of the 256-bit significand; 0 outside {@code [0, 255]}. */
    private long bitAt(int i) {
        if (i < 0 || i > 255) {
            return 0L;
        }
        return (limb(i >> 6) >>> (i & 63)) & 1L;
    }

    /** True if any of bits {@code [0, i]} of the significand is set. */
    private boolean anyBelow(int i) {
        if (i < 0) {
            return false;
        }
        if (i >= 255) {
            return (l0 | l1 | l2 | l3) != 0L;
        }
        int li = i >> 6;
        int bi = i & 63;
        long acc = 0L;
        for (int j = 0; j < li; j++) {
            acc |= limb(j);
        }
        long mask = bi == 63 ? -1L : (1L << (bi + 1)) - 1L;
        return (acc | (limb(li) & mask)) != 0L;
    }

    long limb(int i) {
        return switch (i) {
            case 0 -> l0;
            case 1 -> l1;
            case 2 -> l2;
            case 3 -> l3;
            default -> 0L;
        };
    }

    // ------------------------------------------------------------------ diagnostics

    @Override
    public String toString() {
        if (kind == NAN) {
            return "NaN";
        }
        if (kind == INFINITE) {
            return sign == 0 ? "+Inf" : "-Inf";
        }
        if (isZero()) {
            return sign == 0 ? "+0" : "-0";
        }
        return (sign == 0 ? "+" : "-") + "m=" + Long.toHexString(l3) + ":"
                + Long.toHexString(l2) + ":" + Long.toHexString(l1) + ":" + Long.toHexString(l0)
                + " exp=" + exp;
    }
}
