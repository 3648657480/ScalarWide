package com.scalarwide;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Wide128Test {

    private static final int BITS = 128;
    private static final BigInteger MASK = TestSupport.mask(BITS);
    private static final Random RND = new Random(TestSupport.SEED);
    private static final int[] SHIFTS = {0, 1, 63, 64, 65, 127};

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
        for (int i = 0; i < 5000; i++) {
            check(new BigInteger(BITS, RND), new BigInteger(BITS, RND));
        }
    }

    @Test
    void shiftAmountRangeEnforced() {
        Wide128 v = Wide128.of(1, 2);
        assertThrows(IllegalArgumentException.class, () -> v.shl(-1));
        assertThrows(IllegalArgumentException.class, () -> v.shl(BITS));
        assertThrows(IllegalArgumentException.class, () -> v.ushr(-1));
        assertThrows(IllegalArgumentException.class, () -> v.ushr(BITS));
        assertThrows(IllegalArgumentException.class, () -> v.shr(-1));
        assertThrows(IllegalArgumentException.class, () -> v.shr(BITS));
    }

    @Test
    void fromBigIntegerRejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> Wide128.fromBigInteger(BigInteger.valueOf(-1)));
        assertThrows(IllegalArgumentException.class, () -> Wide128.fromBigInteger(BigInteger.ONE.shiftLeft(BITS)));
    }

    @Test
    void constructorsAndAccessors() {
        assertEquals(BigInteger.ZERO, Wide128.ZERO.toBigInteger());
        assertEquals(BigInteger.ONE, Wide128.ONE.toBigInteger());
        assertEquals(MASK, Wide128.MAX_VALUE.toBigInteger());
        assertEquals(MASK, Wide128.ofSigned(-1).toBigInteger());
        assertEquals(BigInteger.valueOf(0xFFL), Wide128.of(0xFFL).toBigInteger());
        assertEquals(0xFFL, Wide128.of(0xFFL).longValue());
        assertEquals(0xABL, Wide128.of(0xABL, 0xCDL).limb(0));
        assertEquals(0xCDL, Wide128.of(0xABL, 0xCDL).limb(1));
        assertThrows(IndexOutOfBoundsException.class, () -> Wide128.of(1, 2).limb(2));
        assertEquals(0, Wide128.ZERO.bitLength());
        assertEquals(1, Wide128.ONE.bitLength());
        assertEquals(BITS, Wide128.MAX_VALUE.bitLength());
    }

    @Test
    void stringForms() {
        assertEquals("0", Wide128.ZERO.toString());
        assertEquals("0", Wide128.ZERO.toHexString());
        assertEquals("340282366920938463463374607431768211455", Wide128.MAX_VALUE.toString());
        assertEquals("ffffffffffffffffffffffffffffffff", Wide128.MAX_VALUE.toHexString());
        assertEquals("ff", Wide128.of(255).toHexString());
    }

    private static void check(BigInteger a, BigInteger b) {
        Wide128 wa = Wide128.fromBigInteger(a);
        Wide128 wb = Wide128.fromBigInteger(b);

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
        assertEquals(wa, Wide128.fromBigInteger(a), "equals");
        assertEquals(wa.hashCode(), Wide128.fromBigInteger(a).hashCode(), "hashCode");
    }
}
