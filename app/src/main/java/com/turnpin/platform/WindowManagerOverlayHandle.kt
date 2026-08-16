package com.turnpin.platform

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.turnpin.core.OverlayHandle
import com.turnpin.model.OverlayStrategy

/**
 * [OverlayHandle] の実装。TurnPin の中核となる「不可視オーバーレイ」を貼る。
 *
 * WindowManagerService は **Z オーダー上位のウィンドウが宣言した `screenOrientation`
 * を優先**して画面の向きを決める。`TYPE_APPLICATION_OVERLAY` は通常のアプリウィンドウ
 * より上位に置かれるため、ここで向きを宣言すれば他アプリの
 * `android:screenOrientation` 宣言を上書きできる（仕様 §2.1）。
 *
 * 端末・ROM によっては効き方が違うので、貼り方を [OverlayStrategy] で差し替えられる
 * （仕様 §2.3 のフォールバック階段）。
 *
 * 呼び出しはすべてメインスレッドから行うこと（WindowManager の制約）。
 */
class WindowManagerOverlayHandle(
    private val context: Context,
    strategy: OverlayStrategy = OverlayStrategy.DEFAULT,
) : OverlayHandle {

    private val windowManager: WindowManager =
        context.getSystemService(WindowManager::class.java)

    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null

    /**
     * 貼り方の戦略。attach 中に変更した場合は、
     * 呼び出し側が [detach] → [attach] で貼り直すこと（ここでは自動で貼り直さない）。
     */
    var strategy: OverlayStrategy = strategy

    override val isAttached: Boolean
        get() = view != null

    override fun attach(orientation: Int) {
        check(view == null) { "既に attach 済み。多重 addView になる" }

        val overlayView = createOverlayView()
        val layoutParams = createLayoutParams(orientation)

        // 例外（BadTokenException など）は握りつぶさず呼び出し側へ透過させる。
        // OrientationController が ApplyResult.Failed に包む。
        // ここで投げた場合 view は null のままなので、状態は矛盾しない。
        windowManager.addView(overlayView, layoutParams)

        view = overlayView
        params = layoutParams
    }

    override fun updateOrientation(orientation: Int) {
        val overlayView = checkNotNull(view) { "attach していないオーバーレイを更新した" }
        val layoutParams = checkNotNull(params) { "attach していないオーバーレイを更新した" }

        layoutParams.screenOrientation = orientation

        if (strategy == OverlayStrategy.C) {
            reattach(overlayView, layoutParams)
        } else {
            windowManager.updateViewLayout(overlayView, layoutParams)
        }
    }

    /**
     * 一部 ROM は `updateViewLayout` では向きの再評価が走らないため、貼り直して反映させる
     * （戦略 C）。
     *
     * remove と add の間で状態を一度落としているのは、add が失敗したときに
     * 「貼っていないのに isAttached が true」という矛盾を残さないため。
     * そのまま残すと後続の [detach] が既に外れた View を消そうとして例外になる。
     */
    private fun reattach(current: View, layoutParams: WindowManager.LayoutParams) {
        windowManager.removeView(current)
        view = null
        params = null

        // 外した View を貼り直すより、新しい View を使うほうが
        // WindowManager の内部状態（削除待ちキュー）と競合しない。
        val replacement = createOverlayView()
        windowManager.addView(replacement, layoutParams)

        view = replacement
        params = layoutParams
    }

    private fun createOverlayView(): View = View(context).apply {
        // 戦略 A / C は 0x0 で描画自体が起きないが、B / D は面を持つので
        // 完全透明を明示する（既定の背景 null に依存しない）。
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun detach() {
        val overlayView = view ?: return
        windowManager.removeView(overlayView)
        view = null
        params = null
    }

    /** 戦略ごとのウィンドウ設定を組み立てる（仕様 §2.3）。 */
    private fun createLayoutParams(orientation: Int): WindowManager.LayoutParams {
        val (width, height) = when (strategy) {
            // A / C: 描画領域を持たない。他アプリのタッチに最も影響しない。
            OverlayStrategy.A, OverlayStrategy.C -> 0 to 0
            // B: 0x0 を無視して向き決定に参加させない ROM 向けに最小の面を与える。
            OverlayStrategy.B -> 1 to 1
            // D: 最終手段。全画面を覆うので他アプリのタッチに影響が出うる。
            OverlayStrategy.D ->
                WindowManager.LayoutParams.MATCH_PARENT to WindowManager.LayoutParams.MATCH_PARENT
        }

        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_FOCUSABLE: 入力フォーカスを奪わない（他アプリのキーボードを壊さない）
            // NOT_TOUCHABLE: タッチをすべて下のウィンドウへ透過させる
            // LAYOUT_IN_SCREEN: ステータスバー領域を含めて配置し、向き決定に確実に参加させる
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            screenOrientation = orientation
        }
    }
}
