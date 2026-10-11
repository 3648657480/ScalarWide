package com.scalarwide;

/**
 * JDK 21+ overlay: the intrinsic replaces the Java-17 shim in the base class
 * (multi-release JAR, {@code META-INF/versions/21}).
 */
final class MulHi {

    static long high(long a, long b) {
        return Math.unsignedMultiplyHigh(a, b);
    }

    private MulHi() {
    }
}
