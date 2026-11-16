package com.scalarwide.fp;

import java.math.BigInteger;

/**
 * Decimal conversion: correctly rounded output at a requested number of significant digits, and
 * correctly rounded parsing.
 *
 * <p>Everything is exact integer arithmetic on {@code m * 2^e / 10^p}. A {@code BigDecimal}
 * short-cut is not usable: materialising {@code 2^e} for a large exponent would need tens of
 * thousands of digits.
 */
final class Decimal {

    private static final BigInteger TEN = BigInteger.TEN;
    private static final BigInteger FIVE = BigInteger.valueOf(5);

    private Decimal() {
    }

    // ---------------------------------------------------------------- output

    /** Rounds {@code m * 2^e} to {@code digits} significant decimal digits, nearest-even. */
    static String toDecimal(BigInteger m, int e, int digits, boolean negative) {
        if (m.signum() == 0) {
            return negative ? "-0" : "0";
        }
        long approx = (long) Math.floor((m.bitLength() + (long) e) * 0.30102999566398120);
        int p = (int) approx + 1 - digits;
        BigInteger low = TEN.pow(digits - 1);
        BigInteger high = TEN.pow(digits);
        // Decide the scale from the *truncated* quotient: rounding can carry (0.83 -> 1) and would
        // then look like a correctly-sized result at the wrong scale.
        BigInteger t = scaleTrunc(m, e, p);
        while (t.compareTo(high) >= 0) {
            p++;
            t = scaleTrunc(m, e, p);
        }
        while (t.compareTo(low) < 0) {
            p--;
            t = scaleTrunc(m, e, p);
        }
        BigInteger scaled = scaleRound(m, e, p);
        if (scaled.compareTo(high) >= 0) {
            p++;
            scaled = scaleRound(m, e, p);
        }
        return format(scaled.toString(), p, negative);
    }

    /** Numerator and denominator of {@code m * 2^e / 10^p}. */
    private static BigInteger[] scaledParts(BigInteger m, int e, int p) {
        BigInteger num = m;
        BigInteger den = BigInteger.ONE;
        if (p >= 0) {
            den = FIVE.pow(p);
        } else {
            num = num.multiply(FIVE.pow(-p));
        }
        int shift = e - p;
        if (shift >= 0) {
            num = num.shiftLeft(shift);
        } else {
            den = den.shiftLeft(-shift);
        }
        return new BigInteger[] {num, den};
    }

    private static BigInteger scaleTrunc(BigInteger m, int e, int p) {
        BigInteger[] parts = scaledParts(m, e, p);
        return parts[0].divide(parts[1]);
    }

    private static BigInteger scaleRound(BigInteger m, int e, int p) {
        BigInteger[] parts = scaledParts(m, e, p);
        BigInteger[] qr = parts[0].divideAndRemainder(parts[1]);
        BigInteger q = qr[0];
        int cmp = qr[1].shiftLeft(1).compareTo(parts[1]);
        if (cmp > 0 || (cmp == 0 && q.testBit(0))) {
            q = q.add(BigInteger.ONE);
        }
        return q;
    }

    private static String format(String digits, int p, boolean negative) {
        int n = digits.length();
        int pointPos = n + p;
        StringBuilder sb = new StringBuilder();
        if (negative) {
            sb.append('-');
        }
        // Use scientific notation outside a readable plain range, otherwise a value like
        // MAX_VALUE would expand to tens of thousands of characters.
        if (pointPos > 21 || pointPos < -6) {
            sb.append(digits.charAt(0));
            if (n > 1) {
                sb.append('.').append(digits, 1, n);
            }
            sb.append('E').append(pointPos - 1);
            return sb.toString();
        }
        if (p >= 0) {
            sb.append(digits).append("0".repeat(p));
        } else if (pointPos > 0) {
            sb.append(digits, 0, pointPos).append('.').append(digits, pointPos, n);
        } else {
            sb.append("0.").append("0".repeat(-pointPos)).append(digits);
        }
        return sb.toString();
    }
    // ---------------------------------------------------------------- input

    /** A scanned decimal literal: {@code sign * unscaled * 10^exp10}, exact. */
    record Scanned(BigInteger unscaled, int exp10, boolean negative) {
    }

