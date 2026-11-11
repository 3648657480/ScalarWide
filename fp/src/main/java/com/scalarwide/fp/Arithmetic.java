package com.scalarwide.fp;

import com.scalarwide.Wide512;

/**
 * The single rounding point. {@link Exact} produces a value exact to within one ULP together
 * with the sign of the remaining tail; {@code pack} is the only place that rounds.
 */
final class Arithmetic {

    private static final Wide512 ONE = Wide512.of(1, 0, 0, 0, 0, 0, 0, 0);
    private static final Wide512 ZERO = Wide512.of(0, 0, 0, 0, 0, 0, 0, 0);
    private static final int P = Binary256.PRECISION;

    private Arithmetic() {
    }

    /**
     * Rounds {@code sign * (mag - down*delta) * 2^(exp-255)} to nearest-even at {@code P} bits,
     * with gradual underflow below {@code EXP_MIN}.
     */
    static Binary256 pack(int sign, int exp, Wide512 mag, boolean sticky, boolean down) {
        if (down && sticky) {
            // (mag - delta) == (mag - 1) + (1 - delta), which is the "at or above" form.
            mag = mag.sub(ONE);
        }
        if (mag.isZero()) {
            return sign == 0 ? Binary256.ZERO : Binary256.NEGATIVE_ZERO;
        }

        // Normalise the leading bit to position P-1; a subnormal target forces extra shift.
        int lead = mag.bitLength() - 1;
        int shift = lead - (P - 1);
        int target = exp + shift;
        if (target < Binary256.EXP_MIN) {
            shift += Binary256.EXP_MIN - target;
        }

        Wide512 q;
        int guard = 0;
        boolean low;
        if (shift <= 0) {
            q = mag.shl(-shift);
            low = sticky;
        } else if (shift >= 512) {
            q = ZERO;
            guard = 0;
            low = true;
        } else {
            q = mag.ushr(shift);
            guard = (int) (mag.ushr(shift - 1).limb(0) & 1L);
            // Every bit strictly below the guard bit counts into the sticky decision.
            low = sticky || (shift >= 2 && !mag.ushr(shift - 1).shl(shift - 1).equals(mag));
        }

        if (guard != 0 && (low || (q.limb(0) & 1L) != 0L)) {
            q = q.add(ONE);
        }

        int e = exp + shift;
        int bits = q.bitLength();
        if (bits > P) {
            q = q.ushr(1);
            e++;
            bits = P;
        }
        if (bits < P) {
            // Subnormal result: re-normalise internally, the exponent drops below EXP_MIN.
            int grow = P - bits;
            q = q.shl(grow);
            e -= grow;
        }
        if (e > Binary256.EXP_MAX) {
            return sign == 0 ? Binary256.POSITIVE_INFINITY : Binary256.NEGATIVE_INFINITY;
        }
        if (e < Binary256.EXP_MIN - (P - 1)) {
            return sign == 0 ? Binary256.ZERO : Binary256.NEGATIVE_ZERO;
        }
        return Binary256.raw(sign, e, q.limb(0), q.limb(1), q.limb(2), q.limb(3));
    }
}
