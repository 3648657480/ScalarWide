package com.scalarwide;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.math.BigInteger;
import java.nio.ByteOrder;

final class Limbs {

    /**
     * Big-endian 64-bit view over a {@code byte[]}: one accessor call decodes a whole
     * limb instead of eight byte loads with shifting and a runtime index. The call is
     * signature-polymorphic, so the cast to {@code long} does not box.
     */
    static final VarHandle BE_LONG =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

    /** Little-endian counterpart of {@link #BE_LONG}. */
    static final VarHandle LE_LONG =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    private Limbs() {
    }

    static long addCarry(long a, long b, long r) {
        return Carry.carryOut(a, r);
    }

    static long subBorrow(long a, long b, long r) {
        return Carry.borrowOut(a, r);
    }

    static void add(long[] a, long[] b, long[] out) {
        long c = 0;
        for (int i = 0; i < a.length; i++) {
            long s = a[i] + b[i];
            long c1 = addCarry(a[i], b[i], s);
            long r = s + c;
            long c2 = addCarry(s, c, r);
            out[i] = r;
            c = c1 + c2;
        }
    }

    static void sub(long[] a, long[] b, long[] out) {
        long br = 0;
        for (int i = 0; i < a.length; i++) {
            long d = a[i] - b[i];
            long b1 = subBorrow(a[i], b[i], d);
            long r = d - br;
            long b2 = subBorrow(d, br, r);
            out[i] = r;
            br = b1 + b2;
        }
    }

    static void neg(long[] a, long[] out) {
        long c = 1;
        for (int i = 0; i < a.length; i++) {
            long r = ~a[i] + c;
            c = addCarry(~a[i], c, r);
            out[i] = r;
        }
    }

    static void and(long[] a, long[] b, long[] out) {
        for (int i = 0; i < a.length; i++) {
            out[i] = a[i] & b[i];
        }
    }

    static void or(long[] a, long[] b, long[] out) {
        for (int i = 0; i < a.length; i++) {
            out[i] = a[i] | b[i];
        }
    }

    static void xor(long[] a, long[] b, long[] out) {
        for (int i = 0; i < a.length; i++) {
            out[i] = a[i] ^ b[i];
        }
    }

    static void not(long[] a, long[] out) {
        for (int i = 0; i < a.length; i++) {
            out[i] = ~a[i];
        }
    }

    static void andNot(long[] a, long[] b, long[] out) {
        for (int i = 0; i < a.length; i++) {
            out[i] = a[i] & ~b[i];
        }
    }

    static void shl(long[] a, int k, long[] out) {
        int limbShift = k >>> 6;
        int bitShift = k & 63;
        for (int i = a.length - 1; i >= 0; i--) {
            int src = i - limbShift;
            long v = 0;
            if (src >= 0) {
                v = a[src] << bitShift;
                if (bitShift != 0 && src - 1 >= 0) {
                    v |= a[src - 1] >>> (64 - bitShift);
                }
            }
            out[i] = v;
        }
    }

    static void ushr(long[] a, int k, long[] out) {
        int limbShift = k >>> 6;
        int bitShift = k & 63;
        for (int i = 0; i < a.length; i++) {
            int src = i + limbShift;
            long v = 0;
            if (src < a.length) {
                v = a[src] >>> bitShift;
                if (bitShift != 0 && src + 1 < a.length) {
                    v |= a[src + 1] << (64 - bitShift);
                }
            }
            out[i] = v;
        }
    }

    static void shr(long[] a, int k, long[] out) {
        int limbShift = k >>> 6;
        int bitShift = k & 63;
        long fill = a[a.length - 1] >> 63;
        for (int i = 0; i < a.length; i++) {
            int src = i + limbShift;
            long lo = (src < a.length) ? (a[src] >>> bitShift) : fill;
            long hi = 0;
            if (bitShift != 0) {
                long next = (src + 1 < a.length) ? a[src + 1] : fill;
                hi = next << (64 - bitShift);
            }
            out[i] = lo | hi;
        }
    }

