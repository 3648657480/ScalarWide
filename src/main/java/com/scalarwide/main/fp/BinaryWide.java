package com.scalarwide.main.fp;

import com.scalarwide.main.integer.Limbs;

import java.math.BigInteger;

/**
 * Shared implementation of the flat wide formats: {@link FpFormat} supplies the precision and the
 * exponent range, {@link WideOps} does the arithmetic, and this class adds the value-level API
 * (predicates, comparison, decimal I/O) that is identical for every width.
 *
 * <p>Subclasses differ only in their format constant, so {@code Binary640} and {@code Binary1280}
 * are thin shells over this class.
 *
 * @param <T> the concrete format
 */
public abstract class BinaryWide<T extends BinaryWide<T>> implements Comparable<T> {

    final FpFormat format;
    final byte kind;
    final int sign;
    final int exp;
    final long[] mag;

    BinaryWide(FpFormat format, byte kind, int sign, int exp, long[] mag) {
        this.format = format;
        this.kind = kind;
        this.sign = (byte) sign;
        this.exp = exp;
        this.mag = mag;
    }

    /** Builds an instance of the concrete format from already-normalised parts. */
    abstract T make(byte kind, int sign, int exp, long[] mag);

    final FpFormat format() {
        return format;
    }

    final long[] mag() {
        return mag;
    }

    final T from(WideOps.Value v) {
        long[] m = v.mag() == null ? new long[format.limbs()] : v.mag();
        return make(v.kind(), v.sign(), v.exp(), m);
    }

    final WideOps.Value asValue() {
        return new WideOps.Value(kind, sign, exp, mag);
    }

    // ---------------------------------------------------------------- predicates

    public final boolean isNaN() {
        return kind == WideOps.NAN;
    }

    public final boolean isInfinite() {
        return kind == WideOps.INFINITE;
    }

    public final boolean isFinite() {
        return kind == WideOps.FINITE;
    }

    public final boolean isZero() {
        return kind == WideOps.FINITE && WideOps.bitLength(mag) == 0;
    }

    public final boolean isNormal() {
        return kind == WideOps.FINITE && !isZero() && exp >= format.expMin();
    }

    public final boolean isSubnormal() {
        return kind == WideOps.FINITE && !isZero() && exp < format.expMin();
    }

    public final boolean signBit() {
        return sign != 0;
    }

    public final int scaleExponent() {
        return exp;
    }

    // ---------------------------------------------------------------- arithmetic

    public final T negate() {
        if (isNaN()) {
            return make(WideOps.NAN, 0, 0, mag);
        }
        return make(kind, sign ^ 1, exp, mag);
    }

    public final T abs() {
        return sign == 0 ? make(kind, sign, exp, mag) : negate();
    }

    public final T add(T o) {
        return from(WideOps.add(format, asValue(), o.asValue()));
    }

    public final T subtract(T o) {
        return from(WideOps.add(format, asValue(), o.negate().asValue()));
    }

    public final T multiply(T o) {
        return from(WideOps.multiply(format, asValue(), o.asValue()));
    }

    public final T divide(T o) {
        return from(WideOps.divide(format, asValue(), o.asValue()));
    }

    /**
     * Correctly rounded square root. Scales the significand so the integer root carries more than
     * {@code p} bits, then rounds through the same {@link WideOps#pack} every other operation uses.
     */
    public final T sqrt() {
        if (isNaN() || sign != 0) {
            return make(WideOps.NAN, 0, 0, new long[format.limbs()]);
        }
        if (isZero() || isInfinite()) {
            return make(kind, sign, exp, mag);
        }
        int p = format.precision();
        int t = p - 1;
        int e = exp - t;
        BigInteger m = Limbs.toBigInteger(mag);
        int lead = e + m.bitLength() - 1;
        int lsb = Math.floorDiv(lead, 2) - t;
        BigInteger n = m.shiftLeft(e - 2 * lsb);
        BigInteger root = n.sqrt();
        boolean exact = root.multiply(root).equals(n);
        long[] frame = new long[format.limbs()];
        BigInteger work = root;
        for (int i = 0; i < frame.length; i++) {
            frame[i] = work.longValue();
            work = work.shiftRight(64);
        }
        return from(WideOps.pack(format, 0, lsb + t, frame, !exact, false));
    }

