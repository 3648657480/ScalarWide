package com.scalarwide.bench;

import com.scalarwide.DivMagic;
import com.scalarwide.Wide256;
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
 * Magic-division bench: direct {@code x.div(of(d))} vs {@link DivMagic} for
 * common constant divisors, on coordinate-style (sparse) rotating fixtures.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class MagicDivBench {

    private static final int N = 256;
    private static final long D_SMALL = 10;
    private static final long D_MID = 1000;
    private static final long D_LARGE = 2147483647L;
    private static final long D_POW2 = 16;

    public final Wide256[] xs = new Wide256[N];
    public final Wide256 dSmall = Wide256.of(D_SMALL);
    public final Wide256 dMid = Wide256.of(D_MID);
    public final Wide256 dLarge = Wide256.of(D_LARGE);
    public final Wide256 dPow2 = Wide256.of(D_POW2);
    public final DivMagic mSmall = DivMagic.of(D_SMALL);
    public final DivMagic mMid = DivMagic.of(D_MID);
    public final DivMagic mLarge = DivMagic.of(D_LARGE);
    public final DivMagic mPow2 = DivMagic.of(D_POW2);

    private int idx;

    @Setup
    public void setup() {
        Random rnd = new Random(20261011L);
        for (int i = 0; i < N; i++) {
            xs[i] = Wide256.fromBigInteger(RealKeyStats.sampleCoord(rnd).shiftLeft(0));
        }
    }

    private int next() {
        return idx++ & (N - 1);
    }

    @Benchmark
    public Wide256 directDivSmall() {
        return xs[next()].div(dSmall);
    }

    @Benchmark
    public Wide256 magicDivSmall() {
        return mSmall.div(xs[next()]);
    }

    @Benchmark
    public Wide256 directRemSmall() {
        return xs[next()].rem(dSmall);
    }

    @Benchmark
    public Wide256 magicRemSmall() {
        return mSmall.rem(xs[next()]);
    }

    @Benchmark
    public Wide256 directDivMid() {
        return xs[next()].div(dMid);
    }

    @Benchmark
    public Wide256 magicDivMid() {
        return mMid.div(xs[next()]);
    }

    @Benchmark
    public Wide256 directRemMid() {
        return xs[next()].rem(dMid);
    }

    @Benchmark
    public Wide256 magicRemMid() {
        return mMid.rem(xs[next()]);
    }

    @Benchmark
    public Wide256 directDivLarge() {
        return xs[next()].div(dLarge);
    }

    @Benchmark
    public Wide256 magicDivLarge() {
        return mLarge.div(xs[next()]);
    }

    @Benchmark
    public Wide256 directRemLarge() {
        return xs[next()].rem(dLarge);
    }

    @Benchmark
    public Wide256 magicRemLarge() {
        return mLarge.rem(xs[next()]);
    }

    @Benchmark
    public Wide256 directDivPow2() {
        return xs[next()].div(dPow2);
    }

    @Benchmark
    public Wide256 magicDivPow2() {
        return mPow2.div(xs[next()]);
    }
}
