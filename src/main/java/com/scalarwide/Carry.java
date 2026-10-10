package com.scalarwide;

final class Carry {

    private Carry() {
    }

    static long addCarryOut(long a, long b, long r) {
        return ((a & b) | ((a | b) & ~r)) >>> 63;
    }

    static long carryOut(long a, long r) {
        return Long.compareUnsigned(r, a) < 0 ? 1 : 0;
    }

    static long borrowOut(long a, long r) {
        return Long.compareUnsigned(r, a) > 0 ? 1 : 0;
    }

    static long subBorrowOut(long a, long b, long r) {
        return ((~a & b) | ((~a | b) & r)) >>> 63;
    }
}
