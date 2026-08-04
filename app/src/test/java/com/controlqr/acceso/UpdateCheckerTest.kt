package com.controlqr.acceso

import com.controlqr.acceso.update.UpdateChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private val checker = UpdateChecker()

    @Test
    fun `detecta una version mayor`() {
        assertTrue(checker.isNewer("1.1.0", "1.0.9"))
        assertTrue(checker.isNewer("2.0.0", "1.9.9"))
        assertTrue(checker.isNewer("1.0.10", "1.0.9"))
    }

    @Test
    fun `no reporta actualizacion si es igual o menor`() {
        assertFalse(checker.isNewer("1.0.0", "1.0.0"))
        assertFalse(checker.isNewer("1.0.0", "1.0.1"))
        assertFalse(checker.isNewer("0.9.9", "1.0.0"))
    }

    @Test
    fun `ignora sufijos como debug o rc`() {
        assertFalse(checker.isNewer("1.0.0", "1.0.0-debug"))
        assertTrue(checker.isNewer("1.1.0", "1.0.0-rc1"))
    }

    @Test
    fun `tolera versiones con distinto numero de segmentos`() {
        assertTrue(checker.isNewer("1.1", "1.0.9"))
        assertFalse(checker.isNewer("1.0", "1.0.0"))
    }
}
