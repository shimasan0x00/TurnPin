package com.turnpin.core

/**
 * 向きを宣言する不可視オーバーレイウィンドウの操作口。
 *
 * WindowManager への依存をここで断ち切り、[OrientationController] を
 * JVM 単体テストで検証できるようにする。実装は
 * [com.turnpin.platform.WindowManagerOverlayHandle]。
 *
 * 失敗は**例外で表現してよい**（[OrientationController] が
 * [ApplyResult.Failed] に包む）。握りつぶしてはならない。
 */
interface OverlayHandle {

    /** オーバーレイが現在ウィンドウに追加されているか。 */
    val isAttached: Boolean

    /**
     * オーバーレイを追加し、同時に向きを [orientation] に設定する。
     * すでに追加済みの状態で呼んではならない（多重 addView になる）。
     *
     * @param orientation `ActivityInfo.SCREEN_ORIENTATION_*` の値
     */
    fun attach(orientation: Int)

    /**
     * 追加済みのオーバーレイの向きだけを [orientation] に更新する。
     *
     * @param orientation `ActivityInfo.SCREEN_ORIENTATION_*` の値
     */
    fun updateOrientation(orientation: Int)

    /** オーバーレイを取り外す。OS 標準の挙動に完全に戻す。 */
    fun detach()
}
