package com.scalarwide.bench;

import com.scalarwide.MutableWide256;
import com.scalarwide.Wide256;

import java.util.concurrent.CountDownLatch;

/**
 * Thread-safety probe. Modes:
 * <ul>
 *   <li>{@code race T}      - T threads hammer one SHARED MutableWide256 (expected: wrong result)</li>
 *   <li>{@code perthread T} - one MutableWide256 per thread (expected: correct)</li>
 *   <li>{@code scale T}     - T threads of independent immutable mul (throughput scaling)</li>
 * </ul>
 */
public final class ThreadProbe {

    private static final int ITERS = 2_000_000;
    private static final int SCALE_ITERS = 20_000_000;
    private static final int TABLE = 64;

    private static final Wide256 B = Wide256.of(0x1234567890ABCDEFL, 0xFEDCBA0987654321L,
            0x1111111111111111L, 0x2222222222222222L);

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "race";
        int threads = args.length > 1 ? Integer.parseInt(args[1]) : 8;
        switch (mode) {
            case "race" -> race(threads);
            case "perthread" -> perThread(threads);
            case "scale" -> scale(threads);
            default -> throw new IllegalArgumentException(mode);
        }
    }

    private static void race(int threads) throws Exception {
        Wide256 expected = Wide256.of(0, 0, 0, 0);
        for (long i = 0; i < (long) threads * ITERS; i++) {
            expected = expected.add(B);
        }
        MutableWide256 shared = new MutableWide256();
        CountDownLatch start = new CountDownLatch(1);
        Thread[] ts = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            ts[t] = new Thread(() -> {
                await(start);
                for (int i = 0; i < ITERS; i++) {
                    shared.add(B);
                }
            });
            ts[t].start();
        }
        start.countDown();
        for (Thread t : ts) {
            t.join();
        }
        boolean ok = shared.toWide256().compareUnsigned(expected) == 0;
        System.out.println("RACE threads=" + threads + " shared_result_correct=" + ok
                + "  actual=" + shared.toWide256() + "  expected=" + expected);
    }

    private static void perThread(int threads) throws Exception {
        Wide256 expected = Wide256.of(0, 0, 0, 0);
        for (int i = 0; i < ITERS; i++) {
            expected = expected.add(B);
        }
        MutableWide256[] accs = new MutableWide256[threads];
        for (int t = 0; t < threads; t++) {
            accs[t] = new MutableWide256();
        }
        CountDownLatch start = new CountDownLatch(1);
        Thread[] ts = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            int id = t;
            ts[t] = new Thread(() -> {
                MutableWide256 acc = accs[id];
                await(start);
                for (int i = 0; i < ITERS; i++) {
                    acc.add(B);
                }
            });
            ts[t].start();
        }
        start.countDown();
        for (Thread t : ts) {
            t.join();
        }
        boolean ok = true;
        for (MutableWide256 acc : accs) {
            ok &= acc.toWide256().compareUnsigned(expected) == 0;
        }
        System.out.println("PERTHREAD threads=" + threads + " all_correct=" + ok);
    }

    private static void scale(int threads) throws Exception {
        Wide256[] a = new Wide256[TABLE];
        Wide256[] b = new Wide256[TABLE];
        for (int i = 0; i < TABLE; i++) {
            a[i] = Wide256.of(0x9E3779B97F4A7C15L + i, i * 0xDEADBEEFL, ~((long) i), i + 1L);
            b[i] = Wide256.of(0xABCDEF0123456789L ^ i, 0x1111L * (i + 3), i * 0x9E3779B9L, ~((long) i));
        }
        CountDownLatch start = new CountDownLatch(1);
        Thread[] ts = new Thread[threads];
        long[] ops = new long[threads];
        for (int t = 0; t < threads; t++) {
            int id = t;
            ts[t] = new Thread(() -> {
                long s = 0;
                for (int i = 0; i < 200_000; i++) {
                    s += a[i & (TABLE - 1)].mul(b[(i + id) & (TABLE - 1)]).limb(0);
                }
                await(start);
                for (int i = 0; i < SCALE_ITERS; i++) {
                    Wide256 r = a[i & (TABLE - 1)].mul(b[(i + id) & (TABLE - 1)]);
                    s += r.limb(0);
                }
                ops[id] = s;
            });
            ts[t].start();
        }
        long t0 = System.nanoTime();
        start.countDown();
        for (Thread t : ts) {
            t.join();
        }
        long dt = System.nanoTime() - t0;
        long total = (long) threads * SCALE_ITERS;
        System.out.printf("SCALE threads=%2d ops/s=%10.1f M/s  ns/op=%6.2f  checksum=%d%n",
                threads, total / (dt / 1e9) / 1e6, dt / (double) total, ops[0] & 1);
    }

    private static void await(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ThreadProbe() {
    }
}
