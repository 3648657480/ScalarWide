package com.scalarwide.mc.fp;

/**
 * 1280-bit binary coordinate container.
 *
 * <p>Value model: {@code (-1)^sign * M * 2^lsbExp}, where {@code M} is an unsigned integer below
 * {@code 2^1252} held in twenty little-endian limb fields (not an array, so the hot ops carry no
 * bounds checks). The magnitude is kept normalised (the top bit sits at bit 1251) so that
 * {@code lsbExp} and the leading-bit exponent differ by the constant {@code PRECISION - 1}.
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

    private long l0;
    private long l1;
    private long l2;
    private long l3;
    private long l4;
    private long l5;
    private long l6;
    private long l7;
    private long l8;
    private long l9;
    private long l10;
    private long l11;
    private long l12;
    private long l13;
    private long l14;
    private long l15;
    private long l16;
    private long l17;
    private long l18;
    private long l19;
    /** Product staging area: a 20x1 multiply needs one more limb than the container holds. */
    private final long[] staged = new long[LIMBS + 1];
    /** Cold-path scratch for the renormalisation shifts. */
    private final long[] scratch = new long[LIMBS];
    private int lsbExp;
    private boolean negative;

    /** Zero. */
    public Binary1280() {
    }

    /** Exact from a {@code long}; zero clears the sign too. */
    public Binary1280 set(long v) {
        l0 = 0L;
        l1 = 0L;
        l2 = 0L;
        l3 = 0L;
        l4 = 0L;
        l5 = 0L;
        l6 = 0L;
        l7 = 0L;
        l8 = 0L;
        l9 = 0L;
        l10 = 0L;
        l11 = 0L;
        l12 = 0L;
        l13 = 0L;
        l14 = 0L;
        l15 = 0L;
        l16 = 0L;
        l17 = 0L;
        l18 = 0L;
        l19 = 0L;

        if (v == 0L) {
            negative = false;
            lsbExp = 0;
            return this;
        }
        negative = v < 0L;
        long mag = negative ? -v : v;
        int lead = 63 - Long.numberOfLeadingZeros(mag);
        int shift = TOP_BIT - lead;
        int li = shift >>> 6;
        int bs = shift & 63;
        long lo = mag << bs;
        if (li == 18) {
            l18 = lo;
            if (bs != 0) {
                l19 = mag >>> (64 - bs);
            }
        } else {
            l19 = lo;
        }
        lsbExp = -shift;
        return this;
    }

    /** Copies value and sign; the exponent travels with them. */
    public Binary1280 copyFrom(Binary1280 o) {
        l0 = o.l0;
        l1 = o.l1;
        l2 = o.l2;
        l3 = o.l3;
        l4 = o.l4;
        l5 = o.l5;
        l6 = o.l6;
        l7 = o.l7;
        l8 = o.l8;
        l9 = o.l9;
        l10 = o.l10;
        l11 = o.l11;
        l12 = o.l12;
        l13 = o.l13;
        l14 = o.l14;
        l15 = o.l15;
        l16 = o.l16;
        l17 = o.l17;
        l18 = o.l18;
        l19 = o.l19;

        lsbExp = o.lsbExp;
        negative = o.negative;
        return this;
    }

    public void setZero() {
        l0 = 0L;
        l1 = 0L;
        l2 = 0L;
        l3 = 0L;
        l4 = 0L;
        l5 = 0L;
        l6 = 0L;
        l7 = 0L;
        l8 = 0L;
        l9 = 0L;
        l10 = 0L;
        l11 = 0L;
        l12 = 0L;
        l13 = 0L;
        l14 = 0L;
        l15 = 0L;
        l16 = 0L;
        l17 = 0L;
        l18 = 0L;
        l19 = 0L;

        negative = false;
        lsbExp = 0;
    }

    /**
     * True when the value is zero. Relies on the class invariant that every non-zero magnitude is
     * normalised (top bit at limb 19), so the top limb alone decides.
     */
    public boolean isZero() {
        return l19 == 0L;
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
        return limbAt(i);
    }

    /** Package-private for the differential test. */
    boolean signBit() {
        return negative;
    }

    /** Package-private for the differential test: places {@code mag} and {@code exp} verbatim. */
    void setMagnitude(long[] limbs, int exp, boolean sign) {
        l0 = limbs[0];
        l1 = limbs[1];
        l2 = limbs[2];
        l3 = limbs[3];
        l4 = limbs[4];
        l5 = limbs[5];
        l6 = limbs[6];
        l7 = limbs[7];
        l8 = limbs[8];
        l9 = limbs[9];
        l10 = limbs[10];
        l11 = limbs[11];
        l12 = limbs[12];
        l13 = limbs[13];
        l14 = limbs[14];
        l15 = limbs[15];
        l16 = limbs[16];
        l17 = limbs[17];
        l18 = limbs[18];
        l19 = limbs[19];

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
     * Replaces the value with the unsigned integer held in {@code limbs} (little-endian, any
     * length), exactly. Cold path: once per chunk per axis, never inside an octave loop.
     *
     * @throws ArithmeticException if the magnitude needs more than {@link #PRECISION} bits
     */
    public Binary1280 setFrom(long[] limbs) {
        int top = limbs.length - 1;
        while (top >= 0 && limbs[top] == 0L) {
            top--;
        }
        if (top < 0) {
            setZero();
            return this;
        }
        int bits = top >= LIMBS ? Integer.MAX_VALUE
                : top * 64 + 64 - Long.numberOfLeadingZeros(limbs[top]);
        if (bits > PRECISION) {
            throw new ArithmeticException("Binary1280: magnitude exceeds " + PRECISION + " bits");
        }
        for (int i = 0; i <= LIMBS; i++) {
            staged[i] = 0L;
        }
        int copy = Math.min(top + 1, LIMBS);
        System.arraycopy(limbs, 0, staged, 0, copy);
        int shift = PRECISION - bits;
        if (shift > 0) {
            shlInPlace(staged, shift);
        }
        unpackStaged();
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
            throw new IllegalStateException("Binary1280: magnitude is coarser than 1");
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

    /** {@code Math.unsignedMultiplyHigh} is Java 18+; the mc line compiles at release 17. */
    private static long mulHigh(long x, long y) {
        long hi = Math.multiplyHigh(x, y);
        hi += (x >> 63) & y;
        hi += (y >> 63) & x;
        return hi;
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

    /** Carry chain from {@code from} to limb 19. Limbs below {@code from} hold no addend. */
    private void addStaged(int from) {
        long carry = 0L;
        switch (from) {
        case 0:
            {
                long t = l0 + staged[0];
                long c = Long.compareUnsigned(t, l0) < 0 ? 1L : 0L;
                long s = t + carry;
                if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                    c = 1L;
                }
                l0 = s;
                carry = c;
            }
        case 1:
            {
                long t = l1 + staged[1];
                long c = Long.compareUnsigned(t, l1) < 0 ? 1L : 0L;
                long s = t + carry;
                if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                    c = 1L;
                }
                l1 = s;
                carry = c;
            }
        case 2:
            {
                long t = l2 + staged[2];
                long c = Long.compareUnsigned(t, l2) < 0 ? 1L : 0L;
                long s = t + carry;
                if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                    c = 1L;
                }
                l2 = s;
                carry = c;
            }
        case 3:
            {
                long t = l3 + staged[3];
                long c = Long.compareUnsigned(t, l3) < 0 ? 1L : 0L;
                long s = t + carry;
                if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                    c = 1L;
                }
                l3 = s;
                carry = c;
            }
        case 4:
            {
                long t = l4 + staged[4];
                long c = Long.compareUnsigned(t, l4) < 0 ? 1L : 0L;
                long s = t + carry;
                if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                    c = 1L;
                }
                l4 = s;
                carry = c;
            }
        case 5:
            {
                long t = l5 + staged[5];
                long c = Long.compareUnsigned(t, l5) < 0 ? 1L : 0L;
                long s = t + carry;
                if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                    c = 1L;
                }
                l5 = s;
                carry = c;
            }
        case 6:
            {
                long t = l6 + staged[6];
                long c = Long.compareUnsigned(t, l6) < 0 ? 1L : 0L;
                long s = t + carry;
                if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                    c = 1L;
                }
                l6 = s;
                carry = c;
            }
        case 7:
            {
                long t = l7 + staged[7];
                long c = Long.compareUnsigned(t, l7) < 0 ? 1L : 0L;
                long s = t + carry;
                if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                    c = 1L;
                }
                l7 = s;
                carry = c;
            }
        case 8:
            {
                long t = l8 + staged[8];
                long c = Long.compareUnsigned(t, l8) < 0 ? 1L : 0L;
                long s = t + carry;
                if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                    c = 1L;
                }
                l8 = s;
                carry = c;
            }
        case 9:
            {
                long t = l9 + staged[9];
                long c = Long.compareUnsigned(t, l9) < 0 ? 1L : 0L;
                long s = t + carry;
                if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                    c = 1L;
                }
                l9 = s;
                carry = c;
            }
        case 10:
            {
                long t = l10 + staged[10];
                long c = Long.compareUnsigned(t, l10) < 0 ? 1L : 0L;
                long s = t + carry;
                if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                    c = 1L;
                }
                l10 = s;
                carry = c;
            }
        case 11:
            {
                long t = l11 + staged[11];
                long c = Long.compareUnsigned(t, l11) < 0 ? 1L : 0L;
                long s = t + carry;
                if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                    c = 1L;
                }
                l11 = s;
                carry = c;
            }
        case 12:
            {
                long t = l12 + staged[12];
                long c = Long.compareUnsigned(t, l12) < 0 ? 1L : 0L;
                long s = t + carry;
                if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                    c = 1L;
                }
                l12 = s;
                carry = c;
            }
        case 13:
            {
                long t = l13 + staged[13];
                long c = Long.compareUnsigned(t, l13) < 0 ? 1L : 0L;
                long s = t + carry;
                if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                    c = 1L;
                }
                l13 = s;
                carry = c;
            }
        case 14:
            {
                long t = l14 + staged[14];
                long c = Long.compareUnsigned(t, l14) < 0 ? 1L : 0L;
                long s = t + carry;
                if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                    c = 1L;
                }
                l14 = s;
                carry = c;
            }
        case 15:
            {
                long t = l15 + staged[15];
                long c = Long.compareUnsigned(t, l15) < 0 ? 1L : 0L;
                long s = t + carry;
                if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                    c = 1L;
                }
                l15 = s;
                carry = c;
            }
        case 16:
            {
                long t = l16 + staged[16];
                long c = Long.compareUnsigned(t, l16) < 0 ? 1L : 0L;
                long s = t + carry;
                if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                    c = 1L;
                }
                l16 = s;
                carry = c;
            }
        case 17:
            {
                long t = l17 + staged[17];
                long c = Long.compareUnsigned(t, l17) < 0 ? 1L : 0L;
                long s = t + carry;
                if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                    c = 1L;
                }
                l17 = s;
                carry = c;
            }
        case 18:
            {
                long t = l18 + staged[18];
                long c = Long.compareUnsigned(t, l18) < 0 ? 1L : 0L;
                long s = t + carry;
                if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                    c = 1L;
                }
                l18 = s;
                carry = c;
            }
        case 19:
            {
                long t = l19 + staged[19];
                long c = Long.compareUnsigned(t, l19) < 0 ? 1L : 0L;
                long s = t + carry;
                if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                    c = 1L;
                }
                l19 = s;
                carry = c;
            }

        }
    }

    /** Borrow chain from {@code from} to limb 19. Limbs below {@code from} hold no subtrahend. */
    private void subStaged(int from) {
        long borrow = 0L;
        switch (from) {
        case 0:
            {
                long d = l0 - staged[0];
                long b = Long.compareUnsigned(l0, staged[0]) < 0 ? 1L : 0L;
                long r = d - borrow;
                if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                    b = 1L;
                }
                l0 = r;
                borrow = b;
            }
        case 1:
            {
                long d = l1 - staged[1];
                long b = Long.compareUnsigned(l1, staged[1]) < 0 ? 1L : 0L;
                long r = d - borrow;
                if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                    b = 1L;
                }
                l1 = r;
                borrow = b;
            }
        case 2:
            {
                long d = l2 - staged[2];
                long b = Long.compareUnsigned(l2, staged[2]) < 0 ? 1L : 0L;
                long r = d - borrow;
                if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                    b = 1L;
                }
                l2 = r;
                borrow = b;
            }
        case 3:
            {
                long d = l3 - staged[3];
                long b = Long.compareUnsigned(l3, staged[3]) < 0 ? 1L : 0L;
                long r = d - borrow;
                if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                    b = 1L;
                }
                l3 = r;
                borrow = b;
            }
        case 4:
            {
                long d = l4 - staged[4];
                long b = Long.compareUnsigned(l4, staged[4]) < 0 ? 1L : 0L;
                long r = d - borrow;
                if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                    b = 1L;
                }
                l4 = r;
                borrow = b;
            }
        case 5:
            {
                long d = l5 - staged[5];
                long b = Long.compareUnsigned(l5, staged[5]) < 0 ? 1L : 0L;
                long r = d - borrow;
                if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                    b = 1L;
                }
                l5 = r;
                borrow = b;
            }
        case 6:
            {
                long d = l6 - staged[6];
                long b = Long.compareUnsigned(l6, staged[6]) < 0 ? 1L : 0L;
                long r = d - borrow;
                if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                    b = 1L;
                }
                l6 = r;
                borrow = b;
            }
        case 7:
            {
                long d = l7 - staged[7];
                long b = Long.compareUnsigned(l7, staged[7]) < 0 ? 1L : 0L;
                long r = d - borrow;
                if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                    b = 1L;
                }
                l7 = r;
                borrow = b;
            }
        case 8:
            {
                long d = l8 - staged[8];
                long b = Long.compareUnsigned(l8, staged[8]) < 0 ? 1L : 0L;
                long r = d - borrow;
                if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                    b = 1L;
                }
                l8 = r;
                borrow = b;
            }
        case 9:
            {
                long d = l9 - staged[9];
                long b = Long.compareUnsigned(l9, staged[9]) < 0 ? 1L : 0L;
                long r = d - borrow;
                if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                    b = 1L;
                }
                l9 = r;
                borrow = b;
            }
        case 10:
            {
                long d = l10 - staged[10];
                long b = Long.compareUnsigned(l10, staged[10]) < 0 ? 1L : 0L;
                long r = d - borrow;
                if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                    b = 1L;
                }
                l10 = r;
                borrow = b;
            }
        case 11:
            {
                long d = l11 - staged[11];
                long b = Long.compareUnsigned(l11, staged[11]) < 0 ? 1L : 0L;
                long r = d - borrow;
                if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                    b = 1L;
                }
                l11 = r;
                borrow = b;
            }
        case 12:
            {
                long d = l12 - staged[12];
                long b = Long.compareUnsigned(l12, staged[12]) < 0 ? 1L : 0L;
                long r = d - borrow;
                if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                    b = 1L;
                }
                l12 = r;
                borrow = b;
            }
        case 13:
            {
                long d = l13 - staged[13];
                long b = Long.compareUnsigned(l13, staged[13]) < 0 ? 1L : 0L;
                long r = d - borrow;
                if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                    b = 1L;
                }
                l13 = r;
                borrow = b;
            }
        case 14:
            {
                long d = l14 - staged[14];
                long b = Long.compareUnsigned(l14, staged[14]) < 0 ? 1L : 0L;
                long r = d - borrow;
                if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                    b = 1L;
                }
                l14 = r;
                borrow = b;
            }
        case 15:
            {
                long d = l15 - staged[15];
                long b = Long.compareUnsigned(l15, staged[15]) < 0 ? 1L : 0L;
                long r = d - borrow;
                if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                    b = 1L;
                }
                l15 = r;
                borrow = b;
            }
        case 16:
            {
                long d = l16 - staged[16];
                long b = Long.compareUnsigned(l16, staged[16]) < 0 ? 1L : 0L;
                long r = d - borrow;
                if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                    b = 1L;
                }
                l16 = r;
                borrow = b;
            }
        case 17:
            {
                long d = l17 - staged[17];
                long b = Long.compareUnsigned(l17, staged[17]) < 0 ? 1L : 0L;
                long r = d - borrow;
                if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                    b = 1L;
                }
                l17 = r;
                borrow = b;
            }
        case 18:
            {
                long d = l18 - staged[18];
                long b = Long.compareUnsigned(l18, staged[18]) < 0 ? 1L : 0L;
                long r = d - borrow;
                if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                    b = 1L;
                }
                l18 = r;
                borrow = b;
            }
        case 19:
            {
                long d = l19 - staged[19];
                long b = Long.compareUnsigned(l19, staged[19]) < 0 ? 1L : 0L;
                long r = d - borrow;
                if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                    b = 1L;
                }
                l19 = r;
                borrow = b;
            }

        }
    }

    private int cmpStaged() {
        if (staged[LIMBS] != 0L) {
            return -1;
        }
        int c = Long.compareUnsigned(l19, staged[19]);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l18, staged[18]);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l17, staged[17]);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l16, staged[16]);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l15, staged[15]);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l14, staged[14]);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l13, staged[13]);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l12, staged[12]);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l11, staged[11]);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l10, staged[10]);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l9, staged[9]);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l8, staged[8]);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l7, staged[7]);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l6, staged[6]);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l5, staged[5]);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l4, staged[4]);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l3, staged[3]);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l2, staged[2]);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l1, staged[1]);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l0, staged[0]);
        if (c != 0) {
            return c;
        }

        return 0;
    }

    /** {@code staged - m} with a full-width borrow, then written back. Cold: sign crossing only. */
    private void stagedMinusM() {
        scratch[0] = l0;
        scratch[1] = l1;
        scratch[2] = l2;
        scratch[3] = l3;
        scratch[4] = l4;
        scratch[5] = l5;
        scratch[6] = l6;
        scratch[7] = l7;
        scratch[8] = l8;
        scratch[9] = l9;
        scratch[10] = l10;
        scratch[11] = l11;
        scratch[12] = l12;
        scratch[13] = l13;
        scratch[14] = l14;
        scratch[15] = l15;
        scratch[16] = l16;
        scratch[17] = l17;
        scratch[18] = l18;
        scratch[19] = l19;
        long borrow = 0L;
        for (int i = 0; i < LIMBS; i++) {
            long d = staged[i] - scratch[i];
            long b = Long.compareUnsigned(staged[i], scratch[i]) < 0 ? 1L : 0L;
            long r = d - borrow;
            if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                b = 1L;
            }
            staged[i] = r;
            borrow = b;
        }
        unpackStaged();
    }

    /**
     * Restores the normalisation invariant. A left shift is always exact; a right shift can drop
     * low bits, which only happens when the result outgrew the window (truncation contract).
     */
    private void normalize() {
        int bits = bitLength();
        if (bits == 0) {
            setZero();
            return;
        }
        if (bits > PRECISION) {
            int drop = bits - PRECISION;
            shiftRightScratch(drop);
            lsbExp += drop;
        } else if (bits < PRECISION) {
            int grow = PRECISION - bits;
            scratch[0] = l0;
            scratch[1] = l1;
            scratch[2] = l2;
            scratch[3] = l3;
            scratch[4] = l4;
            scratch[5] = l5;
            scratch[6] = l6;
            scratch[7] = l7;
            scratch[8] = l8;
            scratch[9] = l9;
            scratch[10] = l10;
            scratch[11] = l11;
            scratch[12] = l12;
            scratch[13] = l13;
            scratch[14] = l14;
            scratch[15] = l15;
            scratch[16] = l16;
            scratch[17] = l17;
            scratch[18] = l18;
            scratch[19] = l19;
            shlInPlace(scratch, grow);
            l0 = scratch[0];
            l1 = scratch[1];
            l2 = scratch[2];
            l3 = scratch[3];
            l4 = scratch[4];
            l5 = scratch[5];
            l6 = scratch[6];
            l7 = scratch[7];
            l8 = scratch[8];
            l9 = scratch[9];
            l10 = scratch[10];
            l11 = scratch[11];
            l12 = scratch[12];
            l13 = scratch[13];
            l14 = scratch[14];
            l15 = scratch[15];
            l16 = scratch[16];
            l17 = scratch[17];
            l18 = scratch[18];
            l19 = scratch[19];
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
        long lo;
        long hi;
        long s;
        lo = l0 * mag;
        hi = mulHigh(l0, mag);
        s = lo + carry;
        carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
        l0 = s;
        lo = l1 * mag;
        hi = mulHigh(l1, mag);
        s = lo + carry;
        carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
        l1 = s;
        lo = l2 * mag;
        hi = mulHigh(l2, mag);
        s = lo + carry;
        carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
        l2 = s;
        lo = l3 * mag;
        hi = mulHigh(l3, mag);
        s = lo + carry;
        carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
        l3 = s;
        lo = l4 * mag;
        hi = mulHigh(l4, mag);
        s = lo + carry;
        carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
        l4 = s;
        lo = l5 * mag;
        hi = mulHigh(l5, mag);
        s = lo + carry;
        carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
        l5 = s;
        lo = l6 * mag;
        hi = mulHigh(l6, mag);
        s = lo + carry;
        carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
        l6 = s;
        lo = l7 * mag;
        hi = mulHigh(l7, mag);
        s = lo + carry;
        carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
        l7 = s;
        lo = l8 * mag;
        hi = mulHigh(l8, mag);
        s = lo + carry;
        carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
        l8 = s;
        lo = l9 * mag;
        hi = mulHigh(l9, mag);
        s = lo + carry;
        carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
        l9 = s;
        lo = l10 * mag;
        hi = mulHigh(l10, mag);
        s = lo + carry;
        carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
        l10 = s;
        lo = l11 * mag;
        hi = mulHigh(l11, mag);
        s = lo + carry;
        carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
        l11 = s;
        lo = l12 * mag;
        hi = mulHigh(l12, mag);
        s = lo + carry;
        carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
        l12 = s;
        lo = l13 * mag;
        hi = mulHigh(l13, mag);
        s = lo + carry;
        carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
        l13 = s;
        lo = l14 * mag;
        hi = mulHigh(l14, mag);
        s = lo + carry;
        carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
        l14 = s;
        lo = l15 * mag;
        hi = mulHigh(l15, mag);
        s = lo + carry;
        carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
        l15 = s;
        lo = l16 * mag;
        hi = mulHigh(l16, mag);
        s = lo + carry;
        carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
        l16 = s;
        lo = l17 * mag;
        hi = mulHigh(l17, mag);
        s = lo + carry;
        carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
        l17 = s;
        lo = l18 * mag;
        hi = mulHigh(l18, mag);
        s = lo + carry;
        carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
        l18 = s;
        lo = l19 * mag;
        hi = mulHigh(l19, mag);
        s = lo + carry;
        carry = hi + (Long.compareUnsigned(s, lo) < 0 ? 1L : 0L);
        l19 = s;

        int bits = carry != 0L
                ? LIMBS * 64 + 64 - Long.numberOfLeadingZeros(carry)
                : bitLength();
        int drop = bits - PRECISION;
        if (drop > 0) {
            shiftRightCarry(drop, carry);
            lsbExp += drop;
        }
    }

    /**
     * The product can exceed the window by at most 64 bits, one whole limb plus a remainder, so
     * {@code drop == 64} is the only limb-aligned case and gets its own path.
     */
    private void shiftRightCarry(int drop, long higher) {
        long cur;
        if (drop == 64) {
            cur = l19;
            l19 = higher;
            higher = cur;
            cur = l18;
            l18 = higher;
            higher = cur;
            cur = l17;
            l17 = higher;
            higher = cur;
            cur = l16;
            l16 = higher;
            higher = cur;
            cur = l15;
            l15 = higher;
            higher = cur;
            cur = l14;
            l14 = higher;
            higher = cur;
            cur = l13;
            l13 = higher;
            higher = cur;
            cur = l12;
            l12 = higher;
            higher = cur;
            cur = l11;
            l11 = higher;
            higher = cur;
            cur = l10;
            l10 = higher;
            higher = cur;
            cur = l9;
            l9 = higher;
            higher = cur;
            cur = l8;
            l8 = higher;
            higher = cur;
            cur = l7;
            l7 = higher;
            higher = cur;
            cur = l6;
            l6 = higher;
            higher = cur;
            cur = l5;
            l5 = higher;
            higher = cur;
            cur = l4;
            l4 = higher;
            higher = cur;
            cur = l3;
            l3 = higher;
            higher = cur;
            cur = l2;
            l2 = higher;
            higher = cur;
            cur = l1;
            l1 = higher;
            higher = cur;
            cur = l0;
            l0 = higher;
            higher = cur;

        } else {
            cur = l19;
            l19 = (cur >>> drop) | (higher << (64 - drop));
            higher = cur;
            cur = l18;
            l18 = (cur >>> drop) | (higher << (64 - drop));
            higher = cur;
            cur = l17;
            l17 = (cur >>> drop) | (higher << (64 - drop));
            higher = cur;
            cur = l16;
            l16 = (cur >>> drop) | (higher << (64 - drop));
            higher = cur;
            cur = l15;
            l15 = (cur >>> drop) | (higher << (64 - drop));
            higher = cur;
            cur = l14;
            l14 = (cur >>> drop) | (higher << (64 - drop));
            higher = cur;
            cur = l13;
            l13 = (cur >>> drop) | (higher << (64 - drop));
            higher = cur;
            cur = l12;
            l12 = (cur >>> drop) | (higher << (64 - drop));
            higher = cur;
            cur = l11;
            l11 = (cur >>> drop) | (higher << (64 - drop));
            higher = cur;
            cur = l10;
            l10 = (cur >>> drop) | (higher << (64 - drop));
            higher = cur;
            cur = l9;
            l9 = (cur >>> drop) | (higher << (64 - drop));
            higher = cur;
            cur = l8;
            l8 = (cur >>> drop) | (higher << (64 - drop));
            higher = cur;
            cur = l7;
            l7 = (cur >>> drop) | (higher << (64 - drop));
            higher = cur;
            cur = l6;
            l6 = (cur >>> drop) | (higher << (64 - drop));
            higher = cur;
            cur = l5;
            l5 = (cur >>> drop) | (higher << (64 - drop));
            higher = cur;
            cur = l4;
            l4 = (cur >>> drop) | (higher << (64 - drop));
            higher = cur;
            cur = l3;
            l3 = (cur >>> drop) | (higher << (64 - drop));
            higher = cur;
            cur = l2;
            l2 = (cur >>> drop) | (higher << (64 - drop));
            higher = cur;
            cur = l1;
            l1 = (cur >>> drop) | (higher << (64 - drop));
            higher = cur;
            cur = l0;
            l0 = (cur >>> drop) | (higher << (64 - drop));
            higher = cur;

        }
    }

    private void shiftRightScratch(int drop) {
        scratch[0] = l0;
        scratch[1] = l1;
        scratch[2] = l2;
        scratch[3] = l3;
        scratch[4] = l4;
        scratch[5] = l5;
        scratch[6] = l6;
        scratch[7] = l7;
        scratch[8] = l8;
        scratch[9] = l9;
        scratch[10] = l10;
        scratch[11] = l11;
        scratch[12] = l12;
        scratch[13] = l13;
        scratch[14] = l14;
        scratch[15] = l15;
        scratch[16] = l16;
        scratch[17] = l17;
        scratch[18] = l18;
        scratch[19] = l19;
        shiftRightInto(scratch, drop, scratch);
        l0 = scratch[0];
        l1 = scratch[1];
        l2 = scratch[2];
        l3 = scratch[3];
        l4 = scratch[4];
        l5 = scratch[5];
        l6 = scratch[6];
        l7 = scratch[7];
        l8 = scratch[8];
        l9 = scratch[9];
        l10 = scratch[10];
        l11 = scratch[11];
        l12 = scratch[12];
        l13 = scratch[13];
        l14 = scratch[14];
        l15 = scratch[15];
        l16 = scratch[16];
        l17 = scratch[17];
        l18 = scratch[18];
        l19 = scratch[19];
    }

    private void unpackStaged() {
        l0 = staged[0];
        l1 = staged[1];
        l2 = staged[2];
        l3 = staged[3];
        l4 = staged[4];
        l5 = staged[5];
        l6 = staged[6];
        l7 = staged[7];
        l8 = staged[8];
        l9 = staged[9];
        l10 = staged[10];
        l11 = staged[11];
        l12 = staged[12];
        l13 = staged[13];
        l14 = staged[14];
        l15 = staged[15];
        l16 = staged[16];
        l17 = staged[17];
        l18 = staged[18];
        l19 = staged[19];
        for (int i = 0; i <= LIMBS; i++) {
            staged[i] = 0L;
        }
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
            low = lsbExp >= 64 ? 0L : (l0 & (-1L >>> lsbExp)) << lsbExp;
            fracBits = 0L;
            out[0] = negative ? -low : low;
            return 0.0;
        }
        int s = -lsbExp;
        low = window(s);
        boolean shortFraction = s < 53;
        if (shortFraction) {
            fracBits = (l0 & ((1L << s) - 1L)) << (53 - s);
        } else {
            fracBits = window(s - 53) & ((1L << 53) - 1L);
        }
        if (negative) {
            boolean rest = !shortFraction && anyBelow(s - 53);
            if (fracBits != 0L || rest) {
                low = -(low + 1L);
                if (shortFraction) {
                    fracBits = ((1L << s) - (l0 & ((1L << s) - 1L))) << (53 - s);
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
    public int compareTo(Binary1280 o) {
        boolean az = isZero();
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
        int c = Long.compareUnsigned(l19, o.l19);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l18, o.l18);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l17, o.l17);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l16, o.l16);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l15, o.l15);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l14, o.l14);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l13, o.l13);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l12, o.l12);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l11, o.l11);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l10, o.l10);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l9, o.l9);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l8, o.l8);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l7, o.l7);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l6, o.l6);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l5, o.l5);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l4, o.l4);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l3, o.l3);
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
        c = Long.compareUnsigned(l0, o.l0);
        if (c != 0) {
            return c;
        }

        return 0;
    }

    private long window(int bit) {
        int li = bit >>> 6;
        int sh = bit & 63;
        switch (li) {
            case 0:
                {
                    long v = l0 >>> sh;
                    if (sh != 0) {
                        v |= l1 << (64 - sh);
                    }
                    return v;
                }
            case 1:
                {
                    long v = l1 >>> sh;
                    if (sh != 0) {
                        v |= l2 << (64 - sh);
                    }
                    return v;
                }
            case 2:
                {
                    long v = l2 >>> sh;
                    if (sh != 0) {
                        v |= l3 << (64 - sh);
                    }
                    return v;
                }
            case 3:
                {
                    long v = l3 >>> sh;
                    if (sh != 0) {
                        v |= l4 << (64 - sh);
                    }
                    return v;
                }
            case 4:
                {
                    long v = l4 >>> sh;
                    if (sh != 0) {
                        v |= l5 << (64 - sh);
                    }
                    return v;
                }
            case 5:
                {
                    long v = l5 >>> sh;
                    if (sh != 0) {
                        v |= l6 << (64 - sh);
                    }
                    return v;
                }
            case 6:
                {
                    long v = l6 >>> sh;
                    if (sh != 0) {
                        v |= l7 << (64 - sh);
                    }
                    return v;
                }
            case 7:
                {
                    long v = l7 >>> sh;
                    if (sh != 0) {
                        v |= l8 << (64 - sh);
                    }
                    return v;
                }
            case 8:
                {
                    long v = l8 >>> sh;
                    if (sh != 0) {
                        v |= l9 << (64 - sh);
                    }
                    return v;
                }
            case 9:
                {
                    long v = l9 >>> sh;
                    if (sh != 0) {
                        v |= l10 << (64 - sh);
                    }
                    return v;
                }
            case 10:
                {
                    long v = l10 >>> sh;
                    if (sh != 0) {
                        v |= l11 << (64 - sh);
                    }
                    return v;
                }
            case 11:
                {
                    long v = l11 >>> sh;
                    if (sh != 0) {
                        v |= l12 << (64 - sh);
                    }
                    return v;
                }
            case 12:
                {
                    long v = l12 >>> sh;
                    if (sh != 0) {
                        v |= l13 << (64 - sh);
                    }
                    return v;
                }
            case 13:
                {
                    long v = l13 >>> sh;
                    if (sh != 0) {
                        v |= l14 << (64 - sh);
                    }
                    return v;
                }
            case 14:
                {
                    long v = l14 >>> sh;
                    if (sh != 0) {
                        v |= l15 << (64 - sh);
                    }
                    return v;
                }
            case 15:
                {
                    long v = l15 >>> sh;
                    if (sh != 0) {
                        v |= l16 << (64 - sh);
                    }
                    return v;
                }
            case 16:
                {
                    long v = l16 >>> sh;
                    if (sh != 0) {
                        v |= l17 << (64 - sh);
                    }
                    return v;
                }
            case 17:
                {
                    long v = l17 >>> sh;
                    if (sh != 0) {
                        v |= l18 << (64 - sh);
                    }
                    return v;
                }
            case 18:
                {
                    long v = l18 >>> sh;
                    if (sh != 0) {
                        v |= l19 << (64 - sh);
                    }
                    return v;
                }
            case 19:
                return l19 >>> sh;

            default:
                return 0L;
        }
    }

    private boolean anyBelow(int bit) {
        if (bit <= 0) {
            return false;
        }
        int li = bit >>> 6;
        if (li > 0 && l0 != 0L) {
            return true;
        }
        if (li > 1 && l1 != 0L) {
            return true;
        }
        if (li > 2 && l2 != 0L) {
            return true;
        }
        if (li > 3 && l3 != 0L) {
            return true;
        }
        if (li > 4 && l4 != 0L) {
            return true;
        }
        if (li > 5 && l5 != 0L) {
            return true;
        }
        if (li > 6 && l6 != 0L) {
            return true;
        }
        if (li > 7 && l7 != 0L) {
            return true;
        }
        if (li > 8 && l8 != 0L) {
            return true;
        }
        if (li > 9 && l9 != 0L) {
            return true;
        }
        if (li > 10 && l10 != 0L) {
            return true;
        }
        if (li > 11 && l11 != 0L) {
            return true;
        }
        if (li > 12 && l12 != 0L) {
            return true;
        }
        if (li > 13 && l13 != 0L) {
            return true;
        }
        if (li > 14 && l14 != 0L) {
            return true;
        }
        if (li > 15 && l15 != 0L) {
            return true;
        }
        if (li > 16 && l16 != 0L) {
            return true;
        }
        if (li > 17 && l17 != 0L) {
            return true;
        }
        if (li > 18 && l18 != 0L) {
            return true;
        }
        if (li > 19 && l19 != 0L) {
            return true;
        }

        if (li >= LIMBS) {
            return false;
        }
        int sh = bit & 63;
        long mask = sh == 0 ? 0L : (1L << sh) - 1L;
        return (limbAt(li) & mask) != 0L;
    }

    private long limbAt(int i) {
        switch (i) {
            case 0:
                return l0;
            case 1:
                return l1;
            case 2:
                return l2;
            case 3:
                return l3;
            case 4:
                return l4;
            case 5:
                return l5;
            case 6:
                return l6;
            case 7:
                return l7;
            case 8:
                return l8;
            case 9:
                return l9;
            case 10:
                return l10;
            case 11:
                return l11;
            case 12:
                return l12;
            case 13:
                return l13;
            case 14:
                return l14;
            case 15:
                return l15;
            case 16:
                return l16;
            case 17:
                return l17;
            case 18:
                return l18;
            case 19:
                return l19;

            default:
                throw new ArrayIndexOutOfBoundsException(i);
        }
    }

    private int bitLength() {
        if (l19 != 0L) {
            return 1280 - Long.numberOfLeadingZeros(l19);
        }
        if (l18 != 0L) {
            return 1216 - Long.numberOfLeadingZeros(l18);
        }
        if (l17 != 0L) {
            return 1152 - Long.numberOfLeadingZeros(l17);
        }
        if (l16 != 0L) {
            return 1088 - Long.numberOfLeadingZeros(l16);
        }
        if (l15 != 0L) {
            return 1024 - Long.numberOfLeadingZeros(l15);
        }
        if (l14 != 0L) {
            return 960 - Long.numberOfLeadingZeros(l14);
        }
        if (l13 != 0L) {
            return 896 - Long.numberOfLeadingZeros(l13);
        }
        if (l12 != 0L) {
            return 832 - Long.numberOfLeadingZeros(l12);
        }
        if (l11 != 0L) {
            return 768 - Long.numberOfLeadingZeros(l11);
        }
        if (l10 != 0L) {
            return 704 - Long.numberOfLeadingZeros(l10);
        }
        if (l9 != 0L) {
            return 640 - Long.numberOfLeadingZeros(l9);
        }
        if (l8 != 0L) {
            return 576 - Long.numberOfLeadingZeros(l8);
        }
        if (l7 != 0L) {
            return 512 - Long.numberOfLeadingZeros(l7);
        }
        if (l6 != 0L) {
            return 448 - Long.numberOfLeadingZeros(l6);
        }
        if (l5 != 0L) {
            return 384 - Long.numberOfLeadingZeros(l5);
        }
        if (l4 != 0L) {
            return 320 - Long.numberOfLeadingZeros(l4);
        }
        if (l3 != 0L) {
            return 256 - Long.numberOfLeadingZeros(l3);
        }
        if (l2 != 0L) {
            return 192 - Long.numberOfLeadingZeros(l2);
        }
        if (l1 != 0L) {
            return 128 - Long.numberOfLeadingZeros(l1);
        }
        if (l0 != 0L) {
            return 64 - Long.numberOfLeadingZeros(l0);
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
