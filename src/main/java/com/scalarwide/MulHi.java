package com.scalarwide;

/**
 * Unsigned 64x64-to-high-64 multiply. Isolated in one class so that the
 * Java-17 base build ({@code Math.multiplyHigh} plus the unsigned correction)
 * can be swapped for the JDK 18+ intrinsic via a multi-release overlay if the
 * shim cost ever proves material.
 */
final class MulHi {

    static long high(long a, long b) {
        long hi = Math.multiplyHigh(a, b);
        return hi + ((a >> 63) & b) + ((b >> 63) & a);
    }

    private MulHi() {
    }
}
