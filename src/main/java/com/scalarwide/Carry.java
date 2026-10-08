package com.scalarwide;

final class Carry {

    private Carry() {
    }

    static long addCarryOut(long a, long b, long r) {
        return ((a & b) | ((a | b) & ~r)) >>> 63;
    }

    static long subBorrowOut(long a, long b, long r) {
        return ((~a & b) | ((~a | b) & r)) >>> 63;
    }
}
