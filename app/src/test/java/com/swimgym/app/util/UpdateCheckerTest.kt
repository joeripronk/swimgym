package com.swimgym.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `minor bump with a smaller patch number is detected as newer`() {
        assertTrue(UpdateChecker.isNewerVersion("1.0p50", "v1.1p1"))
        assertTrue(UpdateChecker.isNewerVersion("v1.0p45", "v2.0p1"))
    }

    @Test
    fun `patch and minor components are compared independently`() {
        assertTrue(UpdateChecker.isNewerVersion("1.9p99", "1.10p1"))
        assertTrue(UpdateChecker.isNewerVersion("1.0p9", "1.0p10"))
        assertFalse(UpdateChecker.isNewerVersion("1.0p45", "1.0p45"))
        assertFalse(UpdateChecker.isNewerVersion("1.1p1", "1.0p99"))
    }
}
