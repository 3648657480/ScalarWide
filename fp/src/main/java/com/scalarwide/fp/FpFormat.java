package com.scalarwide.fp;

/**
 * A binary floating-point format: {@code precision} significant bits and a normal exponent range.
 *
 * <p>Total width is {@code 1 + exponentBits + (precision - 1)}; the exponent field is not stored
 * as a bit pattern, so the format only needs the numeric bounds.
 */
public final class FpFormat {

    /** Long2 {@code Binary256} / MinecraftTools {@code FloatRefactor256}: 1+19+236 = 256 bits. */
    public static final FpFormat BINARY256 = new FpFormat(237, -262142, 262143);

    /** Long2 {@code Binary640}: 1+24+615 = 640 bits. */
    public static final FpFormat BINARY640 = new FpFormat(616, -8388606, 8388607);

    /**
     * 1280-bit format: 1 + 28 + 1251. Derived from the published progression
     * ({@code 256 -> e=19, 640 -> e=24}, {@code expMax = 2^(e-1)-1}); one constant to change if
     * the reference implementation picks a different exponent width.
     */
    public static final FpFormat BINARY1280 = new FpFormat(1252, -134217726, 134217727);
    private final int precision;
    private final int expMin;
    private final int expMax;
    private final int limbs;

    public FpFormat(int precision, int expMin, int expMax) {
        if (precision < 64) {
            throw new IllegalArgumentException("precision out of range: " + precision);
        }
        if (expMin >= expMax) {
            throw new IllegalArgumentException("empty exponent range");
        }
        this.precision = precision;
        this.expMin = expMin;
        this.expMax = expMax;
        this.limbs = (precision + 63) >>> 6;
    }

    public int precision() {
        return precision;
    }

    public int expMin() {
        return expMin;
    }

    public int expMax() {
        return expMax;
    }

    int limbs() {
        return limbs;
    }

    /** Bit index of the implicit leading one. */
    int topBit() {
        return precision - 1;
    }

    /** Mask of the bits used in the most significant limb. */
    long topLimbMask() {
        int used = precision - 64 * (limbs - 1);
        return used == 64 ? -1L : (1L << used) - 1L;
    }

    @Override
    public String toString() {
        return "FpFormat[p=" + precision + ", exp=[" + expMin + ", " + expMax + "]]";
    }
}
