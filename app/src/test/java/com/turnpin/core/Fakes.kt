package com.turnpin.core

import com.turnpin.model.OrientationMode
import com.turnpin.model.OverlayStrategy

/**
 * [OrientationController] のテスト用テストダブル。
 *
 * モックライブラリを使わず手書きする（実行時依存を増やさない方針のため、
 * テスト依存も JUnit4 のみに限る）。
 */

/**
 * [OverlayHandle] のフェイク。呼び出しを記録し、任意の時点で例外を投げさせられる。
 *
 * [orientationHistory] は attach と updateOrientation の**両方**を記録する。
 * attach 自体が「向きを 1 回設定する」操作なので、
 * 「attach は 1 回・向き更新は 2 回」という仕様（§7）をこの 1 本で検証できる。
 */
class FakeOverlayHandle : OverlayHandle {

    override var isAttached: Boolean = false
        private set

    var attachCount: Int = 0
        private set

    var detachCount: Int = 0
        private set

    /** attach / updateOrientation で設定された向きの並び。 */
    val orientationHistory = mutableListOf<Int>()

    /** 非 null なら attach / updateOrientation でこの例外を投げる。 */
    var failWith: Throwable? = null

    override fun attach(orientation: Int) {
        failWith?.let { throw it }
        check(!isAttached) { "既に attach 済みのオーバーレイに attach した（多重 addView）" }
        isAttached = true
        attachCount++
        orientationHistory += orientation
    }

    override fun updateOrientation(orientation: Int) {
        failWith?.let { throw it }
        check(isAttached) { "attach していないオーバーレイを updateOrientation した" }
        orientationHistory += orientation
    }

    override fun detach() {
        isAttached = false
        detachCount++
    }
}

/** [SettingsStore] のフェイク。既定値は仕様 §4.4 に合わせる。 */
class FakeSettingsStore(
    override var enabled: Boolean = false,
    override var mode: OrientationMode = OrientationMode.PORTRAIT,
    override var startOnBoot: Boolean = false,
    override var syncSystemSettings: Boolean = false,
    override var overlayStrategy: OverlayStrategy = OverlayStrategy.A,
) : SettingsStore

/** [PermissionChecker] のフェイク。許可状態をテストから切り替えられる。 */
class FakePermissionChecker(var granted: Boolean = true) : PermissionChecker {
    override fun canDrawOverlays(): Boolean = granted
}
