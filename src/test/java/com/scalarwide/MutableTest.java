package com.scalarwide;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MutableTest {

    private static final Random RND = new Random(TestSupport.SEED);

    @Test
    void wide128ChainsMatchBigInteger() {
        check128Chain();
        check128Chain();
    }

    @Test
    void wide256ChainsMatchBigInteger() {
        check256Chain();
        check256Chain();
    }

    @Test
    void shiftRangeEnforced() {
        MutableWide128 m128 = new MutableWide128();
        assertThrows(IllegalArgumentException.class, () -> m128.shl(128));
        assertThrows(IllegalArgumentException.class, () -> m128.ushr(-1));
        MutableWide256 m256 = new MutableWide256();
        assertThrows(IllegalArgumentException.class, () -> m256.shl(256));
        assertThrows(IllegalArgumentException.class, () -> m256.ushr(-1));
    }

    private static void check128Chain() {
        BigInteger mask = TestSupport.mask(128);
        Wide128 start = random128();
        MutableWide128 m = new MutableWide128();
        m.set(start);
        BigInteger expected = start.toBigInteger();
        for (int i = 0; i < 4000; i++) {
            Wide128 op = random128();
            switch (RND.nextInt(9)) {
                case 0 -> {
                    m.add(op);
                    expected = expected.add(op.toBigInteger()).and(mask);
                }
                case 1 -> {
                    m.sub(op);
                    expected = expected.subtract(op.toBigInteger()).and(mask);
                }
                case 2 -> {
                    m.mul(op);
                    expected = expected.multiply(op.toBigInteger()).and(mask);
                }
                case 3 -> {
                    m.and(op);
                    expected = expected.and(op.toBigInteger());
                }
                case 4 -> {
                    m.or(op);
                    expected = expected.or(op.toBigInteger());
                }
                case 5 -> {
                    m.xor(op);
                    expected = expected.xor(op.toBigInteger());
                }
                case 6 -> {
                    m.not();
                    expected = expected.xor(mask);
                }
                case 7 -> {
                    int k = RND.nextInt(128);
                    m.shl(k);
                    expected = expected.shiftLeft(k).and(mask);
                }
                default -> {
                    int k = RND.nextInt(128);
                    m.ushr(k);
                    expected = expected.shiftRight(k);
                }
            }
            assertEquals(expected, m.toWide128().toBigInteger(), "128 step " + i);
        }
        assertEquals(start.toBigInteger(), new MutableWide128().set(start).toWide128().toBigInteger());
    }

    private static void check256Chain() {
        BigInteger mask = TestSupport.mask(256);
        Wide256 start = random256();
        MutableWide256 m = new MutableWide256();
        m.set(start);
        BigInteger expected = start.toBigInteger();
        for (int i = 0; i < 4000; i++) {
            Wide256 op = random256();
            switch (RND.nextInt(9)) {
                case 0 -> {
                    m.add(op);
                    expected = expected.add(op.toBigInteger()).and(mask);
                }
                case 1 -> {
                    m.sub(op);
                    expected = expected.subtract(op.toBigInteger()).and(mask);
                }
                case 2 -> {
                    m.mul(op);
                    expected = expected.multiply(op.toBigInteger()).and(mask);
                }
                case 3 -> {
                    m.and(op);
                    expected = expected.and(op.toBigInteger());
                }
                case 4 -> {
                    m.or(op);
                    expected = expected.or(op.toBigInteger());
                }
                case 5 -> {
                    m.xor(op);
                    expected = expected.xor(op.toBigInteger());
                }
                case 6 -> {
                    m.not();
                    expected = expected.xor(mask);
                }
                case 7 -> {
                    int k = RND.nextInt(256);
                    m.shl(k);
                    expected = expected.shiftLeft(k).and(mask);
                }
                default -> {
                    int k = RND.nextInt(256);
                    m.ushr(k);
                    expected = expected.shiftRight(k);
                }
            }
            assertEquals(expected, m.toWide256().toBigInteger(), "256 step " + i);
        }
    }

    private static Wide128 random128() {
        return Wide128.of(RND.nextLong(), RND.nextLong());
    }

    private static Wide256 random256() {
        return Wide256.of(RND.nextLong(), RND.nextLong(), RND.nextLong(), RND.nextLong());
    }
}
