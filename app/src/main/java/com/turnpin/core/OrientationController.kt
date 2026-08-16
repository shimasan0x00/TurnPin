package com.turnpin.core

import com.turnpin.model.OrientationMode

/**
 * TurnPin の中核ロジック。オーバーレイ・永続設定・権限の 3 者を束ねる。
 *
 * Android フレームワークに直接触れないため、JVM 単体テストで全分岐を検証できる
 * （[OrientationControllerTest]）。プロセス内での実体は
 * [com.turnpin.service.TurnPinService] が**ただ 1 つだけ**持つ。
 * Activity と Service が別々に持つと多重 addView になり、
 * アプリの強制停止時にオーバーレイが残留する。
 */
class OrientationController(
    private val overlay: OverlayHandle,
    private val store: SettingsStore,
    private val permissions: PermissionChecker,
) {

    /** 最後に適用したモード。初期値は前回保存分。 */
    private var current: OrientationMode = store.mode

    /**
     * [mode] を適用する。
     *
     * - 権限が無ければ [ApplyResult.PermissionDenied] を返し、オーバーレイには一切触れない
     * - 未 attach なら attach、attach 済みなら向きの更新のみ（多重 addView を避ける）
     * - 成功したときだけ [SettingsStore.enabled] と [SettingsStore.mode] を書く
     * - オーバーレイが投げた例外は握りつぶさず [ApplyResult.Failed] に包んで返す
     */
    fun apply(mode: OrientationMode): ApplyResult {
        // 権限は後から取り消されうるので、適用のたびに毎回問い合わせる。
        if (!permissions.canDrawOverlays()) return ApplyResult.PermissionDenied

        val orientation = mode.toActivityInfo()
        try {
            if (overlay.isAttached) overlay.updateOrientation(orientation)
            else overlay.attach(orientation)
        } catch (e: RuntimeException) {
            // WindowManager 系は BadTokenException / IllegalStateException /
            // SecurityException（いずれも RuntimeException）を投げる。
            // 状態を書き換えずに、原因をそのまま呼び出し側へ返す。
            return ApplyResult.Failed(e)
        }

        current = mode
        store.mode = mode
        store.enabled = true
        return ApplyResult.Success
    }

    /**
     * 制御を停止し、OS 標準の挙動に完全に戻す。
     *
     * 未 attach の状態で呼んでも安全な no-op（[SettingsStore.enabled] は必ず false になる）。
     * `SCREEN_ORIENTATION_UNSPECIFIED` を貼ったままにはせず、必ず取り外す。
     */
    fun stop() {
        if (overlay.isAttached) overlay.detach()
        store.enabled = false
    }

    /**
     * 保存済みの状態から復帰する。アプリ起動時 / BOOT_COMPLETED 時に呼ぶ。
     *
     * 前回 OFF なら何もしない。権限が失効していれば
     * [ApplyResult.PermissionDenied] を返すだけでクラッシュしない
     * （`enabled` は落とさないので、ユーザーが再許可すれば次回復帰する）。
     */
    fun restoreFromStore(): ApplyResult {
        if (!store.enabled) return ApplyResult.Success
        return apply(store.mode)
    }

    /** 最後に適用したモード。まだ適用していなければ前回保存分。 */
    fun currentMode(): OrientationMode = current

    /** オーバーレイが実際に貼られているか。 */
    fun isRunning(): Boolean = overlay.isAttached
}
