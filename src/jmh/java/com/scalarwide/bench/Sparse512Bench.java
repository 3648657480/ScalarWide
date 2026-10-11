package com.scalarwide.bench;

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

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * B5 512-width sparse-dispatch bench: "half-sparse" operands (fit 256 bits),
 * dense controls, and a randomized mix for branch-mispredict exposure.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class Sparse512Bench {

    private static final int N = 256;

    public final Wide512[] sparseA = new Wide512[N];
    public final Wide512[] sparseB = new Wide512[N];
    public final Wide512[] denseA = new Wide512[N];
    public final Wide512[] denseB = new Wide512[N];
    public final Wide512[] mixA = new Wide512[N];
    public final Wide512[] mixB = new Wide512[N];
    public final BigInteger[] bigSparseA = new BigInteger[N];
    public final BigInteger[] bigSparseB = new BigInteger[N];
    public final BigInteger[] bigDenseA = new BigInteger[N];
    public final BigInteger[] bigDenseB = new BigInteger[N];

    private int idx;

    @Setup
    public void setup() {
        Random rnd = new Random(20261011L);
        for (int i = 0; i < N; i++) {
            sparseA[i] = Wide512.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() | 1L, 0, 0, 0, 0);
            sparseB[i] = Wide512.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() | 1L, 0, 0, 0, 0);
            denseA[i] = Wide512.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                    rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() | 1L);
            denseB[i] = Wide512.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                    rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() | 1L);
            bigSparseA[i] = sparseA[i].toBigInteger();
            bigSparseB[i] = sparseB[i].toBigInteger();
            bigDenseA[i] = denseA[i].toBigInteger();
            bigDenseB[i] = denseB[i].toBigInteger();
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
    public Wide512 mulSparse() {
        int i = next();
        return sparseA[i].mul(sparseB[i]);
    }

    @Benchmark
    public Wide512 mulDense() {
        int i = next();
        return denseA[i].mul(denseB[i]);
    }

    @Benchmark
    public Wide512 mulMixed() {
        int i = next();
        return mixA[i].mul(mixB[i]);
    }

    @Benchmark
    public Wide512 mulHighSparse() {
        int i = next();
        return sparseA[i].mulHigh(sparseB[i]);
    }

    @Benchmark
    public Wide512 mulHighDense() {
        int i = next();
        return denseA[i].mulHigh(denseB[i]);
    }

    @Benchmark
    public Wide512 divSparse() {
        int i = next();
        return sparseA[i].div(sparseB[i]);
    }

    @Benchmark
    public Wide512 divDense() {
        int i = next();
        return denseA[i].div(denseB[i]);
    }

    @Benchmark
    public Wide512 divMixed() {
        int i = next();
        return mixA[i].div(mixB[i]);
    }

    @Benchmark
    public BigInteger bigMulSparse() {
        int i = next();
        return bigSparseA[i].multiply(bigSparseB[i]);
    }

    @Benchmark
    public BigInteger bigMulDense() {
        int i = next();
        return bigDenseA[i].multiply(bigDenseB[i]);
    }

    @Benchmark
    public BigInteger bigDivSparse() {
        int i = next();
        return bigSparseA[i].divide(bigSparseB[i]);
    }

    @Benchmark
    public BigInteger bigDivDense() {
        int i = next();
        return bigDenseA[i].divide(bigDenseB[i]);
    }
}
