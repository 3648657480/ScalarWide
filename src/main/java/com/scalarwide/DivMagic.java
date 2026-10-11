package com.scalarwide;

/**
 * MC hard-wiring: division by a fixed single-limb divisor via the OI
 * magic-number trick. Precomputes {@code floor(2^64/d)} once; each 64-bit
 * digit division then costs one high-multiply plus a correction instead of a
 * hardware divide.
 *
 * <p>Divisor domain: {@code 1 <= d < 2^32}. Powers of two take the shift
 * path. Correct for all inputs; the constructor rejects out-of-domain
 * divisors.
 */
public final class DivMagic {

    private final long d;
    private final long magic;
    private final int shift;

    public static DivMagic of(long divisor) {
        if (divisor < 1 || divisor >= (1L << 32)) {
            throw new IllegalArgumentException("divisor out of domain [1, 2^32): " + divisor);
        }
        return new DivMagic(divisor);
    }

    public static DivMagic of(Wide256 divisor) {
        if ((divisor.limb(1) | divisor.limb(2) | divisor.limb(3)) != 0) {
            throw new IllegalArgumentException("divisor must fit one 32-bit limb");
        }
        return of(divisor.limb(0));
    }

    private DivMagic(long divisor) {
        this.d = divisor;
        if ((divisor & (divisor - 1)) == 0) {
            this.shift = Long.numberOfTrailingZeros(divisor);
            this.magic = 0;
        } else {
            this.shift = -1;
            this.magic = Long.divideUnsigned(-1L, divisor);
        }
    }

    public long constant() {
        return d;
    }

    /** Unsigned {@code x / d}. */
    public Wide256 div(Wide256 x) {
        if (shift >= 0) {
            return x.ushr(shift);
        }
        long[] q = new long[4];
        long rem = 0;
        for (int i = 3; i >= 0; i--) {
            long xi = x.limb(i);
            long chunk = (rem << 32) | (xi >>> 32);
            long qh = magicQ(chunk);
            rem = chunk - qh * d;
            chunk = (rem << 32) | (xi & 0xFFFFFFFFL);
            long ql = magicQ(chunk);
            rem = chunk - ql * d;
            q[i] = (qh << 32) | ql;
        }
        return Wide256.of(q[0], q[1], q[2], q[3]);
    }

    /** Unsigned {@code x % d}. */
    public Wide256 rem(Wide256 x) {
        if (shift >= 0) {
            return x.and(Wide256.of((1L << shift) - 1, 0, 0, 0));
        }
        long rem = 0;
        for (int i = 3; i >= 0; i--) {
            long xi = x.limb(i);
            long chunk = (rem << 32) | (xi >>> 32);
            long qh = magicQ(chunk);
            rem = chunk - qh * d;
            chunk = (rem << 32) | (xi & 0xFFFFFFFFL);
            long ql = magicQ(chunk);
            rem = chunk - ql * d;
        }
        return Wide256.of(rem, 0, 0, 0);
    }

    private long magicQ(long n) {
        long q = MulHi.high(n, magic);
        long r = n - q * d;
        return Long.compareUnsigned(r, d) >= 0 ? q + 1 : q;
    }
}
