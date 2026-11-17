package com.scalarwide.mc.fp;

/**
 * 640-bit coordinate container: {@code p = 616}, ten limbs. Same contract as the 1280-bit
 * container; only the operations the terrain sampler needs are provided.
 */
public final class Binary640 extends Container {

    /** Significant bits, matching {@code FpFormat.BINARY640}. */
    public static final int PRECISION = 616;
    /** Little-endian limb count. */
    public static final int LIMBS = 10;

    public Binary640() {
        super(PRECISION, LIMBS);
    }
}