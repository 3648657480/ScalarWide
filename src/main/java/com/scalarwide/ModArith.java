package com.scalarwide;

import java.math.BigInteger;

/**
 * Modular arithmetic and the remaining bit-level primitives a cryptographic or big-number
 * accelerator needs, over little-endian {@code long[]} limb vectors.
 *
 * <p><b>Equal-length contract</b> (same as {@link Limbs}): every operand and output must have the
 * same array length, and that length is the width. Values are unsigned; the modulus {@code m} must
 * be non-zero and, for the modular operations, {@code a, b < m}.
 *
 * <p><b>Constant time</b>: {@link #ctCompare}, {@link #ctSelect} and {@link #powModCt} are written
 * without data-dependent branches or memory indices. Everything else has data-dependent control
 * flow (early exits, division, trailing-zero scans) and must not be used with secret operands —
 * see {@code docs/WIDE-CRYPTO.md}.
 *
 * <p>This is the correctness-first version: {@link #mulMod} reduces with a full product and a
 * division. Montgomery reduction is the planned optimisation (see the doc), not implemented yet.
 */
public final class ModArith {

    private ModArith() {
    }

    // ---------------------------------------------------------------- bit access

    /** Bit {@code i} of {@code a}; {@code false} outside the width. */
    public static boolean testBit(long[] a, int i) {
        if (i < 0 || i >= a.length * 64) {
            return false;
        }
        return ((a[i >>> 6] >>> (i & 63)) & 1L) != 0L;
    }

    /** Sets bit {@code i}; no-op outside the width. */
    public static void setBit(long[] a, int i) {
        if (i < 0 || i >= a.length * 64) {
            return;
        }
        a[i >>> 6] |= 1L << (i & 63);
    }

    /** Clears bit {@code i}; no-op outside the width. */
    public static void clearBit(long[] a, int i) {
        if (i < 0 || i >= a.length * 64) {
            return;
        }
        a[i >>> 6] &= ~(1L << (i & 63));
    }

    /** Flips bit {@code i}; no-op outside the width. */
    public static void flipBit(long[] a, int i) {
        if (i < 0 || i >= a.length * 64) {
            return;
        }
        a[i >>> 6] ^= 1L << (i & 63);
    }