    static int compareUnsigned(long[] a, long[] b) {
        for (int i = a.length - 1; i >= 0; i--) {
            if (a[i] != b[i]) {
                return Long.compareUnsigned(a[i], b[i]);
            }
        }
        return 0;
    }

    static int compareSigned(long[] a, long[] b) {
        int top = a.length - 1;
        if (a[top] != b[top]) {
            return a[top] < b[top] ? -1 : 1;
        }
        for (int i = top - 1; i >= 0; i--) {
            if (a[i] != b[i]) {
                return Long.compareUnsigned(a[i], b[i]);
            }
        }
        return 0;
    }

    static int bitLength(long[] a) {
        for (int i = a.length - 1; i >= 0; i--) {
            if (a[i] != 0) {
                return i * 64 + (64 - Long.numberOfLeadingZeros(a[i]));
            }
        }
        return 0;
    }

    static long[] mul(long[] a, long[] b) {
        int n = a.length;
        int m = b.length;
        long[] acc = new long[n + m];
        for (int i = 0; i < n; i++) {
            long ai = a[i];
            for (int j = 0; j < m; j++) {
                long lo = ai * b[j];
                long hi = MulHi.high(ai, b[j]);
                addAt(acc, i + j, lo);
                addAt(acc, i + j + 1, hi);
            }
        }
        return acc;
    }

    /**
     * Unsigned division with remainder, Knuth Algorithm D (schoolbook,
     * 64-bit digits). {@code u} and {@code v} must have the packed width;
     * {@code q} and {@code r} receive quotient and remainder. Used by the
     * public API; {@link #divmodBinary} is the simple correctness oracle.
     *
     * @throws ArithmeticException if {@code v} is zero
     */
    static void divmod(long[] u, long[] v, long[] q, long[] r) {
        int n = v.length;
        while (n > 0 && v[n - 1] == 0) {
            n--;
        }
        if (n == 0) {
            throw new ArithmeticException("division by zero");
        }
        java.util.Arrays.fill(q, 0L);
        if (compareUnsigned(u, v) < 0) {
            if (r != null) {
                System.arraycopy(u, 0, r, 0, r.length);
            }
            return;
        }
        if (r != null) {
            java.util.Arrays.fill(r, 0L);
        }
        int uz = u.length;
        while (uz > 0 && u[uz - 1] == 0) {
            uz--;
        }
        if (n == 1) {
            divmodSingle(u, uz, v[0], q, r);
            return;
        }
        int s = Long.numberOfLeadingZeros(v[n - 1]);
        long[] vn = new long[n];
        if (s == 0) {
            System.arraycopy(v, 0, vn, 0, n);
        } else {
            for (int i = n - 1; i > 0; i--) {
                vn[i] = (v[i] << s) | (v[i - 1] >>> (64 - s));
            }
            vn[0] = v[0] << s;
        }
        long[] un = new long[uz + 1];
        if (s == 0) {
            System.arraycopy(u, 0, un, 0, uz);
        } else {
            un[uz] = u[uz - 1] >>> (64 - s);
            for (int i = uz - 1; i > 0; i--) {
                un[i] = (u[i] << s) | (u[i - 1] >>> (64 - s));
            }
            un[0] = u[0] << s;
        }
        for (int j = uz - n; j >= 0; j--) {
            long u2 = un[j + n];
            long qhat;
            if (u2 == vn[n - 1]) {
                qhat = -1L;
                long rhat = un[j + n - 1] + vn[n - 1];
                if (Long.compareUnsigned(rhat, un[j + n - 1]) >= 0) {
                    qhat = correctQhat(qhat, rhat, vn, un, j, n);
                }
            } else {
                qhat = divQ(u2, un[j + n - 1], vn[n - 1]);
                long rhat = un[j + n - 1] - qhat * vn[n - 1];
                qhat = correctQhat(qhat, rhat, vn, un, j, n);
            }
            long borrow = 0;
            for (int i = 0; i < n; i++) {
                long phi = MulHi.high(qhat, vn[i]);
                long plo = qhat * vn[i];
                long plo2 = plo + borrow;
                long phi2 = phi + (Long.compareUnsigned(plo2, plo) < 0 ? 1 : 0);
                long t = un[j + i] - plo2;
                borrow = phi2 + (Long.compareUnsigned(t, un[j + i]) > 0 ? 1 : 0);
                un[j + i] = t;
            }
            long t = un[j + n] - borrow;
            boolean finalBorrow = Long.compareUnsigned(t, un[j + n]) > 0;
            un[j + n] = t;
            if (finalBorrow) {
                qhat--;
                long c = 0;
                for (int i = 0; i < n; i++) {
                    long sum = un[j + i] + vn[i];
                    long c1 = Long.compareUnsigned(sum, un[j + i]) < 0 ? 1 : 0;
                    long sum2 = sum + c;
                    long c2 = Long.compareUnsigned(sum2, sum) < 0 ? 1 : 0;
                    un[j + i] = sum2;
                    c = c1 + c2;
                }
                un[j + n] += c;
            }
            q[j] = qhat;
        }
        if (r != null) {
            if (s == 0) {
                System.arraycopy(un, 0, r, 0, n);
            } else {
                for (int i = 0; i < n - 1; i++) {
                    r[i] = (un[i] >>> s) | (un[i + 1] << (64 - s));
                }
                r[n - 1] = un[n - 1] >>> s;
            }
        }
    }

