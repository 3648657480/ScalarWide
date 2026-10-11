package com.scalarwide;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DivMagicTest {

    @Test
    void differentialRandom() {
        Random rnd = new Random(616161L);
        for (int i = 0; i < 20_000; i++) {
            long d = 1 + (rnd.nextLong() & 0xFFFFFFFFL) % 0xFFFFFFFFL;
            DivMagic m = DivMagic.of(d);
            Wide256 x = rnd.nextBoolean()
                    ? Wide256.fromBigInteger(RealKeyStatsLike.sample(rnd))
                    : Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
            assertEquals(x.div(Wide256.of(d)).toBigInteger(), m.div(x).toBigInteger(), "div d=" + d);
            assertEquals(x.rem(Wide256.of(d)).toBigInteger(), m.rem(x).toBigInteger(), "rem d=" + d);
        }
    }

    @Test
    void boundariesAndDomain() {
        long[] divisors = {1, 2, 3, 10, 65535, 65536, 0x7FFFFFFFL, 0xFFFFFFFFL, 0xFFFFFFFEL};
        BigInteger[] xs = {BigInteger.ZERO, BigInteger.ONE, BigInteger.TWO,
                BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE),
                BigInteger.ONE.shiftLeft(127).add(BigInteger.valueOf(12345))};
        for (long d : divisors) {
            DivMagic m = DivMagic.of(d);
            for (BigInteger bx : xs) {
                Wide256 x = Wide256.fromBigInteger(bx);
                assertEquals(bx.divide(BigInteger.valueOf(d)), m.div(x).toBigInteger(), "d=" + d);
                assertEquals(bx.mod(BigInteger.valueOf(d)), m.rem(x).toBigInteger(), "d=" + d);
            }
            // d-1 and neighbours
            Wide256 near = Wide256.of(d - 1);
            assertEquals(Wide256.ZERO, m.div(near));
            assertEquals(Wide256.of(d - 1), m.rem(near));
            assertEquals(Wide256.ONE, m.div(Wide256.of(d)));
            assertEquals(Wide256.ZERO, m.rem(Wide256.of(d)));
        }
        assertThrows(IllegalArgumentException.class, () -> DivMagic.of(0));
        assertThrows(IllegalArgumentException.class, () -> DivMagic.of(1L << 32));
        assertThrows(IllegalArgumentException.class,
                () -> DivMagic.of(Wide256.of(5, 1, 0, 0)));
    }

    /** Local coordinate-style sampler (log-uniform 2^20..2^127). */
    private static final class RealKeyStatsLike {
        static BigInteger sample(Random rnd) {
            int e = 20 + rnd.nextInt(108);
            return BigInteger.ONE.shiftLeft(e - 1).add(new BigInteger(e - 1, rnd));
        }
    }
}
