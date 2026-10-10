package com.scalarwide.mc;

import com.scalarwide.MutableWide256;
import com.scalarwide.Wide128;
import com.scalarwide.Wide256;
import com.scalarwide.Wide512;

/**
 * MC-facing warmup helper: run once at world/chunk-pipeline init to reach C2
 * before the first real chunk, removing the documented cold-start penalty
 * (first calls are 30-40x slower than steady state).
 *
 * <p>Operands rotate through a small fixed table so the JIT cannot fold the
 * loops away entirely; the goal is compilation of the hot op chain, not
 * meaningful results.
 */
public final class McWarmup {

    private static final int TABLE = 64;

    private static final Wide128[] A128 = new Wide128[TABLE];
    private static final Wide128[] B128 = new Wide128[TABLE];
    private static final Wide256[] A256 = new Wide256[TABLE];
    private static final Wide256[] B256 = new Wide256[TABLE];
    private static final Wide512[] A512 = new Wide512[TABLE];
    private static final Wide512[] B512 = new Wide512[TABLE];

    private static long sink;

    static {
        long s = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < TABLE; i++) {
            A128[i] = Wide128.of(mix(s += 0x9E3779B97F4A7C15L), mix(s));
            B128[i] = Wide128.of(mix(s += 0x9E3779B97F4A7C15L), mix(s));
            A256[i] = Wide256.of(mix(s += 0x9E3779B97F4A7C15L), mix(s), mix(s += 7), mix(s));
            B256[i] = Wide256.of(mix(s += 0x9E3779B97F4A7C15L), mix(s), mix(s += 7), mix(s));
            A512[i] = Wide512.of(mix(s += 1), mix(s += 2), mix(s += 3), mix(s += 4),
                    mix(s += 5), mix(s += 6), mix(s += 7), mix(s += 8));
            B512[i] = Wide512.of(mix(s += 9), mix(s += 10), mix(s += 11), mix(s += 12),
                    mix(s += 13), mix(s += 14), mix(s += 15), mix(s += 16));
        }
    }

    public static void run() {
        run(50_000);
    }

    public static void run(int iterations) {
        warm128(iterations);
        warm256(iterations);
        warm512(iterations);
        warmMutable(iterations);
    }

    private static void warm128(int n) {
        long acc = 0;
        for (int i = 0; i < n; i++) {
            int j = i & (TABLE - 1);
            Wide128 r = A128[j].add(B128[j]).mul(A128[(j + 1) & (TABLE - 1)]);
            acc += r.hashCode() + r.compareUnsigned(B128[j]) + r.shl(i & 127).ushr(i & 127).limb(0);
        }
        sink = acc;
    }

    private static void warm256(int n) {
        long acc = 0;
        for (int i = 0; i < n; i++) {
            int j = i & (TABLE - 1);
            Wide256 r = A256[j].add(B256[j]).mul(B256[(j + 3) & (TABLE - 1)]);
            Wide256 h = A256[j].mulHigh(B256[j]);
            acc += r.hashCode() + h.compareUnsigned(B256[j]) + r.shl(i & 251).ushr(i & 251).limb(0);
        }
        sink = acc;
    }

    private static void warm512(int n) {
        long acc = 0;
        for (int i = 0; i < n; i++) {
            int j = i & (TABLE - 1);
            Wide512 r = A512[j].add(B512[j]).mul(B512[(j + 5) & (TABLE - 1)]);
            Wide512 h = A512[j].mulHigh(B512[j]);
            acc += r.hashCode() + h.compareUnsigned(B512[j]) + r.shr(i & 511).limb(0);
        }
        sink = acc;
    }

    private static void warmMutable(int n) {
        MutableWide256 acc = new MutableWide256();
        long s = 0;
        for (int i = 0; i < n; i++) {
            int j = i & (TABLE - 1);
            acc.set(A256[j]).add(B256[j]).mul(B256[(j + 7) & (TABLE - 1)]);
            s += acc.toWide256().limb(0);
        }
        sink = s;
    }

    private static long mix(long v) {
        v ^= v >>> 33;
        v *= 0xFF51AFD7ED558CCDL;
        v ^= v >>> 33;
        return v;
    }

    private McWarmup() {
    }
}
