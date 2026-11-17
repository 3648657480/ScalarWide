package com.scalarwide.main.integer;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Differential gate for {@link Montgomery}. Every result is checked against {@link BigInteger} at
 * the same width; the context caches {@code n0inv} and {@code R^2 mod m}, so a sign or modulus
 * mistake shows up immediately.
 */
class MontgomeryTest {

    private static final long SEED = 12345L;
    private static final int N = 1500;
    private static final int[] WIDTHS = {2, 4, 6, 8, 9};

    @Test
    void mulMontMatchesTheReducedProduct() {
        Random rnd = new Random(SEED);
        for (int len : WIDTHS) {
            BigInteger mod = oddModulus(rnd, len);
            long[] m = toLimbs(mod, len);
            Montgomery ctx = new Montgomery(m);
            long[] out = new long[len];
            for (int i = 0; i < N; i++) {
                BigInteger a = randomBelow(rnd, mod);
                BigInteger b = randomBelow(rnd, mod);
                ctx.mulMont(toLimbs(a, len), toLimbs(b, len), out);
                BigInteger r = BigInteger.ONE.shiftLeft(64 * len).mod(mod);
                assertEquals(a.multiply(b).mod(mod).multiply(r.modInverse(mod)).mod(mod),
                        toBig(out), "mulMont len=" + len);
            }
        }
    }

    @Test
    void toAndFromMontRoundTrip() {
        Random rnd = new Random(SEED + 1);
        for (int len : WIDTHS) {
            BigInteger mod = oddModulus(rnd, len);
            Montgomery ctx = new Montgomery(toLimbs(mod, len));
            for (int i = 0; i < N; i++) {
                BigInteger a = randomBelow(rnd, mod);
                long[] in = toLimbs(a, len);
                long[] inDomain = new long[len];
                long[] back = new long[len];
                ctx.toMont(in, inDomain);
                ctx.fromMont(inDomain, back);
                assertEquals(a, toBig(back), "toMont/fromMont len=" + len);
            }
        }
    }

    @Test
    void mulModInTheDomainMatchesPlainArithmetic() {
        Random rnd = new Random(SEED + 2);
        for (int len : WIDTHS) {
            BigInteger mod = oddModulus(rnd, len);
            Montgomery ctx = new Montgomery(toLimbs(mod, len));
            long[] a = new long[len];
            long[] b = new long[len];
            long[] prod = new long[len];
            long[] got = new long[len];
            for (int i = 0; i < N; i++) {
                BigInteger x = randomBelow(rnd, mod);
                BigInteger y = randomBelow(rnd, mod);
                ctx.toMont(toLimbs(x, len), a);
                ctx.toMont(toLimbs(y, len), b);
                ctx.mulMont(a, b, prod);
                ctx.fromMont(prod, got);
                assertEquals(x.multiply(y).mod(mod), toBig(got), "domain product len=" + len);
            }
        }
    }

    @Test
    void powModMatchesBigInteger() {
        Random rnd = new Random(SEED + 3);
        for (int len : new int[] {4, 9}) {
            BigInteger mod = oddModulus(rnd, len);
            Montgomery ctx = new Montgomery(toLimbs(mod, len));
            long[] out = new long[len];
            for (int i = 0; i < N / 4; i++) {
                BigInteger base = randomBelow(rnd, mod);
                BigInteger exp = new BigInteger(mod.bitLength(), rnd).mod(mod);
                ctx.powMod(toLimbs(base, len), toLimbs(exp, len), out);
                assertEquals(base.modPow(exp, mod), toBig(out), "powMod len=" + len);
            }
        }
    }

    @Test
    void domainEdgesAreExact() {
        BigInteger mod = BigInteger.ONE.shiftLeft(521).subtract(BigInteger.ONE);
        int len = 9;
        Montgomery ctx = new Montgomery(toLimbs(mod, len));
        long[] out = new long[len];
        // (p-1) is -1: its square is 1 in any representation
        BigInteger minusOne = mod.subtract(BigInteger.ONE);
        long[] dom = new long[len];
        ctx.toMont(toLimbs(minusOne, len), dom);
        ctx.mulMont(dom, dom, out);
        ctx.fromMont(out, out);
        assertEquals(BigInteger.ONE, toBig(out), "(p-1)^2 mod p");
        // zero stays zero through the domain
        long[] zero = new long[len];
        ctx.toMont(zero, out);
        assertArrayEquals(zero, out, "toMont(0)");
        ctx.fromMont(out, out);
        assertArrayEquals(zero, out, "fromMont(0)");
    }

    @Test
    void invalidModuliAreRejected() {
        assertThrows(ArithmeticException.class, () -> new Montgomery(new long[2]));
        assertThrows(ArithmeticException.class, () -> new Montgomery(new long[] {4L, 0L}));
        assertThrows(ArithmeticException.class, () -> new Montgomery(new long[0]));
    }

    // ---------------------------------------------------------------- helpers

    private static BigInteger oddModulus(Random rnd, int len) {
        return new BigInteger(len * 64, rnd).setBit(len * 64 - 1).setBit(0);
    }

    private static BigInteger randomBelow(Random rnd, BigInteger m) {
        BigInteger v;
        do {
            v = new BigInteger(m.bitLength(), rnd);
        } while (v.compareTo(m) >= 0);
        return v;
    }

    static long[] toLimbs(BigInteger v, int len) {
        long[] out = new long[len];
        BigInteger work = v;
        for (int i = 0; i < len; i++) {
            out[i] = work.longValue();
            work = work.shiftRight(64);
        }
        return out;
    }

    static BigInteger toBig(long[] limbs) {
        BigInteger v = BigInteger.ZERO;
        for (int i = limbs.length - 1; i >= 0; i--) {
            long limb = limbs[i];
            BigInteger w = BigInteger.valueOf(limb & Long.MAX_VALUE);
            if (limb < 0) {
                w = w.setBit(63);
            }
            v = v.shiftLeft(64).or(w);
        }
        return v;
    }
}
