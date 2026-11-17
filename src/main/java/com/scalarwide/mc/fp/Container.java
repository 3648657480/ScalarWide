package com.scalarwide.mc.fp;

import java.util.Arrays;

/**
 * 1280-bit binary coordinate container.
 *
 * <p>Value model: {@code (-1)^sign * M * 2^lsbExp}, where {@code M} is an unsigned integer below
 * {@code 2^1252} held in twenty little-endian limbs. The magnitude is kept normalised (limb 19 has
 * bit 44 set) so that {@code lsbExp} and the leading-bit exponent differ by the constant
 * {@code precision - 1}.
 *
 * <p>This is deliberately not a general arithmetic type: {@code add}/{@code subtract}/wide
 * {@code multiply} are out of scope because they cannot meet the container budget
 * (see {@code docs/B1280-CONTAINER.md}). Only the four operations a per-octave noise loop needs are
 * provided, and every one of them rewrites this instance instead of returning a new value, so a
 * caller that reuses one container per axis allocates nothing inside the loop.
 *
 * <p>Thread safety: none. One container per thread, per the G1.1 generation model.
 */
public abstract class Container {

    /** Significant bits of the magnitude window. */
    final int precision;
    /** Little-endian limb count. */
    final int limbs;
    /** Limb holding the implicit leading one. */
    final int topLimb;

    final long[] m;
    /** Product staging area: a limbs x 1 multiply needs one more limb than the container holds. */
    private final long[] staged;
    private int lsbExp;
    private boolean negative;

    Container(int precision, int limbs) {
        this.precision = precision;
        this.limbs = limbs;
        this.topLimb = (precision - 1) >>> 6;
        this.m = new long[limbs];
        this.staged = new long[limbs + 1];
    }

    /** Exact from a {@code long}; zero clears the sign too. */
    public Container set(long v) {
        Arrays.fill(m, 0L);
        if (v == 0L) {
            negative = false;
            lsbExp = 0;
            return this;
        }
        negative = v < 0;
        long mag = negative ? -v : v;
        int lead = 63 - Long.numberOfLeadingZeros(mag);
        int shift = (precision - 1) - lead;
        int li = shift >>> 6;
        int bs = shift & 63;
        m[li] = mag << bs;
        if (bs != 0 && li + 1 < limbs) {
            m[li + 1] = mag >>> (64 - bs);
        }
        lsbExp = -shift;
        return this;
    }

    /** Copies value and sign; the exponent travels with them. */
    public Container copyFrom(Container o) {
        System.arraycopy(o.m, 0, m, 0, limbs);
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
        return m[topLimb] == 0L;
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
    void setMagnitude(long[] src, int exp, boolean sign) {
        System.arraycopy(src, 0, m, 0, limbs);
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
            throw new ArithmeticException("Container exponent overflow");
        }
        lsbExp = e;
    }

    /**
     * Replaces the value with the unsigned integer held in {@code limbs} (little-endian, any
     * length), exactly. Cold path: once per chunk per axis, never inside an octave loop.
     *
     * @throws ArithmeticException if the magnitude needs more than {@link #precision} bits
     */
    public Container setFrom(long[] src) {
        int top = src.length - 1;
        while (top >= 0 && src[top] == 0L) {
            top--;
        }
        if (top < 0) {
            setZero();
            return this;
        }
        int bits = top >= limbs ? Integer.MAX_VALUE
                : top * 64 + 64 - Long.numberOfLeadingZeros(src[top]);
        if (bits > precision) {
            throw new ArithmeticException("Container: magnitude exceeds " + precision + " bits");
        }
        Arrays.fill(m, 0L);
        int copy = Math.min(top + 1, limbs);
        System.arraycopy(src, 0, m, 0, copy);
        int shift = precision - bits;
        if (shift > 0) {
            shlInPlace(m, shift);
        }
        lsbExp = -shift;
        negative = false;
        return this;
    }

    /**
     * Adds a signed 64-bit integer at the value's binary point. The caller guarantees
     * {@code |v| >= 2^53} (far field), so no renormalisation happens in the hot path; the
     * crossing case is still handled exactly.
     *
     * @throws IllegalStateException if the current LSB sits above 2^0, where a unit offset would
     *         not be representable
     */
    public void addSmall(long delta) {
        if (delta == 0L) {
            return;
        }
        if (isZero()) {
            set(delta);
            return;
        }
        if (lsbExp > 0) {
            throw new IllegalStateException("Container: magnitude is coarser than 1");
        }
        long mag = delta < 0L ? -delta : delta;
        boolean deltaNegative = delta < 0L;
        int shift = -lsbExp;
        int li = shift >>> 6;
        int bs = shift & 63;
        staged[li] = mag << bs;
        int hi = bs != 0 ? li + 1 : -1;
        if (hi >= 0) {
            staged[hi] = mag >>> (64 - bs);
        }
        if (negative == deltaNegative) {
            addStaged(li);
        } else {
            int cmp = cmpStaged();
            if (cmp > 0) {
                subStaged(li);
            } else if (cmp < 0) {
                // |delta| > |v|: the result flips sign and needs renormalisation.
                stagedMinusM();
                negative = deltaNegative;
                normalize();
            } else {
                setZero();
            }
        }
        staged[li] = 0L;
        if (hi >= 0) {
            staged[hi] = 0L;
        }
    }