    /**
     * Corrects an overestimated quotient digit (D3): while
     * {@code qhat*v[n-2] > rhat*B + u[j+n-2]}, decrement.
     */
    private static long correctQhat(long qhat, long rhat, long[] vn, long[] un, int j, int n) {
        while (true) {
            long lo = qhat * vn[n - 2];
            long hi = MulHi.high(qhat, vn[n - 2]);
            if (Long.compareUnsigned(hi, rhat) > 0
                    || (hi == rhat && Long.compareUnsigned(lo, un[j + n - 2]) > 0)) {
                qhat--;
                long old = rhat;
                rhat += vn[n - 1];
                if (Long.compareUnsigned(rhat, old) < 0) {
                    break;
                }
            } else {
                break;
            }
        }
        return qhat;
    }

    /**
     * Unsigned 128/64 quotient; requires {@code u1 < v} and {@code v >= 2^63}
     * (normalized), so the quotient fits 64 bits and the two 32-bit estimate
     * halves are well-defined. Follows Hacker's Delight {@code divlu2} with
     * the input already normalized (shift 0).
     */
    private static long divQ(long u1, long u0, long v) {
        final long b = 1L << 32;
        long vn1 = v >>> 32;
        long vn0 = v & 0xFFFFFFFFL;
        long q1 = Long.divideUnsigned(u1, vn1);
        long rhat = u1 - q1 * vn1;
        while (Long.compareUnsigned(q1, b) >= 0
                || Long.compareUnsigned(q1 * vn0, b * rhat + (u0 >>> 32)) > 0) {
            q1--;
            rhat += vn1;
            if (Long.compareUnsigned(rhat, b) >= 0) {
                break;
            }
        }
        long un21 = u1 * b + (u0 >>> 32) - q1 * v;
        long q0 = Long.divideUnsigned(un21, vn1);
        rhat = un21 - q0 * vn1;
        while (Long.compareUnsigned(q0, b) >= 0
                || Long.compareUnsigned(q0 * vn0, b * rhat + (u0 & 0xFFFFFFFFL)) > 0) {
            q0--;
            rhat += vn1;
            if (Long.compareUnsigned(rhat, b) >= 0) {
                break;
            }
        }
        return q1 * b + q0;
    }

