# Generates src/jmh/java/com/scalarwide/bench/CompetitionBench.java
# 3 widths x 20 operations, both implementations, same 256-element input pools as Precheck.
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))

$binary = @(
  @{ n='add';      w='add';            l='add' },
  @{ n='subtract'; w='sub';            l='subtract' },
  @{ n='multiply'; w='mul';            l='multiply' },
  @{ n='and';      w='and';            l='and' },
  @{ n='or';       w='or';             l='or' },
  @{ n='xor';      w='xor';            l='xor' },
  @{ n='compareTo';w='compareSigned';  l='compareTo' },
  @{ n='equals';   w='equals';         l='equals' }
)
$unary = @(
  @{ n='not';        w='not';              l='not' },
  @{ n='negate';     w='neg';              l='negate' },
  @{ n='shiftLeft';  w='shl7';             l='shift7' },
  @{ n='shiftRight'; w='shr7';             l='shiftM7' },
  @{ n='hashCode';   w='hashCode';         l='hashCode' },
  @{ n='toBytes';    w='toByteArrayFixed'; l='toBytes' },
  @{ n='toString';   w='toString';         l='toString' },
  @{ n='toHexString';w='toHexString';      l='toHexString' },
  @{ n='toLong';     w='longValue';        l='toLong' },
  @{ n='bitLength';  w='bitLength';        l='bitLength' },
  @{ n='ofLong';     w='ofLongW';          l='ofLongL' },
  @{ n='fromBytes';  w='fromBytesW';       l='fromBytesL' }
)

$sb = New-Object System.Text.StringBuilder
function L($s) { [void]$sb.Append($s); [void]$sb.Append("`r`n") }

L 'package com.scalarwide.bench;'
L ''
L 'import com.scalarwide.Wide1024;'
L 'import com.scalarwide.Wide256;'
L 'import com.scalarwide.Wide512;'
L 'import com.inf.long2.ints.Int1024;'
L 'import com.inf.long2.ints.Int256;'
L 'import com.inf.long2.ints.Int512;'
L 'import org.openjdk.jmh.annotations.Benchmark;'
L 'import org.openjdk.jmh.annotations.BenchmarkMode;'
L 'import org.openjdk.jmh.annotations.Fork;'
L 'import org.openjdk.jmh.annotations.Measurement;'
L 'import org.openjdk.jmh.annotations.Mode;'
L 'import org.openjdk.jmh.annotations.OutputTimeUnit;'
L 'import org.openjdk.jmh.annotations.Scope;'
L 'import org.openjdk.jmh.annotations.Setup;'
L 'import org.openjdk.jmh.annotations.State;'
L 'import org.openjdk.jmh.annotations.Warmup;'
L 'import org.openjdk.jmh.infra.Blackhole;'
L ''
L 'import java.math.BigInteger;'
L 'import java.util.Random;'
L 'import java.util.concurrent.TimeUnit;'
L ''
L '/**'
L ' * ScalarWide vs Long2 on the 20 shared operations, three widths.'
L ' *'
L ' * <p>Input pools are the same 256 values, same seed and order as {@link Precheck}, so a'
L ' * timing row and its correctness row describe the same inputs. Each call rotates the cursor'
L ' * one element, matching the reference report method: one target operation per call, result'
L ' * returned to JMH.'
L ' */'
L '@BenchmarkMode(Mode.AverageTime)'
L '@OutputTimeUnit(TimeUnit.NANOSECONDS)'
L '@Fork(2)'
L '@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)'
L '@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)'
L 'public class CompetitionBench {'
L ''
L '    private static final int N = 256;'
L '    private static final long SEED = 12345L;'
L '    private static final BigInteger M64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);'
L ''
L '    static BigInteger[] pool(int width, long salt) {'
L '        Random rnd = new Random(SEED + salt);'
L '        BigInteger mask = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);'
L '        BigInteger[] v = new BigInteger[N];'
L '        for (int i = 0; i < N; i++) {'
L '            if (i == 0) { v[i] = BigInteger.ZERO; }'
L '            else if (i == 1) { v[i] = BigInteger.ONE; }'
L '            else if (i == 2) { v[i] = mask; }'
L '            else if (i == 3) { v[i] = BigInteger.ONE.shiftLeft(width - 1); }'
L '            else { v[i] = new BigInteger(width, rnd).and(mask); }'
L '        }'
L '        return v;'
L '    }'
L ''
L '    static long[] limbs(BigInteger v, int limbs) {'
L '        long[] out = new long[limbs];'
L '        for (int i = 0; i < limbs; i++) {'
L '            out[limbs - 1 - i] = v.shiftRight(64 * i).longValue();'
L '        }'
L '        return out;'
L '    }'
L ''
L '    static byte[] fixedBe(BigInteger v, int width) {'
L '        byte[] out = new byte[width / 8];'
L '        for (int i = 0; i < out.length; i++) {'
L '            out[i] = (byte) v.shiftRight(8 * (out.length - 1 - i)).intValue();'
L '        }'
L '        return out;'
L '    }'
L ''

foreach ($w in @(@{b=256;x=4}, @{b=512;x=8}, @{b=1024;x=16})) {
  $bits = $w.b; $limbs = $w.x
  L "    @State(Scope.Thread)"
  L "    public static class S$bits {"
  L "        Wide$bits[] wa; Wide$bits[] wb;"
  L "        Int$bits[] la; Int$bits[] lb;"
  L "        byte[][] raw$bits;"
  L "        int c;"
  L ""
  L "        @Setup"
  L "        public void setup() {"
  L "            BigInteger[] a = pool($bits, 1);"
  L "            BigInteger[] b = pool($bits, 2);"
  L "            wa = new Wide$bits[N]; wb = new Wide$bits[N];"
  L "            la = new Int$bits[N]; lb = new Int$bits[N];"
  L "            raw$bits = new byte[N][];"
  L "            for (int i = 0; i < N; i++) {"
  L "                wa[i] = Wide$bits.fromBigInteger(a[i]);"
  L "                wb[i] = Wide$bits.fromBigInteger(b[i]);"
  L "                la[i] = Int$bits.of(limbs(a[i], $limbs));"
  L "                lb[i] = Int$bits.of(limbs(b[i], $limbs));"
  L "                raw$bits[i] = fixedBe(a[i], $bits);"
  L "            }"
  L "        }"
  L "    }"
  L ""
}

# per-op methods
foreach ($w in @(@{b=256;x=4}, @{b=512;x=8}, @{b=1024;x=16})) {
  $bits = $w.b
  foreach ($op in $binary) {
    L "    @Benchmark"
    L "    public Wide$bits w${bits}_$($op.n)(S$bits s) { int i = s.c++ & 255; return s.wa[i].$($op.w)(s.wb[i]); }"
    L ""
    L "    @Benchmark"
    L "    public Int$bits l${bits}_$($op.n)(S$bits s) { int i = s.c++ & 255; return s.la[i].$($op.l)(s.lb[i]); }"
    L ""
  }
}
[System.IO.File]::WriteAllText((Join-Path (Resolve-Path 'src\jmh\java\com\scalarwide\bench').Path 'CompetitionBench.java'), $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
"generated: " + (($sb.ToString() -split "`r`n").Count) + " lines"
