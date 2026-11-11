package com.scalarwide.fp;

import com.scalarwide.Wide1024;

/**
 * Integer square root by bit-pair extraction: the classic restoring algorithm, kept deliberately
 * simple because it is the correctness anchor. A Newton version can be added later and checked
 * against this one limb for limb.
 *
 * <p>Runs in {@code O(bits)} iterations, each doing a shift, a compare and a subtract on a
 * {@link Wide1024}; that is slow but obviously correct.
 */
final class IntegerSqrt {

    private static final Wide1024 ONE = Wide1024.of(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    private static final Wide1024 THREE = Wide1024.of(3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    private IntegerSqrt() {
    }

    /** {@code floor(sqrt(n))}. */
    static Wide1024 of(Wide1024 n) {
        int bits = n.bitLength();
        if (bits == 0) {
            return Wide1024.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        Wide1024 rem = Wide1024.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        Wide1024 root = Wide1024.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        int top = (bits + 1) & ~1; // first pair boundary at or above the leading bit
        for (int i = top; i > 0; i -= 2) {
            rem = rem.shl(2).or(n.ushr(i - 2).and(THREE));
            root = root.shl(1);
            Wide1024 trial = root.shl(1).or(ONE);
            if (rem.compareUnsigned(trial) >= 0) {
                rem = rem.sub(trial);
                root = root.or(ONE);
            }
        }
        return root;
    }
}
