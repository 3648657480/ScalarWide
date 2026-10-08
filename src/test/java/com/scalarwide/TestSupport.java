package com.scalarwide;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

final class TestSupport {

    static final long SEED = 12345L;

    private TestSupport() {
    }

    static BigInteger mask(int bits) {
        return BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
    }

    static BigInteger toSigned(BigInteger v, int bits) {
        return v.testBit(bits - 1) ? v.subtract(BigInteger.ONE.shiftLeft(bits)) : v;
    }

    static List<BigInteger> extremes(int bits) {
        BigInteger one = BigInteger.ONE;
        BigInteger max = mask(bits);
        BigInteger half = one.shiftLeft(bits - 1);
        BigInteger altA = BigInteger.ZERO;
        BigInteger alt5 = BigInteger.ZERO;
        for (int i = 0; i < bits / 4; i++) {
            altA = altA.shiftLeft(4).or(BigInteger.valueOf(0xA));
            alt5 = alt5.shiftLeft(4).or(BigInteger.valueOf(0x5));
        }
        List<BigInteger> vs = new ArrayList<>();
        vs.add(BigInteger.ZERO);
        vs.add(one);
        vs.add(BigInteger.TWO);
        vs.add(half.subtract(one));
        vs.add(half);
        vs.add(half.add(one));
        vs.add(max);
        vs.add(max.subtract(one));
        vs.add(max.subtract(BigInteger.TWO));
        vs.add(half.shiftRight(1));
        vs.add(altA);
        vs.add(alt5);
        vs.add(max.xor(altA));
        return vs;
    }
}
