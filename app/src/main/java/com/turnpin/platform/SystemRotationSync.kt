package com.turnpin.platform

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import com.turnpin.core.SystemRotation
import com.turnpin.model.OrientationMode

/**
 * オーバーレイの適用に合わせて、OS の回転設定も揃える補助機能（仕様 §2.4）。
 *
 * ロック画面など**オーバーレイが効かない領域**での挙動を多少マシにするためだけのもので、
 * 本体機能ではない。WRITE_SETTINGS が無い／拒否された場合はサイレントにスキップし、
 * オーバーレイのみで動作を続ける（ここでエラーにして機能を止めない）。
 */
class SystemRotationSync(private val context: Context) {

    /** WRITE_SETTINGS が許可されているか。UI のチェックボックス制御にも使う。 */
    fun canSync(): Boolean = Settings.System.canWrite(context)

    /**
     * [mode] に合わせて `ACCELEROMETER_ROTATION` / `USER_ROTATION` を書く。
     *
     * @return 実際に書き込んだら true、権限が無くてスキップしたら false
     */
    fun apply(mode: OrientationMode): Boolean {
        if (!canSync()) return false

        val resolver = context.contentResolver

        // 自動回転の可否を先に決める。固定モードでは OFF にしないと USER_ROTATION が効かない。
        val autoRotate = if (SystemRotation.shouldAutoRotate(mode)) {
            SystemRotation.AUTO_ROTATE_ON
        } else {
            SystemRotation.AUTO_ROTATE_OFF
        }
        Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, autoRotate)

        SystemRotation.userRotationFor(mode, isNaturallyLandscape())?.let { rotation ->
            Settings.System.putInt(resolver, Settings.System.USER_ROTATION, rotation)
        }
        return true
    }

    /**
     * 端末の自然向きが横か。`Display.getRotation()` は自然向きからの回転量なので、
     * 今見えている向きと突き合わせて逆算する（Fire Max 11 の自然向きは横）。
     */
    private fun isNaturallyLandscape(): Boolean {
        val display = context.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)
        val displayIsPortrait =
            context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        return SystemRotation.isNaturallyLandscape(display.rotation, displayIsPortrait)
    }
}
