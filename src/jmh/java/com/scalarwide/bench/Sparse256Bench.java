package com.scalarwide.bench;

import com.scalarwide.Wide256;
import com.scalarwide.Wide512;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * B4 sparse-dispatch bench: coordinate-scale operands (log-uniform &lt; 2^127,
 * the mc fast-path domain), dense controls, and a randomized sparse/dense mix
 * to expose branch mispredict cost. Operands rotate so the JIT cannot fold.
 *
 * <p>比较体系 v2（2026-10-14）：BigInteger 对照已移除（仅正确性对拍）。</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class Sparse256Bench {

    private static final int N = 256;

    public final Wide256[] sparseA = new Wide256[N];
    public final Wide256[] sparseB = new Wide256[N];
    public final Wide256[] denseA = new Wide256[N];
    public final Wide256[] denseB = new Wide256[N];
    public final Wide256[] mixA = new Wide256[N];
    public final Wide256[] mixB = new Wide256[N];

    private int idx;

    @Setup
    public void setup() {
        Random rnd = new Random(20261011L);
        for (int i = 0; i < N; i++) {
            sparseA[i] = Wide256.fromBigInteger(RealKeyStats.sampleCoord(rnd));
            sparseB[i] = Wide256.fromBigInteger(RealKeyStats.sampleCoord(rnd));
            denseA[i] = Wide256.of(rnd.nextLong(), rnd.nextLong() | 1L, rnd.nextLong(), rnd.nextLong() | 1L);
            denseB[i] = Wide256.of(rnd.nextLong(), rnd.nextLong() | 1L, rnd.nextLong(), rnd.nextLong() | 1L);
            if (rnd.nextBoolean()) {
                mixA[i] = sparseA[i];
                mixB[i] = sparseB[i];
            } else {
                mixA[i] = denseA[i];
                mixB[i] = denseB[i];
            }
        }
    }

    private int next() {
        return idx++ & (N - 1);
    }

    @Benchmark
    public Wide256 mulSparse() {
        int i = next();
        return sparseA[i].mul(sparseB[i]);
    }

    @Benchmark
    public Wide256 mulDense() {
        int i = next();
        return denseA[i].mul(denseB[i]);
    }

    @Benchmark
    public Wide256 mulMixed() {
        int i = next();
        return mixA[i].mul(mixB[i]);
    }

    @Benchmark
    public Wide512 mulFullSparse() {
        int i = next();
        return sparseA[i].mulFull(sparseB[i]);
    }

    @Benchmark
    public Wide512 mulFullDense() {
        int i = next();
        return denseA[i].mulFull(denseB[i]);
    }

    @Benchmark
    public Wide256 mulHighSparse() {
        int i = next();
        return sparseA[i].mulHigh(sparseB[i]);
    }

    @Benchmark
    public Wide256 mulHighDense() {
        int i = next();
        return denseA[i].mulHigh(denseB[i]);
    }

    @Benchmark
    public Wide256 divSparse() {
        int i = next();
        return sparseA[i].div(sparseB[i]);
    }

    @Benchmark
    public Wide256 divDense() {
        int i = next();
        return denseA[i].div(denseB[i]);
    }

    @Benchmark
    public Wide256 divMixed() {
        int i = next();
        return mixA[i].div(mixB[i]);
    }

    @Benchmark
    public Wide256 remSparse() {
        int i = next();
        return sparseA[i].rem(sparseB[i]);
    }

    @Benchmark
    public Wide256 remDense() {
        int i = next();
        return denseA[i].rem(denseB[i]);
    }
}
