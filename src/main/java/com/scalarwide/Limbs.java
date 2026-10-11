package com.scalarwide;

import java.math.BigInteger;

final class Limbs {

    private static final BigInteger TWO64 = BigInteger.ONE.shiftLeft(64);

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
                long hi = Math.unsignedMultiplyHigh(ai, b[j]);
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
                long phi = Math.unsignedMultiplyHigh(qhat, vn[i]);
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
            long hi = Math.unsignedMultiplyHigh(qhat, vn[n - 2]);
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

    static BigInteger toBigInteger(long[] a) {
        BigInteger v = BigInteger.ZERO;
        for (int i = a.length - 1; i >= 0; i--) {
            BigInteger limb = BigInteger.valueOf(a[i]);
            if (a[i] < 0) {
                limb = limb.add(TWO64);
            }
            v = v.shiftLeft(64).or(limb);
        }
        return v;
    }
}
