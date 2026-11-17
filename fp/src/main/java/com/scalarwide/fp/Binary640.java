package com.scalarwide.fp;

import java.util.Arrays;

/**
 * 640-bit binary floating point: {@code p = 616}, exponent range
 * {@code [-8388606, 8388607]} (the format Long2 calls {@code Binary640}).
 *
 * <p>Value model: {@code (-1)^sign * m * 2^(exp - 615)} with {@code m} a 616-bit integer, always
 * normalised internally; subnormalisation happens only when a result is packed. Semantics follow
 * IEEE 754 for values (correct rounding, signed zero, infinities, NaN, gradual underflow).
 *
 * <p>See {@code fp/docs/SPEC.md} and {@code fp/docs/SEMANTICS.md}.
 */
public final class Binary640 extends BinaryWide<Binary640> {

    /** Format: precision and exponent range. */
    public static final FpFormat FORMAT = FpFormat.BINARY640;
    /** Significant bits, including the implicit leading one. */
    public static final int PRECISION = FORMAT.precision();
    /** Smallest exponent of a normal value. */
    public static final int EXP_MIN = FORMAT.expMin();
    /** Largest exponent of a normal value. */
    public static final int EXP_MAX = FORMAT.expMax();

    public static final Binary640 ZERO = new Binary640(WideOps.FINITE, 0, 0, new long[FORMAT.limbs()]);
    public static final Binary640 NEGATIVE_ZERO =
            new Binary640(WideOps.FINITE, 1, 0, new long[FORMAT.limbs()]);
    public static final Binary640 ONE = unit(0, 0);
    public static final Binary640 NEGATIVE_ONE = unit(1, 0);
    public static final Binary640 POSITIVE_INFINITY =
            new Binary640(WideOps.INFINITE, 0, 0, new long[FORMAT.limbs()]);
    public static final Binary640 NEGATIVE_INFINITY =
            new Binary640(WideOps.INFINITE, 1, 0, new long[FORMAT.limbs()]);
    public static final Binary640 NaN = new Binary640(WideOps.NAN, 0, 0, new long[FORMAT.limbs()]);
    public static final Binary640 MAX_VALUE = fill(-1L, EXP_MAX);
    public static final Binary640 MIN_NORMAL = unit(0, EXP_MIN);
    public static final Binary640 MIN_VALUE = unit(0, EXP_MIN - (PRECISION - 1));

    private Binary640(byte kind, int sign, int exp, long[] mag) {
        super(FORMAT, kind, sign, exp, mag);
    }

    @Override
    Binary640 make(byte kind, int sign, int exp, long[] mag) {
        return new Binary640(kind, sign, exp, mag);
    }

    private static Binary640 unit(int sign, int exp) {
        long[] m = new long[FORMAT.limbs()];
        m[FORMAT.limbs() - 1] = 1L << (FORMAT.topBit() & 63);
        return new Binary640(WideOps.FINITE, sign, exp, m);
    }

    private static Binary640 fill(long value, int exp) {
        long[] m = new long[FORMAT.limbs()];
        Arrays.fill(m, value);
        m[FORMAT.limbs() - 1] &= FORMAT.topLimbMask();
        return new Binary640(WideOps.FINITE, 0, exp, m);
    }

    private static Binary640 wrap(WideOps.Value v) {
        long[] m = v.mag() == null ? new long[FORMAT.limbs()] : v.mag();
        return new Binary640(v.kind(), v.sign(), v.exp(), m);
    }

    /** Exact conversion; every {@code long} is representable. */
    public static Binary640 of(long v) {
        return wrap(WideOps.of(FORMAT, v));
    }

    /** Exact conversion; every finite {@code double} is representable. */
    public static Binary640 of(double v) {
        return wrap(WideOps.of(FORMAT, v));
    }

    /** Correctly rounded parse of a decimal literal. */
    public static Binary640 of(String s) {
        return wrap(WideOps.fromDecimal(FORMAT, Decimal.scan(s)));
    }
}
