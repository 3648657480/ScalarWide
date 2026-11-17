package com.scalarwide.fp;

import java.util.Arrays;

/**
 * 1280-bit binary coordinate container.
 *
 * <p>Value model: {@code (-1)^sign * M * 2^lsbExp}, where {@code M} is an unsigned integer below
 * {@code 2^1252} held in twenty little-endian limbs. The magnitude is kept normalised (limb 19 has
 * bit 44 set) so that {@code lsbExp} and the leading-bit exponent differ by the constant
 * {@code PRECISION - 1}.
 *
 * <p>This is deliberately not a general arithmetic type: {@code add}/{@code subtract}/wide
 * {@code multiply} are out of scope because they cannot meet the container budget
 * (see {@code docs/B1280-CONTAINER.md}). Only the four operations a per-octave noise loop needs are
 * provided, and every one of them rewrites this instance instead of returning a new value, so a
 * caller that reuses one container per axis allocates nothing inside the loop.
 *
 * <p>Thread safety: none. One container per thread, per the G1.1 generation model.
 */
public final class Binary1280 {

    /** Significant bits, matching {@code FpFormat.BINARY1280}. */
    public static final int PRECISION = 1252;
    /** Little-endian limb count: {@code ceil(1280 / 64)}, i.e. 28 spare bits above the precision. */
    public static final int LIMBS = 20;
    /** Bit position of the implicit leading one. */
    private static final int TOP_BIT = PRECISION - 1;
    private static final int TOP_LIMB = TOP_BIT >>> 6;
    private static final long TOP_MASK = 1L << (TOP_BIT & 63);

    private final long[] m = new long[LIMBS];
    /** Product staging area: a 20x1 multiply needs one more limb than the container holds. */
    private final long[] staged = new long[LIMBS + 1];
    private int lsbExp;
    private boolean negative;

    /** Zero. */
    public Binary1280() {
    }

    /** Exact from a {@code long}; zero clears the sign too. */
    public Binary1280 set(long v) {
        Arrays.fill(m, 0L);
        if (v == 0L) {
            negative = false;
            lsbExp = 0;
            return this;
        }
        negative = v < 0;
        long mag = negative ? -v : v;
        int lead = 63 - Long.numberOfLeadingZeros(mag);
        int shift = TOP_BIT - lead;
        int li = shift >>> 6;
        int bs = shift & 63;
        m[li] = mag << bs;
        if (bs != 0 && li + 1 < LIMBS) {
            m[li + 1] = mag >>> (64 - bs);
        }
        lsbExp = -shift;
        return this;
    }

    /** Copies value and sign; the exponent travels with them. */
    public Binary1280 copyFrom(Binary1280 o) {
        System.arraycopy(o.m, 0, m, 0, LIMBS);
        lsbExp = o.lsbExp;
        negative = o.negative;
        return this;
    }

    public void setZero() {
        Arrays.fill(m, 0L);
        negative = false;
        lsbExp = 0;
    }

    /**
     * True when the value is zero. Relies on the class invariant that every non-zero magnitude is
     * normalised (top bit at limb 19), so the top limb alone decides.
     */
    public boolean isZero() {
        return m[TOP_LIMB] == 0L;
    }

    /** {@code -1}, {@code 0}, {@code +1}. */
    public int signum() {
        return isZero() ? 0 : (negative ? -1 : 1);
    }

    /** Exponent of the least significant mantissa bit. */
    public int lsbExponent() {
        return lsbExp;
    }

    /** Package-private for the differential test: limb {@code i} of the normalised magnitude. */
    long limb(int i) {
        return m[i];
    }

    /** Package-private for the differential test. */
    boolean signBit() {
        return negative;
    }

    /** Package-private for the differential test: places {@code mag} and {@code exp} verbatim. */
    void setMagnitude(long[] limbs, int exp, boolean sign) {
        System.arraycopy(limbs, 0, m, 0, LIMBS);
        lsbExp = exp;
        negative = sign;
    }

    /**
     * Multiplies by {@code 2^k}. Only the exponent changes: the mantissa is not touched, so this is
     * a single add and cannot overflow any limb.
     *
     * @throws ArithmeticException if the exponent leaves the representable {@code int} window
     */
    public void scalePow2(int k) {
        int e = lsbExp + k;
        if (((lsbExp ^ e) & (k ^ e)) < 0) {
            throw new ArithmeticException("Binary1280 exponent overflow");
        }
        lsbExp = e;
    }

