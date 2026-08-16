package com.turnpin.core

import com.turnpin.model.OrientationMode

/**
 * システムの回転設定（`ACCELEROMETER_ROTATION` / `USER_ROTATION`）へ書き込む値を求める、
 * Android 非依存の純粋ロジック（仕様 §2.4 の補助機能）。
 *
 * これは**あくまで補助**であり、TurnPin の本体機能はオーバーレイ側にある。
 * ロック画面などオーバーレイが効かない領域での挙動を多少マシにするためだけに使う。
 *
 * 戻り値の 0〜3 は `android.view.Surface.ROTATION_0` 〜 `ROTATION_270`。
 * Surface を import すると JVM 単体テストで Stub 例外になるため数値で持つ。
 */
object SystemRotation {

    /** `Settings.System.ACCELEROMETER_ROTATION` に書く値。 */
    const val AUTO_ROTATE_ON = 1
    const val AUTO_ROTATE_OFF = 0

    /** OS の自動回転を有効にすべきモードか。センサーに委ねるモードだけ true。 */
    fun shouldAutoRotate(mode: OrientationMode): Boolean = when (mode) {
        OrientationMode.AUTO,
        OrientationMode.PORTRAIT_SENSOR,
        OrientationMode.LANDSCAPE_SENSOR,
        -> true

        OrientationMode.PORTRAIT,
        OrientationMode.PORTRAIT_REVERSE,
        OrientationMode.LANDSCAPE,
        OrientationMode.LANDSCAPE_REVERSE,
        OrientationMode.LOCK_CURRENT,
        -> false
    }

    /**
     * `Settings.System.USER_ROTATION` に書く値。書くべきでないモードでは `null`。
     *
     * `ROTATION_*` は**端末の自然向きからの回転量**なので、同じ「縦」でも
     * 自然向きが横の端末（Fire Max 11 など）と縦の端末とで値が変わる。
     * そのため [naturallyLandscape] を外から渡してもらう。
     *
     * 逆向き（上下逆・左右逆）が ROTATION_90 と ROTATION_270 のどちらに対応するかは
     * 端末の実装依存で厳密には決まらない。本機能は補助なので慣例に従う。
     */
    fun userRotationFor(mode: OrientationMode, naturallyLandscape: Boolean): Int? = when (mode) {
        // 自然向きと同じ軸なら 0、90 度回した軸なら 1（=ROTATION_90）から始める。
        OrientationMode.LANDSCAPE -> if (naturallyLandscape) 0 else 1
        OrientationMode.PORTRAIT -> if (naturallyLandscape) 1 else 0
        OrientationMode.LANDSCAPE_REVERSE -> if (naturallyLandscape) 2 else 3
        OrientationMode.PORTRAIT_REVERSE -> if (naturallyLandscape) 3 else 2

        // センサー任せ・現状維持のモードは固定すべき回転値を持たない。
        OrientationMode.AUTO,
        OrientationMode.PORTRAIT_SENSOR,
        OrientationMode.LANDSCAPE_SENSOR,
        OrientationMode.LOCK_CURRENT,
        -> null
    }

    /**
     * 端末の自然向きが横かどうかを、現在の回転量と見えている向きから逆算する。
     *
     * @param rotation `Display.getRotation()` の値（0〜3）
     * @param displayIsPortrait 今この瞬間、画面が縦に見えているか
     *   （`Configuration.orientation == ORIENTATION_PORTRAIT`）
     */
    fun isNaturallyLandscape(rotation: Int, displayIsPortrait: Boolean): Boolean {
        // 0 / 180 度なら見えている向きがそのまま自然向き。
        // 90 / 270 度なら自然向きは見えている向きの逆。
        val rotatedQuarterTurn = rotation == 1 || rotation == 3
        return if (rotatedQuarterTurn) displayIsPortrait else !displayIsPortrait
    }
}
