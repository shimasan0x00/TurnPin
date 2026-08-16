package com.turnpin.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [OrientationMode] の純粋ロジックに対する仕様（振る舞い）テスト。
 * 1 テスト 1 概念・AAA（Arrange / Act / Assert）で記述する。
 *
 * ここで期待値に使う数値は `android.content.pm.ActivityInfo.SCREEN_ORIENTATION_*` の値。
 * ActivityInfo を import すると JVM 単体テストで Stub 例外になるため、
 * 本体・テストとも数値リテラルで持ち、対応をコメントで示す。
 */
class OrientationModeTest {

    // ---- toActivityInfo ------------------------------------------------------

    @Test
    fun `AUTO は FULL_SENSOR に変換される`() {
        // Arrange
        val mode = OrientationMode.AUTO

        // Act
        val result = mode.toActivityInfo()

        // Assert
        assertEquals(10, result)  // SCREEN_ORIENTATION_FULL_SENSOR
    }

    @Test
    fun `PORTRAIT は PORTRAIT に変換される`() {
        val mode = OrientationMode.PORTRAIT

        val result = mode.toActivityInfo()

        assertEquals(1, result)   // SCREEN_ORIENTATION_PORTRAIT
    }

    @Test
    fun `PORTRAIT_REVERSE は REVERSE_PORTRAIT に変換される`() {
        val mode = OrientationMode.PORTRAIT_REVERSE

        val result = mode.toActivityInfo()

        assertEquals(9, result)   // SCREEN_ORIENTATION_REVERSE_PORTRAIT
    }

    @Test
    fun `LANDSCAPE は LANDSCAPE に変換される`() {
        val mode = OrientationMode.LANDSCAPE

        val result = mode.toActivityInfo()

        assertEquals(0, result)   // SCREEN_ORIENTATION_LANDSCAPE
    }

    @Test
    fun `LANDSCAPE_REVERSE は REVERSE_LANDSCAPE に変換される`() {
        val mode = OrientationMode.LANDSCAPE_REVERSE

        val result = mode.toActivityInfo()

        assertEquals(8, result)   // SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    }

    @Test
    fun `PORTRAIT_SENSOR は SENSOR_PORTRAIT に変換される`() {
        val mode = OrientationMode.PORTRAIT_SENSOR

        val result = mode.toActivityInfo()

        assertEquals(7, result)   // SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    @Test
    fun `LANDSCAPE_SENSOR は SENSOR_LANDSCAPE に変換される`() {
        val mode = OrientationMode.LANDSCAPE_SENSOR

        val result = mode.toActivityInfo()

        assertEquals(6, result)   // SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    @Test
    fun `LOCK_CURRENT は LOCKED に変換される`() {
        val mode = OrientationMode.LOCK_CURRENT

        val result = mode.toActivityInfo()

        assertEquals(14, result)  // SCREEN_ORIENTATION_LOCKED
    }

    // ---- fromActivityInfo ----------------------------------------------------

    @Test
    fun `全モードが往復変換で元のモードに戻る`() {
        // Arrange
        val allModes = OrientationMode.entries

        // Act / Assert
        allModes.forEach { mode ->
            val roundTripped = OrientationMode.fromActivityInfo(mode.toActivityInfo())
            assertEquals(mode, roundTripped)
        }
    }

    @Test
    fun `未知の定数には null を返す`() {
        // Arrange
        val unspecified = -1  // SCREEN_ORIENTATION_UNSPECIFIED（TurnPin では使わない）

        // Act
        val result = OrientationMode.fromActivityInfo(unspecified)

        // Assert
        assertNull(result)
    }

    @Test
    fun `マッピングに無い USER 系の定数には null を返す`() {
        // Arrange
        val userLandscape = 11  // SCREEN_ORIENTATION_USER_LANDSCAPE

        // Act
        val result = OrientationMode.fromActivityInfo(userLandscape)

        // Assert
        assertNull(result)
    }

    // ---- next（通知の「90°回す」用の巡回） -----------------------------------

    @Test
    fun `next は縦から横へ進む`() {
        val result = OrientationMode.PORTRAIT.next()

        assertEquals(OrientationMode.LANDSCAPE, result)
    }

    @Test
    fun `next は横から縦逆へ進む`() {
        val result = OrientationMode.LANDSCAPE.next()

        assertEquals(OrientationMode.PORTRAIT_REVERSE, result)
    }

    @Test
    fun `next は縦逆から横逆へ進む`() {
        val result = OrientationMode.PORTRAIT_REVERSE.next()

        assertEquals(OrientationMode.LANDSCAPE_REVERSE, result)
    }

    @Test
    fun `next は横逆から縦へ戻って巡回する`() {
        val result = OrientationMode.LANDSCAPE_REVERSE.next()

        assertEquals(OrientationMode.PORTRAIT, result)
    }

    @Test
    fun `next を4回呼ぶと元のモードに戻る`() {
        // Arrange
        val start = OrientationMode.PORTRAIT

        // Act
        val result = start.next().next().next().next()

        // Assert
        assertEquals(start, result)
    }

    @Test
    fun `4方向以外のモードから next を呼ぶと巡回の先頭である縦になる`() {
        // Arrange: AUTO / センサー系 / LOCK_CURRENT は 4 方向の巡回に含まれない。
        val outsideCycle = listOf(
            OrientationMode.AUTO,
            OrientationMode.PORTRAIT_SENSOR,
            OrientationMode.LANDSCAPE_SENSOR,
            OrientationMode.LOCK_CURRENT,
        )

        // Act / Assert
        outsideCycle.forEach { mode ->
            assertEquals(OrientationMode.PORTRAIT, mode.next())
        }
    }

    // ---- requiredAxis（ドリフト再適用の判定用） -------------------------------

    @Test
    fun `縦系のモードは縦軸を要求する`() {
        // Arrange
        val portraitModes = listOf(
            OrientationMode.PORTRAIT,
            OrientationMode.PORTRAIT_REVERSE,
            OrientationMode.PORTRAIT_SENSOR,
        )

        // Act / Assert
        portraitModes.forEach { mode ->
            assertEquals(OrientationAxis.PORTRAIT, mode.requiredAxis())
        }
    }

    @Test
    fun `横系のモードは横軸を要求する`() {
        val landscapeModes = listOf(
            OrientationMode.LANDSCAPE,
            OrientationMode.LANDSCAPE_REVERSE,
            OrientationMode.LANDSCAPE_SENSOR,
        )

        landscapeModes.forEach { mode ->
            assertEquals(OrientationAxis.LANDSCAPE, mode.requiredAxis())
        }
    }

    @Test
    fun `AUTO は軸を要求しない`() {
        // Arrange: 全方向自動なので、どの向きになっても「食い違い」ではない。
        val mode = OrientationMode.AUTO

        // Act
        val result = mode.requiredAxis()

        // Assert
        assertNull(result)
    }

    @Test
    fun `LOCK_CURRENT は軸を要求しない`() {
        // Arrange: 適用時点の向きで固定するため、特定の軸を要求しない。
        val mode = OrientationMode.LOCK_CURRENT

        val result = mode.requiredAxis()

        assertNull(result)
    }
}