    /** Parses the literal without any rounding; the caller decides the target format. */
    static Scanned scan(String s) {
        int i = 0;
        int n = s.length();
        boolean negative = false;
        if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            negative = s.charAt(i) == '-';
            i++;
        }
        StringBuilder digits = new StringBuilder();
        int pointAt = -1;
        int seen = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
                seen++;
                i++;
            } else if (c == '.' && pointAt < 0) {
                pointAt = digits.length();
                i++;
            } else {
                break;
            }
        }
        if (seen == 0) {
            throw new NumberFormatException("no digits: " + s);
        }
        int exp10 = 0;
        if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            i++;
            boolean eneg = false;
            if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                eneg = s.charAt(i) == '-';
                i++;
            }
            int start = i;
            while (i < n && Character.isDigit(s.charAt(i))) {
                i++;
            }
            if (start == i) {
                throw new NumberFormatException("bad exponent: " + s);
            }
            exp10 = Integer.parseInt(s.substring(start, i));
            if (eneg) {
                exp10 = -exp10;
            }
        }
        if (i != n) {
            throw new NumberFormatException("trailing characters: " + s);
        }
        if (pointAt >= 0) {
            exp10 -= digits.length() - pointAt;
        }
        BigInteger unscaled = digits.length() == 0 ? BigInteger.ZERO : new BigInteger(digits.toString());
        return new Scanned(unscaled, exp10, negative);
    }

    /** Parses a decimal literal into the given format, correctly rounded. */
    static BigFloat parseBig(FpFormat f, String s) {
        Scanned sc = scan(s);
        if (sc.unscaled().signum() == 0) {
            return sc.negative() ? BigFloat.negativeZero(f) : BigFloat.zero(f);
        }
        BigInteger num = sc.unscaled();
        BigInteger den = BigInteger.ONE;
        if (sc.exp10() >= 0) {
            num = num.multiply(TEN.pow(sc.exp10()));
        } else {
            den = den.multiply(TEN.pow(-sc.exp10()));
        }
        int p = f.precision();
        int lead = exactLead(num, den);
        // One extra bit: the dropped bit then acts as the guard and the remainder as sticky.
        int shift = p - lead;
        if (shift >= 0) {
            num = num.shiftLeft(shift);
        } else {
            den = den.shiftLeft(-shift);
        }
        BigInteger[] qr = num.divideAndRemainder(den);
        // q carries p+1 bits, so its leading bit sits one place above the target leading bit.
        return BigFloat.packBig(f, sc.negative() ? 1 : 0, lead - 1, qr[0], qr[1].signum() != 0, false);
    }

    /** Exact {@code floor(log2(num/den))}. */
    private static int exactLead(BigInteger num, BigInteger den) {
        int lead = num.bitLength() - den.bitLength();
        if (lead >= 0) {
            if (num.compareTo(den.shiftLeft(lead)) < 0) {
                lead--;
            }
        } else if (num.shiftLeft(-lead).compareTo(den) < 0) {
            lead--;
        }
        return lead;
    }
    /** Parses a decimal literal into {@link Binary256}. */
    static Binary256 parse(String s) {
        Scanned sc = scan(s);
        if (sc.unscaled().signum() == 0) {
            return sc.negative() ? Binary256.NEGATIVE_ZERO : Binary256.ZERO;
        }
        return toBinary(sc.unscaled(), sc.exp10(), sc.negative());
    }
    /** Rounds {@code sign * unscaled * 10^exp10} to the binary format, nearest-even. */
    private static Binary256 toBinary(BigInteger unscaled, int exp10, boolean negative) {
        BigInteger num = unscaled;
        BigInteger den = BigInteger.ONE;
        if (exp10 >= 0) {
            num = num.multiply(TEN.pow(exp10));
        } else {
            den = den.multiply(TEN.pow(-exp10));
        }
        // Exact floor(log2(num/den)); the estimate can be one too high, so verify it.
        int lead = num.bitLength() - den.bitLength();
        if (lead >= 0) {
            if (num.compareTo(den.shiftLeft(lead)) < 0) {
                lead--;
            }
        } else if (num.shiftLeft(-lead).compareTo(den) < 0) {
            lead--;
        }
        int shift = (Binary256.PRECISION - 1) - lead;
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
        if (q.bitLength() > Binary256.PRECISION) {
            q = q.shiftRight(1);
            e++;
        }
        if (q.bitLength() < Binary256.PRECISION) {
            int grow = Binary256.PRECISION - q.bitLength();
            q = q.shiftLeft(grow);
            e -= grow;
        }
        BigInteger mask = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
        long[] limbs = new long[4];
        for (int k = 0; k < 4; k++) {
            limbs[k] = q.and(mask).longValue();
            q = q.shiftRight(64);
        }
        return Binary256.raw(negative ? 1 : 0, e, limbs[0], limbs[1], limbs[2], limbs[3]);
    }
}