    /**
     * Multiplies the magnitude by {@code w}, exactly while the product stays under
     * {@code 2^1252}; beyond that the low bits are shifted out (truncation, one rounding-free
     * step) so the container keeps its full precision window. A negative {@code w} flips the sign.
     */
    public void mulSmall(long w) {
        if (w == 0L) {
            setZero();
            return;
        }
        if (w < 0L && !isZero()) {
            negative = !negative;
        }
        long mag = w < 0L ? -w : w;
        long carry = 0L;
        for (int i = 0; i < LIMBS; i++) {
            long lo = m[i] * mag;
            long hi = Math.unsignedMultiplyHigh(m[i], mag);
            long s = lo + carry;
            carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
            staged[i] = s;
        }
        staged[LIMBS] = carry;
        int bits = bitLength(staged);
        int drop = bits - PRECISION;
        if (drop <= 0) {
            System.arraycopy(staged, 0, m, 0, LIMBS);
            return;
        }
        shiftRightInto(staged, drop, m);
        lsbExp += drop;
    }

    /**
     * Splits the value at the binary point: the low 64 bits of {@code floor(v)} go to
     * {@code out[0]} (two's complement, so negative floors read as their bit pattern), and the
     * fractional part {@code v - floor(v)} is returned as a double truncated to 53 bits.
     *
     * <p>The truncation is exact: this returns {@code floor(v)} plus the first 53 bits of the
     * fraction, never a rounded value, so repeated sampling is bit-reproducible.
     *
     * @param out one-element scratch array, written with the integer part's low 64 bits
     */
    public double floorLow64Frac(long[] out) {
        if (isZero()) {
            out[0] = 0L;
            return 0.0;
        }
        long low;
        long fracBits;
        if (lsbExp >= 0) {
            low = lsbExp >= 64 ? 0L : (m[0] & (-1L >>> lsbExp)) << lsbExp;
            fracBits = 0L;
            out[0] = negative ? -low : low;
            return 0.0;
        }
        int s = -lsbExp;
        low = window(s);
        boolean shortFraction = s < 53;
        if (shortFraction) {
            fracBits = (m[0] & ((1L << s) - 1L)) << (53 - s);
        } else {
            fracBits = window(s - 53) & ((1L << 53) - 1L);
        }
        if (negative) {
            // floor(v) = -(floor(|v|) + 1) when there is any fraction at all, and the fraction
            // becomes 1 - f; its top 53 bits need the discarded tail to know whether to borrow.
            boolean rest = !shortFraction && anyBelow(s - 53);
            if (fracBits != 0L || rest) {
                low = -(low + 1L);
                if (shortFraction) {
                    fracBits = ((1L << s) - (m[0] & ((1L << s) - 1L))) << (53 - s);
                } else {
                    fracBits = (1L << 53) - fracBits - (rest ? 1L : 0L);
                }
            } else {
                low = -low;
            }
        }
        out[0] = low;
        return fracBits * 0x1.0p-53;
    }

    /** Compares numeric value, not bit pattern. */
    public int compareTo(Binary1280 o) {        boolean az = isZero();
        boolean bz = o.isZero();
        if (az || bz) {
            return az && bz ? 0 : (az ? -Integer.signum(o.signum()) : Integer.signum(signum()));
        }
        if (negative != o.negative) {
            return negative ? -1 : 1;
        }
        int c = Integer.compare(lsbExp, o.lsbExp);
        if (c == 0) {
            c = compareLimbs(o);
        }
        return negative ? -c : c;
    }

    private int compareLimbs(Binary1280 o) {
        for (int i = LIMBS - 1; i >= 0; i--) {
            int c = Long.compareUnsigned(m[i], o.m[i]);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private long window(int bit) {
        int li = bit >>> 6;
        int sh = bit & 63;
        if (li >= LIMBS) {
            return 0L;
        }
        long v = m[li] >>> sh;
        if (sh != 0 && li + 1 < LIMBS) {
            v |= m[li + 1] << (64 - sh);
        }
        return v;
    }

    private boolean anyBelow(int bit) {
        if (bit <= 0) {
            return false;
        }
        int li = bit >>> 6;
        for (int i = 0; i < li && i < LIMBS; i++) {
            if (m[i] != 0L) {
                return true;
            }
        }
        if (li >= LIMBS) {
            return false;
        }
        int sh = bit & 63;
        return (m[li] & (sh == 0 ? 0L : (1L << sh) - 1L)) != 0L;
    }

    private static int bitLength(long[] a) {
        for (int i = a.length - 1; i >= 0; i--) {
            if (a[i] != 0L) {
                return i * 64 + 64 - Long.numberOfLeadingZeros(a[i]);
            }
        }
        return 0;
    }

    private static void shiftRightInto(long[] src, int k, long[] dst) {
        int ls = k >>> 6;
        int bs = k & 63;
        for (int i = 0; i < LIMBS; i++) {
            int j = i + ls;
            long v = j < src.length ? src[j] >>> bs : 0L;
            if (bs != 0 && j + 1 < src.length) {
                v |= src[j + 1] << (64 - bs);
            }
            dst[i] = v;
        }
    }
}
