package com.scalarwide;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Child-JVM probe for the opt-in guard ({@code -Dscalarwide.guard=...}):
 * hammers a SHARED mutable from 8 threads and reports whether the guard
 * threw; then runs the per-thread pattern and checks correctness.
 * Prints THREW/NOTHREW and PERTHREAD_OK/FAIL.
 */
public final class GuardProbeMain {

    private static final Wide256 B = Wide256.of(0x1234567890ABCDEFL, 0xFEDCBA0987654321L,
            0x1111111111111111L, 0x2222222222222222L);
    private static final int ITERS = 2_000_000;

    public static void main(String[] args) throws Exception {
        AtomicBoolean threw = new AtomicBoolean();
        MutableWide256 shared = new MutableWide256();
        CountDownLatch start = new CountDownLatch(1);
        Thread[] ts = new Thread[8];
        for (int t = 0; t < ts.length; t++) {
            ts[t] = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    return;
                }
                try {
                    for (int i = 0; i < ITERS; i++) {
                        shared.add(B);
                    }
                } catch (IllegalStateException e) {
                    threw.set(true);
                }
            });
            ts[t].start();
        }
        start.countDown();
        for (Thread t : ts) {
            t.join();
        }
        System.out.println(threw.get() ? "THREW" : "NOTHREW");

        Wide256 expectedSmall = Wide256.of(0, 0, 0, 0);
        for (int i = 0; i < ITERS / 4; i++) {
            expectedSmall = expectedSmall.add(B);
        }
        boolean ok = true;
        for (int t = 0; t < 4; t++) {
            MutableWide256 own = new MutableWide256();
            for (int i = 0; i < ITERS / 4; i++) {
                own.add(B);
            }
            ok &= own.toWide256().compareUnsigned(expectedSmall) == 0;
        }
        System.out.println("PERTHREAD_" + (ok ? "OK" : "FAIL"));
    }

    private GuardProbeMain() {
    }
}
