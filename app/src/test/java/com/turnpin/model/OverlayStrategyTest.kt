package com.turnpin.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [OverlayStrategy] の復元仕様。SharedPreferences に壊れた値が残っていても
 * クラッシュさせず既定へ倒すことを保証する。
 */
class OverlayStrategyTest {

    @Test
    fun `fromName は全戦略を名前から復元できる`() {
        OverlayStrategy.entries.forEach { strategy ->
            assertEquals(strategy, OverlayStrategy.fromName(strategy.name))
        }
    }

    @Test
    fun `fromName は未知の名前を既定戦略にフォールバックする`() {
        // Arrange
        val stale = "E"

        // Act
        val result = OverlayStrategy.fromName(stale)

        // Assert
        assertEquals(OverlayStrategy.DEFAULT, result)
    }

    @Test
    fun `fromName は null を既定戦略にフォールバックする`() {
        val result = OverlayStrategy.fromName(null)

        assertEquals(OverlayStrategy.DEFAULT, result)
    }

    @Test
    fun `既定戦略は A である`() {
        // 仕様 §2.3: 0x0・タッチ透過の A を既定にし、効かない端末で B〜D を試す。
        assertEquals(OverlayStrategy.A, OverlayStrategy.DEFAULT)
    }
}