    private static void divmodSingle(long[] u, int uz, long d, long[] q, long[] r) {
        if (Long.compareUnsigned(d, 1L << 32) < 0) {
            long rem = 0;
            for (int i = uz - 1; i >= 0; i--) {
                long chunk = (rem << 32) | (u[i] >>> 32);
                long qh = Long.divideUnsigned(chunk, d);
                rem = chunk - qh * d;
                chunk = (rem << 32) | (u[i] & 0xFFFFFFFFL);
                long ql = Long.divideUnsigned(chunk, d);
                rem = chunk - ql * d;
                q[i] = (qh << 32) | ql;
            }
            if (r != null) {
                r[0] = rem;
            }
            return;
        }
        int s = Long.numberOfLeadingZeros(d);
        if (s == 0) {
            long rem = 0;
            for (int i = uz - 1; i >= 0; i--) {
                long qd = divQ(rem, u[i], d);
                rem = u[i] - qd * d;
                q[i] = qd;
            }
            if (r != null) {
                r[0] = rem;
            }
            return;
        }
        long dn = d << s;
        long[] un = new long[uz + 1];
        un[uz] = u[uz - 1] >>> (64 - s);
        for (int i = uz - 1; i > 0; i--) {
            un[i] = (u[i] << s) | (u[i - 1] >>> (64 - s));
        }
        un[0] = u[0] << s;
        long rem = 0;
        for (int i = uz; i >= 0; i--) {
            long qd = divQ(rem, un[i], dn);
            rem = un[i] - qd * dn;
            if (i < uz) {
                q[i] = qd;
            }
        }
        if (r != null) {
            r[0] = rem >>> s;
        }
    }

    /**
     * Unsigned division with remainder (binary shift-subtract; simple and
     * obviously correct, O(bits^2)). Kept as the internal oracle for
     * {@link #divmod}.
     *
     * @throws ArithmeticException if {@code v} is zero
     */
    static void divmodBinary(long[] u, long[] v, long[] q, long[] r) {
        int top = v.length;
        while (top > 0 && v[top - 1] == 0) {
            top--;
        }
        if (top == 0) {
            throw new ArithmeticException("division by zero");
        }
        java.util.Arrays.fill(q, 0L);
        if (compareUnsigned(u, v) < 0) {
            System.arraycopy(u, 0, r, 0, r.length);
            return;
        }
        java.util.Arrays.fill(r, 0L);
        for (int i = bitLength(u) - 1; i >= 0; i--) {
            shl(r, 1, r);
            r[0] |= (u[i >>> 6] >>> (i & 63)) & 1L;
            if (compareUnsigned(r, v) >= 0) {
                sub(r, v, r);
                q[i >>> 6] |= 1L << (i & 63);
            }
        }
    }

    private static void addAt(long[] acc, int idx, long v) {
        long s = acc[idx] + v;
        long c = addCarry(acc[idx], v, s);
        acc[idx] = s;
        int i = idx + 1;
        while (c != 0 && i < acc.length) {
            long s2 = acc[i] + c;
            c = addCarry(acc[i], c, s2);
            acc[i] = s2;
            i++;
        }
    }

    static void mulLow(long[] a, long[] b, long[] out) {
        long[] full = mul(a, b);
        System.arraycopy(full, 0, out, 0, out.length);
    }

    static void mulHigh(long[] a, long[] b, long[] out) {
        long[] full = mul(a, b);
        System.arraycopy(full, a.length, out, 0, out.length);
    }

