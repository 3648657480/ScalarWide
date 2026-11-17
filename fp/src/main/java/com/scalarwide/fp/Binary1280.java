package com.scalarwide.fp;

import java.util.Arrays;

/**
 * 1280-bit binary floating point: {@code p = 1252}, exponent range
 * {@code [-134217726, 134217727]} (the format Long2 calls {@code Binary1280}).
 *
 * <p>Value model: {@code (-1)^sign * m * 2^(exp - 1251)} with {@code m} a 1252-bit integer, always
 * normalised internally; subnormalisation happens only when a result is packed. Semantics follow
 * IEEE 754 for values (correct rounding, signed zero, infinities, NaN, gradual underflow).
 *
 * <p>See {@code fp/docs/SPEC.md} and {@code fp/docs/SEMANTICS.md}.
 */
public final class Binary1280 extends BinaryWide<Binary1280> {

    /** Format: precision and exponent range. */
    public static final FpFormat FORMAT = FpFormat.BINARY1280;
    /** Significant bits, including the implicit leading one. */
    public static final int PRECISION = FORMAT.precision();
    /** Smallest exponent of a normal value. */
    public static final int EXP_MIN = FORMAT.expMin();
    /** Largest exponent of a normal value. */
    public static final int EXP_MAX = FORMAT.expMax();

    public static final Binary1280 ZERO = new Binary1280(WideOps.FINITE, 0, 0, new long[FORMAT.limbs()]);
    public static final Binary1280 NEGATIVE_ZERO =
            new Binary1280(WideOps.FINITE, 1, 0, new long[FORMAT.limbs()]);
    public static final Binary1280 ONE = unit(0, 0);
    public static final Binary1280 NEGATIVE_ONE = unit(1, 0);
    public static final Binary1280 POSITIVE_INFINITY =
            new Binary1280(WideOps.INFINITE, 0, 0, new long[FORMAT.limbs()]);
    public static final Binary1280 NEGATIVE_INFINITY =
            new Binary1280(WideOps.INFINITE, 1, 0, new long[FORMAT.limbs()]);
    public static final Binary1280 NaN = new Binary1280(WideOps.NAN, 0, 0, new long[FORMAT.limbs()]);
    public static final Binary1280 MAX_VALUE = fill(-1L, EXP_MAX);
    public static final Binary1280 MIN_NORMAL = unit(0, EXP_MIN);
    public static final Binary1280 MIN_VALUE = unit(0, EXP_MIN - (PRECISION - 1));

    private Binary1280(byte kind, int sign, int exp, long[] mag) {
        super(FORMAT, kind, sign, exp, mag);
    }

    @Override
    Binary1280 make(byte kind, int sign, int exp, long[] mag) {
        return new Binary1280(kind, sign, exp, mag);
    }

    private static Binary1280 unit(int sign, int exp) {
        long[] m = new long[FORMAT.limbs()];
        m[FORMAT.limbs() - 1] = 1L << (FORMAT.topBit() & 63);
        return new Binary1280(WideOps.FINITE, sign, exp, m);
    }

    private static Binary1280 fill(long value, int exp) {
        long[] m = new long[FORMAT.limbs()];
        Arrays.fill(m, value);
        m[FORMAT.limbs() - 1] &= FORMAT.topLimbMask();
        return new Binary1280(WideOps.FINITE, 0, exp, m);
    }

    private static Binary1280 wrap(WideOps.Value v) {
        long[] m = v.mag() == null ? new long[FORMAT.limbs()] : v.mag();
        return new Binary1280(v.kind(), v.sign(), v.exp(), m);
    }

    /** Exact conversion; every {@code long} is representable. */
    public static Binary1280 of(long v) {
        return wrap(WideOps.of(FORMAT, v));
    }

    /** Exact conversion; every finite {@code double} is representable. */
    public static Binary1280 of(double v) {
        return wrap(WideOps.of(FORMAT, v));
    }

    /** Correctly rounded parse of a decimal literal. */
    public static Binary1280 of(String s) {
        return wrap(WideOps.fromDecimal(FORMAT, Decimal.scan(s)));
    }
}
