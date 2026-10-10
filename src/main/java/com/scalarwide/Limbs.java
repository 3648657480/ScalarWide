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
     * Unsigned division with remainder (binary shift-subtract; correctness
     * first - a Knuth-D fast path is a future measured round). {@code u} and
     * {@code v} must have the packed width; {@code q} and {@code r} receive
     * quotient and remainder.
     *
     * @throws ArithmeticException if {@code v} is zero
     */
    static void divmod(long[] u, long[] v, long[] q, long[] r) {
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
