package com.scalarwide.mc;

import org.junit.jupiter.api.Test;

class McWarmupTest {

    @Test
    void runsWithoutError() {
        McWarmup.run(10_000);
    }
}
