package com.turnpin.model

import com.turnpin.R

/**
 * TurnPin が扱う画面の向きモード。
 *
 * [activityInfo] は `android.content.pm.ActivityInfo.SCREEN_ORIENTATION_*` の値だが、
 * **ActivityInfo を import しない**。android.jar のスタブは JVM 単体テストで
 * `RuntimeException: Stub!` を投げるため、この enum を JVM でテストできなくなる。
 * 代わりに数値リテラルを持ち、対応する定数名をコメントで示す。
 *
 * [labelResId] は R の int 定数なので、参照しても Android ランタイムは不要。
 */
enum class OrientationMode(
    private val activityInfo: Int,
    val labelResId: Int,
) {
    /** SCREEN_ORIENTATION_FULL_SENSOR — センサーに任せて 4 方向すべてに回る。 */
    AUTO(10, R.string.mode_auto),

    /** SCREEN_ORIENTATION_PORTRAIT */
    PORTRAIT(1, R.string.mode_portrait),

    /** SCREEN_ORIENTATION_REVERSE_PORTRAIT — OS 標準では切り替えられない向き。 */
    PORTRAIT_REVERSE(9, R.string.mode_portrait_reverse),

    /** SCREEN_ORIENTATION_LANDSCAPE */
    LANDSCAPE(0, R.string.mode_landscape),

    /** SCREEN_ORIENTATION_REVERSE_LANDSCAPE */
    LANDSCAPE_REVERSE(8, R.string.mode_landscape_reverse),

    /** SCREEN_ORIENTATION_SENSOR_PORTRAIT — 縦のまま上下だけセンサーに任せる。 */
    PORTRAIT_SENSOR(7, R.string.mode_portrait_sensor),

    /** SCREEN_ORIENTATION_SENSOR_LANDSCAPE — 横のまま左右だけセンサーに任せる。 */
    LANDSCAPE_SENSOR(6, R.string.mode_landscape_sensor),

    /** SCREEN_ORIENTATION_LOCKED — 適用した瞬間の向きで固定する。 */
    LOCK_CURRENT(14, R.string.mode_lock_current),
    ;

    /** WindowManager.LayoutParams#screenOrientation に設定する値。 */
    fun toActivityInfo(): Int = activityInfo

    /**
     * 4 方向を 90° ずつ回した次のモード。通知の「90°回す」相当の操作に使う。
     *
     * 巡回は 縦 → 横 → 縦（逆） → 横（逆） → 縦。
     * 巡回に含まれないモード（AUTO / センサー系 / LOCK_CURRENT）からは先頭の [PORTRAIT] へ入る。
     */
    fun next(): OrientationMode {
        val index = ROTATION_CYCLE.indexOf(this)
        return if (index < 0) ROTATION_CYCLE.first()
        else ROTATION_CYCLE[(index + 1) % ROTATION_CYCLE.size]
    }

    /**
     * このモードが要求する画面の軸。どの向きでも構わないモードでは `null`。
     *
     * ドリフト再適用（仕様 §4.6）で、実際の `Configuration.orientation` と突き合わせる。
     * 上下逆・左右逆の区別は `Configuration.orientation` からは取れないため、
     * ここでも軸までしか表現しない。
     */
    fun requiredAxis(): OrientationAxis? = when (this) {
        PORTRAIT, PORTRAIT_REVERSE, PORTRAIT_SENSOR -> OrientationAxis.PORTRAIT
        LANDSCAPE, LANDSCAPE_REVERSE, LANDSCAPE_SENSOR -> OrientationAxis.LANDSCAPE
        AUTO, LOCK_CURRENT -> null
    }

    companion object {
        /** [next] が辿る 4 方向の順序。 */
        private val ROTATION_CYCLE = listOf(PORTRAIT, LANDSCAPE, PORTRAIT_REVERSE, LANDSCAPE_REVERSE)

        /** 保存値が壊れていた / 無かったときに使うモード（仕様 §4.4）。 */
        val DEFAULT = PORTRAIT

        /**
         * `SCREEN_ORIENTATION_*` の値から対応するモードを引く。
         * TurnPin が扱わない値（UNSPECIFIED や USER 系）には `null` を返す。
         */
        fun fromActivityInfo(value: Int): OrientationMode? =
            entries.firstOrNull { it.activityInfo == value }

        /**
         * 永続化された enum 名から引く。未知の名前・null は [DEFAULT] にフォールバックする。
         * 保存値が壊れていてもクラッシュさせないため、例外は投げない。
         */
        fun fromName(name: String?): OrientationMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
