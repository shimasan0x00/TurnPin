package com.turnpin.core

import com.turnpin.model.OrientationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SystemRotation] の仕様テスト（仕様 §2.4 の補助機能）。
 *
 * 期待値の 0〜3 は `android.view.Surface.ROTATION_0` 〜 `ROTATION_270`。
 * Surface を import すると JVM 単体テストで Stub 例外になるため数値で持つ。
 */
class SystemRotationTest {

    // ---- shouldAutoRotate ----------------------------------------------------

    @Test
    fun `センサーに任せるモードでは自動回転を有効にする`() {
        // Arrange
        val sensorModes = listOf(
            OrientationMode.AUTO,
            OrientationMode.PORTRAIT_SENSOR,
            OrientationMode.LANDSCAPE_SENSOR,
        )

        // Act / Assert
        sensorModes.forEach { mode ->
            assertTrue(mode.name, SystemRotation.shouldAutoRotate(mode))
        }
    }

    @Test
    fun `向きを固定するモードでは自動回転を無効にする`() {
        // Arrange
        val fixedModes = listOf(
            OrientationMode.PORTRAIT,
            OrientationMode.PORTRAIT_REVERSE,
            OrientationMode.LANDSCAPE,
            OrientationMode.LANDSCAPE_REVERSE,
            OrientationMode.LOCK_CURRENT,
        )

        // Act / Assert
        fixedModes.forEach { mode ->
            assertFalse(mode.name, SystemRotation.shouldAutoRotate(mode))
        }
    }

    // ---- userRotationFor: 自然向きが横の端末（Fire Max 11 など） ---------------

    @Test
    fun `自然向きが横の端末では横が ROTATION_0 になる`() {
        val result = SystemRotation.userRotationFor(
            OrientationMode.LANDSCAPE, naturallyLandscape = true
        )

        assertEquals(0, result)
    }

    @Test
    fun `自然向きが横の端末では縦が ROTATION_90 になる`() {
        val result = SystemRotation.userRotationFor(
            OrientationMode.PORTRAIT, naturallyLandscape = true
        )

        assertEquals(1, result)
    }

    @Test
    fun `自然向きが横の端末では横逆が ROTATION_180 になる`() {
        val result = SystemRotation.userRotationFor(
            OrientationMode.LANDSCAPE_REVERSE, naturallyLandscape = true
        )

        assertEquals(2, result)
    }

    @Test
    fun `自然向きが横の端末では縦逆が ROTATION_270 になる`() {
        val result = SystemRotation.userRotationFor(
            OrientationMode.PORTRAIT_REVERSE, naturallyLandscape = true
        )

        assertEquals(3, result)
    }

    // ---- userRotationFor: 自然向きが縦の端末 ----------------------------------

    @Test
    fun `自然向きが縦の端末では縦が ROTATION_0 になる`() {
        val result = SystemRotation.userRotationFor(
            OrientationMode.PORTRAIT, naturallyLandscape = false
        )

        assertEquals(0, result)
    }

    @Test
    fun `自然向きが縦の端末では横が ROTATION_90 になる`() {
        val result = SystemRotation.userRotationFor(
            OrientationMode.LANDSCAPE, naturallyLandscape = false
        )

        assertEquals(1, result)
    }

    @Test
    fun `自然向きが縦の端末では縦逆が ROTATION_180 になる`() {
        val result = SystemRotation.userRotationFor(
            OrientationMode.PORTRAIT_REVERSE, naturallyLandscape = false
        )

        assertEquals(2, result)
    }

    @Test
    fun `自然向きが縦の端末では横逆が ROTATION_270 になる`() {
        val result = SystemRotation.userRotationFor(
            OrientationMode.LANDSCAPE_REVERSE, naturallyLandscape = false
        )

        assertEquals(3, result)
    }

    // ---- userRotationFor: 向きを特定できないモード -----------------------------

    @Test
    fun `向きを特定できないモードでは USER_ROTATION を書かない`() {
        // Arrange: センサー任せ・現状維持のモードは固定すべき回転値を持たない。
        val undetermined = listOf(
            OrientationMode.AUTO,
            OrientationMode.PORTRAIT_SENSOR,
            OrientationMode.LANDSCAPE_SENSOR,
            OrientationMode.LOCK_CURRENT,
        )

        // Act / Assert
        undetermined.forEach { mode ->
            assertNull(mode.name, SystemRotation.userRotationFor(mode, naturallyLandscape = true))
            assertNull(mode.name, SystemRotation.userRotationFor(mode, naturallyLandscape = false))
        }
    }

    // ---- 自然向きの判定 ------------------------------------------------------

    @Test
    fun `ROTATION_0 で横に見えている端末は自然向きが横`() {
        // Arrange: Fire Max 11 の素の状態
        val result = SystemRotation.isNaturallyLandscape(rotation = 0, displayIsPortrait = false)

        assertTrue(result)
    }

    @Test
    fun `ROTATION_0 で縦に見えている端末は自然向きが縦`() {
        val result = SystemRotation.isNaturallyLandscape(rotation = 0, displayIsPortrait = true)

        assertFalse(result)
    }

    @Test
    fun `ROTATION_90 で縦に見えている端末は自然向きが横`() {
        // Arrange: 自然向きが横の端末を 90 度回すと縦になる
        val result = SystemRotation.isNaturallyLandscape(rotation = 1, displayIsPortrait = true)

        assertTrue(result)
    }

    @Test
    fun `ROTATION_270 で横に見えている端末は自然向きが縦`() {
        val result = SystemRotation.isNaturallyLandscape(rotation = 3, displayIsPortrait = false)

        assertFalse(result)
    }
}