    private static void shlInPlace(long[] a, int k) {
        int ls = k >>> 6;
        int bs = k & 63;
        for (int i = a.length - 1; i >= 0; i--) {
            int src = i - ls;
            long v = 0L;
            if (src >= 0) {
                v = a[src] << bs;
                if (bs != 0 && src - 1 >= 0) {
                    v |= a[src - 1] >>> (64 - bs);
                }
            }
            a[i] = v;
        }
    }

    private void addStaged(int from) {
        long carry = 0L;
        for (int i = from; i < limbs; i++) {
            long t = m[i] + staged[i];
            long c = Long.compareUnsigned(t, m[i]) < 0 ? 1L : 0L;
            long s = t + carry;
            if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                c = 1L;
            }
            m[i] = s;
            carry = c;
        }
        int bits = bitLength(m);
        if (bits > precision) {
            int drop = bits - precision;
            shiftRightInto(m, drop, m);
            lsbExp += drop;
        }
    }

    private void subStaged(int from) {
        long borrow = 0L;
        for (int i = from; i < limbs; i++) {
            long d = m[i] - staged[i];
            long b = Long.compareUnsigned(m[i], staged[i]) < 0 ? 1L : 0L;
            long r = d - borrow;
            if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                b = 1L;
            }
            m[i] = r;
            borrow = b;
        }
    }

    private int cmpStaged() {
        for (int i = staged.length - 1; i >= limbs; i--) {
            if (staged[i] != 0L) {
                return -1;
            }
        }
        for (int i = limbs - 1; i >= 0; i--) {
            int c = Long.compareUnsigned(m[i], staged[i]);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private void stagedMinusM() {
        long borrow = 0L;
        for (int i = 0; i < limbs; i++) {
            long d = staged[i] - m[i];
            long b = Long.compareUnsigned(staged[i], m[i]) < 0 ? 1L : 0L;
            long r = d - borrow;
            if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                b = 1L;
            }
            m[i] = r;
            borrow = b;
        }
    }

    /**
     * Restores the normalisation invariant. A left shift is always exact; a right shift can drop
     * low bits, which only happens when the result outgrew the window (truncation contract).
     */
    private void normalize() {
        int bits = bitLength(m);
        if (bits == 0) {
            setZero();
            return;
        }
        if (bits > precision) {
            int drop = bits - precision;
            shiftRightInto(m, drop, m);
            lsbExp += drop;
        } else if (bits < precision) {
            int grow = precision - bits;
            shlInPlace(m, grow);
            lsbExp -= grow;
        }
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
        for (int i = 0; i < limbs; i++) {
            long lo = m[i] * mag;
            long hi = Math.unsignedMultiplyHigh(m[i], mag);
            long s = lo + carry;
            carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
            staged[i] = s;
        }
        staged[limbs] = carry;
        int bits = bitLength(staged);
        int drop = bits - precision;
        if (drop <= 0) {
            System.arraycopy(staged, 0, m, 0, limbs);
        } else {
            shiftRightInto(staged, drop, m);
            lsbExp += drop;
        }
        Arrays.fill(staged, 0L);
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
    public int compareTo(Container o) {        boolean az = isZero();
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

    private int compareLimbs(Container o) {
        for (int i = limbs - 1; i >= 0; i--) {
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
        if (li >= limbs) {
            return 0L;
        }
        long v = m[li] >>> sh;
        if (sh != 0 && li + 1 < limbs) {
            v |= m[li + 1] << (64 - sh);
        }
        return v;
    }

    private boolean anyBelow(int bit) {
        if (bit <= 0) {
            return false;
        }
        int li = bit >>> 6;
        for (int i = 0; i < li && i < limbs; i++) {
            if (m[i] != 0L) {
                return true;
            }
        }
        if (li >= limbs) {
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

    private void shiftRightInto(long[] src, int k, long[] dst) {
        int ls = k >>> 6;
        int bs = k & 63;
        for (int i = 0; i < limbs; i++) {
            int j = i + ls;
            long v = j < src.length ? src[j] >>> bs : 0L;
            if (bs != 0 && j + 1 < src.length) {
                v |= src[j + 1] << (64 - bs);
            }
            dst[i] = v;
        }
    }
}