    /**
     * {@code this * b + c} with a single rounding. The exact {@code 2p}-bit product and {@code c}
     * are aligned into one frame and rounded once; when the exponent gap exceeds the frame the
     * smaller operand collapses into a sticky tail.
     */
    public final T fma(T b, T c) {
        int l = format.limbs();
        long[] zeroMag = new long[l];
        if (isNaN() || b.isNaN() || c.isNaN()) {
            return make(WideOps.NAN, 0, 0, zeroMag);
        }
        boolean aInf = isInfinite();
        boolean bInf = b.isInfinite();
        if ((aInf && b.isZero()) || (isZero() && bInf)) {
            return make(WideOps.NAN, 0, 0, zeroMag);
        }
        if (aInf || bInf) {
            boolean prodSign = (sign ^ b.sign) != 0;
            if (c.isInfinite() && c.sign != (prodSign ? 1 : 0)) {
                return make(WideOps.NAN, 0, 0, zeroMag);
            }
            return make(WideOps.INFINITE, prodSign ? 1 : 0, 0, new long[l]);
        }
        if (c.isInfinite()) {
            return c;
        }
        if (isZero() || b.isZero()) {
            return c;
        }
        if (c.isZero()) {
            return from(WideOps.multiply(format, asValue(), b.asValue()));
        }
        return from(WideOps.fma(format, asValue(), b.asValue(), c.asValue()));
    }
    // ---------------------------------------------------------------- comparison

    @Override
    public final int compareTo(T o) {
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
        int magCmp = WideOps.compareMagnitude(asValue(), o.asValue());
        return sign == 0 ? magCmp : -magCmp;
    }

    @Override
    public final boolean equals(Object obj) {
        if (!(obj instanceof BinaryWide<?> o) || o.format != format) {
            return false;
        }
        if (kind != o.kind) {
            return false;
        }
        if (kind == WideOps.NAN) {
            return true;
        }
        if (kind == WideOps.INFINITE) {
            return sign == o.sign;
        }
        if (isZero() != o.isZero()) {
            return false;
        }
        if (!isZero() && exp != o.exp) {
            return false;
        }
        if (sign != o.sign) {
            return false;
        }
        for (int i = 0; i < mag.length; i++) {
            if (mag[i] != o.mag[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public final int hashCode() {
        if (kind == WideOps.NAN) {
            return 0x7FF8_0000;
        }
        if (kind == WideOps.INFINITE) {
            return sign == 0 ? 0x7FF0_0000 : 0xFFF0_0000;
        }
        int h = sign * 31 + (isZero() ? 0 : exp);
        for (int i = mag.length - 1; i >= 0; i--) {
            h = h * 31 + Long.hashCode(mag[i]);
        }
        return h;
    }

    // ---------------------------------------------------------------- conversions

    public final double toDouble() {
        return WideOps.toDouble(format, asValue());
    }

    /** Correctly rounded decimal with the given number of significant digits ({@code digits >= 1}). */
    public final String toString(int digits) {
        if (digits < 1) {
            throw new IllegalArgumentException("digits must be >= 1");
        }
        if (isNaN()) {
            return "NaN";
        }
        if (isInfinite()) {
            return sign == 0 ? "Infinity" : "-Infinity";
        }
        if (isZero()) {
            return sign == 0 ? "0" : "-0";
        }
        return Decimal.toDecimal(Limbs.toBigInteger(mag), exp - (format.precision() - 1), digits,
                sign != 0);
    }

    @Override
    public final String toString() {
        int digits = (int) Math.ceil(format.precision() * 0.30103) + 2;
        String s = toString(digits);
        int dot = s.indexOf('.');
        if (dot < 0) {
            return s;
        }
        int end = s.length();
        while (end > dot + 1 && s.charAt(end - 1) == '0') {
            end--;
        }
        if (end == dot + 1) {
            end = dot;
        }
        return s.substring(0, end);
    }
}
