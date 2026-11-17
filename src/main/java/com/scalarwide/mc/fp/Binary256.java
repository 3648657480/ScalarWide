package com.scalarwide.mc.fp;

/**
 * 256-bit coordinate container: {@code p = 237}, four limbs. Same contract as the 1280-bit
 * container; only the operations the terrain sampler needs are provided.
 */
public final class Binary256 extends Container {

    /** Significant bits, matching {@code FpFormat.BINARY256}. */
    public static final int PRECISION = 237;
    /** Little-endian limb count. */
    public static final int LIMBS = 4;

    public Binary256() {
        super(PRECISION, LIMBS);
    }
}