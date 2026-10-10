package com.scalarwide;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the opt-in thread-ownership guard in child JVMs (the guard flag is
 * read once per class load, so it cannot be toggled inside the test JVM).
 */
class GuardTest {

    private static String runProbe(boolean guard) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java,
                "-cp", System.getProperty("java.class.path"),
                "-Dscalarwide.guard=" + guard,
                "com.scalarwide.GuardProbeMain");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        return out + "\nexit=" + code;
    }

    @Test
    void guardOnDetectsSharedMutable() throws Exception {
        String out = runProbe(true);
        assertTrue(out.contains("THREW"), out);
        assertTrue(out.contains("PERTHREAD_OK"), out);
    }

    @Test
    void guardOffIsSilentAndCorrect() throws Exception {
        String out = runProbe(false);
        assertTrue(out.contains("NOTHREW"), out);
        assertTrue(out.contains("PERTHREAD_OK"), out);
    }
}
