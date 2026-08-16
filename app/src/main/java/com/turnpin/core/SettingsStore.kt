package com.turnpin.core

import com.turnpin.model.OrientationMode
import com.turnpin.model.OverlayStrategy

/**
 * 永続設定の読み書き口（仕様 §4.4）。
 *
 * SharedPreferences への依存をここで断ち切る。実装は
 * [com.turnpin.platform.PrefsSettingsStore]。
 */
interface SettingsStore {

    /** 回転制御が ON か。既定 `false`。 */
    var enabled: Boolean

    /** 最後に適用したモード。既定 [OrientationMode.PORTRAIT]。 */
    var mode: OrientationMode

    /** 端末起動時に自動で復帰するか。既定 `false`。 */
    var startOnBoot: Boolean

    /** WRITE_SETTINGS でシステムの回転設定も揃えるか（§2.4）。既定 `false`。 */
    var syncSystemSettings: Boolean

    /** オーバーレイの貼り方（互換モード）。既定 [OverlayStrategy.A]。 */
    var overlayStrategy: OverlayStrategy
}