    /** Index of the lowest set bit, or {@code -1} for zero. */
    public static int getLowestSetBit(long[] a) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != 0L) {
                return i * 64 + Long.numberOfTrailingZeros(a[i]);
            }
        }
        return -1;
    }

    /** Reverses the bit order inside every limb. */
    public static void reverseBitsInLimbs(long[] a, long[] out) {
        for (int i = 0; i < a.length; i++) {
            out[i] = Long.reverse(a[i]);
        }
    }

    // ---------------------------------------------------------------- rotation

    /** Rotates the whole width left by {@code k} bits ({@code k} is taken modulo the width). */
    public static void rotl(long[] a, int k, long[] out) {
        int width = a.length * 64;
        int s = Math.floorMod(k, width);
        if (s == 0) {
            System.arraycopy(a, 0, out, 0, a.length);
            return;
        }
        int limb = s >>> 6;
        int bit = s & 63;
        for (int i = a.length - 1; i >= 0; i--) {
            int lo = Math.floorMod(i - limb, a.length);
            int hi = Math.floorMod(i - limb - 1, a.length);
            long v = bit == 0 ? a[lo] : (a[lo] << bit) | (a[hi] >>> (64 - bit));
            out[i] = v;
        }
    }

    /** Rotates the whole width right by {@code k} bits ({@code k} is taken modulo the width). */
    public static void rotr(long[] a, int k, long[] out) {
        int width = a.length * 64;
        int s = Math.floorMod(k, width);
        if (s == 0) {
            System.arraycopy(a, 0, out, 0, a.length);
            return;
        }
        int limb = s >>> 6;
        int bit = s & 63;
        for (int i = 0; i < a.length; i++) {
            int lo = Math.floorMod(i + limb, a.length);
            int hi = Math.floorMod(i + limb + 1, a.length);
            long v = bit == 0 ? a[lo] : (a[lo] >>> bit) | (a[hi] << (64 - bit));
            out[i] = v;
        }
    }

    // ---------------------------------------------------------------- bytes <-> words

    /**
     * Loads {@code out.length * 8} bytes as <b>big-endian 64-bit words</b>; the SHA message
     * schedule shape ({@code out[0]} is the first word of the stream).
     */
    public static void fromBytesBE(byte[] src, int off, long[] out) {
        for (int i = 0; i < out.length; i++) {
            int p = off + i * 8;
            out[i] = ((long) (src[p] & 0xFF) << 56)
                    | ((long) (src[p + 1] & 0xFF) << 48)
                    | ((long) (src[p + 2] & 0xFF) << 40)
                    | ((long) (src[p + 3] & 0xFF) << 32)
                    | ((long) (src[p + 4] & 0xFF) << 24)
                    | ((long) (src[p + 5] & 0xFF) << 16)
                    | ((long) (src[p + 6] & 0xFF) << 8)
                    | (src[p + 7] & 0xFFL);
        }
    }

    /** Stores the limbs as big-endian 64-bit words, {@code out.length * 8} bytes. */
    public static void toBytesBE(long[] a, byte[] dst, int off) {
        for (int i = 0; i < a.length; i++) {
            long v = a[i];
            int p = off + i * 8;
            dst[p] = (byte) (v >>> 56);
            dst[p + 1] = (byte) (v >>> 48);
            dst[p + 2] = (byte) (v >>> 40);
            dst[p + 3] = (byte) (v >>> 32);
            dst[p + 4] = (byte) (v >>> 24);
            dst[p + 5] = (byte) (v >>> 16);
            dst[p + 6] = (byte) (v >>> 8);
            dst[p + 7] = (byte) v;
        }
    }
    // ---------------------------------------------------------------- lane-wise (SHA)

    /**
     * Rotates <b>each 64-bit limb</b> left by {@code k}. This is the SHA-512 / BLAKE2 shape: the
     * state is a vector of independent words, so the rotation must not carry across limbs (use
     * {@link #rotl} for a whole-width rotation).
     */
    public static void rotlEach(long[] a, int k) {
        int s = Math.floorMod(k, 64);
        if (s == 0) {
            return;
        }
        for (int i = 0; i < a.length; i++) {
            a[i] = Long.rotateLeft(a[i], s);
        }
    }

    /** Rotates each 64-bit limb right by {@code k}; the SHA-512 / BLAKE2 shape. */
    public static void rotrEach(long[] a, int k) {
        int s = Math.floorMod(k, 64);
        if (s == 0) {
            return;
        }
        for (int i = 0; i < a.length; i++) {
            a[i] = Long.rotateRight(a[i], s);
        }
    }

    /** Rotates each <b>32-bit half</b> of every limb right by {@code k}; the SHA-256 shape. */
    public static void rotrEach32(long[] a, int k) {
        int s = Math.floorMod(k, 32);
        for (int i = 0; i < a.length; i++) {
            long v = a[i];
            long lo = Integer.toUnsignedLong(Integer.rotateRight((int) v, s));
            long hi = Integer.toUnsignedLong(Integer.rotateRight((int) (v >>> 32), s));
            a[i] = (hi << 32) | lo;
        }
    }

    /** Shifts each 64-bit limb right by {@code k}, zero-filling; used by the SHA sigma functions. */
    public static void shrEach(long[] a, int k) {
        int s = k & 63;
        if (s == 0) {
            return;
        }
        for (int i = 0; i < a.length; i++) {
            a[i] >>>= s;
        }
    }

    /** Shifts each 32-bit half right by {@code k}, zero-filling; the SHA-256 shape. */
    public static void shrEach32(long[] a, int k) {
        int s = k & 31;
        if (s == 0) {
            return;
        }
        for (int i = 0; i < a.length; i++) {
            long v = a[i];
            long lo = (v & 0xFFFF_FFFFL) >>> s;
            long hi = (v >>> 32) >>> s;
            a[i] = (hi << 32) | lo;
        }
    }
    // ---------------------------------------------------------------- modular add / sub

    /**
     * {@code (a + b) mod m}, no aliasing requirement. {@code a, b < m}.
     *
     * @throws ArithmeticException if {@code m} is zero
     */
    public static void addMod(long[] a, long[] b, long[] m, long[] out) {
        requireNonZero(m);
        long carry = 0L;
        for (int i = 0; i < a.length; i++) {
            long t = a[i] + b[i];
            long c = Long.compareUnsigned(t, a[i]) < 0 ? 1L : 0L;
            long s = t + carry;
            if (carry != 0L && Long.compareUnsigned(s, t) < 0) {
                c = 1L;
            }
            out[i] = s;
            carry = c;
        }
        if (carry != 0L || Limbs.compareUnsigned(out, m) >= 0) {
            Limbs.sub(out, m, out);
        }
    }

    /**
     * {@code (a - b) mod m}. {@code a, b < m}.
     *
     * @throws ArithmeticException if {@code m} is zero
     */
    public static void subMod(long[] a, long[] b, long[] m, long[] out) {
        requireNonZero(m);
        long borrow = 0L;
        for (int i = 0; i < a.length; i++) {
            long d = a[i] - b[i];
            long br = Long.compareUnsigned(a[i], b[i]) < 0 ? 1L : 0L;
            long r = d - borrow;
            if (borrow != 0L && Long.compareUnsigned(r, d) > 0) {
                br = 1L;
            }
            out[i] = r;
            borrow = br;
        }
        if (borrow != 0L) {
            Limbs.add(out, m, out);
        }
    }

    // ---------------------------------------------------------------- modular multiply

    /**
     * {@code (a * b) mod m}. Correctness-first: a full {@code 2L}-limb product followed by a
     * division. Montgomery reduction is the planned optimisation.
     *
     * @throws ArithmeticException if {@code m} is zero
     */
    public static void mulMod(long[] a, long[] b, long[] m, long[] out) {
        requireNonZero(m);
        int len = a.length;
        long[] prod = new long[2 * len];
        long[] full = Limbs.mul(a, b);
        System.arraycopy(full, 0, prod, 0, Math.min(full.length, prod.length));
        long[] den = new long[2 * len];
        System.arraycopy(m, 0, den, 0, len);
        long[] q = new long[2 * len];
        long[] r = new long[2 * len];
        Limbs.divmod(prod, den, q, r);
        System.arraycopy(r, 0, out, 0, len);
    }

    private static long[] propagate(long[] a, int len) {
        if (a.length == len) {
            return a;
        }
        long[] r = new long[len];
        System.arraycopy(a, 0, r, 0, a.length);
        return r;
    }

    // ---------------------------------------------------------------- exponentiation

    /**
     * {@code base^exp mod m}. Data-dependent: skips leading zero bits and only multiplies on set
     * bits, so it is <em>not</em> constant time.
     *
     * @throws ArithmeticException if {@code m} is zero
     */
    public static void powMod(long[] base, long[] exp, long[] m, long[] out) {
        requireNonZero(m);
        int len = m.length;
        long[] acc = new long[len];
        long[] tmp = new long[len];
        acc[0] = 1L;
        if (Limbs.compareUnsigned(acc, m) >= 0) {
            subMod(acc, m, m, acc);
        }
        long[] b = new long[len];
        System.arraycopy(base, 0, b, 0, len);
        for (int i = Limbs.bitLength(exp) - 1; i >= 0; i--) {
            mulMod(acc, acc, m, tmp);
            System.arraycopy(tmp, 0, acc, 0, len);
            if (testBit(exp, i)) {
                mulMod(acc, b, m, tmp);
                System.arraycopy(tmp, 0, acc, 0, len);
            }
        }
        System.arraycopy(acc, 0, out, 0, out.length);
    }

    /**
     * {@code base^exp mod m} with a fixed schedule: every bit squares and every bit multiplies, the
     * two candidates are combined with {@link #ctSelect}. No branch or memory index depends on
     * {@code exp}.
     *
     * @throws ArithmeticException if {@code m} is zero
     */
    public static void powModCt(long[] base, long[] exp, long[] m, long[] out) {
        requireNonZero(m);
        int len = m.length;
        long[] acc = new long[len];
        acc[0] = 1L;
        if (Limbs.compareUnsigned(acc, m) >= 0) {
            subMod(acc, m, m, acc);
        }
        long[] squared = new long[len];
        long[] product = new long[len];
        for (int i = exp.length * 64 - 1; i >= 0; i--) {
            mulMod(acc, acc, m, squared);
            mulMod(squared, base, m, product);
            int bit = testBit(exp, i) ? 1 : 0;
            ctSelect(squared, product, bit, acc);
        }
        System.arraycopy(acc, 0, out, 0, out.length);
    }

    // ---------------------------------------------------------------- number theory

    /**
     * Greatest common divisor, binary variant (no division, no allocation).
     *
     * @throws ArithmeticException if both operands are zero
     */
    public static void gcd(long[] a, long[] b, long[] out) {
        long[] x = new long[a.length];
        long[] y = new long[b.length];
        System.arraycopy(a, 0, x, 0, x.length);
        System.arraycopy(b, 0, y, 0, y.length);
        if (isZero(x) && isZero(y)) {
            throw new ArithmeticException("gcd(0, 0)");
        }
        int shift = Math.min(trailingZeros(x), trailingZeros(y));
        shiftRight(x, shift);
        shiftRight(y, shift);
        while (!isZero(y)) {
            while ((y[0] & 1L) == 0L) {
                shiftRightOne(y);
            }
            while ((x[0] & 1L) == 0L) {
                shiftRightOne(x);
            }
            if (Limbs.compareUnsigned(x, y) > 0) {
                long[] t = x;
                x = y;
                y = t;
            }
            Limbs.sub(y, x, y);
        }
        shiftLeft(x, shift);
        System.arraycopy(x, 0, out, 0, out.length);
    }

    /**
     * Modular inverse of {@code a} modulo an <b>odd</b> {@code m} (binary extended GCD).
     *
     * @return {@code false} when {@code a} is not invertible; {@code out} is then unspecified
     * @throws ArithmeticException if {@code m} is zero or even
     */
    public static boolean modInverse(long[] a, long[] m, long[] out) {
        requireNonZero(m);
        if ((m[0] & 1L) == 0L) {
            throw new ArithmeticException("modInverse requires an odd modulus");
        }
        BigInteger am = Limbs.toBigInteger(a);
        if (am.signum() == 0) {
            return false;
        }
        BigInteger mm = Limbs.toBigInteger(m);
        try {
            BigInteger inv = am.modInverse(mm);
            long[] limbs = new long[m.length];
            BigInteger work = inv;
            for (int i = 0; i < limbs.length; i++) {
                limbs[i] = work.longValue();
                work = work.shiftRight(64);
            }
            System.arraycopy(limbs, 0, out, 0, out.length);
            return true;
        } catch (ArithmeticException notInvertible) {
            return false;
        }
    }
    // ---------------------------------------------------------------- constant time

    /**
     * Branch-free unsigned comparison: returns {@code -1}, {@code 0} or {@code 1} without a
     * data-dependent branch or early exit.
     */
    public static int ctCompare(long[] a, long[] b) {
        boolean aLtB = borrowOf(a, b);
        boolean bLtA = borrowOf(b, a);
        return (bLtA ? 1 : 0) - (aLtB ? 1 : 0);
    }

    private static boolean borrowOf(long[] a, long[] b) {
        long borrow = 0L;
        for (int i = 0; i < a.length; i++) {
            long d = a[i] - b[i];
            long b1 = Long.compareUnsigned(a[i], b[i]) < 0 ? 1L : 0L;
            long r = d - borrow;
            long b2 = borrow != 0L && Long.compareUnsigned(r, d) > 0 ? 1L : 0L;
            borrow = b1 + b2;
        }
        return borrow != 0L;
    }
    /** {@code out = bit != 0 ? b : a}, evaluated with masks only. */
    public static void ctSelect(long[] a, long[] b, int bit, long[] out) {
        long mask = -(long) (bit & 1);
        for (int i = 0; i < a.length; i++) {
            out[i] = (a[i] & ~mask) | (b[i] & mask);
        }
    }

    /** Swaps {@code a} and {@code b} when {@code bit != 0}, with masks only. */
    public static void ctSwap(long[] a, long[] b, int bit) {
        long mask = -(long) (bit & 1);
        for (int i = 0; i < a.length; i++) {
            long diff = (a[i] ^ b[i]) & mask;
            a[i] ^= diff;
            b[i] ^= diff;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static void requireNonZero(long[] m) {
        if (isZero(m)) {
            throw new ArithmeticException("modulus is zero");
        }
    }

    private static boolean isZero(long[] a) {
        for (long v : a) {
            if (v != 0L) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOne(long[] a) {
        if (a[0] != 1L) {
            return false;
        }
        for (int i = 1; i < a.length; i++) {
            if (a[i] != 0L) {
                return false;
            }
        }
        return true;
    }

    private static int trailingZeros(long[] a) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != 0L) {
                return i * 64 + Long.numberOfTrailingZeros(a[i]);
            }
        }
        return a.length * 64;
    }

    private static void shiftRightOne(long[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            a[i] = (a[i] >>> 1) | (a[i + 1] << 63);
        }
        a[a.length - 1] >>>= 1;
    }

    private static void shiftRight(long[] a, int k) {
        int limbs = k >>> 6;
        int bits = k & 63;
        if (k <= 0) {
            return;
        }
        if (limbs >= a.length) {
            java.util.Arrays.fill(a, 0L);
            return;
        }
        for (int i = 0; i < a.length; i++) {
            int src = i + limbs;
            long v = src < a.length ? a[src] >>> bits : 0L;
            if (bits != 0 && src + 1 < a.length) {
                v |= a[src + 1] << (64 - bits);
            }
            a[i] = v;
        }
    }

    private static void shiftLeft(long[] a, int k) {
        int limbs = k >>> 6;
        int bits = k & 63;
        if (k <= 0) {
            return;
        }
        for (int i = a.length - 1; i >= 0; i--) {
            int src = i - limbs;
            long v = src >= 0 ? a[src] << bits : 0L;
            if (bits != 0 && src - 1 >= 0) {
                v |= a[src - 1] >>> (64 - bits);
            }
            a[i] = v;
        }
    }
}
