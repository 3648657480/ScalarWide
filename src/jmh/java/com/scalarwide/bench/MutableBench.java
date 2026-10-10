package com.scalarwide.bench;

import com.scalarwide.MutableWide256;
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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class MutableBench {

    public Wide256 a;
    public Wide256 b;
    public Wide256 c;
    public Wide256 d;
    public MutableWide256 scratch;
    public Wide256 acc;

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        long top = Long.MAX_VALUE;
        a = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & top);
        b = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & top);
        c = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & top);
        d = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & top);
        scratch = new MutableWide256();
        acc = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & top);
    }

    @Benchmark
    public Wide256 immutableChain() {
        return a.add(b).mul(c).add(d);
    }

    @Benchmark
    public Wide256 mutableChain() {
        scratch.set(a);
        scratch.add(b);
        scratch.mul(c);
        scratch.add(d);
        return scratch.toWide256();
    }

    @Benchmark
    public Wide256 immutableAdd() {
        return a.add(b);
    }

    @Benchmark
    public MutableWide256 mutableAdd() {
        scratch.set(a);
        scratch.add(b);
        return scratch;
    }

    @Benchmark
    public Wide256 immutableMul() {
        return a.mul(c);
    }

    @Benchmark
    public MutableWide256 mutableMul() {
        scratch.set(a);
        scratch.mul(c);
        return scratch;
    }

    @Benchmark
    public MutableWide256 mutableSet() {
        scratch.set(a);
        return scratch;
    }

    @Benchmark
    public Wide256 immutableChain8() {
        return a.add(b).add(c).add(d).add(a).add(b).add(c).add(d).add(a);
    }

    @Benchmark
    public MutableWide256 mutableChain8() {
        scratch.set(a);
        scratch.add(b);
        scratch.add(c);
        scratch.add(d);
        scratch.add(a);
        scratch.add(b);
        scratch.add(c);
        scratch.add(d);
        scratch.add(a);
        return scratch;
    }

    @Benchmark
    public Wide256 immutableAccumulate() {
        acc = acc.add(b);
        return acc;
    }

    @Benchmark
    public MutableWide256 mutableAccumulate() {
        scratch.add(b);
        return scratch;
    }
}
