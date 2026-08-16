package com.turnpin.core

import com.turnpin.model.OrientationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [OrientationController] の仕様（振る舞い）テスト（仕様書 §7）。
 *
 * Android フレームワークに一切触れないため plain JUnit で完結する。
 * 1 テスト 1 概念・AAA（Arrange / Act / Assert）。
 */
class OrientationControllerTest {

    private val overlay = FakeOverlayHandle()
    private val store = FakeSettingsStore()
    private val permissions = FakePermissionChecker(granted = true)

    private fun controller() = OrientationController(overlay, store, permissions)

    // ---- 権限 ---------------------------------------------------------------

    @Test
    fun `権限が無いとき apply は PermissionDenied を返す`() {
        // Arrange
        permissions.granted = false

        // Act
        val result = controller().apply(OrientationMode.LANDSCAPE)

        // Assert
        assertEquals(ApplyResult.PermissionDenied, result)
    }

    @Test
    fun `権限が無いとき apply はオーバーレイに一切触れない`() {
        // Arrange
        permissions.granted = false

        // Act
        controller().apply(OrientationMode.LANDSCAPE)

        // Assert
        assertEquals(0, overlay.attachCount)
        assertFalse(overlay.isAttached)
    }

    @Test
    fun `権限が無いとき apply は store の enabled を立てない`() {
        // Arrange
        permissions.granted = false

        // Act
        controller().apply(OrientationMode.LANDSCAPE)

        // Assert
        assertFalse(store.enabled)
    }

    // ---- 適用 ---------------------------------------------------------------

    @Test
    fun `権限があるとき apply はオーバーレイを attach して指定モードの定数を設定する`() {
        // Arrange
        val mode = OrientationMode.PORTRAIT_REVERSE

        // Act
        val result = controller().apply(mode)

        // Assert
        assertEquals(ApplyResult.Success, result)
        assertTrue(overlay.isAttached)
        assertEquals(1, overlay.attachCount)
        assertEquals(listOf(mode.toActivityInfo()), overlay.orientationHistory)
    }

    @Test
    fun `同じモードで apply を2回呼んでも attach は1回だけ`() {
        // Arrange
        val target = controller()

        // Act
        target.apply(OrientationMode.LANDSCAPE)
        target.apply(OrientationMode.LANDSCAPE)

        // Assert
        assertEquals(1, overlay.attachCount)
    }

    @Test
    fun `異なるモードで apply を2回呼ぶと attach は1回で向き更新は2回`() {
        // Arrange
        val target = controller()

        // Act
        target.apply(OrientationMode.PORTRAIT)
        target.apply(OrientationMode.LANDSCAPE)

        // Assert
        assertEquals(1, overlay.attachCount)
        assertEquals(
            listOf(
                OrientationMode.PORTRAIT.toActivityInfo(),
                OrientationMode.LANDSCAPE.toActivityInfo(),
            ),
            overlay.orientationHistory,
        )
    }

    @Test
    fun `apply 成功後は store の enabled が true になる`() {
        // Act
        controller().apply(OrientationMode.LANDSCAPE)

        // Assert
        assertTrue(store.enabled)
    }

    @Test
    fun `apply 成功後は store の mode が指定モードになる`() {
        // Arrange
        val mode = OrientationMode.LANDSCAPE_REVERSE

        // Act
        controller().apply(mode)

        // Assert
        assertEquals(mode, store.mode)
    }

    @Test
    fun `apply 成功後は isRunning が true になる`() {
        // Arrange
        val target = controller()

        // Act
        target.apply(OrientationMode.PORTRAIT)

        // Assert
        assertTrue(target.isRunning())
    }

    @Test
    fun `currentMode は最後に適用したモードを返す`() {
        // Arrange
        val target = controller()

        // Act
        target.apply(OrientationMode.LANDSCAPE_SENSOR)

        // Assert
        assertEquals(OrientationMode.LANDSCAPE_SENSOR, target.currentMode())
    }

    // ---- 停止 ---------------------------------------------------------------

    @Test
    fun `stop はオーバーレイを detach して store の enabled を false にする`() {
        // Arrange
        val target = controller()
        target.apply(OrientationMode.LANDSCAPE)

        // Act
        target.stop()

        // Assert
        assertEquals(1, overlay.detachCount)
        assertFalse(overlay.isAttached)
        assertFalse(store.enabled)
    }

    @Test
    fun `attach していない状態の stop は例外にならず detach も呼ばれない`() {
        // Arrange: 一度も apply していない controller
        val target = controller()

        // Act
        target.stop()

        // Assert
        assertEquals(0, overlay.detachCount)
        assertFalse(store.enabled)
    }

    @Test
    fun `stop 後に apply すると再び attach される`() {
        // Arrange
        val target = controller()
        target.apply(OrientationMode.PORTRAIT)
        target.stop()

        // Act
        target.apply(OrientationMode.LANDSCAPE)

        // Assert
        assertEquals(2, overlay.attachCount)
        assertTrue(overlay.isAttached)
    }

    // ---- 例外の伝播（握りつぶさないこと） -------------------------------------

    @Test
    fun `overlay が例外を投げたとき apply は Failed を返す`() {
        // Arrange
        val boom = IllegalStateException("addView failed")
        overlay.failWith = boom

        // Act
        val result = controller().apply(OrientationMode.LANDSCAPE)

        // Assert
        assertTrue(result is ApplyResult.Failed)
        assertSame(boom, (result as ApplyResult.Failed).cause)
    }

    @Test
    fun `overlay が例外を投げたとき store の enabled は true にならない`() {
        // Arrange
        overlay.failWith = IllegalStateException("addView failed")

        // Act
        controller().apply(OrientationMode.LANDSCAPE)

        // Assert
        assertFalse(store.enabled)
    }

    @Test
    fun `overlay が例外を投げたとき isRunning は false のまま`() {
        // Arrange
        overlay.failWith = IllegalStateException("addView failed")
        val target = controller()

        // Act
        target.apply(OrientationMode.LANDSCAPE)

        // Assert
        assertFalse(target.isRunning())
    }

    // ---- restoreFromStore（起動時 / boot 時） --------------------------------

    @Test
    fun `restoreFromStore は enabled が true のとき保存済みモードを適用する`() {
        // Arrange
        store.enabled = true
        store.mode = OrientationMode.LANDSCAPE_REVERSE

        // Act
        val result = controller().restoreFromStore()

        // Assert
        assertEquals(ApplyResult.Success, result)
        assertEquals(
            listOf(OrientationMode.LANDSCAPE_REVERSE.toActivityInfo()),
            overlay.orientationHistory,
        )
    }

    @Test
    fun `restoreFromStore は enabled が false のとき何もしない`() {
        // Arrange
        store.enabled = false

        // Act
        val result = controller().restoreFromStore()

        // Assert
        assertEquals(ApplyResult.Success, result)
        assertEquals(0, overlay.attachCount)
        assertFalse(overlay.isAttached)
    }

    @Test
    fun `restoreFromStore は権限失効時に PermissionDenied を返しクラッシュしない`() {
        // Arrange: 前回 ON のまま、OS 設定から権限が取り消された状態
        store.enabled = true
        store.mode = OrientationMode.PORTRAIT
        permissions.granted = false

        // Act
        val result = controller().restoreFromStore()

        // Assert
        assertEquals(ApplyResult.PermissionDenied, result)
        assertEquals(0, overlay.attachCount)
    }
}
