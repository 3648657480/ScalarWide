package com.scalarwide;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Wide1024Test {

    private static final int BITS = 1024;
    private static final BigInteger MASK = TestSupport.mask(BITS);
    private static final Random RND = new Random(TestSupport.SEED);
    private static final int[] SHIFTS = {
            0, 1, 63, 64, 65, 127, 128, 129, 255, 256, 257, 383, 384, 385, 511, 512,
            513, 639, 640, 641, 767, 768, 769, 895, 896, 897, 959, 960, 961, 1023
    };

    @Test
    void extremesMatchBigInteger() {
        for (BigInteger a : TestSupport.extremes(BITS)) {
            for (BigInteger b : TestSupport.extremes(BITS)) {
                check(a, b);
            }
        }
    }

    @Test
    void randomMatchBigInteger() {
        for (int i = 0; i < 1500; i++) {
            check(new BigInteger(BITS, RND), new BigInteger(BITS, RND));
        }
    }

    @Test
    void shiftAmountRangeEnforced() {
        Wide1024 v = Wide1024.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);
        assertThrows(IllegalArgumentException.class, () -> v.shl(-1));
        assertThrows(IllegalArgumentException.class, () -> v.shl(BITS));
        assertThrows(IllegalArgumentException.class, () -> v.ushr(-1));
        assertThrows(IllegalArgumentException.class, () -> v.ushr(BITS));
        assertThrows(IllegalArgumentException.class, () -> v.shr(-1));
        assertThrows(IllegalArgumentException.class, () -> v.shr(BITS));
    }

    @Test
    void fromBigIntegerRejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> Wide1024.fromBigInteger(BigInteger.valueOf(-1)));
        assertThrows(IllegalArgumentException.class, () -> Wide1024.fromBigInteger(BigInteger.ONE.shiftLeft(BITS)));
    }

    @Test
    void constructorsAndAccessors() {
        assertEquals(BigInteger.ZERO, Wide1024.ZERO.toBigInteger());
        assertEquals(BigInteger.ONE, Wide1024.ONE.toBigInteger());
        assertEquals(MASK, Wide1024.MAX_VALUE.toBigInteger());
        assertEquals(MASK, Wide1024.ofSigned(-1).toBigInteger());
        assertEquals(0xFFL, Wide1024.of(0xFFL).longValue());
        Wide1024 v = Wide1024.of(0x11L, 0x22L, 0x33L, 0x44L, 0x55L, 0x66L, 0x77L, 0x88L,
                0x99L, 0xAAL, 0xBBL, 0xCCL, 0xDDL, 0xEEL, 0xFFL, 0x111L);
        assertEquals(0x55L, v.limb(4));
        assertEquals(0x111L, v.limb(15));
        assertThrows(IndexOutOfBoundsException.class, () -> v.limb(16));
        assertEquals(0, Wide1024.ZERO.bitLength());
        assertEquals(BITS, Wide1024.MAX_VALUE.bitLength());
    }

    private static void check(BigInteger a, BigInteger b) {
        Wide1024 wa = Wide1024.fromBigInteger(a);
        Wide1024 wb = Wide1024.fromBigInteger(b);

        assertEquals(a.add(b).and(MASK), wa.add(wb).toBigInteger(), "add");
        assertEquals(a.subtract(b).and(MASK), wa.sub(wb).toBigInteger(), "sub");
        assertEquals(a.negate().and(MASK), wa.neg().toBigInteger(), "neg");
        BigInteger p = a.multiply(b);
        assertEquals(p.and(MASK), wa.mul(wb).toBigInteger(), "mul");
        assertEquals(p.shiftRight(BITS), wa.mulHigh(wb).toBigInteger(), "mulHigh");
        assertEquals(a.and(b), wa.and(wb).toBigInteger(), "and");
        assertEquals(a.or(b), wa.or(wb).toBigInteger(), "or");
        assertEquals(a.xor(b), wa.xor(wb).toBigInteger(), "xor");
        assertEquals(a.andNot(b).and(MASK), wa.andNot(wb).toBigInteger(), "andNot");
        assertEquals(MASK.xor(a), wa.not().toBigInteger(), "not");

        BigInteger signedA = TestSupport.toSigned(a, BITS);
        BigInteger signedB = TestSupport.toSigned(b, BITS);
        for (int k : SHIFTS) {
            assertEquals(a.shiftLeft(k).and(MASK), wa.shl(k).toBigInteger(), "shl " + k);
            assertEquals(a.shiftRight(k), wa.ushr(k).toBigInteger(), "ushr " + k);
            assertEquals(signedA.shiftRight(k).and(MASK), wa.shr(k).toBigInteger(), "shr " + k);
        }

        assertEquals(Integer.signum(a.compareTo(b)), Integer.signum(wa.compareUnsigned(wb)), "compareUnsigned");
        assertEquals(Integer.signum(signedA.compareTo(signedB)), Integer.signum(wa.compareSigned(wb)), "compareSigned");
        assertEquals(Integer.signum(a.compareTo(b)), Integer.signum(wa.compareTo(wb)), "compareTo");
        assertEquals(a.bitLength(), wa.bitLength(), "bitLength");
        assertEquals(a.toString(16), wa.toHexString(), "hex");
        assertEquals(a.toString(), wa.toString(), "dec");
        assertEquals(wa, Wide1024.fromBigInteger(a), "equals");
        assertEquals(wa.hashCode(), Wide1024.fromBigInteger(a).hashCode(), "hashCode");
    }
}
