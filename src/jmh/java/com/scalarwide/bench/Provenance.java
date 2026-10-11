package com.scalarwide.bench;

/**
 * Measurement provenance: prints the (host, cpu, threads, jdk, commit) header
 * that every benchmark output must carry. Java reads the Windows CPUID-derived
 * {@code PROCESSOR_IDENTIFIER} (vendor + family/model/stepping), which is the
 * hardware anchor for the ledger (results are machine-specific, R10).
 */
public final class Provenance {

    public static String line() {
        String cpu = System.getenv("PROCESSOR_IDENTIFIER");
        if (cpu == null || cpu.isEmpty()) {
            cpu = System.getProperty("os.arch");
        }
        String commit = System.getenv("SCALARWIDE_COMMIT");
        if (commit == null || commit.isEmpty()) {
            commit = "-";
        }
        return "PROVENANCE host=" + host()
                + " cpu=" + cpu
                + " threads=" + Runtime.getRuntime().availableProcessors()
                + " jdk=" + System.getProperty("java.version")
                + " vm=" + System.getProperty("java.vm.name")
                + " commit=" + commit;
    }

    public static void print() {
        System.out.println(line());
        System.out.flush();
    }

    public static void main(String[] args) {
        print();
    }

    private static String host() {
        String h = System.getenv("COMPUTERNAME");
        if (h == null || h.isEmpty()) {
            h = System.getenv("HOSTNAME");
        }
        return h == null || h.isEmpty() ? "unknown" : h;
    }

    private Provenance() {
    }
}