    /**
     * Unsigned hexadecimal representation of {@code a} (little-endian limbs), no prefix,
     * {@code "0"} for zero. Base 16 is four bits per digit, so the limbs are converted
     * in place — routing through {@link BigInteger} costs ~4x more.
     */
    static String toHexString(long[] a) {
        int top = a.length - 1;
        while (top >= 0 && a[top] == 0) {
            top--;
        }
        if (top < 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder((top + 1) * 16);
        sb.append(Long.toHexString(a[top]));
        for (int i = top - 1; i >= 0; i--) {
            String s = Long.toHexString(a[i]);
            for (int pad = s.length(); pad < 16; pad++) {
                sb.append('0');
            }
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * Decimal chunk base: nineteen digits — the largest power of ten that fits a {@code long} and is
     * <em>already normalised</em> (top bit set), which the reciprocal division below requires. That
     * choice removes the pre-shift of the dividend entirely; shifting once only works for the first
     * pass, because the quotient returned by each division is back in unshifted space.
     *
     * <p>{@code 10^19} is above {@link Long#MAX_VALUE}, so it cannot be written as a decimal literal.
     */
    static final long DEC_BASE = -8_446_744_073_709_551_616L;

    /**
     * Reciprocal of {@link #DEC_BASE}: {@code floor((2^128 - 1) / base) - 2^64}. Computed once at
     * class-init with {@link BigInteger}; it replaces a hardware 128/64 divide (20-30 cycles) with a
     * multiply-high plus two fixups.
     *
     * <p>Both the base and the reciprocal exceed {@link Long#MAX_VALUE} and therefore read as
     * negative; they are only used as unsigned operands.
     */
    private static final long DEC_RECIP = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)
            .divide(BigInteger.valueOf(DEC_BASE & Long.MAX_VALUE).setBit(63))
            .subtract(BigInteger.ONE.shiftLeft(64))
            .longValue();

    /** Digits per chunk. */
    private static final int DEC_DIGITS = 19;

    /**
     * Unsigned decimal representation of {@code a} (little-endian limbs), {@code "0"} for zero.
     *
     * <p>Nineteen-digit chunks are peeled off with a reciprocal 128/64 division (Möller-Granlund),
     * so the conversion never issues a hardware divide and never touches {@link BigInteger}.
     */
    static String toDecimalString(long[] a) {
        int top = a.length - 1;
        while (top >= 0 && a[top] == 0) {
            top--;
        }
        if (top < 0) {
            return "0";
        }
        int n = top + 1;
        long[] mag = new long[n];
        System.arraycopy(a, 0, mag, 0, n);

        long[] chunks = new long[(n * 64) / 63 + 2];
        int nc = 0;
        while (true) {
            long rem = 0;
            boolean quotientNonZero = false;
            for (int i = n - 1; i >= 0; i--) {
                long nh = rem;
                long nl = mag[i];
                // Möller-Granlund 3/2 division: (qh:ql) = nh * recip, then (qh:ql) += (nh+1 : nl).
                long qh = MulHi.high(nh, DEC_RECIP);
                long ql = nh * DEC_RECIP;
                long lo = ql + nl;
                long hi = qh + nh + 1 + (Long.compareUnsigned(lo, ql) < 0 ? 1 : 0);
                long r = nl - hi * DEC_BASE;
                if (Long.compareUnsigned(r, lo) > 0) {
                    hi--;
                    r += DEC_BASE;
                }
                if (Long.compareUnsigned(r, DEC_BASE) >= 0) {
                    r -= DEC_BASE;
                    hi++;
                }
                mag[i] = hi;
                rem = r;
                if (hi != 0) {
                    quotientNonZero = true;
                }
            }
            chunks[nc++] = rem;
            if (!quotientNonZero) {
                break;
            }
        }

        StringBuilder sb = new StringBuilder(nc * DEC_DIGITS);
        sb.append(Long.toUnsignedString(chunks[nc - 1]));
        for (int i = nc - 2; i >= 0; i--) {
            String s = Long.toUnsignedString(chunks[i]);
            for (int pad = s.length(); pad < DEC_DIGITS; pad++) {
                sb.append('0');
            }
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * Unsigned value of {@code a} (little-endian limbs) as a non-negative
     * {@link BigInteger}. Single pass: the limb vector is laid out big-endian
     * once and handed to {@link BigInteger#BigInteger(int, byte[])}; signum 1
     * keeps leading zero bytes out of the magnitude.
     */
    static BigInteger toBigInteger(long[] a) {
        byte[] be = new byte[a.length * 8];
        int pos = 0;
        for (int i = a.length - 1; i >= 0; i--) {
            long v = a[i];
            be[pos] = (byte) (v >>> 56);
            be[pos + 1] = (byte) (v >>> 48);
            be[pos + 2] = (byte) (v >>> 40);
            be[pos + 3] = (byte) (v >>> 32);
            be[pos + 4] = (byte) (v >>> 24);
            be[pos + 5] = (byte) (v >>> 16);
            be[pos + 6] = (byte) (v >>> 8);
            be[pos + 7] = (byte) v;
            pos += 8;
        }
        return new BigInteger(1, be);
    }
}
